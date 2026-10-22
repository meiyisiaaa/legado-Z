package io.legado.app.ui.book.read

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.removeLocalUriCache
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.TextFile
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReaderBodyAccessibilityInstrumentedTest {

    @Test
    fun bodyExposesInteractiveTextAndReaderActionsAndRestoresFocusAfterMenu() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val automationInfo = requireNotNull(automation.serviceInfo)
        val originalAutomationFlags = automationInfo.flags
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        var previousInBookshelf = false
        var previousIsComic = false
        var hasExistingReaderState = false
        instrumentation.runOnMainSync {
            hasExistingReaderState = ReadBook.callBack != null || ReadBook.book != null
            previousInBookshelf = ReadBook.inBookshelf
            previousIsComic = ReadBookConfig.isComic
        }
        assumeTrue("Skip to avoid replacing an existing reader owner/state", !hasExistingReaderState)

        val fixtureDir = File(context.cacheDir, "reader-accessibility-${UUID.randomUUID()}")
        val fixtureFile = File(fixtureDir, "reader-${UUID.randomUUID()}.txt")
        val firstMarker = "READER_ACCESSIBILITY_CHAPTER_MARKER"
        val bodyMarker = "READER_ACCESSIBILITY_SYNTHETIC_BODY"
        val linkLabel = "链接测试"
        val previousImageWay = context.getPrefString(PreferKey.clickImgWay)
        val hadImageWay = PreferenceManager.getDefaultSharedPreferences(context)
            .contains(PreferKey.clickImgWay)
        val previousHelpVersions = listOf("readHelpVersion", "readMenuHelpVersion")
            .associateWith { LocalConfig.all[it] as? Int }
        var importedBook: Book? = null
        var scenario: ActivityScenario<ReadBookActivity>? = null

        try {
            // UiAutomation includes unimportant views by default; query the importance-filtered tree.
            automationInfo.flags = originalAutomationFlags and
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
            automation.setServiceInfo(automationInfo)
            assertEquals(0, automation.serviceInfo.flags and
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS)
            // First-use help is independent of the body/menu fixture and opens another window.
            LocalConfig.edit().putInt("readHelpVersion", 1).putInt("readMenuHelpVersion", 1).apply()
            assertTrue("Could not create private fixture directory", fixtureDir.mkdirs())
            fixtureFile.writeText(
                buildString {
                    appendLine("第一章 阅读器无障碍验收")
                    appendLine(firstMarker)
                    appendLine("正文测试内容。".repeat(500))
                    appendLine("第二章 后续章节")
                    appendLine("后续正文内容。".repeat(200))
                },
                Charsets.UTF_8
            )

            importedBook = LocalBook.importFile(Uri.fromFile(fixtureFile))
            importedBook!!.apply {
                tocUrl = "^第[一二]章[ \\t]+.*\$${TextFile.spaceChars}"
                save()
            }
            context.putPrefString(PreferKey.clickImgWay, "1")

            val launchIntent = android.content.Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", importedBook!!.bookUrl)
                .putExtra("inBookshelf", true)
            scenario = ActivityScenario.launch<ReadBookActivity>(launchIntent)

            await("first chapter layout") {
                var ready = false
                scenario!!.onActivity { activity ->
                    val page = activity.findViewById<ReadView>(R.id.read_view).curPage.textPage
                    ready = ReadBook.book?.bookUrl == importedBook!!.bookUrl &&
                        ReadBook.chapterSize == 2 &&
                        ReadBook.curTextChapter?.chapter?.index == 0 &&
                        ReadBook.curTextChapter?.isCompleted == true &&
                        page.isCompleted && page.text.contains(firstMarker)
                }
                ready
            }

            scenario!!.onActivity { activity ->
                val pageView = activity.findViewById<ReadView>(R.id.read_view).curPage
                val contentView = pageView.findViewById<ContentTextView>(R.id.content_text_view)
                val syntheticPage = createInteractivePage(linkLabel)
                contentView.setContent(syntheticPage)
                pageView.setContentDescription(bodyMarker)
            }

            val link = awaitNode(automation) { it.text?.toString() == linkLabel }
            val imageDescription = context.getString(R.string.preview_image_by_click)
            val image = awaitNode(automation) { it.contentDescription?.toString() == imageDescription }
            assertEquals(
                "Adjacent characters in the same hyperlink should form one accessibility node",
                1,
                countNodes(automation.rootInActiveWindow) { it.text?.toString() == linkLabel }
            )
            assertTrue("The hyperlink should be an individually actionable node", link.isClickable)
            assertTrue("The hyperlink should have visible hit bounds", hasBoundsInScreen(link))
            assertTrue("The configured image preview should be individually actionable", image.isClickable)
            assertTrue("The image should have visible hit bounds", hasBoundsInScreen(image))

            val originalLinkBounds = Rect().also(link::getBoundsInScreen)
            scenario!!.onActivity { activity ->
                activity.findViewById<ReadView>(R.id.read_view).curPage
                    .findViewById<ContentTextView>(R.id.content_text_view).scroll(-8)
            }
            await("link bounds follow the scrolled content") {
                val node = findNode(automation.rootInActiveWindow) { it.text?.toString() == linkLabel }
                val bounds = Rect()
                node?.getBoundsInScreen(bounds)
                node != null && bounds.top == originalLinkBounds.top - 8
            }
            scenario!!.onActivity { activity ->
                activity.findViewById<ReadView>(R.id.read_view).curPage.resetPageOffset()
            }
            await("link bounds return after resetting the scroll") {
                val node = findNode(automation.rootInActiveWindow) { it.text?.toString() == linkLabel }
                val bounds = Rect()
                node?.getBoundsInScreen(bounds)
                node != null && bounds == originalLinkBounds
            }

            val linkMonitor = instrumentation.addMonitor(OpenUrlConfirmActivity::class.java.name, null, false)
            try {
                assertTrue("Activating the hyperlink should open its URL confirmation", link.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                val confirmActivity = instrumentation.waitForMonitorWithTimeout(linkMonitor, 5_000)
                assertNotNull("The hyperlink activation did not launch the URL confirmation", confirmActivity)
                instrumentation.runOnMainSync { confirmActivity?.finish() }
            } finally {
                instrumentation.removeMonitor(linkMonitor)
            }
            await("reader returns after hyperlink confirmation") {
                findNode(automation.rootInActiveWindow) { it.contentDescription?.toString() == bodyMarker } != null
            }

            val currentImage = awaitNode(automation) {
                it.contentDescription?.toString() == imageDescription
            }
            assertTrue("Activating the image should open its preview", currentImage.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("image preview dialog") {
                var shown = false
                scenario!!.onActivity { activity ->
                    shown = activity.supportFragmentManager.fragments.any { it is PhotoDialog && it.isAdded }
                }
                shown
            }
            scenario!!.onActivity { activity ->
                activity.supportFragmentManager.fragments.filterIsInstance<PhotoDialog>()
                    .forEach { it.dismissAllowingStateLoss() }
            }
            await("image preview dialog dismissal") {
                var dismissed = true
                scenario!!.onActivity { activity ->
                    dismissed = activity.supportFragmentManager.fragments.none { it is PhotoDialog && it.isAdded }
                }
                dismissed
            }

            val linkBeforeReplacement = awaitNode(automation) { it.text?.toString() == linkLabel }
            scenario!!.onActivity { activity ->
                val pageView = activity.findViewById<ReadView>(R.id.read_view).curPage
                pageView.findViewById<ContentTextView>(R.id.content_text_view)
                    .setContent(TextPage(text = "replacement body"))
                pageView.setContentDescription("READER_ACCESSIBILITY_REPLACED_BODY")
            }
            assertFalse(
                "A hyperlink node from a replaced page must not activate stale content",
                linkBeforeReplacement.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
            assertTrue("The stale hyperlink node should still be queryable as a hidden placeholder", linkBeforeReplacement.refresh())
            val staleBounds = Rect().also(linkBeforeReplacement::getBoundsInScreen)
            assertTrue("A stale hyperlink node must have empty bounds", staleBounds.isEmpty)
            assertFalse("A stale hyperlink node must not remain visible", linkBeforeReplacement.isVisibleToUser)
            scenario!!.onActivity { activity ->
                val pageView = activity.findViewById<ReadView>(R.id.read_view).curPage
                pageView.findViewById<ContentTextView>(R.id.content_text_view)
                    .setContent(createInteractivePage(linkLabel))
                pageView.setContentDescription(bodyMarker)
            }

            val currentBody = awaitNode(automation) { it.contentDescription?.toString() == bodyMarker }
            val labels = currentBody.actionList.mapNotNull { action -> action.label?.toString() }
            assertTrue("The body should expose a named next-page action", labels.contains(context.getString(R.string.next_page)))
            assertFalse("The first page should not expose a previous-page action", labels.contains(context.getString(R.string.prev_page)))
            val menuAction = currentBody.actionList.firstOrNull { it.label?.toString() == context.getString(R.string.menu) }
            assertNotNull("The body should expose a named menu action", menuAction)
            assertTrue("The menu action should open the reader menu", currentBody.performAction(requireNotNull(menuAction).id))

            await("reader body accessibility isolation") {
                var hidden = false
                scenario!!.onActivity { activity ->
                    val readView = activity.findViewById<ReadView>(R.id.read_view)
                    hidden = readView.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS &&
                        activity.handlerBinding.readMenu.visibility == View.VISIBLE
                }
                hidden && findNode(automation.rootInActiveWindow) { it.contentDescription?.toString() == bodyMarker } == null
            }

            scenario!!.onActivity { activity ->
                activity.handlerBinding.readMenu.runMenuOut(anim = false)
            }
            await("reader menu dismissal restores native body importance") {
                var restored = false
                scenario!!.onActivity { activity ->
                    val readView = activity.findViewById<ReadView>(R.id.read_view)
                    restored = readView.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO &&
                        activity.handlerBinding.readMenu.visibility == View.GONE
                }
                restored
            }
            var restoredBodyDescription = ""
            await("the current reader body returns to the platform tree") {
                scenario!!.onActivity { activity ->
                    restoredBodyDescription = activity.findViewById<ReadView>(R.id.read_view).curPage
                        .findViewById<ContentTextView>(R.id.content_text_view).contentDescription?.toString().orEmpty()
                }
                restoredBodyDescription.isNotBlank() && findNode(automation.rootInActiveWindow) {
                    it.contentDescription?.toString() == restoredBodyDescription
                } != null
            }

            val hasTalkBack = accessibilityManager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
                .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" }
            if (hasTalkBack) {
                await("TalkBack accessibility focus restored to the reader body") {
                    val focusedNode = automation.rootInActiveWindow
                        ?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                    findNode(focusedNode) { it.contentDescription?.toString() == restoredBodyDescription } != null
                }
            }

            scenario!!.onActivity { activity ->
                activity.isShowingSearchResult = true
                activity.handlerBinding.searchMenu.runMenuIn()
            }
            await("search results isolate the reader body") {
                var hidden = false
                scenario!!.onActivity { activity ->
                    hidden = activity.findViewById<ReadView>(R.id.read_view).importantForAccessibility ==
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
                hidden
            }
            scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await("Back from search results restores the reader body") {
                var restored = false
                scenario!!.onActivity { activity ->
                    restored = !activity.isShowingSearchResult &&
                        activity.handlerBinding.searchMenu.visibility != View.VISIBLE &&
                        activity.findViewById<ReadView>(R.id.read_view).importantForAccessibility ==
                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                }
                restored
            }
        } finally {
            scenario?.close()
            automationInfo.flags = originalAutomationFlags
            automation.setServiceInfo(automationInfo)
            LocalConfig.edit().apply {
                previousHelpVersions.forEach { (key, value) ->
                    if (value == null) remove(key) else putInt(key, value)
                }
            }.apply()
            if (hadImageWay) {
                context.putPrefString(PreferKey.clickImgWay, previousImageWay)
            } else {
                context.removePref(PreferKey.clickImgWay)
            }
            importedBook?.let { ownedBook ->
                appDb.bookChapterDao.delByBook(ownedBook.bookUrl)
                appDb.readRecordDao.deleteByName(ownedBook.name)
                appDb.bookDao.getBook(ownedBook.bookUrl)?.let { persistedBook ->
                    LocalBook.deleteBook(persistedBook, deleteOriginal = true)
                    persistedBook.delete()
                }
                ownedBook.removeLocalUriCache()
                fixtureFile.delete()
                TextFile.clear()
            } ?: fixtureFile.delete()
            fixtureDir.delete()
            instrumentation.runOnMainSync {
                if (ReadBook.callBack == null) {
                    if (ReadBook.book?.bookUrl == importedBook?.bookUrl) {
                        ReadBook.book = null
                    }
                    ReadBook.clearTextChapter()
                    ReadBook.chapterSize = 0
                    ReadBook.simulatedChapterSize = 0
                    ReadBook.durChapterIndex = 0
                    ReadBook.durChapterPos = 0
                    ReadBook.bookSource = null
                    ReadBook.contentProcessor = null
                    ReadBook.inBookshelf = previousInBookshelf
                    ReadBookConfig.isComic = previousIsComic
                }
            }
        }
    }

    private fun createInteractivePage(linkLabel: String): TextPage {
        val paint = ChapterProvider.contentPaint
        val lineTop = ChapterProvider.visibleRect.top + 16f
        val lineBottom = lineTop + paint.textSize * 1.8f
        val line = TextLine(
            text = linkLabel,
            lineTop = lineTop,
            lineBase = lineBottom - paint.fontMetrics.descent,
            lineBottom = lineBottom
        )
        var x = ChapterProvider.visibleRect.left + 16f
        val linkUrl = "https://example.invalid/accessibility"
        linkLabel.forEach { character ->
            val text = character.toString()
            val right = x + paint.measureText(text)
            line.addColumn(
                TextHtmlColumn(x, right, text, paint.textSize, null, linkUrl)
            )
            x = right
        }
        val imageWidth = paint.textSize * 1.5f
        line.addColumn(ImageColumn(x + paint.textSize, x + paint.textSize + imageWidth, "missing-accessibility-test.png"))

        return TextPage(text = "$linkLabel ").apply {
            addLine(line)
            height = ChapterProvider.visibleHeight.toFloat()
            renderHeight = ChapterProvider.visibleHeight
            isCompleted = true
        }
    }

    private fun awaitNode(
        automation: UiAutomation,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await("accessibility node") {
            found = findNode(automation.rootInActiveWindow, predicate)
            found != null
        }
        return requireNotNull(found)
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            findNode(node.getChild(index), predicate)?.let { return it }
        }
        return null
    }

    private fun countNodes(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Int {
        if (node == null) return 0
        var count = if (predicate(node)) 1 else 0
        for (index in 0 until node.childCount) {
            count += countNodes(node.getChild(index), predicate)
        }
        return count
    }

    private fun hasBoundsInScreen(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return !bounds.isEmpty
    }

    private fun await(label: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $label")
    }
}
