package com.example.opdsreader.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opdsreader.data.model.OpdsEntry
import com.example.opdsreader.data.model.OpdsFeed
import com.example.opdsreader.data.network.OpdsRepository
import kotlinx.coroutines.launch
import android.util.Log
import java.net.URI
import kotlin.text.toIntOrNull

data class MainUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFeed: OpdsFeed? = null,
    val navigationStack: List<String> = listOf(),
    val serverUrl: String = "",
    val isReading: Boolean = false,
    val streamUrl: String? = null,
    val pageCount: Int = 0,
    val showLastReadDialog: Boolean = false,
    val lastReadPage: Int = 0,
    val currentEntry: OpdsEntry? = null
)

class MainViewModel(
    private val repository: OpdsRepository
) : ViewModel() {
    var uiState by mutableStateOf(MainUiState())
        private set

    fun updateServerUrl(url: String) {
        uiState = uiState.copy(serverUrl = url)
    }

    fun loadFeed(url: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    uiState = uiState.copy(isLoading = false, error = "URL格式错误: 请确保URL以http://或https://开头")  
                    return@launch
                }

                repository.getFeed(url).fold(
                    onSuccess = { feed ->
                        uiState = uiState.copy(isLoading = false, currentFeed = feed, navigationStack = uiState.navigationStack + url, serverUrl = url)
                    },
                    onFailure = { exception ->
                        uiState = uiState.copy(isLoading = false, error = exception.message ?: "未知错误")
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = "加载失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private var isNavigatingBack = false

    fun navigateBack(): Boolean {
        if (isNavigatingBack) return false

        if (uiState.isReading) {
            uiState = uiState.copy(isReading = false, streamUrl = null, pageCount = 0)
            return true
        }
        if (uiState.navigationStack.size <= 1) return false

        isNavigatingBack = true

        val newStack = uiState.navigationStack.dropLast(1)
        val previousUrl = newStack.last()

        uiState = uiState.copy(navigationStack = newStack, isLoading = true, error = null, currentFeed = null)

        viewModelScope.launch {
            try {
                repository.getFeed(previousUrl).fold(
                    onSuccess = { feed ->
                        uiState = uiState.copy(isLoading = false, currentFeed = feed)
                        isNavigatingBack = false
                    },
                    onFailure = { exception ->
                        uiState = uiState.copy(isLoading = false, error = exception.message ?: "Unknown error")
                        isNavigatingBack = false
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message ?: "Unknown error")
                isNavigatingBack = false
            }
        }
        return true
    }

    fun handleEntryClick(entry: OpdsEntry) {
        Log.d("OPDSReader", "Entry clicked: ${entry.title}, links: ${entry.links?.size ?: 0}")

        val links = entry.links ?: return

        links.forEach { link ->
            when {
                link.type == "application/x-cbz" || link.type == "application/x-zip-compressed" -> {
                    val streamLink = links.find { it.rel == "http://vaemendis.net/opds-pse/stream" }
                    Log.d("OPDSReader", "  Found streamLink (CBZ/ZIP): $streamLink")
                    Log.d("OPDSReader", "  streamLink.lastRead: ${streamLink?.lastRead}")

                    val streamHref = streamLink?.href
                    if (!streamHref.isNullOrBlank() && (streamLink.count?.toIntOrNull() ?: 0) > 0) {
                        val fullStreamUrl = streamHref.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            ?: constructFullUrl(uiState.serverUrl, streamHref)

                        Log.d("OPDSReader", "  Constructed fullStreamUrl (CBZ): $fullStreamUrl")

                        val lastReadPage = streamLink.lastRead?.toIntOrNull()
                        val pageCount = streamLink.count?.toIntOrNull() ?: 0

                        if (lastReadPage != null && lastReadPage > 0) {
                            uiState = uiState.copy(
                                showLastReadDialog = true,
                                lastReadPage = lastReadPage,
                                streamUrl = fullStreamUrl,
                                pageCount = pageCount,
                                currentEntry = entry
                            ).apply {
                                Log.d("OPDSReader", "  Updated UI state for last read dialog (CBZ), streamUrl: ${uiState.streamUrl}")
                            }
                        } else {
                            // 如果沒有 LASTREAD 值或值不正確，直接調用 startReading 從第一頁開始
                            startReading(entry, 1)
                        }
                        return
                    } else {
                        Log.d("OPDSReader", "  Invalid stream link (CBZ): href=$streamHref, count=${streamLink?.count}")
                    }
                }
                link.type?.startsWith("application/atom+xml") == true -> {
                    Log.d("OPDSReader", "  Found atom+xml link, processing as navigation")
                    link.href?.let { href ->
                        Log.d("OPDSReader", "  Handling navigation link with href: $href")
                        val fullUrl = href.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            ?: constructFullUrl(uiState.navigationStack.lastOrNull() ?: uiState.serverUrl, href)

                        if (fullUrl.isEmpty()) {
                            uiState = uiState.copy(error = "無法構建完整URL: 請先設置服務器URL")
                            return
                        }

                        Log.d("OPDSReader", "  Loading feed from URL: $fullUrl")
                        loadFeed(fullUrl)
                        Log.d("OPDSReader", "  Feed loading initiated")
                    } ?: Log.d("OPDSReader", "  Navigation link has null href")
                    return
                }
                else -> {
                    Log.d("OPDSReader", "  Unhandled link type: ${link.type}")
                }
            }
        }
        Log.d("OPDSReader", "No suitable links found in entry ${entry.title}")
    }

    internal fun constructFullUrl(baseUrl: String, relativePath: String): String {
        if (baseUrl.isEmpty()) return ""
        return try {
            val uri = URI(baseUrl)
            val scheme = uri.scheme
            val authority = uri.authority
            val base = "$scheme://$authority"
            val path = relativePath.removePrefix("/")
            "$base/$path"
        } catch (e: Exception) {
            Log.e("OPDSReader", "  解析URL失败：${e.message}，使用备用方法")
            val base = baseUrl.removeSuffix("/")
            val path = relativePath.removePrefix("/")
            "$base/$path"
        }
    }

    fun startReading(entry: OpdsEntry?, startPage: Int) {
        if (entry == null) {
            Log.e("OPDSReader", "從頭開始：找不到對應的entry，streamUrl=${uiState.streamUrl}")
            return
        }

        val links = entry.links
        val streamLink = links?.find { it.rel == "http://vaemendis.net/opds-pse/stream" }
        Log.d("OPDSReader", "開始閱讀：entry=${entry.title}, streamLink=$streamLink, href=${streamLink?.href}")

        val href = streamLink?.href
        if (href.isNullOrBlank()) {
            Log.e("OPDSReader", "無法開始閱讀：streamLink為空或href為空")
            return
        }

        var fullStreamUrl = href.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: constructFullUrl(uiState.serverUrl, href)

        fullStreamUrl = fullStreamUrl.replace("{pageNumber}", (if (startPage == 1) 0 else (startPage - 1)).toString())
        Log.d("OPDSReader", "開始閱讀：替換頁碼後的URL=$fullStreamUrl，頁碼=${if (startPage == 1) 0 else (startPage - 1)}")

        Log.d("OPDSReader", "開始閱讀：設置streamUrl=$fullStreamUrl, pageCount=${streamLink?.count?.toIntOrNull() ?: 0}, startPage=$startPage")

        uiState = uiState.copy(
            isReading = true,
            showLastReadDialog = false,
            streamUrl = fullStreamUrl,
            pageCount = streamLink?.count?.toIntOrNull() ?: 0,
            lastReadPage = startPage,
            currentEntry = entry
        )
    }

    fun dismissContinueReadingDialog() {
        uiState = uiState.copy(showLastReadDialog = false)
    }

    fun continuePreviousReading(entry: OpdsEntry?) {
        if (entry == null) {
            Log.e("OPDSReader", "繼續閱讀：找不到對應的entry，streamUrl=${uiState.streamUrl}")
            return
        }

        val links = entry.links
        val streamLink = links?.find { it.rel == "http://vaemendis.net/opds-pse/stream" }
        if (streamLink == null) {
            Log.e("OPDSReader", "繼續閱讀：找不到streamLink")
            return
        }

        val lastReadPage = streamLink.lastRead?.toIntOrNull() ?: 1
        Log.d("OPDSReader", "繼續閱讀：entry=${entry.title}, lastReadPage=$lastReadPage")

        val href = streamLink.href
        if (href.isNullOrBlank()) {
            Log.e("OPDSReader", "無法繼續閱讀：href為空")
            return
        }

        var fullStreamUrl = href.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: constructFullUrl(uiState.serverUrl, href)

        fullStreamUrl = fullStreamUrl.replace("{pageNumber}", (lastReadPage - 1).toString())
        Log.d("OPDSReader", "繼續閱讀：替換頁碼後的URL=$fullStreamUrl")

        uiState = uiState.copy(
            isReading = true,
            showLastReadDialog = false,
            streamUrl = fullStreamUrl,
            pageCount = streamLink.count?.toIntOrNull() ?: 0,
            lastReadPage = lastReadPage,
            currentEntry = entry
        )
    }

    fun resetNavigation() {
        uiState = uiState.copy(
            navigationStack = emptyList(),
            currentFeed = null,
            error = null,
            isLoading = false,
            serverUrl = ""
        )
    }
}