package com.noverra.launcher.data

import android.content.Context
import android.os.Environment
import com.noverra.launcher.data.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object GameIntegrityManager {

    // Target Path: /storage/emulated/0/SAMP (Easier for User to access/verify)
    // Or Android/data/com.samp.mobile/files if we have permission.
    // For this MVP we'll use a specific folder "NoverraSAMP" in root to verify logic.
    private val TARGET_ROOT = File(Environment.getExternalStorageDirectory(), "NoverraSAMP")
    private val client = OkHttpClient()

    fun getTargetDir(): File {
        if (!TARGET_ROOT.exists()) {
            TARGET_ROOT.mkdirs()
        }
        return TARGET_ROOT
    }

    suspend fun checkIntegrity(files: List<FileEntry>): Pair<Int, List<FileEntry>> {
        return withContext(Dispatchers.IO) {
            val missing = ArrayList<FileEntry>()
            var correctCount = 0
            
            if (!TARGET_ROOT.exists()) TARGET_ROOT.mkdirs()

            for (entry in files) {
                val file = File(TARGET_ROOT, entry.path)
                // Basic check: Exists and Size matches (approx)
                if (file.exists() && file.length() == entry.size) {
                    correctCount++
                } else {
                    missing.add(entry)
                }
            }
            Pair(correctCount, missing)
        }
    }

    suspend fun downloadFile(entry: FileEntry, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val targetFile = File(TARGET_ROOT, entry.path)
                targetFile.parentFile?.mkdirs()

                val request = Request.Builder().url(entry.url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) return@withContext false

                response.body?.let { body ->
                    val inputStream: InputStream = body.byteStream()
                    val outputStream = FileOutputStream(targetFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead: Long = 0
                    val fileSize = body.contentLength()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (fileSize > 0) {
                            onProgress(totalRead.toFloat() / fileSize.toFloat())
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
