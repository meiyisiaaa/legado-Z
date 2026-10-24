package io.legado.app.base.adapter

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecyclerAdapterMoveRefreshTest {
    @Test
    fun latestListReplacementWaitsUntilMoveEnds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val parent = FrameLayout(context)
            val adapter = TextRowAdapter(context)
            val initial = listOf(
                Row("a", "A old", "revision 1"),
                Row("b", "B", "revision 1"),
                Row("c", "C", "revision 1")
            )
            adapter.setItems(initial)

            adapter.beginMove()
            assertTrue(adapter.areListUpdatesPaused())
            adapter.swapItem(0, 1)

            val transientRefresh = listOf(
                Row("a", "A transient", "revision 2"),
                Row("d", "D transient", "revision 2")
            )
            val latestRefresh = listOf(
                Row("a", "A latest", "revision 3"),
                Row("d", "D latest", "revision 3")
            )
            adapter.setItems(transientRefresh)
            adapter.setItems(latestRefresh)

            val movedRows = listOf(
                Row("b", "B", "revision 1"),
                Row("a", "A old", "revision 1"),
                Row("c", "C", "revision 1")
            )
            assertEquals(movedRows, adapter.getItems())
            assertEquals(
                listOf("b|B|revision 1", "a|A old|revision 1", "c|C|revision 1"),
                adapter.renderedRows(parent)
            )

            adapter.endMove()

            assertFalse(adapter.areListUpdatesPaused())
            assertEquals(latestRefresh, adapter.getItems())
            assertEquals(
                listOf("a|A latest|revision 3", "d|D latest|revision 3"),
                adapter.renderedRows(parent)
            )
        }
    }

    private data class Row(val key: String, val title: String, val revision: String)

    private class TextRowBinding(val textView: TextView) : ViewBinding {
        override fun getRoot(): TextView = textView
    }

    private class TextRowAdapter(context: Context) : RecyclerAdapter<Row, TextRowBinding>(context) {
        fun beginMove() = pauseListUpdates()

        fun endMove() = resumeListUpdates()

        fun areListUpdatesPaused() = listUpdatesPaused

        fun renderedRows(parent: ViewGroup): List<String> = getItems().indices.map { position ->
            val holder = onCreateViewHolder(parent, getItemViewType(position))
            bindViewHolder(holder, position)
            (holder.itemView as TextView).text.toString()
        }

        override fun getViewBinding(parent: ViewGroup) = TextRowBinding(TextView(context))

        override fun convert(
            holder: ItemViewHolder,
            binding: TextRowBinding,
            item: Row,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = "${item.key}|${item.title}|${item.revision}"
        }

        override fun registerListener(holder: ItemViewHolder, binding: TextRowBinding) = Unit
    }
}
