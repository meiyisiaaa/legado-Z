package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.ButtonColumn
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.ReviewColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * 阅读内容视图
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var selectAble = AppConfig.textSelectAble
    val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = ChapterProvider.visibleRect
    val selectStart = TextPos(0, -1, -1)
    private val selectEnd = TextPos(0, -1, -1)
    var textPage: TextPage = TextPage()
        private set
    var isMainView = false
    var longScreenshot = false
    var reverseStartCursor = false
    var reverseEndCursor = false

    //滚动参数
    private val pageFactory get() = callBack.pageFactory
    private val pageDelegate get() = callBack.pageDelegate
    private var pageOffset = 0
    private var autoPager: AutoPager? = null
    private var isScroll = false
    private val renderRunnable by lazy { Runnable { preRenderPage() } }
    private var lastClickTime = 0L
    private var doubleClick = false
    private var accessibilityTargetsDirty = true
    private var accessibilityTargets = emptyList<AccessibilityTarget>()
    private var nextAccessibilityTargetId = 1
    private var accessibilityChangePosted = false
    private val notifyAccessibilityChange = Runnable {
        accessibilityChangePosted = false
        accessibilityHelper.invalidateRoot()
    }

    private val accessibilityHelper = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            return currentAccessibilityTargets().firstOrNull { it.bounds.contains(x.toInt(), y.toInt()) }
                ?.id ?: INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            currentAccessibilityTargets().forEach { virtualViewIds.add(it.id) }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            val target = currentAccessibilityTargets().firstOrNull { it.id == virtualViewId }
            if (target == null) {
                // ExploreByTouchHelper requires every requested virtual node to have text or a
                // content description and bounds. A provider can still receive an old ID after
                // page content changes, so return a harmless, hidden placeholder for that race.
                node.setBoundsInParent(Rect())
                node.className = "android.view.View"
                node.contentDescription = " "
                node.isEnabled = false
                node.isFocusable = false
                node.isClickable = false
                node.isVisibleToUser = false
                return
            }
            node.setBoundsInParent(target.bounds)
            node.className = when (target.kind) {
                ACCESSIBILITY_LINK -> "android.widget.TextView"
                ACCESSIBILITY_IMAGE -> "android.widget.ImageView"
                else -> "android.widget.Button"
            }
            if (target.kind == ACCESSIBILITY_LINK) {
                node.text = target.label
            } else {
                node.contentDescription = target.label
            }
            node.isFocusable = true
            node.isClickable = true
            node.isVisibleToUser = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            if (virtualViewId == HOST_ID) {
                return performReaderAction(action)
            }
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            val target = currentAccessibilityTargets().firstOrNull { it.id == virtualViewId }
                ?: return false
            return performAccessibilityClick(target.column)
        }

        override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
            super.onPopulateNodeForHost(node)
            if (!isMainView) return
            if (pageFactory.hasPrev()) {
                node.addAction(action(ACTION_PREVIOUS_PAGE, R.string.prev_page))
            }
            if (pageFactory.hasNext()) {
                node.addAction(action(ACTION_NEXT_PAGE, R.string.next_page))
            }
            node.addAction(action(ACTION_OPEN_MENU, R.string.menu))
        }
    }

    //绘制图片的paint
    val imagePaint by lazy {
        Paint().apply {
            isAntiAlias = AppConfig.useAntiAlias
        }
    }

    init {
        callBack = activity as CallBack
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
    }

    /**
     * 设置内容
     */
    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        invalidateAccessibilityTree(discardTargets = true)
        // 非滑动翻页动画需要同步重绘，不然翻页可能会出现闪烁
        if (isScroll) {
            postInvalidate()
        } else {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isMainView) return
        ChapterProvider.upViewSize(w, h)
        textPage.format()
        invalidateAccessibilityTree()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        autoPager?.onDraw(canvas)
        if (longScreenshot) {
            canvas.translate(0f, scrollY.toFloat())
        }
        check(!visibleRect.isEmpty) { "visibleRect 为空" }
        canvas.clipRect(visibleRect)
        drawPage(canvas)
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        textPage.draw(this, canvas, relativeOffset)
        if (!callBack.isScroll) return
        //滚动翻页
        if (!pageFactory.hasNext()) return
        val textPage1 = relativePage(1)
        relativeOffset += textPage.height
        textPage1.draw(this, canvas, relativeOffset)
        if (!pageFactory.hasNextPlus()) return
        relativeOffset += textPage1.height
        if (relativeOffset < ChapterProvider.visibleHeight) {
            val textPage2 = relativePage(2)
            textPage2.draw(this, canvas, relativeOffset)
        }
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
        autoPager?.computeOffset()
    }

    /**
     * 滚动事件
     * pageOffset 向上滚动 减小 向下滚动 增大
     * pageOffset 范围 0 ~ -textPage.height 大于0为上一页，小于-textPage.height为下一页
     * 以内容显示区域顶端为界，pageOffset的绝对值为textPage上方的高度
     * pageOffset + textPage.height 为 textPage 下方的高度
     */
    fun scroll(mOffset: Int) {
        pageOffset += mOffset
        if (longScreenshot) {
            scrollY += -mOffset
        }
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0
            pageDelegate?.abortAnim()
        } else if (!pageFactory.hasNext()
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = (ChapterProvider.visibleHeight - textPage.height).toInt()
            pageOffset = min(0, offset)
            pageDelegate?.abortAnim()
        } else if (pageOffset > 0) {
            if (pageFactory.moveToPrev(true)) {
                pageOffset -= textPage.height.toInt()
            } else {
                pageOffset = 0
                pageDelegate?.abortAnim()
            }
        } else if (pageOffset < -textPage.height) {
            val height = textPage.height
            if (pageFactory.moveToNext(upContent = true)) {
                pageOffset += height.toInt()
            } else {
                pageOffset = -height.toInt()
                pageDelegate?.abortAnim()
            }
        }
        invalidateAccessibilityTree()
        postInvalidate()
    }

    fun submitRenderTask() {
        renderThread.submit(renderRunnable)
    }

    private fun preRenderPage() {
        val view = this
        var invalidate = false
        pageFactory.run {
            if (hasPrev() && prevPage.render(view)) {
                invalidate = true
            }
            if (curPage.render(view)) {
                invalidate = true
            }
            if (hasNext() && nextPage.render(view) && callBack.isScroll) {
                invalidate = true
            }
            if (hasNextPlus() && nextPlusPage.render(view) && callBack.isScroll
                && relativeOffset(2) < ChapterProvider.visibleHeight
            ) {
                invalidate = true
            }
            if (invalidate) {
                postInvalidate()
                pageDelegate?.postInvalidate()
            }
        }
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        pageOffset = 0
        invalidateAccessibilityTree()
    }

    /**
     * 长按
     */
    fun longPress(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touch(x, y) { _, textPos, _, _, column ->
            when (column) {
                is ImageColumn -> callBack.onImageLongPress(x, y, column.src)
                is TextColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
                is TextHtmlColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
            }
        }
    }

    /**
     * 单击
     * @return true:已处理, false:未处理
     */
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun click(x: Float, y: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        val debounceClick = currentTime - lastClickTime < 300L //300毫秒防抖和双击
        lastClickTime = currentTime
        doubleClick = if (debounceClick) {
            !doubleClick
        } else {
            false
        }
        var handled = false
        touch(x, y) { _, textPos, textPage, textLine, column ->
            when (column) {
                is ButtonColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }

                is ReviewColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }

                is ImageColumn -> when (AppConfig.clickImgWay) {
                    "1" -> { //预览图片
                        activity?.showDialogFragment(PhotoDialog(column.src, isBook = true))
                        handled = true
                    }
                    "2" -> { //兼容处理
                        if (!debounceClick) {
                            if (ReadBook.book?.isOnLineTxt == true) {
                                val click = column.click
                                val src = column.src
                                if (!click.isNullOrBlank()) {
                                    callBack.clickImg(click, src)
                                    handled = true
                                } else {
                                    handled = callBack.oldClickImg(src)
                                }
                            }
                        }
                    }
                    "3" -> { //关闭
                        handled = false
                    }
                    "4" -> { //双击
                        if (doubleClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        } else {
                            handled = true
                        }
                    }
                    else -> { //默认点击
                        if (!debounceClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        }
                    }
                }
                is TextHtmlColumn -> {
                    column.linkUrl?.let {
                        activity?.startActivity<OpenUrlConfirmActivity> {
                            putExtra("uri", it)
                        }
                        handled = true
                    }
                }
            }
        }
        return handled
    }

    /**
     * 选择文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touchRough(x, y) { _, textPos, _, _, column ->
            if (column is TextBaseColumn) {
                column.selected = true
                select(textPos)
            }
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (selectStart.compare(textPos) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectEnd) <= 0) {
                selectStartMoveIndex(textPos)
            } else {
                touchRough(x - 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectEnd) > 0) {
                        reverseStartCursor = true
                        reverseEndCursor = false
                        selectEnd.columnIndex++
                        selectStartMoveIndex(selectEnd)
                        selectEndMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (textPos.compare(selectEnd) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectStart) >= 0) {
                selectEndMoveIndex(textPos)
            } else {
                touchRough(x + 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectStart) < 0) {
                        reverseEndCursor = true
                        reverseStartCursor = false
                        selectStart.columnIndex--
                        selectEndMoveIndex(selectStart)
                        selectStartMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * @param touched 回调
     */
    private fun touch(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                if (textLine.isTouch(x, y, relativeOffset)) {
                    for ((charIndex, textColumn) in textLine.columns.withIndex()) {
                        if (textColumn.isTouch(x)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * 文本选择专用
     * @param touched 回调
     */
    private fun touchRough(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for (lineIndex in textPage.lines.indices) {
                val textLine = textPage.getLine(lineIndex)
                if (textLine.isTouchY(y, relativeOffset)) {
                    if (textPage.doublePage) {
                        val halfWidth = width / 2
                        if (textLine.isLeftLine && x > halfWidth) {
                            continue
                        }
                        if (!textLine.isLeftLine && x < halfWidth) {
                            continue
                        }
                    }
                    val columns = textLine.columns
                    for (charIndex in columns.indices) {
                        val textColumn = columns[charIndex]
                        if (textColumn.isTouch(x)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    val isLast = columns.first().start < x
                    val charIndex = if (isLast) columns.lastIndex + 1 else -1
                    val textColumn = if (isLast) columns.last() else columns.first()
                    touched.invoke(
                        relativeOffset,
                        TextPos(relativePos, lineIndex, charIndex),
                        textPage, textLine, textColumn
                    )
                    return
                }
            }
        }
    }

    fun getCurVisiblePage(): TextPage {
        val visiblePage = TextPage()
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    visiblePage.addLine(visibleLine)
                }
            }
        }
        return visiblePage
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    return textPage.chapterIndex to visibleLine
                }
            }
        }
        return null
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectStart.relativePagePos = relativePagePos
        selectStart.lineIndex = lineIndex
        selectStart.columnIndex = max(0, charIndex)
        val textLine = relativePage(relativePagePos).getLine(lineIndex)
        val textColumn = textLine.getColumn(charIndex)
        upSelectedStart(
            if (charIndex < textLine.columns.size) textColumn.start else textColumn.end,
            textLine.lineBottom + relativeOffset(relativePagePos),
            textLine.lineTop + relativeOffset(relativePagePos)
        )
        upSelectChars()
    }

    fun selectStartMoveIndex(textPos: TextPos) = textPos.run {
        selectStartMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(
        relativePage: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectEnd.relativePagePos = relativePage
        selectEnd.lineIndex = lineIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        selectEnd.columnIndex = min(charIndex, textLine.columns.lastIndex)
        val textColumn = textLine.getColumn(charIndex)
        upSelectedEnd(
            if (charIndex > -1) textColumn.end else textColumn.start,
            textLine.lineBottom + relativeOffset(relativePage)
        )
        upSelectChars()
    }

    fun selectEndMoveIndex(textPos: TextPos) = textPos.run {
        selectEndMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    private fun upSelectChars() {
        if (!selectStart.isSelected() && !selectEnd.isSelected()) {
            return
        }
        val last = if (callBack.isScroll) 2 else 0
        val textPos = TextPos(0, 0, 0)
        for (relativePos in 0..last) {
            textPos.relativePagePos = relativePos
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                textPos.lineIndex = lineIndex
                for ((charIndex, column) in textLine.columns.withIndex()) {
                    textPos.columnIndex = charIndex
                    if (column is TextBaseColumn) {
                        val compareStart = textPos.compare(selectStart)
                        val compareEnd = textPos.compare(selectEnd)
                        column.selected = compareStart >= 0 && compareEnd <= 0
                        column.isSearchResult =
                            column.selected && callBack.isSelectingSearchResult
                        if (column.isSearchResult) {
                            textPage.searchResult.add(column)
                        }
                    }
                }
            }
        }
        postInvalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) {
        callBack.run {
            upSelectedStart(x + imgBgPaddingStart, y + headerHeight, top + headerHeight)
        }
    }

    private fun upSelectedEnd(x: Float, y: Float) {
        callBack.run {
            upSelectedEnd(x + imgBgPaddingStart, y + headerHeight)
        }
    }

    fun resetReverseCursor() {
        reverseStartCursor = false
        reverseEndCursor = false
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        val last = if (callBack.isScroll) 2 else 0
        for (relativePos in 0..last) {
            val textPage = relativePage(relativePos)
            textPage.lines.forEach { textLine ->
                textLine.columns.forEach {
                    if (it is TextBaseColumn) {
                        it.selected = false
                        if (clearSearchResult) {
                            it.isSearchResult = false
                            textPage.searchResult.remove(it)
                        }
                    }
                }
            }
        }
        selectStart.reset()
        selectEnd.reset()
        postInvalidate()
        callBack.onCancelSelect()
    }

    fun getSelectedText(): String {
        val textPos = TextPos(0, 0, 0)
        val builder = StringBuilder()
        for (relativePos in selectStart.relativePagePos..selectEnd.relativePagePos) {
            val textPage = relativePage(relativePos)
            textPos.relativePagePos = relativePos
            textPage.lines.forEachIndexed { lineIndex, textLine ->
                textPos.lineIndex = lineIndex
                textLine.columns.forEachIndexed { charIndex, column ->
                    textPos.columnIndex = charIndex
                    val compareStart = textPos.compare(selectStart)
                    val compareEnd = textPos.compare(selectEnd)
                    if (column is TextBaseColumn) {
                        when {
                            compareStart == -1 -> if (
                                selectStart.columnIndex == textLine.columns.size
                                && charIndex == textLine.columns.lastIndex
                            ) {
                                builder.append("\n")
                            }

                            compareEnd == 1 -> if (selectEnd.columnIndex == -1 && charIndex == 0) {
                                builder.append("\n")
                            }

                            compareStart >= 0 && compareEnd <= 0 -> {
                                builder.append(column.charData)
                                if (
                                    textLine.isParagraphEnd
                                    && charIndex == textLine.columns.lastIndex
                                    && compareEnd != 0
                                ) {
                                    builder.append("\n")
                                }
                            }
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    fun createBookmark(): Bookmark? {
        val page = relativePage(selectStart.relativePagePos)
        page.getTextChapter().let { chapter ->
            ReadBook.book?.let { book ->
                return book.createBookMark().apply {
                    chapterIndex = page.chapterIndex
                    chapterPos = chapter.getReadLength(page.index) +
                            page.getPosByLineColumn(selectStart.lineIndex, selectStart.columnIndex)
                    chapterName = chapter.title
                    bookText = getSelectedText()
                }
            }
        }
        return null
    }

    private fun relativeOffset(relativePos: Int): Float {
        return when (relativePos) {
            0 -> pageOffset.toFloat()
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + pageFactory.nextPage.height
        }
    }

    fun relativePage(relativePos: Int): TextPage {
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory.nextPage
            else -> pageFactory.nextPlusPage
        }
    }

    fun setAutoPager(autoPager: AutoPager?) {
        this.autoPager = autoPager
    }

    fun setIsScroll(value: Boolean) {
        isScroll = value
        invalidateAccessibilityTree()
    }

    fun invalidateAccessibilityTree() {
        invalidateAccessibilityTree(discardTargets = false)
    }

    private fun invalidateAccessibilityTree(discardTargets: Boolean) {
        if (discardTargets) {
            clearAccessibilityFocus()
            accessibilityTargets = emptyList()
        }
        accessibilityTargetsDirty = true
        if (discardTargets) {
            removeCallbacks(notifyAccessibilityChange)
            accessibilityChangePosted = false
            accessibilityHelper.invalidateRoot()
        } else if (!accessibilityChangePosted) {
            // Scroll geometry stays current without sending a subtree event on every frame.
            accessibilityChangePosted = true
            postDelayed(notifyAccessibilityChange, 100L)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(notifyAccessibilityChange)
        accessibilityChangePosted = false
        super.onDetachedFromWindow()
    }

    private fun clearAccessibilityFocus() {
        val focusedId = accessibilityHelper.accessibilityFocusedVirtualViewId
        if (focusedId != ExploreByTouchHelper.INVALID_ID) {
            accessibilityHelper.getAccessibilityNodeProvider(this)?.performAction(
                focusedId,
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                null
            )
        }
    }

    fun requestContentAccessibilityFocus(): Boolean {
        clearAccessibilityFocus()
        return performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (performReaderAction(action)) return true
        return super.performAccessibilityAction(action, arguments)
    }

    private fun performReaderAction(action: Int): Boolean {
        if (!isMainView) return false
        return when (action) {
            ACTION_PREVIOUS_PAGE -> {
                if (!pageFactory.hasPrev()) return false
                val delegate = pageDelegate ?: return false
                val reader = activity?.findViewById<ReadView>(R.id.read_view) ?: return false
                delegate.prevPageByAnim(reader.defaultAnimationSpeed)
                true
            }

            ACTION_NEXT_PAGE -> {
                if (!pageFactory.hasNext()) return false
                val delegate = pageDelegate ?: return false
                val reader = activity?.findViewById<ReadView>(R.id.read_view) ?: return false
                delegate.nextPageByAnim(reader.defaultAnimationSpeed)
                true
            }

            ACTION_OPEN_MENU -> {
                val reader = activity as? ReadView.CallBack ?: return false
                reader.showActionMenu()
                true
            }

            else -> false
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    private fun action(id: Int, labelRes: Int) = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        id,
        context.getString(labelRes)
    )

    private fun currentAccessibilityTargets(): List<AccessibilityTarget> {
        if (!accessibilityTargetsDirty) return accessibilityTargets

        val previousIds = accessibilityTargets.associateBy { it.key }
        val clip = RectF(
            max(0f, visibleRect.left),
            max(0f, visibleRect.top),
            min(width.toFloat(), visibleRect.right),
            min(height.toFloat(), visibleRect.bottom)
        )
        val targets = ArrayList<AccessibilityTarget>()
        if (!clip.isEmpty) {
            for (relativePagePos in 0..2) {
                val offset = relativeOffset(relativePagePos)
                if (relativePagePos > 0) {
                    if (!callBack.isScroll || offset >= ChapterProvider.visibleHeight) break
                    if (relativePagePos == 1 && !pageFactory.hasNext()) break
                    if (relativePagePos == 2 && !pageFactory.hasNextPlus()) break
                }
                val page = relativePage(relativePagePos)
                for ((lineIndex, line) in page.lines.withIndex()) {
                    val top = line.lineTop + offset
                    val bottom = line.lineBottom + offset
                    if (bottom <= clip.top || top >= clip.bottom) continue
                    val columns = line.columns
                    var columnIndex = 0
                    while (columnIndex < columns.size) {
                        val column = columns[columnIndex]
                        when {
                            column is TextHtmlColumn && !column.linkUrl.isNullOrBlank() -> {
                                val firstColumn = columnIndex
                                val linkUrl = column.linkUrl
                                val label = StringBuilder()
                                var left = column.start
                                var right = column.end
                                while (columnIndex < columns.size) {
                                    val linkedColumn = columns[columnIndex] as? TextHtmlColumn
                                    if (linkedColumn?.linkUrl != linkUrl) break
                                    val currentColumn = linkedColumn ?: break
                                    label.append(currentColumn.charData)
                                    left = min(left, currentColumn.start)
                                    right = max(right, currentColumn.end)
                                    columnIndex++
                                }
                                if (label.isNotBlank()) {
                                    addAccessibilityTarget(
                                        targets, previousIds, page, relativePagePos, lineIndex,
                                        firstColumn, columnIndex - 1, ACCESSIBILITY_LINK,
                                        label.toString(), column, left, right, line, offset, clip
                                    )
                                }
                            }

                            column is ImageColumn && isImageAccessibilityClickable(column) -> {
                                addAccessibilityTarget(
                                    targets, previousIds, page, relativePagePos, lineIndex,
                                    columnIndex, columnIndex, ACCESSIBILITY_IMAGE,
                                    context.getString(R.string.preview_image_by_click), column,
                                    column.start, column.end, line, offset, clip
                                )
                                columnIndex++
                            }

                            column is ButtonColumn -> {
                                addAccessibilityTarget(
                                    targets, previousIds, page, relativePagePos, lineIndex,
                                    columnIndex, columnIndex, ACCESSIBILITY_BUTTON,
                                    context.getString(R.string.custom_button), column,
                                    column.start, column.end, line, offset, clip
                                )
                                columnIndex++
                            }

                            column is ReviewColumn && column.count > 0 -> {
                                addAccessibilityTarget(
                                    targets, previousIds, page, relativePagePos, lineIndex,
                                    columnIndex, columnIndex, ACCESSIBILITY_BUTTON,
                                    context.getString(R.string.review), column,
                                    column.start, column.end, line, offset, clip
                                )
                                columnIndex++
                            }

                            else -> columnIndex++
                        }
                    }
                }
            }
        }
        accessibilityTargets = targets
        accessibilityTargetsDirty = false
        val focusedId = accessibilityHelper.accessibilityFocusedVirtualViewId
        if (focusedId != ExploreByTouchHelper.INVALID_ID && targets.none { it.id == focusedId }) {
            clearAccessibilityFocus()
        }
        return targets
    }

    private fun addAccessibilityTarget(
        targets: MutableList<AccessibilityTarget>,
        previousIds: Map<AccessibilityTargetKey, AccessibilityTarget>,
        page: TextPage,
        relativePagePos: Int,
        lineIndex: Int,
        firstColumn: Int,
        lastColumn: Int,
        kind: Int,
        label: String,
        column: BaseColumn,
        left: Float,
        right: Float,
        line: TextLine,
        offset: Float,
        clip: RectF
    ) {
        val targetBounds = RectF(left, line.lineTop + offset, right, line.lineBottom + offset)
        if (!targetBounds.intersect(clip)) return
        val bounds = Rect()
        targetBounds.roundOut(bounds)
        if (bounds.isEmpty) return
        val key = AccessibilityTargetKey(page, relativePagePos, lineIndex, firstColumn, lastColumn, kind)
        val id = previousIds[key]?.id ?: allocateAccessibilityTargetId()
        targets.add(AccessibilityTarget(id, key, bounds, kind, label, column))
    }

    private fun allocateAccessibilityTargetId(): Int {
        val id = nextAccessibilityTargetId
        nextAccessibilityTargetId = if (id == Int.MAX_VALUE) 1 else id + 1
        return id
    }

    private fun isImageAccessibilityClickable(column: ImageColumn): Boolean = when (AppConfig.clickImgWay) {
        "1" -> true
        "2" -> ReadBook.book?.isOnLineTxt == true
        "3" -> false
        else -> !column.click.isNullOrBlank()
    }

    private fun performAccessibilityClick(column: BaseColumn): Boolean = when (column) {
        is ButtonColumn, is ReviewColumn -> {
            context.toastOnUi("Button Pressed!")
            true
        }

        is TextHtmlColumn -> column.linkUrl?.let { url ->
            activity?.startActivity<OpenUrlConfirmActivity> { putExtra("uri", url) }
            true
        } ?: false

        is ImageColumn -> when (AppConfig.clickImgWay) {
            "1" -> {
                activity?.showDialogFragment(PhotoDialog(column.src, isBook = true))
                true
            }

            "2" -> if (ReadBook.book?.isOnLineTxt == true) {
                val click = column.click
                if (!click.isNullOrBlank()) {
                    callBack.clickImg(click, column.src)
                    true
                } else {
                    callBack.oldClickImg(column.src)
                }
            } else {
                false
            }

            "3" -> false
            else -> column.click?.takeIf(String::isNotBlank)?.let {
                callBack.clickImg(it, column.src)
                true
            } ?: false
        }

        else -> false
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return callBack.isScroll && pageFactory.hasNext()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longScreenshot = true
                scrollY = 0
            }

            MotionEvent.ACTION_UP -> {
                longScreenshot = false
                scrollY = 0
            }
        }
        return callBack.onLongScreenshotTouchEvent(event)
    }

    private data class AccessibilityTarget(
        val id: Int,
        val key: AccessibilityTargetKey,
        val bounds: Rect,
        val kind: Int,
        val label: String,
        val column: BaseColumn
    )

    private class AccessibilityTargetKey(
        private val page: TextPage,
        private val relativePagePos: Int,
        private val lineIndex: Int,
        private val firstColumn: Int,
        private val lastColumn: Int,
        private val kind: Int
    ) {
        override fun equals(other: Any?): Boolean = other is AccessibilityTargetKey &&
            page === other.page && relativePagePos == other.relativePagePos &&
            lineIndex == other.lineIndex && firstColumn == other.firstColumn &&
            lastColumn == other.lastColumn && kind == other.kind

        override fun hashCode(): Int {
            var result = System.identityHashCode(page)
            result = 31 * result + relativePagePos
            result = 31 * result + lineIndex
            result = 31 * result + firstColumn
            result = 31 * result + lastColumn
            return 31 * result + kind
        }
    }

    companion object {
        private const val ACCESSIBILITY_LINK = 1
        private const val ACCESSIBILITY_IMAGE = 2
        private const val ACCESSIBILITY_BUTTON = 3
        private const val ACTION_PREVIOUS_PAGE = 0x00F00001
        private const val ACTION_NEXT_PAGE = 0x00F00002
        private const val ACTION_OPEN_MENU = 0x00F00003
        private val renderThread by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "TextPageRender")
            }
        }
        private val cursorWidth = 24.dpToPx()
    }

    interface CallBack {
        val headerHeight: Int
        val imgBgPaddingStart: Int
        val pageFactory: TextPageFactory
        val pageDelegate: PageDelegate?
        val isScroll: Boolean
        var isSelectingSearchResult: Boolean
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onImageLongPress(x: Float, y: Float, src: String)
        fun onCancelSelect()
        fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean
        fun oldClickImg(src: String): Boolean
        fun clickImg(click: String, src: String)
    }
}
