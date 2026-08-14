package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DownloadEngine
import com.example.data.DownloadRepository
import com.example.data.StorageStats
import com.example.model.DefaultPortals
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import com.example.model.DownloadedFile
import com.example.model.FileCategory
import com.example.model.PortalItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class MainTab(val title: String) {
    PORTALS("Portals & Web"),
    DOWNLOADING("Downloading"),
    DOWNLOADED("Downloaded Files")
}

enum class SortOption(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    SIZE_DESC("Largest Size"),
    NAME_ASC("Name (A-Z)")
}

data class DetectedDownload(
    val url: String,
    val suggestedName: String,
    val mimeType: String?,
    val contentLength: Long,
    val userAgent: String? = null,
    val contentDisposition: String? = null
)

data class UiState(
    val currentTab: MainTab = MainTab.PORTALS,
    val isBrowserOpen: Boolean = false,
    val currentBrowserUrl: String = "https://ssstik.io",
    val currentBrowserTitle: String = "SSSTik",
    val activePortals: List<PortalItem> = DefaultPortals.list,
    val selectedPortal: PortalItem? = DefaultPortals.list.first(),
    val directUrlInput: String = "",
    val detectedDownload: DetectedDownload? = null,
    val showDirectDownloadDialog: Boolean = false,
    val selectedCategory: FileCategory = FileCategory.ALL,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NEWEST,
    val selectedFileForPreview: DownloadedFile? = null,
    val selectedFileForRename: DownloadedFile? = null,
    val selectedFileForDelete: DownloadedFile? = null,
    val snackbarMessage: String? = null,
    val storageStats: StorageStats = StorageStats(0L, 0L, 0L, 0),
    val showStoragePermissionDialog: Boolean = false
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = DownloadEngine(application)
    private val repository = DownloadRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val rawTasks = engine.tasks

    val activeTasks: StateFlow<List<DownloadTask>> = engine.tasks.combine(_uiState) { taskMap, _ ->
        taskMap.values
            .filter { it.status != DownloadStatus.COMPLETED }
            .sortedByDescending { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<DownloadTask>> = engine.tasks.combine(_uiState) { taskMap, _ ->
        taskMap.values
            .filter { it.status == DownloadStatus.COMPLETED }
            .sortedByDescending { it.completedTime ?: it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedFiles: StateFlow<List<DownloadedFile>> = repository.downloadedFiles.combine(_uiState) { files, state ->
        var list = files
        if (state.selectedCategory != FileCategory.ALL) {
            list = list.filter { it.category == state.selectedCategory }
        }
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.trim().lowercase()
            list = list.filter { it.name.lowercase().contains(query) }
        }
        when (state.sortOption) {
            SortOption.NEWEST -> list.sortedByDescending { it.lastModified }
            SortOption.OLDEST -> list.sortedBy { it.lastModified }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val saved = repository.loadSavedTasks()
        engine.initTasks(saved)
        refreshFilesAndStorage()

        // Auto-save tasks whenever they change
        viewModelScope.launch {
            engine.tasks.collect { map ->
                repository.saveTasks(map.values.toList())
                val completed = map.values.filter { it.status == DownloadStatus.COMPLETED }
                repository.refreshDownloadedFiles(completed)
                updateStorageStats()
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == MainTab.DOWNLOADED) {
            refreshFilesAndStorage()
        }
    }

    fun openPortal(portal: PortalItem) {
        _uiState.update {
            it.copy(
                selectedPortal = portal,
                currentBrowserUrl = portal.url,
                currentBrowserTitle = portal.name,
                isBrowserOpen = true,
                currentTab = MainTab.PORTALS
            )
        }
    }

    fun openCustomUrl(url: String) {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        _uiState.update {
            it.copy(
                currentBrowserUrl = cleanUrl,
                currentBrowserTitle = cleanUrl.substringAfter("://").substringBefore("/"),
                isBrowserOpen = true,
                currentTab = MainTab.PORTALS
            )
        }
    }

    fun closeBrowser() {
        _uiState.update { it.copy(isBrowserOpen = false) }
    }

    fun updateBrowserInfo(url: String, title: String) {
        _uiState.update { it.copy(currentBrowserUrl = url, currentBrowserTitle = title) }
    }

    fun onDownloadDetected(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val suggestedName = engine.extractFileNameFromUrl(url, mimeType)
        val detected = DetectedDownload(
            url = url,
            suggestedName = suggestedName,
            mimeType = mimeType,
            contentLength = contentLength,
            userAgent = userAgent,
            contentDisposition = contentDisposition
        )
        _uiState.update { it.copy(detectedDownload = detected) }
    }

    fun dismissDetectedDownload() {
        _uiState.update { it.copy(detectedDownload = null) }
    }

    fun confirmDownload(
        url: String,
        customFileName: String? = null,
        sourcePortal: String? = null,
        mimeType: String? = null
    ) {
        dismissDetectedDownload()
        val portalName = sourcePortal ?: _uiState.value.selectedPortal?.name ?: "Direct"
        val taskId = engine.enqueueDownload(
            url = url,
            suggestedFileName = customFileName,
            sourcePortal = portalName,
            mimeType = mimeType,
            scope = viewModelScope
        )
        showSnackbar("Download started: ${customFileName ?: "File"}")
        // Switch to downloading view to show progress
        selectTab(MainTab.DOWNLOADING)
    }

    fun pauseDownload(id: String) {
        engine.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        engine.resumeDownload(id, viewModelScope)
    }

    fun cancelDownload(id: String) {
        engine.cancelDownload(id)
        showSnackbar("Download canceled")
    }

    fun removeTask(id: String) {
        engine.removeTask(id)
    }

    fun pauseAllDownloads() {
        engine.pauseAll()
        showSnackbar("All downloads paused")
    }

    fun resumeAllDownloads() {
        engine.resumeAll(viewModelScope)
        showSnackbar("All downloads resumed")
    }

    fun setDirectUrlInput(input: String) {
        _uiState.update { it.copy(directUrlInput = input) }
    }

    fun showDirectDownloadDialog(show: Boolean) {
        _uiState.update { it.copy(showDirectDownloadDialog = show) }
    }

    fun setShowStoragePermissionDialog(show: Boolean) {
        _uiState.update { it.copy(showStoragePermissionDialog = show) }
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank()) {
                _uiState.update { it.copy(directUrlInput = text.trim()) }
                showSnackbar("Pasted link from clipboard")
            }
        }
    }

    fun setSelectedCategory(category: FileCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.update { it.copy(sortOption = sort) }
    }

    fun selectFileForPreview(file: DownloadedFile?) {
        _uiState.update { it.copy(selectedFileForPreview = file) }
    }

    fun selectFileForRename(file: DownloadedFile?) {
        _uiState.update { it.copy(selectedFileForRename = file) }
    }

    fun selectFileForDelete(file: DownloadedFile?) {
        _uiState.update { it.copy(selectedFileForDelete = file) }
    }

    fun confirmRenameFile(file: DownloadedFile, newNameWithoutExt: String) {
        val renamed = repository.renameFile(file.file, newNameWithoutExt)
        if (renamed != null) {
            showSnackbar("Renamed to ${renamed.name}")
            refreshFilesAndStorage()
        } else {
            showSnackbar("Failed to rename file")
        }
        selectFileForRename(null)
    }

    fun confirmDeleteFile(file: DownloadedFile) {
        val success = repository.deleteFile(file.file)
        if (success) {
            showSnackbar("File deleted")
            refreshFilesAndStorage()
        } else {
            showSnackbar("Failed to delete file")
        }
        selectFileForDelete(null)
    }

    fun openFile(context: Context, file: DownloadedFile) {
        try {
            val intent = repository.createViewIntent(file.file, file.mimeType)
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            showSnackbar("No app found to open this file type")
        }
    }

    fun shareFile(context: Context, file: DownloadedFile) {
        try {
            val intent = repository.createShareIntent(file.file, file.mimeType)
            context.startActivity(Intent.createChooser(intent, "Share file via"))
        } catch (e: Exception) {
            showSnackbar("Failed to share file")
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun refreshFilesAndStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val completed = engine.tasks.value.values.filter { it.status == DownloadStatus.COMPLETED }
            repository.refreshDownloadedFiles(completed)
            updateStorageStats()
        }
    }

    private fun updateStorageStats() {
        val stats = repository.getStorageStats()
        _uiState.update { it.copy(storageStats = stats) }
    }
}
