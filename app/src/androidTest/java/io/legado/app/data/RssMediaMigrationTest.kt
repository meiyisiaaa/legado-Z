package io.legado.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.entities.RssMediaDownloadTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssMediaMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate91To92PreservesArticlesAndStarsAndPersistsMediaTasks() = runBlocking {
        val name = "rss-media-migration-test"
        helper.createDatabase(name, 91).apply {
            execSQL("""INSERT INTO rssArticles (origin, link, sort, title, `order`, `read`, content)
                VALUES ('feed', 'article', 'sort', 'Article title', 7, 0, '<video src="/video.mp4"/>')""")
            execSQL("""INSERT INTO rssStars (origin, link, sort, title, starTime, content)
                VALUES ('feed', 'article', 'sort', 'Star title', 9, '<img src="/image.jpg"/>')""")
            close()
        }
        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java, name
        ).addMigrations(*DatabaseMigrations.migrations).build()
        try {
            val article = database.rssArticleDao.get("feed", "article", "sort")!!
            val star = database.rssStarDao.get("feed", "article")!!
            assertEquals("Article title", article.title)
            assertEquals("<video src=\"/video.mp4\"/>", article.content)
            assertEquals("[]", article.enclosureJson)
            assertEquals("Star title", star.title)
            assertEquals("<img src=\"/image.jpg\"/>", star.content)
            assertEquals("[]", star.enclosureJson)
            val updated = article.copy(enclosureJson = """[{"url":"https://example.org/a.mp4"}]""")
            database.rssArticleDao.update(updated)
            assertEquals(updated.enclosureJson, database.rssArticleDao.flowByOriginSort("feed", "sort").first().single().enclosureJson)
            database.rssStarDao.update(updated.toStar())
            assertEquals(updated.enclosureJson, database.rssStarDao.get("feed", "article")!!.toRssArticle().enclosureJson)
            database.rssMediaDownloadDao.upsert(RssMediaDownloadTask(
                id = "task", origin = "feed", link = "article", title = "Video",
                url = "https://example.org/a.mp4", kind = "video",
                requestUrl = """https://example.org/a.mp4,{"headers":{"Referer":"https://example.org"}}"""
            ))
            assertNotNull(database.rssMediaDownloadDao.get("task"))
            assertEquals(
                """https://example.org/a.mp4,{"headers":{"Referer":"https://example.org"}}""",
                database.rssMediaDownloadDao.get("task")!!.requestUrl
            )
        } finally {
            database.close()
        }
    }
}
