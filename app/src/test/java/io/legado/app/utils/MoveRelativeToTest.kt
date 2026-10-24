package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveRelativeToTest {

    @Test
    fun `moves a filtered row relative to visible target while keeping hidden rows ordered`() {
        val rows = listOf("a", "hidden-1", "b", "hidden-2", "c", "hidden-3")
            .map { Row(it, order = 0, metadata = "metadata-$it") }

        val moved = moveRelativeTo(rows, "a", "c", after = true, keySelector = Row::key)

        assertEquals(
            listOf("hidden-1", "b", "hidden-2", "c", "a", "hidden-3"),
            moved.map(Row::key)
        )
        assertEquals(rows.filter { it.key != "a" }, moved.filter { it.key != "a" })
        assertEquals(rows.associateBy(Row::key), moved.associateBy(Row::key))
    }

    @Test
    fun `supports placing a row before a target in canonical ascending order`() {
        val rows = listOf("a", "hidden", "b", "c").map { Row(it, 1, it) }

        assertEquals(
            listOf("a", "hidden", "c", "b"),
            moveRelativeTo(rows, "c", "b", after = false, keySelector = Row::key).map(Row::key)
        )
    }

    @Test
    fun `invalid or unchanged keys leave the current list untouched`() {
        val rows = listOf(Row("a", 3, "a"), Row("b", 3, "b"))

        assertEquals(rows, moveRelativeTo(rows, "a", "a", after = true, keySelector = Row::key))
        assertEquals(rows, moveRelativeTo(rows, "missing", "b", after = true, keySelector = Row::key))
        assertEquals(rows, moveRelativeTo(rows, "a", "missing", after = false, keySelector = Row::key))
    }

    private data class Row(val key: String, val order: Int, val metadata: String)
}
