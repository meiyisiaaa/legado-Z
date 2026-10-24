package io.legado.app.ui.ai

import android.content.Context
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.ai.batch.BatchTask
import io.legado.app.ai.batch.BatchTaskState
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemAiBatchTaskBinding
import io.legado.app.utils.getCompatColor

/**
 * 批量生成任务列表适配器。
 *
 * 只做状态渲染，不持有任何业务逻辑：条目状态由
 * [io.legado.app.ai.batch.BatchSourceGenerator] 单向推过来，这里永远是只读的。
 *
 * @param onClick 点击成功条目 → 上层把该条结果搬到结果区预览
 */
class BatchTaskAdapter(
    context: Context,
    private val onClick: (BatchTask) -> Unit
) : RecyclerAdapter<BatchTask, ItemAiBatchTaskBinding>(context) {

    private val normalColor = context.getCompatColor(R.color.secondaryText)
    private val successColor = context.getCompatColor(R.color.success)
    private val errorColor = context.getCompatColor(R.color.error)

    override fun getViewBinding(parent: ViewGroup): ItemAiBatchTaskBinding =
        ItemAiBatchTaskBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemAiBatchTaskBinding,
        item: BatchTask,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 序号对用户 +1：内部 0 起始，列表里显示第 1 条
            tvUrl.text = "${item.index + 1}. ${item.url}"
            tvState.text = stateText(item)
            tvState.setTextColor(
                when (item.state) {
                    BatchTaskState.SUCCESS -> successColor
                    BatchTaskState.FAILED -> errorColor
                    else -> normalColor
                }
            )
        }
    }

    /** 状态文案；失败时把「失败」和具体原因拼在一行，省得用户还要点开才知道为什么。 */
    private fun stateText(item: BatchTask): String {
        val stateRes = when (item.state) {
            BatchTaskState.PENDING -> R.string.ai_batch_state_pending
            BatchTaskState.RUNNING -> R.string.ai_batch_state_running
            BatchTaskState.SUCCESS -> R.string.ai_batch_state_success
            BatchTaskState.FAILED -> R.string.ai_batch_state_failed
            BatchTaskState.CANCELLED -> R.string.ai_batch_state_cancelled
        }
        val base = context.getString(stateRes)
        if (item.state != BatchTaskState.FAILED) return base
        val reason = if (item.errorMessageRes != 0) {
            context.getString(item.errorMessageRes)
        } else {
            ""
        }
        val detail = item.errorDetail.trim()
        return buildString {
            append(base)
            if (reason.isNotEmpty()) {
                append("：")
                append(reason)
            }
            if (detail.isNotEmpty()) {
                append("（")
                append(detail)
                append("）")
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemAiBatchTaskBinding) {
        binding.root.setOnClickListener {
            val item = getItemByLayoutPosition(holder.layoutPosition) ?: return@setOnClickListener
            if (item.state == BatchTaskState.SUCCESS) {
                onClick(item)
            }
        }
    }
}
