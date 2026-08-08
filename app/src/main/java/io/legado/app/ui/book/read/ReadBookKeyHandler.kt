package io.legado.app.ui.book.read

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import io.legado.app.R
import io.legado.app.constant.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.utils.Debounce
import io.legado.app.utils.LogUtils

/**
 * 处理阅读界面的键盘、音量键、鼠标滚轮和触摸事件。
 *
 * 职责：
 * - 音量键翻页
 * - 物理按键（上下页、空格）
 * - 鼠标滚轮翻页
 * - 文字选择光标移动
 *
 * 与 ReadBookActivity 解耦，便于单独测试。
 */
class ReadBookKeyHandler(
    private val activity: ReadBookActivity,
    private val nextPageDebounce: Debounce,
    private val prevPageDebounce: Debounce,
) {
    /** 物理上一页键 */
    fun isPrevKey(keyCode: Int) = activity.isPrevKey(keyCode)

    /** 物理下一页键 */
    fun isNextKey(keyCode: Int) = activity.isNextKey(keyCode)

    /** 菜单键处理：显示/隐藏菜单 */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !activity.menuLayoutIsVisible) {
                activity.binding.readMenu.runMenuIn()
                return true
            }
            if (!isDown && !activity.menuLayoutIsVisible) {
                activity.binding.readMenu.canShowMenu = true
                return true
            }
        }
        return false
    }

    /** 鼠标滚轮翻页 */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER)) {
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
                if (axisValue < 0.0f) {
                    mouseWheelPage(PageDirection.NEXT, axisValue)
                } else {
                    mouseWheelPage(PageDirection.PREV, axisValue)
                }
                return true
            }
        }
        return false
    }

    /** 按键按下 */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (activity.menuLayoutIsVisible) return false
        val longPress = event.repeatCount > 0
        when {
            isPrevKey(keyCode) -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }
            isNextKey(keyCode) -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeyPage(PageDirection.PREV, longPress)) return true
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeyPage(PageDirection.NEXT, longPress)) return true
            KeyEvent.KEYCODE_PAGE_UP -> { handleKeyPage(PageDirection.PREV, longPress); return true }
            KeyEvent.KEYCODE_PAGE_DOWN -> { handleKeyPage(PageDirection.NEXT, longPress); return true }
            KeyEvent.KEYCODE_SPACE -> { handleKeyPage(PageDirection.NEXT, longPress); return true }
        }
        return false
    }

    /** 按键松开 */
    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) return true
            }
        }
        return false
    }

    /** 文字选择触摸事件：移动左右光标 */
    fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!activity.binding.readView.isTextSelected) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> activity.textActionMenu.dismiss()
            MotionEvent.ACTION_MOVE -> {
                when (v.id) {
                    R.id.cursor_left -> if (!activity.binding.readView.curPage.getReverseStartCursor()) {
                        activity.binding.readView.curPage.selectStartMove(
                            event.rawX + activity.binding.cursorLeft.width,
                            event.rawY - activity.binding.cursorLeft.height
                        )
                    } else {
                        activity.binding.readView.curPage.selectEndMove(
                            event.rawX - activity.binding.cursorRight.width,
                            event.rawY - activity.binding.cursorRight.height
                        )
                    }
                    R.id.cursor_right -> if (activity.binding.readView.curPage.getReverseEndCursor()) {
                        activity.binding.readView.curPage.selectStartMove(
                            event.rawX + activity.binding.cursorLeft.width,
                            event.rawY - activity.binding.cursorLeft.height
                        )
                    } else {
                        activity.binding.readView.curPage.selectEndMove(
                            event.rawX - activity.binding.cursorRight.width,
                            event.rawY - activity.binding.cursorRight.height
                        )
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                activity.binding.readView.curPage.resetReverseCursor()
                activity.showTextActionMenu()
            }
        }
        return true
    }

    /** 更新文字选择开始位置光标 */
    fun upSelectedStart(x: Float, y: Float, top: Float) {
        with(activity.binding) {
            cursorLeft.x = x - cursorLeft.width
            cursorLeft.y = y
            cursorLeft.visible(true)
            textMenuPosition.x = x
            textMenuPosition.y = top
        }
    }

    /** 更新文字选择结束位置光标 */
    fun upSelectedEnd(x: Float, y: Float) {
        with(activity.binding) {
            cursorRight.x = x
            cursorRight.y = y
            cursorRight.visible(true)
        }
    }

    /** 取消文字选择 */
    fun onCancelSelect() {
        with(activity.binding) {
            cursorLeft.invisible()
            cursorRight.invisible()
            activity.textActionMenu.dismiss()
        }
    }

    /** 长截图事件委托 */
    fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean {
        return activity.binding.readView.onTouchEvent(event)
    }

    // ---------- 内部翻页逻辑 ----------

    private fun mouseWheelPage(direction: PageDirection, distance: Float) {
        if (activity.menuLayoutIsVisible || !AppConfig.mouseWheelPage) return
        if (activity.binding.readView.isScroll) {
            (activity.binding.readView.pageDelegate as? ScrollPageDelegate)
                ?.curPage?.scroll((distance * 50).toInt())
        } else {
            keyPageDebounce(direction, mouseWheel = true, longPress = false)
        }
    }

    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (!AppConfig.volumeKeyPage) return false
        if (!AppConfig.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) return false
        handleKeyPage(direction, longPress)
        return true
    }

    private fun handleKeyPage(direction: PageDirection, longPress: Boolean) {
        if (AppConfig.keyPageOnLongPress || direction == PageDirection.NONE) {
            keyPage(direction)
        } else {
            keyPageDebounce(direction, longPress = longPress)
        }
    }

    private fun keyPageDebounce(
        direction: PageDirection,
        mouseWheel: Boolean = false,
        longPress: Boolean
    ) {
        if (longPress) return
        nextPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        prevPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        when (direction) {
            PageDirection.NEXT -> nextPageDebounce.invoke()
            PageDirection.PREV -> prevPageDebounce.invoke()
            else -> {}
        }
    }

    private fun keyPage(direction: PageDirection) {
        activity.binding.readView.cancelSelect()
        activity.binding.readView.pageDelegate?.isCancel = false
        activity.binding.readView.pageDelegate?.keyTurnPage(direction)
    }
}
