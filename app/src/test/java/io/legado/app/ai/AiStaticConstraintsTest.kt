package io.legado.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §7 共享约束的**静态扫描**回归（纯 JVM，读取源码文本）。
 *
 * 1. `io.legado.app.ai` 内除 [AiConfig] 外，不得 import 任何 `android.*` / `androidx.*` /
 *    `io.legado.app.data.*` —— 内核层必须零 Android 依赖才能跑 JVM 单测。
 * 2. 禁止 `new OkHttpClient()`（必须从已有 client 派生，§7.2）。
 *
 * 另见 [SourceDebugEngineTest] 对「调试引擎不含 PageFetcher」的反射守卫。
 */
class AiStaticConstraintsTest {

    private val aiRoot: File = findAiSourceRoot()

    @Test
    fun `内核层除AiConfig外不依赖Android或data实体`() {
        val banned = Regex("^import (android\\.|androidx\\.|io\\.legado\\.app\\.data\\.)")
        collectAiKotlinFiles().forEach { file ->
            if (file.name == "AiConfig.kt") return@forEach
            val violations = file.readLines()
                .mapIndexedNotNull { index, line ->
                    if (banned.containsMatchIn(line)) "${file.name}:${index + 1} $line" else null
                }
            assertTrue(
                "${file.name} 违反了「内核层零 Android/data 依赖」约束:\n" +
                    violations.joinToString("\n"),
                violations.isEmpty()
            )
        }
    }

    @Test
    fun `禁止直接new OkHttpClient实例`() {
        collectAiKotlinFiles().forEach { file ->
            val text = file.readText()
            assertFalse(
                "${file.name} 不应出现 `OkHttpClient()` 直接实例化（应从已有 client 派生）",
                text.contains("OkHttpClient()")
            )
        }
    }

    private fun collectAiKotlinFiles(): List<File> {
        val files = mutableListOf<File>()
        aiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { files.add(it) }
        assertTrue("应在 $aiRoot 下找到 ai 内核源码", files.isNotEmpty())
        return files
    }

    companion object {
        /** 从工作目录向上查找 ai 源码根（兼容 Gradle 以模块目录或仓库根作为 user.dir）。 */
        fun findAiSourceRoot(): File {
            var dir = File(System.getProperty("user.dir") ?: ".")
            repeat(6) {
                val candidate = File(dir, "src/main/java/io/legado/app/ai")
                if (candidate.isDirectory) return candidate
                dir = dir.parentFile ?: return@repeat
            }
            throw IllegalStateException(
                "找不到 ai 源码根目录 src/main/java/io/legado/app/ai" +
                    "（user.dir=${System.getProperty("user.dir")}）"
            )
        }
    }
}
