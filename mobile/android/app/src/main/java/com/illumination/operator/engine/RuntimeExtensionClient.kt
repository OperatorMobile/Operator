package com.illumination.operator.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener

data class RuntimeExtensionProbe(
    val available: Boolean,
    val packageName: String?,
    val authorized: Boolean,
    val summary: String,
    val rawJson: String?,
)

object RuntimeExtensionClient {
    private const val ACTION_BIND = "com.illumination.operator.runtime.BIND"
    private const val MSG_CAPABILITIES = 1
    private const val KEY_REQUEST_ID = "requestId"
    private const val KEY_JSON = "json"
    private val candidatePackages = listOf(
        "com.illumination.operator.runtime",
        "com.illumination.operator.runtime.debug",
    )

    fun probe(
        context: Context,
        token: String? = null,
        timeoutMs: Long = 1_500L,
    ): RuntimeExtensionProbe {
        candidatePackages.forEach { packageName ->
            val result = probePackage(context, packageName, token, timeoutMs)
            if (result.available) {
                return result
            }
        }
        return RuntimeExtensionProbe(
            available = false,
            packageName = null,
            authorized = false,
            summary = "not installed",
            rawJson = null,
        )
    }

    private fun probePackage(
        context: Context,
        packageName: String,
        token: String?,
        timeoutMs: Long,
    ): RuntimeExtensionProbe {
        val thread = HandlerThread("OperatorRuntimeExtensionProbe").apply { start() }
        val latch = CountDownLatch(1)
        var responseJson: String? = null
        val reply = Messenger(
            object : Handler(thread.looper) {
                override fun handleMessage(msg: Message) {
                    responseJson = msg.data.getString(KEY_JSON)
                    latch.countDown()
                }
            }
        )
        var bound = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val request = JSONObject()
                token?.takeIf(String::isNotBlank)?.let { request.put("token", it) }
                val message = Message.obtain(null, MSG_CAPABILITIES).apply {
                    replyTo = reply
                    data = Bundle().apply {
                        putString(KEY_REQUEST_ID, "runtime-probe")
                        putString(KEY_JSON, request.toString())
                    }
                }
                runCatching { Messenger(service).send(message) }
                    .onFailure { latch.countDown() }
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        return try {
            val intent = Intent(ACTION_BIND).setPackage(packageName)
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                RuntimeExtensionProbe(
                    available = false,
                    packageName = packageName,
                    authorized = false,
                    summary = "not installed",
                    rawJson = null,
                )
            } else if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                RuntimeExtensionProbe(
                    available = true,
                    packageName = packageName,
                    authorized = false,
                    summary = "installed; no response",
                    rawJson = null,
                )
            } else {
                probeFromJson(packageName, responseJson)
            }
        } catch (error: SecurityException) {
            RuntimeExtensionProbe(
                available = true,
                packageName = packageName,
                authorized = false,
                summary = "installed; permission denied",
                rawJson = null,
            )
        } catch (error: Exception) {
            RuntimeExtensionProbe(
                available = false,
                packageName = packageName,
                authorized = false,
                summary = error.message ?: error.javaClass.simpleName,
                rawJson = null,
            )
        } finally {
            if (bound) {
                runCatching { context.unbindService(connection) }
            }
            thread.quitSafely()
        }
    }

    private fun probeFromJson(packageName: String, rawJson: String?): RuntimeExtensionProbe {
        val payload = parseObject(rawJson)
        val ok = payload.optBoolean("ok", false)
        val authorized = payload.optBoolean("authorized", false)
        val tools = payload.optJSONArray("tools")?.length() ?: 0
        val summary = when {
            !ok -> payload.optString("error").takeIf(String::isNotBlank) ?: "installed; error"
            authorized -> "paired; $tools tools"
            else -> "installed; pairing required"
        }
        return RuntimeExtensionProbe(
            available = ok,
            packageName = packageName,
            authorized = authorized,
            summary = summary,
            rawJson = rawJson,
        )
    }

    private fun parseObject(rawJson: String?): JSONObject =
        runCatching {
            when (val parsed = JSONTokener(rawJson ?: "{}").nextValue()) {
                is JSONObject -> parsed
                else -> JSONObject()
            }
        }.getOrDefault(JSONObject())
}
