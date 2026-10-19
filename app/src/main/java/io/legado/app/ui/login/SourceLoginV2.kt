package io.legado.app.ui.login

import com.google.gson.JsonObject
import io.legado.app.data.entities.rule.FlexChildStyle
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.GSON

/** The rows object returned by a single-file source's `loginUi(state)` function. */
data class SourceLoginV2Ui(val rows: List<Row>) {
    data class Row(
        val name: String = "",
        val type: String = "text",
        val action: String? = null,
        val style: FlexChildStyle? = null,
        val key: String? = null,
        val hint: String? = null,
        val value: String? = null,
        val options: List<String>? = null,
        val countdown: Int? = null
    ) {
        val label: String get() = name
    }

    companion object {
        private val supportedTypes = setOf("text", "password", "button", "label", "select", "toggle")

        fun parse(json: String?): SourceLoginV2Ui {
            if (json.isNullOrBlank()) throw NoStackTraceException("loginUi(state) 返回空结果")
            val root = runCatching { GSON.fromJson(json, JsonObject::class.java) }
                .getOrNull() ?: throw NoStackTraceException("loginUi(state) 必须返回对象")
            val rowsElement = root.get("rows")
            if (rowsElement?.isJsonArray != true) {
                throw NoStackTraceException("loginUi(state).rows 必须是数组")
            }
            val rows = rowsElement.asJsonArray.mapIndexed { index, element ->
                val row = runCatching { GSON.fromJson(element, Row::class.java) }
                    .getOrNull() ?: throw NoStackTraceException("loginUi 第 ${index + 1} 项格式无效")
                if (row.type !in supportedTypes) {
                    throw NoStackTraceException("loginUi 第 ${index + 1} 项 type 不支持: ${row.type}")
                }
                row
            }
            return SourceLoginV2Ui(rows)
        }
    }
}
