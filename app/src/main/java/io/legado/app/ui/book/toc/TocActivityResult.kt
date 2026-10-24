package io.legado.app.ui.book.toc

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class TocActivityResult : ActivityResultContract<String, Array<Any>?>() {

    data class Args(
        val bookUrl: String,
        val initialTab: Int = 0
    )

    override fun createIntent(context: Context, input: String): Intent {
        return createIntent(context, Args(input))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Array<Any>? {
        return parseTocResult(resultCode, intent)
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "initialTab"

        fun createIntent(context: Context, input: Args): Intent {
            return Intent(context, TocActivity::class.java)
                .putExtra("bookUrl", input.bookUrl)
                .putExtra(EXTRA_INITIAL_TAB, input.initialTab)
        }

        fun parseTocResult(resultCode: Int, intent: Intent?): Array<Any>? {
            if (resultCode == RESULT_OK) {
                intent?.let {
                    return arrayOf(
                        it.getIntExtra("index", 0),
                        it.getIntExtra("chapterPos", 0),
                        it.getBooleanExtra("chapterChanged", false),
                        it.getIntExtra("durVolumeIndex", 0),
                        it.getIntExtra("chapterInVolumeIndex", 0)
                    )
                }
            }
            return null
        }
    }
}
