package io.legado.app.ai

import io.legado.app.ai.source.SourceJsonExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceJsonExtractorTest {

    @Test
    fun `优先选择包含书源schema字段的候选`() {
        val reply = """
            说明示例：{"foo":"1","bar":"2","baz":"3","qux":"4","quux":"5","corge":"6"}
            ```json
            ${AiTestFixtures.VALID_SOURCE_JSON}
            ```
        """.trimIndent()

        val result = SourceJsonExtractor.extract(reply)

        assertEquals("https://example.com", result.get("bookSourceUrl").asString)
    }
}
