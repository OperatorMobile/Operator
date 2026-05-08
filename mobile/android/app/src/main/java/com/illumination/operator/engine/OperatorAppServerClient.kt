package com.illumination.operator.engine

import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class OperatorEngineSession(
    val handle: Long,
    val configJson: String,
)

sealed class OperatorEngineStartResult {
    data class Started(val client: OperatorAppServerClient) : OperatorEngineStartResult()
    data class Failed(val message: String) : OperatorEngineStartResult()
}

class OperatorAppServerClient private constructor(
    private val session: OperatorEngineSession,
) {
    val handle: Long = session.handle
    val configJson: String = session.configJson

    fun request(requestJson: String): String =
        OperatorEngineBridge.sendRequest(handle, requestJson)

    fun request(
        method: String,
        paramsJson: String? = null,
        id: String = nextRequestId(),
    ): String = request(requestJson(method, paramsJson, id))

    fun readAccount(
        refreshToken: Boolean = false,
        id: String = nextRequestId("account-read"),
    ): String = request(
        method = "account/read",
        paramsJson = JSONObject()
            .put("refreshToken", refreshToken)
            .toString(),
        id = id,
    )

    fun startChatGptDeviceCodeLogin(
        id: String = nextRequestId("account-login"),
    ): String = request(
        method = "account/login/start",
        paramsJson = JSONObject()
            .put("type", "chatgptDeviceCode")
            .toString(),
        id = id,
    )

    fun cancelAccountLogin(
        loginId: String,
        id: String = nextRequestId("account-login-cancel"),
    ): String = request(
        method = "account/login/cancel",
        paramsJson = JSONObject()
            .put("loginId", loginId)
            .toString(),
        id = id,
    )

    fun startThread(
        cwd: String,
        permissions: JSONObject? = workspacePermissionProfileSelection(),
        id: String = nextRequestId("thread-start"),
    ): String {
        val params = JSONObject().put("cwd", cwd)
        permissions?.let { params.put("permissions", it) }
        return request(
            method = "thread/start",
            paramsJson = params.toString(),
            id = id,
        )
    }

    fun listThreads(
        limit: Int = 8,
        archived: Boolean = false,
        id: String = nextRequestId("thread-list"),
    ): String = request(
        method = "thread/list",
        paramsJson = JSONObject()
            .put("limit", limit)
            .put("archived", archived)
            .toString(),
        id = id,
    )

    fun unarchiveThread(
        threadId: String,
        id: String = nextRequestId("thread-unarchive"),
    ): String = request(
        method = "thread/unarchive",
        paramsJson = JSONObject()
            .put("threadId", threadId)
            .toString(),
        id = id,
    )

    fun deleteThread(
        threadId: String,
        archived: Boolean,
        id: String = nextRequestId("thread-delete"),
    ): String = request(
        method = "thread/delete",
        paramsJson = JSONObject()
            .put("threadId", threadId)
            .put("archived", archived)
            .toString(),
        id = id,
    )

    fun resumeThread(
        threadId: String,
        excludeTurns: Boolean = false,
        permissions: JSONObject? = workspacePermissionProfileSelection(),
        id: String = nextRequestId("thread-resume"),
    ): String {
        val params = JSONObject()
            .put("threadId", threadId)
            .put("excludeTurns", excludeTurns)
        permissions?.let { params.put("permissions", it) }
        return request(
            method = "thread/resume",
            paramsJson = params.toString(),
            id = id,
        )
    }

    fun startTextTurn(
        threadId: String,
        cwd: String,
        text: String,
        localImagePaths: List<String> = emptyList(),
        model: String? = null,
        reasoningEffort: String? = null,
        serviceTierFast: Boolean = false,
        approvalPolicy: String? = null,
        approvalsReviewer: String? = null,
        personality: String? = null,
        permissions: JSONObject? = workspacePermissionProfileSelection(),
        collaborationMode: JSONObject? = null,
        id: String = nextRequestId("turn-start"),
    ): String {
        val input = JSONArray()
        text
            .takeIf(String::isNotBlank)
            ?.let { value ->
                input.put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", value)
                        .put("textElements", JSONArray())
                )
            }
        localImagePaths
            .filter(String::isNotBlank)
            .forEach { path ->
                input.put(
                    JSONObject()
                        .put("type", "localImage")
                        .put("path", path)
                )
            }
        if (input.length() == 0) {
            input.put(
                JSONObject()
                    .put("type", "text")
                    .put("text", text)
                    .put("textElements", JSONArray())
            )
        }

        val params = JSONObject()
            .put("threadId", threadId)
            .put("cwd", cwd)
            .put("input", input)
        model
            ?.takeIf(String::isNotBlank)
            ?.let { params.put("model", it) }
        reasoningEffort
            ?.takeIf(String::isNotBlank)
            ?.let { params.put("effort", it) }
        if (serviceTierFast) {
            params.put("serviceTier", "fast")
        }
        approvalPolicy
            ?.takeIf(String::isNotBlank)
            ?.let { params.put("approvalPolicy", it) }
        approvalsReviewer
            ?.takeIf(String::isNotBlank)
            ?.let { params.put("approvalsReviewer", it) }
        personality
            ?.takeIf(String::isNotBlank)
            ?.let { params.put("personality", it) }
        permissions?.let { params.put("permissions", it) }
        collaborationMode?.let { params.put("collaborationMode", it) }

        return request(
            method = "turn/start",
            paramsJson = params.toString(),
            id = id,
        )
    }

    fun steerTextTurn(
        threadId: String,
        turnId: String,
        text: String,
        localImagePaths: List<String> = emptyList(),
        id: String = nextRequestId("turn-steer"),
    ): String {
        val input = JSONArray()
        text
            .takeIf(String::isNotBlank)
            ?.let { value ->
                input.put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", value)
                        .put("textElements", JSONArray())
                )
            }
        localImagePaths
            .filter(String::isNotBlank)
            .forEach { path ->
                input.put(
                    JSONObject()
                        .put("type", "localImage")
                        .put("path", path)
                )
            }
        if (input.length() == 0) {
            input.put(
                JSONObject()
                    .put("type", "text")
                    .put("text", text)
                    .put("textElements", JSONArray())
            )
        }

        return request(
            method = "turn/steer",
            paramsJson = JSONObject()
                .put("threadId", threadId)
                .put("expectedTurnId", turnId)
                .put("input", input)
                .toString(),
            id = id,
        )
    }

    fun interruptTurn(
        threadId: String,
        turnId: String,
        id: String = nextRequestId("turn-interrupt"),
    ): String = request(
        method = "turn/interrupt",
        paramsJson = JSONObject()
            .put("threadId", threadId)
            .put("turnId", turnId)
            .toString(),
        id = id,
    )

    fun execCommand(
        command: List<String>,
        cwd: String? = null,
        timeoutMs: Long? = null,
        outputBytesCap: Int? = 200_000,
        processId: String? = null,
        tty: Boolean = false,
        streamStdin: Boolean = false,
        streamStdoutStderr: Boolean = false,
        disableTimeout: Boolean = false,
        disableOutputCap: Boolean = false,
        env: Map<String, String>? = null,
        terminalRows: Int? = null,
        terminalCols: Int? = null,
        id: String = nextRequestId("command-exec"),
    ): String {
        val params = JSONObject()
            .put(
                "command",
                JSONArray().also { values ->
                    command.forEach { part -> values.put(part) }
                },
            )
        cwd
            ?.takeIf(String::isNotBlank)
            ?.let { params.put("cwd", it) }
        timeoutMs?.let { params.put("timeoutMs", it) }
        outputBytesCap?.let { params.put("outputBytesCap", it) }
        processId
            ?.takeIf(String::isNotBlank)
            ?.let { params.put("processId", it) }
        if (tty) {
            params.put("tty", true)
        }
        if (streamStdin) {
            params.put("streamStdin", true)
        }
        if (streamStdoutStderr) {
            params.put("streamStdoutStderr", true)
        }
        if (disableTimeout) {
            params.put("disableTimeout", true)
        }
        if (disableOutputCap) {
            params.put("disableOutputCap", true)
        }
        if (!env.isNullOrEmpty()) {
            params.put(
                "env",
                JSONObject().also { values ->
                    env.forEach { (key, value) -> values.put(key, value) }
                },
            )
        }
        if (terminalRows != null && terminalCols != null) {
            params.put(
                "size",
                JSONObject()
                    .put("rows", terminalRows)
                    .put("cols", terminalCols),
            )
        }
        return request(
            method = "command/exec",
            paramsJson = params.toString(),
            id = id,
        )
    }

    fun writeCommandInput(
        processId: String,
        input: String,
        closeStdin: Boolean = false,
        id: String = nextRequestId("command-write"),
    ): String = request(
        method = "command/exec/write",
        paramsJson = JSONObject()
            .put("processId", processId)
            .put("deltaBase64", Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8)))
            .put("closeStdin", closeStdin)
            .toString(),
        id = id,
    )

    fun resizeCommandPty(
        processId: String,
        rows: Int,
        cols: Int,
        id: String = nextRequestId("command-resize"),
    ): String = request(
        method = "command/exec/resize",
        paramsJson = JSONObject()
            .put("processId", processId)
            .put(
                "size",
                JSONObject()
                    .put("rows", rows)
                    .put("cols", cols),
            )
            .toString(),
        id = id,
    )

    fun terminateCommand(
        processId: String,
        id: String = nextRequestId("command-terminate"),
    ): String = request(
        method = "command/exec/terminate",
        paramsJson = JSONObject()
            .put("processId", processId)
            .toString(),
        id = id,
    )

    fun nextEvent(): String? =
        OperatorEngineBridge.nextEvent(handle)

    fun respondToServerRequest(requestId: String, resultJson: String): String =
        OperatorEngineBridge.respondToServerRequest(handle, requestId, resultJson)

    fun failServerRequest(requestId: String, errorJson: String): String =
        OperatorEngineBridge.failServerRequest(handle, requestId, errorJson)

    fun shutdown(): String =
        OperatorEngineBridge.shutdownEngine(handle)

    companion object {
        private val requestCounter = AtomicLong(1)

        fun start(
            appFilesDir: String,
            codexHome: String? = null,
            workspaceRoot: String? = null,
        ): OperatorEngineStartResult {
            val configJson = OperatorEngineBridge.startConfigJson(
                appFilesDir = appFilesDir,
                codexHome = codexHome,
                workspaceRoot = workspaceRoot,
            )
            val handle = OperatorEngineBridge.startEngine(configJson)
            if (handle == 0L) {
                return OperatorEngineStartResult.Failed("embedded Codex start failed")
            }

            return OperatorEngineStartResult.Started(
                OperatorAppServerClient(
                    OperatorEngineSession(
                        handle = handle,
                        configJson = configJson,
                    )
                )
            )
        }

        fun nextRequestId(prefix: String = "operator"): String =
            "$prefix-${requestCounter.getAndIncrement()}"

        fun requestJson(
            method: String,
            paramsJson: String? = null,
            id: String = nextRequestId(),
        ): String {
            val request = JSONObject()
                .put("id", id)
                .put("method", method)

            paramsJson
                ?.takeIf(String::isNotBlank)
                ?.let { request.put("params", parseJsonValue(it)) }

            return request.toString()
        }

        private fun parseJsonValue(value: String): Any =
            runCatching { JSONTokener(value).nextValue() }.getOrElse { value }
    }
}

fun operatorPermissionProfileSelection(profileId: String? = null): JSONObject =
    JSONObject()
        .put("type", "profile")
        .put("id", profileId?.takeIf(String::isNotBlank) ?: ":workspace")

private fun workspacePermissionProfileSelection(): JSONObject =
    operatorPermissionProfileSelection(":workspace")
