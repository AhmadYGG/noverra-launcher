package com.noverra.launcher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object SAMPManager {
    const val SAMP_PACKAGE = "com.samp.mobile"

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SAMP_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchGame(context: Context, playerName: String, serverIp: String, serverPort: Int) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SAMP_PACKAGE)
        launchIntent?.apply {
            putExtra("player_name", playerName)
            putExtra("server_ip", serverIp)
            putExtra("server_port", serverPort)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }
}
