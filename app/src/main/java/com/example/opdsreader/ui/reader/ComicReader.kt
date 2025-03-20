package com.example.opdsreader.ui.reader

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.example.opdsreader.data.model.PageMode
import com.example.opdsreader.data.model.ReaderSettings
import com.example.opdsreader.di.ImageLoaderModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@OptIn(coil.annotation.ExperimentalCoilApi::class)
class ImagePreloader(private val context: Context, private val imageLoader: ImageLoader) {
    private val preloadJobs = mutableMapOf<String, Job>()
    private val preloadedUrls = mutableSetOf<String>()
    private val imageAspectRatios = mutableMapOf<String, Float>()

    private fun isMemoryNearlyFull(threshold: Float = 0.8f): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val usedPercentage = 1 - (memoryInfo.availMem.toFloat() / memoryInfo.totalMem.toFloat())
            Log.d("ImagePreloader", "Memory usage: ${(usedPercentage * 100).toInt()}%")
            usedPercentage > threshold
        } catch (e: Exception) {
            Log.e("ImagePreloader", "Error checking memory status", e)
            false
        }
    }

    private fun preloadImage(url: String) {
        if (url in preloadedUrls) {
            Log.d("ImagePreloader", "Image already preloaded: $url")
            return
        }
        preloadJobs[url]?.cancel()
        preloadJobs[url] = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ImagePreloader", "Preloading image: $url")
                if (imageLoader.memoryCache?.get(MemoryCache.Key(url)) != null) {
                    Log.d("ImagePreloader", "Image already in cache: $url")
                    preloadedUrls.add(url)
                    return@launch
                }
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .allowHardware(false)
                    .listener(
                        onSuccess = { _, result ->
                            imageAspectRatios[url] = result.drawable.intrinsicWidth.toFloat() / result.drawable.intrinsicHeight.toFloat()
                            Log.d("ImagePreloader", "✅ Successfully cached image: $url, AspectRatio: ${imageAspectRatios[url]}")
                        },
                        onError = { _, error ->
                            Log.e("ImagePreloader", "❌ Failed to cache image: $url, error: ${error.throwable}")
                        }
                    )
                    .build()
                imageLoader.execute(request)
                preloadedUrls.add(url)
                Log.d("ImagePreloader", "Preload request completed for: $url")
            } catch (e: Exception) {
                Log.e("ImagePreloader", "Error preloading image: $url", e)
            } finally {
                preloadJobs.remove(url)
            }
        }
    }

    private fun clearPreloadCache(pagesToKeep: Set<String> = emptySet()) {
        // Use iterator to avoid ConcurrentModificationException and for API < 24
        val it = preloadJobs.entries.iterator()
        while (it.hasNext()) {
            val (url, job) = it.next()
            if (url !in pagesToKeep) {
                job.cancel()
                it.remove()
            }
        }

        preloadedUrls.removeAll { it !in pagesToKeep }
        imageAspectRatios.keys.removeAll { it !in pagesToKeep }
        Log.d("ImagePreloader", "Cleared preload cache, kept ${pagesToKeep.size} pages")
    }

    fun preloadPagesAround(baseUrl: String, streamUrl: String, currentPage: Int, pageCount: Int, preloadRange: Int) {
        val currentPageUrl = buildPageUrl(baseUrl, streamUrl, currentPage)
        val nextPageUrl = if (currentPage < pageCount) buildPageUrl(baseUrl, streamUrl, currentPage + 1) else null
        val prevPageUrl = if (currentPage > 1) buildPageUrl(baseUrl, streamUrl, currentPage - 1) else null

        val pagesToKeep = mutableSetOf(currentPageUrl).apply {
            nextPageUrl?.let { add(it) }
            prevPageUrl?.let { add(it) }
        }

        if (isMemoryNearlyFull()) {
            Log.d("ImagePreloader", "Memory nearly full, clearing preload cache but keeping essential pages")
            clearPreloadCache(pagesToKeep)
        }

        preloadImage(currentPageUrl)
        Log.d("ImagePreloader", "Preloaded current page: $currentPage, URL: $currentPageUrl")

        ((currentPage + 1).coerceAtMost(pageCount)..(currentPage + preloadRange).coerceAtMost(pageCount)).forEach {
            preloadImage(buildPageUrl(baseUrl, streamUrl, it))
        }

        (1.coerceAtLeast(currentPage - preloadRange) until currentPage).forEach {
            preloadImage(buildPageUrl(baseUrl, streamUrl, it))
        }
    }

    internal fun buildPageUrl(baseUrl: String, streamUrl: String, page: Int): String {
        val adjustedPage = (page - 1).coerceAtLeast(0)
        val pageUrl = if (streamUrl.contains("pageNumber=")) {
            streamUrl.replace(Regex("pageNumber=\\d+"), "pageNumber=$adjustedPage")
        } else if (streamUrl.contains("{pageNumber}")) {
            streamUrl.replace("{pageNumber}", adjustedPage.toString())
        } else {
            streamUrl
        }
        return try {
            if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) {
                pageUrl
            } else {
                "$baseUrl/${pageUrl.removePrefix("/")}"
            }
        } catch (e: Exception) {
            Log.e("ImagePreloader", "Error parsing URL: baseUrl=$baseUrl, pageUrl=$pageUrl", e)
            "$baseUrl/${pageUrl.removePrefix("/")}"
        }
    }

    fun getAspectRatio(url: String): Float? {
        return imageAspectRatios[url]
    }
}

