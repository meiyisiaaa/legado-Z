package io.legado.app.ai

import io.legado.app.ai.source.BookSourceSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * 防漂移测试：[BookSourceSchema.TOP_LEVEL_FIELDS] 必须与 `BookSource` 实体**双向零差集**。
 *
 * 为什么要这么写：`BookSourceSchema` 刻意没有 import `BookSource`
 * （那是 Room + `@Parcelize` 实体，引进来会把 Android 依赖拖进纯 JVM 单测）。
 * 手抄字段表的代价是「上游 legado 加字段时我们不知道」——
 * 于是用反射在测试期把两边对上：
 * - 实体有、白名单没有 → AI 永远不会生成该字段，能力缺失；
 * - 白名单有、实体没有 → 生成了也会被 Gson 静默丢弃，还骗过了校验器。
 * 两种都必须红灯。
 *
 * 用 `Class.forName(name, initialize = false)` 加载：只读结构、不跑静态初始化，
 * 因此不会触发实体里任何 Android 运行时调用。
 */
class BookSourceSchemaCoverageTest {

    private val bookSourceClassName = "io.legado.app.data.entities.BookSource"

    private fun bookSourceFieldNames(): List<String> {
        val clazz = Class.forName(bookSourceClassName, false, javaClass.classLoader)
        return clazz.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .filter { !it.startsWith("$") }
    }

    @Test
    fun `实体字段全部在白名单内`() {
        val leaked = bookSourceFieldNames().filter { it !in BookSourceSchema.TOP_LEVEL_FIELDS }

        assertTrue(
            "BookSource 新增了字段但 BookSourceSchema.TOP_LEVEL_FIELDS 没跟上：$leaked",
            leaked.isEmpty()
        )
    }

    @Test
    fun `白名单没有实体不存在的字段`() {
        val fields = bookSourceFieldNames().toSet()
        val ghosts = BookSourceSchema.TOP_LEVEL_FIELDS.filter { it !in fields }

        assertTrue(
            "白名单里有 BookSource 并不存在的字段（生成后会被静默丢弃）：$ghosts",
            ghosts.isEmpty()
        )
    }

    @Test
    fun `白名单与实体字段数量一致`() {
        assertEquals(
            bookSourceFieldNames().size,
            BookSourceSchema.TOP_LEVEL_FIELDS.size
        )
    }

    @Test
    fun `必填字段与核心规则字段都在白名单内`() {
        BookSourceSchema.REQUIRED_FIELDS.forEach {
            assertTrue("$it 不在白名单", it in BookSourceSchema.TOP_LEVEL_FIELDS)
        }
        BookSourceSchema.CORE_RULE_FIELDS.forEach {
            assertTrue("$it 不在白名单", it in BookSourceSchema.TOP_LEVEL_FIELDS)
        }
        BookSourceSchema.RULE_OBJECT_FIELDS.keys.forEach {
            assertTrue("$it 不在白名单", it in BookSourceSchema.TOP_LEVEL_FIELDS)
        }
    }

    @Test
    fun `关键规则条目必须是对应规则对象的合法字段`() {
        BookSourceSchema.CRITICAL_RULE_ENTRIES.forEach { (ruleName, criticals) ->
            val allowed = BookSourceSchema.RULE_OBJECT_FIELDS[ruleName]
            assertTrue("$ruleName 不是已知规则对象", allowed != null)
            criticals.forEach { critical ->
                assertTrue(
                    "$ruleName.$critical 不在该规则的字段白名单内",
                    critical in allowed!!
                )
            }
        }
    }

    @Test
    fun `预览分组除基础组外都是规则对象`() {
        BookSourceSchema.PREVIEW_GROUPS.forEach { group ->
            if (group == BookSourceSchema.PREVIEW_GROUP_BASE) return@forEach
            assertTrue(
                "$group 不是已知规则对象",
                BookSourceSchema.RULE_OBJECT_FIELDS.containsKey(group)
            )
        }
    }

    @Test
    fun `字段说明只描述白名单内的字段`() {
        val unknown = BookSourceSchema.FIELD_DESCRIPTIONS.keys
            .filter { it !in BookSourceSchema.TOP_LEVEL_FIELDS }

        assertTrue("字段说明里有白名单外的字段：$unknown", unknown.isEmpty())
    }
}
