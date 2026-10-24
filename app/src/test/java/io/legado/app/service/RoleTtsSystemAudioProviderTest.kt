package io.legado.app.service

import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoleTtsSystemAudioProviderTest {
    @get:Rule val files = TemporaryFolder()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun narrationRestoresTheEngineDefaultAfterAnExplicitRoleVoice() = runTest {
        val provider = RoleTtsSystemAudioProvider(RuntimeEnvironment.getApplication())
        val narrator = Voice("narrator", Locale.CHINESE, 300, 300, false, emptySet())
        val character = Voice("character", Locale.CHINESE, 300, 300, false, emptySet())
        ShadowTextToSpeech.addVoice(narrator)
        ShadowTextToSpeech.addVoice(character)
        try {
            val first = async {
                provider.synthesize("test-engine", character.name, "角色", 1f, files.newFile(), "character")
            }
            runCurrent()
            val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
            val shadow = shadowOf(tts)
            tts.setVoice(narrator)
            shadow.simulateSynthesizeToFileResult(TextToSpeech.SUCCESS)
            shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            first.await()
            assertEquals(character, shadow.currentVoice)

            for (defaultName in listOf(null, "")) {
                tts.setVoice(character)
                val narration = async {
                    provider.synthesize("test-engine", defaultName, "旁白", 1f, files.newFile(), "narrator")
                }
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()
                narration.await()
                assertEquals(narrator, shadow.currentVoice)
            }
        } finally {
            provider.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
