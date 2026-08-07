package io.legado.app.ui.ai

import android.content.Context
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.ai.Citation
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemAiChatMessageBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.visible
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin

/**
 * AI 对话流适配器。
 *
 * 四种角色共用一套 item 布局，靠 **对齐方式 + 文字颜色** 区分，不做多 viewType：
 * 消息条目的结构完全一致（正文 + 可选附注），多套布局只会带来同步维护成本。
 *
 * Markdown 只对 [AiChatRole.ASSISTANT] 渲染，统一走
 * [setMarkdown][io.legado.app.utils.setMarkdown]（架构 §8 硬约束，与词典/关于页一致）。
 *
 * @param onCitationClick 点击出处 → 跳转到对应章节
 * @param onCopy 长按回答 → 复制到剪贴板
 * @param onImageLongClick Markdown 里的图片被长按（交给上层弹 PhotoDialog）
 */
class AiChatAdapter(
    context: Context,
    private val onCitationClick: (Citation) -> Unit,
    private val onCopy: (String) -> Unit,
    private val onImageLongClick: (String) -> Unit
) : RecyclerAdapter<AiChatItem, ItemAiChatMessageBinding>(context) {

    /**
     * 全适配器共用一个 Markwon。
     *
     * 不加 Glide 图片插件：AI 回答是纯文本 Markdown，
     * 引入图片加载既没必要，又会让"回答里藏个远程图片"变成一个跟踪像素。
     */
    private val markwon: Markwon by lazy {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    private val defaultTextColor = context.getCompatColor(R.color.primaryText)
    private val secondaryTextColor = context.getCompatColor(R.color.secondaryText)
    private val citationColor = context.accentColor

    override fun getViewBinding(parent: ViewGroup): ItemAiChatMessageBinding =
        ItemAiChatMessageBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemAiChatMessageBinding,
        item: AiChatItem,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 复用带来的残留：上一条可能是 Markdown，movementMethod 必须先清干净
            tvContent.movementMethod = null

            when (item.role) {
                AiChatRole.USER -> {
                    tvContent.gravity = android.view.Gravity.END
                    tvContent.setTextColor(defaultTextColor)
                    tvContent.text = item.text
                }

                AiChatRole.ASSISTANT -> {
                    tvContent.gravity = android.view.Gravity.START
                    tvContent.setTextColor(defaultTextColor)
                    tvContent.setMarkdown(
                        markwon,
                        markwon.toMarkdown(item.text),
                        imgOnLongClickListener = onImageLongClick
                    )
                }

                AiChatRole.NOTICE -> {
                    tvContent.gravity = android.view.Gravity.CENTER_HORIZONTAL
                    tvContent.setTextColor(secondaryTextColor)
                    tvContent.text = item.text
                }

                AiChatRole.CITATION -> {
                    tvContent.gravity = android.view.Gravity.START
                    tvContent.setTextColor(citationColor)
                    tvContent.text = item.text
                }
            }

            if (item.footer.isBlank()) {
                tvFooter.gone()
                tvFooter.text = ""
            } else {
                tvFooter.visible()
                tvFooter.text = item.footer
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemAiChatMessageBinding) {
        binding.root.setOnClickListener {
            val item = getItemByLayoutPosition(holder.layoutPosition) ?: return@setOnClickListener
            item.citation?.let(onCitationClick)
        }
        binding.root.setOnLongClickListener {
            val item = getItemByLayoutPosition(holder.layoutPosition)
                ?: return@setOnLongClickListener false
            if (item.role != AiChatRole.ASSISTANT) return@setOnLongClickListener false
            onCopy(item.text)
            true
        }
    }
}
