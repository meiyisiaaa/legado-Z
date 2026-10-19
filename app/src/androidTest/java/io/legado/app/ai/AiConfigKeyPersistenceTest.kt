package io.legado.app.ai

import android.app.Application
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android Keystore and preference boundary used by [AiConfig]. */
@RunWith(AndroidJUnit4::class)
class AiConfigKeyPersistenceTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var originalAiPreferences: Map<String, Any?>

    @Before
    fun setUp() {
        preferences = ApplicationProvider.getApplicationContext<Application>().defaultSharedPreferences
        originalAiPreferences = AI_PREFERENCE_KEYS.associateWith { key ->
            preferences.all[key].let { value -> if (value is Set<*>) value.toSet() else value }
        }
    }

    @After
    fun tearDown() {
        val editor = preferences.edit()
        originalAiPreferences.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> editor.remove(key)
            }
        }
        check(editor.commit()) { "Failed to restore AI preferences after test" }
    }

    @Test
    fun snapshotDecryptsV2KeyWithoutRewritingCiphertext() {
        val result = AiConfig.save(
            AiChatOptions(
                endpoint = "https://api.example.invalid/v1",
                apiKey = TEST_API_KEY,
                model = "key-persistence-test"
            )
        )
        assertFalse("test AI config must pass validation: ${result.errors()}", result.hasError())

        val savedCiphertext = preferences.getString(PreferKey.aiApiKey, null)
        assertTrue(
            "saved API key must be Keystore-encrypted v2 data",
            savedCiphertext?.startsWith("v2:") == true
        )

        repeat(2) {
            assertEquals(TEST_API_KEY, AiConfig.snapshot().apiKey)
            assertEquals(savedCiphertext, preferences.getString(PreferKey.aiApiKey, null))
        }
    }

    companion object {
        private const val TEST_API_KEY = "synthetic-keystore-persistence-test-key"

        private val AI_PREFERENCE_KEYS = listOf(
            PreferKey.aiEndpoint,
            PreferKey.aiApiKey,
            PreferKey.aiModel,
            PreferKey.aiTemperature,
            PreferKey.aiMaxTokens,
            PreferKey.aiTimeoutSec,
            PreferKey.aiHtmlMaxChars,
            PreferKey.aiAutoRepair,
            PreferKey.aiExplainMaxChars,
            PreferKey.aiReaderAssistantEnabled
        )
    }
}
