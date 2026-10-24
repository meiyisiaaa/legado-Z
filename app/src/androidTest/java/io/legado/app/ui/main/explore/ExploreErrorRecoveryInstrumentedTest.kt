package io.legado.app.ui.main.explore

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExploreErrorRecoveryInstrumentedTest {

    @Test
    fun inlineExploreScriptFailureUsesErrorSentinelAndCacheClearLoadsRecoveredKinds() = runBlocking {
        val exploreUrl = """@js:infoMap.containsKey('retry')
            ? 'Fantasy::genre://fantasy&&Mystery::genre://mystery'
            : (function() { throw new Error('fixture explore failure'); })()""".trimIndent()
        val source = BookSource(
            bookSourceUrl = "https://explore-error-test.invalid/${UUID.randomUUID()}",
            bookSourceName = "Explore error recovery fixture",
            exploreUrl = exploreUrl
        )
        val infoMap = InfoMap(source.bookSourceUrl)
        ExploreAdapter.exploreInfoMapList.put(source.bookSourceUrl, infoMap)

        try {
            val failedKinds = source.exploreKinds()
            assertEquals(1, failedKinds.size)
            val errorKind = failedKinds.single()
            assertTrue(errorKind.title.startsWith("ERROR:"))
            assertTrue(errorKind.title.contains("fixture explore failure"))
            assertTrue(errorKind.url.orEmpty().contains("fixture explore failure"))

            infoMap["retry"] = "ready"
            assertTrue("The failed result stays cached until Retry clears it",
                source.exploreKinds().single().title.startsWith("ERROR:"))
            source.clearExploreKindsCache()

            assertEquals(
                listOf(
                    ExploreKind(title = "Fantasy", url = "genre://fantasy"),
                    ExploreKind(title = "Mystery", url = "genre://mystery")
                ),
                source.exploreKinds()
            )
        } finally {
            source.clearExploreKindsCache()
            ExploreAdapter.exploreInfoMapList.remove(source.bookSourceUrl)
        }
    }
}
