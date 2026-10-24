package io.legado.app.ui.book.info.edit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.VideoPlay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class BookNoteEditActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureId = "book-note-test-${UUID.randomUUID()}"
    private lateinit var book: Book

    @Before fun insertFixture() {
        book = Book(bookUrl = fixtureId, name = fixtureId, author = "Note test fixture",
            intro = "Source introduction", userNote = "Saved note", durChapterIndex = 5, durChapterPos = 78)
        appDb.bookDao.insert(book)
    }

    @After fun removeFixture() {
        appDb.bookDao.getBook(fixtureId)?.let { appDb.bookDao.delete(it) }
    }

    private fun noteField(scenario: ActivityScenario<BookInfoEditActivity>): SemanticsNodeInteraction {
        lateinit var label: String
        scenario.onActivity { label = it.getString(R.string.book_note_label) }
        val matcher = hasSetTextAction() and hasText(label)
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNode(matcher)
    }

    @Test fun unsavedDraftSurvivesRecreationAndBackDoesNotPersistIt() {
        ActivityScenario.launch<BookInfoEditActivity>(
            Intent(context, BookInfoEditActivity::class.java).putExtra("bookUrl", fixtureId)
        ).use { scenario ->
            noteField(scenario).performScrollTo().performTextReplacement("Uncommitted note\nSecond line")
            scenario.recreate()
            noteField(scenario).performScrollTo().assertTextContains("Uncommitted note\nSecond line")
            assertEquals("Saved note", appDb.bookDao.getBook(fixtureId)?.userNote)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        val saved = appDb.bookDao.getBook(fixtureId)!!
        assertEquals("Saved note", saved.userNote)
        assertEquals(5, saved.durChapterIndex)
        assertEquals(78, saved.durChapterPos)
    }

    @Test fun savingNotePreservesNewerProgressAndUpdatesAllActiveReaderNotes() {
        val previousReaders = listOf(ReadBook.book, ReadManga.book, AudioPlay.book, VideoPlay.book)
        val activeReaders = List(4) { book.copy(durChapterPos = 800 + it) }
        try {
            ActivityScenario.launch<BookInfoEditActivity>(
                Intent(context, BookInfoEditActivity::class.java).putExtra("bookUrl", fixtureId)
            ).use { scenario ->
                noteField(scenario).performScrollTo().performTextReplacement("Saved from editor")
                appDb.bookDao.update(book.copy(durChapterIndex = 9, durChapterPos = 345))
                scenario.onActivity { activity ->
                    ReadBook.book = activeReaders[0]
                    ReadManga.book = activeReaders[1]
                    AudioPlay.book = activeReaders[2]
                    VideoPlay.book = activeReaders[3]
                    val save = MenuBuilder(activity).add(0, R.id.menu_save, 0, activity.getString(R.string.action_save))
                    activity.onCompatOptionsItemSelected(save)
                }
                compose.waitUntil(5_000) {
                    appDb.bookDao.getBook(fixtureId)?.userNote == "Saved from editor" &&
                        activeReaders.all { it.userNote == "Saved from editor" }
                }
            }
            val saved = appDb.bookDao.getBook(fixtureId)!!
            assertEquals(9, saved.durChapterIndex)
            assertEquals(345, saved.durChapterPos)
            activeReaders.forEachIndexed { index, reader ->
                assertEquals(800 + index, reader.durChapterPos)
                assertEquals("Saved from editor", reader.userNote)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ReadBook.book = previousReaders[0]
                ReadManga.book = previousReaders[1]
                AudioPlay.book = previousReaders[2]
                VideoPlay.book = previousReaders[3]
            }
        }
    }

    @Test fun realImeKeepsNoteAndSaveReachableAndBackHidesKeyboardBeforeCancelling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previousReaders = listOf(ReadBook.book, ReadManga.book, AudioPlay.book, VideoPlay.book)
        try {
            ActivityScenario.launch<BookInfoEditActivity>(
                Intent(context, BookInfoEditActivity::class.java).putExtra("bookUrl", fixtureId)
            ).use { scenario ->
                fun imeVisible(): Boolean {
                    var visible = false
                    scenario.onActivity { activity ->
                        visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                    return visible
                }

                val noteField = noteField(scenario)
                noteField.performScrollTo().performClick()
                // This must time out if the device has no usable soft keyboard.
                compose.waitUntil(10_000) { imeVisible() }
                noteField.performTextInput("\nUnsaved IME draft\nSecond line")
                noteField.assertIsDisplayed().assertIsFocused()
                assertTrue("Soft keyboard disappeared during note input", imeVisible())
                scenario.onActivity { activity ->
                    assertEquals("The editor must resize its window for the IME",
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                        activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    val save = requireNotNull(activity.findViewById<View>(R.id.menu_save)) {
                        "The toolbar save action is not present while the IME is open"
                    }
                    val saveBounds = Rect()
                    val viewport = Rect()
                    activity.window.decorView.getWindowVisibleDisplayFrame(viewport)
                    assertTrue("The toolbar save action is not reachable", save.isShown && save.isEnabled &&
                        save.isClickable && save.getGlobalVisibleRect(saveBounds) &&
                        viewport.contains(saveBounds))
                    val windowBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Rect(activity.windowManager.currentWindowMetrics.bounds)
                    } else {
                        val metrics = DisplayMetrics()
                        @Suppress("DEPRECATION")
                        activity.windowManager.defaultDisplay.getRealMetrics(metrics)
                        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
                    }
                    val insets = requireNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
                    val screenViewport = Rect(windowBounds).apply {
                        top += insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                        bottom -= insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    }
                    val location = IntArray(2)
                    save.getLocationOnScreen(location)
                    val physicalSaveBounds = Rect(location[0], location[1],
                        location[0] + save.width, location[1] + save.height)
                    assertTrue("The save action is outside the physical screen above the IME: " +
                        "save=$physicalSaveBounds, usable=$screenViewport",
                        !physicalSaveBounds.isEmpty && screenViewport.contains(physicalSaveBounds))
                }
                compose.waitForIdle()
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.waitForIdle(1_000, 5_000)
                val directory = File(context.getExternalFilesDir(null), "book-note-visual")
                assertTrue("Cannot create the native screenshot directory", directory.isDirectory || directory.mkdirs())
                val prefix = InstrumentationRegistry.getArguments().getString("visualPrefix") ?: "current"
                val screenshotFile = File(directory, "$prefix-ime.png")
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
                    "Native screenshot capture failed while the IME was visible"
                }
                try {
                    screenshotFile.outputStream().use {
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    screenshot.recycle()
                }
                assertTrue("The native IME screenshot is empty", screenshotFile.length() > 0)
                assertEquals("Saved note", appDb.bookDao.getBook(fixtureId)?.userNote)

                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                compose.waitUntil(5_000) { !imeVisible() }
                assertEquals("First Back must only hide the IME", Lifecycle.State.RESUMED, scenario.state)
                noteField.assertTextContains("Unsaved IME draft", substring = true)
                assertEquals("Saved note", appDb.bookDao.getBook(fixtureId)?.userNote)

                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                compose.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
            }
            val saved = appDb.bookDao.getBook(fixtureId)!!
            assertEquals("Saved note", saved.userNote)
            assertEquals(5, saved.durChapterIndex)
            assertEquals(78, saved.durChapterPos)
        } finally {
            instrumentation.runOnMainSync {
                ReadBook.book = previousReaders[0]
                ReadManga.book = previousReaders[1]
                AudioPlay.book = previousReaders[2]
                VideoPlay.book = previousReaders[3]
            }
        }
    }
}
