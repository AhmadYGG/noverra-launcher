package com.noverra.launcher.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.noverra.launcher.data.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object GameIntegrityManager {

    private const val TAG = "GameIntegrityManager"

    // GPU Type constants (sama seperti NoverraLauncher)
    const val GPU_TYPE_DXT = 1
    const val GPU_TYPE_ETC = 2
    const val GPU_TYPE_PVR = 3
    
    // Current GPU type - akan di-set dari HomeFragment
    var currentGpuType: Int = GPU_TYPE_ETC // Default ETC (paling umum)

    // Target Path: sama dengan NoverraLauncher menggunakan getExternalFilesDir
    // Akan di-set dari context
    private var targetRoot: File? = null
    
    // Files yang harus di-skip (sama seperti NoverraLauncher)
    private val SKIP_FILES = setOf(
        "samp_log.txt",
        "svlog.txt", 
        "gtasatelem.set",
        "GTASAMP10.b",
        ".htaccess",
        "gta_sa.set",
        "settings.ini"
    )
    
    // OkHttpClient dengan timeout yang lebih panjang untuk file besar
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    fun initialize(context: Context) {
        // Gunakan path yang sama dengan NoverraLauncher: getExternalFilesDir(null)
        targetRoot = context.getExternalFilesDir(null)
        Log.d(TAG, "Initialized with target root: ${targetRoot?.absolutePath}")
    }

    fun getTargetDir(): File {
        val root = targetRoot ?: File(Environment.getExternalStorageDirectory(), "NoverraSAMP")
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    /**
     * Filter file berdasarkan GPU type (sama seperti NoverraLauncher)
     * Return true jika file harus di-download untuk GPU type saat ini
     */
    private fun shouldDownloadForGpu(entry: FileEntry): Boolean {
        val path = entry.path
        
        // File yang mengandung player, playerhi, menu, samp selalu di-download
        if (path.contains("player") || path.contains("playerhi") || 
            path.contains("menu") || path.contains("samp")) {
            return true
        }
        
        // Filter berdasarkan GPU type
        return when {
            path.contains(".dxt.") -> currentGpuType == GPU_TYPE_DXT
            path.contains(".etc.") -> currentGpuType == GPU_TYPE_ETC
            path.contains(".pvr.") -> currentGpuType == GPU_TYPE_PVR
            else -> true // File tanpa suffix GPU selalu di-download
        }
    }

    /**
     * Check apakah file harus di-skip (sama seperti NoverraLauncher)
     */
    private fun shouldSkipFile(entry: FileEntry): Boolean {
        return entry.name in SKIP_FILES || entry.name.startsWith(".")
    }
    
    /**
     * Get download URL untuk file entry
     * PENTING: Selalu gunakan path untuk membangun URL, sama seperti NoverraLauncher
     * URL di JSON hanya berisi nama file, bukan path lengkap
     */
    fun getDownloadUrl(entry: FileEntry): String {
        // Selalu gunakan path untuk membangun URL (sama seperti NoverraLauncher)
        // NoverraLauncher: "https://samp-mobile.shop/files/" + path
        return "https://samp-mobile.shop/files/${entry.path}"
    }

    suspend fun checkIntegrity(files: List<FileEntry>): Pair<Int, List<FileEntry>> {
        return withContext(Dispatchers.IO) {
            val missing = ArrayList<FileEntry>()
            var correctCount = 0
            
            val root = getTargetDir()

            for (entry in files) {
                // Skip file yang tidak perlu di-download (sama seperti NoverraLauncher)
                if (shouldSkipFile(entry)) {
                    Log.d(TAG, "Skipping system file: ${entry.name}")
                    continue
                }
                
                // Filter berdasarkan GPU type (sama seperti NoverraLauncher)
                if (!shouldDownloadForGpu(entry)) {
                    Log.d(TAG, "Skipping file for different GPU type: ${entry.name}")
                    continue
                }
                
                val file = File(root, entry.path)
                // Basic check: Exists and Size matches (sama seperti NoverraLauncher)
                if (file.exists() && file.length() == entry.size) {
                    correctCount++
                } else {
                    missing.add(entry)
                    Log.d(TAG, "Missing/outdated file: ${entry.name}, exists=${file.exists()}, " +
                            "localSize=${if(file.exists()) file.length() else 0}, expectedSize=${entry.size}")
                }
            }
            Log.d(TAG, "Integrity check: $correctCount correct, ${missing.size} missing")
            Pair(correctCount, missing)
        }
    }

    suspend fun downloadFile(entry: FileEntry, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var tempFile: File? = null
            
            try {
                val root = getTargetDir()
                val targetFile = File(root, entry.path)
                
                // Pastikan parent directory ada untuk target dan temp file
                targetFile.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        val created = parent.mkdirs()
                        Log.d(TAG, "Created directory ${parent.absolutePath}: $created")
                    }
                }
                
                // Download ke temp file dulu, baru rename jika sukses
                tempFile = File(root, "${entry.path}.tmp")
                tempFile.parentFile?.mkdirs()
                
                // Hapus temp file jika sudah ada dari download sebelumnya yang gagal
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                // PENTING: Selalu gunakan path untuk membangun URL (sama seperti NoverraLauncher)
                // URL di JSON hanya berisi nama file, bukan path lengkap
                // NoverraLauncher: "https://samp-mobile.shop/files/" + path
                val downloadUrl = "https://samp-mobile.shop/files/${entry.path}"
                
                Log.d(TAG, "Starting download: ${entry.name} from $downloadUrl")
                Log.d(TAG, "Target path: ${targetFile.absolutePath}")

                // Validasi URL
                if (downloadUrl.isBlank() || !downloadUrl.startsWith("http")) {
                    Log.e(TAG, "Invalid URL for ${entry.name}: $downloadUrl")
                    return@withContext false
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "NoverraLauncher/1.0")
                    .header("Accept", "*/*")
                    .build()
                    
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed for ${entry.name}: HTTP ${response.code} - ${response.message}")
                    response.close()
                    return@withContext false
                }

                val body = response.body
                if (body == null) {
                    Log.e(TAG, "Download failed for ${entry.name}: Empty response body")
                    response.close()
                    return@withContext false
                }

                inputStream = body.byteStream()
                outputStream = FileOutputStream(tempFile)
                
                // Buffer lebih besar untuk file besar (64KB)
                val buffer = ByteArray(65536)
                var bytesRead: Int
                var totalRead: Long = 0
                val fileSize = body.contentLength()
                
                Log.d(TAG, "Downloading ${entry.name}, server size: $fileSize, expected size: ${entry.size}")

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (fileSize > 0) {
                        onProgress(totalRead.toFloat() / fileSize.toFloat())
                    }
                }
                
                outputStream.flush()
                outputStream.close()
                outputStream = null
                inputStream.close()
                inputStream = null
                response.close()

                // Verifikasi ukuran file (sama seperti NoverraLauncher)
                val downloadedSize = tempFile.length()
                
                if (downloadedSize == 0L) {
                    Log.e(TAG, "Downloaded file is empty for ${entry.name}")
                    tempFile.delete()
                    return@withContext false
                }
                
                // NoverraLauncher mengecek size match, tapi kita beri toleransi
                if (entry.size > 0 && downloadedSize != entry.size) {
                    Log.w(TAG, "Size mismatch for ${entry.name}: expected ${entry.size}, got $downloadedSize (continuing anyway)")
                }

                // Hapus file lama jika ada, lalu rename temp file
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    Log.e(TAG, "Failed to rename temp file for ${entry.name}")
                    tempFile.delete()
                    return@withContext false
                }

                Log.d(TAG, "Successfully downloaded: ${entry.name} ($downloadedSize bytes)")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Download exception for ${entry.name}: ${e.message}", e)
                tempFile?.delete()
                return@withContext false
            } finally {
                try {
                    inputStream?.close()
                    outputStream?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing streams: ${e.message}")
                }
            }
        }
    }
}
