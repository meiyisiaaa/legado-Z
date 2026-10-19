package io.legado.app.help.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 设备私有的 AES-GCM 凭据。密文仍须从备份排除。 */
object KeystoreSecret {
    private fun key(alias: String): SecretKey = synchronized(this) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
        store.getKey(alias, null) as SecretKey
    }

    fun encrypt(value: String, alias: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        return listOf(cipher.iv, cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            .joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    fun decrypt(value: String, alias: String): String {
        val parts = value.split('.')
        require(parts.size == 2) { "Invalid encrypted credential" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}
