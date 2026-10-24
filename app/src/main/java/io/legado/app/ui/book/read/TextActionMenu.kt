package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.app.SearchManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.SupportMenuInflater
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.ComposePopupView
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val composeView = ComposePopupView(context)
    private val menuItems: List<MenuItemImpl>
    private val visibleMenuItems = arrayListOf<MenuItemImpl>()
    private val moreMenuItems = arrayListOf<MenuItemImpl>()
    private var displayedEntries by mutableStateOf(emptyList<TextActionMenuEntry>())
    private var expanded by mutableStateOf(false)
    private var showMore by mutableStateOf(false)
    private var popupPositionRequest = 0
    private val expandTextMenu get() = context.getPrefBoolean(PreferKey.expandTextMenu)

    init {
        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false

        val myMenu = MenuBuilder(context)
        val otherMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) onInitializeMenu(otherMenu)
        menuItems = myMenu.visibleItems + otherMenu.visibleItems

        val visibleCount = VISIBLE_MENU_ITEM_COUNT.coerceAtMost(menuItems.size)
        visibleMenuItems.addAll(menuItems.subList(0, visibleCount))
        moreMenuItems.addAll(menuItems.subList(visibleCount, menuItems.size))
        contentView = composeView
        composeView.setContent {
            LegadoTheme {
                TextActionMenuContent(
                    items = displayedEntries,
                    expanded = expanded,
                    showMore = showMore,
                    moreLabel = "更多",
                    backLabel = context.getString(R.string.back),
                    onToggleMore = ::toggleMore,
                    onSelect = ::onMenuItemSelected,
                    onLongClick = ::toggleReadAloudMode
                )
            }
        }
        setOnDismissListener {
            if (!expandTextMenu) {
                expanded = false
                displayedEntries = entriesFor(visibleMenuItems)
            }
        }
        upMenu()
    }

    fun upMenu() {
        if (expandTextMenu) {
            expanded = true
            showMore = false
            displayedEntries = entriesFor(menuItems)
        } else {
            expanded = false
            showMore = moreMenuItems.isNotEmpty()
            displayedEntries = entriesFor(visibleMenuItems)
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
        val positionRequest = ++popupPositionRequest
        contentView.alpha = 1f
        if (expandTextMenu) {
            when {
                startTopY > 500 -> showAtLocation(view, Gravity.BOTTOM or Gravity.START, startX, windowHeight - startTopY)
                endBottomY - startBottomY > 500 -> showAtLocation(view, Gravity.TOP or Gravity.START, startX, startBottomY)
                else -> showAtLocation(view, Gravity.TOP or Gravity.START, endX, endBottomY)
            }
        } else {
            val showAboveSelection = startBottomY > 500
            val x = when {
                showAboveSelection -> startX
                endBottomY - startBottomY > 500 -> startX
                else -> endX
            }
            val y = when {
                showAboveSelection -> startTopY
                endBottomY - startBottomY > 500 -> startBottomY
                else -> endBottomY
            }
            val gravity = Gravity.TOP or Gravity.START

            if (showAboveSelection) {
                contentView.alpha = 0f
                contentView.doOnAttach { popupContent ->
                    popupContent.doOnPreDraw {
                        if (positionRequest == popupPositionRequest && isShowing) {
                            popupContent.doOnLayout { laidOutContent ->
                                if (positionRequest == popupPositionRequest && isShowing) {
                                    update(x, startTopY - laidOutContent.height, -1, -1)
                                    laidOutContent.postOnAnimation {
                                        if (positionRequest == popupPositionRequest && isShowing) {
                                            laidOutContent.alpha = 1f
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            showAtLocation(view, gravity, x, y)
        }
    }

    private fun entriesFor(items: List<MenuItemImpl>) = items.map { item ->
        TextActionMenuEntry(menuItems.indexOf(item), item.title?.toString().orEmpty())
    }

    private fun toggleMore() {
        if (expanded) {
            expanded = false
            displayedEntries = entriesFor(visibleMenuItems)
        } else {
            expanded = true
            displayedEntries = entriesFor(moreMenuItems)
        }
    }

    private fun toggleReadAloudMode() {
        AppConfig.contentSelectSpeakMod = if (AppConfig.contentSelectSpeakMod == 0) 1 else 0
        val message = if (AppConfig.contentSelectSpeakMod == 1) {
            "切换为从选择的地方开始一直朗读"
        } else {
            "切换为朗读选择内容"
        }
        context.toastOnUi(message)
    }

    private fun onMenuItemSelected(index: Int) {
        menuItems.getOrNull(index)?.let { item ->
            if (!callBack.onMenuItemSelected(item.itemId)) {
                performDefaultAction(item)
            }
        }
        callBack.onMenuActionFinally()
    }

    private fun performDefaultAction(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(callBack.selectedText) }
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
            else -> item.intent?.let { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(intent)
                    }.onFailure { error ->
                        AppLog.put("执行文本菜单操作出错\n$error", error, true)
                    }
                }
            }
        }
    }

    private fun createProcessTextIntent() = Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")

    @SuppressLint("QueryPermissionsNeeded")
    private fun getSupportedActivities(): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(createProcessTextIntent(), 0)

    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent =
        createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)

    @SuppressLint("RestrictedApi")
    private fun onInitializeMenu(menu: Menu) {
        runCatching {
            var order = 100
            getSupportedActivities().forEach { info ->
                menu.add(Menu.NONE, Menu.NONE, order++, info.loadLabel(context.packageManager)).intent =
                    createProcessTextIntentForResolveInfo(info)
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
        private const val VISIBLE_MENU_ITEM_COUNT = 6
    }
}
