package io.legado.app.ui.code

object EditSafety {
    const val MAX_SORA_UTF8_BYTES = 256 * 1024

    fun requiresSafeEditor(text: CharSequence): Boolean =
        text.toString().toByteArray(Charsets.UTF_8).size > MAX_SORA_UTF8_BYTES
}
