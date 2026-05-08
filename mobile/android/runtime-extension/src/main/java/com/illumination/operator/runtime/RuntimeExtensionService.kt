package com.illumination.operator.runtime

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class RuntimeExtensionService : Service() {
    private val worker: ExecutorService = Executors.newCachedThreadPool()
    private val messenger by lazy {
        Messenger(IncomingHandler(this))
    }
    @Volatile
    private var installedTools: List<File> = emptyList()

    override fun onCreate() {
        super.onCreate()
        installedTools = runCatching { RuntimeToolInstaller.install(this) }
            .onFailure { Log.w(LOG_TAG, "runtime tool install failed", it) }
            .getOrDefault(emptyList())
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != RuntimeExtensionProtocol.ACTION_BIND) {
            return null
        }
        return messenger.binder
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun handleMessage(message: Message) {
        val requestId = message.data.getString(RuntimeExtensionProtocol.KEY_REQUEST_ID)
        val params = parseJsonObject(message.data.getString(RuntimeExtensionProtocol.KEY_JSON))
        when (message.what) {
            RuntimeExtensionProtocol.MSG_CAPABILITIES -> {
                sendJson(
                    replyTo = message.replyTo,
                    what = RuntimeExtensionProtocol.MSG_CAPABILITIES,
                    requestId = requestId,
                    payload = capabilitiesJson(params),
                )
            }
            RuntimeExtensionProtocol.MSG_EXEC -> {
                worker.execute {
                    val payload = runCatching {
                        if (!isAuthorized(params)) {
                            errorJson("runtime extension pairing token is required")
                        } else {
                            execJson(params)
                        }
                    }.getOrElse { errorJson(it.message ?: it.javaClass.simpleName) }
                    sendJson(
                        replyTo = message.replyTo,
                        what = RuntimeExtensionProtocol.MSG_EXEC,
                        requestId = requestId,
                        payload = payload,
                    )
                }
            }
            else -> {
                sendJson(
                    replyTo = message.replyTo,
                    what = message.what,
                    requestId = requestId,
                    payload = errorJson("unknown runtime extension message ${message.what}"),
                )
            }
        }
    }

    private fun capabilitiesJson(params: JSONObject): JSONObject {
        val toolNames = installedTools
            .map { it.name }
            .distinct()
            .sorted()
        return JSONObject()
            .put("ok", true)
            .put("package", packageName)
            .put("applicationId", BuildConfig.APPLICATION_ID)
            .put("targetSdk", applicationInfo.targetSdkVersion)
            .put("authorized", isAuthorized(params))
            .put("tokenRequired", true)
            .put("workspace", RuntimeExtensionState.workspace(filesDir).absolutePath)
            .put("codexHome", RuntimeExtensionState.codexHome(filesDir).absolutePath)
            .put("tools", JSONArray(toolNames))
            .put(
                "capabilities",
                JSONArray(
                    listOf(
                        "exec",
                        "cancel",
                        "app-private-files",
                        "packaged-tools",
                        "runtime-assets",
                    )
                )
            )
    }

    private fun execJson(params: JSONObject): JSONObject {
        val command = commandFromParams(params)
            ?: return errorJson("command is required")
        val cwd = params.optString("cwd")
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?: RuntimeExtensionState.workspace(filesDir)
        require(cwd.exists() || cwd.mkdirs()) {
            "failed to create ${cwd.absolutePath}"
        }

        val timeoutMs = params.optLong("timeoutMs", DEFAULT_TIMEOUT_MS)
            .coerceIn(1_000L, MAX_TIMEOUT_MS)
        val outputBytesCap = params.optInt("outputBytesCap", DEFAULT_OUTPUT_CAP)
            .coerceIn(4_096, MAX_OUTPUT_CAP)

        val processBuilder = ProcessBuilder("/system/bin/sh", "-lc", command)
            .directory(cwd)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
        configureEnvironment(processBuilder.environment())
        val process = processBuilder.start()
        val stdout = worker.submit<String> { readCapped(process.inputStream, outputBytesCap) }
        val stderr = worker.submit<String> { readCapped(process.errorStream, outputBytesCap) }
        val exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!exited) {
            process.destroyForcibly()
        }

        return JSONObject()
            .put("ok", exited)
            .put("timedOut", !exited)
            .put("exitCode", if (exited) process.exitValue() else JSONObject.NULL)
            .put("cwd", cwd.absolutePath)
            .put("stdout", stdout.get(1, TimeUnit.SECONDS))
            .put("stderr", stderr.get(1, TimeUnit.SECONDS))
    }

    private fun configureEnvironment(env: MutableMap<String, String>) {
        val tmp = RuntimeExtensionState.tmp(filesDir)
        val codexHome = RuntimeExtensionState.codexHome(filesDir)
        val xdgCache = File(filesDir, "xdg/cache")
        val xdgConfig = File(filesDir, "xdg/config")
        val xdgData = File(filesDir, "xdg/data")
        val tools = File(filesDir, "tools")
        val caBundle = File(tools, "cacert.pem")
        val toolchainPrefix = File(tools, "toolchain/usr")
        env["HOME"] = codexHome.absolutePath
        env["USER"] = "operator"
        env["LOGNAME"] = "operator"
        env["PATH"] = RuntimeExtensionState.shellPath(filesDir)
        env["TMPDIR"] = tmp.absolutePath
        env["TMP"] = tmp.absolutePath
        env["TEMP"] = tmp.absolutePath
        env["XDG_CACHE_HOME"] = xdgCache.absolutePath
        env["XDG_CONFIG_HOME"] = xdgConfig.absolutePath
        env["XDG_DATA_HOME"] = xdgData.absolutePath
        env["GIT_EXEC_PATH"] = RuntimeExtensionState.toolsBin(filesDir).absolutePath
        env["GIT_CONFIG_NOSYSTEM"] = "1"
        env["GIT_TERMINAL_PROMPT"] = "0"
        env["GIT_TEMPLATE_DIR"] = File(tools, "share/git-core/templates").absolutePath
        env["GIT_SSL_CAINFO"] = caBundle.absolutePath
        env["SSL_CERT_FILE"] = caBundle.absolutePath
        env["REQUESTS_CA_BUNDLE"] = caBundle.absolutePath
        env["PIP_CERT"] = caBundle.absolutePath
        env["CURL_CA_BUNDLE"] = caBundle.absolutePath
        env["PYTHONUSERBASE"] = File(tools, "python-user").absolutePath
        env["PYTHONPYCACHEPREFIX"] = File(tmp, "python-pycache").absolutePath
        env["PIP_CACHE_DIR"] = File(xdgCache, "pip").absolutePath
        env["PIP_DISABLE_PIP_VERSION_CHECK"] = "1"
        env["NODE_HOME"] = File(tools, "node").absolutePath
        env["NODE_PATH"] = File(tools, "node/lib/node_modules").absolutePath
        env["NPM_CONFIG_CACHE"] = File(xdgCache, "npm").absolutePath
        env["NPM_CONFIG_PREFIX"] = File(tools, "node-global").absolutePath
        env["NPM_CONFIG_TMP"] = File(tmp, "npm").absolutePath
        env["NPM_CONFIG_UPDATE_NOTIFIER"] = "false"
        env["NPM_CONFIG_FUND"] = "false"
        env["NPM_CONFIG_AUDIT"] = "false"
        env["TERMUX_PREFIX"] = toolchainPrefix.absolutePath
        env["PREFIX"] = toolchainPrefix.absolutePath
        env["LD_LIBRARY_PATH"] = listOf(
            File(tools, "lib").absolutePath,
            applicationInfo.nativeLibraryDir,
            File(tools, "python/lib").absolutePath,
            File(tools, "python-dev-libs/lib").absolutePath,
            File(toolchainPrefix, "lib").absolutePath,
        ).joinToString(":")
        env["PAGER"] = "cat"
        env["GIT_PAGER"] = "cat"
    }

    private fun commandFromParams(params: JSONObject): String? {
        val value = params.opt("command") ?: return null
        return when (value) {
            is String -> value.takeIf(String::isNotBlank)
            is JSONArray -> {
                (0 until value.length())
                    .map { index -> shellQuote(value.optString(index)) }
                    .joinToString(" ")
                    .takeIf(String::isNotBlank)
            }
            else -> null
        }
    }

    private fun isAuthorized(params: JSONObject): Boolean =
        params.optString("token") == RuntimeExtensionState.pairingToken(this)

    private fun sendJson(
        replyTo: Messenger?,
        what: Int,
        requestId: String?,
        payload: JSONObject,
    ) {
        if (replyTo == null) {
            return
        }
        val data = Bundle().apply {
            requestId?.let { putString(RuntimeExtensionProtocol.KEY_REQUEST_ID, it) }
            putString(RuntimeExtensionProtocol.KEY_JSON, payload.toString())
        }
        val response = Message.obtain(null, what).apply {
            this.data = data
        }
        runCatching { replyTo.send(response) }
            .onFailure { Log.w(LOG_TAG, "failed to send runtime response", it) }
    }

    private class IncomingHandler(
        private val service: RuntimeExtensionService,
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            service.handleMessage(msg)
        }
    }

    companion object {
        private const val LOG_TAG = "OperatorRuntime"
        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val MAX_TIMEOUT_MS = 30 * 60_000L
        private const val DEFAULT_OUTPUT_CAP = 200_000
        private const val MAX_OUTPUT_CAP = 2_000_000

        private fun parseJsonObject(value: String?): JSONObject =
            runCatching {
                when (val parsed = JSONTokener(value ?: "{}").nextValue()) {
                    is JSONObject -> parsed
                    else -> JSONObject()
                }
            }.getOrDefault(JSONObject())

        private fun errorJson(message: String): JSONObject =
            JSONObject()
                .put("ok", false)
                .put("error", message)

        private fun readCapped(input: InputStream, outputBytesCap: Int): String {
            val bytes = input.readBytes()
            val capped = if (bytes.size > outputBytesCap) {
                bytes.copyOf(outputBytesCap)
            } else {
                bytes
            }
            val suffix = if (bytes.size > outputBytesCap) {
                "\n[output truncated at $outputBytesCap bytes]"
            } else {
                ""
            }
            return capped.toString(Charsets.UTF_8) + suffix
        }

        private fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
