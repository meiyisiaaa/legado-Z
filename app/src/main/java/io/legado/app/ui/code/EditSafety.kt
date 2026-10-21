package io.legado.app.ui.code

object EditSafety {
    const val MAX_SORA_UTF8_BYTES = 256 * 1024
    private const val MAX_ACCESSIBILITY_UTF16_UNITS = 100_000

    fun requiresSafeEditor(text: CharSequence): Boolean =
        text.toString().toByteArray(Charsets.UTF_8).size > MAX_SORA_UTF8_BYTES

    internal fun accessibilityBeforeText(text: CharSequence): CharSequence {
        if (text.length <= MAX_ACCESSIBILITY_UTF16_UNITS) return text
        var end = MAX_ACCESSIBILITY_UTF16_UNITS
        if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        return text.subSequence(0, end)
    }
}
