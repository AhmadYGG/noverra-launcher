package com.noverra.launcher.data

import com.google.gson.Gson
import com.noverra.launcher.data.model.ClientConfig
import com.noverra.launcher.data.model.ClientConfigResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object GameRepository {

    private val client = OkHttpClient()
    private val gson = Gson()
    private const val CONFIG_URL = "https://samp-mobile.shop/client_config.json"

    fun fetchConfig(callback: (Result<ClientConfig>) -> Unit) {
        val request = Request.Builder().url(CONFIG_URL).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("Unexpected code $response")))
                        return
                    }

                    try {
                        val body = it.body?.string()
                        val configResponse = gson.fromJson(body, ClientConfigResponse::class.java)
                        callback(Result.success(configResponse.clientConfig))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun fetchFileList(url: String, callback: (Result<List<com.noverra.launcher.data.model.FileEntry>>) -> Unit) {
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("fetchFileList failed: $response")))
                        return
                    }

                    try {
                        val body = it.body?.string()
                        val listResponse = gson.fromJson(body, com.noverra.launcher.data.model.FileListResponse::class.java)
                        callback(Result.success(listResponse.files))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
}
