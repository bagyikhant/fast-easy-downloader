package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import com.example.model.DownloadedFile
import com.example.model.FileCategory
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(private val context: Context) {

    private val TAG = "DownloadRepository"
    private val PREF_NAME = "downloader_prefs"
    private val KEY_TASKS = "saved_tasks"

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val taskListType = Types.newParameterizedType(List::class.java, DownloadTask::class.java)
    private val taskAdapter = moshi.adapter<List<DownloadTask>>(taskListType)

    private val _downloadedFiles = MutableStateFlow<List<DownloadedFile>>(emptyList())
    val downloadedFiles = _downloadedFiles.asStateFlow()

    private val downloadsDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun loadSavedTasks(): List<DownloadTask> {
        val json = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            taskAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse saved tasks JSON", e)
            emptyList()
        }
    }

    fun saveTasks(tasks: List<DownloadTask>) {
        try {
            val json = taskAdapter.toJson(tasks)
            prefs.edit().putString(KEY_TASKS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tasks to prefs", e)
        }
    }

    suspend fun refreshDownloadedFiles(completedTasks: List<DownloadTask>) = withContext(Dispatchers.IO) {
        val taskMap = completedTasks.associateBy { it.localFilePath.ifEmpty { File(downloadsDir, it.fileName).absolutePath } }
        val files = downloadsDir.listFiles { file ->
            file.isFile && !file.name.endsWith(".download")
        } ?: emptyArray()

        val list = files.map { file ->
            val matchingTask = taskMap[file.absolutePath] ?: completedTasks.find { it.fileName == file.name }
            DownloadedFile.fromFile(
                file = file,
                sourceUrl = matchingTask?.url,
                sourcePortal = matchingTask?.sourcePortal
            )
        }.sortedByDescending { it.lastModified }

        _downloadedFiles.value = list
    }

    fun deleteFile(file: File): Boolean {
        return try {
            val result = file.delete()
            // remove temp file too if any
            val temp = File(file.parentFile, "${file.name}.download")
            if (temp.exists()) temp.delete()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${file.name}", e)
            false
        }
    }

    fun renameFile(oldFile: File, newNameWithoutExt: String): File? {
        return try {
            val ext = if (oldFile.extension.isNotEmpty()) ".${oldFile.extension}" else ""
            val cleanBaseName = newNameWithoutExt.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val newFile = File(oldFile.parentFile, "$cleanBaseName$ext")
            if (oldFile.renameTo(newFile)) {
                newFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file: ${oldFile.name}", e)
            null
        }
    }

    fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createShareIntent(file: File, mimeType: String): Intent {
        val uri = getFileUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createViewIntent(file: File, mimeType: String): Intent {
        val uri = getFileUri(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getStorageStats(): StorageStats {
        var totalDownloadsSize = 0L
        val files = downloadsDir.listFiles() ?: emptyArray()
        for (f in files) {
            if (f.isFile) {
                totalDownloadsSize += f.length()
            }
        }

        var freeSpace = 0L
        var totalSpace = 0L
        try {
            val stat = StatFs(downloadsDir.path)
            freeSpace = stat.availableBlocksLong * stat.blockSizeLong
            totalSpace = stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get StatFs", e)
        }

        return StorageStats(
            appDownloadsSize = totalDownloadsSize,
            deviceFreeSpace = freeSpace,
            deviceTotalSpace = totalSpace,
            filesCount = files.count { it.isFile && !it.name.endsWith(".download") }
        )
    }
}

data class StorageStats(
    val appDownloadsSize: Long,
    val deviceFreeSpace: Long,
    val deviceTotalSpace: Long,
    val filesCount: Int
) {
    val formattedAppDownloadsSize: String
        get() = DownloadTask.formatBytes(appDownloadsSize)

    val formattedFreeSpace: String
        get() = DownloadTask.formatBytes(deviceFreeSpace)

    val formattedTotalSpace: String
        get() = DownloadTask.formatBytes(deviceTotalSpace)
}
