package io.legado.app.help.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class KeystoreSecretTest {
    @Test fun roundTripUsesRandomProviderIvAndDetectsTampering() {
        val alias = "legado_crypto_test_${System.nanoTime()}"
        try {
            val first = KeystoreSecret.encrypt("仅限设备的令牌", alias)
            val second = KeystoreSecret.encrypt("仅限设备的令牌", alias)
            assertNotEquals(first, second)
            assertEquals("仅限设备的令牌", KeystoreSecret.decrypt(first, alias))
            val parts = first.split('.').toMutableList()
            val ciphertext = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
            parts[1] = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
            try { KeystoreSecret.decrypt(parts.joinToString("."), alias); fail("tampering must fail") }
            catch (_: javax.crypto.AEADBadTagException) { }
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
