package io.legado.app.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.main.bookshelf.BookshelfContent
import io.legado.app.ui.main.bookshelf.BookshelfAppearance
import io.legado.app.ui.main.bookshelf.BookshelfOptions
import io.legado.app.ui.main.bookshelf.BookshelfOptionsContent
import io.legado.app.utils.toTimeAgo
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposeMainUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mainNavigationHidesDiscoveryAndRssWhenTheyAreNotConfigured() {
        composeRule.setContent {
            LegadoTheme {
                MainNavigation(
                    pages = listOf(0, 3),
                    selected = 0,
                    updating = 0,
                    onSelect = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.bookshelf)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.my)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.discovery)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.rss)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.bookshelf)).assertDoesNotExist()
    }

    @Test
    fun mainNavigationSelectsTheTappedPageAndShowsBookshelfUpdateBadge() {
        var selectedPage: Int? = null
        composeRule.setContent {
            LegadoTheme {
                MainNavigation(
                    pages = listOf(0, 1, 2, 3),
                    selected = 0,
                    updating = 3,
                    onSelect = { selectedPage = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.my)).performClick()
        composeRule.onNodeWithText("3").assertIsDisplayed()

        assertEquals(3, selectedPage)
    }

    @Test
    fun bookshelfShowsEmptyStateWithoutBookEntries() {
        composeRule.setContent {
            LegadoTheme {
                BookshelfContent(
                    entries = emptyList(),
                    groupId = BookGroup.IdAll,
                    layout = 1,
                    scrollRequest = 0,
                    refreshEnabled = false,
                    updating = { false },
                    onRefresh = {},
                    onOpen = {},
                    onDetails = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.bookshelf_empty)).assertIsDisplayed()
    }

    @Test
    fun bookshelfBookClickDispatchesTheSelectedBook() {
        val book = Book(
            bookUrl = "ui-test://book/sample",
            name = "测试书籍",
            author = "测试作者"
        )
        var opened: Any? = null
        composeRule.setContent {
            LegadoTheme {
                BookshelfContent(
                    entries = listOf(book),
                    groupId = BookGroup.IdAll,
                    layout = 1,
                    scrollRequest = 0,
                    refreshEnabled = false,
                    updating = { false },
                    onRefresh = {},
                    onOpen = { opened = it },
                    onDetails = {}
                )
            }
        }

        composeRule.onNodeWithText(book.name).performClick()

        assertEquals(book, opened)
    }

    @Test
    fun bookshelfGroupLongPressDispatchesGroupDetails() {
        val group = BookGroup(groupId = 42L, groupName = "测试分组")
        var details: Any? = null
        composeRule.setContent {
            LegadoTheme {
                BookshelfContent(
                    entries = listOf(group),
                    groupId = group.groupId,
                    layout = 1,
                    scrollRequest = 0,
                    refreshEnabled = false,
                    updating = { false },
                    onRefresh = {},
                    onOpen = {},
                    onDetails = { details = it }
                )
            }
        }

        composeRule.onNodeWithText(group.groupName).performTouchInput { longClick() }

        assertEquals(group, details)
    }

    @Test
    fun unreadAndNewChaptersDoNotHideTheRequestedUpdateTime() {
        val book = Book(bookUrl = "ui-test://updates", origin = "https://example.invalid",
            name = "更新提示测试", totalChapterNum = 20, durChapterIndex = 5, lastCheckCount = 2,
            latestChapterTime = System.currentTimeMillis() - 5_400_000)
        val updateTime = book.latestChapterTime.toTimeAgo()
        composeRule.setContent { LegadoTheme {
            BookshelfContent(listOf(book), BookGroup.IdAll, 1, 0, false, { false }, {}, {}, {},
                appearance = BookshelfAppearance(showUnread = true, showLastUpdateTime = true))
        } }
        composeRule.onNodeWithText(updateTime).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.bookshelf_unread_with_new_chapters, 14, 2)
        ).assertIsDisplayed()
    }

    @Test
    fun gridGroupNameFollowsTheHideBookNamePreference() {
        val group = BookGroup(groupId = 43L, groupName = "隐藏分组标题测试")
        composeRule.setContent { LegadoTheme {
            BookshelfContent(listOf(group), BookGroup.IdRoot, 3, 0, false, { false }, {}, {}, {},
                appearance = BookshelfAppearance(bookName = 1))
        } }
        composeRule.onNodeWithText(group.groupName).assertDoesNotExist()
    }

    @Test
    fun gridKeepsTheOriginalCoverTitleAndUnreadHierarchy() {
        val book = Book(bookUrl = "ui-test://grid-hierarchy", name = "原版网格层级",
            author = "不应出现在网格的作者", totalChapterNum = 20, durChapterIndex = 5,
            durChapterTitle = "不应出现在网格的当前章节", latestChapterTitle = "不应出现在网格的最新章节")
        composeRule.setContent { LegadoTheme {
            BookshelfContent(listOf(book), BookGroup.IdAll, 3, 0, false, { false }, {}, {}, {})
        } }
        composeRule.onNodeWithText(book.name).assertIsDisplayed()
        composeRule.onNodeWithText(book.author).assertDoesNotExist()
        composeRule.onNodeWithText(book.durChapterTitle!!).assertDoesNotExist()
        composeRule.onNodeWithText(book.latestChapterTitle!!).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.bookshelf_unread_chapters, 14)
        ).assertIsDisplayed()
    }

    @Test
    fun bookshelfOptionsKeepsLayoutAndSortChangesInTheSameDraft() {
        val initial = BookshelfOptions(0, 0, 0, 0, 8, true, false, false, false)
        var draft = initial
        composeRule.setContent { LegadoTheme {
            BookshelfOptionsContent(initial) { draft = it }
        } }
        composeRule.onNodeWithText(context.getString(R.string.layout_grid3)).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.bookshelf_px_2)).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.overlay)).performScrollTo().performClick()
        assertEquals(initial.copy(layout = 3, sort = 2, bookName = 2), draft)
    }
}
