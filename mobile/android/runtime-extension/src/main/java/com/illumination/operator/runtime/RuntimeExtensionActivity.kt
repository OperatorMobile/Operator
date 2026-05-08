package com.illumination.operator.runtime

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView

class RuntimeExtensionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val installedTools = runCatching { RuntimeToolInstaller.install(this) }
            .getOrDefault(emptyList())
            .map { it.name }
            .distinct()
            .sorted()
        val token = RuntimeExtensionState.pairingToken(this)
        val textView = TextView(this).apply {
            setTextColor(0xFFE8EAED.toInt())
            setBackgroundColor(0xFF0B0D10.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START
            setPadding(32, 32, 32, 32)
            text = buildString {
                appendLine("Operator Runtime Extension")
                appendLine()
                appendLine("Package")
                appendLine(BuildConfig.APPLICATION_ID)
                appendLine()
                appendLine("Pairing token")
                appendLine(token)
                appendLine()
                appendLine("Workspace")
                appendLine(RuntimeExtensionState.workspace(filesDir).absolutePath)
                appendLine()
                appendLine("Tools")
                if (installedTools.isEmpty()) {
                    appendLine("No packaged tools found in this build.")
                } else {
                    installedTools.forEach { appendLine(it) }
                }
            }
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(0xFF0B0D10.toInt())
                addView(textView)
            }
        )
    }
}
