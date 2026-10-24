package io.legado.app.ui.book.search

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchActivityStateInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SearchActivity>()

    private val sourceUrl = "https://search-state.invalid/${UUID.randomUUID()}"
    private val sourceScope = "状态恢复测试::$sourceUrl"
    private var fixtureInserted = false

    @Before
    fun insertLocalScriptSource() {
        assertNull(appDb.bookSourceDao.getBookSource(sourceUrl))
        appDb.bookSourceDao.insert(BookSource(
            bookSourceUrl = sourceUrl,
            bookSourceName = "状态恢复测试",
            enabledExplore = false,
            mainJs = """
                var config = { bookSourceUrl: '$sourceUrl', bookSourceName: '状态恢复测试' };
                function search(key, page) {
                    if (page > 1) return [];
                    return [{ bookUrl: '$sourceUrl/book', name: key, author: '测试' }];
                }
            """.trimIndent()
        ))
        fixtureInserted = true
    }

    @After
    fun removeLocalScriptSource() {
        composeRule.activityRule.scenario.close()
        if (fixtureInserted) appDb.bookSourceDao.delete(sourceUrl)
    }

    @Test
    fun recreationKeepsSubmittedQuerySeparateFromUnsubmittedDraft() {
        val original = prepareDraft()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val restored = ViewModelProvider(composeRule.activity)[SearchViewModel::class.java]
            assertSame(original, restored)
            assertEquals(SUBMITTED_QUERY, restored.searchKey)
            assertEquals(sourceScope, restored.searchScope.toString())
        }
        composeRule.onNode(hasSetTextAction()).assertTextEquals(DRAFT)
    }

    @Test
    fun freshViewModelRestoresSubmittedQueryAndTemporarySourceWithoutSubmittingDraft() {
        val original = prepareDraft()
        // A fresh store exercises the saved-state path used after process recreation.
        composeRule.runOnIdle { composeRule.activity.viewModelStore.clear() }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(5_000) {
            val restored = ViewModelProvider(composeRule.activity)[SearchViewModel::class.java]
            restored.searchBookLiveData.value?.any { it.name == SUBMITTED_QUERY } == true &&
                restored.isSearchLiveData.value == false
        }

        composeRule.runOnIdle {
            val restored = ViewModelProvider(composeRule.activity)[SearchViewModel::class.java]
            assertNotSame(original, restored)
            assertEquals(SUBMITTED_QUERY, restored.searchKey)
            assertEquals(sourceScope, restored.searchScope.toString())
        }
        composeRule.onNode(hasSetTextAction()).assertTextEquals(DRAFT)
    }

    private fun prepareDraft(): SearchViewModel {
        lateinit var model: SearchViewModel
        composeRule.runOnIdle {
            model = ViewModelProvider(composeRule.activity)[SearchViewModel::class.java]
            model.searchScope.update(sourceScope, postValue = false, save = false)
            model.searchKey = SUBMITTED_QUERY
            model.searchBookLiveData.postValue(listOf(SearchBook(
                bookUrl = "search-state://result", name = "状态恢复样本", author = "测试"
            )))
        }
        composeRule.waitUntil(5_000) { model.searchBookLiveData.value?.isNotEmpty() == true }
        composeRule.onNode(hasSetTextAction()).performClick().performTextReplacement(DRAFT)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("状态恢复样本").assertExists()
        return model
    }

    companion object {
        private const val SUBMITTED_QUERY = "已提交搜索"
        private const val DRAFT = "未提交草稿"
    }
}
