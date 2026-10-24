package io.legado.app.ui.config

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.legado.app.help.DirectLinkUpload
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ComposeConfigFormsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceCheckRejectsOverflowAndKeepsAUsableCheckPath() {
        var saved: SourceCheckSettings? = null
        compose.setContent { LegadoTheme {
            SourceCheckContent(SourceCheckSettings(180000, true, false, true, true, true, true, true),
                onClose = {}, onSave = { saved = it })
        } }
        compose.onNodeWithTag("source_check_search").performClick()
        compose.onNodeWithTag("source_check_discovery").performClick()
        compose.onNodeWithTag("source_check_search").assertIsOn()
        compose.onNodeWithTag("source_check_info").assertIsOff()
        compose.onNodeWithTag("source_check_category").assertIsNotEnabled()
        compose.onNodeWithTag("source_check_timeout").performTextReplacement(Long.MAX_VALUE.toString())
        compose.onNodeWithTag("source_check_save").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("source_check_timeout").performTextReplacement("12")
        compose.onNodeWithTag("source_check_save").performClick()
        compose.runOnIdle {
            assertEquals(12000L, saved?.timeout)
            assertEquals(false, saved?.content)
        }
    }

    @Test fun uploadRuleRejectsBlankUrlAndPreservesUneditedExpiry() {
        val initial = DirectLinkUpload.Rule("https://example.invalid/upload", "@json:url", "测试规则", true, 17)
        var saved: DirectLinkUpload.Rule? = null
        compose.setContent { LegadoTheme {
            DirectLinkUploadContent(initial, emptyList(), false, null, {}, {}, {}, { null },
                onSave = { saved = it }, onTest = {})
        } }
        compose.onNodeWithTag("upload_rule_url").performTextReplacement("")
        compose.onNodeWithTag("upload_rule_save").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("upload_rule_url").performTextReplacement(initial.uploadUrl)
        compose.onNodeWithTag("upload_rule_save").performClick()
        compose.runOnIdle { assertEquals(initial, saved) }
    }
}
