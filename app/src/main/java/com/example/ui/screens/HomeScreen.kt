package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DownloadStatus
import com.example.ui.components.AboutDialog
import com.example.ui.components.DeleteFileDialog
import com.example.ui.components.DetectedDownloadDialog
import com.example.ui.components.DirectDownloadDialog
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.RenameFileDialog
import com.example.ui.components.StoragePermissionDialog
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.viewmodel.DownloadViewModel
import com.example.ui.viewmodel.MainTab
import com.example.util.StoragePermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val downloadedFiles by viewModel.downloadedFiles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAboutDialog by remember { mutableStateOf(false) }

    val activeDownloadingCount = remember(activeTasks) {
        activeTasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Hide top bar when in browser mode to allow maximum webview viewport
            if (!uiState.isBrowserOpen) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(PrimaryBlue, SecondaryTeal)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Fast Easy Downloader",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "SSSTik • FDown • SaveFrom • Y2Mate",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.setShowStoragePermissionDialog(true) },
                            modifier = Modifier.testTag("topbar_btn_storage_perm")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Storage Permissions",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.showDirectDownloadDialog(true) },
                            modifier = Modifier.testTag("topbar_btn_add_url")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Download",
                                tint = PrimaryBlue
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshFilesAndStorage() },
                            modifier = Modifier.testTag("topbar_btn_refresh")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.testTag("topbar_btn_about")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About App",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // Bottom navigation
            if (!uiState.isBrowserOpen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.PORTALS,
                        onClick = { viewModel.selectTab(MainTab.PORTALS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Portals"
                            )
                        },
                        label = { Text("Portals") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_portals")
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.DOWNLOADING,
                        onClick = { viewModel.selectTab(MainTab.DOWNLOADING) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeDownloadingCount > 0) {
                                        Badge(
                                            containerColor = PrimaryBlue,
                                            contentColor = Color.White
                                        ) {
                                            Text("$activeDownloadingCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Downloading"
                                )
                            }
                        },
                        label = { Text("Downloading") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_downloading")
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.DOWNLOADED,
                        onClick = { viewModel.selectTab(MainTab.DOWNLOADED) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (downloadedFiles.isNotEmpty()) {
                                        Badge(
                                            containerColor = SecondaryTeal,
                                            contentColor = Color.White
                                        ) {
                                            Text("${downloadedFiles.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Downloaded Files"
                                )
                            }
                        },
                        label = { Text("Downloaded") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_downloaded")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                MainTab.PORTALS -> {
                    PortalsScreen(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
                MainTab.DOWNLOADING -> {
                    DownloadingScreen(
                        tasks = activeTasks,
                        viewModel = viewModel
                    )
                }
                MainTab.DOWNLOADED -> {
                    DownloadedFilesScreen(
                        files = downloadedFiles,
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Intercepted Download Detected Dialog
    uiState.detectedDownload?.let { detected ->
        DetectedDownloadDialog(
            detected = detected,
            onConfirm = { customName ->
                viewModel.confirmDownload(
                    url = detected.url,
                    customFileName = customName,
                    sourcePortal = uiState.selectedPortal?.name,
                    mimeType = detected.mimeType
                )
            },
            onDismiss = { viewModel.dismissDetectedDownload() }
        )
    }

    // Direct Download Dialog
    if (uiState.showDirectDownloadDialog) {
        DirectDownloadDialog(
            initialUrl = uiState.directUrlInput,
            onConfirm = { url, customName ->
                viewModel.showDirectDownloadDialog(false)
                viewModel.confirmDownload(
                    url = url,
                    customFileName = customName,
                    sourcePortal = "Direct"
                )
            },
            onDismiss = { viewModel.showDirectDownloadDialog(false) }
        )
    }

    // Rename Dialog
    uiState.selectedFileForRename?.let { file ->
        RenameFileDialog(
            file = file,
            onConfirm = { newName ->
                viewModel.confirmRenameFile(file, newName)
            },
            onDismiss = { viewModel.selectFileForRename(null) }
        )
    }

    // Delete Dialog
    uiState.selectedFileForDelete?.let { file ->
        DeleteFileDialog(
            file = file,
            onConfirm = {
                viewModel.confirmDeleteFile(file)
            },
            onDismiss = { viewModel.selectFileForDelete(null) }
        )
    }

    // File Details Dialog
    uiState.selectedFileForPreview?.let { file ->
        FileDetailsDialog(
            file = file,
            onOpen = { viewModel.openFile(context, file) },
            onShare = { viewModel.shareFile(context, file) },
            onDismiss = { viewModel.selectFileForPreview(null) }
        )
    }

    // Storage & Media Permission Dialog
    if (uiState.showStoragePermissionDialog) {
        StoragePermissionDialog(
            onDismiss = { viewModel.setShowStoragePermissionDialog(false) },
            onPermissionGranted = {
                viewModel.setShowStoragePermissionDialog(false)
                viewModel.refreshFilesAndStorage()
                viewModel.showSnackbar("Storage access enabled successfully!")
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}
