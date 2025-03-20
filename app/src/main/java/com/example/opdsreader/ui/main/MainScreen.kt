package com.example.opdsreader.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.opdsreader.data.model.OpdsEntry
import com.example.opdsreader.ui.reader.ComicReader

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@coil.annotation.ExperimentalCoilApi
fun MainScreen(
    viewModel: MainViewModel,
    onEntryClick: (OpdsEntry) -> Unit
) {
    val uiState = viewModel.uiState

    if (uiState.showLastReadDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissContinueReadingDialog() },
            title = { Text("繼續閱讀？") },
            text = { Text("是否從上次閱讀的第${uiState.lastReadPage}頁繼續？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (uiState.currentEntry == null) {
                            android.util.Log.e("OPDSReader", "繼續閱讀：找不到對應的entry，streamUrl=${uiState.streamUrl}")
                            return@TextButton
                        }
                        android.util.Log.d("OPDSReader", "繼續閱讀：找到entry ${uiState.currentEntry.title}，開始閱讀")
                        viewModel.continuePreviousReading(uiState.currentEntry)
                    }
                ) {
                    Text("繼續閱讀")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (uiState.currentEntry == null) {
                            android.util.Log.e("OPDSReader", "從頭開始：找不到對應的entry，streamUrl=${uiState.streamUrl}")
                            return@TextButton
                        }
                        android.util.Log.d("OPDSReader", "從頭開始：找到entry ${uiState.currentEntry.title}，開始閱讀")
                        viewModel.startReading(uiState.currentEntry, 1)
                    }
                ) {
                    Text("從頭開始")
                }
            }
        )
    }

    if (uiState.isReading && uiState.streamUrl != null) {
        ComicReader(
            key = uiState.streamUrl,
            streamUrl = uiState.streamUrl,
            pageCount = uiState.pageCount,
            onClose = { viewModel.navigateBack() },
            baseUrl = uiState.serverUrl.takeIf { it.isNotBlank() }?.let { it.trimEnd('/') } ?: "http://localhost/",
            onNextBook = {
                val entries = uiState.currentFeed?.entries ?: emptyList()
                val currentFullStreamUrlWithPage = uiState.streamUrl
                val currentFullStreamUrlWithoutPage = currentFullStreamUrlWithPage.substringBeforeLast("&pageNumber=")
                android.util.Log.d("MainScreen", "onNextBook: uiState.streamUrl (without page) = $currentFullStreamUrlWithoutPage")
                var nextEntry: OpdsEntry? = null
                val currentEntryIndex = entries.indexOfFirst { entry ->
                    val relativeStreamUrl = entry.links?.find { it.rel == "http://vaemendis.net/opds-pse/stream" }?.href
                    val fullStreamUrlWithPage = relativeStreamUrl?.let {
                        it.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            ?: viewModel.constructFullUrl(uiState.navigationStack.lastOrNull() ?: uiState.serverUrl, it)
                    }
                    val fullStreamUrlWithoutPage = fullStreamUrlWithPage?.substringBeforeLast("&pageNumber=")
                    fullStreamUrlWithoutPage == currentFullStreamUrlWithoutPage
                }

                android.util.Log.d("MainScreen", "onNextBook: currentEntryIndex = $currentEntryIndex, entries size = ${entries.size}")

                if (currentEntryIndex != -1) {
                    val nextIndex = currentEntryIndex + 1
                    android.util.Log.d("MainScreen", "onNextBook: nextIndex = $nextIndex")
                    if (nextIndex < entries.size) {
                        nextEntry = entries[nextIndex]
                        android.util.Log.d("MainScreen", "onNextBook: Found next entry: ${nextEntry?.title}")
                    } else {
                        android.util.Log.d("MainScreen", "onNextBook: Already at the end of the feed.")
                        // 可以考慮在這裡做一些提示，例如 Toast
                    }
                } else {
                    android.util.Log.e("MainScreen", "onNextBook: Could not find current entry in feed.")
                }

                // 先導航回上一層
                viewModel.navigateBack()

                // 如果找到了下一本書，則點擊它
                nextEntry?.let {
                    android.util.Log.d("MainScreen", "onNextBook: Navigating to next entry: ${it.title}")
                    viewModel.handleEntryClick(it)
                }
            },
            onPreviousBook = {
                val entries = uiState.currentFeed?.entries ?: emptyList()
                val currentFullStreamUrlWithPage = uiState.streamUrl
                val currentFullStreamUrlWithoutPage = currentFullStreamUrlWithPage.substringBeforeLast("&pageNumber=")
                android.util.Log.d("MainScreen", "onPreviousBook: uiState.streamUrl (without page) = $currentFullStreamUrlWithoutPage")
                var previousEntry: OpdsEntry? = null
                val currentEntryIndex = entries.indexOfFirst { entry ->
                    val relativeStreamUrl = entry.links?.find { it.rel == "http://vaemendis.net/opds-pse/stream" }?.href
                    val fullStreamUrlWithPage = relativeStreamUrl?.let {
                        it.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            ?: viewModel.constructFullUrl(uiState.navigationStack.lastOrNull() ?: uiState.serverUrl, it)
                    }
                    val fullStreamUrlWithoutPage = fullStreamUrlWithPage?.substringBeforeLast("&pageNumber=")
                    fullStreamUrlWithoutPage == currentFullStreamUrlWithoutPage
                }

                android.util.Log.d("MainScreen", "onPreviousBook: currentEntryIndex = $currentEntryIndex, entries size = ${entries.size}")

                if (currentEntryIndex != -1) {
                    val previousIndex = currentEntryIndex - 1
                    android.util.Log.d("MainScreen", "onPreviousBook: previousIndex = $previousIndex")
                    if (previousIndex >= 0) {
                        previousEntry = entries[previousIndex]
                        android.util.Log.d("MainScreen", "onPreviousBook: Found previous entry: ${previousEntry?.title}")
                    } else {
                        android.util.Log.d("MainScreen", "onPreviousBook: Already at the beginning of the feed.")
                        // 可以考慮在這裡做一些提示，例如 Toast
                    }
                } else {
                    android.util.Log.e("MainScreen", "onPreviousBook: Could not find current entry in feed.")
                }

                // 先導航回上一層
                viewModel.navigateBack()

                // 如果找到了前一本書，則點擊它
                previousEntry?.let {
                    android.util.Log.d("MainScreen", "onPreviousBook: Navigating to previous entry: ${it.title}")
                    viewModel.handleEntryClick(it)
                }
            },
            startPage = uiState.lastReadPage
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.currentFeed?.title ?: "OPDS Reader") },
                navigationIcon = if (uiState.navigationStack.size > 1) {
                    {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                } else {
                    {}
                },
                actions = {
                    if (uiState.navigationStack.isNotEmpty()) {
                        IconButton(onClick = { viewModel.resetNavigation() }) {
                            Icon(Icons.Default.Home, contentDescription = "返回主畫面")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.navigationStack.isEmpty()) {
                ServerUrlInput(
                    serverUrl = uiState.serverUrl,
                    onServerUrlChange = viewModel::updateServerUrl,
                    onConnect = viewModel::loadFeed,
                    modifier = Modifier.padding(16.dp)
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "发生错误",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        if (uiState.navigationStack.isNotEmpty()) {
                            Button(onClick = {
                                val lastUrl = uiState.navigationStack.last()
                                viewModel.loadFeed(lastUrl)
                            }) {
                                Text("重试")
                            }
                        }
                    }
                }
                uiState.currentFeed != null -> {
                    LazyColumn {
                        items(uiState.currentFeed.entries ?: emptyList()) { entry ->
                            EntryItem(
                                entry = entry,
                                onClick = { onEntryClick(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerUrlInput(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("OPDS Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { onConnect("https://demo.kavitareader.com/api/opds/9003cf99-9213-4206-a787-af2fe4cc5f1f/series/15") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Demo書庫")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onConnect(serverUrl) },
                enabled = serverUrl.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
fun EntryItem(
    entry: OpdsEntry,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = entry.title ?: "Untitled",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = entry.content?.let { content ->
            {
                Text(
                    text = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}