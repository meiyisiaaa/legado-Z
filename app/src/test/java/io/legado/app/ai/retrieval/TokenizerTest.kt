package io.legado.app.ai.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Tokenizer] 的行为回归。
 *
 * 重点盯住三件事：中文 bigram 切得对不对、中英边界会不会粘连、标点会不会混进词里。
 * 这三处一旦回退，检索的召回率会静默塌方而不会报任何错 —— 必须靠测试兜住。
 */
class TokenizerTest {

    @Test
    fun `中文按bigram切分`() {
        assertEquals(listOf("天下", "下无", "无双"), Tokenizer.tokenize("天下无双"))
    }

    @Test
    fun `单个汉字退化为单字词`() {
        assertEquals(listOf("剑"), Tokenizer.tokenize("剑"))
    }

    @Test
    fun `英文按空白与标点切分并小写化`() {
        assertEquals(
            listOf("hello", "world", "chapter", "3"),
            Tokenizer.tokenize("Hello, World! Chapter 3")
        )
    }

    @Test
    fun `中英混排在边界处断开不粘连`() {
        assertEquals(
            listOf("张三", "三说", "ok", "很好", "好的"),
            Tokenizer.tokenize("张三说 OK，很好的")
        )
    }

    @Test
    fun `标点与空白不会进入词表`() {
        val tokens = Tokenizer.tokenize("——「师父」，你在？")
        assertTrue(
            "词表混入了标点：$tokens",
            tokens.none { token -> token.any { !it.isLetterOrDigit() } }
        )
    }

    @Test
    fun `空串与纯标点返回空词表`() {
        assertEquals(emptyList<String>(), Tokenizer.tokenize(""))
        assertEquals(emptyList<String>(), Tokenizer.tokenize("！！！……　"))
    }

    @Test
    fun `归一化折叠非构词字符为单空格并去首尾`() {
        assertEquals("hello world", Tokenizer.normalize("  Hello,   World!  "))
        assertEquals("天下 无双", Tokenizer.normalize("天下，无双。"))
    }

    @Test
    fun `归一化保留数字与字母`() {
        assertEquals("v1 2 3", Tokenizer.normalize("V1.2-3"))
    }

    @Test
    fun `distinctTokens去重且保持首次出现顺序`() {
        // "师父师父说道" 的 bigram 序列为 师父/父师/师父/父说/说道，去重后重复的"师父"只保留首次
        assertEquals(
            listOf("师父", "父师", "父说", "说道"),
            Tokenizer.distinctTokens("师父师父说道")
        )
    }

    @Test
    fun `tokenize保留重复用于词频统计`() {
        val tokens = Tokenizer.tokenize("剑 剑 剑")
        assertEquals(3, tokens.size)
        assertEquals(3, tokens.count { it == "剑" })
    }
}
