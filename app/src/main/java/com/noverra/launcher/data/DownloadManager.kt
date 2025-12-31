package com.noverra.launcher.data

import android.util.Log
import com.noverra.launcher.data.model.FileEntry
import kotlinx.coroutines.*

/**
 * Singleton Download Manager yang berjalan di GlobalScope
 * Agar download tetap berjalan meskipun pindah tab/fragment
 */
object DownloadManager {
    
    private const val TAG = "DownloadManager"
    
    // Download state
    var isDownloading = false
        private set
    
    var downloadProgress: DownloadProgress? = null
        private set
    
    // Callback untuk UI update (akan di-set oleh fragment)
    var onProgressUpdate: ((DownloadProgress) -> Unit)? = null
    var onDownloadComplete: ((DownloadResult) -> Unit)? = null
    
    // Current download job
    private var downloadJob: Job? = null
    
    data class DownloadProgress(
        val currentFile: String,
        val currentUrl: String,
        val fileProgress: Int, // 0-100
        val totalFiles: Int,
        val completedFiles: Int,
        val failedFiles: Int
    )
    
    data class DownloadResult(
        val success: Boolean,
        val totalFiles: Int,
        val downloadedFiles: Int,
        val failedFiles: Int,
        val failedFilesList: List<String>
    )
    
    fun startDownload(files: List<FileEntry>) {
        if (isDownloading) {
            Log.w(TAG, "Download already in progress")
            return
        }
        
        // Cancel previous job if any
        downloadJob?.cancel()
        
        // Start new download in GlobalScope (survives fragment lifecycle)
        downloadJob = GlobalScope.launch(Dispatchers.Main) {
            isDownloading = true
            
            try {
                val (_, missing) = GameIntegrityManager.checkIntegrity(files)
                
                if (missing.isEmpty()) {
                    isDownloading = false
                    onDownloadComplete?.invoke(DownloadResult(true, 0, 0, 0, emptyList()))
                    return@launch
                }
                
                var downloaded = 0
                var failed = 0
                val maxRetries = 3
                val failedFilesList = mutableListOf<String>()
                
                for ((index, entry) in missing.withIndex()) {
                    val downloadUrl = GameIntegrityManager.getDownloadUrl(entry)
                    
                    // Update progress
                    downloadProgress = DownloadProgress(
                        currentFile = entry.name,
                        currentUrl = downloadUrl,
                        fileProgress = 0,
                        totalFiles = missing.size,
                        completedFiles = downloaded,
                        failedFiles = failed
                    )
                    notifyProgress()
                    
                    Log.d(TAG, "Downloading: ${entry.name} from $downloadUrl")
                    
                    var retryCount = 0
                    var success = false
                    
                    while (retryCount < maxRetries && !success) {
                        success = withContext(Dispatchers.IO) {
                            GameIntegrityManager.downloadFile(entry) { progress ->
                                // Update file progress
                                downloadProgress = downloadProgress?.copy(
                                    fileProgress = (progress * 100).toInt()
                                )
                                // Notify on main thread
                                GlobalScope.launch(Dispatchers.Main) {
                                    notifyProgress()
                                }
                            }
                        }
                        
                        if (!success) {
                            retryCount++
                            if (retryCount < maxRetries) {
                                downloadProgress = downloadProgress?.copy(
                                    currentFile = "${entry.name} (Retry $retryCount/$maxRetries)"
                                )
                                notifyProgress()
                                Log.w(TAG, "Retry $retryCount for ${entry.name}")
                                delay(1000)
                            }
                        }
                    }
                    
                    if (success) {
                        downloaded++
                        Log.d(TAG, "SUCCESS: ${entry.name}")
                    } else {
                        failed++
                        failedFilesList.add("${entry.name} -> $downloadUrl")
                        Log.e(TAG, "FAILED: ${entry.name}")
                    }
                    
                    // Update completed count
                    downloadProgress = downloadProgress?.copy(
                        completedFiles = downloaded,
                        failedFiles = failed
                    )
                    notifyProgress()
                }
                
                isDownloading = false
                downloadProgress = null
                
                val result = DownloadResult(
                    success = failed == 0,
                    totalFiles = missing.size,
                    downloadedFiles = downloaded,
                    failedFiles = failed,
                    failedFilesList = failedFilesList
                )
                
                Log.d(TAG, "Download complete: $downloaded/${missing.size}, failed: $failed")
                onDownloadComplete?.invoke(result)
                
            } catch (e: CancellationException) {
                Log.w(TAG, "Download cancelled")
                isDownloading = false
                downloadProgress = null
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                isDownloading = false
                downloadProgress = null
                onDownloadComplete?.invoke(DownloadResult(false, 0, 0, 0, listOf(e.message ?: "Unknown error")))
            }
        }
    }
    
    fun cancelDownload() {
        downloadJob?.cancel()
        isDownloading = false
        downloadProgress = null
        Log.d(TAG, "Download cancelled by user")
    }
    
    private fun notifyProgress() {
        downloadProgress?.let { progress ->
            onProgressUpdate?.invoke(progress)
        }
    }
    
    // Clear callbacks (call when fragment is destroyed)
    fun clearCallbacks() {
        onProgressUpdate = null
        onDownloadComplete = null
    }
}
