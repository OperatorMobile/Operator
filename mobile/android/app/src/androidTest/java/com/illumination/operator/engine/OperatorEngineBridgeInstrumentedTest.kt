package com.illumination.operator.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class OperatorEngineBridgeInstrumentedTest {
    @Test
    fun lifecycleSmokeUsesEmbeddedCodexAndAppPrivateWorkspace() {
        val root = freshSmokeRoot("lifecycle")
        val workspaceRoot = File(root, "workspaces/default").absolutePath

        val status = OperatorEngineBridge.lifecycleSmokeStatus(root.absolutePath)

        assertTrue(status, status.contains("initialize=startEngine(in-process)"))
        assertTrue(status, status.contains("config="))
        assertTrue(status, status.contains("threadStart="))
        assertTrue(status, status.contains("threadRead="))
        assertTrue(status, status.contains("threadList="))
        assertTrue(status, status.contains("account="))
        assertTrue(status, status.contains("\"cwd\":\"$workspaceRoot\""))
        assertFalse(status, status.contains("\"cwd\":\"/\","))
        assertFalse(status, status.contains("\"cwd\":\"/\"}"))
    }

    @Test
    fun turnSmokeDoesNotStartWithoutRealAccount() {
        val root = freshSmokeRoot("turn-gated")

        val status = OperatorEngineBridge.lifecycleSmokeStatus(
            appFilesDir = root.absolutePath,
            includeTurnSmoke = true,
        )

        assertTrue(status, status.contains("account="))
        assertTrue(status, status.contains("turnStart=not attempted: account unavailable"))
        assertFalse(status, status.contains("turnInterrupt="))
    }

    @Test
    fun commandExecSmokeRunsInAppPrivateWorkspace() {
        val root = freshSmokeRoot("command")
        val workspaceRoot = File(root, "workspaces/default").absolutePath
        val shellWorkspaceRoot = workspaceRoot.replace("/data/user/0/", "/data/data/")
        val rgBinary = File(root, "tools/bin/rg")
        val applyPatchBinary = File(root, "tools/bin/apply_patch")
        val gitBinary = File(root, "tools/bin/git")
        val gitRemoteHttpBinary = File(root, "tools/bin/git-remote-http")
        val gitRemoteHttpsBinary = File(root, "tools/bin/git-remote-https")
        val caBundle = File(root, "tools/cacert.pem")
        val gitTemplateDir = File(root, "tools/share/git-core/templates")

        val status = OperatorEngineBridge.lifecycleSmokeStatus(
            appFilesDir = root.absolutePath,
            includeCommandSmoke = true,
        )

        assertTrue("bundled rg was not installed at ${rgBinary.absolutePath}", rgBinary.isFile)
        assertTrue("bundled rg is not executable at ${rgBinary.absolutePath}", rgBinary.canExecute())
        assertTrue(
            "bundled apply_patch was not installed at ${applyPatchBinary.absolutePath}",
            applyPatchBinary.isFile
        )
        assertTrue(
            "bundled apply_patch is not executable at ${applyPatchBinary.absolutePath}",
            applyPatchBinary.canExecute()
        )
        assertTrue("bundled git was not installed at ${gitBinary.absolutePath}", gitBinary.isFile)
        assertTrue("bundled git is not executable at ${gitBinary.absolutePath}", gitBinary.canExecute())
        assertTrue(
            "bundled git-remote-http was not installed at ${gitRemoteHttpBinary.absolutePath}",
            gitRemoteHttpBinary.isFile
        )
        assertTrue(
            "bundled git-remote-http is not executable at ${gitRemoteHttpBinary.absolutePath}",
            gitRemoteHttpBinary.canExecute()
        )
        assertTrue(
            "bundled git-remote-https was not installed at ${gitRemoteHttpsBinary.absolutePath}",
            gitRemoteHttpsBinary.isFile
        )
        assertTrue(
            "bundled git-remote-https is not executable at ${gitRemoteHttpsBinary.absolutePath}",
            gitRemoteHttpsBinary.canExecute()
        )
        assertTrue("bundled CA bundle was not installed at ${caBundle.absolutePath}", caBundle.isFile)
        assertTrue("bundled CA bundle is empty at ${caBundle.absolutePath}", caBundle.length() > 0)
        assertTrue(
            "git template directory was not created at ${gitTemplateDir.absolutePath}",
            gitTemplateDir.isDirectory
        )
        assertTrue(status, status.contains("commandExec="))
        assertTrue(status, status.contains("commandExecPathShell="))
        assertTrue(status, status.contains("commandExecNonzero="))
        assertTrue(status, status.contains("commandExecOutputCap="))
        assertTrue(status, status.contains("commandExecTimeout="))
        assertTrue(status, status.contains("commandExecMissing="))
        assertTrue(status, status.contains("commandExecToolbox="))
        assertTrue(status, status.contains("commandExecToolInventory="))
        assertTrue(status, status.contains("commandExecRipgrep="))
        assertTrue(status, status.contains("commandExecApplyPatch="))
        assertTrue(status, status.contains("commandExecGit="))
        assertTrue(status, status.contains("commandExecStream="))
        assertTrue(status, status.contains("commandExecStdinWrite="))
        assertTrue(status, status.contains("commandExecStdin="))
        assertTrue(status, status.contains("commandExecPtyResize="))
        assertTrue(status, status.contains("commandExecPtyWrite="))
        assertTrue(status, status.contains("commandExecPty="))
        assertTrue(status, status.contains("commandExecTerminate="))
        assertTrue(status, status.contains("commandExecTerminated="))
        assertFalse(status, status.contains("commandExecStdin=timeout"))
        assertFalse(status, status.contains("commandExecPty=timeout"))
        assertFalse(status, status.contains("commandExecTerminated=timeout"))
        val ptyWrite = statusResponse(status, "commandExecPtyWrite=")
        val ptyResize = statusResponse(status, "commandExecPtyResize=")
        val pty = statusResponse(status, "commandExecPty=")
        val ripgrep = statusResponse(status, "commandExecRipgrep=")
        val applyPatch = statusResponse(status, "commandExecApplyPatch=")
        val git = statusResponse(status, "commandExecGit=")
        assertTrue(ptyResize.toString(), ptyResize.optBoolean("ok"))
        assertTrue(ptyWrite.toString(), ptyWrite.optBoolean("ok"))
        assertTrue(pty.toString(), pty.optBoolean("ok"))
        assertTrue(pty.toString(), pty.optJSONObject("result")?.optInt("exitCode") == 0)
        assertTrue(ripgrep.toString(), ripgrep.optBoolean("ok"))
        assertTrue(ripgrep.toString(), ripgrep.optJSONObject("result")?.optInt("exitCode") == 0)
        assertTrue(ripgrep.toString(), ripgrep.optJSONObject("result")?.optString("stderr") == "")
        assertTrue(
            ripgrep.toString(),
            ripgrep.optJSONObject("result")?.optString("stdout")?.contains("rg-smoke/note.txt:2:beta\n") == true
        )
        assertTrue(applyPatch.toString(), applyPatch.optBoolean("ok"))
        assertTrue(applyPatch.toString(), applyPatch.optJSONObject("result")?.optInt("exitCode") == 0)
        assertTrue(applyPatch.toString(), applyPatch.optJSONObject("result")?.optString("stderr") == "")
        assertTrue(
            applyPatch.toString(),
            applyPatch.optJSONObject("result")?.optString("stdout")?.contains("patched from android\n") == true
        )
        assertTrue(git.toString(), git.optBoolean("ok"))
        assertTrue(git.toString(), git.optJSONObject("result")?.optInt("exitCode") == 0)
        assertTrue(git.toString(), git.optJSONObject("result")?.optString("stderr") == "")
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains("/tools/bin\n") == true
        )
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains("git version 2.54.0\n") == true
        )
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains(" M note.txt\n") == true
        )
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains("+beta\n") == true
        )
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains("one\ntwo\n") == true
        )
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains("main\n") == true
        )
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains("apply-target.txt\n") == true
        )
        assertTrue(
            git.toString(),
            git.optJSONObject("result")?.optString("stdout")?.contains("note.txt\n") == true
        )
        assertTrue(status, status.contains("\"exitCode\":0"))
        assertTrue(
            status,
            status.contains("\"stdout\":\"cwd=$workspaceRoot\\n") ||
                status.contains("\"stdout\":\"cwd=$shellWorkspaceRoot\\n")
        )
        assertTrue(status, status.contains("PATH="))
        assertTrue(status, status.contains("/system/bin"))
        assertTrue(status, status.contains("\"stdout\":\"path-shell-ok\\nshell-path=/system/bin/sh\\n\""))
        assertTrue(status, status.contains("\"stdout\":\"stdin=hello-android\\n\""))
        assertTrue(status, status.contains("\"exitCode\":7"))
        assertTrue(status, status.contains("\"stderr\":\"stderr-smoke\\n\""))
        assertTrue(status, status.contains("\"stdout\":\"abcde\""))
        assertTrue(status, status.contains("\"stderr\":\"uvwxy\""))
        assertTrue(status, status.contains("\"exitCode\":124"))
        assertTrue(status, status.contains("failed to spawn command"))
        assertTrue(status, status.contains("BETA\\n"))
        assertTrue(status, status.contains("tool-smoke/moved.txt"))
        assertTrue(status, status.contains("missing=\\n"))
        assertTrue(status, status.contains("rg=present:"))
        assertTrue(status, status.contains("/tools/bin/rg"))
        assertTrue(status, status.contains("apply_patch=present:"))
        assertTrue(status, status.contains("/tools/bin/apply_patch"))
        assertTrue(status, status.contains("git=present:"))
        assertTrue(status, status.contains("/tools/bin/git"))
        assertTrue(status, status.contains("git-remote-http=present:"))
        assertTrue(status, status.contains("/tools/bin/git-remote-http"))
        assertTrue(status, status.contains("git-remote-https=present:"))
        assertTrue(status, status.contains("/tools/bin/git-remote-https"))
        assertTrue(status, status.contains("patched from android\\n"))
        assertTrue(status, status.contains("\"method\":\"command/exec/outputDelta\""))
    }

    @Test
    fun fileSystemApisRoundTripThroughEmbeddedCodexWorkspace() {
        val root = freshSmokeRoot("fs")
        val workspaceRoot = File(root, "workspaces/default").absolutePath
        val client = when (
            val start = OperatorAppServerClient.start(
                appFilesDir = root.absolutePath,
                workspaceRoot = workspaceRoot,
            )
        ) {
            is OperatorEngineStartResult.Started -> start.client
            is OperatorEngineStartResult.Failed -> error(start.message)
        }

        try {
            val smokeDir = File(workspaceRoot, "fs-smoke").absolutePath
            val sourceFile = File(smokeDir, "note.txt").absolutePath
            val copiedFile = File(smokeDir, "copy.txt").absolutePath
            val dataBase64 = Base64.getEncoder()
                .encodeToString("hello from fs\n".toByteArray(Charsets.UTF_8))

            val createResponse = client.request(
                method = "fs/createDirectory",
                paramsJson = JSONObject()
                    .put("path", smokeDir)
                    .put("recursive", true)
                    .toString(),
                id = "fs-create",
            )
            assertTrue(createResponse, createResponse.contains("\"result\":{}"))

            val writeResponse = client.request(
                method = "fs/writeFile",
                paramsJson = JSONObject()
                    .put("path", sourceFile)
                    .put("dataBase64", dataBase64)
                    .toString(),
                id = "fs-write",
            )
            assertTrue(writeResponse, writeResponse.contains("\"result\":{}"))

            val readResponse = client.request(
                method = "fs/readFile",
                paramsJson = JSONObject()
                    .put("path", sourceFile)
                    .toString(),
                id = "fs-read",
            )
            assertTrue(readResponse, readResponse.contains("\"dataBase64\":\"$dataBase64\""))

            val copyResponse = client.request(
                method = "fs/copy",
                paramsJson = JSONObject()
                    .put("sourcePath", sourceFile)
                    .put("destinationPath", copiedFile)
                    .toString(),
                id = "fs-copy",
            )
            assertTrue(copyResponse, copyResponse.contains("\"result\":{}"))

            val metadataResponse = client.request(
                method = "fs/getMetadata",
                paramsJson = JSONObject()
                    .put("path", copiedFile)
                    .toString(),
                id = "fs-metadata",
            )
            assertTrue(metadataResponse, metadataResponse.contains("\"isFile\":true"))
            assertTrue(metadataResponse, metadataResponse.contains("\"isDirectory\":false"))

            val readDirectoryResponse = client.request(
                method = "fs/readDirectory",
                paramsJson = JSONObject()
                    .put("path", smokeDir)
                    .toString(),
                id = "fs-read-dir",
            )
            assertTrue(readDirectoryResponse, readDirectoryResponse.contains("\"fileName\":\"note.txt\""))
            assertTrue(readDirectoryResponse, readDirectoryResponse.contains("\"fileName\":\"copy.txt\""))

            val removeResponse = client.request(
                method = "fs/remove",
                paramsJson = JSONObject()
                    .put("path", smokeDir)
                    .put("recursive", true)
                    .put("force", true)
                    .toString(),
                id = "fs-remove",
            )
            assertTrue(removeResponse, removeResponse.contains("\"result\":{}"))
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun shutdownReturnsWhileCommandExecIsRunning() {
        val root = freshSmokeRoot("shutdown-command")
        val workspaceRoot = File(root, "workspaces/default").absolutePath
        val client = when (
            val start = OperatorAppServerClient.start(
                appFilesDir = root.absolutePath,
                workspaceRoot = workspaceRoot,
            )
        ) {
            is OperatorEngineStartResult.Started -> start.client
            is OperatorEngineStartResult.Failed -> error(start.message)
        }

        var commandResponse: String? = null
        val commandThread = Thread {
            commandResponse = client.request(
                method = "command/exec",
                paramsJson = JSONObject()
                    .put(
                        "command",
                        org.json.JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("sleep 30")
                    )
                    .put("processId", "shutdown-command")
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 30000)
                    .put("outputBytesCap", 4096)
                    .toString(),
                id = "shutdown-command-exec",
            )
        }

        commandThread.start()
        Thread.sleep(250)
        val shutdownResponse = client.shutdown()
        commandThread.join(5000)

        assertTrue(shutdownResponse, shutdownResponse.contains("\"ok\":true"))
        assertTrue(
            commandResponse ?: "command response timed out",
            commandResponse != null
        )
    }

    @Test
    fun terminateReturnsWhilePtyCommandExecIsRunning() {
        val root = freshSmokeRoot("terminate-pty-command")
        val workspaceRoot = File(root, "workspaces/default").absolutePath
        val client = when (
            val start = OperatorAppServerClient.start(
                appFilesDir = root.absolutePath,
                workspaceRoot = workspaceRoot,
            )
        ) {
            is OperatorEngineStartResult.Started -> start.client
            is OperatorEngineStartResult.Failed -> error(start.message)
        }

        val processId = "terminate-pty-command"
        var commandResponse: String? = null
        val commandThread = Thread {
            commandResponse = client.request(
                method = "command/exec",
                paramsJson = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("stty -echo 2>/dev/null || true; printf 'pty-ready\\n'; sleep 30")
                    )
                    .put("processId", processId)
                    .put("tty", true)
                    .put(
                        "size",
                        JSONObject()
                            .put("rows", 24)
                            .put("cols", 80)
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 30000)
                    .put("outputBytesCap", 4096)
                    .toString(),
                id = "terminate-pty-command-exec",
            )
        }

        try {
            commandThread.start()
            val terminateResponse = terminateCommand(client, processId, "terminate-pty-command")
            commandThread.join(5000)

            assertTrue(terminateResponse, JSONObject(terminateResponse).optBoolean("ok"))
            assertTrue(
                commandResponse ?: "PTY command response timed out",
                commandResponse != null
            )
            assertFalse(commandResponse.orEmpty(), commandResponse.orEmpty().contains("timeout"))
        } finally {
            if (commandThread.isAlive) {
                terminateCommand(client, processId, "terminate-pty-command-cleanup")
                commandThread.join(5000)
            }
            client.shutdown()
        }
    }

    @Test
    fun shutdownReturnsWhilePtyCommandExecIsRunning() {
        val root = freshSmokeRoot("shutdown-pty-command")
        val workspaceRoot = File(root, "workspaces/default").absolutePath
        val client = when (
            val start = OperatorAppServerClient.start(
                appFilesDir = root.absolutePath,
                workspaceRoot = workspaceRoot,
            )
        ) {
            is OperatorEngineStartResult.Started -> start.client
            is OperatorEngineStartResult.Failed -> error(start.message)
        }

        var commandResponse: String? = null
        val commandThread = Thread {
            commandResponse = client.request(
                method = "command/exec",
                paramsJson = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put("stty -echo 2>/dev/null || true; printf 'pty-ready\\n'; sleep 30")
                    )
                    .put("processId", "shutdown-pty-command")
                    .put("tty", true)
                    .put(
                        "size",
                        JSONObject()
                            .put("rows", 24)
                            .put("cols", 80)
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 30000)
                    .put("outputBytesCap", 4096)
                    .toString(),
                id = "shutdown-pty-command-exec",
            )
        }

        commandThread.start()
        Thread.sleep(250)
        val shutdownResponse = client.shutdown()
        commandThread.join(5000)

        assertTrue(shutdownResponse, shutdownResponse.contains("\"ok\":true"))
        assertTrue(
            commandResponse ?: "PTY command response timed out",
            commandResponse != null
        )
    }

    @Test
    fun remoteGitHttpsSmokeOnDemand() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("operator.remoteGitSmoke") == "true")

        val root = freshSmokeRoot("remote-git")
        val workspaceRoot = File(root, "workspaces/default").absolutePath
        val client = when (
            val start = OperatorAppServerClient.start(
                appFilesDir = root.absolutePath,
                workspaceRoot = workspaceRoot,
            )
        ) {
            is OperatorEngineStartResult.Started -> start.client
            is OperatorEngineStartResult.Failed -> error(start.message)
        }

        try {
            val response = client.request(
                method = "command/exec",
                paramsJson = JSONObject()
                    .put(
                        "command",
                        JSONArray()
                            .put("/system/bin/sh")
                            .put("-c")
                            .put(
                                """
                                set -e
                                rm -rf remote-smoke
                                git --version
                                git --exec-path
                                git ls-remote https://github.com/openai/codex.git HEAD
                                git clone --depth 1 https://github.com/octocat/Hello-World.git remote-smoke
                                git -C remote-smoke rev-parse --is-inside-work-tree
                                git -C remote-smoke log -1 --format=%s
                                rm -rf remote-smoke
                                """.trimIndent()
                            )
                    )
                    .put("cwd", workspaceRoot)
                    .put("timeoutMs", 60000)
                    .put("outputBytesCap", 32768)
                    .toString(),
                id = "remote-git-https-smoke",
            )
            val json = JSONObject(response)
            val result = json.optJSONObject("result")
            val stdout = result?.optString("stdout").orEmpty()

            assertTrue(response, json.optBoolean("ok"))
            assertTrue(response, result?.optInt("exitCode") == 0)
            assertTrue(response, stdout.contains("git version 2.54.0\n"))
            assertTrue(response, stdout.contains("/tools/bin\n"))
            assertTrue(response, stdout.contains("9ddfda9db7b71153fdd4eed29d9503db5789f434\tHEAD\n"))
            assertTrue(response, stdout.contains("true\n"))
            assertTrue(response, stdout.contains("Merge pull request #6 from Spaceghost/patch-1\n"))
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun chatGptDeviceCodeLoginStartCancelOnDemand() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("operator.deviceCodeLoginSmoke") == "true")

        val root = freshSmokeRoot("device-code-login")
        val workspaceRoot = File(root, "workspaces/default").absolutePath
        val client = when (
            val start = OperatorAppServerClient.start(
                appFilesDir = root.absolutePath,
                workspaceRoot = workspaceRoot,
            )
        ) {
            is OperatorEngineStartResult.Started -> start.client
            is OperatorEngineStartResult.Failed -> error(start.message)
        }

        try {
            val accountBefore = JSONObject(client.readAccount(id = "device-code-account-before"))
            assertTrue("account/read failed before login", accountBefore.optBoolean("ok"))

            val loginResponse = JSONObject(client.startChatGptDeviceCodeLogin(id = "device-code-login-start"))
            assertTrue("device-code login start failed", loginResponse.optBoolean("ok"))
            val loginResult = loginResponse.optJSONObject("result")
            assertTrue("device-code login response missing result", loginResult != null)
            assertTrue(
                "device-code login response had unexpected type",
                loginResult?.optString("type") == "chatgptDeviceCode"
            )
            val loginId = loginResult?.optString("loginId").orEmpty()
            val verificationUrl = loginResult?.optString("verificationUrl").orEmpty()
            val userCode = loginResult?.optString("userCode").orEmpty()
            assertTrue("device-code login id was empty", loginId.isNotBlank())
            assertTrue("device-code verification URL was empty", verificationUrl.startsWith("https://"))
            assertTrue("device-code user code was empty", userCode.isNotBlank())

            val cancelResponse = JSONObject(client.cancelAccountLogin(loginId, id = "device-code-login-cancel"))
            assertTrue("device-code login cancel failed", cancelResponse.optBoolean("ok"))
            assertTrue(
                "device-code login cancel did not report canceled",
                cancelResponse.optJSONObject("result")?.optString("status") == "canceled"
            )

            val completion = waitForAccountLoginCompleted(client, loginId, attempts = 20)
            accountLoginCompletedPayload(completion)?.let { payload ->
                assertFalse(
                    "device-code login unexpectedly completed after cancel",
                    payload.optBoolean("success")
                )
            }
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun authenticatedTurnCompletesOnDemand() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("operator.authenticatedTurnSmoke") == "true")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appFilesDir = context.filesDir
        OperatorToolInstaller.installBundledTools(context, appFilesDir.absolutePath)
        val workspaceRoot = File(appFilesDir, "workspaces/default").absolutePath
        val client = when (
            val start = OperatorAppServerClient.start(
                appFilesDir = appFilesDir.absolutePath,
                workspaceRoot = workspaceRoot,
            )
        ) {
            is OperatorEngineStartResult.Started -> start.client
            is OperatorEngineStartResult.Failed -> error(start.message)
        }

        try {
            val startedEvent = waitForEvent(client, attempts = 10) { event ->
                event.optString("type") == "engine.started"
            }
            val network = startedEvent?.optJSONObject("network")
            assertTrue("engine.started event did not include Android CA bundle", network?.isNull("caBundle") == false)
            assertTrue(
                "engine.started event did not use Codex CA override for websocket TLS",
                network?.optString("websocketTls") == "codex_ca_certificate_override"
            )

            val account = JSONObject(client.readAccount(refreshToken = true, id = "authenticated-turn-account"))
            assertTrue("account/read failed before authenticated turn", account.optBoolean("ok"))
            assumeTrue(
                "authenticated turn smoke requires a persisted account",
                accountCanStartTurn(account)
            )

            val thread = JSONObject(client.startThread(cwd = workspaceRoot, id = "authenticated-turn-thread"))
            assertTrue("thread/start failed before authenticated turn", thread.optBoolean("ok"))
            val threadId = thread
                .optJSONObject("result")
                ?.optJSONObject("thread")
                ?.optString("id")
                .orEmpty()
            assertTrue("thread/start did not return a thread id", threadId.isNotBlank())

            val turn = JSONObject(
                client.startTextTurn(
                    threadId = threadId,
                    cwd = workspaceRoot,
                    text = "Operator Android authenticated smoke. Reply with exactly: operator-android-auth-ok",
                    id = "authenticated-turn-start",
                )
            )
            assertTrue("turn/start failed for authenticated smoke", turn.optBoolean("ok"))
            val turnId = turn
                .optJSONObject("result")
                ?.optJSONObject("turn")
                ?.optString("id")
                .orEmpty()
            assertTrue("turn/start did not return a turn id", turnId.isNotBlank())

            val observedEvents = mutableListOf<JSONObject>()
            val completion = waitForNotification(
                client = client,
                method = "turn/completed",
                attempts = 360,
                observedEvents = observedEvents,
            )
            assertTrue(
                "authenticated turn did not complete before timeout",
                completion != null
            )
            assertFalse(
                "authenticated turn fell back from WebSockets to HTTPS",
                observedEvents.any(::isWebsocketFallbackWarning)
            )
        } finally {
            client.shutdown()
        }
    }

    private fun freshSmokeRoot(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "operator-$name-${System.nanoTime()}")
        root.deleteRecursively()
        require(root.mkdirs()) { "failed to create ${root.absolutePath}" }
        OperatorToolInstaller.installBundledTools(context, root.absolutePath)
        return root
    }

    private fun statusResponse(status: String, prefix: String): JSONObject {
        val line = status.lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?: error("missing status line $prefix")
        return JSONObject(line.substringAfter(prefix))
    }

    private fun waitForAccountLoginCompleted(
        client: OperatorAppServerClient,
        loginId: String,
        attempts: Int = 50,
    ): JSONObject? {
        repeat(attempts) {
            val event = client.nextEvent()
            if (!event.isNullOrBlank()) {
                val json = JSONObject(event)
                if (
                    json.optString("method") == "account/login/completed" &&
                    accountLoginCompletedPayload(json)?.optString("loginId") == loginId
                ) {
                    return json
                }
            }
            Thread.sleep(100)
        }
        return null
    }

    private fun waitForNotification(
        client: OperatorAppServerClient,
        method: String,
        attempts: Int = 120,
        observedEvents: MutableList<JSONObject>? = null,
    ): JSONObject? {
        return waitForEvent(client, attempts, observedEvents) { event ->
            event.optString("method") == method
        }
    }

    private fun waitForEvent(
        client: OperatorAppServerClient,
        attempts: Int = 120,
        observedEvents: MutableList<JSONObject>? = null,
        predicate: (JSONObject) -> Boolean,
    ): JSONObject? {
        repeat(attempts) {
            val event = client.nextEvent()
            if (!event.isNullOrBlank()) {
                val json = JSONObject(event)
                observedEvents?.add(json)
                if (predicate(json)) {
                    return json
                }
            }
            Thread.sleep(250)
        }
        return null
    }

    private fun isWebsocketFallbackWarning(event: JSONObject): Boolean {
        if (event.optString("method") != "warning") {
            return false
        }

        val message = event
            .optJSONObject("payload")
            ?.optJSONObject("params")
            ?.optString("message")
            .orEmpty()
        return message.contains("Falling back from WebSockets to HTTPS transport")
    }

    private fun accountCanStartTurn(accountResponse: JSONObject): Boolean {
        val result = accountResponse.optJSONObject("result") ?: return false
        val requiresOpenAiAuth = result.optBoolean("requiresOpenaiAuth", true)
        return !requiresOpenAiAuth || !result.isNull("account")
    }

    private fun accountLoginCompletedPayload(event: JSONObject?): JSONObject? =
        event
            ?.optJSONObject("payload")
            ?.let { it.optJSONObject("account/login/completed") ?: it }

    private fun terminateCommand(
        client: OperatorAppServerClient,
        processId: String,
        requestPrefix: String,
    ): String {
        var lastResponse = ""
        repeat(20) { attempt ->
            lastResponse = client.request(
                method = "command/exec/terminate",
                paramsJson = JSONObject()
                    .put("processId", processId)
                    .toString(),
                id = "$requestPrefix-terminate-$attempt",
            )
            if (!lastResponse.contains("no active command/exec")) {
                return lastResponse
            }
            Thread.sleep(100)
        }
        return lastResponse
    }
}
