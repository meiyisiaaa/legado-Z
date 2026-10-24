package io.legado.app.ui.about

import android.content.Intent
import android.os.SystemClock
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadRecord
import io.legado.app.help.config.LocalConfig
import io.legado.app.utils.getInt
import io.legado.app.utils.putInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReadRecordQueryInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun rapidSearchAndSortingKeepLatestQueryAndClearLoading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = appDb.readRecordDao
        val uniqueId = UUID.randomUUID().toString()
        val fixture = ReadRecord(
            deviceId = "read-record-query-$uniqueId",
            bookName = "ReadRecordFixture-$uniqueId",
            readTime = 1,
            lastRead = 1
        )
        val unmatchedQuery = "ReadRecordNoMatch-$uniqueId"
        val previousSortMode = LocalConfig.getInt("readRecordSort")
        var fixtureInserted = false
        var scenario: ActivityScenario<ReadRecordActivity>? = null

        try {
            runBlocking(Dispatchers.IO) {
                assertNull(dao.getReadTime(fixture.bookName))
                dao.insert(fixture)
            }
            fixtureInserted = true

            scenario = ActivityScenario.launch<ReadRecordActivity>(
                Intent(context, ReadRecordActivity::class.java)
            )

            lateinit var emptyLabel: String
            lateinit var loadingLabel: String
            scenario!!.onActivity { activity ->
                emptyLabel = activity.getString(R.string.read_record_empty)
                loadingLabel = activity.getString(R.string.loading)
                val searchView = activity.findViewById<SearchView>(R.id.search_view)
                searchView.setQuery(fixture.bookName, false)
            }
            waitForText(fixture.bookName)

            scenario!!.onActivity { activity ->
                val searchView = activity.findViewById<SearchView>(R.id.search_view)
                searchView.setQuery(fixture.bookName.dropLast(4), false)
                searchView.setQuery(fixture.bookName, false)
                searchView.setQuery(unmatchedQuery, false)
                assertEquals(unmatchedQuery, searchView.query.toString())
            }
            waitForNoResults(emptyLabel, loadingLabel, fixture.bookName)
            composeRule.onNodeWithText(emptyLabel).assertIsDisplayed()

            scenario!!.onActivity { activity ->
                activity.findViewById<SearchView>(R.id.search_view)
                    .setQuery(fixture.bookName, false)
            }
            waitForText(fixture.bookName)

            scenario!!.onActivity { activity ->
                val searchView = activity.findViewById<SearchView>(R.id.search_view)
                searchView.setQuery(unmatchedQuery, false)
                val sortItem = MenuBuilder(activity).add(
                    0,
                    R.id.menu_sort_read_time,
                    0,
                    activity.getString(R.string.last_read_time_sort)
                )
                activity.onCompatOptionsItemSelected(sortItem)
                assertEquals(
                    unmatchedQuery,
                    searchView.query.toString()
                )
            }
            waitForNoResults(emptyLabel, loadingLabel, fixture.bookName)
            composeRule.onNodeWithText(emptyLabel).assertIsDisplayed()
            composeRule.onNodeWithText(fixture.bookName).assertDoesNotExist()
        } finally {
            scenario?.close()
            if (fixtureInserted) {
                runBlocking(Dispatchers.IO) { dao.delete(fixture) }
            }
            LocalConfig.putInt("readRecordSort", previousSortMode)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoResults(emptyLabel: String, loadingLabel: String, fixtureName: String) {
        var stableSince = 0L
        composeRule.waitUntil(timeoutMillis = 20_000) {
            val isEmpty = composeRule.onAllNodesWithText(emptyLabel).fetchSemanticsNodes().isNotEmpty()
            val isLoading = composeRule.onAllNodesWithContentDescription(loadingLabel)
                .fetchSemanticsNodes().isNotEmpty()
            val fixtureVisible = composeRule.onAllNodesWithText(fixtureName).fetchSemanticsNodes().isNotEmpty()
            if (isEmpty && !isLoading && !fixtureVisible) {
                val now = SystemClock.uptimeMillis()
                if (stableSince == 0L) stableSince = now
                now - stableSince >= 300
            } else {
                stableSince = 0L
                false
            }
        }
    }
}
