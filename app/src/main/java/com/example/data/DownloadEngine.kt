package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import com.example.model.FileCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DownloadEngine(private val context: Context) {

    private val TAG = "DownloadEngine"

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks = _tasks.asStateFlow()

    private val downloadsDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun initTasks(loadedTasks: List<DownloadTask>) {
        val map = loadedTasks.associateBy { it.id }.toMutableMap()
        // If app restarted while downloading, mark those as PAUSED so user can resume
        map.forEach { (id, task) ->
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                map[id] = task.copy(status = DownloadStatus.PAUSED, downloadSpeed = 0L)
            }
        }
        _tasks.value = map
    }

    fun enqueueDownload(
        url: String,
        suggestedFileName: String? = null,
        sourcePortal: String? = null,
        mimeType: String? = null,
        scope: CoroutineScope
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val initialName = suggestedFileName ?: extractFileNameFromUrl(url, mimeType)
        val initialCategory = FileCategory.fromExtension(File(initialName).extension).let {
            if (it == FileCategory.OTHER && mimeType != null) FileCategory.fromMimeType(mimeType) else it
        }

        val task = DownloadTask(
            id = id,
            url = url,
            fileName = initialName,
            category = initialCategory,
            status = DownloadStatus.QUEUED,
            sourcePortal = sourcePortal,
            mimeType = mimeType,
            startTime = System.currentTimeMillis()
        )

        _tasks.update { it + (id to task) }
        startDownload(id, scope)
        return id
    }

    fun startDownload(id: String, scope: CoroutineScope) {
        val currentTask = _tasks.value[id] ?: return
        if (activeJobs[id]?.isActive == true) return

        val job = scope.launch(Dispatchers.IO) {
            executeDownload(id)
        }
        activeJobs[id] = job
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _tasks.update { map ->
            val task = map[id] ?: return@update map
            map + (id to task.copy(status = DownloadStatus.PAUSED, downloadSpeed = 0L))
        }
    }

    fun resumeDownload(id: String, scope: CoroutineScope) {
        startDownload(id, scope)
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val task = _tasks.value[id]
        if (task != null) {
            // Delete temp file if exists
            val tempFile = File(downloadsDir, "${task.fileName}.download")
            if (tempFile.exists()) {
                tempFile.delete()
            }
            _tasks.update { it - id }
        }
    }

    fun removeTask(id: String) {
        cancelDownload(id)
        _tasks.update { it - id }
    }

    fun pauseAll() {
        _tasks.value.values.filter { it.status == DownloadStatus.DOWNLOADING }.forEach {
            pauseDownload(it.id)
        }
    }

    fun resumeAll(scope: CoroutineScope) {
        _tasks.value.values.filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED }.forEach {
            resumeDownload(it.id, scope)
        }
    }

    private suspend fun executeDownload(id: String) {
        val task = _tasks.value[id] ?: return
        val url = task.url
        var downloaded = task.downloadedBytes
        var finalFileName = task.fileName
        var finalCategory = task.category

        _tasks.update { map ->
            val t = map[id] ?: return@update map
            map + (id to t.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null))
        }

        val tempFile = File(downloadsDir, "$finalFileName.download")
        if (!tempFile.exists() && downloaded > 0) {
            // If file was deleted, restart from 0
            downloaded = 0L
        }

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "en-US,en;q=0.9")

            if (downloaded > 0L && tempFile.exists()) {
                requestBuilder.addHeader("Range", "bytes=$downloaded-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                // If Range not satisfiable (416), restart from beginning
                if (response.code == 416) {
                    tempFile.delete()
                    downloaded = 0L
                    _tasks.update { map ->
                        val t = map[id] ?: return@update map
                        map + (id to t.copy(downloadedBytes = 0L))
                    }
                    executeDownload(id)
                    return
                }
                throw Exception("HTTP error code: ${response.code} ${response.message}")
            }

            val responseBody = response.body ?: throw Exception("Empty response body")
            val contentDisposition = response.header("Content-Disposition")
            val contentType = response.header("Content-Type")
            val resolvedName = resolveFileName(url, contentDisposition, contentType, finalFileName)
            finalFileName = sanitizeFileName(resolvedName)
            finalCategory = FileCategory.fromExtension(File(finalFileName).extension).let {
                if (it == FileCategory.OTHER && contentType != null) FileCategory.fromMimeType(contentType) else it
            }

            var totalBytes = responseBody.contentLength()
            if (totalBytes > 0 && response.code == 206) {
                totalBytes += downloaded
            } else if (totalBytes <= 0 && task.totalBytes > 0) {
                totalBytes = task.totalBytes
            }

            _tasks.update { map ->
                val t = map[id] ?: return@update map
                map + (id to t.copy(
                    fileName = finalFileName,
                    category = finalCategory,
                    totalBytes = totalBytes,
                    mimeType = contentType ?: t.mimeType
                ))
            }

            val inputStream: InputStream = responseBody.byteStream()
            val fileAccess = RandomAccessFile(tempFile, "rw")
            if (downloaded > 0) {
                fileAccess.seek(downloaded)
            } else {
                fileAccess.setLength(0)
            }

            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var currentSpeed = 0L

            inputStream.use { stream ->
                fileAccess.use { output ->
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        if (!CoroutineScope(Dispatchers.IO).isActive) {
                            throw CancellationException("Download canceled/paused")
                        }

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        bytesSinceLastUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastUpdateTime
                        if (elapsed >= 500) {
                            currentSpeed = (bytesSinceLastUpdate * 1000) / elapsed
                            lastUpdateTime = now
                            bytesSinceLastUpdate = 0L

                            _tasks.update { map ->
                                val t = map[id] ?: return@update map
                                map + (id to t.copy(
                                    downloadedBytes = downloaded,
                                    downloadSpeed = currentSpeed
                                ))
                            }
                        }
                    }
                }
            }

            // Download completed successfully!
            val targetFile = getUniqueFile(downloadsDir, finalFileName)
            if (tempFile.exists()) {
                tempFile.renameTo(targetFile)
            }

            _tasks.update { map ->
                val t = map[id] ?: return@update map
                map + (id to t.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = targetFile.length(),
                    totalBytes = targetFile.length(),
                    downloadSpeed = 0L,
                    completedTime = System.currentTimeMillis(),
                    localFilePath = targetFile.absolutePath,
                    fileName = targetFile.name
                ))
            }
            activeJobs.remove(id)

        } catch (e: CancellationException) {
            Log.d(TAG, "Download job paused/cancelled: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $id: ${e.message}", e)
            _tasks.update { map ->
                val t = map[id] ?: return@update map
                map + (id to t.copy(
                    status = DownloadStatus.FAILED,
                    downloadSpeed = 0L,
                    errorMessage = e.localizedMessage ?: "Download failed"
                ))
            }
            activeJobs.remove(id)
        }
    }

    private fun resolveFileName(url: String, contentDisposition: String?, contentType: String?, currentName: String): String {
        if (!contentDisposition.isNullOrBlank()) {
            val name = parseContentDisposition(contentDisposition)
            if (!name.isNullOrBlank()) return name
        }
        if (currentName.isNotBlank() && currentName != "download" && currentName.contains(".")) {
            return currentName
        }
        val fromUrl = extractFileNameFromUrl(url, contentType)
        return fromUrl
    }

    private fun parseContentDisposition(contentDisposition: String): String? {
        try {
            // Match filename*=UTF-8''encoded_name
            val utf8Matcher = Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE).matcher(contentDisposition)
            if (utf8Matcher.find()) {
                return URLDecoder.decode(utf8Matcher.group(1), "UTF-8")
            }
            // Match filename="name" or filename=name
            val stdMatcher = Pattern.compile("filename=[\"']?([^\"';]+)[\"']?", Pattern.CASE_INSENSITIVE).matcher(contentDisposition)
            if (stdMatcher.find()) {
                return stdMatcher.group(1).trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Content-Disposition: $contentDisposition", e)
        }
        return null
    }

    fun extractFileNameFromUrl(url: String, mimeType: String? = null): String {
        try {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val nameFromPath = cleanUrl.substringAfterLast("/")
            if (nameFromPath.isNotBlank() && nameFromPath.contains(".")) {
                return URLDecoder.decode(nameFromPath, "UTF-8")
            }
        } catch (e: Exception) {
            // fallback
        }
        val ext = when {
            mimeType?.contains("video/mp4", ignoreCase = true) == true -> ".mp4"
            mimeType?.contains("video/webm", ignoreCase = true) == true -> ".webm"
            mimeType?.contains("audio/mpeg", ignoreCase = true) == true || mimeType?.contains("audio/mp3", ignoreCase = true) == true -> ".mp3"
            mimeType?.contains("audio/mp4", ignoreCase = true) == true || mimeType?.contains("audio/m4a", ignoreCase = true) == true -> ".m4a"
            mimeType?.contains("image/jpeg", ignoreCase = true) == true -> ".jpg"
            mimeType?.contains("image/png", ignoreCase = true) == true -> ".png"
            mimeType?.contains("application/pdf", ignoreCase = true) == true -> ".pdf"
            else -> ".mp4"
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "download_$timestamp$ext"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    private fun getUniqueFile(dir: File, baseName: String): File {
        var file = File(dir, baseName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var count = 1
        while (file.exists()) {
            file = File(dir, "${nameWithoutExt}_$count$ext")
            count++
        }
        return file
    }
}
