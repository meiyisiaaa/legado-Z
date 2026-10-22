package io.legado.app.ui.book.info

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.book.info.edit.BookInfoEditScreen
import io.legado.app.ui.book.info.edit.BookInfoEditUiState
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class BookNoteUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()

    private fun record(state: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(3_500, 10_000)
        val directory = File(context.getExternalFilesDir(null), "book-note-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun detailsOnlyShowNonBlankNotesAndKeepLongNotesReachableAtLargeFont() {
        val label = context.getString(R.string.book_note_label)
        var note by mutableStateOf("")
        compose.setContent { LegadoTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,
                arguments.getString("visualFontScale")?.toFloat() ?: 2f)) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.width(320.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
                        BookInfoScreen(BookInfoUiState(name = "Book", userNote = note, loading = false),
                            coverContent = { Box(it) }, introContent = {},
                            onCoverClick = {}, onCoverLongClick = {}, onNameClick = {}, onNameLongClick = {},
                            onAuthorClick = {}, onAuthorLongClick = {}, onOriginClick = {}, onChangeSource = {},
                            onChangeGroup = {}, onOpenToc = {}, onKindClick = {}, onKindLongClick = {},
                            onShelfClick = {}, onReadClick = {})
                    }
                }
            }
        } }
        compose.onNodeWithText(label).assertDoesNotExist()
        val longNote = "私人书目备注，保留多行内容。\n".repeat(12) + "最后一行"
        compose.runOnIdle { note = longNote }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText(longNote).performScrollTo().assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText(longNote).assertIsDisplayed()
        record("detail")
    }

    @Test fun editingNoteDoesNotChangeIntroAndExplainsBackupBoundary() {
        val label = context.getString(R.string.book_note_label)
        var state by mutableStateOf(BookInfoEditUiState(name = "Book", intro = "Source introduction"))
        compose.setContent { LegadoTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,
                arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        BookInfoEditScreen(state, { state = it }, coverContent = { Box(it) },
                            onSelectCover = {}, onChangeCover = {}, onRefreshCover = {})
                    }
                }
            }
        } }
        val noteField = compose.onNode(hasSetTextAction() and hasText(label))
        noteField.performScrollTo().performTextReplacement("My note\nSecond paragraph")
        compose.onNodeWithText(context.getString(R.string.book_note_backup_scope)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("My note\nSecond paragraph", state.userNote)
            assertEquals("Source introduction", state.intro)
        }
        noteField.assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("My note\nSecond paragraph")).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Source introduction")).assertIsDisplayed()
        record("edit")
    }
}
