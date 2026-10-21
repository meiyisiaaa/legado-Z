package io.legado.app.ui.main.bookshelf

import io.legado.app.help.config.AppConfig

internal data class BookshelfAppearance(
    val marginPx: Int = 12,
    val bookName: Int = 0,
    val showUnread: Boolean = true,
    val showLastUpdateTime: Boolean = false,
    val fastScroll: Boolean = false
) {
    companion object {
        fun fromPreferences() = BookshelfAppearance(AppConfig.bookshelfMargin, AppConfig.showBookname,
            AppConfig.showUnread, AppConfig.showLastUpdateTime, AppConfig.showBookshelfFastScroller)
    }
}
