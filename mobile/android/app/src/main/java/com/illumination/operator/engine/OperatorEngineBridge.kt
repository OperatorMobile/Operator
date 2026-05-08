package com.illumination.operator.engine

import android.util.Log
import java.io.File
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object OperatorEngineBridge {
    private const val LOG_TAG = "OperatorSmoke"
    private const val SMOKE_TURN_PROMPT =
        "Operator Android smoke turn. Reply with one short sentence."

    private val loadFailure = runCatching {
        System.loadLibrary("operator_mobile_engine")
    }.exceptionOrNull()

    fun startConfigJson(
        appFilesDir: String,
        codexHome: String? = null,
        workspaceRoot: String? = null,
    ): String = JSONObject()
        .put("mode", "embedded")
        .put("appFilesDir", appFilesDir)
        .apply {
            codexHome?.takeIf(String::isNotBlank)?.let { put("codexHome", it) }
            workspaceRoot?.takeIf(String::isNotBlank)?.let { put("workspaceRoot", it) }
        }
        .toString()

    fun engineStatus(): String {
        val failure = loadFailure
        if (failure != null) {
            return "native bridge unavailable: ${failure.javaClass.simpleName}"
        }

        return runCatching { nativeEngineStatus() }
            .getOrElse { "native bridge call failed: ${it.javaClass.simpleName}" }
    }

    fun startEngine(configJson: String): Long {
        val failure = loadFailure
        if (failure != null) {
            return 0L
        }

        return runCatching { nativeStartEngine(configJson) }.getOrElse { 0L }
    }

    fun sendRequest(handle: Long, requestJson: String): String {
        val failure = loadFailure
        if (failure != null) {
            return bridgeUnavailablePayload(failure)
        }

        return runCatching { nativeSendRequest(handle, requestJson) }
            .getOrElse { bridgeCallFailedPayload(it) }
    }

    fun nextEvent(handle: Long): String? {
        val failure = loadFailure
        if (failure != null) {
            return bridgeUnavailablePayload(failure)
        }

        return runCatching { nativeNextEvent(handle) }
            .getOrElse { bridgeCallFailedPayload(it) }
    }

    fun respondToServerRequest(handle: Long, requestId: String, resultJson: String): String {
        val failure = loadFailure
        if (failure != null) {
            return bridgeUnavailablePayload(failure)
        }

        return runCatching { nativeRespondToServerRequest(handle, requestId, resultJson) }
            .getOrElse { bridgeCallFailedPayload(it) }
    }

    fun failServerRequest(handle: Long, requestId: String, errorJson: String): String {
        val failure = loadFailure
        if (failure != null) {
            return bridgeUnavailablePayload(failure)
        }

        return runCatching { nativeFailServerRequest(handle, requestId, errorJson) }
            .getOrElse { bridgeCallFailedPayload(it) }
    }

    fun shutdownEngine(handle: Long): String {
        val failure = loadFailure
        if (failure != null) {
            return bridgeUnavailablePayload(failure)
        }

        return runCatching { nativeShutdownEngine(handle) }
            .getOrElse { bridgeCallFailedPayload(it) }
    }

    fun lifecycleSmokeStatus(
        appFilesDir: String,
        includeTurnSmoke: Boolean = false,
        includeCommandSmoke: Boolean = false,
    ): String {
        val failure = loadFailure
        if (failure != null) {
            return "native bridge unavailable: ${failure.javaClass.simpleName}"
        }

        return runCatching {
            val workspaceRoot = File(appFilesDir, "workspaces/default").absolutePath
            val configJson = startConfigJson(
                appFilesDir = appFilesDir,
                workspaceRoot = workspaceRoot,
            )
            val handle = startEngine(configJson)
            if (handle == 0L) {
                return@runCatching "embedded Codex start failed: ${nativeLastStartError() ?: "unknown error"}"
            }

            val lines = mutableListOf("handle=$handle", "initialize=startEngine(in-process)")
            fun record(line: String) {
                lines += line
                Log.i(LOG_TAG, line)
            }

            lines.forEach { Log.i(LOG_TAG, it) }

            var threadId: String? = null
            var hasRolloutFile = false
            try {
                val configResponse = sendRequest(
                    handle,
                    smokeRequestJson(
                        id = "smoke-config",
                        method = "configRequirements/read",
                    )
                )
                record("config=$configResponse")

                val threadStartResponse = sendRequest(
                    handle,
                    smokeRequestJson(
                        id = "smoke-thread-start",
                        method = "thread/start",
                        params = JSONObject()
                            .put("cwd", workspaceRoot),
                    )
                )
                record("threadStart=$threadStartResponse")
                threadId = threadIdFromThreadResponse(threadStartResponse)
                threadId?.let { id ->
                    record("threadId=$id")
                    val rolloutPath = threadPathFromThreadResponse(threadStartResponse)
                    hasRolloutFile = rolloutPath?.let { File(it).isFile } == true
                    record(
                        "threadRollout=" + if (hasRolloutFile) {
                            "materialized"
                        } else {
                            "not materialized"
                        }
                    )

                    record(
                        "threadRead=" + sendRequest(
                            handle,
                            smokeRequestJson(
                                id = "smoke-thread-read",
                                method = "thread/read",
                                params = JSONObject()
                                    .put("threadId", id)
                                    .put("includeTurns", false),
                            )
                        )
                    )

                    record(
                        "threadList=" + sendRequest(
                            handle,
                            smokeRequestJson(
                                id = "smoke-thread-list",
                                method = "thread/list",
                                params = JSONObject()
                                    .put("limit", 5),
                            )
                        )
                    )
                }

                val accountResponse = sendRequest(
                    handle,
                    smokeRequestJson(
                        id = "smoke-account-read",
                        method = "account/read",
                        params = JSONObject()
                            .put("refreshToken", false),
                    )
                )
                record("account=$accountResponse")

                if (includeCommandSmoke) {
                    runCommandExecSmoke(handle, workspaceRoot, ::record)
                }

                if (includeTurnSmoke) {
                    if (threadId == null) {
                        record("turnStart=not attempted: thread id unavailable")
                    } else if (!accountCanStartTurn(accountResponse)) {
                        record("turnStart=not attempted: account unavailable")
                    } else {
                        runTurnStartSmoke(handle, threadId, workspaceRoot, ::record)
                    }
                }

                val eventDrainLimit = if (includeCommandSmoke) 32 else 8
                drainEvents(handle, maxEvents = eventDrainLimit).forEachIndexed { index, event ->
                    record("event.${index + 1}=$event")
                }
            } finally {
                record("shutdown=${shutdownEngine(handle)}")
            }

            if (!hasRolloutFile) {
                record("threadResume=not attempted: rollout file is not materialized")
            } else threadId?.let { id ->
                val resumeHandle = startEngine(configJson)
                if (resumeHandle == 0L) {
                    record("resumeHandle=0")
                    record("resumeStartError=${nativeLastStartError() ?: "unknown error"}")
                } else {
                    record("resumeHandle=$resumeHandle")
                    try {
                        val resumeResponse = sendRequest(
                            resumeHandle,
                            smokeRequestJson(
                                id = "smoke-thread-resume",
                                method = "thread/resume",
                                params = JSONObject()
                                    .put("threadId", id)
                                    .put("excludeTurns", true),
                            )
                        )
                        record("threadResume=$resumeResponse")
                        drainEvents(resumeHandle, maxEvents = 8).forEachIndexed { index, event ->
                            record("resumeEvent.${index + 1}=$event")
                        }
                    } finally {
                        record("resumeShutdown=${shutdownEngine(resumeHandle)}")
                    }
                }
            }

            lines.joinToString(separator = "\n")
        }.getOrElse {
            "native bridge lifecycle failed: ${it.javaClass.simpleName}"
                .also { message -> Log.e(LOG_TAG, message, it) }
        }
    }

    private fun smokeRequestJson(
        id: String,
        method: String,
        params: JSONObject? = null,
    ): String = JSONObject()
        .put("id", id)
        .put("method", method)
        .apply {
            params?.let { put("params", it) }
        }
        .toString()

    private fun runCommandExecSmoke(
        handle: Long,
        workspaceRoot: String,
        record: (String) -> Unit,
    ) {
        val cwdResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-cwd",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("printf 'cwd=%s\\n' \"\$PWD\"; printf 'PATH=%s\\n' \"\$PATH\"")
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExec=$cwdResponse")

        val pathShellResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-path-shell",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("sh")
                            .put("-c")
                            .put("printf 'path-shell-ok\\n'; printf 'shell-path=%s\\n' \"\$(command -v sh 2>/dev/null || true)\"")
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecPathShell=$pathShellResponse")

        val nonzeroResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-nonzero",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("printf 'stderr-smoke\\n' >&2; exit 7")
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecNonzero=$nonzeroResponse")

        val outputCapResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-output-cap",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("printf 'abcdef'; printf 'uvwxyz' >&2")
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 5),
            )
        )
        record("commandExecOutputCap=$outputCapResponse")

        val timeoutResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-timeout",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("python3 -c 'import time; time.sleep(5)'")
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 200)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecTimeout=$timeoutResponse")

        val missingCommandResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-missing",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/operator-command-does-not-exist")
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecMissing=$missingCommandResponse")

        val toolboxResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-toolbox",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                "missing=\"\"; " +
                                    "for tool in sh ls cat sed grep find mkdir rm cp mv chmod env pwd; do " +
                                    "command -v \"\$tool\" >/dev/null 2>&1 || missing=\"\$missing \$tool\"; " +
                                    "done; " +
                                    "rm -rf tool-smoke; mkdir -p tool-smoke; " +
                                    "printf 'alpha\\nbeta\\n' > tool-smoke/source.txt; " +
                                    "cp tool-smoke/source.txt tool-smoke/copy.txt; " +
                                    "mv tool-smoke/copy.txt tool-smoke/moved.txt; " +
                                    "chmod 600 tool-smoke/moved.txt; " +
                                    "cat tool-smoke/moved.txt | grep beta | sed 's/beta/BETA/'; " +
                                    "find tool-smoke -name moved.txt -print; " +
                                    "pwd; env | grep '^PATH='; " +
                                    "rm -rf tool-smoke; " +
                                    "printf 'missing=%s\\n' \"\$missing\""
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecToolbox=$toolboxResponse")

        val toolInventoryResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-tool-inventory",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                "for tool in rg git git-remote-http git-remote-https apply_patch python3 pip node npm npx mktemp which; do " +
                                    "path=\$(command -v \"\$tool\" 2>/dev/null || true); " +
                                    "if [ -n \"\$path\" ]; then " +
                                    "printf '%s=present:%s\\n' \"\$tool\" \"\$path\"; " +
                                    "else printf '%s=missing\\n' \"\$tool\"; fi; " +
                                    "done"
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecToolInventory=$toolInventoryResponse")

        val runtimeResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-runtime",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                """
                                set -e
                                for tool in python3 pip node npm mktemp; do
                                  command -v "${'$'}tool"
                                done
                                python3 -V
                                python3 -c 'import ssl, tempfile; print("python-ok", tempfile.gettempdir(), ssl.get_default_verify_paths().cafile)'
                                python3 -m pip --version
                                tmp="${'$'}(mktemp -d "${'$'}TMPDIR/operator-runtime.XXXXXX")"
                                python3 -m venv "${'$'}tmp/venv"
                                "${'$'}tmp/venv/bin/python" -m pip --version
                                node -v
                                node -e 'console.log("node-ok")'
                                npm -v
                                rm -rf "${'$'}tmp"
                                """.trimIndent()
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 20000)
                    .put("outputBytesCap", 16384),
            )
        )
        record("commandExecRuntime=$runtimeResponse")

        val ripgrepResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-ripgrep",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                "rm -rf rg-smoke; mkdir -p rg-smoke; " +
                                    "printf 'alpha\\nbeta\\n' > rg-smoke/note.txt; " +
                                    "rg --line-number beta rg-smoke; " +
                                    "rm -rf rg-smoke"
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecRipgrep=$ripgrepResponse")

        val applyPatchResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-apply-patch",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                """
                                rm -f patch-smoke.txt
                                apply_patch <<'PATCH'
                                *** Begin Patch
                                *** Add File: patch-smoke.txt
                                +patched from android
                                *** End Patch
                                PATCH
                                cat patch-smoke.txt
                                rm -f patch-smoke.txt
                                """.trimIndent()
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecApplyPatch=$applyPatchResponse")

        val gitResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-git",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                """
                                set -e
                                rm -rf git-smoke git-home
                                mkdir -p git-smoke git-home
                                export HOME="${'$'}PWD/git-home"
                                export GIT_CONFIG_NOSYSTEM=1
                                export GIT_PAGER=cat
                                export PAGER=cat
                                cd git-smoke
                                git --exec-path
                                git --version
                                git init -q -b main
                                git config user.email operator@example.invalid
                                git config user.name Operator
                                printf 'alpha\n' > note.txt
                                git add note.txt
                                git commit -q -m init
                                printf 'beta\n' >> note.txt
                                git status --porcelain
                                git diff -- note.txt
                                printf 'one\n' > apply-target.txt
                                git add apply-target.txt
                                git commit -q -m apply-base
                                cat > apply.patch <<'PATCH'
                                diff --git a/apply-target.txt b/apply-target.txt
                                --- a/apply-target.txt
                                +++ b/apply-target.txt
                                @@ -1 +1,2 @@
                                 one
                                +two
                                PATCH
                                git apply --check apply.patch
                                git apply apply.patch
                                cat apply-target.txt
                                git rev-parse --abbrev-ref HEAD
                                git rev-parse --verify HEAD
                                git merge-base HEAD HEAD
                                git ls-files
                                cd ..
                                rm -rf git-smoke git-home
                                """.trimIndent()
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 10000)
                    .put("outputBytesCap", 16384),
            )
        )
        record("commandExecGit=$gitResponse")

        val networkResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-network",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                """
                                set -eu
                                command -v gh
                                gh --version | sed -n '1p'
                                gh_status_file="${'$'}TMPDIR/gh-auth-status.${'$'}${'$'}"
                                set +e
                                gh auth status --hostname github.com >"${'$'}gh_status_file" 2>&1
                                gh_status="${'$'}?"
                                set -e
                                cat "${'$'}gh_status_file"
                                if grep -E '(\[::1\]:53|connection refused|no such host)' "${'$'}gh_status_file" >/dev/null 2>&1; then
                                  rm -f "${'$'}gh_status_file"
                                  exit 17
                                fi
                                rm -f "${'$'}gh_status_file"
                                printf 'gh-auth-status-exit=%s\n' "${'$'}gh_status"
                                git ls-remote https://github.com/git/git.git HEAD | sed -n '1p'
                                """.trimIndent()
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 30000)
                    .put("outputBytesCap", 32768),
            )
        )
        record("commandExecNetwork=$networkResponse")

        val streamResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-stream",
                method = "command/exec",
                params = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("printf 'stream-stdout\\n'; printf 'stream-stderr\\n' >&2")
                    )
                    .put("processId", "smoke-stream")
                    .put("streamStdoutStderr", true)
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 5000)
                    .put("outputBytesCap", 4096),
            )
        )
        record("commandExecStream=$streamResponse")

        val stdinProcessId = "smoke-stdin"
        var stdinCommandResponse: String? = null
        val stdinCommandThread = Thread {
            stdinCommandResponse = sendRequest(
                handle,
                smokeRequestJson(
                    id = "smoke-command-exec-stdin",
                    method = "command/exec",
                    params = JSONObject()
                        .put(
                            "command",
                            JSONArray()
                                .put("/system/bin/sh")
                                .put("-c")
                                .put("IFS= read line; printf 'stdin=%s\\n' \"\$line\"")
                        )
                        .put("processId", stdinProcessId)
                        .put("streamStdin", true)
                        .put("cwd", workspaceRoot)
                        .put("timeoutMs", 5000)
                        .put("outputBytesCap", 4096),
                )
            )
        }
        stdinCommandThread.start()
        val stdinWriteResponse = writeCommandInput(
            handle = handle,
            processId = stdinProcessId,
            input = "hello-android\n",
        )
        record("commandExecStdinWrite=$stdinWriteResponse")
        stdinCommandThread.join(5000)
        record("commandExecStdin=${stdinCommandResponse ?: "timeout"}")

        val ptyProcessId = "smoke-pty"
        var ptyCommandResponse: String? = null
        val ptyCommandThread = Thread {
            ptyCommandResponse = sendRequest(
                handle,
                smokeRequestJson(
                    id = "smoke-command-exec-pty",
                    method = "command/exec",
                    params = JSONObject()
                        .put(
                            "command",
                            JSONArray()
                                .put("/system/bin/sh")
                                .put("-c")
                                .put(
                                    "stty -echo 2>/dev/null || true; " +
                                        "if [ -t 0 ]; then printf 'tty\\n'; else printf 'notty\\n'; fi; " +
                                        "IFS= read line; printf 'pty:%s\\n' \"\$line\""
                                )
                        )
                        .put("processId", ptyProcessId)
                        .put("tty", true)
                        .put(
                            "size",
                            JSONObject()
                                .put("rows", 24)
                                .put("cols", 80)
                        )
                        .put("cwd", workspaceRoot)
                        .put("timeoutMs", 5000)
                        .put("outputBytesCap", 4096),
                )
            )
        }
        ptyCommandThread.start()
        val ptyResizeResponse = resizeCommandPty(
            handle = handle,
            processId = ptyProcessId,
            rows = 32,
            cols = 100,
        )
        record("commandExecPtyResize=$ptyResizeResponse")
        val ptyWriteResponse = writeCommandInput(
            handle = handle,
            processId = ptyProcessId,
            input = "pty-android\n",
        )
        record("commandExecPtyWrite=$ptyWriteResponse")
        ptyCommandThread.join(5000)
        record("commandExecPty=${ptyCommandResponse ?: "timeout"}")

        val terminateProcessId = "smoke-terminate"
        var terminatedCommandResponse: String? = null
        val commandThread = Thread {
            terminatedCommandResponse = sendRequest(
                handle,
                smokeRequestJson(
                    id = "smoke-command-exec-long",
                    method = "command/exec",
                    params = JSONObject()
                        .put(
                            "command",
                            JSONArray()
                                .put("/system/bin/sh")
                                .put("-c")
                                .put("python3 -c 'import time; time.sleep(30)'")
                        )
                        .put("processId", terminateProcessId)
                        .put("cwd", workspaceRoot)
                        .put("timeoutMs", 30000)
                        .put("outputBytesCap", 4096),
                )
            )
        }
        commandThread.start()
        Thread.sleep(250)
        val terminateResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-command-exec-terminate",
                method = "command/exec/terminate",
                params = JSONObject()
                    .put("processId", terminateProcessId),
            )
        )
        record("commandExecTerminate=$terminateResponse")
        commandThread.join(5000)
        record("commandExecTerminated=${terminatedCommandResponse ?: "timeout"}")
    }

    private fun writeCommandInput(
        handle: Long,
        processId: String,
        input: String,
    ): String {
        val deltaBase64 = Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
        var lastResponse = ""
        repeat(10) { attempt ->
            lastResponse = sendRequest(
                handle,
                smokeRequestJson(
                    id = "smoke-command-exec-stdin-write-$attempt",
                    method = "command/exec/write",
                    params = JSONObject()
                        .put("processId", processId)
                        .put("deltaBase64", deltaBase64)
                        .put("closeStdin", true),
                )
            )
            if (!lastResponse.contains("no active command/exec")) {
                return lastResponse
            }
            Thread.sleep(100)
        }
        return lastResponse
    }

    private fun resizeCommandPty(
        handle: Long,
        processId: String,
        rows: Int,
        cols: Int,
    ): String {
        var lastResponse = ""
        repeat(10) { attempt ->
            lastResponse = sendRequest(
                handle,
                smokeRequestJson(
                    id = "smoke-command-exec-pty-resize-$attempt",
                    method = "command/exec/resize",
                    params = JSONObject()
                        .put("processId", processId)
                        .put(
                            "size",
                            JSONObject()
                                .put("rows", rows)
                                .put("cols", cols)
                        ),
                )
            )
            if (!lastResponse.contains("no active command/exec")) {
                return lastResponse
            }
            Thread.sleep(100)
        }
        return lastResponse
    }

    private fun runTurnStartSmoke(
        handle: Long,
        threadId: String,
        workspaceRoot: String,
        record: (String) -> Unit,
    ) {
        val turnStartResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-turn-start",
                method = "turn/start",
                params = JSONObject()
                    .put("threadId", threadId)
                    .put("cwd", workspaceRoot)
                    .put(
                        "input",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", SMOKE_TURN_PROMPT)
                                .put("textElements", JSONArray())
                        )
                    ),
            )
        )
        record("turnStart=$turnStartResponse")

        val turnId = turnIdFromTurnResponse(turnStartResponse) ?: return
        val interruptResponse = sendRequest(
            handle,
            smokeRequestJson(
                id = "smoke-turn-interrupt",
                method = "turn/interrupt",
                params = JSONObject()
                    .put("threadId", threadId)
                    .put("turnId", turnId),
            )
        )
        record("turnInterrupt=$interruptResponse")
    }

    private fun accountCanStartTurn(accountResponse: String): Boolean =
        runCatching {
            val result = JSONObject(accountResponse).optJSONObject("result")
                ?: return@runCatching false
            val requiresOpenAiAuth = result.optBoolean("requiresOpenaiAuth", true)
            !requiresOpenAiAuth || !result.isNull("account")
        }.getOrDefault(false)

    private fun drainEvents(handle: Long, maxEvents: Int): List<String> = buildList {
        repeat(maxEvents) {
            nextEvent(handle)?.let(::add)
        }
    }

    private fun threadIdFromThreadResponse(responseJson: String): String? =
        runCatching {
            JSONObject(responseJson)
                .optJSONObject("result")
                ?.optJSONObject("thread")
                ?.optString("id")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()

    private fun threadPathFromThreadResponse(responseJson: String): String? =
        runCatching {
            JSONObject(responseJson)
                .optJSONObject("result")
                ?.optJSONObject("thread")
                ?.optString("path")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()

    private fun turnIdFromTurnResponse(responseJson: String): String? =
        runCatching {
            JSONObject(responseJson)
                .optJSONObject("result")
                ?.optJSONObject("turn")
                ?.optString("id")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()

    private fun bridgeUnavailablePayload(failure: Throwable): String = JSONObject()
        .put("ok", false)
        .put("error", "native bridge unavailable: ${failure.javaClass.simpleName}")
        .toString()

    private fun bridgeCallFailedPayload(failure: Throwable): String = JSONObject()
        .put("ok", false)
        .put("error", "native bridge call failed: ${failure.javaClass.simpleName}")
        .toString()

    private external fun nativeEngineStatus(): String
    private external fun nativeLastStartError(): String?
    private external fun nativeStartEngine(configJson: String): Long
    private external fun nativeSendRequest(handle: Long, requestJson: String): String
    private external fun nativeNextEvent(handle: Long): String?
    private external fun nativeRespondToServerRequest(
        handle: Long,
        requestId: String,
        resultJson: String
    ): String

    private external fun nativeFailServerRequest(
        handle: Long,
        requestId: String,
        errorJson: String
    ): String

    private external fun nativeShutdownEngine(handle: Long): String
}
