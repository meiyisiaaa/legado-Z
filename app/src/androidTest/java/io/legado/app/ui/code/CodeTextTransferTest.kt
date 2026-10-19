package io.legado.app.ui.code

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeTextTransferTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun inlineBoundaryAndLargeUtf8RoundTripUseOwnedPrivateFiles() {
        val inlineIntent = Intent()
        val inlineToken = CodeTextTransfer.setInput(
            context,
            inlineIntent,
            "a".repeat(CodeTextTransfer.MAX_INLINE_LENGTH)
        )
        assertNull(inlineToken)
        assertEquals(CodeTextTransfer.MAX_INLINE_LENGTH, inlineIntent.getStringExtra("text")?.length)
        assertFalse(inlineIntent.hasExtra(CodeTextTransfer.EXTRA_USE_TEXT_FILE))

        val largeText = "中😀".repeat(6_001)
        val largeIntent = Intent()
        val inputToken = CodeTextTransfer.setInput(context, largeIntent, largeText)
        assertNotNull(inputToken)
        assertNull(largeIntent.getStringExtra("text"))
        assertEquals(largeText, CodeTextTransfer.readInput(context, largeIntent).text)

        val returnedText = largeText + "\n编辑完成"
        val cursorPosition = 12_345
        val result = CodeTextTransfer.createResult(context, returnedText, cursorPosition)
        val outputToken = result.getStringExtra(CodeTextTransfer.EXTRA_TEXT_FILE_TOKEN)
        assertNotNull(outputToken)
        assertEquals(cursorPosition, result.getIntExtra("cursorPosition", -1))
        assertEquals(returnedText, CodeTextTransfer.readResult(context, result))
        assertFalse(File(context.filesDir, "code-edit-transfer/$outputToken").exists())

        CodeTextTransfer.delete(context, inputToken!!)
    }

    @Test
    fun launchFailureDeletesTheInputTransferItCreated() {
        val intent = Intent()
        var token: String? = null
        try {
            CodeTextTransfer.launch(context, intent, "x".repeat(12_001)) {
                token = it.getStringExtra(CodeTextTransfer.EXTRA_TEXT_FILE_TOKEN)
                throw IllegalStateException("simulated start failure")
            }
        } catch (expected: IllegalStateException) {
            assertEquals("simulated start failure", expected.message)
        }
        assertNotNull(token)
        assertFalse(File(context.filesDir, "code-edit-transfer/$token").exists())
    }

    @Test
    fun safeEditorThresholdCountsUtf8Bytes() {
        val limit = EditSafety.MAX_SORA_UTF8_BYTES
        assertFalse(EditSafety.requiresSafeEditor("a".repeat(limit)))
        assertTrue(EditSafety.requiresSafeEditor("a".repeat(limit + 1)))
        assertTrue(EditSafety.requiresSafeEditor("界".repeat(limit / 3 + 1)))
        assertFalse(EditSafety.requiresSafeEditor("界".repeat(limit / 3)))
    }

    @Test
    fun processRecreationRestoresDraftAndCursorWithoutReplacingInitialText() {
        val original = "原始规则😀\n" + "正文规则".repeat(70_000)
        val edited = original.replaceFirst("原始规则", "已编辑规则")
        val latestDraft = "$edited\n最后快照"
        val cursor = latestDraft.length - 3
        val application = context.applicationContext as Application
        val firstViewModel = CodeEditViewModel(application).apply {
            initialText = original
            updateSafeDraft(edited, cursor)
        }
        val savedState = Bundle()
        try {
            firstViewModel.saveProcessState(savedState, edited, cursor)
            val token = savedState.getString(CodeEditViewModel.STATE_PROCESS_DRAFT_TOKEN)
            assertNotNull(token)
            firstViewModel.saveProcessState(savedState, latestDraft, cursor)
            assertEquals(token, savedState.getString(CodeEditViewModel.STATE_PROCESS_DRAFT_TOKEN))
            assertEquals(latestDraft, CodeTextTransfer.read(context, token!!))
            assertEquals(cursor, savedState.getInt(CodeEditViewModel.STATE_PROCESS_DRAFT_CURSOR))
            assertEquals(
                setOf(
                    "codeEditProcessDraftRequired",
                    CodeEditViewModel.STATE_PROCESS_DRAFT_TOKEN,
                    CodeEditViewModel.STATE_PROCESS_DRAFT_CURSOR
                ),
                savedState.keySet()
            )

            // A fresh ViewModel represents process recreation: only the baseline arrives from the
            // original input, while the saved-state token restores the edited snapshot.
            val restoredViewModel = CodeEditViewModel(application).apply {
                initialText = original
                prepareProcessRestore(savedState)
                restorePendingProcessDraft()
            }
            assertEquals(original, restoredViewModel.initialText)
            assertEquals(latestDraft, restoredViewModel.safeDraft)
            assertEquals(cursor, restoredViewModel.safeCursorPosition)
            restoredViewModel.releaseProcessState()
            firstViewModel.releaseProcessState()
            assertFalse(File(context.filesDir, "code-edit-transfer/$token").exists())
        } finally {
            savedState.getString(CodeEditViewModel.STATE_PROCESS_DRAFT_TOKEN)?.let {
                CodeTextTransfer.delete(context, it)
            }
        }
    }

    @Test
    fun processRecreationRestoresSoraDraftForTextBelowSafeEditorThreshold() {
        val original = "a".repeat(20_000)
        val edited = "prefix-$original"
        val cursor = 17
        val application = context.applicationContext as Application
        val firstViewModel = CodeEditViewModel(application).apply {
            initialText = original
            updateEditorDraft(edited, cursor)
        }
        val savedState = Bundle()
        try {
            firstViewModel.saveProcessState(savedState, edited, cursor)
            val restoredViewModel = CodeEditViewModel(application).apply {
                initialText = original
                prepareProcessRestore(savedState)
                restorePendingProcessDraft()
            }
            assertEquals(original, restoredViewModel.initialText)
            assertEquals(edited, restoredViewModel.editorDraft)
            assertEquals(cursor, restoredViewModel.editorDraftCursorPosition)
            restoredViewModel.releaseProcessState()
            firstViewModel.releaseProcessState()
        } finally {
            savedState.getString(CodeEditViewModel.STATE_PROCESS_DRAFT_TOKEN)?.let {
                CodeTextTransfer.delete(context, it)
            }
        }
    }

    @Test
    fun processRecreationUsesSafeEditorWhenSmallInitialTextHasLargeDraft() {
        val original = "小文本"
        val edited = "大草稿" + "a".repeat(EditSafety.MAX_SORA_UTF8_BYTES + 1)
        val cursor = edited.length - 1
        val application = context.applicationContext as Application
        val firstViewModel = CodeEditViewModel(application).apply {
            initialText = original
        }
        val savedState = Bundle()
        try {
            firstViewModel.saveProcessState(savedState, edited, cursor)
            val restoredViewModel = CodeEditViewModel(application).apply {
                initialText = original
                prepareProcessRestore(savedState)
                restorePendingProcessDraft()
            }
            assertEquals(original, restoredViewModel.initialText)
            assertTrue(restoredViewModel.useSafeEditor)
            assertEquals(edited, restoredViewModel.safeDraft)
            assertEquals(cursor, restoredViewModel.safeCursorPosition)
            assertNull(restoredViewModel.editorDraft)
            restoredViewModel.releaseProcessState()
            firstViewModel.releaseProcessState()
        } finally {
            savedState.getString(CodeEditViewModel.STATE_PROCESS_DRAFT_TOKEN)?.let {
                CodeTextTransfer.delete(context, it)
            }
        }
    }

    @Test
    fun missingSavedDraftFailsRestoreInsteadOfFallingBackToInitialText() {
        val original = "原始文本".repeat(70_000)
        val viewModel = CodeEditViewModel(context.applicationContext as Application).apply {
            initialText = original
        }
        val savedState = Bundle().apply {
            putBoolean("codeEditProcessDraftRequired", true)
            putString(CodeEditViewModel.STATE_PROCESS_DRAFT_TOKEN, UUID.randomUUID().toString())
            putInt(CodeEditViewModel.STATE_PROCESS_DRAFT_CURSOR, 12)
        }

        viewModel.prepareProcessRestore(savedState)
        var restoreFailed = false
        try {
            viewModel.restorePendingProcessDraft()
        } catch (_: Exception) {
            restoreFailed = true
        } finally {
            viewModel.releaseProcessState()
        }

        assertTrue("a missing checkpoint must be surfaced", restoreFailed)
        assertEquals(original, viewModel.initialText)
        assertNull(viewModel.safeDraft)
    }

    @Test
    fun transferTokenCannotDeleteAFileReachedThroughSymlink() {
        val directory = File(context.filesDir, "code-edit-transfer").apply { mkdirs() }
        val outside = File(context.filesDir, "code-transfer-boundary-${UUID.randomUUID()}").apply {
            writeText("keep")
        }
        val token = UUID.randomUUID().toString()
        val link = File(directory, token)
        try {
            Os.symlink(outside.absolutePath, link.absolutePath)
            var rejected = false
            try {
                CodeTextTransfer.delete(context, token)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("transfer API must reject a token that resolves outside its directory", rejected)
            assertTrue("the outside file must remain untouched", outside.exists())
        } finally {
            link.delete()
            outside.delete()
        }
    }
}
