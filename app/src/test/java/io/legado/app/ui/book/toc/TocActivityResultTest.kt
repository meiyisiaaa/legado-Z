package io.legado.app.ui.book.toc

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TocActivityResultTest {

    @Test
    fun `目录启动参数保留书籍和初始标签`() {
        val intent = TocActivityResult.createIntent(
            RuntimeEnvironment.getApplication(),
            TocActivityResult.Args(bookUrl = "test://book", initialTab = 1)
        )

        assertEquals("test://book", intent.getStringExtra("bookUrl"))
        assertEquals(1, intent.getIntExtra(TocActivityResult.EXTRA_INITIAL_TAB, 0))
    }
}
