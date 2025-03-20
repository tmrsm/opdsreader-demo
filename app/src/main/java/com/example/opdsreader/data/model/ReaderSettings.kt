package com.example.opdsreader.data.model

enum class PageMode {
    SINGLE_PAGE,
    DOUBLE_PAGE
}

enum class ScaleMode {
    FIT_WIDTH,
    FIT_HEIGHT,
    FIT_SCREEN
}

data class ReaderSettings(
    var pageMode: PageMode = PageMode.SINGLE_PAGE,
    var scaleMode: ScaleMode = ScaleMode.FIT_SCREEN,
    var keepScreenOn: Boolean = true,
    var showPageNumber: Boolean = true,
    var autoOpenNextBook: Boolean = true,
    var fullscreenMode: Boolean = true,
    var enablePreloading: Boolean = true,
    var preloadingRange: Int = 3 // 预加载后页
) {
    companion object {
        fun getDefault() = ReaderSettings()
    }
}