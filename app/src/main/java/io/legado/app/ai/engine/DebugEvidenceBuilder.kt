package io.legado.app.ai.engine

import io.legado.app.ai.html.HtmlChunker
import io.legado.app.model.DebugLogSanitizer
import io.legado.app.model.DebugRunEvent
import io.legado.app.model.DebugRunSnapshot

data class DebugEvidence(
    val logs: String,
    val sampleHtml: String,
    val truncated: Boolean
) {
    fun previewText(maxHtmlChars: Int): String = buildString {
        if (logs.isNotBlank()) {
            append("【将发送的调试日志】\n")
            append(logs)
            append('\n')
        }
        val html = HtmlChunker.chunk(sampleHtml, maxHtmlChars)
        if (html.isNotBlank()) {
            append("【将发送的 HTML 片段】\n")
            append(html)
        }
        if (truncated) append("\n[内容已按长度上限截断]")
    }
}

/** Builds only the evidence the user selected, before the model client sees it. */
object DebugEvidenceBuilder {
    const val MAX_LOG_CHARS = 8_000
    const val MAX_HTML_CHARS = 20_000
    private val htmlStates = setOf(10, 20, 30, 40)

    fun build(
        snapshot: DebugRunSnapshot?,
        includeLogs: Boolean,
        includeHtml: Boolean,
        manualSampleHtml: String = ""
    ): DebugEvidence {
        val logs = StringBuilder()
        val html = StringBuilder()
        var truncated = false

        snapshot?.events.orEmpty().forEach { event ->
            when {
                event.state in htmlStates && includeHtml -> {
                    truncated = appendBounded(
                        html,
                        formatHtmlEvent(event),
                        MAX_HTML_CHARS
                    ) || truncated
                }

                event.state !in htmlStates && includeLogs -> {
                    truncated = appendBounded(
                        logs,
                        formatLogEvent(event),
                        MAX_LOG_CHARS
                    ) || truncated
                }
            }
        }

        if (includeHtml && manualSampleHtml.isNotBlank()) {
            truncated = appendBounded(
                html,
                "【手动提供的 HTML】\n${DebugLogSanitizer.sanitize(manualSampleHtml)}\n",
                MAX_HTML_CHARS
            ) || truncated
        }
        return DebugEvidence(logs.toString(), html.toString(), truncated)
    }

    private fun formatLogEvent(event: DebugRunEvent): String = buildString {
        append("[state=")
        append(event.state)
        append("] ")
        event.sourceUrl?.let {
            append(DebugLogSanitizer.sanitize(it))
            append(' ')
        }
        append(DebugLogSanitizer.sanitize(event.payload))
        append('\n')
    }

    private fun formatHtmlEvent(event: DebugRunEvent): String = buildString {
        append("【调试响应 state=")
        append(event.state)
        append("】\n")
        append(DebugLogSanitizer.sanitize(event.payload))
        append('\n')
    }

    /** Returns true when this entry had to be clipped or omitted. */
    private fun appendBounded(target: StringBuilder, value: String, limit: Int): Boolean {
        val remaining = limit - target.length
        if (remaining <= 0) return value.isNotEmpty()
        if (value.length <= remaining) {
            target.append(value)
            return false
        }
        target.append(value.take(remaining))
        return true
    }
}
