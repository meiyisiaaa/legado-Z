package io.legado.app.ai

import android.security.KeyPairGeneratorSpec
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import splitties.init.appCtx
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.security.auth.x500.X500Principal

/**
 * AI 配置的持久化壳。
 *
 * **本文件是 `io.legado.app.ai` 包中唯一允许触碰 Android API 的文件**
 * （`appCtx` / SharedPreferences），其余内核类一律纯 Kotlin。
 *
 * 存储选型（架构文档 Q3）：SharedPreferences 而非 Room / DataStore ——
 * v1 是**单实例全局配置**，语义等同 `AppConfig`，工程零 DataStore 依赖。
 *
 * apiKey 使用 Android Keystore AES-GCM 密文存储（API 23+；API 21/22 使用 RSA 包裹 AES），
 * 并在备份/导出中显式排除，见 [io.legado.app.help.storage.BackupConfig] 的
 * `ignorePrefKeys`。读取到旧版明文时会立即迁移并覆盖。
 */
object AiConfig {

    /**
     * 读取当前配置快照。任何下游（ViewModel / Engine / Client）**只能**通过此方法拿配置，
     * 禁止直接读 Pref。
     */
    fun snapshot(): AiChatOptions = AiChatOptions(
        endpoint = appCtx.getPrefString(PreferKey.aiEndpoint, "").orEmpty(),
        apiKey = readApiKey(),
        model = appCtx.getPrefString(PreferKey.aiModel, "").orEmpty(),
        temperature = readTemperature(),
        maxTokens = appCtx.getPrefInt(PreferKey.aiMaxTokens, AiChatOptions.DEFAULT_MAX_TOKENS),
        timeoutSec = appCtx.getPrefInt(PreferKey.aiTimeoutSec, AiChatOptions.DEFAULT_TIMEOUT_SEC),
        htmlMaxChars = appCtx.getPrefInt(
            PreferKey.aiHtmlMaxChars,
            AiChatOptions.DEFAULT_HTML_MAX_CHARS
        ),
        autoRepair = appCtx.getPrefBoolean(
            PreferKey.aiAutoRepair,
            AiChatOptions.DEFAULT_AUTO_REPAIR
        )
    )

    /**
     * 校验并保存配置。
     *
     * 校验不通过（存在 ERROR）时**不写入任何偏好**，原样返回问题清单供 UI 展示。
     *
     * @return 校验结果；[ValidationResult.hasError] 为 false 表示已成功落盘
     */
    fun save(options: AiChatOptions): ValidationResult {
        val normalized = options.copy(
            endpoint = AiConfigValidator.normalizeEndpoint(options.endpoint),
            apiKey = options.apiKey.trim(),
            model = options.model.trim()
        )
        val result = AiConfigValidator.validate(normalized)
        if (result.hasError()) {
            return result
        }
        val encryptedApiKey = runCatching { encryptApiKey(normalized.apiKey) }.getOrNull()
        if (encryptedApiKey == null) {
            return ValidationResult(
                listOf(
                    ValidationIssue(
                        field = AiConfigValidator.FIELD_API_KEY,
                        level = IssueLevel.ERROR,
                        messageRes = R.string.ai_issue_api_key_storage
                    )
                )
            )
        }
        appCtx.putPrefString(PreferKey.aiEndpoint, normalized.endpoint)
        appCtx.putPrefString(PreferKey.aiApiKey, encryptedApiKey)
        appCtx.putPrefString(PreferKey.aiModel, normalized.model)
        appCtx.putPrefString(PreferKey.aiTemperature, normalized.temperature.toString())
        appCtx.putPrefInt(PreferKey.aiMaxTokens, normalized.maxTokens)
        appCtx.putPrefInt(PreferKey.aiTimeoutSec, normalized.timeoutSec)
        appCtx.putPrefInt(PreferKey.aiHtmlMaxChars, normalized.htmlMaxChars)
        appCtx.putPrefBoolean(PreferKey.aiAutoRepair, normalized.autoRepair)
        return result
    }

    /**
     * 读取读者助手（Phase 2）参数快照。
     *
     * 与 [snapshot] 拆开：书源助手链路完全用不到这些字段，混在一起会让两条链路互相污染。
     * 读出即钳制，下游拿到的一定是合法值。
     */
    fun readerSnapshot(): AiReaderOptions = AiReaderOptions(
        explainMaxChars = clamp(
            appCtx.getPrefInt(
                PreferKey.aiExplainMaxChars,
                AiReaderOptions.DEFAULT_EXPLAIN_MAX_CHARS
            ),
            AiReaderOptions.MIN_EXPLAIN_MAX_CHARS,
            AiReaderOptions.MAX_EXPLAIN_MAX_CHARS
        ),
        readerAssistantEnabled = appCtx.getPrefBoolean(
            PreferKey.aiReaderAssistantEnabled,
            AiReaderOptions.DEFAULT_READER_ASSISTANT_ENABLED
        )
    )

    /**
     * 保存读者助手参数。
     *
     * 这些字段全部是「越界即钳制」而非「越界即报错」——它们不影响请求能否成功，
     * 只影响成本与效果，弹错误对话框打断用户是过度设计。故本方法无返回值。
     */
    fun saveReader(options: AiReaderOptions) {
        appCtx.putPrefInt(
            PreferKey.aiExplainMaxChars,
            clamp(
                options.explainMaxChars,
                AiReaderOptions.MIN_EXPLAIN_MAX_CHARS,
                AiReaderOptions.MAX_EXPLAIN_MAX_CHARS
            )
        )
        appCtx.putPrefBoolean(
            PreferKey.aiReaderAssistantEnabled,
            options.readerAssistantEnabled
        )
    }

