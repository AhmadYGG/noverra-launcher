package com.noverra.launcher.data.model

import com.google.gson.annotations.SerializedName

data class ClientConfigResponse(
    @SerializedName("client_config") val clientConfig: ClientConfig
)

data class ClientConfig(
    @SerializedName("app_server_status") val appServerStatus: Boolean,
    @SerializedName("game_version") val gameVersion: String,
    @SerializedName("game_url") val gameUrl: String, // Likely the APK url as per user request description
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("url_launcher") val urlLauncher: String, // This is labeled as launcher.apk in request
    @SerializedName("url_cache_files") val urlCacheFiles: String
)

data class FileListResponse(
    @SerializedName("files") val files: List<FileEntry>
)

data class FileEntry(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("url") val url: String,
    @SerializedName("size") val size: Long
)