@Composable
@coil.annotation.ExperimentalCoilApi
fun ComicReader(
    key: String,
    streamUrl: String,
    pageCount: Int,
    onClose: () -> Unit,
    onNextBook: () -> Unit = {},
    onPreviousBook: () -> Unit = {},
    startPage: Int = 1,
    baseUrl: String = "http://localhost/"
) {
    var currentPage by remember(key) { mutableIntStateOf(startPage) }
    val context = LocalContext.current
    val imageLoader = remember { ImageLoaderModule.provideImageLoader(context) }
    var showSettings by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(ReaderSettings.getDefault()) }
    val imagePreloader = remember { ImagePreloader(context, imageLoader) }
    val view = LocalView.current
    var imageAspectRatio by remember { mutableFloatStateOf(0f) }

    DisposableEffect(view, currentPage) {
        val window = (context as? Activity)?.window
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            window?.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(this, true)
                WindowInsetsControllerCompat(this, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        val imageUrl = remember(streamUrl, currentPage) {
            val adjustedPage = (currentPage - 1).coerceAtLeast(0)
            val pageUrl = if (streamUrl.contains("pageNumber=")) {
                streamUrl.replace(Regex("pageNumber=\\d+"), "pageNumber=$adjustedPage")
            } else if (streamUrl.contains("{pageNumber}")) {
                streamUrl.replace("{pageNumber}", adjustedPage.toString())
            } else {
                streamUrl
            }
            try {
                if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) {
                    pageUrl
                } else {
                    "$baseUrl/${pageUrl.removePrefix("/")}"
                }
            } catch (e: Exception) {
                Log.e("ComicReader", "Error parsing URL: baseUrl=$baseUrl, pageUrl=$pageUrl", e)
                "$baseUrl/${pageUrl.removePrefix("/")}"
            }
        }

        Log.d("ComicReader", "Loading image from URL: $imageUrl")

        LaunchedEffect(currentPage) {
            if (settings.enablePreloading) {
                Log.d("ComicReader", "Preloading pages around $currentPage with range ${settings.preloadingRange}")
                imagePreloader.preloadPagesAround(baseUrl, streamUrl, currentPage, pageCount, settings.preloadingRange)
            }
        }

        //var shouldShowDoublePage by remember(key1 = currentPage, key2 = settings.pageMode) { mutableStateOf(false) }

        // 預先計算下一頁的 URL
        val nextPageImageUrl = remember(baseUrl, streamUrl, currentPage, pageCount) {
            if (currentPage < pageCount) {
                imagePreloader.buildPageUrl(baseUrl, streamUrl, currentPage + 1)
            } else null
        }

        // 獨立的函數來決定是否顯示雙頁
        fun computeShouldShowDoublePage(): Boolean {
            if (settings.pageMode == PageMode.SINGLE_PAGE) return false
            if (currentPage <= 1 || currentPage >= pageCount) return false

            // 檢查當前頁
            val currentAspectRatio = imagePreloader.getAspectRatio(imageUrl) ?: return false.also {
                Log.d("ComicReader", "Could not get aspect ratio for current page ($currentPage).")
            }
            if (currentAspectRatio > 1.0f) {
                Log.d("ComicReader", "Current page ($currentPage) is wide, using single page mode.")
                return false
            }

            // 檢查下一頁
            if (nextPageImageUrl != null) {
                val nextAspectRatio = imagePreloader.getAspectRatio(nextPageImageUrl) ?: return false.also {
                    Log.d("ComicReader", "Could not get aspect ratio for next page (${currentPage + 1}).")
                }
                if (nextAspectRatio > 1.0f) {
                    Log.d("ComicReader", "Next page (${currentPage + 1}) is wide, using single page mode.")
                    return false
                }
            }
            return true
        }

        // 當 imageUrl 或 nextPageImageUrl 改變時，重新計算 shouldShowDoublePage
        //LaunchedEffect(imageUrl, nextPageImageUrl, settings.pageMode) {
        //    shouldShowDoublePage = computeShouldShowDoublePage()
        //}
        val shouldShowDoublePage = remember(settings.pageMode, imageUrl, nextPageImageUrl) {
            computeShouldShowDoublePage()
        }

        if (!shouldShowDoublePage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(false)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .placeholderMemoryCacheKey(imageUrl)
                    .allowHardware(false)
                    .allowRgb565(true)
                    .listener(
                        onStart = {
                            val isCached = imageLoader.memoryCache?.get(MemoryCache.Key(imageUrl)) != null
                            val diskCached = imageLoader.diskCache?.openSnapshot(imageUrl)?.use { true } ?: false
                            isLoading = !(isCached || diskCached)
                            Log.d("ComicReader", "Loading started for page $currentPage, URL: $imageUrl, Cached: ${isCached || diskCached}")
                        },
                        onSuccess = { _, result ->
                            isLoading = false
                            error = null
                            val newAspectRatio = result.drawable.intrinsicWidth.toFloat() / result.drawable.intrinsicHeight.toFloat()
                            if (imageAspectRatio != newAspectRatio) {
                                imageAspectRatio = newAspectRatio
                                if (settings.enablePreloading) {
                                    imagePreloader.preloadPagesAround(baseUrl, streamUrl, currentPage, pageCount, settings.preloadingRange)
                                }
                                Log.d("ComicReader", "Successfully loaded page $currentPage, URL: $imageUrl, AspectRatio: $imageAspectRatio")
                                //shouldShowDoublePage = shouldRenderAsDoublePage()
                            }
                        },
                        onError = { _, throwable ->
                            isLoading = false
                            error = throwable.throwable.localizedMessage
                            Log.e("ComicReader", "Error loading page $currentPage: ${throwable.throwable}", throwable.throwable)
                        }
                    )
                    .build(),
                contentDescription = "Comic page $currentPage",
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentScale = ContentScale.Fit,
                imageLoader = imageLoader
            )
        } else {
            Layout(
                content = {
                    nextPageImageUrl?.let { nextUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(nextUrl)
                                .crossfade(false)
                                .memoryCacheKey(nextUrl)
                                .diskCacheKey(nextUrl)
                                .placeholderMemoryCacheKey(nextUrl)
                                .allowHardware(false)
                                .allowRgb565(true)
                                .listener(
                                    onStart = {
                                        val isCached = imageLoader.memoryCache?.get(MemoryCache.Key(nextUrl)) != null
                                        val diskCached = imageLoader.diskCache?.openSnapshot(nextUrl)?.use { true } ?: false
                                        isLoading = !(isCached || diskCached)
                                        imagePreloader.getAspectRatio(nextUrl)?.let {
                                            Log.d("ComicReader", "Using preloaded aspect ratio for next page: $it")
                                        }
                                    },
                                    onSuccess = { _, _ ->
                                        isLoading = false
                                        error = null
                                        if (settings.enablePreloading) {
                                            Log.d("ComicReader", "Successfully loaded next page")
                                        }
                                    },
                                    onError = { _, throwable ->
                                        isLoading = false
                                        error = throwable.throwable.localizedMessage
                                    }
                                )
                                .build(),
                            contentDescription = "Comic page ${currentPage + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White),
                            contentScale = ContentScale.Fit,
                            imageLoader = imageLoader
                        )
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(false)
                            .memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            .placeholderMemoryCacheKey(imageUrl)
                            .allowHardware(false)
                            .allowRgb565(true)
                            .listener(
                                onStart = {
                                    val isCached = imageLoader.memoryCache?.get(MemoryCache.Key(imageUrl)) != null
                                    val diskCached = imageLoader.diskCache?.openSnapshot(imageUrl)?.use { true } ?: false
                                    isLoading = !(isCached || diskCached)
                                },
                                onSuccess = { _, _ ->
                                    error = null
                                },
                                onError = { _, throwable ->
                                    isLoading = false
                                    error = throwable.throwable.localizedMessage
                                }
                            )
                            .build(),
                        contentDescription = "Comic page $currentPage",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentScale = ContentScale.Fit,
                        imageLoader = imageLoader
                    )
                }
            ) { measurables, constraints ->
                val width = constraints.maxWidth / 2
                val height = constraints.maxHeight
                val placeables = measurables.map { it.measure(constraints.copy(maxWidth = width, minWidth = 0)) }

                layout(constraints.maxWidth, height) {
                    placeables.forEachIndexed { index, placeable ->
                        val x = if (index == 0) width - placeable.width else width
                        placeable.place(x = x, y = 0)
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        error?.let { errorMessage ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                Button(onClick = { error = null; isLoading = true }) {
                    Text("重試")
                }
            }
        }

        if (settings.showPageNumber && !showSettings) {
            Text(
                text = "$currentPage/$pageCount",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        if (!showSettings) {
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(3) { i ->
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(3) { j ->
                            val areaType = when {
                                i == 1 && j == 1 -> ReaderAreaType.SETTINGS
                                i == 0 -> ReaderAreaType.PREVIOUS_PAGE
                                else -> ReaderAreaType.NEXT_PAGE
                            }
                            ReaderClickableArea(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                areaType = areaType,
                                currentPage = currentPage,
                                pageCount = pageCount,
                                settings = settings,
                                shouldShowDoublePage = shouldShowDoublePage,
                                baseUrl = baseUrl,
                                streamUrl = streamUrl,
                                imagePreloader = imagePreloader,
                                onPageChange = { currentPage = it },
                                onPreviousBook = onPreviousBook,
                                onNextBook = onNextBook,
                                onShowSettings = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }

        if (showSettings) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("返回到上层目录")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("跳转到页面: ")
                        var pageInput by remember { mutableStateOf(currentPage.toString()) }
                        TextField(
                            value = pageInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    pageInput = input
                                }
                            },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val newPage = pageInput.toIntOrNull() ?: currentPage
                            if (newPage in 1..pageCount) {
                                currentPage = newPage
                                showSettings = false
                            }
                        }) {
                            Text("跳转")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("显示页码")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.showPageNumber,
                            onCheckedChange = { settings = settings.copy(showPageNumber = it) }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用预加载")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.enablePreloading,
                            onCheckedChange = { settings = settings.copy(enablePreloading = it) }
                        )
                    }
                    if (settings.enablePreloading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("预加载范围: ${settings.preloadingRange}页")
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (settings.preloadingRange > 1) {
                                    settings = settings.copy(preloadingRange = settings.preloadingRange - 1)
                                }
                            }) {
                                Text("-")
                            }
                            IconButton(onClick = {
                                settings = settings.copy(preloadingRange = settings.preloadingRange + 1)
                            }) {
                                Text("+")
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("页面模式")
                        Spacer(modifier = Modifier.weight(1f))
                        Row {
                            Button(
                                onClick = { settings = settings.copy(pageMode = PageMode.SINGLE_PAGE) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.pageMode == PageMode.SINGLE_PAGE)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (settings.pageMode == PageMode.SINGLE_PAGE)
                                        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("单页")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { settings = settings.copy(pageMode = PageMode.DOUBLE_PAGE) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.pageMode == PageMode.DOUBLE_PAGE)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (settings.pageMode == PageMode.DOUBLE_PAGE)
                                        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("双页")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showSettings = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

enum class ReaderAreaType {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    SETTINGS
}

@Composable
private fun ReaderClickableArea(
    modifier: Modifier = Modifier,
    areaType: ReaderAreaType,
    currentPage: Int,
    pageCount: Int,
    settings: ReaderSettings,
    shouldShowDoublePage: Boolean,
    baseUrl: String,
    streamUrl: String,
    imagePreloader: ImagePreloader,
    onPageChange: (Int) -> Unit,
    onPreviousBook: () -> Unit,
    onNextBook: () -> Unit,
    onShowSettings: () -> Unit
) {
    fun buildPageUrl(baseUrl: String, streamUrl: String, page: Int): String {
        val adjustedPage = (page - 1).coerceAtLeast(0)
        val pageUrl = if (streamUrl.contains("pageNumber=")) {
            streamUrl.replace(Regex("pageNumber=\\d+"), "pageNumber=$adjustedPage")
        } else if (streamUrl.contains("{pageNumber}")) {
            streamUrl.replace("{pageNumber}", adjustedPage.toString())
        } else {
            streamUrl
        }
        return try {
            if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) {
                pageUrl
            } else {
                "$baseUrl/${pageUrl.removePrefix("/")}"
            }
        } catch (e: Exception) {
            Log.e("ComicReader", "Error parsing URL: baseUrl=$baseUrl, pageUrl=$pageUrl", e)
            "$baseUrl/${pageUrl.removePrefix("/")}"
        }
    }

    fun shouldShowDoublePageForPreviousPage(currentPage: Int, settings: ReaderSettings, imagePreloader: ImagePreloader, baseUrl: String, streamUrl: String): Boolean {
        if (settings.pageMode != PageMode.DOUBLE_PAGE) return false
        if (currentPage <= 2) return false // 第一页或第二页时，不应有前一页的双页

        val prevPage = currentPage - 1
        val prevPageUrl = buildPageUrl(baseUrl, streamUrl, prevPage)
        val prevPageAspectRatio = imagePreloader.getAspectRatio(prevPageUrl) ?: return false.also {
            Log.d("ComicReader", "Could not get aspect ratio for previous page ($prevPage).")
        }

        if (prevPageAspectRatio > 1.0f) {
            Log.d("ComicReader", "Previous page ($prevPage) is wide, no double page.")
            return false
        }

        val prevPrevPage = currentPage - 2
        if (prevPrevPage < 1) return false // 确保有前前页

        val prevPrevPageUrl = buildPageUrl(baseUrl, streamUrl, prevPrevPage)
        val prevPrevPageAspectRatio = imagePreloader.getAspectRatio(prevPrevPageUrl) ?: return false.also{
            Log.d("ComicReader", "Could not get aspect ratio for previous previous page ($prevPrevPage).")
        }

        if (prevPrevPageAspectRatio > 1.0f) {
            Log.d("ComicReader", "Previous previous page ($prevPrevPage) is wide, no double page.")
            return false
        }

        return true // 前一页和前前页都适合双页显示
    }

    Box(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                when (areaType) {
                    ReaderAreaType.PREVIOUS_PAGE -> {
                        val newPage = if (currentPage > 1) {
                            if (shouldShowDoublePageForPreviousPage(currentPage, settings, imagePreloader, baseUrl, streamUrl)) {
                                (currentPage - 2).coerceAtLeast(1)
                            } else {
                                currentPage - 1
                            }
                        } else {
                            pageCount
                        }

                        val prevPageUrl = buildPageUrl(baseUrl, streamUrl, newPage)

                        Log.d("ComicReader", "Preloading previous page: $newPage, URL: $prevPageUrl")
                        if (settings.enablePreloading) {
                            imagePreloader.preloadPagesAround(baseUrl, streamUrl, newPage, pageCount, 1)
                        }

                        Log.d("ComicReader", "Turn to previous page: from page $currentPage to page $newPage")
                        if (newPage == pageCount) onPreviousBook()
                        onPageChange(newPage)
                    }

                    ReaderAreaType.NEXT_PAGE -> {
                        if (currentPage < pageCount) {
                            val newPage = if (settings.pageMode == PageMode.DOUBLE_PAGE) {
                                if (shouldShowDoublePage) (currentPage + 2) else (currentPage + 1)
                            } else {
                                (currentPage + 1)
                            }.coerceAtMost(pageCount)


                            val nextPageUrl = buildPageUrl(baseUrl, streamUrl, newPage)

                            Log.d("ComicReader", "Preloading next page: $newPage, URL: $nextPageUrl")
                            if (settings.enablePreloading) {
                                imagePreloader.preloadPagesAround(baseUrl, streamUrl, newPage, pageCount, 1)
                            }

                            Log.d("ComicReader", "Turn to next page: from page $currentPage to page $newPage")
                            onPageChange(newPage)
                        } else {
                            Log.d("ComicReader", "Reached the last page, triggering onNextBook")
                            onNextBook()
                        }
                    }

                    ReaderAreaType.SETTINGS -> onShowSettings()
                }
            }
    )
}