package io.legado.app.ui.ai

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.legado.app.ai.RulePreviewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemRulePreviewBinding

/**
 * 书源规则预览的 RecyclerView 适配器。
 *
 * 每个 [RulePreviewGroup] 渲染为一行：组标题（资源 id 解析）+ 多行 `key = value`。
 * 纯只读展示，不注册任何点击事件。
 */
class RulePreviewAdapter(context: Context) :
    RecyclerAdapter<RulePreviewGroup, ItemRulePreviewBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemRulePreviewBinding =
        ItemRulePreviewBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemRulePreviewBinding,
        item: RulePreviewGroup,
        payloads: MutableList<Any>
    ) {
        binding.tvGroupTitle.setText(item.titleRes)
        if (item.entries.isEmpty()) {
            binding.tvEntries.visibility = View.GONE
            binding.tvEntries.text = ""
        } else {
            binding.tvEntries.visibility = View.VISIBLE
            binding.tvEntries.text = item.entries.joinToString("\n") { entry ->
                "${entry.key} = ${entry.value}"
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemRulePreviewBinding) {
        // 只读展示，无需事件监听
    }
}
