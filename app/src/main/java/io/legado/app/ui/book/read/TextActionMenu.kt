package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }
    private val menuItems: List<MenuItemImpl>
    private val visibleMenuItems = arrayListOf<MenuItemImpl>()
    private val moreMenuItems = arrayListOf<MenuItemImpl>()
    private val expandTextMenu get() = context.getPrefBoolean(PreferKey.expandTextMenu)

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false

        val myMenu = MenuBuilder(context)
        val otherMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            onInitializeMenu(otherMenu)
        }
        menuItems = myMenu.visibleItems + otherMenu.visibleItems
        // 常驻行只放前 VISIBLE_MENU_ITEM_COUNT 项，其余折进「更多」。
        // coerceAtMost 兜底：当可见项不足阈值时（例如资源被裁剪）避免 subList 越界。
        val visibleCount = VISIBLE_MENU_ITEM_COUNT.coerceAtMost(menuItems.size)
        visibleMenuItems.addAll(menuItems.subList(0, visibleCount))
        moreMenuItems.addAll(menuItems.subList(visibleCount, menuItems.size))
        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter
        setOnDismissListener {
            if (!context.getPrefBoolean(PreferKey.expandTextMenu)) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.isVisible) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
                adapter.setItems(moreMenuItems)
                binding.recyclerView.gone()
                binding.recyclerViewMore.visible()
            } else {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        upMenu()
    }

    fun upMenu() {
        if (expandTextMenu) {
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.visible()
        }
    }

    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        if (expandTextMenu) {
            when {
                startTopY > 500 -> {
                    showAtLocation(
                        view,
                        Gravity.BOTTOM or Gravity.START,
                        startX,
                        windowHeight - startTopY
                    )
                }

                endBottomY - startBottomY > 500 -> {
                    showAtLocation(view, Gravity.TOP or Gravity.START, startX, startBottomY)
                }

                else -> {
                    showAtLocation(view, Gravity.TOP or Gravity.START, endX, endBottomY)
                }
            }
        } else {
            contentView.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED,
            )
            val popupHeight = contentView.measuredHeight
            when {
                startBottomY > 500 -> {
                    showAtLocation(
                        view,
                        Gravity.TOP or Gravity.START,
                        startX,
                        startTopY - popupHeight
                    )
                }

                endBottomY - startBottomY > 500 -> {
                    showAtLocation(
                        view,
                        Gravity.TOP or Gravity.START,
                        startX,
                        startBottomY
                    )
                }

                else -> {
                    showAtLocation(
                        view,
                        Gravity.TOP or Gravity.START,
                        endX,
                        endBottomY
                    )
                }
            }
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItemImpl, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItemImpl,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                textView.text = item.title
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                }
                callBack.onMenuActionFinally()
            }
            holder.itemView.setOnLongClickListener {
                if (AppConfig.contentSelectSpeakMod == 0) {
                    AppConfig.contentSelectSpeakMod = 1
                    context.toastOnUi("切换为从选择的地方开始一直朗读")
                } else {
                    AppConfig.contentSelectSpeakMod = 0
                    context.toastOnUi("切换为朗读选择内容")
                }
                true
            }
        }
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { e ->
                        AppLog.put("执行文本菜单操作出错\n$e", e, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    /**
     * Start with a menu Item order value that is high enough
     * so that your "PROCESS_TEXT" menu items appear after the
     * standard selection menu items like Cut, Copy, Paste.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                menu.add(
                    Menu.NONE, Menu.NONE,
                    menuItemOrder++, resolveInfo.loadLabel(context.packageManager)
                ).intent = createProcessTextIntentForResolveInfo(resolveInfo)
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }

    companion object {

        /**
         * 选区菜单常驻行显示的菜单项数量，超出部分折进「更多」。
         *
         * 取 6 而不是原来的 5：content_select_action.xml 里「AI 赏析」插在「字典」之后
         * 排到第 6 位，阈值同步 +1 才能让它与字典平级直接可见。
         * 这样做的好处是既有 8 项的相对顺序完全不动，也没有任何一项被挤进「更多」
         * —— 若维持 5，就必须把「朗读」这个高频 TTS 入口降级折叠。
         * 布局上安全：常驻行用的是 FlexboxLayoutManager(flexWrap=wrap)，放不下会自动换行；
         * expandTextMenu 打开时本来就一次性铺满全部 10 项，已验证换行排布可行。
         */
        private const val VISIBLE_MENU_ITEM_COUNT = 6
    }
}