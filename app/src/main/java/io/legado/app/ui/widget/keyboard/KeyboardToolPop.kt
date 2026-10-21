package io.legado.app.ui.widget.keyboard

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.PopupWindow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.dialog.ComposePopupView
import io.legado.app.utils.activity
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.windowSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import splitties.systemservices.windowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/** Compose keyboard helper surface hosted by a PopupWindow so it can follow the active IME. */
class KeyboardToolPop(
    private val context: Context,
    private val scope: CoroutineScope,
    private val rootView: View,
    private val callBack: CallBack
) : PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    ViewTreeObserver.OnGlobalLayoutListener,
    KeyboardAssistsConfig.CallBack {

    private val assists = mutableStateListOf<KeyboardAssist>()
    private var columnCount by mutableIntStateOf(AppConfig.showBoardLine.coerceAtLeast(1))
    private var assistsExpanded by mutableStateOf(true)
    private var showUndoRedo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    private var contentHeight = 0
    private var maxContentHeight by mutableIntStateOf(0)
    private var remainingImePadding = 0
    private var mIsSoftKeyBoardShowing = false
    var initialPadding = 0

    private val composeView = ComposePopupView(context)

    init {
        contentView = composeView
        composeView.setContent {
            LegadoTheme {
                KeyboardToolPopupContent(
                    assists = assists.toList(),
                    columnCount = columnCount,
                    expanded = assistsExpanded,
                    showUndoRedo = showUndoRedo,
                    onHelp = ::helpAlert,
                    onUndo = callBack::onUndoClicked,
                    onRedo = callBack::onRedoClicked,
                    onAssistClick = { callBack.sendText(it.value) },
                    onExpandedChange = { assistsExpanded = it },
                    onHeightChanged = ::onContentHeightChanged,
                    maxHeight = with(LocalDensity.current) { maxContentHeight.toDp() }
                )
            }
        }

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        inputMethodMode = INPUT_METHOD_NEEDED // Keep the popup above the soft keyboard.
        upAdapterData()
    }

    fun attachToWindow(window: Window) {
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onGlobalLayout() {
        val rect = Rect()
        rootView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = windowManager.windowSize.heightPixels
        val keyboardHeight = screenHeight - rect.bottom
        val preShowing = mIsSoftKeyBoardShowing
        if (abs(keyboardHeight) > screenHeight / 5) {
            mIsSoftKeyBoardShowing = true
            val density = context.resources.displayMetrics.density
            val location = IntArray(2)
            rootView.getLocationOnScreen(location)
            val rootBottom = location[1] + rootView.height
            val visibleHeight = (minOf(rootBottom, rect.bottom) - maxOf(location[1], rect.top))
                .coerceAtLeast(0)
            // adjustResize may already exclude the IME; only pad the part still covering the root.
            remainingImePadding = (rootBottom - rect.bottom).coerceIn(0, initialPadding.coerceAtLeast(0))
            val toolbarHeight = rootView.findViewById<View>(R.id.title_bar)?.height
                ?.takeIf { it > 0 } ?: (56 * density).roundToInt()
            // Reserve usable editor space after adjustResize, rather than sizing against the full screen.
            maxContentHeight = minOf(
                (visibleHeight * 0.4f).roundToInt(),
                visibleHeight - toolbarHeight - (96 * density).roundToInt()
            ).coerceAtLeast(0)
            if (maxContentHeight < (56 * density).roundToInt()) {
                rootView.setPadding(0, 0, 0, remainingImePadding)
                if (isShowing) dismiss()
                return
            }
            rootView.setPadding(0, 0, 0, remainingImePadding +
                minOf(maxContentHeight, maxOf(composeView.measuredHeight, contentHeight)))
            if (!isShowing) {
                showAtLocation(rootView, Gravity.BOTTOM, 0, 0)
            }
        } else {
            mIsSoftKeyBoardShowing = false
            remainingImePadding = 0
            rootView.setPadding(0, 0, 0, 0)
            if (preShowing) {
                dismiss()
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun upAdapterData() {
        scope.launch {
            appDb.keyboardAssistsDao.flowByType(0).catch {
                AppLog.put("键盘帮助浮窗获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                assists.clear()
                assists.addAll(it)
            }
        }
    }

    private fun onContentHeightChanged(size: IntSize) {
        val newHeight = size.height
        if (newHeight <= 0 || newHeight == contentHeight) return
        contentHeight = newHeight
        if (!isShowing) return
        composeView.post {
            if (isShowing && contentHeight == newHeight && mIsSoftKeyBoardShowing) {
                rootView.setPadding(0, 0, 0, remainingImePadding + minOf(maxContentHeight, newHeight))
            }
        }
    }

    private fun helpAlert() {
        val items = arrayListOf(
            SelectItem(context.getString(R.string.assists_key_config), "keyConfig")
        )
        items.addAll(callBack.helpActions())
        context.selector(context.getString(R.string.help), items) { _, selectItem, _ ->
            when (selectItem.value) {
                "keyConfig" -> config()
                else -> callBack.onHelpActionSelect(selectItem.value)
            }
        }
    }

    private fun config() {
        composeView.activity?.showDialogFragment(KeyboardAssistsConfig(this))
    }

    override fun requestLayout() {
        columnCount = AppConfig.showBoardLine.coerceAtLeast(1)
        composeView.requestLayout()
    }

    interface CallBack {

        fun helpActions(): List<SelectItem<String>> = arrayListOf()

        fun onHelpActionSelect(action: String)

        fun sendText(text: String)

        fun onUndoClicked()
        fun onRedoClicked()
    }
}

@Composable
private fun KeyboardToolPopupContent(
    assists: List<KeyboardAssist>,
    columnCount: Int,
    expanded: Boolean,
    showUndoRedo: Boolean,
    onHelp: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAssistClick: (KeyboardAssist) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onHeightChanged: (IntSize) -> Unit,
    maxHeight: Dp = 320.dp,
    modifier: Modifier = Modifier
) {
    val motion = LocalLegadoMotion.current
    val helpDescription = stringResource(
        R.string.keyboard_tool_help_description,
        stringResource(R.string.help),
        stringResource(R.string.assists_key_config)
    )
    val undoDescription = stringResource(R.string.keyboard_tool_undo)
    val redoDescription = stringResource(R.string.keyboard_tool_redo)
    val expandAssistsDescription = stringResource(R.string.keyboard_tool_expand_assists)
    val collapseAssistsDescription = stringResource(R.string.keyboard_tool_collapse_assists)
    val columns = columnCount.coerceAtLeast(1)
    val maxAssistHeight = (maxHeight - 56.dp).coerceAtLeast(0.dp)
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight).onSizeChanged(onHeightChanged),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onHelp,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = helpDescription
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_help),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (showUndoRedo) {
                    IconButton(
                        onClick = onUndo,
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = undoDescription
                        }
                    ) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
                    }
                    IconButton(
                        onClick = onRedo,
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = redoDescription
                        }
                    ) {
                        Icon(painterResource(R.drawable.ic_redo), contentDescription = null)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onExpandedChange(!expanded) },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (expanded) collapseAssistsDescription else expandAssistsDescription
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (motion.enabled) expandVertically(animationSpec = tween(motion.state))
                else EnterTransition.None,
                exit = if (motion.enabled) shrinkVertically(animationSpec = tween(motion.state))
                else ExitTransition.None
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxAssistHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    assists.chunked(columns).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { assist ->
                                KeyboardAssistKey(
                                    key = assist.key,
                                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 2.dp),
                                    onClick = { onAssistClick(assist) }
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(Modifier.weight(1f).height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardAssistKey(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = key },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = key,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun KeyboardToolPopupPreview() {
    LegadoTheme {
        KeyboardToolPopupContent(
            assists = listOf(
                KeyboardAssist(key = "${'$'}", value = "${'$'}"),
                KeyboardAssist(key = "${'$'}..${'$'}", value = "${'$'}..${'$'}"),
                KeyboardAssist(key = "REGEX", value = "REGEX"),
                KeyboardAssist(key = "source.getVariable()", value = "source.getVariable()")
            ),
            columnCount = 4,
            expanded = true,
            showUndoRedo = true,
            onHelp = {},
            onUndo = {},
            onRedo = {},
            onAssistClick = {},
            onExpandedChange = {},
            onHeightChanged = {}
        )
    }
}
