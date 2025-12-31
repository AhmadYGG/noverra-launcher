package com.noverra.launcher.data

import android.util.Log
import com.google.gson.Gson
import com.noverra.launcher.data.model.ClientConfig
import com.noverra.launcher.data.model.ClientConfigResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object GameRepository {

    private const val TAG = "GameRepository"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
        
    private val gson = Gson()
    private const val CONFIG_URL = "https://samp-mobile.shop/client_config.json"

    fun fetchConfig(callback: (Result<ClientConfig>) -> Unit) {
        Log.d(TAG, "Fetching config from: $CONFIG_URL")
        val request = Request.Builder()
            .url(CONFIG_URL)
            .header("User-Agent", "NoverraLauncher/1.0")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "fetchConfig failed: ${e.message}", e)
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "fetchConfig HTTP error: ${response.code} - ${response.message}")
                        callback(Result.failure(IOException("Unexpected code $response")))
                        return
                    }

                    try {
                        val body = it.body?.string()
                        Log.d(TAG, "Config response: $body")
                        val configResponse = gson.fromJson(body, ClientConfigResponse::class.java)
                        Log.d(TAG, "Parsed config - urlCacheFiles: ${configResponse.clientConfig.urlCacheFiles}")
                        callback(Result.success(configResponse.clientConfig))
                    } catch (e: Exception) {
                        Log.e(TAG, "fetchConfig parse error: ${e.message}", e)
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun fetchFileList(url: String, callback: (Result<List<com.noverra.launcher.data.model.FileEntry>>) -> Unit) {
        Log.d(TAG, "Fetching file list from: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NoverraLauncher/1.0")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "fetchFileList failed: ${e.message}", e)
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "fetchFileList HTTP error: ${response.code} - ${response.message}")
                        callback(Result.failure(IOException("fetchFileList failed: HTTP ${response.code}")))
                        return
                    }

                    try {
                        val body = it.body?.string()
                        Log.d(TAG, "FileList response length: ${body?.length ?: 0}")
                        val listResponse = gson.fromJson(body, com.noverra.launcher.data.model.FileListResponse::class.java)
                        Log.d(TAG, "Parsed ${listResponse.files.size} files from list")
                        
                        // Log beberapa file pertama untuk debugging
                        listResponse.files.take(3).forEach { file ->
                            Log.d(TAG, "File entry: name=${file.name}, url=${file.url}, size=${file.size}")
                        }
                        
                        callback(Result.success(listResponse.files))
                    } catch (e: Exception) {
                        Log.e(TAG, "fetchFileList parse error: ${e.message}", e)
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
}
