package io.legado.app.ui.code

import android.content.Context
import android.content.Intent
import android.system.Os
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Transfers editor text without placing large payloads in Binder intents. */
object CodeTextTransfer {
    const val MAX_INLINE_LENGTH = 12_000
    const val EXTRA_TEXT_FILE_TOKEN = "codeTextFileToken"
    const val EXTRA_USE_TEXT_FILE = "codeUseTextFile"
    const val EXTRA_READ_ONLY = "codeReadOnly"

    data class IncomingText(val text: String, val ownedToken: String?)

    fun launch(
        context: Context,
        intent: Intent,
        text: CharSequence,
        start: (Intent) -> Unit
    ) {
        val token = setInput(context, intent, text)
        try {
            start(intent)
        } catch (error: Throwable) {
            token?.let { delete(context, it) }
            throw error
        }
    }

    fun setInput(context: Context, intent: Intent, text: CharSequence): String? {
        intent.removeExtra("text")
        intent.removeExtra(EXTRA_TEXT_FILE_TOKEN)
        intent.removeExtra(EXTRA_USE_TEXT_FILE)
        val value = text.toString()
        if (value.length <= MAX_INLINE_LENGTH) {
            intent.putExtra("text", value)
            return null
        }
        val token = write(context, value)
        intent.putExtra(EXTRA_TEXT_FILE_TOKEN, token)
        intent.putExtra(EXTRA_USE_TEXT_FILE, true)
        return token
    }

    fun readInput(context: Context, intent: Intent): IncomingText {
        if (intent.getBooleanExtra(EXTRA_USE_TEXT_FILE, false)) {
            val token = intent.getStringExtra(EXTRA_TEXT_FILE_TOKEN)
                ?: throw IllegalArgumentException("Missing editor text token")
            return IncomingText(read(context, token), token)
        }
        return IncomingText(
            intent.getStringExtra("text") ?: throw IllegalArgumentException("Missing editor text"),
            null
        )
    }

    fun createResult(
        context: Context,
        text: String?,
        cursorPosition: Int,
        onOutputCreated: ((String) -> Unit)? = null
    ): Intent {
        return Intent().apply {
            if (text != null) {
                if (text.length <= MAX_INLINE_LENGTH) {
                    putExtra("text", text)
                } else {
                    val token = write(context, text)
                    try {
                        onOutputCreated?.invoke(token)
                    } catch (error: Throwable) {
                        delete(context, token)
                        throw error
                    }
                    putExtra(EXTRA_TEXT_FILE_TOKEN, token)
                    putExtra(EXTRA_USE_TEXT_FILE, true)
                }
            }
            putExtra("cursorPosition", cursorPosition.coerceAtLeast(0))
        }
    }

    /** Consumes and removes a returned text file even when decoding fails. */
    fun readResult(
        context: Context,
        result: Intent?,
        onError: (Exception) -> Unit = {}
    ): String? {
        if (result == null) return null
        val token = result.getStringExtra(EXTRA_TEXT_FILE_TOKEN)
        if (token == null) return result.getStringExtra("text")
        var text: String? = null
        var failure: Exception? = null
        try {
            text = read(context, token)
        } catch (error: Exception) {
            failure = error
        }
        try {
            delete(context, token)
        } catch (error: Exception) {
            if (failure == null) failure = error
        }
        failure?.let(onError)
        return text
    }

    fun delete(context: Context, token: String) {
        val file = fileFor(context, token)
        if (file.exists() && !file.delete() && file.exists()) {
            throw IOException("Unable to remove editor text transfer")
        }
    }

    fun write(context: Context, text: String): String {
        val token = UUID.randomUUID().toString()
        val destination = fileFor(context, token)
        val partial = fileFor(context, "$token.partial")
        try {
            BufferedWriter(
                OutputStreamWriter(FileOutputStream(partial), StandardCharsets.UTF_8)
            ).use { writer -> writer.write(text) }
            if (!partial.renameTo(destination)) {
                throw IllegalStateException("Unable to finalize editor text transfer")
            }
            return token
        } catch (error: Throwable) {
            partial.delete()
            destination.delete()
            throw error
        }
    }

    /** Atomically replaces an owned transfer file while preserving its token. */
    fun writeReplacing(context: Context, token: String, text: String) {
        val destination = fileFor(context, token)
        val partial = fileFor(context, "$token.partial")
        try {
            BufferedWriter(
                OutputStreamWriter(FileOutputStream(partial), StandardCharsets.UTF_8)
            ).use { writer -> writer.write(text) }
            Os.rename(partial.absolutePath, destination.absolutePath)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    fun read(context: Context, token: String): String {
        val file = fileFor(context, token)
        BufferedReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8)).use { reader ->
            return reader.readText()
        }
    }

    private fun fileFor(context: Context, token: String): File {
        require(Regex("^[0-9a-fA-F-]{36}(?:\\.partial)?$").matches(token)) {
            "Invalid editor text token"
        }
        val root = File(context.filesDir, "code-edit-transfer")
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) {
            throw IllegalStateException("Unable to create editor transfer directory")
        }
        val canonicalRoot = root.canonicalFile
        val canonicalFiles = context.filesDir.canonicalFile
        require(canonicalRoot.path.startsWith(canonicalFiles.path + File.separator)) {
            "Editor transfer directory escaped app storage"
        }
        val file = File(canonicalRoot, token).canonicalFile
        require(file.parentFile == canonicalRoot) { "Editor text token escaped transfer directory" }
        return file
    }
}