    /** 阅读器 AI 入口是否可用：总开关打开 **且** 基础配置齐备。 */
    fun isReaderAssistantAvailable(): Boolean =
        readerSnapshot().readerAssistantEnabled && isConfigured()

    /**
     * 把 [value] 钳制到 `[min, max]` 闭区间。
     *
     * 声明为 `internal` 而非 `private`：单元测试需要直接覆盖边界值，
     * 而给它套一层反射或把测试塞进同文件都比暴露 internal 更糟。
     */
    internal fun clamp(value: Int, min: Int, max: Int): Int = when {
        value < min -> min
        value > max -> max
        else -> value
    }

    /** endpoint / apiKey / model 三者齐备才算配置完成。 */
    fun isConfigured(): Boolean {
        val options = snapshot()
        return options.endpoint.isNotBlank() &&
                options.apiKey.isNotBlank() &&
                options.model.isNotBlank()
    }

    /** 清空全部 AI 配置（含 apiKey）。 */
    fun clear() {
        appCtx.removePref(PreferKey.aiEndpoint)
        appCtx.removePref(PreferKey.aiApiKey)
        appCtx.removePref(PreferKey.aiModel)
        appCtx.removePref(PreferKey.aiTemperature)
        appCtx.removePref(PreferKey.aiMaxTokens)
        appCtx.removePref(PreferKey.aiTimeoutSec)
        appCtx.removePref(PreferKey.aiHtmlMaxChars)
        appCtx.removePref(PreferKey.aiAutoRepair)
        appCtx.removePref(PreferKey.aiExplainMaxChars)
        appCtx.removePref(PreferKey.aiReaderAssistantEnabled)
    }

    /**
     * temperature 以字符串存储：工程既有的 Pref 扩展没有 Float 版本，
     * 且配置页本就是文本输入，存字符串可避免精度与迁移问题。
     */
    private fun readTemperature(): Float {
        val raw = appCtx.getPrefString(PreferKey.aiTemperature, "").orEmpty()
        return raw.toFloatOrNull() ?: AiChatOptions.DEFAULT_TEMPERATURE
    }

    private fun readApiKey(): String {
        val stored = appCtx.getPrefString(PreferKey.aiApiKey, "").orEmpty()
        if (stored.isBlank()) return ""
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            return runCatching {
                saveEncryptedApiKey(stored)
                stored
            }.getOrElse {
                appCtx.removePref(PreferKey.aiApiKey)
                ""
            }
        }
        return runCatching { decryptApiKey(stored) }.getOrElse { "" }
    }

    private fun saveEncryptedApiKey(value: String) {
        val encrypted = encryptApiKey(value)
        appCtx.putPrefString(PreferKey.aiApiKey, encrypted)
    }

    private fun encryptApiKey(value: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cipher.init(Cipher.ENCRYPT_MODE, androidKeyStoreAesKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            return listOf(iv, ciphertext)
                .joinToString(ENCODED_PART_SEPARATOR) { Base64.encodeToString(it, Base64.NO_WRAP) }
                .let { AES_ENCRYPTED_PREFIX + it }
        }

        val aesKey = ByteArray(AES_KEY_BYTES).also(SecureRandom()::nextBytes)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val wrappedKey = rsaCipher(Cipher.ENCRYPT_MODE).doFinal(aesKey)
        return listOf(wrappedKey, iv, ciphertext)
            .joinToString(ENCODED_PART_SEPARATOR) { Base64.encodeToString(it, Base64.NO_WRAP) }
            .let { ENCRYPTED_PREFIX + it }
    }

    private fun decryptApiKey(stored: String): String {
        if (stored.startsWith(AES_ENCRYPTED_PREFIX)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val parts = stored.removePrefix(AES_ENCRYPTED_PREFIX)
                    .split(ENCODED_PART_SEPARATOR)
                require(parts.size == 2)
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    androidKeyStoreAesKey(),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
                return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            }
            error("AES Android Keystore keys require API 23+")
        }

        val parts = stored.removePrefix(ENCRYPTED_PREFIX).split(ENCODED_PART_SEPARATOR)
        require(parts.size == 3)
        val wrappedKey = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        val aesKey = rsaCipher(Cipher.DECRYPT_MODE).doFinal(wrappedKey)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun androidKeyStoreAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(AES_KEY_ALIAS)) {
            KeyGenerator.getInstance(AES_ALGORITHM, ANDROID_KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        AES_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setKeySize(AES_KEY_BYTES * 8)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
            keyStore.load(null)
        }
        return requireNotNull(keyStore.getKey(AES_KEY_ALIAS, null) as? SecretKey)
    }

    private fun rsaCipher(mode: Int): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(RSA_ALGORITHM, ANDROID_KEYSTORE)
            val now = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, KEY_VALID_YEARS) }
            generator.initialize(
                KeyPairGeneratorSpec.Builder(appCtx)
                    .setAlias(KEY_ALIAS)
                    .setSubject(X500Principal("CN=$KEY_ALIAS"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(now.time)
                    .setEndDate(end.time)
                    .build()
            )
            generator.generateKeyPair()
            keyStore.load(null)
        }
        val privateKey = keyStore.getKey(KEY_ALIAS, null)
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(mode, if (mode == Cipher.DECRYPT_MODE) privateKey else publicKey)
        return cipher
    }

    private const val ENCRYPTED_PREFIX = "v1:"
    private const val AES_ENCRYPTED_PREFIX = "v2:"
    private const val ENCODED_PART_SEPARATOR = "."
    private const val KEY_ALIAS = "legado_ai_api_key"
    private const val AES_KEY_ALIAS = "legado_ai_api_key_aes"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val RSA_ALGORITHM = "RSA"
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGORITHM = "AES"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_VALID_YEARS = 30
}
