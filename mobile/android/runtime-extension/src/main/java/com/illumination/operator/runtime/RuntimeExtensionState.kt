package com.illumination.operator.runtime

import android.content.Context
import java.io.File
import java.util.UUID

object RuntimeExtensionState {
    private const val PREFS = "operator-runtime-extension"
    private const val KEY_PAIRING_TOKEN = "pairingToken"
    private const val ANDROID_SYSTEM_PATH =
        "/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin"

    fun pairingToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_PAIRING_TOKEN, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PAIRING_TOKEN, created).apply()
        return created
    }

    fun toolsBin(filesDir: File): File = File(filesDir, "tools/bin")

    fun workspace(filesDir: File): File = File(filesDir, "workspaces/default")

    fun codexHome(filesDir: File): File = File(filesDir, "codex-home")

    fun tmp(filesDir: File): File = File(filesDir, "tmp-run")

    fun shellPath(filesDir: File): String =
        listOf(
            "node_modules/.bin",
            toolsBin(filesDir).absolutePath,
            File(filesDir, "tools/python/bin").absolutePath,
            File(filesDir, "tools/python-user/bin").absolutePath,
            File(filesDir, "tools/node-global/bin").absolutePath,
            File(filesDir, "tools/toolchain/usr/bin").absolutePath,
            ANDROID_SYSTEM_PATH,
        ).joinToString(":")
}
