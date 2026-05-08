package com.illumination.operator

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.util.Log
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.LineBackgroundSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.illumination.operator.engine.OperatorAppServerClient
import com.illumination.operator.engine.OperatorEngineBridge
import com.illumination.operator.engine.OperatorEngineRuntime
import com.illumination.operator.engine.OperatorEngineStartResult
import com.illumination.operator.engine.OperatorToolInstaller
import com.illumination.operator.engine.RuntimeExtensionClient
import com.illumination.operator.engine.operatorPermissionProfileSelection
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tables.TableSpan
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageSizeResolverDef
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.node.Code
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        val appFilesDir = filesDir.absolutePath
        val includeTurnSmoke = intent.getBooleanExtra("operator.includeTurnSmoke", false)
        val includeCommandSmoke = intent.getBooleanExtra("operator.includeCommandSmoke", false)
        setContent {
            OperatorApp(appFilesDir, includeTurnSmoke, includeCommandSmoke)
        }
    }
}

@Composable
private fun OperatorApp(
    appFilesDir: String = "/tmp/operator-preview",
    includeTurnSmoke: Boolean = false,
    includeCommandSmoke: Boolean = false,
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OperatorColors.deck
        ) {
            OperatorHome(appFilesDir, includeTurnSmoke, includeCommandSmoke)
        }
    }
}

@Composable
private fun OperatorHome(
    appFilesDir: String = "/tmp/operator-preview",
    includeTurnSmoke: Boolean = false,
    includeCommandSmoke: Boolean = false,
) {
    val context = LocalContext.current
    val engineStatus = remember { OperatorEngineBridge.engineStatus() }
    var toolInstallState by remember(appFilesDir) {
        mutableStateOf<ToolInstallUiState>(
            ToolInstallUiState.Installing(
                label = "tools",
                detail = "preparing bundled runtime",
            )
        )
    }
    var lifecycleStatus by remember(appFilesDir, includeTurnSmoke, includeCommandSmoke) {
        mutableStateOf("starting embedded runtime")
    }
    LaunchedEffect(context, appFilesDir) {
        val mainHandler = Handler(Looper.getMainLooper())
        toolInstallState = ToolInstallUiState.Installing(
            label = "tools",
            detail = "checking bundled command set",
        )
        fun postInstallProgress(label: String, detail: String) {
            mainHandler.post {
                if (toolInstallState is ToolInstallUiState.Installing) {
                    toolInstallState = ToolInstallUiState.Installing(
                        label = label,
                        detail = detail,
                    )
                }
            }
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                OperatorToolInstaller.installBundledTools(context, appFilesDir) { progress ->
                    postInstallProgress(progress.label, progress.detail)
                }
                postInstallProgress(
                    label = "environment",
                    detail = "applying shell runtime variables",
                )
                OperatorToolInstaller.applyProcessEnvironment(context, appFilesDir)
                postInstallProgress(
                    label = "background",
                    detail = "starting background runtime service",
                )
                OperatorBackgroundService.start(context, appFilesDir)
            }
        }
        toolInstallState = result.fold(
            onSuccess = { ToolInstallUiState.Ready },
            onFailure = { error ->
                Log.w("OperatorTools", "failed to install bundled tools", error)
                ToolInstallUiState.Failed(error.readableMessage())
            },
        )
    }
    LaunchedEffect(appFilesDir, includeTurnSmoke, includeCommandSmoke, toolInstallState) {
        lifecycleStatus = when (val install = toolInstallState) {
            is ToolInstallUiState.Installing -> "preparing Android runtime: ${install.detail}"
            is ToolInstallUiState.Failed -> "runtime installer failed: ${install.message}"
            ToolInstallUiState.Ready -> withContext(Dispatchers.IO) {
                OperatorEngineBridge.lifecycleSmokeStatus(
                    appFilesDir = appFilesDir,
                    includeTurnSmoke = includeTurnSmoke,
                    includeCommandSmoke = includeCommandSmoke,
                )
            }
        }
    }
    val threadId = remember(lifecycleStatus) { threadIdFromSmokeStatus(lifecycleStatus) }
    val serverRequestCount = remember(lifecycleStatus) { serverRequestCountFromSmokeStatus(lifecycleStatus) }

    CodexWorkspaceDock(
        appFilesDir = appFilesDir,
        toolInstallState = toolInstallState,
        lifecycleStatus = lifecycleStatus,
        engineStatus = engineStatus,
        startupThreadId = threadId,
        startupServerRequestCount = serverRequestCount,
    )
}

private sealed class ToolInstallUiState {
    data class Installing(
        val label: String,
        val detail: String,
    ) : ToolInstallUiState()

    data object Ready : ToolInstallUiState()

    data class Failed(
        val message: String,
    ) : ToolInstallUiState()
}

@Composable
private fun HeaderStrip() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Operator",
                color = OperatorColors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Codex engine: embedded runtime",
                color = OperatorColors.textSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        StatusPill(label = "LOCAL", color = OperatorColors.ok)
    }
}

@Composable
private fun RuntimeLane(lifecycleStatus: String, threadId: String?) {
    val summary = remember(lifecycleStatus, threadId) {
        runtimeSummaryFromSmokeStatus(lifecycleStatus, threadId)
    }
    Panel(
        title = "Runtime",
        eyebrow = summary.eyebrow
    ) {
        LedgerLine(
            label = "engine",
            value = summary.engine
        )
        LedgerLine(
            label = "thread",
            value = summary.thread
        )
        LedgerLine(
            label = "account",
            value = summary.account
        )
        LedgerLine(
            label = "turn smoke",
            value = summary.turn
        )
    }
}

@Composable
private fun ToolStatusGrid(engineStatus: String, threadId: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactStatus(
            label = "Engine",
            value = if (engineStatus.startsWith("{")) "native ready" else "bridge unavailable",
            modifier = Modifier.weight(1f)
        )
        CompactStatus(
            label = "Thread",
            value = if (threadId == null) "not verified" else "verified",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StartupRequestDock(serverRequestCount: Int) {
    Panel(
        title = "Startup Requests",
        eyebrow = "smoke"
    ) {
        Text(
            text = if (serverRequestCount == 0) "none observed" else "$serverRequestCount observed",
            color = OperatorColors.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CodexWorkspaceDock(
    appFilesDir: String,
    toolInstallState: ToolInstallUiState,
    lifecycleStatus: String,
    engineStatus: String,
    startupThreadId: String?,
    startupServerRequestCount: Int,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val inputMethodManager = remember(context) {
        context.getSystemService(InputMethodManager::class.java)
    }
    val promptFocusRequester = remember { FocusRequester() }
    var promptFocusRequestNonce by remember { mutableIntStateOf(0) }
    val timelineListState = rememberLazyListState()
    val uiPreferences = remember(context) {
        context.getSharedPreferences(OPERATOR_UI_PREFS, Context.MODE_PRIVATE)
    }
    val markdownPreferences = remember(context) {
        context.getSharedPreferences("operator-markdown", Context.MODE_PRIVATE)
    }
    val workspacesRoot = "$appFilesDir/workspaces"
    val workspaceRoot = "$appFilesDir/workspaces/default"
    var runtimeExtensionStatus by remember { mutableStateOf("checking") }
    var settingsOpen by remember { mutableStateOf(false) }
    var archivedChatsOpen by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf<OperatorAppServerClient?>(null) }
    val clientForDispose by rememberUpdatedState(client)
    var loginPollJob by remember { mutableStateOf<Job?>(null) }
    val loginPollJobForDispose by rememberUpdatedState(loginPollJob)
    var turnPollJob by remember { mutableStateOf<Job?>(null) }
    val turnPollJobForDispose by rememberUpdatedState(turnPollJob)
    var state by remember { mutableStateOf<AccountPanelState>(AccountPanelState.Loading) }
    var prompt by remember { mutableStateOf("") }
    var imageAttachments by remember { mutableStateOf<List<PendingImageAttachment>>(emptyList()) }
    var imageAttachmentNotice by remember { mutableStateOf<String?>(null) }
    var imageAttachmentBusy by remember { mutableStateOf(false) }
    var inspectorContent by remember { mutableStateOf<InspectorContent?>(null) }
    var reviewInspectorContent by remember { mutableStateOf<ReviewInspectorContent?>(null) }
    var reviewActionBusy by remember { mutableStateOf(false) }
    var pendingReviewAction by remember { mutableStateOf<ReviewGitAction?>(null) }
    var threadId by remember(uiPreferences) {
        mutableStateOf(uiPreferences.nonBlankString(OPERATOR_UI_ACTIVE_THREAD_ID))
    }
    var autoResumeAttemptedThreadId by remember { mutableStateOf<String?>(null) }
    var activeTurnId by remember { mutableStateOf<String?>(null) }
    var turnState by remember { mutableStateOf<TurnPanelState>(TurnPanelState.Idle) }
    var assistantText by remember { mutableStateOf("") }
    var reasoningText by remember { mutableStateOf("") }
    var transcriptRows by remember { mutableStateOf<List<TranscriptRow>>(emptyList()) }
    var projectFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentThreads by remember { mutableStateOf<List<ThreadSummary>>(emptyList()) }
    var threadListState by remember { mutableStateOf<ThreadListState>(ThreadListState.Loading) }
    var sessionDrawerNotice by remember { mutableStateOf<String?>(null) }
    var archivedThreads by remember { mutableStateOf<List<ThreadSummary>>(emptyList()) }
    var archivedThreadListState by remember { mutableStateOf<ThreadListState>(ThreadListState.Loading) }
    var selectedArchivedProjectCwd by remember { mutableStateOf<String?>(null) }
    var archivedChatsNotice by remember { mutableStateOf<String?>(null) }
    var pendingArchivedDeleteThreadId by remember { mutableStateOf<String?>(null) }
    var terminalOpen by remember { mutableStateOf(false) }
    var terminalInitialCommand by remember { mutableStateOf<String?>(null) }
    var sessionSearchQuery by remember { mutableStateOf("") }
    var projectCreatorOpen by remember { mutableStateOf(false) }
    var projectNameDraft by remember { mutableStateOf("") }
    var projectNameError by remember { mutableStateOf<String?>(null) }
    var projectCreationBusy by remember { mutableStateOf(false) }
    var activeProjectCwd by remember(workspacesRoot, workspaceRoot, uiPreferences) {
        mutableStateOf(
            restoredWorkspaceCwd(
                workspacesRoot = workspacesRoot,
                defaultProjectCwd = workspaceRoot,
                storedCwd = uiPreferences.nonBlankString(OPERATOR_UI_ACTIVE_PROJECT_CWD),
            )
        )
    }

    fun hideKeyboardAndClearFocus() {
        focusManager.clearFocus(force = true)
        inputMethodManager?.hideSoftInputFromWindow(rootView.windowToken, 0)
    }

    fun requestComposerFocus() {
        promptFocusRequestNonce += 1
    }

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue to drawerState.targetValue }
            .collect { (current, target) ->
                if (current == DrawerValue.Open || target == DrawerValue.Open) {
                    hideKeyboardAndClearFocus()
                }
            }
    }

    LaunchedEffect(promptFocusRequestNonce) {
        if (
            promptFocusRequestNonce > 0 &&
            !terminalOpen &&
            !settingsOpen &&
            !archivedChatsOpen &&
            !projectCreatorOpen &&
            inspectorContent == null &&
            reviewInspectorContent == null &&
            drawerState.currentValue == DrawerValue.Closed &&
            drawerState.targetValue == DrawerValue.Closed
        ) {
            delay(80)
            promptFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(context) {
        runtimeExtensionStatus = withContext(Dispatchers.IO) {
            RuntimeExtensionClient.probe(context.applicationContext).summary
        }
    }
    var pendingRequests by remember { mutableStateOf<List<PendingServerRequest>>(emptyList()) }
    var modelOverride by remember(uiPreferences) {
        mutableStateOf(uiPreferences.nonBlankString(OPERATOR_UI_MODEL_OVERRIDE))
    }
    var reasoningEffortOverride by remember(uiPreferences) {
        mutableStateOf(uiPreferences.nonBlankString(OPERATOR_UI_REASONING_EFFORT))
    }
    var fastServiceTier by remember(uiPreferences) {
        mutableStateOf(uiPreferences.getBoolean(OPERATOR_UI_FAST_SERVICE_TIER, false))
    }
    var approvalPolicyOverride by remember(uiPreferences) {
        mutableStateOf(uiPreferences.nonBlankString(OPERATOR_UI_APPROVAL_POLICY))
    }
    var approvalsReviewerOverride by remember(uiPreferences) {
        mutableStateOf(uiPreferences.nonBlankString(OPERATOR_UI_APPROVALS_REVIEWER))
    }
    var permissionSelectionOverrideJson by remember(uiPreferences) {
        mutableStateOf(
            uiPreferences.nonBlankString(OPERATOR_UI_PERMISSION_SELECTION)
                ?: uiPreferences.nonBlankString(OPERATOR_UI_PERMISSION_PROFILE)
                    ?.let { operatorPermissionProfileSelection(it).toString() }
        )
    }
    var personalityOverride by remember(uiPreferences) {
        mutableStateOf(uiPreferences.nonBlankString(OPERATOR_UI_PERSONALITY_OVERRIDE))
    }
    var planModeEnabled by remember(uiPreferences) {
        mutableStateOf(uiPreferences.getBoolean(OPERATOR_UI_PLAN_MODE, false))
    }
    var oledThemeEnabled by remember(uiPreferences) {
        mutableStateOf(uiPreferences.getBoolean(OPERATOR_UI_OLED_THEME, false))
    }
    OperatorColors.oledEnabled = oledThemeEnabled
    var statusBarItems by remember(uiPreferences) {
        mutableStateOf(
            statusBarItemsFromPreference(
                uiPreferences.getString(OPERATOR_UI_STATUS_BAR_ITEMS, null),
            ),
        )
    }
    var configSnapshot by remember { mutableStateOf<JSONObject?>(null) }
    var tokenUsageSnapshot by remember { mutableStateOf<StatusTokenUsage?>(null) }
    var rateLimitSnapshot by remember { mutableStateOf(StatusRateLimitState()) }
    var gitBranchSnapshot by remember { mutableStateOf<String?>(null) }
    var slashInteraction by remember { mutableStateOf<SlashInteraction?>(null) }
    var markdownCodeTheme by remember(markdownPreferences) {
        mutableStateOf(
            MarkdownCodeTheme.fromKey(
                markdownPreferences.getString(MARKDOWN_CODE_THEME_KEY, null)
            )
        )
    }
    val markdownRenderSettings = remember(markdownCodeTheme) {
        MarkdownRenderSettings(codeTheme = markdownCodeTheme)
    }

    fun setMarkdownCodeTheme(theme: MarkdownCodeTheme) {
        markdownCodeTheme = theme
        markdownPreferences.edit().putString(MARKDOWN_CODE_THEME_KEY, theme.key).apply()
    }

    fun setOledThemeEnabled(enabled: Boolean) {
        oledThemeEnabled = enabled
        OperatorColors.oledEnabled = enabled
    }

    suspend fun startAccountClient(): OperatorAppServerClient {
        when (val install = toolInstallState) {
            is ToolInstallUiState.Installing -> throw IllegalStateException(
                "Operator runtime is still preparing: ${install.detail}"
            )
            is ToolInstallUiState.Failed -> throw IllegalStateException(
                "Operator runtime failed to prepare: ${install.message}"
            )
            ToolInstallUiState.Ready -> Unit
        }
        client?.let { return it }
        val start = withContext(Dispatchers.IO) {
            ensureProjectDirectories(workspacesRoot, workspaceRoot)
            OperatorEngineRuntime.start(
                appFilesDir = appFilesDir,
                workspaceRoot = workspaceRoot,
            )
        }
        return when (start) {
            is OperatorEngineStartResult.Started -> start.client.also { client = it }
            is OperatorEngineStartResult.Failed -> throw IllegalStateException(start.message)
        }
    }

    fun appendTranscript(row: TranscriptRow) {
        transcriptRows = (transcriptRows + row).takeLast(120)
    }

    fun appendOrReplaceStatus(row: TranscriptRow) {
        transcriptRows = if (
            row.kind == TimelineKind.Status &&
            transcriptRows.lastOrNull()?.kind == TimelineKind.Status &&
            transcriptRows.lastOrNull()?.label == row.label
        ) {
            transcriptRows.dropLast(1) + row
        } else {
            (transcriptRows + row).takeLast(120)
        }
    }

    suspend fun runGitCommand(
        activeClient: OperatorAppServerClient,
        command: List<String>,
        cwd: String = activeProjectCwd,
    ): CommandExecResult {
        val response = withContext(Dispatchers.IO) {
            activeClient.execCommand(
                command = command,
                cwd = cwd,
                timeoutMs = 30_000L,
                outputBytesCap = 400_000,
            )
        }
        return commandExecResult(response, command.joinToString(" "))
    }

    fun refreshStatusGitBranch() {
        if (MobileStatusBarItem.GitBranch !in statusBarItems) {
            gitBranchSnapshot = null
            return
        }
        scope.launch {
            gitBranchSnapshot = runCatching {
                val activeClient = startAccountClient()
                runGitCommand(
                    activeClient = activeClient,
                    command = listOf("git", "branch", "--show-current"),
                    cwd = activeProjectCwd,
                ).stdout.trim().takeIf(String::isNotBlank)
            }.getOrNull()
        }
    }

    fun runDoctorSlashCommand(commandName: String, includeNetwork: Boolean) {
        appendTranscript(
            TranscriptRow(
                label = commandName,
                value = "running Android development runtime checks",
                kind = TimelineKind.Tool,
            )
        )
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val command = if (includeNetwork) {
                    listOf("operator-doctor", "--network")
                } else {
                    listOf("operator-doctor")
                }
                val response = withContext(Dispatchers.IO) {
                    activeClient.execCommand(
                        command = command,
                        cwd = activeProjectCwd,
                        timeoutMs = if (includeNetwork) 60_000L else 20_000L,
                        outputBytesCap = 200_000,
                    )
                }
                commandExecResult(response, command.joinToString(" "))
            }.onSuccess { result ->
                val output = listOf(result.stdout.trim(), result.stderr.trim())
                    .filter(String::isNotBlank)
                    .joinToString("\n\n")
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = doctorSummary(result),
                        detail = output.ifBlank { "doctor returned no output" },
                        kind = if (result.exitCode == 0 && "FAIL " !in output) TimelineKind.Tool else TimelineKind.Error,
                    )
                )
                inspectorContent = InspectorContent(
                    title = "Android Doctor",
                    eyebrow = "cwd ${projectNameFromCwd(activeProjectCwd)}",
                    summary = doctorSummary(result),
                    body = output.ifBlank { "doctor returned no output" },
                )
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun loadReviewInspector(diffScope: ReviewDiffScope, notice: String? = null) {
        val cwd = activeProjectCwd
        reviewInspectorContent = ReviewInspectorContent.loading(
            scope = diffScope,
            cwd = cwd,
            notice = notice,
        )
        pendingReviewAction = null
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val repoRoot = runGitCommand(
                    activeClient = activeClient,
                    command = listOf("git", "rev-parse", "--show-toplevel"),
                    cwd = cwd,
                )
                if (repoRoot.exitCode != 0) {
                    return@runCatching ReviewInspectorContent.empty(
                        scope = diffScope,
                        cwd = cwd,
                        statusSummary = "No Git repository detected for ${projectNameFromCwd(cwd)}.",
                        notice = "Git filters and file actions appear when the active project is inside a Git repository.",
                    )
                }
                val branch = runGitCommand(
                    activeClient = activeClient,
                    command = listOf("git", "branch", "--show-current"),
                    cwd = cwd,
                )
                val status = runGitCommand(
                    activeClient = activeClient,
                    command = listOf("git", "status", "--short", "--branch"),
                    cwd = cwd,
                )
                val diff = runGitCommand(
                    activeClient = activeClient,
                    command = diffScope.gitCommand,
                    cwd = cwd,
                )
                reviewContentFromGit(
                    scope = diffScope,
                    cwd = cwd,
                    status = status,
                    diff = diff,
                    repository = GitRepositoryContext(
                        root = repoRoot.stdout.trim(),
                        branch = branch.stdout.trim().takeIf(String::isNotBlank),
                    ),
                    notice = notice,
                )
            }.onSuccess { content ->
                reviewInspectorContent = content
            }.onFailure { error ->
                reviewInspectorContent = ReviewInspectorContent.empty(
                    scope = diffScope,
                    cwd = cwd,
                    statusSummary = "git review unavailable",
                    notice = error.readableMessage(),
                )
            }
        }
    }

    fun executeReviewAction(action: ReviewGitAction) {
        if (reviewActionBusy) {
            return
        }
        val currentContent = reviewInspectorContent ?: return
        if (action.kind.requiresConfirmation && pendingReviewAction != action) {
            pendingReviewAction = action
            reviewInspectorContent = currentContent.copy(
                notice = "Confirm ${action.kind.label.lowercase()} for ${action.path}.",
            )
            return
        }
        pendingReviewAction = null
        reviewActionBusy = true
        reviewInspectorContent = currentContent.copy(
            notice = "${action.kind.label} running for ${action.path}",
        )
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                withContext(Dispatchers.IO) {
                    executeReviewGitAction(
                        activeClient = activeClient,
                        cwd = currentContent.cwd,
                        action = action,
                    )
                }
            }.onSuccess { message ->
                reviewActionBusy = false
                loadReviewInspector(currentContent.scope.refreshScope, message)
            }.onFailure { error ->
                reviewActionBusy = false
                reviewInspectorContent = currentContent.copy(
                    notice = error.readableMessage(),
                )
            }
        }
    }

    fun configEdit(keyPath: String, value: Any): JSONObject =
        JSONObject()
            .put("keyPath", keyPath)
            .put("value", value)
            .put("mergeStrategy", "replace")

    fun refreshConfigSnapshot(activeClient: OperatorAppServerClient? = null) {
        scope.launch {
            runCatching {
                val codexClient = activeClient ?: startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    codexClient.request(
                        method = "config/read",
                        paramsJson = JSONObject()
                            .put("includeLayers", false)
                            .put("cwd", activeProjectCwd)
                            .toString(),
                        id = "ui-status-config-read",
                    )
                }
                JSONObject(response)
                    .optJSONObject("result")
                    ?.optJSONObject("config")
                    ?.deepCopy()
            }.onSuccess { config ->
                configSnapshot = config
            }
        }
    }

    fun refreshRateLimits(activeClient: OperatorAppServerClient? = null) {
        scope.launch {
            runCatching {
                val codexClient = activeClient ?: startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    codexClient.request(
                        method = "account/rateLimits/read",
                        id = "ui-rate-limits-read",
                    )
                }
                statusRateLimitStateFromReadResponse(JSONObject(response))
            }.onSuccess { state ->
                state?.let { rateLimitSnapshot = it }
            }
        }
    }

    fun persistConfigEdits(summary: String, edits: JSONArray) {
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.request(
                        method = "config/batchWrite",
                        paramsJson = JSONObject()
                            .put("edits", edits)
                            .put("reloadUserConfig", true)
                            .toString(),
                        id = "ui-config-write",
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("config/batchWrite failed"))
                }
                refreshConfigSnapshot(activeClient)
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        label = "config",
                        value = "failed to save $summary: ${error.readableMessage()}",
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun persistModelSelection(modelId: String?, effort: String?) {
        persistConfigEdits(
            summary = "model selection",
            edits = JSONArray()
                .put(configEdit("model", modelId ?: JSONObject.NULL))
                .put(configEdit("model_reasoning_effort", effort ?: JSONObject.NULL)),
        )
    }

    fun persistReasoningEffort(effort: String?) {
        persistConfigEdits(
            summary = "thinking mode",
            edits = JSONArray()
                .put(configEdit("model_reasoning_effort", effort ?: JSONObject.NULL)),
        )
    }

    fun refreshAccount(refreshToken: Boolean = false) {
        state = AccountPanelState.Loading
        scope.launch {
            state = runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.readAccount(refreshToken = refreshToken)
                }
                val nextState = accountPanelStateFromReadResponse(response)
                if (nextState is AccountPanelState.SignedIn) {
                    refreshRateLimits(activeClient)
                }
                nextState
            }.getOrElse { error ->
                AccountPanelState.Error(error.readableMessage())
            }
        }
    }

    fun refreshThreads(showLoading: Boolean = true) {
        val hasCachedThreads = recentThreads.isNotEmpty()
        if (showLoading && !hasCachedThreads) {
            threadListState = ThreadListState.Loading
        } else if (hasCachedThreads && threadListState is ThreadListState.Error) {
            threadListState = ThreadListState.Ready
        }
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.listThreads(limit = 50)
                }
                recentThreads = threadSummariesFromListResponse(response)
                threadListState = ThreadListState.Ready
            }.onFailure { error ->
                if (recentThreads.isEmpty()) {
                    recentThreads = emptyList()
                    threadListState = ThreadListState.Error(error.readableMessage())
                } else {
                    threadListState = ThreadListState.Ready
                }
            }
        }
    }

    fun refreshProjectFolders() {
        scope.launch {
            projectFolders = withContext(Dispatchers.IO) {
                ensureProjectDirectories(workspacesRoot, workspaceRoot)
            }
        }
    }

    fun toggleStatusBarItem(item: MobileStatusBarItem) {
        statusBarItems = toggledStatusBarItems(statusBarItems, item)
    }

    fun refreshArchivedThreads() {
        archivedThreadListState = ThreadListState.Loading
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.listThreads(limit = 200, archived = true)
                }
                archivedThreads = threadSummariesFromListResponse(response)
                archivedThreadListState = ThreadListState.Ready
            }.onFailure { error ->
                archivedThreads = emptyList()
                archivedThreadListState = ThreadListState.Error(error.readableMessage())
            }
        }
    }

    fun runCapabilitySlashCommand(
        commandName: String,
        method: String,
        params: JSONObject? = null,
        summarize: (JSONObject) -> String,
        afterSuccess: (JSONObject) -> Unit = {},
    ) {
        appendTranscript(
            TranscriptRow(
                label = "slash",
                value = "$commandName -> $method",
                kind = TimelineKind.System,
            )
        )
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.request(
                        method = method,
                        paramsJson = params?.toString(),
                        id = "ui-slash-${commandName.removePrefix("/")}",
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("$method failed"))
                }
                afterSuccess(json)
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = summarize(json),
                        detail = response,
                        kind = TimelineKind.Tool,
                    )
                )
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun selectModelOverride(modelId: String?, closeInteraction: Boolean = true) {
        val picker = slashInteraction as? SlashInteraction.ModelPicker
        val nextReasoningEffort = picker
            ?.modelFor(modelId)
            ?.let { model ->
                reasoningEffortOverride?.takeIf { effort ->
                    model.reasoningEfforts.any { it.value == effort }
                }
            }
            ?: reasoningEffortOverride.takeIf { picker == null }
        modelOverride = modelId
        reasoningEffortOverride = nextReasoningEffort
        persistModelSelection(modelId, nextReasoningEffort)
        slashInteraction = if (closeInteraction) {
            null
        } else {
            picker?.copy(
                selectedModelId = modelId,
                selectedReasoningEffort = nextReasoningEffort,
            )
        }
        appendTranscript(
            TranscriptRow(
                label = "/model",
                value = modelId?.let { "next turns will use model $it" }
                    ?: "model override cleared; next turns use config default",
                kind = TimelineKind.System,
            )
        )
    }

    fun selectReasoningEffortOverride(effort: String?) {
        reasoningEffortOverride = effort
        persistReasoningEffort(effort)
        slashInteraction = (slashInteraction as? SlashInteraction.ModelPicker)
            ?.copy(selectedReasoningEffort = effort)
        appendTranscript(
            TranscriptRow(
                label = "/model",
                value = effort?.let { "next turns will use thinking mode ${reasoningEffortLabel(it)}" }
                    ?: "thinking mode override cleared; next turns use model default",
                kind = TimelineKind.System,
            )
        )
    }

    fun setExperimentalFeature(name: String, enabled: Boolean) {
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val params = JSONObject()
                    .put("enablement", JSONObject().put(name, enabled))
                val response = withContext(Dispatchers.IO) {
                    activeClient.request(
                        method = "experimentalFeature/enablement/set",
                        paramsJson = params.toString(),
                        id = "ui-slash-experimental-set",
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("experimentalFeature/enablement/set failed"))
                }
                slashInteraction = (slashInteraction as? SlashInteraction.ExperimentalPicker)
                    ?.let { picker ->
                        picker.copy(
                            features = picker.features.map { feature ->
                                if (feature.name == name) {
                                    feature.copy(enabled = enabled)
                                } else {
                                    feature
                                }
                            }
                        )
                    }
                appendTranscript(
                    TranscriptRow(
                        label = "/experimental",
                        value = "$name ${if (enabled) "enabled" else "disabled"}",
                        detail = response,
                        kind = TimelineKind.Tool,
                    )
                )
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        label = "/experimental",
                        value = error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    suspend fun defaultTurnModel(activeClient: OperatorAppServerClient): String? {
        modelOverride?.takeIf(String::isNotBlank)?.let { return it }
        val configResponse = withContext(Dispatchers.IO) {
            activeClient.request(
                method = "config/read",
                paramsJson = JSONObject()
                    .put("includeLayers", false)
                    .put("cwd", activeProjectCwd)
                    .toString(),
                id = "ui-plan-config-read",
            )
        }
        val configModel = runCatching { JSONObject(configResponse) }
            .getOrNull()
            ?.optJSONObject("result")
            ?.optJSONObject("config")
            ?.optNonBlankString("model")
        if (!configModel.isNullOrBlank()) {
            return configModel
        }
        val modelResponse = withContext(Dispatchers.IO) {
            activeClient.request(
                method = "model/list",
                paramsJson = JSONObject()
                    .put("limit", 50)
                    .put("includeHidden", false)
                    .toString(),
                id = "ui-plan-model-list",
            )
        }
        val models = runCatching { JSONObject(modelResponse) }
            .getOrNull()
            ?.optJSONObject("result")
            ?.optJSONArray("data")
            ?: return null
        for (index in 0 until models.length()) {
            val model = models.optJSONObject(index) ?: continue
            if (model.optBoolean("isDefault")) {
                return model.optNonBlankString("id") ?: model.optNonBlankString("model")
            }
        }
        return models.optJSONObject(0)?.optNonBlankString("id")
            ?: models.optJSONObject(0)?.optNonBlankString("model")
    }

    suspend fun activeCollaborationMode(activeClient: OperatorAppServerClient): JSONObject? {
        val model = defaultTurnModel(activeClient)
            ?: throw IllegalStateException("collaboration mode could not resolve a model")
        val mode = if (planModeEnabled) "plan" else "default"
        return JSONObject()
            .put("mode", mode)
            .put(
                "settings",
                JSONObject()
                    .put("model", model)
                    .put(
                        "reasoning_effort",
                        if (planModeEnabled) {
                            reasoningEffortOverride ?: "medium"
                        } else {
                            reasoningEffortOverride ?: JSONObject.NULL
                        }
                    )
                    .put("developer_instructions", JSONObject.NULL)
            )
    }

    fun setPlanMode(enabled: Boolean) {
        planModeEnabled = enabled
        val modeLabel = if (enabled) "plan" else "code"
        appendTranscript(
            TranscriptRow(
                label = modeLabel,
                value = "$modeLabel mode enabled for next turns",
                kind = TimelineKind.Mode,
            )
        )
    }

    fun startNewThread() {
        threadId = null
        activeTurnId = null
        turnPollJob?.cancel()
        turnPollJob = null
        turnState = TurnPanelState.Idle
        assistantText = ""
        reasoningText = ""
        transcriptRows = emptyList()
        prompt = ""
        imageAttachments = emptyList()
        imageAttachmentNotice = null
    }

    fun openProjectCreator() {
        if (turnState.isActiveTurn) {
            sessionDrawerNotice = "Stop the active turn before creating a new project."
            return
        }
        projectNameDraft = ""
        projectNameError = null
        projectCreatorOpen = true
    }

    fun closeProjectCreator() {
        if (projectCreationBusy) {
            return
        }
        projectCreatorOpen = false
        projectNameDraft = ""
        projectNameError = null
    }

    fun attachImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }
        imageAttachmentBusy = true
        imageAttachmentNotice = "Adding images..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        copyImageAttachmentToAppStorage(
                            context = context,
                            appFilesDir = appFilesDir,
                            uri = uri,
                        )
                    }
                }
            }.onSuccess { copied ->
                val next = (imageAttachments + copied).takeLast(MAX_CHAT_IMAGE_ATTACHMENTS)
                val dropped = imageAttachments.size + copied.size - next.size
                imageAttachments = next
                imageAttachmentNotice = when {
                    dropped > 0 -> "Attached ${copied.size} image(s); kept the latest $MAX_CHAT_IMAGE_ATTACHMENTS."
                    copied.size == 1 -> "Image attached."
                    else -> "${copied.size} images attached."
                }
            }.onFailure { error ->
                imageAttachmentNotice = "Image attach failed: ${error.readableMessage()}"
            }
            imageAttachmentBusy = false
        }
    }

    fun removeImageAttachment(id: String) {
        imageAttachments = imageAttachments.filterNot { it.id == id }
        imageAttachmentNotice = null
    }

    fun createNewProject(projectName: String) {
        if (turnState.isActiveTurn) {
            projectNameError = "Stop the active turn before creating a new project."
            return
        }
        if (projectCreationBusy) {
            return
        }
        val directoryName = projectDirectoryNameCandidate(projectName)
        if (directoryName == null) {
            projectNameError = "Enter a unique project name with letters or numbers."
            return
        }
        projectCreationBusy = true
        scope.launch {
            val result = runCatching {
                val cwd = withContext(Dispatchers.IO) {
                    createNamedProjectDirectory(workspacesRoot, directoryName)
                }
                activeProjectCwd = cwd
                projectFolders = withContext(Dispatchers.IO) {
                    ensureProjectDirectories(workspacesRoot, workspaceRoot)
                }
                startNewThread()
                projectCreatorOpen = false
                projectNameDraft = ""
                projectNameError = null
                sessionDrawerNotice = "Project ${projectNameFromCwd(cwd)} created."
            }
            projectCreationBusy = false
            result.onFailure { error ->
                projectNameError = error.readableMessage()
            }
        }
    }

    fun resumeExistingThread(id: String) {
        if (turnState.isActiveTurn) {
            return
        }
        turnState = TurnPanelState.Starting
        turnPollJob?.cancel()
        turnPollJob = null
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.resumeThread(
                        threadId = id,
                        excludeTurns = false,
                        permissions = activePermissionSelection(permissionSelectionOverrideJson),
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("thread/resume failed"))
                }
                val resumedThread = json
                    .optJSONObject("result")
                    ?.optJSONObject("thread")
                threadId = id
                activeTurnId = null
                activeProjectCwd = resumedThread
                    ?.optNonBlankString("cwd")
                    ?: json.optJSONObject("result")?.optNonBlankString("cwd")
                    ?: recentThreads.firstOrNull { it.id == id }?.cwd
                    ?: activeProjectCwd
                assistantText = ""
                reasoningText = ""
                pendingRequests = emptyList()
                transcriptRows = resumedThread
                    ?.let(::transcriptRowsFromThread)
                    .orEmpty()
                    .takeLast(120)
                turnState = TurnPanelState.Idle
            }.onFailure { error ->
                turnState = TurnPanelState.Error(error.readableMessage())
                appendTranscript(TranscriptRow("error", error.readableMessage(), kind = TimelineKind.Error))
            }
        }
    }

    suspend fun ensureThread(activeClient: OperatorAppServerClient): String {
        threadId?.let { return it }
        val response = withContext(Dispatchers.IO) {
            ensureProjectDirectory(activeProjectCwd)
            activeClient.startThread(
                cwd = activeProjectCwd,
                permissions = activePermissionSelection(permissionSelectionOverrideJson),
                id = "ui-thread-start",
            )
        }
        val json = JSONObject(response)
        if (!json.optBoolean("ok")) {
            throw IllegalStateException(json.optErrorMessage("thread/start failed"))
        }
        val id = json
            .optJSONObject("result")
            ?.optJSONObject("thread")
            ?.optString("id")
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("thread/start returned no thread id")
        threadId = id
        return id
    }

    fun pollTurn(activeClient: OperatorAppServerClient, activeThreadId: String, turnId: String) {
        turnPollJob?.cancel()
        turnPollJob = scope.launch {
            while (true) {
                val event = withContext(Dispatchers.IO) { activeClient.nextEvent() }
                if (!event.isNullOrBlank()) {
                    val json = runCatching { JSONObject(event) }.getOrNull()
                    val method = json?.optString("method").orEmpty()
                    val params = notificationParams(json)
                    val isTurnScopedEvent = method in turnScopedNotificationMethods
                    val isThreadScopedEvent = method == "serverRequest/resolved"
                    if (isTurnScopedEvent && !notificationMatchesTurn(params, activeThreadId, turnId)) {
                        continue
                    }
                    if (isThreadScopedEvent && !notificationMatchesThread(params, activeThreadId)) {
                        continue
                    }
                    when (method) {
                        "turn/started" -> {
                            turnState = TurnPanelState.Running
                        }

                        "thread/tokenUsage/updated" -> {
                            tokenUsageFromParams(params)?.let { tokenUsageSnapshot = it }
                        }

                        "account/rateLimits/updated" -> {
                            statusRateLimitStateFromNotification(params)?.let { rateLimitSnapshot = it }
                        }

                        "item/agentMessage/delta" -> {
                            assistantText += params?.optString("delta").orEmpty()
                        }

                        "item/reasoning/summaryTextDelta",
                        "item/reasoning/textDelta" -> {
                            reasoningText += params?.optString("delta").orEmpty()
                        }

                        "item/reasoning/summaryPartAdded" -> {
                            if (reasoningText.isNotBlank()) {
                                reasoningText += "\n"
                            }
                        }

                        "item/plan/delta" -> {
                            appendTranscript(
                                TranscriptRow(
                                    label = "plan",
                                    value = params?.optString("delta").orEmpty().trim()
                                        .ifBlank { "updated" },
                                    detail = event,
                                    kind = TimelineKind.Thinking,
                                )
                            )
                        }

                        "item/commandExecution/outputDelta" -> {
                            appendTranscript(
                                TranscriptRow(
                                    label = "command output",
                                    value = params?.optString("delta").orEmpty().trim()
                                        .take(180)
                                        .ifBlank { "streamed output" },
                                    detail = event,
                                    kind = TimelineKind.Tool,
                                )
                            )
                        }

                        "item/fileChange/outputDelta" -> {
                            appendTranscript(
                                TranscriptRow(
                                    label = "file output",
                                    value = params?.optString("delta").orEmpty().trim()
                                        .take(180)
                                        .ifBlank { "file change output" },
                                    detail = event,
                                    kind = TimelineKind.Tool,
                                )
                            )
                        }

                        "item/fileChange/patchUpdated" -> {
                            appendTranscript(
                                TranscriptRow(
                                    label = "file patch",
                                    value = "patch updated",
                                    detail = event,
                                    kind = TimelineKind.Tool,
                                )
                            )
                        }

                        "turn/diff/updated" -> {
                            val diff = params?.optString("diff").orEmpty()
                            if (diff.isNotBlank() && reviewInspectorContent != null) {
                                reviewInspectorContent = reviewContentFromRawDiff(
                                    scope = ReviewDiffScope.Event,
                                    cwd = activeProjectCwd,
                                    statusSummary = "active turn diff",
                                    diffText = diff,
                                    repository = reviewInspectorContent?.repository,
                                    notice = "Updated from active turn.",
                                )
                            }
                        }

                        "item/commandExecution/terminalInteraction" -> {
                            appendTranscript(
                                TranscriptRow(
                                    label = "terminal input",
                                    value = params?.optString("stdin").orEmpty().trim()
                                        .take(180)
                                        .ifBlank { "terminal interaction" },
                                    detail = event,
                                    kind = TimelineKind.Tool,
                                )
                            )
                        }

                        "item/mcpToolCall/progress" -> {
                            appendTranscript(
                                TranscriptRow(
                                    label = "tool progress",
                                    value = params?.optString("message").takeIf { !it.isNullOrBlank() }
                                        ?: "progress",
                                    detail = event,
                                    kind = TimelineKind.Tool,
                                )
                            )
                        }

                        "item/completed" -> {
                            val item = params?.optJSONObject("item")
                            val itemType = item?.optString("type").orEmpty()
                            if (itemType == "agentMessage") {
                                assistantText = item?.optString("text")?.takeIf(String::isNotBlank)
                                    ?: assistantText
                            }
                            completedItemRow(item)?.let { row ->
                                appendTranscript(row)
                            }
                        }

                        "turn/completed" -> {
                            if (reasoningText.isNotBlank()) {
                                appendTranscript(
                                    TranscriptRow(
                                        label = "thinking",
                                        value = reasoningText.trim(),
                                        kind = TimelineKind.Thinking,
                                    )
                                )
                                reasoningText = ""
                            }
                            if (assistantText.isNotBlank()) {
                                appendTranscript(
                                    TranscriptRow(
                                        label = "codex",
                                        value = assistantText.trim(),
                                        kind = TimelineKind.Assistant,
                                    )
                                )
                                assistantText = ""
                            }
                            val status = params
                                ?.optJSONObject("turn")
                                ?.optString("status")
                                ?.takeIf(String::isNotBlank)
                                ?: "completed"
                            turnState = TurnPanelState.Completed(status)
                            activeTurnId = null
                            pendingRequests = pendingRequests.filterNot { request ->
                                request.matchesThreadTurn(activeThreadId, turnId)
                            }
                            turnPollJob = null
                            refreshThreads()
                            return@launch
                        }

                        "serverRequest/resolved" -> {
                            val requestId = params?.optString("requestId").orEmpty()
                            if (requestId.isNotBlank()) {
                                pendingRequests = pendingRequests.filterNot { it.requestId == requestId }
                            }
                        }

                        "error", "warning", "guardianWarning" -> {
                            val row = notificationTranscriptRow(
                                method = method,
                                params = params,
                                detail = event,
                            )
                            if (row.kind == TimelineKind.Status) {
                                appendOrReplaceStatus(row)
                            } else {
                                appendTranscript(row)
                            }
                        }

                        else -> {
                            val type = json?.optString("type").orEmpty()
                            if (type == "server.request") {
                                val request = pendingServerRequestFromEvent(json)
                                if (request != null && request.matchesThreadTurn(activeThreadId, turnId)) {
                                    pendingRequests = (pendingRequests + request)
                                        .distinctBy(PendingServerRequest::requestId)
                                }
                            }
                        }
                    }
                } else {
                    delay(25)
                }
            }
        }
    }

    fun startTurn() {
        val text = prompt.trim()
        val attachments = imageAttachments
        if ((text.isBlank() && attachments.isEmpty()) || turnState.isActiveTurn) {
            return
        }
        prompt = ""
        imageAttachments = emptyList()
        imageAttachmentNotice = null
        appendTranscript(
            TranscriptRow(
                "you",
                userSubmissionSummary(text, attachments),
                kind = TimelineKind.User,
            )
        )
        turnState = TurnPanelState.Starting
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val accountResponse = withContext(Dispatchers.IO) {
                    activeClient.readAccount(refreshToken = false)
                }
                val accountState = accountPanelStateFromReadResponse(accountResponse)
                state = accountState
                if (accountState !is AccountPanelState.SignedIn) {
                    throw IllegalStateException("account required")
                }

                val activeThreadId = ensureThread(activeClient)
                val collaborationMode = activeCollaborationMode(activeClient)
                val response = withContext(Dispatchers.IO) {
                    activeClient.startTextTurn(
                        threadId = activeThreadId,
                        cwd = activeProjectCwd,
                        text = text,
                        localImagePaths = attachments.map(PendingImageAttachment::path),
                        model = modelOverride,
                        reasoningEffort = reasoningEffortOverride,
                        serviceTierFast = fastServiceTier,
                        approvalPolicy = approvalPolicyOverride,
                        approvalsReviewer = approvalsReviewerOverride,
                        personality = personalityOverride,
                        permissions = activePermissionSelection(permissionSelectionOverrideJson),
                        collaborationMode = collaborationMode,
                        id = "ui-turn-start",
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("turn/start failed"))
                }
                val turnId = json
                    .optJSONObject("result")
                    ?.optJSONObject("turn")
                    ?.optString("id")
                    ?.takeIf(String::isNotBlank)
                    ?: throw IllegalStateException("turn/start returned no turn id")
                activeTurnId = turnId
                assistantText = ""
                reasoningText = ""
                turnState = TurnPanelState.Running
                pollTurn(activeClient, activeThreadId, turnId)
            }.onFailure { error ->
                turnState = TurnPanelState.Error(error.readableMessage())
                appendTranscript(
                    TranscriptRow(
                        "error",
                        error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun submitReviewComment(path: String, line: Int?, comment: String, diffContext: String) {
        val trimmed = comment.trim()
        if (trimmed.isBlank()) {
            reviewInspectorContent = reviewInspectorContent?.copy(
                notice = "Add a comment before sending.",
            )
            return
        }
        val target = line?.let { "$path:$it" } ?: path
        val instruction = buildString {
            append("Address this review comment at `")
            append(target)
            append("`.\n\n")
            append(trimmed)
            if (diffContext.isNotBlank()) {
                append("\n\nDiff context:\n```diff\n")
                append(diffContext.trim())
                append("\n```")
            }
        }
        appendTranscript(
            TranscriptRow(
                label = "you",
                value = "review comment for $target\n$trimmed",
                kind = TimelineKind.User,
            )
        )
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val activeThreadId = ensureThread(activeClient)
                val turnId = activeTurnId
                if (turnState.isActiveTurn && !turnId.isNullOrBlank()) {
                    val response = withContext(Dispatchers.IO) {
                        activeClient.steerTextTurn(
                            threadId = activeThreadId,
                            turnId = turnId,
                            text = instruction,
                            id = "ui-review-steer",
                        )
                    }
                    val json = JSONObject(response)
                    if (!json.optBoolean("ok")) {
                        throw IllegalStateException(json.optErrorMessage("turn/steer failed"))
                    }
                    reviewInspectorContent = reviewInspectorContent?.copy(
                        notice = "Review comment sent to active turn.",
                    )
                } else {
                    turnState = TurnPanelState.Starting
                    val collaborationMode = activeCollaborationMode(activeClient)
                    val response = withContext(Dispatchers.IO) {
                        activeClient.startTextTurn(
                            threadId = activeThreadId,
                            cwd = activeProjectCwd,
                            text = instruction,
                            model = modelOverride,
                            reasoningEffort = reasoningEffortOverride,
                            serviceTierFast = fastServiceTier,
                            approvalPolicy = approvalPolicyOverride,
                            approvalsReviewer = approvalsReviewerOverride,
                            personality = personalityOverride,
                            permissions = activePermissionSelection(permissionSelectionOverrideJson),
                            collaborationMode = collaborationMode,
                            id = "ui-review-turn-start",
                        )
                    }
                    val json = JSONObject(response)
                    if (!json.optBoolean("ok")) {
                        throw IllegalStateException(json.optErrorMessage("turn/start failed"))
                    }
                    val nextTurnId = json
                        .optJSONObject("result")
                        ?.optJSONObject("turn")
                        ?.optString("id")
                        ?.takeIf(String::isNotBlank)
                        ?: throw IllegalStateException("turn/start returned no turn id")
                    activeTurnId = nextTurnId
                    assistantText = ""
                    reasoningText = ""
                    turnState = TurnPanelState.Running
                    reviewInspectorContent = reviewInspectorContent?.copy(
                        notice = "Review comment started a Codex turn.",
                    )
                    pollTurn(activeClient, activeThreadId, nextTurnId)
                }
            }.onFailure { error ->
                turnState = if (turnState == TurnPanelState.Starting) {
                    TurnPanelState.Error(error.readableMessage())
                } else {
                    turnState
                }
                appendTranscript(
                    TranscriptRow(
                        label = "review comment",
                        value = error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
                reviewInspectorContent = reviewInspectorContent?.copy(
                    notice = error.readableMessage(),
                )
            }
        }
    }

    fun archiveThread(id: String) {
        if (id == threadId && turnState.isActiveTurn) {
            sessionDrawerNotice = "Stop the active turn before archiving this session."
            return
        }
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.request(
                        method = "thread/archive",
                        paramsJson = JSONObject()
                            .put("threadId", id)
                            .toString(),
                        id = "ui-thread-archive",
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("thread/archive failed"))
                }
                if (id == threadId) {
                    startNewThread()
                }
                refreshThreads()
                sessionDrawerNotice = "Session archived."
            }.onFailure { error ->
                sessionDrawerNotice = error.readableMessage()
            }
        }
    }

    fun steerTurn() {
        val text = prompt.trim()
        val attachments = imageAttachments
        val activeThreadId = threadId
        val turnId = activeTurnId
        if ((text.isBlank() && attachments.isEmpty()) || activeThreadId.isNullOrBlank() || turnId.isNullOrBlank()) {
            return
        }
        prompt = ""
        imageAttachments = emptyList()
        imageAttachmentNotice = null
        appendTranscript(
            TranscriptRow(
                "you",
                userSubmissionSummary(text, attachments),
                kind = TimelineKind.User,
            )
        )
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.steerTextTurn(
                        threadId = activeThreadId,
                        turnId = turnId,
                        text = text,
                        localImagePaths = attachments.map(PendingImageAttachment::path),
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("turn/steer failed"))
                }
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        "steer error",
                        error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun interruptTurn() {
        val activeThreadId = threadId
        val turnId = activeTurnId
        if (
            activeThreadId.isNullOrBlank() ||
            turnId.isNullOrBlank() ||
            turnState == TurnPanelState.Interrupting
        ) {
            return
        }
        turnState = TurnPanelState.Interrupting
        scope.launch {
            runCatching {
                client?.let { activeClient ->
                    withContext(Dispatchers.IO) {
                        activeClient.interruptTurn(activeThreadId, turnId)
                    }
                }
            }.onFailure { error ->
                turnState = TurnPanelState.Running
                appendTranscript(
                    TranscriptRow(
                        "stop error",
                        error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun appendSlashError(message: String) {
        appendTranscript(
            TranscriptRow(
                label = "slash",
                value = message,
                kind = TimelineKind.Error,
            )
        )
    }

    fun setPersonalitySelection(value: String?) {
        val normalized = value
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in setOf("friendly", "pragmatic", "none") }
        if (value != null && normalized == null) {
            appendSlashError("Usage: /personality [friendly|pragmatic|none|default]")
            return
        }
        personalityOverride = normalized
        persistConfigEdits(
            summary = "personality",
            edits = JSONArray()
                .put(configEdit("personality", normalized ?: JSONObject.NULL)),
        )
        slashInteraction = null
        appendTranscript(
            TranscriptRow(
                label = "/personality",
                value = normalized?.let { "next turns will use $it personality" }
                    ?: "personality override cleared; next turns use config default",
                kind = TimelineKind.System,
            )
        )
    }

    fun setPermissionPresetSelection(option: SlashPickerOption) {
        val preset = option.payload
        val permissions = preset?.optJSONObject("permissions")
        val approvalPolicy = preset?.optNonBlankString("approvalPolicy")
        val approvalsReviewer = preset?.optNonBlankString("approvalsReviewer")
        if (preset == null || permissions == null || approvalPolicy == null || approvalsReviewer == null) {
            appendSlashError("Permission preset ${option.label} did not include a complete Codex turn context")
            return
        }
        approvalPolicyOverride = approvalPolicy
        approvalsReviewerOverride = approvalsReviewer
        permissionSelectionOverrideJson = permissions.toString()
        slashInteraction = null
        appendTranscript(
            TranscriptRow(
                label = "/permissions",
                value = "next turns will use ${option.label.lowercase()} permissions",
                kind = TimelineKind.System,
            )
        )
    }

    fun clearPermissionPresetOverrides(commandName: String) {
        approvalPolicyOverride = null
        approvalsReviewerOverride = null
        permissionSelectionOverrideJson = null
        slashInteraction = null
        appendTranscript(
            TranscriptRow(
                label = commandName,
                value = "permission preset cleared; next turns use Android workspace permissions and config default approval behavior",
                kind = TimelineKind.System,
            )
        )
    }

    fun selectSlashOption(action: SlashOptionAction, option: SlashPickerOption) {
        when (action) {
            SlashOptionAction.CollaborationMode -> {
                val mode = option.token.lowercase()
                when (mode) {
                    "plan" -> setPlanMode(true)
                    "default", "code", "execute", "pair_programming" -> setPlanMode(false)
                    else -> appendSlashError("Unsupported collaboration mode ${option.label}")
                }
                slashInteraction = null
            }

            SlashOptionAction.Personality -> {
                setPersonalitySelection(
                    option.token.takeUnless { it.equals("default", ignoreCase = true) },
                )
            }

            SlashOptionAction.PermissionPreset -> {
                setPermissionPresetSelection(option)
            }
        }
    }

    fun openPersonalityPicker() {
        slashInteraction = SlashInteraction.OptionPicker(
            title = "Personality",
            detail = "Choose the communication style for future Codex turns.",
            action = SlashOptionAction.Personality,
            options = personalityOptions(personalityOverride),
        )
    }

    fun openPermissionsPicker(commandName: String) {
        runCapabilitySlashCommand(
            commandName = commandName,
            method = "approvalPreset/list",
            summarize = { json ->
                val presetCount = permissionPresetOptionsFromResponse(
                    response = json,
                    approvalPolicyOverride = approvalPolicyOverride,
                    permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                    approvalsReviewerOverride = approvalsReviewerOverride,
                ).size
                "$presetCount Codex permission presets available"
            },
            afterSuccess = { json ->
                slashInteraction = SlashInteraction.OptionPicker(
                    title = "Permissions",
                    detail = "Choose approval and sandbox behavior from Codex-provided presets.",
                    action = SlashOptionAction.PermissionPreset,
                    options = permissionPresetOptionsFromResponse(
                        response = json,
                        approvalPolicyOverride = approvalPolicyOverride,
                        permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                        approvalsReviewerOverride = approvalsReviewerOverride,
                    ),
                )
            },
        )
    }

    fun requireThreadForSlash(commandName: String): String? {
        val activeThreadId = threadId
        if (activeThreadId.isNullOrBlank()) {
            appendSlashError("$commandName requires an active Codex thread")
            return null
        }
        return activeThreadId
    }

    fun slashNotMobileBacked(commandName: String, reason: String) {
        appendTranscript(
            TranscriptRow(
                label = commandName,
                value = reason,
                kind = TimelineKind.System,
            )
        )
    }

    fun runThreadActionSlashCommand(
        commandName: String,
        method: String,
        params: JSONObject? = null,
        successLabel: String,
        afterSuccess: (JSONObject) -> Unit = {},
    ) {
        appendTranscript(
            TranscriptRow(
                label = "slash",
                value = "$commandName -> $method",
                kind = TimelineKind.System,
            )
        )
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.request(
                        method = method,
                        paramsJson = params?.toString(),
                        id = "ui-slash-${commandName.removePrefix("/")}",
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("$method failed"))
                }
                afterSuccess(json)
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = successLabel,
                        detail = response,
                        kind = TimelineKind.Tool,
                    )
                )
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun startReviewSlashCommand(commandName: String, args: String) {
        val activeThreadId = requireThreadForSlash(commandName) ?: return
        appendTranscript(TranscriptRow("slash", "$commandName -> review/start", kind = TimelineKind.System))
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val target = reviewTargetFromSlashArgs(args)
                val params = JSONObject()
                    .put("threadId", activeThreadId)
                    .put("target", target)
                val response = withContext(Dispatchers.IO) {
                    activeClient.request(
                        method = "review/start",
                        paramsJson = params.toString(),
                        id = "ui-slash-review",
                    )
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("review/start failed"))
                }
                val result = json.optJSONObject("result") ?: JSONObject()
                val reviewThreadId = result.optString("reviewThreadId").takeIf(String::isNotBlank)
                    ?: activeThreadId
                val turnId = result.optJSONObject("turn")?.optString("id")?.takeIf(String::isNotBlank)
                    ?: throw IllegalStateException("review/start returned no turn id")
                threadId = reviewThreadId
                activeTurnId = turnId
                turnState = TurnPanelState.Running
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = "review started in ${shortId(reviewThreadId)}",
                        detail = response,
                        kind = TimelineKind.Tool,
                    )
                )
                pollTurn(activeClient, reviewThreadId, turnId)
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun runSlashCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        val commandToken = trimmed.substringBefore(" ").lowercase()
        val args = trimmed.substringAfter(" ", "").trim()
        val command = slashCommandFor(commandToken)
        val commandName = command?.name ?: commandToken
        prompt = ""
        if (command == null) {
            appendSlashError("unknown command $commandToken")
            return
        }
        if (turnState.isActiveTurn && !command.availableDuringTurn) {
            appendSlashError("$commandName is disabled while a turn is running")
            return
        }

        when (commandName) {
            "/help" -> {
                appendTranscript(
                    TranscriptRow(
                        label = "slash",
                        value = slashHelpText(),
                        kind = TimelineKind.System,
                    )
                )
            }

            "/new" -> {
                startNewThread()
                appendTranscript(TranscriptRow("slash", "started a new thread", kind = TimelineKind.System))
            }

            "/resume" -> {
                scope.launch { drawerState.open() }
            }

            "/account" -> {
                refreshAccount(refreshToken = true)
                settingsOpen = true
                scope.launch { drawerState.close() }
            }

            "/clear" -> {
                assistantText = ""
                reasoningText = ""
                pendingRequests = emptyList()
                transcriptRows = emptyList()
            }

            "/stop" -> {
                if (activeTurnId.isNullOrBlank() && threadId.isNullOrBlank()) {
                    appendSlashError("/stop has no active turn or thread to stop")
                    return
                }
                if (!activeTurnId.isNullOrBlank()) {
                    interruptTurn()
                }
                threadId?.let { activeThreadId ->
                    runThreadActionSlashCommand(
                        commandName = commandToken,
                        method = "thread/backgroundTerminals/clean",
                        params = JSONObject().put("threadId", activeThreadId),
                        successLabel = "background terminals cleaned",
                    )
                }
            }

            "/mcp" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "mcpServerStatus/list",
                    params = JSONObject()
                        .put("limit", 20)
                        .put("detail", if (trimmed.endsWith(" verbose")) "full" else "toolsAndAuthOnly"),
                    summarize = ::mcpStatusSummary,
                )
            }

            "/apps" -> {
                val params = JSONObject().put("limit", 20)
                threadId?.let { params.put("threadId", it) }
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "app/list",
                    params = params,
                    summarize = ::appListSummary,
                )
            }

            "/plugins" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "plugin/list",
                    params = JSONObject()
                        .put("cwds", JSONArray().put(activeProjectCwd)),
                    summarize = ::pluginListSummary,
                )
            }

            "/skills" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "skills/list",
                    params = JSONObject()
                        .put("cwds", JSONArray().put(activeProjectCwd))
                        .put("forceReload", false),
                    summarize = ::skillsListSummary,
                )
            }

            "/model" -> {
                if (args.isBlank()) {
                    runCapabilitySlashCommand(
                        commandName = commandName,
                        method = "model/list",
                        params = JSONObject()
                            .put("limit", 50)
                            .put("includeHidden", false),
                        summarize = ::modelListSummary,
                        afterSuccess = { json ->
                            slashInteraction = SlashInteraction.ModelPicker(
                                models = modelOptionsFromResponse(json),
                                selectedModelId = modelOverride,
                                selectedReasoningEffort = reasoningEffortOverride,
                            )
                        },
                    )
                } else {
                    if (args.equals("default", ignoreCase = true) || args.equals("clear", ignoreCase = true)) {
                        selectModelOverride(null)
                    } else {
                        selectModelOverride(args)
                    }
                }
            }

            "/fast" -> {
                when (args.lowercase()) {
                    "", "toggle" -> fastServiceTier = !fastServiceTier
                    "on", "true", "1" -> fastServiceTier = true
                    "off", "false", "0" -> fastServiceTier = false
                    "status" -> Unit
                    else -> {
                        appendSlashError("Usage: /fast [on|off|status]")
                        return
                    }
                }
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = if (fastServiceTier) "fast service tier enabled for next turns" else "fast service tier disabled",
                        kind = TimelineKind.System,
                    )
                )
            }

            "/approvals" -> {
                val policy = approvalPolicyFromSlashArg(args)
                if (args.equals("default", ignoreCase = true) || args.equals("clear", ignoreCase = true)) {
                    clearPermissionPresetOverrides(commandName)
                } else if (policy == null && args.isNotBlank()) {
                    appendSlashError("Usage: /approvals [untrusted|on-failure|on-request|never]")
                } else if (policy != null) {
                    approvalPolicyOverride = policy
                    appendTranscript(
                        TranscriptRow(
                            label = commandName,
                            value = "next turns will use approval policy $policy",
                            kind = TimelineKind.System,
                        )
                    )
                } else {
                    openPermissionsPicker(commandName)
                }
            }

            "/permissions" -> {
                when {
                    args.isBlank() -> openPermissionsPicker(commandName)
                    args.equals("clear", ignoreCase = true) -> clearPermissionPresetOverrides(commandName)
                    else -> runCapabilitySlashCommand(
                        commandName = commandName,
                        method = "approvalPreset/list",
                        summarize = { json ->
                            val options = permissionPresetOptionsFromResponse(
                                response = json,
                                approvalPolicyOverride = approvalPolicyOverride,
                                permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                                approvalsReviewerOverride = approvalsReviewerOverride,
                            )
                            if (options.any { slashOptionMatchesArg(it, args) }) {
                                "permission preset selected from Codex presets"
                            } else {
                                "no Codex permission preset named ${args.trim()}"
                            }
                        },
                        afterSuccess = { json ->
                            val options = permissionPresetOptionsFromResponse(
                                response = json,
                                approvalPolicyOverride = approvalPolicyOverride,
                                permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                                approvalsReviewerOverride = approvalsReviewerOverride,
                            )
                            val option = options.firstOrNull { slashOptionMatchesArg(it, args) }
                            if (option == null) {
                                appendSlashError("No Codex permission preset named ${args.trim()}. Run /permissions to choose from the current list.")
                            } else {
                                setPermissionPresetSelection(option)
                            }
                        },
                    )
                }
            }

            "/experimental" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "experimentalFeature/list",
                    params = JSONObject().put("limit", 50),
                    summarize = ::experimentalFeatureSummary,
                    afterSuccess = { json ->
                        slashInteraction = SlashInteraction.ExperimentalPicker(
                            features = experimentalOptionsFromResponse(json),
                        )
                    },
                )
            }

            "/hooks" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "hooks/list",
                    params = JSONObject().put("cwds", JSONArray().put(activeProjectCwd)),
                    summarize = ::hooksListSummary,
                )
            }

            "/collab" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "collaborationMode/list",
                    params = JSONObject(),
                    summarize = ::collaborationModeSummary,
                    afterSuccess = { json ->
                        slashInteraction = SlashInteraction.OptionPicker(
                            title = "Collaboration",
                            detail = "Choose how Codex should collaborate on future turns.",
                            action = SlashOptionAction.CollaborationMode,
                            options = collaborationModeOptionsFromResponse(json, planModeEnabled),
                        )
                    },
                )
            }

            "/review" -> {
                loadReviewInspector(ReviewDiffScope.Head)
                startReviewSlashCommand(commandName, args)
            }

            "/rename" -> {
                val activeThreadId = requireThreadForSlash(commandName) ?: return
                if (args.isBlank()) {
                    appendSlashError("Usage: /rename <thread name>")
                    return
                }
                runThreadActionSlashCommand(
                    commandName = commandName,
                    method = "thread/name/set",
                    params = JSONObject()
                        .put("threadId", activeThreadId)
                        .put("name", args),
                    successLabel = "renamed thread",
                    afterSuccess = { refreshThreads() },
                )
            }

            "/fork" -> {
                val activeThreadId = requireThreadForSlash(commandName) ?: return
                runThreadActionSlashCommand(
                    commandName = commandName,
                    method = "thread/fork",
                    params = JSONObject()
                        .put("threadId", activeThreadId)
                        .put("excludeTurns", true)
                        .put("persistExtendedHistory", true),
                    successLabel = "forked thread",
                    afterSuccess = { json ->
                        val forkedId = json.optJSONObject("result")
                            ?.optJSONObject("thread")
                            ?.optString("id")
                            ?.takeIf(String::isNotBlank)
                        forkedId?.let {
                            threadId = it
                            activeTurnId = null
                            turnState = TurnPanelState.Idle
                            assistantText = ""
                            reasoningText = ""
                            transcriptRows = listOf(
                                TranscriptRow(
                                    label = "thread",
                                    value = "forked ${shortId(it)}",
                                    kind = TimelineKind.System,
                                )
                            )
                            refreshThreads()
                        }
                    },
                )
            }

            "/init" -> {
                if (File(activeProjectCwd, "AGENTS.md").exists()) {
                    appendTranscript(
                        TranscriptRow(
                            label = commandName,
                            value = "AGENTS.md already exists here; skipping /init to avoid overwriting it",
                            kind = TimelineKind.System,
                        )
                    )
                } else {
                    prompt = INIT_AGENTS_PROMPT
                    startTurn()
                }
            }

            "/compact" -> {
                val activeThreadId = requireThreadForSlash(commandName) ?: return
                runThreadActionSlashCommand(
                    commandName = commandName,
                    method = "thread/compact/start",
                    params = JSONObject().put("threadId", activeThreadId),
                    successLabel = "compaction requested",
                )
            }

            "/goal" -> {
                val activeThreadId = requireThreadForSlash(commandName) ?: return
                when {
                    args.isBlank() -> runCapabilitySlashCommand(
                        commandName = commandName,
                        method = "thread/goal/get",
                        params = JSONObject().put("threadId", activeThreadId),
                        summarize = ::threadGoalSummary,
                    )

                    args.equals("clear", ignoreCase = true) -> runThreadActionSlashCommand(
                        commandName = commandName,
                        method = "thread/goal/clear",
                        params = JSONObject().put("threadId", activeThreadId),
                        successLabel = "goal cleared",
                    )

                    else -> runThreadActionSlashCommand(
                        commandName = commandName,
                        method = "thread/goal/set",
                        params = JSONObject()
                            .put("threadId", activeThreadId)
                            .put("objective", args),
                        successLabel = "goal updated",
                    )
                }
            }

            "/plan" -> {
                when (args.lowercase()) {
                    "", "toggle" -> setPlanMode(!planModeEnabled)
                    "on", "true", "1" -> setPlanMode(true)
                    "off", "false", "0" -> setPlanMode(false)
                    "status" -> appendTranscript(
                        TranscriptRow(
                            label = if (planModeEnabled) "plan" else "code",
                            value = if (planModeEnabled) "plan mode is enabled" else "code mode is enabled",
                            kind = TimelineKind.Mode,
                        )
                    )

                    else -> {
                        planModeEnabled = true
                        prompt = args
                        startTurn()
                    }
                }
            }

            "/diff" -> {
                appendTranscript(
                    TranscriptRow(
                        label = commandName,
                        value = "opened review inspector",
                        kind = TimelineKind.System,
                    )
                )
                loadReviewInspector(ReviewDiffScope.Head)
            }

            "/mention" -> {
                prompt = "@"
            }

            "/status" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "config/read",
                    params = JSONObject()
                        .put("includeLayers", false)
                        .put("cwd", activeProjectCwd),
                    summarize = { json ->
                        localStatusSummary(
                            json = json,
                            threadId = threadId,
                            modelOverride = modelOverride,
                            reasoningEffortOverride = reasoningEffortOverride,
                            fastServiceTier = fastServiceTier,
                            approvalPolicyOverride = approvalPolicyOverride,
                            approvalsReviewerOverride = approvalsReviewerOverride,
                            permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                            personalityOverride = personalityOverride,
                            planModeEnabled = planModeEnabled,
                            accountState = state,
                        )
                    },
                )
            }

            "/doctor" -> {
                runDoctorSlashCommand(
                    commandName = commandName,
                    includeNetwork = args.contains("network", ignoreCase = true),
                )
            }

            "/debug-config" -> {
                runCapabilitySlashCommand(
                    commandName = commandName,
                    method = "config/read",
                    params = JSONObject()
                        .put("includeLayers", true)
                        .put("cwd", activeProjectCwd),
                    summarize = ::debugConfigSummary,
                )
            }

            "/logout" -> {
                runThreadActionSlashCommand(
                    commandName = commandName,
                    method = "account/logout",
                    successLabel = "signed out",
                    afterSuccess = {
                        state = AccountPanelState.SignedOut("signed out")
                    },
                )
            }

            "/quit", "/exit" -> {
                (context as? Activity)?.moveTaskToBack(true)
                    ?: slashNotMobileBacked(commandName, "Android activity exit is not available from this context")
            }

            "/feedback" -> {
                if (args.isBlank()) {
                    appendSlashError("Usage: /feedback <short description>")
                } else {
                    runThreadActionSlashCommand(
                        commandName = commandName,
                        method = "feedback/upload",
                        params = JSONObject()
                            .put("classification", "mobile")
                            .put("reason", args)
                            .put("threadId", threadId ?: JSONObject.NULL)
                            .put("includeLogs", false),
                        successLabel = "feedback uploaded",
                    )
                }
            }

            "/copy" -> {
                val textToCopy = assistantText.takeIf(String::isNotBlank)
                    ?: transcriptRows.lastOrNull { it.kind == TimelineKind.Assistant }?.value
                if (textToCopy.isNullOrBlank()) {
                    appendSlashError("/copy has no assistant message to copy")
                } else {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Codex response", textToCopy))
                    appendTranscript(TranscriptRow(commandName, "copied last assistant message", kind = TimelineKind.System))
                }
            }

            "/ps" -> {
                val activeThreadId = requireThreadForSlash(commandName) ?: return
                runThreadActionSlashCommand(
                    commandName = commandName,
                    method = "thread/shellCommand",
                    params = JSONObject()
                        .put("threadId", activeThreadId)
                        .put("command", "ps -A | head -80"),
                    successLabel = "requested process list",
                )
            }

            "/keymap" -> slashNotMobileBacked(commandName, "hardware/mobile keymap UI is not built yet")
            "/setup-default-sandbox" -> slashNotMobileBacked(commandName, "Windows degraded-sandbox setup is desktop-only")
            "/sandbox-add-read-dir" -> slashNotMobileBacked(commandName, "additional read roots need Android permission-profile UI before enabling")
            "/autoreview" -> slashNotMobileBacked(commandName, "auto-review denial replay needs its Android screen; use /permissions to route future approvals through auto-review")
            "/memories" -> slashNotMobileBacked(commandName, "memory inspection/reset needs confirmation UI before enabling")
            "/agent", "/subagents" -> slashNotMobileBacked(commandName, "agent picker and subagent spawning UI are not wired yet")
            "/side" -> slashNotMobileBacked(commandName, "side-conversation UI is not wired yet")
            "/title" -> slashNotMobileBacked(commandName, "terminal-title setup is desktop-only")
            "/statusline" -> slashNotMobileBacked(commandName, "terminal statusline setup is desktop-only")
            "/theme" -> slashNotMobileBacked(commandName, "theme picker needs Android settings UI")
            "/personality" -> {
                when {
                    args.isBlank() -> openPersonalityPicker()
                    args.equals("default", ignoreCase = true) || args.equals("clear", ignoreCase = true) ->
                        setPersonalitySelection(null)
                    else -> setPersonalitySelection(args)
                }
            }
            "/realtime" -> slashNotMobileBacked(commandName, "realtime audio is not wired for Android yet")
        }
    }

    fun restoreArchivedThread(id: String) {
        archivedChatsNotice = null
        pendingArchivedDeleteThreadId = null
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.unarchiveThread(threadId = id)
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("thread/unarchive failed"))
                }
                archivedChatsNotice = "Session restored."
                refreshArchivedThreads()
                refreshThreads()
            }.onFailure { error ->
                archivedChatsNotice = error.readableMessage()
            }
        }
    }

    fun deleteArchivedThread(id: String) {
        archivedChatsNotice = null
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.deleteThread(threadId = id, archived = true)
                }
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optErrorMessage("thread/delete failed"))
                }
                pendingArchivedDeleteThreadId = null
                archivedChatsNotice = "Session permanently deleted."
                refreshArchivedThreads()
            }.onFailure { error ->
                archivedChatsNotice = error.readableMessage()
            }
        }
    }

    fun submitPrompt() {
        val text = prompt.trim()
        if (text.startsWith("/") && imageAttachments.isEmpty()) {
            runSlashCommand(text)
        } else {
            startTurn()
        }
    }

    fun answerServerRequest(request: PendingServerRequest, action: ServerRequestAction) {
        if (!request.matchesActiveScope(threadId, activeTurnId)) {
            pendingRequests = pendingRequests.filterNot { it.requestId == request.requestId }
            appendTranscript(
                TranscriptRow(
                    "server request",
                    "request no longer belongs to the active turn",
                    kind = TimelineKind.System,
                )
            )
            return
        }
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = serverRequestResponse(request, action)
                withContext(Dispatchers.IO) {
                    if (response == null) {
                        activeClient.failServerRequest(
                            request.requestId,
                            serverRequestErrorResponse(request, action).toString(),
                        )
                    } else {
                        activeClient.respondToServerRequest(request.requestId, response.toString())
                    }
                }
                pendingRequests = pendingRequests.filterNot { it.requestId == request.requestId }
                appendTranscript(
                    TranscriptRow(
                        "server request",
                        "${action.label} ${request.shortMethod}",
                        kind = TimelineKind.Approval,
                    )
                )
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        "approval error",
                        error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun answerInteractiveRequest(
        request: PendingServerRequest,
        response: JSONObject,
        transcriptSummary: String,
    ) {
        if (!request.matchesActiveScope(threadId, activeTurnId)) {
            pendingRequests = pendingRequests.filterNot { it.requestId == request.requestId }
            appendTranscript(
                TranscriptRow(
                    "interactive response",
                    "request no longer belongs to the active turn",
                    kind = TimelineKind.System,
                )
            )
            return
        }
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                withContext(Dispatchers.IO) {
                    activeClient.respondToServerRequest(request.requestId, response.toString())
                }
                pendingRequests = pendingRequests.filterNot { it.requestId == request.requestId }
                appendTranscript(
                    TranscriptRow(
                        "interactive response",
                        transcriptSummary.ifBlank { "submitted ${request.shortMethod}" },
                        detail = response.toString(2),
                        kind = TimelineKind.Approval,
                    )
                )
            }.onFailure { error ->
                appendTranscript(
                    TranscriptRow(
                        "interactive response error",
                        error.readableMessage(),
                        kind = TimelineKind.Error,
                    )
                )
            }
        }
    }

    fun pollLoginCompletion(activeClient: OperatorAppServerClient, loginId: String) {
        loginPollJob?.cancel()
        loginPollJob = scope.launch {
            while (true) {
                val event = withContext(Dispatchers.IO) { activeClient.nextEvent() }
                if (!event.isNullOrBlank()) {
                    val json = runCatching { JSONObject(event) }.getOrNull()
                    val method = json?.optString("method").orEmpty()
                    if (method == "account/login/completed") {
                        val payload = accountLoginCompletedPayload(json)
                        if (payload?.optString("loginId") == loginId) {
                            if (payload.optBoolean("success")) {
                                val response = withContext(Dispatchers.IO) {
                                    activeClient.readAccount(refreshToken = true)
                                }
                                state = accountPanelStateFromReadResponse(response)
                            } else {
                                val error = payload.optString("error")
                                    .takeIf(String::isNotBlank)
                                    ?: "login canceled"
                                state = AccountPanelState.SignedOut(error)
                            }
                            loginPollJob = null
                            return@launch
                        }
                    } else if (method == "account/updated") {
                        val response = withContext(Dispatchers.IO) {
                            activeClient.readAccount(refreshToken = false)
                        }
                        state = accountPanelStateFromReadResponse(response)
                    }
                } else {
                    delay(75)
                }
            }
        }
    }

    fun startDeviceLogin() {
        state = AccountPanelState.Loading
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                val response = withContext(Dispatchers.IO) {
                    activeClient.startChatGptDeviceCodeLogin()
                }
                val pending = accountPanelStateFromLoginStartResponse(response)
                state = pending
                pollLoginCompletion(activeClient, pending.loginId)
            }.onFailure { error ->
                state = AccountPanelState.Error(error.readableMessage())
            }
        }
    }

    fun cancelLogin(loginId: String) {
        loginPollJob?.cancel()
        loginPollJob = null
        scope.launch {
            runCatching {
                val activeClient = startAccountClient()
                withContext(Dispatchers.IO) { activeClient.cancelAccountLogin(loginId) }
                val response = withContext(Dispatchers.IO) {
                    activeClient.readAccount(refreshToken = false)
                }
                state = accountPanelStateFromReadResponse(response)
            }.onFailure { error ->
                state = AccountPanelState.Error(error.readableMessage())
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        attachImageUris(uris)
    }

    LaunchedEffect(appFilesDir, toolInstallState) {
        if (toolInstallState !is ToolInstallUiState.Ready) {
            return@LaunchedEffect
        }
        refreshProjectFolders()
        refreshAccount(refreshToken = false)
        refreshThreads()
        refreshConfigSnapshot()
    }

    LaunchedEffect(activeProjectCwd, toolInstallState) {
        if (toolInstallState !is ToolInstallUiState.Ready) {
            return@LaunchedEffect
        }
        refreshConfigSnapshot()
    }

    LaunchedEffect(activeProjectCwd, statusBarItems, toolInstallState) {
        if (toolInstallState !is ToolInstallUiState.Ready) {
            return@LaunchedEffect
        }
        refreshStatusGitBranch()
    }

    LaunchedEffect(
        threadId,
        activeProjectCwd,
        modelOverride,
        reasoningEffortOverride,
        fastServiceTier,
        approvalPolicyOverride,
        approvalsReviewerOverride,
        permissionSelectionOverrideJson,
        personalityOverride,
        planModeEnabled,
        oledThemeEnabled,
        statusBarItems,
    ) {
        uiPreferences.edit()
            .putNullableString(OPERATOR_UI_ACTIVE_THREAD_ID, threadId)
            .putNullableString(OPERATOR_UI_ACTIVE_PROJECT_CWD, activeProjectCwd)
            .putNullableString(OPERATOR_UI_MODEL_OVERRIDE, modelOverride)
            .putNullableString(OPERATOR_UI_REASONING_EFFORT, reasoningEffortOverride)
            .putNullableString(OPERATOR_UI_APPROVAL_POLICY, approvalPolicyOverride)
            .putNullableString(OPERATOR_UI_APPROVALS_REVIEWER, approvalsReviewerOverride)
            .putNullableString(OPERATOR_UI_PERMISSION_SELECTION, permissionSelectionOverrideJson)
            .putNullableString(OPERATOR_UI_PERSONALITY_OVERRIDE, personalityOverride)
            .putBoolean(OPERATOR_UI_FAST_SERVICE_TIER, fastServiceTier)
            .putBoolean(OPERATOR_UI_PLAN_MODE, planModeEnabled)
            .putBoolean(OPERATOR_UI_OLED_THEME, oledThemeEnabled)
            .putString(OPERATOR_UI_STATUS_BAR_ITEMS, statusBarItems.joinToString(",") { it.id })
            .apply()
    }

    LaunchedEffect(state, threadId, transcriptRows.isEmpty(), turnState) {
        val savedThreadId = threadId
        if (
            state is AccountPanelState.SignedIn &&
            !savedThreadId.isNullOrBlank() &&
            savedThreadId != autoResumeAttemptedThreadId &&
            transcriptRows.isEmpty() &&
            !turnState.isActiveTurn
        ) {
            autoResumeAttemptedThreadId = savedThreadId
            resumeExistingThread(savedThreadId)
        }
    }

    LaunchedEffect(drawerState.currentValue, toolInstallState) {
        if (drawerState.isOpen && toolInstallState is ToolInstallUiState.Ready) {
            refreshProjectFolders()
            refreshThreads(showLoading = false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            loginPollJobForDispose?.cancel()
            turnPollJobForDispose?.cancel()
        }
    }

    val timelineItemCount =
        transcriptRows.size +
            pendingRequests.size +
            if (assistantText.isNotBlank()) 1 else 0 +
            if (reasoningText.isNotBlank()) 1 else 0 +
            if (transcriptRows.isEmpty() && assistantText.isBlank() && reasoningText.isBlank()) 1 else 0
    LaunchedEffect(timelineItemCount, assistantText.length, reasoningText.length) {
        if (timelineItemCount > 0) {
            timelineListState.animateScrollToItem(timelineItemCount - 1)
        }
    }

    BackHandler(enabled = inspectorContent != null) {
        inspectorContent = null
    }

    BackHandler(enabled = reviewInspectorContent != null && inspectorContent == null) {
        reviewInspectorContent = null
        pendingReviewAction = null
    }

    BackHandler(enabled = terminalOpen && inspectorContent == null && reviewInspectorContent == null) {
        terminalOpen = false
        requestComposerFocus()
    }

    BackHandler(enabled = archivedChatsOpen && !terminalOpen && inspectorContent == null && reviewInspectorContent == null) {
        archivedChatsOpen = false
    }

    BackHandler(enabled = settingsOpen && !terminalOpen && !archivedChatsOpen && inspectorContent == null && reviewInspectorContent == null) {
        settingsOpen = false
    }

    BackHandler(enabled = projectCreatorOpen && !terminalOpen && inspectorContent == null && reviewInspectorContent == null) {
        closeProjectCreator()
    }

    BackHandler(enabled = !terminalOpen && !settingsOpen && !archivedChatsOpen && !projectCreatorOpen && inspectorContent == null && reviewInspectorContent == null) {
        scope.launch {
            if (drawerState.isOpen) {
                drawerState.close()
            } else {
                hideKeyboardAndClearFocus()
                refreshProjectFolders()
                refreshThreads(showLoading = false)
                drawerState.open()
            }
        }
    }

    val headerThreadId = threadId ?: startupThreadId
    val activeThreadTitle = recentThreads.firstOrNull { it.id == headerThreadId }?.title
    val statusBarContext = MobileStatusBarContext(
        activeThreadId = headerThreadId,
        activeThreadTitle = activeThreadTitle,
        activeProjectCwd = activeProjectCwd,
        turnState = turnState,
        modelOverride = modelOverride,
        reasoningEffortOverride = reasoningEffortOverride,
        fastServiceTier = fastServiceTier,
        config = configSnapshot,
        tokenUsage = tokenUsageSnapshot,
        rateLimits = rateLimitSnapshot,
        gitBranch = gitBranchSnapshot,
        taskProgress = taskProgressFromTranscriptRows(transcriptRows),
    )

    CompositionLocalProvider(LocalMarkdownRenderSettings provides markdownRenderSettings) {
        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !terminalOpen && !settingsOpen && !archivedChatsOpen && !projectCreatorOpen && inspectorContent == null && reviewInspectorContent == null,
            drawerContent = {
                SessionHistoryDrawer(
                    accountState = state,
                    threadListState = threadListState,
                    projectFolders = projectFolders,
                    threads = recentThreads,
                    activeThreadId = threadId,
                    activeProjectCwd = activeProjectCwd,
                    searchQuery = sessionSearchQuery,
                    notice = sessionDrawerNotice,
                    onNewThread = {
                        sessionDrawerNotice = null
                        startNewThread()
                        scope.launch { drawerState.close() }
                    },
                    onNewProject = {
                        sessionDrawerNotice = null
                        openProjectCreator()
                        if (!turnState.isActiveTurn) {
                            scope.launch { drawerState.close() }
                        }
                    },
                    onSearchQueryChange = {
                        sessionSearchQuery = it
                        sessionDrawerNotice = null
                    },
                    onResumeThread = { thread ->
                        thread.cwd?.let { activeProjectCwd = it }
                        sessionDrawerNotice = null
                        resumeExistingThread(thread.id)
                        scope.launch { drawerState.close() }
                    },
                    onArchiveThread = ::archiveThread,
                    onRefreshAccount = { refreshAccount(refreshToken = true) },
                    onOpenSettings = {
                        settingsOpen = true
                        scope.launch { drawerState.close() }
                    },
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OperatorColors.deck)
                    .statusBarsPadding()
            ) {
                ChatTopBar(
                    turnState = turnState,
                    terminalEnabled = toolInstallState is ToolInstallUiState.Ready,
                    statusBarItems = statusBarItems,
                    statusBarContext = statusBarContext,
                    onOpenDrawer = {
                        hideKeyboardAndClearFocus()
                        if (toolInstallState is ToolInstallUiState.Ready) {
                            refreshThreads(showLoading = false)
                        }
                        scope.launch { drawerState.open() }
                    },
                    onOpenTerminal = {
                        hideKeyboardAndClearFocus()
                        settingsOpen = false
                        archivedChatsOpen = false
                        projectCreatorOpen = false
                        terminalOpen = true
                        scope.launch { drawerState.close() }
                    },
                )
                ConversationTimeline(
                    modifier = Modifier.weight(1f),
                    rows = transcriptRows,
                    assistantText = assistantText,
                    reasoningText = reasoningText,
                    pendingRequests = pendingRequests,
                    toolInstallState = toolInstallState,
                    accountState = state,
                    turnState = turnState,
                    projectFolders = projectFolders,
                    threads = recentThreads,
                    activeProjectCwd = activeProjectCwd,
                    listState = timelineListState,
                    onSelectProject = { cwd ->
                        activeProjectCwd = cwd
                        startNewThread()
                    },
                    onNewProject = ::openProjectCreator,
                    onAccept = { answerServerRequest(it, ServerRequestAction.Accept) },
                    onDecline = { answerServerRequest(it, ServerRequestAction.Decline) },
                    onCancel = { answerServerRequest(it, ServerRequestAction.Cancel) },
                    onAnswerInteractiveRequest = ::answerInteractiveRequest,
                    onInspectEvent = { row ->
                        val reviewContent = row.toReviewInspectorContent(activeProjectCwd)
                        if (reviewContent != null) {
                            reviewInspectorContent = reviewContent
                        } else {
                            inspectorContent = row.toInspectorContent()
                        }
                    },
                    onInspectRequest = { inspectorContent = it.toInspectorContent() },
                )
                ComposerDock(
                    toolInstallState = toolInstallState,
                    accountState = state,
                    turnState = turnState,
                    prompt = prompt,
                    promptFocusRequester = promptFocusRequester,
                    imageAttachments = imageAttachments,
                    imageAttachmentNotice = imageAttachmentNotice,
                    imageAttachmentBusy = imageAttachmentBusy,
                    activeTurnId = activeTurnId,
                    slashInteraction = slashInteraction,
                    planModeEnabled = planModeEnabled,
                    onPromptChange = { prompt = it },
                    onAttachImages = { imagePickerLauncher.launch("image/*") },
                    onRemoveImageAttachment = ::removeImageAttachment,
                    onStartTurn = ::submitPrompt,
                    onSteerTurn = ::steerTurn,
                    onInterruptTurn = ::interruptTurn,
                    onRunSlashCommand = ::runSlashCommand,
                    onDismissSlashInteraction = { slashInteraction = null },
                    onSelectModel = { model -> selectModelOverride(model, closeInteraction = false) },
                    onSelectReasoningEffort = ::selectReasoningEffortOverride,
                    onToggleExperimental = ::setExperimentalFeature,
                    onSelectSlashOption = ::selectSlashOption,
                    onTogglePlanMode = { setPlanMode(!planModeEnabled) },
                    onStartDeviceLogin = ::startDeviceLogin,
                    onCancelLogin = ::cancelLogin,
                    onRefreshAccount = { refreshAccount(refreshToken = true) },
                    onOpenLoginUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }
        }
        if (settingsOpen && !archivedChatsOpen) {
            SettingsScreen(
                accountState = state,
                activeThreadId = threadId,
                activeProjectCwd = activeProjectCwd,
                appFilesDir = appFilesDir,
                engineStatus = engineStatus,
                runtimeExtensionStatus = runtimeExtensionStatus,
                startupServerRequestCount = startupServerRequestCount,
                modelOverride = modelOverride,
                reasoningEffortOverride = reasoningEffortOverride,
                fastServiceTier = fastServiceTier,
                approvalPolicyOverride = approvalPolicyOverride,
                approvalsReviewerOverride = approvalsReviewerOverride,
                permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                personalityOverride = personalityOverride,
                planModeEnabled = planModeEnabled,
                oledThemeEnabled = oledThemeEnabled,
                markdownCodeTheme = markdownCodeTheme,
                statusBarItems = statusBarItems,
                statusBarContext = statusBarContext,
                onClose = { settingsOpen = false },
                onRefreshAccount = { refreshAccount(refreshToken = true) },
                onOledThemeChange = ::setOledThemeEnabled,
                onMarkdownCodeThemeChange = ::setMarkdownCodeTheme,
                onToggleStatusBarItem = ::toggleStatusBarItem,
                onOpenTerminalCommand = { command ->
                    hideKeyboardAndClearFocus()
                    settingsOpen = false
                    archivedChatsOpen = false
                    projectCreatorOpen = false
                    terminalInitialCommand = command
                    terminalOpen = true
                    scope.launch { drawerState.close() }
                },
                onOpenArchivedChats = {
                    archivedChatsOpen = true
                    archivedChatsNotice = null
                    pendingArchivedDeleteThreadId = null
                    refreshArchivedThreads()
                },
            )
        }
        if (archivedChatsOpen) {
            ArchivedChatsScreen(
                threadListState = archivedThreadListState,
                threads = archivedThreads,
                selectedProjectCwd = selectedArchivedProjectCwd,
                notice = archivedChatsNotice,
                pendingDeleteThreadId = pendingArchivedDeleteThreadId,
                onClose = { archivedChatsOpen = false },
                onRefresh = {
                    archivedChatsNotice = null
                    pendingArchivedDeleteThreadId = null
                    refreshArchivedThreads()
                },
                onSelectProject = { cwd ->
                    selectedArchivedProjectCwd = cwd
                    archivedChatsNotice = null
                    pendingArchivedDeleteThreadId = null
                },
                onRestoreThread = ::restoreArchivedThread,
                onDeleteThread = { threadIdToDelete ->
                    if (pendingArchivedDeleteThreadId == threadIdToDelete) {
                        deleteArchivedThread(threadIdToDelete)
                    } else {
                        pendingArchivedDeleteThreadId = threadIdToDelete
                        archivedChatsNotice = "Tap Delete again to permanently remove this session."
                    }
                },
                onCancelDelete = {
                    pendingArchivedDeleteThreadId = null
                    archivedChatsNotice = null
                },
            )
        }
        inspectorContent?.let { content ->
            InspectorScreen(
                content = content,
                onClose = { inspectorContent = null },
            )
        }
        reviewInspectorContent?.let { content ->
            ReviewInspectorScreen(
                content = content,
                actionBusy = reviewActionBusy,
                pendingAction = pendingReviewAction,
                onClose = {
                    reviewInspectorContent = null
                    pendingReviewAction = null
                },
                onRefresh = { loadReviewInspector(content.scope.refreshScope) },
                onSelectScope = { loadReviewInspector(it) },
                onAction = ::executeReviewAction,
                onCancelPendingAction = {
                    pendingReviewAction = null
                    reviewInspectorContent = reviewInspectorContent?.copy(notice = null)
                },
                onSubmitComment = ::submitReviewComment,
            )
        }
            if (projectCreatorOpen) {
                ProjectCreationOverlay(
                    value = projectNameDraft,
                    directoryName = projectDirectoryNameCandidate(projectNameDraft),
                    error = projectNameError,
                    busy = projectCreationBusy,
                    onValueChange = {
                        projectNameDraft = it
                        projectNameError = null
                    },
                    onCreate = { createNewProject(projectNameDraft) },
                    onDismiss = ::closeProjectCreator,
                )
            }
            if (terminalOpen) {
                TerminalScreen(
                    appFilesDir = appFilesDir,
                    activeProjectCwd = activeProjectCwd,
                    initialCommand = terminalInitialCommand,
                    onInitialCommandConsumed = { terminalInitialCommand = null },
                    onClose = {
                        terminalOpen = false
                        requestComposerFocus()
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    turnState: TurnPanelState,
    terminalEnabled: Boolean,
    statusBarItems: List<MobileStatusBarItem>,
    statusBarContext: MobileStatusBarContext,
    onOpenDrawer: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val statusText = remember(statusBarItems, statusBarContext) {
        statusBarLine(statusBarItems, statusBarContext)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OperatorColors.deck)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderMenuButton(onClick = onOpenDrawer)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Operator",
                    color = OperatorColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                HeaderTerminalButton(
                    color = turnState.color,
                    enabled = terminalEnabled,
                    onClick = onOpenTerminal,
                )
            }
            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    color = OperatorColors.textTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val TERMINAL_DEFAULT_ROWS = 32
private const val TERMINAL_DEFAULT_COLS = 100
private const val TERMINAL_MIN_ROWS = 8
private const val TERMINAL_MAX_ROWS = 120
private const val TERMINAL_MIN_COLS = 32
private const val TERMINAL_MAX_COLS = 240
private const val TERMINAL_HISTORY_LIMIT = 100
private const val TERMINAL_FONT_SIZE_SP = 13f
private const val TERMINAL_LINE_HEIGHT_SP = 17f

@Composable
private fun TerminalScreen(
    appFilesDir: String,
    activeProjectCwd: String,
    initialCommand: String?,
    onInitialCommandConsumed: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val renderSettings = LocalMarkdownRenderSettings.current
    val terminalTheme = remember(renderSettings.codeTheme) {
        terminalThemeColors(renderSettings.codeTheme)
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val keyRailScrollState = rememberScrollState()
    var input by remember { mutableStateOf("") }
    var status by remember(activeProjectCwd) { mutableStateOf("starting") }
    var notice by remember(activeProjectCwd) { mutableStateOf<String?>(null) }
    var terminalClient by remember { mutableStateOf<OperatorAppServerClient?>(null) }
    var terminalProcessId by remember { mutableStateOf<String?>(null) }
    var terminalInputView by remember { mutableStateOf<EditText?>(null) }
    var commandHistory by remember(activeProjectCwd) { mutableStateOf<List<String>>(emptyList()) }
    var historyCursor by remember(activeProjectCwd) { mutableStateOf<Int?>(null) }
    var historyDraft by remember(activeProjectCwd) { mutableStateOf("") }
    var terminalRows by remember(activeProjectCwd) { mutableStateOf(TERMINAL_DEFAULT_ROWS) }
    var terminalCols by remember(activeProjectCwd) { mutableStateOf(TERMINAL_DEFAULT_COLS) }
    var restartNonce by remember(activeProjectCwd) { mutableStateOf(0) }
    val terminalEmulator = remember(activeProjectCwd, restartNonce) {
        TerminalEmulator(terminalRows, terminalCols)
    }
    var terminalSnapshot by remember(activeProjectCwd, restartNonce) {
        mutableStateOf(terminalEmulator.snapshot())
    }
    val terminalReady = terminalClient != null && terminalProcessId != null

    fun appendOutput(delta: String) {
        if (delta.isEmpty()) {
            return
        }
        terminalSnapshot = terminalEmulator.feed(delta)
    }

    fun showNotice(value: String) {
        notice = value
        scope.launch {
            delay(1_500)
            if (notice == value) {
                notice = null
            }
        }
    }

    suspend fun writeRawInput(delta: String): String? {
        val activeClient = terminalClient ?: return "terminal is not ready"
        val processId = terminalProcessId ?: return "terminal is not ready"
        val response = withContext(Dispatchers.IO) {
            activeClient.writeCommandInput(
                processId = processId,
                input = delta,
            )
        }
        val json = runCatching { JSONObject(response) }.getOrNull()
        return when {
            json?.optBoolean("ok") == true -> null
            json?.optBoolean("ok") == false -> json.optErrorMessage("command/exec/write failed")
            else -> "invalid command/exec/write response"
        }
    }

    fun sendRawInput(delta: String) {
        scope.launch {
            val errorMessage = runCatching { writeRawInput(delta) }
                .getOrElse { error -> error.readableMessage() }
            errorMessage?.let { appendOutput("\r\n[write failed: $it]\r\n") }
        }
    }

    fun sendRawInputWhenReady(delta: String) {
        scope.launch {
            var lastError: String? = null
            repeat(10) { attempt ->
                val errorMessage = runCatching { writeRawInput(delta) }
                    .getOrElse { error -> error.readableMessage() }
                if (errorMessage == null) {
                    return@launch
                }
                lastError = errorMessage
                val retryable = errorMessage.contains("no active command/exec", ignoreCase = true)
                    || errorMessage.contains("terminal is not ready", ignoreCase = true)
                if (!retryable) {
                    appendOutput("\r\n[write failed: $errorMessage]\r\n")
                    return@launch
                }
                if (attempt < 9) {
                    delay(120)
                }
            }
            appendOutput("\r\n[write failed: ${lastError ?: "terminal is not ready"}]\r\n")
        }
    }

    fun requestTerminalInputFocus() {
        val editText = terminalInputView ?: return
        editText.requestFocus()
        editText.post {
            editText.context
                .getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun replaceInput(value: String) {
        input = value
        terminalInputView?.let { editText ->
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(editText.text.length)
            }
        }
        requestTerminalInputFocus()
    }

    fun recallHistory(direction: Int) {
        if (commandHistory.isEmpty()) {
            return
        }
        val cursor = historyCursor
        if (cursor == null && direction > 0) {
            return
        }
        if (cursor == null) {
            historyDraft = input
        }
        val nextCursor = when {
            cursor == null -> commandHistory.lastIndex
            direction < 0 -> (cursor - 1).coerceAtLeast(0)
            else -> {
                val next = cursor + 1
                if (next > commandHistory.lastIndex) {
                    historyCursor = null
                    replaceInput(historyDraft)
                    return
                }
                next
            }
        }
        historyCursor = nextCursor
        replaceInput(commandHistory[nextCursor])
    }

    fun submitInput(
        line: String = input,
        allowEmpty: Boolean = false,
        waitForShell: Boolean = false,
    ): Boolean {
        if (!terminalReady) {
            return false
        }
        val submitted = line
        if (submitted.isEmpty() && !allowEmpty) {
            return false
        }
        submitted.trim().takeIf(String::isNotBlank)?.let { historyEntry ->
            commandHistory = (commandHistory + historyEntry)
                .let { history ->
                    if (history.size >= 2 && history[history.lastIndex - 1] == history.last()) {
                        history.dropLast(1)
                    } else {
                        history
                    }
                }
                .takeLast(TERMINAL_HISTORY_LIMIT)
        }
        historyCursor = null
        historyDraft = ""
        input = ""
        if (waitForShell) {
            sendRawInputWhenReady(submitted + "\r")
        } else {
            sendRawInput(submitted + "\r")
        }
        return true
    }

    fun copyOutput() {
        if (terminalSnapshot.isBlank) {
            return
        }
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Operator terminal output", terminalSnapshot.copyText))
        showNotice("copied output")
        requestTerminalInputFocus()
    }

    fun clearOutput() {
        terminalSnapshot = terminalEmulator.clear()
        showNotice("cleared")
        requestTerminalInputFocus()
    }

    fun restartTerminal() {
        restartNonce += 1
    }

    LaunchedEffect(terminalSnapshot.displayText) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    LaunchedEffect(terminalReady, initialCommand) {
        if (terminalReady) {
            val command = initialCommand?.takeIf(String::isNotBlank)
            if (command != null) {
                submitInput(command, waitForShell = true)
                onInitialCommandConsumed()
            } else {
                requestTerminalInputFocus()
            }
        }
    }

    LaunchedEffect(terminalReady, terminalRows, terminalCols) {
        val activeClient = terminalClient ?: return@LaunchedEffect
        val processId = terminalProcessId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                activeClient.resizeCommandPty(
                    processId = processId,
                    rows = terminalRows,
                    cols = terminalCols,
                )
            }
        }
    }

    LaunchedEffect(context, appFilesDir, activeProjectCwd, restartNonce) {
        terminalSnapshot = terminalEmulator.reset(terminalRows, terminalCols)
        input = ""
        status = "starting"
        notice = null
        terminalClient = null
        terminalProcessId = null
        val processId = "android-terminal-${UUID.randomUUID()}"
        val launchCommand = terminalLaunchCommand(appFilesDir)
        val startResult = withContext(Dispatchers.IO) {
            ensureProjectDirectory(activeProjectCwd)
            OperatorAppServerClient.start(
                appFilesDir = appFilesDir,
                workspaceRoot = activeProjectCwd,
            )
        }
        val activeClient = when (startResult) {
            is OperatorEngineStartResult.Started -> startResult.client
            is OperatorEngineStartResult.Failed -> {
                status = "failed"
                appendOutput("${startResult.message}\r\n")
                return@LaunchedEffect
            }
        }
        terminalClient = activeClient
        terminalProcessId = processId
        status = "running ${launchCommand.label}"

        var execResponse: String? = null
        val execJob = launch(Dispatchers.IO) {
            execResponse = activeClient.execCommand(
                command = launchCommand.command,
                cwd = activeProjectCwd,
                outputBytesCap = null,
                processId = processId,
                tty = true,
                streamStdin = true,
                streamStdoutStderr = true,
                disableTimeout = true,
                disableOutputCap = true,
                env = terminalEnvironment(
                    context = context.applicationContext,
                    appFilesDir = appFilesDir,
                    cwd = activeProjectCwd,
                    shellPath = launchCommand.shellPath,
                ),
                terminalRows = terminalRows,
                terminalCols = terminalCols,
                id = "android-terminal-exec-$processId",
            )
        }

        try {
            while (execJob.isActive) {
                val event = withContext(Dispatchers.IO) { activeClient.nextEvent() }
                if (event == null) {
                    delay(5)
                    continue
                }
                terminalOutputDelta(event, processId)?.let(::appendOutput)
            }
            execJob.join()
            status = terminalExitStatus(execResponse)
        } finally {
            execJob.cancel()
            withContext(Dispatchers.IO) {
                runCatching { activeClient.terminateCommand(processId) }
                runCatching { activeClient.shutdown() }
            }
            if (terminalClient === activeClient) {
                terminalClient = null
                terminalProcessId = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(terminalTheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { requestTerminalInputFocus() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(terminalTheme.background)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalBackButton(theme = terminalTheme, onClick = onClose)
            Text(
                text = buildString {
                    append(projectNameFromCwd(activeProjectCwd))
                    append("  ")
                    append(notice ?: status)
                    append("  ")
                    append(terminalCols)
                    append("x")
                    append(terminalRows)
                },
                color = terminalTheme.muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TerminalIconButton(
                iconRes = R.drawable.ic_lucide_copy,
                contentDescription = "Copy terminal output",
                theme = terminalTheme,
                enabled = !terminalSnapshot.isBlank,
                onClick = ::copyOutput,
            )
            TerminalIconButton(
                iconRes = R.drawable.ic_lucide_trash_2,
                contentDescription = "Clear terminal output",
                theme = terminalTheme,
                enabled = !terminalSnapshot.isBlank,
                onClick = ::clearOutput,
            )
            TerminalIconButton(
                iconRes = R.drawable.ic_lucide_rotate_ccw,
                contentDescription = "Restart shell",
                theme = terminalTheme,
                onClick = ::restartTerminal,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val (nextCols, nextRows) = with(density) {
                        val charWidthPx = TERMINAL_FONT_SIZE_SP.sp.toPx() * 0.62f
                        val lineHeightPx = TERMINAL_LINE_HEIGHT_SP.sp.toPx()
                        val cols = (size.width / charWidthPx).roundToInt()
                            .coerceIn(TERMINAL_MIN_COLS, TERMINAL_MAX_COLS)
                        val rows = (size.height / lineHeightPx).roundToInt()
                            .coerceIn(TERMINAL_MIN_ROWS, TERMINAL_MAX_ROWS)
                        cols to rows
                    }
                    if (nextCols != terminalCols) {
                        terminalCols = nextCols
                    }
                    if (nextRows != terminalRows) {
                        terminalRows = nextRows
                    }
                    if (nextCols != terminalSnapshot.cols || nextRows != terminalSnapshot.rows) {
                        terminalSnapshot = terminalEmulator.resize(nextRows, nextCols)
                    }
                }
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            SelectionContainer {
                Text(
                    text = terminalSnapshot.displayText,
                    color = terminalTheme.foreground,
                    fontSize = TERMINAL_FONT_SIZE_SP.sp,
                    lineHeight = TERMINAL_LINE_HEIGHT_SP.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(keyRailScrollState)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalKeyButton("Esc", terminalTheme, enabled = terminalReady) {
                sendRawInput("\u001B")
                requestTerminalInputFocus()
            }
            TerminalKeyButton("Tab", terminalTheme, enabled = terminalReady) {
                sendRawInput("\t")
                requestTerminalInputFocus()
            }
            TerminalKeyButton("Ctrl-C", terminalTheme, enabled = terminalReady) {
                sendRawInput("\u0003")
                requestTerminalInputFocus()
            }
            TerminalKeyButton("↑", terminalTheme, enabled = terminalReady) {
                sendRawInput("\u001B[A")
                requestTerminalInputFocus()
            }
            TerminalKeyButton("↓", terminalTheme, enabled = terminalReady) {
                sendRawInput("\u001B[B")
                requestTerminalInputFocus()
            }
            TerminalKeyButton("←", terminalTheme, enabled = terminalReady) {
                sendRawInput("\u001B[D")
                requestTerminalInputFocus()
            }
            TerminalKeyButton("→", terminalTheme, enabled = terminalReady) {
                sendRawInput("\u001B[C")
                requestTerminalInputFocus()
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(terminalTheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\$ ",
                color = terminalTheme.foreground,
                fontSize = TERMINAL_FONT_SIZE_SP.sp,
                fontFamily = FontFamily.Monospace,
            )
            TerminalInputField(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 24.dp),
                input = input,
                terminalReady = terminalReady,
                theme = terminalTheme,
                onInputChange = { input = it },
                onInputView = { terminalInputView = it },
                onSubmit = { line ->
                    if (submitInput(line, allowEmpty = true)) {
                        terminalInputView?.text?.clear()
                    }
                },
                onArrowUp = {
                    sendRawInput("\u001B[A")
                    requestTerminalInputFocus()
                },
                onArrowDown = {
                    sendRawInput("\u001B[B")
                    requestTerminalInputFocus()
                },
                onArrowLeft = {
                    sendRawInput("\u001B[D")
                    requestTerminalInputFocus()
                },
                onArrowRight = {
                    sendRawInput("\u001B[C")
                    requestTerminalInputFocus()
                },
                onTab = {
                    sendRawInput("\t")
                    requestTerminalInputFocus()
                },
                onEscape = {
                    sendRawInput("\u001B")
                    requestTerminalInputFocus()
                },
            )
        }
    }
}

@Composable
private fun TerminalBackButton(
    theme: TerminalThemeColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(theme.foreground.copy(alpha = 0.08f))
            .border(1.dp, theme.foreground.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lucide_arrow_left),
            contentDescription = "Back",
            tint = theme.foreground,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TerminalIconButton(
    iconRes: Int,
    contentDescription: String,
    theme: TerminalThemeColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.control.copy(alpha = if (enabled) 1f else 0.42f))
            .border(1.dp, theme.rule.copy(alpha = if (enabled) 1f else 0.54f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = theme.foreground.copy(alpha = if (enabled) 0.9f else 0.36f),
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun TerminalKeyButton(
    label: String,
    theme: TerminalThemeColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(theme.control.copy(alpha = if (enabled) 1f else 0.42f))
            .border(1.dp, theme.rule.copy(alpha = if (enabled) 1f else 0.54f), RoundedCornerShape(7.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = theme.foreground.copy(alpha = if (enabled) 0.9f else 0.38f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun TerminalInputField(
    modifier: Modifier,
    input: String,
    terminalReady: Boolean,
    theme: TerminalThemeColors,
    onInputChange: (String) -> Unit,
    onInputView: (EditText) -> Unit,
    onSubmit: (String) -> Unit,
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    onArrowLeft: () -> Unit,
    onArrowRight: () -> Unit,
    onTab: () -> Unit,
    onEscape: () -> Unit,
) {
    val latestOnInputChange by rememberUpdatedState(onInputChange)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val latestOnArrowUp by rememberUpdatedState(onArrowUp)
    val latestOnArrowDown by rememberUpdatedState(onArrowDown)
    val latestOnArrowLeft by rememberUpdatedState(onArrowLeft)
    val latestOnArrowRight by rememberUpdatedState(onArrowRight)
    val latestOnTab by rememberUpdatedState(onTab)
    val latestOnEscape by rememberUpdatedState(onEscape)
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            EditText(viewContext).apply {
                background = null
                includeFontPadding = false
                setSingleLine(true)
                setPadding(0, 0, 0, 0)
                setTextColor(theme.foreground.toArgb())
                setHintTextColor(theme.muted.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TERMINAL_FONT_SIZE_SP)
                typeface = Typeface.MONOSPACE
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setOnKeyListener { view, keyCode, event ->
                    val isControlKey = keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        keyCode == KeyEvent.KEYCODE_TAB ||
                        keyCode == KeyEvent.KEYCODE_ESCAPE
                    if (!isControlKey) {
                        return@setOnKeyListener false
                    }
                    if (event.action != KeyEvent.ACTION_DOWN) {
                        return@setOnKeyListener true
                    }
                    when (keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            latestOnSubmit((view as EditText).text.toString())
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            latestOnArrowUp()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            latestOnArrowDown()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            latestOnArrowLeft()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            latestOnArrowRight()
                            true
                        }
                        KeyEvent.KEYCODE_TAB -> {
                            latestOnTab()
                            true
                        }
                        KeyEvent.KEYCODE_ESCAPE -> {
                            latestOnEscape()
                            true
                        }
                        else -> false
                    }
                }
                setOnEditorActionListener { view, actionId, event ->
                    val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN
                    val submitAction = actionId == EditorInfo.IME_ACTION_SEND ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_GO
                    if ((event == null && submitAction) || enterPressed) {
                        latestOnSubmit(view.text.toString())
                        true
                    } else {
                        false
                    }
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                        val value = s?.toString().orEmpty()
                        val newlineIndex = listOf(value.indexOf('\n'), value.indexOf('\r'))
                            .filter { it >= 0 }
                            .minOrNull()
                        if (newlineIndex == null) {
                            latestOnInputChange(value)
                        } else {
                            val submitted = value.substring(0, newlineIndex)
                            if (submitted.isNotEmpty()) {
                                latestOnSubmit(submitted)
                            } else {
                                latestOnInputChange("")
                            }
                        }
                    }

                    override fun afterTextChanged(s: Editable?) = Unit
                })
                onInputView(this)
            }
        },
        update = { editText ->
            onInputView(editText)
            editText.isEnabled = true
            editText.setTextColor(theme.foreground.toArgb())
            editText.setHintTextColor(theme.muted.toArgb())
            editText.alpha = if (terminalReady) 1f else 0.72f
            if (editText.text.toString() != input) {
                editText.setText(input)
                editText.setSelection(editText.text.length)
            }
        },
    )
}

private data class TerminalLaunchCommand(
    val command: List<String>,
    val label: String,
    val shellPath: String,
)

private fun terminalLaunchCommand(appFilesDir: String): TerminalLaunchCommand {
    val zsh = File(appFilesDir, "tools/bin/zsh")
    if (zsh.isUsableShell("-f", "-c", "exit 0")) {
        return TerminalLaunchCommand(
            command = listOf(zsh.absolutePath, "-f", "-i"),
            label = "zsh",
            shellPath = zsh.absolutePath,
        )
    }
    val toolchainZsh = File(appFilesDir, "tools/toolchain/usr/bin/zsh")
    if (toolchainZsh.isUsableShell("-f", "-c", "exit 0")) {
        return TerminalLaunchCommand(
            command = listOf(toolchainZsh.absolutePath, "-f", "-i"),
            label = "zsh",
            shellPath = toolchainZsh.absolutePath,
        )
    }
    val bash = File(appFilesDir, "tools/bin/bash")
    if (bash.isUsableShell("--noprofile", "--norc", "-c", "exit 0")) {
        return TerminalLaunchCommand(
            command = listOf(bash.absolutePath, "--noprofile", "--norc", "-i"),
            label = "bash",
            shellPath = bash.absolutePath,
        )
    }
    val toolchainBash = File(appFilesDir, "tools/toolchain/usr/bin/bash")
    if (toolchainBash.isUsableShell("--noprofile", "--norc", "-c", "exit 0")) {
        return TerminalLaunchCommand(
            command = listOf(toolchainBash.absolutePath, "--noprofile", "--norc", "-i"),
            label = "bash",
            shellPath = toolchainBash.absolutePath,
        )
    }
    val bundledSh = File(appFilesDir, "tools/bin/sh")
    if (bundledSh.isUsableShell("-c", "exit 0")) {
        return TerminalLaunchCommand(
            command = listOf(bundledSh.absolutePath, "-i"),
            label = "busybox sh",
            shellPath = bundledSh.absolutePath,
        )
    }
    val bundledAsh = File(appFilesDir, "tools/bin/ash")
    if (bundledAsh.isUsableShell("-c", "exit 0")) {
        return TerminalLaunchCommand(
            command = listOf(bundledAsh.absolutePath, "-i"),
            label = "busybox ash",
            shellPath = bundledAsh.absolutePath,
        )
    }
    return TerminalLaunchCommand(
        command = listOf("/system/bin/sh", "-i"),
        label = "system sh",
        shellPath = "/system/bin/sh",
    )
}

private fun File.isUsableShell(vararg args: String): Boolean {
    if (!isFile || !canExecute()) {
        return false
    }
    return runCatching {
        val process = ProcessBuilder(listOf(absolutePath) + args.toList())
            .redirectErrorStream(true)
            .start()
        val exited = process.waitFor(750, TimeUnit.MILLISECONDS)
        if (!exited) {
            process.destroy()
            false
        } else {
            process.exitValue() == 0
        }
    }.getOrDefault(false)
}

private fun terminalEnvironment(
    context: Context,
    appFilesDir: String,
    cwd: String,
    shellPath: String,
): Map<String, String> {
    val env = OperatorToolInstaller.runtimeEnvironment(
        context = context,
        appFilesDir = appFilesDir,
    ).toMutableMap()
    val projectBin = File(cwd, "node_modules/.bin").absolutePath
    env["PATH"] = listOf(projectBin, env["PATH"].orEmpty())
        .filter(String::isNotBlank)
        .joinToString(":")
    env["PWD"] = cwd
    env["SHELL"] = shellPath
    env["TERM"] = "xterm-256color"
    env["COLORTERM"] = "truecolor"
    env["PS1"] = "\$ "
    env["USER"] = "operator"
    env["LOGNAME"] = "operator"
    env["GIT_CONFIG_NOSYSTEM"] = "true"
    env["PAGER"] = "cat"
    env["GIT_PAGER"] = "cat"
    return env
}

private fun terminalOutputDelta(event: String, processId: String): String? {
    val json = runCatching { JSONObject(event) }.getOrNull() ?: return null
    if (json.optString("method") != "command/exec/outputDelta") {
        return null
    }
    val params = notificationParams(json)
        ?: json.optJSONObject("payload")
        ?: json.optJSONObject("params")
        ?: return null
    if (params.optString("processId") != processId) {
        return null
    }
    val deltaBase64 = params.optString("deltaBase64").takeIf(String::isNotBlank) ?: return null
    val bytes = runCatching { Base64.getDecoder().decode(deltaBase64) }.getOrNull() ?: return null
    return bytes.toString(Charsets.UTF_8)
}

private fun terminalExitStatus(response: String?): String {
    val raw = response ?: return "exited"
    return runCatching {
        val result = commandExecResult(raw, "terminal")
        if (result.exitCode == 0) {
            "exited"
        } else {
            "exited ${result.exitCode}"
        }
    }.getOrElse { error ->
        "failed: ${error.readableMessage()}"
    }
}

private data class TerminalThemeColors(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val control: Color,
    val rule: Color,
)

private fun terminalThemeColors(theme: MarkdownCodeTheme): TerminalThemeColors =
    TerminalThemeColors(
        background = Color(theme.codeBlockBackgroundColor()),
        foreground = Color(theme.codeBlockTextColor()),
        muted = when (theme) {
            MarkdownCodeTheme.Default -> Color(0xFF57606A)
            MarkdownCodeTheme.Operator -> OperatorColors.textTertiary
            MarkdownCodeTheme.Darkula -> Color(0xFF808080)
        },
        control = when (theme) {
            MarkdownCodeTheme.Default -> Color(0xFFFFFFFF)
            MarkdownCodeTheme.Operator -> OperatorColors.panel
            MarkdownCodeTheme.Darkula -> Color(0xFF313335)
        },
        rule = when (theme) {
            MarkdownCodeTheme.Default -> Color(0xFFD0D7DE)
            MarkdownCodeTheme.Operator -> OperatorColors.line
            MarkdownCodeTheme.Darkula -> Color(0xFF555555)
        },
    )

@Composable
private fun ConversationTimeline(
    modifier: Modifier,
    rows: List<TranscriptRow>,
    assistantText: String,
    reasoningText: String,
    pendingRequests: List<PendingServerRequest>,
    toolInstallState: ToolInstallUiState,
    accountState: AccountPanelState,
    turnState: TurnPanelState,
    projectFolders: List<String>,
    threads: List<ThreadSummary>,
    activeProjectCwd: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onAccept: (PendingServerRequest) -> Unit,
    onDecline: (PendingServerRequest) -> Unit,
    onCancel: (PendingServerRequest) -> Unit,
    onAnswerInteractiveRequest: (PendingServerRequest, JSONObject, String) -> Unit,
    onInspectEvent: (TranscriptRow) -> Unit,
    onInspectRequest: (PendingServerRequest) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(OperatorColors.deck),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (rows.isEmpty() && assistantText.isBlank() && reasoningText.isBlank()) {
            item {
                EmptyTranscriptState(
                    toolInstallState = toolInstallState,
                    accountState = accountState,
                    turnState = turnState,
                    projectFolders = projectFolders,
                    threads = threads,
                    activeProjectCwd = activeProjectCwd,
                    onSelectProject = onSelectProject,
                    onNewProject = onNewProject,
                )
            }
        }
        rows.forEach { row ->
            item {
                TimelineRow(row, onInspect = onInspectEvent)
            }
        }
        liveActivityLabel(turnState, assistantText, reasoningText, pendingRequests)?.let { activity ->
            item {
                LiveActivityLine(label = activity.first, detail = activity.second)
            }
        }
        if (reasoningText.isNotBlank()) {
            item {
                TimelineRow(
                    TranscriptRow(
                        label = "thinking",
                        value = reasoningText,
                        kind = TimelineKind.Thinking,
                    ),
                    onInspect = onInspectEvent,
                )
            }
        }
        pendingRequests.forEach { request ->
            item {
                ApprovalTimelineCard(
                    request = request,
                    onAccept = { onAccept(request) },
                    onDecline = { onDecline(request) },
                    onCancel = { onCancel(request) },
                    onInspect = { onInspectRequest(request) },
                    onAnswerInteractiveRequest = { response, summary ->
                        onAnswerInteractiveRequest(request, response, summary)
                    },
                )
            }
        }
        if (assistantText.isNotBlank()) {
            item {
                TimelineRow(
                    TranscriptRow(
                        label = "codex",
                        value = assistantText,
                        kind = TimelineKind.Assistant,
                    ),
                    onInspect = onInspectEvent,
                )
            }
        }
    }
}

private fun liveActivityLabel(
    turnState: TurnPanelState,
    assistantText: String,
    reasoningText: String,
    pendingRequests: List<PendingServerRequest>,
): Pair<String, String>? =
    when {
        turnState == TurnPanelState.Starting -> "working" to "preparing turn"
        turnState == TurnPanelState.Interrupting -> "stopping" to "waiting for Codex to finish interrupt cleanup"
        turnState == TurnPanelState.Running &&
            assistantText.isBlank() &&
            reasoningText.isBlank() &&
            pendingRequests.isEmpty() -> "working" to "waiting for first update"
        else -> null
    }

@Composable
private fun LiveActivityLine(
    label: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(OperatorColors.ok),
        )
        Text(
            text = label.uppercase(),
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = detail,
            color = OperatorColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyTranscriptState(
    toolInstallState: ToolInstallUiState,
    accountState: AccountPanelState,
    turnState: TurnPanelState,
    projectFolders: List<String>,
    threads: List<ThreadSummary>,
    activeProjectCwd: String,
    onSelectProject: (String) -> Unit,
    onNewProject: () -> Unit,
) {
    val showStartupLoading = toolInstallState is ToolInstallUiState.Installing ||
        (toolInstallState is ToolInstallUiState.Ready &&
            (accountState == AccountPanelState.Loading || turnState == TurnPanelState.Starting))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showStartupLoading) {
            DynamicStartupLoadingState(
                toolInstallState = toolInstallState,
                accountState = accountState,
                turnState = turnState,
                activeProjectCwd = activeProjectCwd,
            )
        } else if (toolInstallState is ToolInstallUiState.Failed) {
            Text(
                text = "Runtime preparation failed",
                color = OperatorColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = toolInstallState.message,
                color = OperatorColors.danger,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = emptyTranscriptTitle(accountState),
                color = OperatorColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (turnState == TurnPanelState.Idle) {
                EmptyProjectPicker(
                    projectFolders = projectFolders,
                    threads = threads,
                    activeProjectCwd = activeProjectCwd,
                    onSelectProject = onSelectProject,
                    onNewProject = onNewProject,
                )
            } else {
                Text(
                    text = emptyTranscriptDetail(turnState),
                    color = OperatorColors.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun DynamicStartupLoadingState(
    toolInstallState: ToolInstallUiState,
    accountState: AccountPanelState,
    turnState: TurnPanelState,
    activeProjectCwd: String,
) {
    val transition = rememberInfiniteTransition(label = "startup-loading")
    val orbitRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
        ),
        label = "startup-orbit",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "startup-sweep",
    )
    val glow by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "startup-glow",
    )
    val steps = remember(toolInstallState, accountState, turnState, activeProjectCwd) {
        startupLoadingSteps(toolInstallState, accountState, turnState, activeProjectCwd)
    }
    val activeStep = if (toolInstallState is ToolInstallUiState.Installing) {
        0
    } else {
        ((sweep * steps.size).toInt()).coerceIn(0, steps.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size((106 * glow).dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(OperatorColors.ok.copy(alpha = 0.07f))
                    .border(
                        width = 1.dp,
                        color = OperatorColors.ok.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(30.dp),
                    ),
            )
            Icon(
                painter = painterResource(R.drawable.ic_operator),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(84.dp),
            )
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer { rotationZ = orbitRotation },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(OperatorColors.ok),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = when {
                    toolInstallState is ToolInstallUiState.Installing -> "Preparing Operator runtime"
                    turnState == TurnPanelState.Starting -> "Opening Codex runtime"
                    else -> "Starting Operator"
                },
                color = OperatorColors.textPrimary,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = steps[activeStep].detail,
                color = OperatorColors.textTertiary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StartupProgressRail(progress = sweep)
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            steps.forEachIndexed { index, step ->
                StartupLoadingStepRow(
                    step = step,
                    active = index == activeStep,
                    complete = index < activeStep,
                )
            }
        }
    }
}

@Composable
private fun StartupProgressRail(progress: Float) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(OperatorColors.line),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(OperatorColors.ok),
        )
    }
}

@Composable
private fun StartupLoadingStepRow(
    step: StartupLoadingStep,
    active: Boolean,
    complete: Boolean,
) {
    val markerColor = when {
        active -> OperatorColors.ok
        complete -> OperatorColors.success
        else -> OperatorColors.lineStrong
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (active) 8.dp else 6.dp)
                .clip(CircleShape)
                .background(markerColor),
        )
        Text(
            text = step.label.uppercase(),
            color = if (active) OperatorColors.textPrimary else OperatorColors.textTertiary,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(74.dp),
            maxLines = 1,
        )
        Text(
            text = step.detail,
            color = if (active) OperatorColors.textSecondary else OperatorColors.textTertiary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class StartupLoadingStep(
    val label: String,
    val detail: String,
)

private fun startupLoadingSteps(
    toolInstallState: ToolInstallUiState,
    accountState: AccountPanelState,
    turnState: TurnPanelState,
    activeProjectCwd: String,
): List<StartupLoadingStep> {
    if (toolInstallState is ToolInstallUiState.Installing) {
        return listOf(
            StartupLoadingStep(toolInstallState.label, toolInstallState.detail),
            StartupLoadingStep("workspace", "keeping app files available"),
            StartupLoadingStep("bridge", "waiting for runtime tools"),
            StartupLoadingStep("account", "queued after runtime startup"),
        )
    }
    val accountDetail = when (accountState) {
        AccountPanelState.Loading -> "checking account state"
        is AccountPanelState.SignedIn -> accountState.detail
        is AccountPanelState.LoginPending -> "device login pending"
        is AccountPanelState.SignedOut -> "sign-in needed"
        is AccountPanelState.Error -> "account unavailable"
    }
    return listOf(
        StartupLoadingStep("runtime", if (turnState == TurnPanelState.Starting) "opening Codex runtime" else "warming embedded bridge"),
        StartupLoadingStep("account", accountDetail),
        StartupLoadingStep("workspace", projectNameFromCwd(activeProjectCwd)),
        StartupLoadingStep("thread", if (turnState == TurnPanelState.Starting) "preparing turn" else "readying session"),
    )
}

@Composable
private fun EmptyProjectPicker(
    projectFolders: List<String>,
    threads: List<ThreadSummary>,
    activeProjectCwd: String,
    onSelectProject: (String) -> Unit,
    onNewProject: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val projects = remember(projectFolders, threads, activeProjectCwd) {
        sessionProjectsFromSources(
            threads = threads,
            projectFolders = (projectFolders + activeProjectCwd).distinct(),
        )
    }
    val selectedProject = projects.firstOrNull { it.cwd == activeProjectCwd }
    val pickerWidth = 244.dp
    Box(
        modifier = Modifier.width(pickerWidth),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(OperatorColors.panel)
                .border(1.dp, OperatorColors.line, RoundedCornerShape(999.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedProject?.name ?: projectNameFromCwd(activeProjectCwd),
                color = OperatorColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(
                    if (expanded) {
                        R.drawable.ic_lucide_chevron_up
                    } else {
                        R.drawable.ic_lucide_chevron_down
                    }
                ),
                contentDescription = if (expanded) "Close project picker" else "Open project picker",
                tint = OperatorColors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = OperatorColors.panelRaised,
            modifier = Modifier.width(pickerWidth),
        ) {
            projects.forEach { project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoIndication {
                            expanded = false
                            onSelectProject(project.cwd)
                        }
                        .background(if (project.cwd == activeProjectCwd) OperatorColors.selected else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = project.name,
                            color = OperatorColors.textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when (project.threadCount) {
                                1 -> "1 session"
                                else -> "${project.threadCount} sessions"
                            },
                            color = OperatorColors.textTertiary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OperatorColors.line)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableNoIndication {
                        expanded = false
                        onNewProject()
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_plus),
                    contentDescription = null,
                    tint = OperatorColors.ok,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "New project",
                    color = OperatorColors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun emptyTranscriptTitle(accountState: AccountPanelState): String =
    when (accountState) {
        is AccountPanelState.SignedIn -> "Ready for a Codex task"
        is AccountPanelState.LoginPending -> "Complete device login"
        AccountPanelState.Loading -> "Reading Operator state"
        is AccountPanelState.Error -> "Operator state unavailable"
        is AccountPanelState.SignedOut -> "Sign in to Operator"
    }

private fun emptyTranscriptDetail(turnState: TurnPanelState): String =
    when (turnState) {
        TurnPanelState.Idle -> "choose a project"
        TurnPanelState.Starting -> "opening Codex runtime"
        TurnPanelState.Running -> "Codex is working"
        TurnPanelState.Interrupting -> "stopping current task"
        is TurnPanelState.Completed,
        is TurnPanelState.Error -> turnState.label
    }

@Composable
private fun TimelineRow(
    row: TranscriptRow,
    onInspect: (TranscriptRow) -> Unit,
) {
    when (row.kind) {
        TimelineKind.User -> UserPromptRow(row)
        TimelineKind.Assistant -> AssistantMessageRow(row)
        TimelineKind.Status -> LiveActivityLine(label = row.label, detail = row.value)
        TimelineKind.Mode -> ModeNoticeLine(row)
        TimelineKind.Tool,
        TimelineKind.Thinking,
        TimelineKind.Approval,
        TimelineKind.System,
        TimelineKind.Error -> EventTimelineRow(row, onInspect = onInspect)
    }
}

@Composable
private fun ModeNoticeLine(row: TranscriptRow) {
    val modeColor = when (row.label.lowercase()) {
        "plan" -> OperatorColors.ok
        "code" -> OperatorColors.textSecondary
        else -> OperatorColors.textTertiary
    }
    Text(
        text = row.value,
        color = modeColor.copy(alpha = 0.64f),
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp),
    )
}

@Composable
private fun UserPromptRow(row: TranscriptRow) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.value,
            color = OperatorColors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(10.dp))
                .background(OperatorColors.userBubble)
                .border(
                    1.dp,
                    OperatorColors.userLine,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AssistantMessageRow(row: TranscriptRow) {
    MarkdownText(
        value = row.value,
        color = OperatorColors.textPrimary,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

private const val OPERATOR_UI_PREFS = "operator-ui"
private const val OPERATOR_UI_ACTIVE_THREAD_ID = "active_thread_id"
private const val OPERATOR_UI_ACTIVE_PROJECT_CWD = "active_project_cwd"
private const val OPERATOR_UI_MODEL_OVERRIDE = "model_override"
private const val OPERATOR_UI_REASONING_EFFORT = "reasoning_effort_override"
private const val OPERATOR_UI_FAST_SERVICE_TIER = "fast_service_tier"
private const val OPERATOR_UI_APPROVAL_POLICY = "approval_policy_override"
private const val OPERATOR_UI_APPROVALS_REVIEWER = "approvals_reviewer_override"
private const val OPERATOR_UI_PERMISSION_SELECTION = "permission_selection_override"
private const val OPERATOR_UI_PERMISSION_PROFILE = "permission_profile_override"
private const val OPERATOR_UI_PERSONALITY_OVERRIDE = "personality_override"
private const val OPERATOR_UI_PLAN_MODE = "plan_mode"
private const val OPERATOR_UI_OLED_THEME = "oled_theme"
private const val OPERATOR_UI_STATUS_BAR_ITEMS = "status_bar_items"
private const val MAX_STATUS_BAR_ITEMS = 4
private const val BASELINE_CONTEXT_TOKENS = 12_000L
private const val MARKDOWN_CODE_THEME_KEY = "markdown_code_theme"

private data class MarkdownRenderSettings(
    val codeTheme: MarkdownCodeTheme = MarkdownCodeTheme.Operator,
)

private enum class MarkdownCodeTheme(
    val key: String,
    val label: String,
    val detail: String,
) {
    Operator("operator", "Operator", "Codex dark"),
    Darkula("darkula", "Darkula", "high contrast"),
    Default("default", "Default", "light syntax");

    companion object {
        fun fromKey(value: String?): MarkdownCodeTheme =
            entries.firstOrNull { it.key == value } ?: Operator
    }
}

private val LocalMarkdownRenderSettings = staticCompositionLocalOf { MarkdownRenderSettings() }

@Composable
private fun MarkdownText(
    value: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val context = LocalContext.current
    val renderSettings = LocalMarkdownRenderSettings.current
    val markwon = remember(context, renderSettings) {
        buildOperatorMarkwon(context, renderSettings)
    }
    val segments = remember(value) { splitMarkdownIntoSegments(value) }
    if (
        maxLines == Int.MAX_VALUE &&
        overflow == TextOverflow.Clip &&
        segments.any { it is MarkdownSegment.CodeBlock }
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            segments.forEach { segment ->
                when (segment) {
                    is MarkdownSegment.Markdown -> {
                        if (segment.value.isNotBlank()) {
                            MarkdownTextView(
                                value = segment.value,
                                markwon = markwon,
                                color = color,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    is MarkdownSegment.CodeBlock -> {
                        MarkdownCodeBlock(
                            language = segment.language,
                            code = segment.code,
                            settings = renderSettings,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        return
    }

    MarkdownTextView(
        value = value,
        markwon = markwon,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
private fun MarkdownTextView(
    value: String,
    markwon: Markwon,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val markdown = remember(value) { prepareMarkdownForMarkwon(value) }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = false
                setTextColor(color.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
                setLineSpacing(0f, lineHeight.value / fontSize.value)
                setMaxLines(maxLines)
                ellipsize = overflow.toTextUtilsTruncateAt()
                linksClickable = true
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
            textView.setLineSpacing(0f, lineHeight.value / fontSize.value)
            textView.maxLines = maxLines
            textView.ellipsize = overflow.toTextUtilsTruncateAt()
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Composable
private fun MarkdownCodeBlock(
    language: String?,
    code: String,
    settings: MarkdownRenderSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    val highlighted = remember(code, language, settings.codeTheme) {
        highlightCodeBlock(language, code, settings)
    }
    val background = Color(settings.codeTheme.codeBlockBackgroundColor())
    val foreground = Color(settings.codeTheme.codeBlockTextColor())
    val scrollState = rememberScrollState()

    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language?.takeIf { it.isNotBlank() } ?: "code",
                color = if (settings.codeTheme == MarkdownCodeTheme.Default) Color(0xFF57606A) else OperatorColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (copied) "copied" else "copy",
                color = if (copied) OperatorColors.success else OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (settings.codeTheme == MarkdownCodeTheme.Default) {
                            Color(0xFFEAEFF5).copy(alpha = 0.72f)
                        } else {
                            OperatorColors.deck.copy(alpha = 0.42f)
                        }
                    )
                    .border(1.dp, OperatorColors.line.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                    .clickable {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Operator code block", code))
                        copied = true
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            factory = { viewContext ->
                TextView(viewContext).apply {
                    includeFontPadding = false
                    typeface = Typeface.MONOSPACE
                    setTextColor(foreground.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setLineSpacing(0f, 1.35f)
                    setHorizontallyScrolling(true)
                    setTextIsSelectable(true)
                }
            },
            update = { textView ->
                textView.setTextColor(foreground.toArgb())
                textView.text = highlighted
            },
        )
    }
}

@Composable
private fun EventTimelineRow(
    row: TranscriptRow,
    onInspect: (TranscriptRow) -> Unit,
) {
    var expanded by remember(row) { mutableStateOf(false) }
    val presentation = remember(row) { timelinePresentation(row) }
    val diffFiles = remember(row) { parseUnifiedDiff(diffTextFromTranscriptRow(row)) }
    val metaPills = remember(row, diffFiles) { timelineMetaPills(row, diffFiles) }
    val canOpen = row.detail.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(presentation.radius))
            .background(presentation.surface)
            .border(1.dp, presentation.border, RoundedCornerShape(presentation.radius))
            .clickable {
                if (canOpen) {
                    onInspect(row)
                } else {
                    expanded = !expanded
                }
            }
            .padding(horizontal = 9.dp, vertical = presentation.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(presentation.accent.copy(alpha = 0.72f))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (presentation.marker.isNotBlank()) {
                        TimelineMarkerBadge(
                            marker = presentation.marker,
                            accent = presentation.accent,
                            surface = presentation.markerSurface,
                            border = presentation.border,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = presentation.title,
                            color = presentation.accent,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (presentation.eyebrow.isNotBlank()) {
                            Text(
                                text = presentation.eyebrow,
                                color = OperatorColors.textTertiary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    TimelineMoreButton(label = if (expanded) "less" else "more") {
                        expanded = !expanded
                    }
                }
            }
            if (metaPills.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    metaPills.forEach { pill ->
                        TimelineMetaPill(pill)
                    }
                }
            }
            if (row.kind == TimelineKind.Thinking || row.label.equals("plan", ignoreCase = true)) {
                MarkdownText(
                    value = row.value.ifBlank { "received" },
                    color = presentation.contentColor,
                    fontSize = presentation.summarySize,
                    lineHeight = presentation.summaryLineHeight,
                    maxLines = if (expanded) 12 else presentation.collapsedLines,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = row.value.ifBlank { "received" },
                    color = presentation.contentColor,
                    fontSize = presentation.summarySize,
                    lineHeight = presentation.summaryLineHeight,
                    maxLines = if (expanded) 12 else presentation.collapsedLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (diffFiles.isNotEmpty()) {
                TimelineDiffPreview(
                    files = diffFiles,
                    expanded = expanded,
                )
            }
            if (expanded) {
                Text(
                    text = row.detail.ifBlank { row.value },
                    color = OperatorColors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TimelineMarkerBadge(
    marker: String,
    accent: Color,
    surface: Color,
    border: Color,
) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = marker,
            color = accent,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimelineMoreButton(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = OperatorColors.textTertiary,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clickableNoIndication { onClick() }
            .padding(horizontal = 3.dp, vertical = 2.dp),
    )
}

@Composable
private fun TimelineMetaPill(pill: TimelineMetaPillModel) {
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(OperatorColors.panelRaised.copy(alpha = 0.72f))
            .border(1.dp, OperatorColors.line.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(pill.color),
        )
        Text(
            text = pill.label,
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimelineDiffPreview(
    files: List<ReviewDiffFile>,
    expanded: Boolean,
) {
    val visibleFiles = if (expanded) files.take(5) else files.take(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OperatorColors.panelRaised.copy(alpha = 0.82f))
            .border(1.dp, OperatorColors.lineStrong.copy(alpha = 0.86f), RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DIFF PREVIEW",
                color = OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }
        ReviewDiffStatStrip(
            changedFiles = files.size,
            addedLines = files.sumOf { it.addedLineCount },
            deletedLines = files.sumOf { it.deletedLineCount },
        )
        visibleFiles.forEach { file ->
            TimelineDiffFileSummary(file)
        }
        if (files.size > visibleFiles.size) {
            Text(
                text = "+${files.size - visibleFiles.size} more files",
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TimelineDiffFileSummary(file: ReviewDiffFile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(OperatorColors.deck.copy(alpha = 0.58f))
            .border(1.dp, OperatorColors.line.copy(alpha = 0.72f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileStatusPill(file.status)
        Text(
            text = file.path,
            color = OperatorColors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${file.addedLineCount} -${file.deletedLineCount}",
            color = OperatorColors.textTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private data class TimelinePresentation(
    val title: String,
    val eyebrow: String,
    val marker: String,
    val accent: Color,
    val surface: Color,
    val markerSurface: Color,
    val border: Color,
    val contentColor: Color,
    val radius: Dp,
    val verticalPadding: Dp,
    val summarySize: androidx.compose.ui.unit.TextUnit,
    val summaryLineHeight: androidx.compose.ui.unit.TextUnit,
    val collapsedLines: Int,
)

private data class TimelineMetaPillModel(
    val label: String,
    val color: Color = OperatorColors.textTertiary,
)

private fun timelineMetaPills(
    row: TranscriptRow,
    diffFiles: List<ReviewDiffFile>,
): List<TimelineMetaPillModel> {
    val pills = mutableListOf<TimelineMetaPillModel>()
    if (diffFiles.isNotEmpty()) {
        pills += TimelineMetaPillModel(
            label = when (diffFiles.size) {
                1 -> "1 file"
                else -> "${diffFiles.size} files"
            },
            color = OperatorColors.ok,
        )
        val added = diffFiles.sumOf { it.addedLineCount }
        val deleted = diffFiles.sumOf { it.deletedLineCount }
        if (added > 0) pills += TimelineMetaPillModel("+$added", OperatorColors.success)
        if (deleted > 0) pills += TimelineMetaPillModel("-$deleted", OperatorColors.danger)
    }
    row.value
        .lineSequence()
        .flatMap { line -> line.split(" / ", " | ", ", ").asSequence() }
        .map { it.trim() }
        .filter { it.length in 2..28 }
        .filterNot { it.contains('\n') || it.contains('{') || it.contains("diff --git") }
        .filterNot { value -> pills.any { it.label.equals(value, ignoreCase = true) } }
        .take(3 - pills.size.coerceAtMost(3))
        .forEach { value ->
            pills += TimelineMetaPillModel(value, timelineMetaColor(value))
        }
    return pills.take(4)
}

private fun timelineMetaColor(value: String): Color {
    val lower = value.lowercase()
    return when {
        lower.contains("fail") || lower.contains("error") -> OperatorColors.warn
        lower.contains("deny") || lower.contains("decline") -> OperatorColors.danger
        lower.contains("success") || lower.contains("complete") || lower.contains("appl") -> OperatorColors.success
        lower.contains("running") || lower.contains("progress") || lower.contains("stream") -> OperatorColors.ok
        lower.startsWith("+") -> OperatorColors.success
        lower.startsWith("-") -> OperatorColors.danger
        else -> OperatorColors.textTertiary
    }
}

private fun timelinePresentation(row: TranscriptRow): TimelinePresentation {
    val label = row.label.lowercase()
    return when {
        row.kind == TimelineKind.Thinking || label == "plan" -> TimelinePresentation(
            title = if (label == "plan") "PLAN" else "THINKING",
            eyebrow = if (label == "plan") "working plan" else "reasoning update",
            marker = if (label == "plan") "plan" else "...",
            accent = OperatorColors.textSecondary,
            surface = OperatorColors.deck,
            markerSurface = OperatorColors.panel,
            border = OperatorColors.thinkingLine,
            contentColor = OperatorColors.textSecondary,
            radius = 8.dp,
            verticalPadding = 6.dp,
            summarySize = 12.sp,
            summaryLineHeight = 17.sp,
            collapsedLines = 2,
        )

        row.kind == TimelineKind.Error -> TimelinePresentation(
            title = "ERROR",
            eyebrow = label,
            marker = "!",
            accent = OperatorColors.warn,
            surface = OperatorColors.errorSurface,
            markerSurface = OperatorColors.panelRaised,
            border = OperatorColors.warn,
            contentColor = OperatorColors.textPrimary,
            radius = 10.dp,
            verticalPadding = 8.dp,
            summarySize = 13.sp,
            summaryLineHeight = 18.sp,
            collapsedLines = 3,
        )

        label.contains("command") -> TimelinePresentation(
            title = "COMMAND",
            eyebrow = "shell execution",
            marker = "\$",
            accent = OperatorColors.ok,
            surface = OperatorColors.panel,
            markerSurface = OperatorColors.panelRaised,
            border = OperatorColors.lineStrong,
            contentColor = OperatorColors.textPrimary,
            radius = 10.dp,
            verticalPadding = 8.dp,
            summarySize = 12.sp,
            summaryLineHeight = 17.sp,
            collapsedLines = 2,
        )

        label.contains("file") || label.contains("patch") -> TimelinePresentation(
            title = "FILE CHANGE",
            eyebrow = "workspace update",
            marker = "diff",
            accent = OperatorColors.success,
            surface = OperatorColors.diffSurface,
            markerSurface = OperatorColors.panelRaised,
            border = OperatorColors.success.copy(alpha = 0.42f),
            contentColor = OperatorColors.textPrimary,
            radius = 10.dp,
            verticalPadding = 8.dp,
            summarySize = 12.sp,
            summaryLineHeight = 17.sp,
            collapsedLines = 3,
        )

        label.contains("mcp") || label.contains("tool") || label.contains("agent") -> TimelinePresentation(
            title = row.label.uppercase(),
            eyebrow = "tool call",
            marker = "fn",
            accent = OperatorColors.ok,
            surface = OperatorColors.panel,
            markerSurface = OperatorColors.panelRaised,
            border = OperatorColors.line,
            contentColor = OperatorColors.textSecondary,
            radius = 8.dp,
            verticalPadding = 7.dp,
            summarySize = 12.sp,
            summaryLineHeight = 17.sp,
            collapsedLines = 2,
        )

        label.contains("web") || label.contains("image") -> TimelinePresentation(
            title = row.label.uppercase(),
            eyebrow = "external context",
            marker = "ctx",
            accent = OperatorColors.ok,
            surface = OperatorColors.panel,
            markerSurface = OperatorColors.panelRaised,
            border = OperatorColors.line,
            contentColor = OperatorColors.textSecondary,
            radius = 8.dp,
            verticalPadding = 7.dp,
            summarySize = 12.sp,
            summaryLineHeight = 17.sp,
            collapsedLines = 2,
        )

        row.kind == TimelineKind.Approval -> TimelinePresentation(
            title = "APPROVAL",
            eyebrow = label,
            marker = "ok?",
            accent = OperatorColors.ok,
            surface = OperatorColors.approvalSurface,
            markerSurface = OperatorColors.panelRaised,
            border = OperatorColors.ok,
            contentColor = OperatorColors.textPrimary,
            radius = 10.dp,
            verticalPadding = 8.dp,
            summarySize = 13.sp,
            summaryLineHeight = 18.sp,
            collapsedLines = 2,
        )

        else -> TimelinePresentation(
            title = row.label.uppercase(),
            eyebrow = "",
            marker = "",
            accent = OperatorColors.textTertiary,
            surface = OperatorColors.deck,
            markerSurface = OperatorColors.panel,
            border = OperatorColors.line,
            contentColor = OperatorColors.textSecondary,
            radius = 8.dp,
            verticalPadding = 6.dp,
            summarySize = 12.sp,
            summaryLineHeight = 17.sp,
            collapsedLines = 1,
        )
    }
}

@Composable
private fun ApprovalTimelineCard(
    request: PendingServerRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onInspect: () -> Unit,
    onAnswerInteractiveRequest: (JSONObject, String) -> Unit,
) {
    var expanded by remember(request.requestId) { mutableStateOf(false) }
    val formSpec = remember(request.requestId, request.params?.toString()) {
        interactiveFormSpec(request)
    }
    var formValues by remember(request.requestId, request.params?.toString()) {
        mutableStateOf(formSpec?.initialValues().orEmpty())
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.ok, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${request.cardKind.uppercase()} / ${request.cardSubject.uppercase()}",
                color = OperatorColors.ok,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "open",
                    color = OperatorColors.ok,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onInspect() },
                )
                Text(
                    text = if (expanded) "less" else "details",
                    color = OperatorColors.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }
        Text(
            text = request.summary,
            color = OperatorColors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        request.detail.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                color = OperatorColors.textTertiary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
            )
        }
        if (expanded) {
            Text(
                text = request.expandedDetail
                    .ifBlank { request.detail }
                    .ifBlank { request.summary },
                color = OperatorColors.textTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
            )
        }
        if (formSpec != null) {
            InteractiveRequestForm(
                spec = formSpec,
                values = formValues,
                onValuesChange = { formValues = it },
                onSubmit = { response, summary ->
                    onAnswerInteractiveRequest(response, summary)
                },
                onDecline = onDecline.takeIf { formSpec.allowDecline },
                onCancel = onCancel.takeIf { formSpec.allowCancel },
            )
        } else if (request.canUseQuickActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryActionButton(label = "Accept", onClick = onAccept)
                SecondaryActionButton(label = "Decline", onClick = onDecline)
                SecondaryActionButton(label = "Cancel", onClick = onCancel)
            }
        } else {
            Text(
                text = "requires structured input",
                color = OperatorColors.warn,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun InteractiveRequestForm(
    spec: InteractiveFormSpec,
    values: Map<String, InteractiveFieldValue>,
    onValuesChange: (Map<String, InteractiveFieldValue>) -> Unit,
    onSubmit: (JSONObject, String) -> Unit,
    onDecline: (() -> Unit)?,
    onCancel: (() -> Unit)?,
) {
    val scrollState = rememberScrollState()
    val canSubmit = spec.canSubmit(values)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = spec.title,
                color = OperatorColors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            spec.message.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = OperatorColors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            spec.fields.forEachIndexed { index, field ->
                InteractiveFieldEditor(
                    position = "${index + 1}/${spec.fields.size}",
                    field = field,
                    value = values[field.id] ?: InteractiveFieldValue(),
                    onChange = { nextValue ->
                        onValuesChange(values + (field.id to nextValue))
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryActionButton(
                label = spec.submitLabel,
                enabled = canSubmit,
                onClick = {
                    onSubmit(
                        interactiveFormResponse(spec, values),
                        interactiveFormSummary(spec, values),
                    )
                },
            )
            onDecline?.let { SecondaryActionButton(label = "Decline", onClick = it) }
            onCancel?.let { SecondaryActionButton(label = "Cancel", onClick = it) }
        }
    }
}

@Composable
private fun InteractiveFieldEditor(
    position: String,
    field: InteractiveFieldSpec,
    value: InteractiveFieldValue,
    onChange: (InteractiveFieldValue) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = field.label,
                    color = OperatorColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                field.description.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        color = OperatorColors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
            Text(
                text = listOf(position, if (field.required) "required" else "optional")
                    .joinToString(" / "),
                color = OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        when (field.kind) {
            InteractiveFieldKind.Text,
            InteractiveFieldKind.Secret,
            InteractiveFieldKind.Number,
            InteractiveFieldKind.Integer -> {
                InteractiveTextInput(
                    value = value.values.firstOrNull().orEmpty(),
                    hint = field.inputHint,
                    kind = field.kind,
                    onValueChange = { text ->
                        onChange(value.copy(values = listOf(text)))
                    },
                )
            }

            InteractiveFieldKind.Choice,
            InteractiveFieldKind.MultiChoice,
            InteractiveFieldKind.Boolean -> {
                val options = field.renderOptions()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        val selected = value.values.contains(option.value)
                        InteractiveChoiceRow(
                            option = option,
                            selected = selected,
                            multiSelect = field.kind == InteractiveFieldKind.MultiChoice,
                            onClick = {
                                val nextValues = if (field.kind == InteractiveFieldKind.MultiChoice) {
                                    if (selected) {
                                        value.values - option.value
                                    } else {
                                        value.values + option.value
                                    }
                                } else {
                                    listOf(option.value)
                                }
                                onChange(value.copy(values = nextValues.distinct()))
                            },
                        )
                    }
                }
                if (field.allowsOther && value.values.contains(INTERACTIVE_OTHER_VALUE)) {
                    Text(
                        text = "Add details in notes.",
                        color = OperatorColors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
        val notes = field.notes
        val showNotes = notes != null && when (notes.visibility) {
            InteractiveNotesVisibility.Always -> true
            InteractiveNotesVisibility.WhenAnswered -> value.values.any(String::isNotBlank)
        }
        if (notes != null && showNotes) {
            InteractiveTextInput(
                value = value.noteText,
                hint = notes.hint,
                kind = InteractiveFieldKind.Text,
                onValueChange = { text ->
                    onChange(value.copy(noteText = text))
                },
            )
        }
    }
}

@Composable
private fun InteractiveTextInput(
    value: String,
    hint: String,
    kind: InteractiveFieldKind,
    onValueChange: (String) -> Unit,
) {
    val singleLine = kind != InteractiveFieldKind.Text
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = OperatorColors.textPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(OperatorColors.ok),
        keyboardOptions = KeyboardOptions(keyboardType = kind.keyboardType),
        visualTransformation = if (kind == InteractiveFieldKind.Secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        singleLine = singleLine,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp, max = if (singleLine) 42.dp else 96.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isBlank()) {
                    Text(
                        text = hint,
                        color = OperatorColors.textTertiary,
                        fontSize = 13.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun InteractiveChoiceRow(
    option: InteractiveOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) OperatorColors.userBubble else OperatorColors.panel)
            .border(
                1.dp,
                if (selected) OperatorColors.userLine else OperatorColors.line,
                RoundedCornerShape(7.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = option.label,
                color = OperatorColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            option.description.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = OperatorColors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = when {
                selected && multiSelect -> "On"
                selected -> "Selected"
                multiSelect -> "Add"
                else -> "Choose"
            },
            color = if (selected) OperatorColors.ok else OperatorColors.textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ComposerDock(
    toolInstallState: ToolInstallUiState,
    accountState: AccountPanelState,
    turnState: TurnPanelState,
    prompt: String,
    promptFocusRequester: FocusRequester,
    imageAttachments: List<PendingImageAttachment>,
    imageAttachmentNotice: String?,
    imageAttachmentBusy: Boolean,
    activeTurnId: String?,
    slashInteraction: SlashInteraction?,
    planModeEnabled: Boolean,
    onPromptChange: (String) -> Unit,
    onAttachImages: () -> Unit,
    onRemoveImageAttachment: (String) -> Unit,
    onStartTurn: () -> Unit,
    onSteerTurn: () -> Unit,
    onInterruptTurn: () -> Unit,
    onRunSlashCommand: (String) -> Unit,
    onDismissSlashInteraction: () -> Unit,
    onSelectModel: (String?) -> Unit,
    onSelectReasoningEffort: (String?) -> Unit,
    onToggleExperimental: (String, Boolean) -> Unit,
    onSelectSlashOption: (SlashOptionAction, SlashPickerOption) -> Unit,
    onTogglePlanMode: () -> Unit,
    onStartDeviceLogin: () -> Unit,
    onCancelLogin: (String) -> Unit,
    onRefreshAccount: () -> Unit,
    onOpenLoginUrl: (String) -> Unit,
) {
    val isRunning = turnState == TurnPanelState.Running
    val isInterrupting = turnState == TurnPanelState.Interrupting
    val hasPrompt = prompt.isNotBlank()
    val hasImages = imageAttachments.isNotEmpty()
    val hasDraft = hasPrompt || hasImages
    val isSlashCommand = prompt.trimStart().startsWith("/") && !hasImages
    val actionLabel = when {
        isSlashCommand -> "Run"
        isInterrupting -> "Stopping"
        isRunning && !hasDraft -> "Stop"
        else -> "Send"
    }
    val actionEnabled = when {
        isSlashCommand -> hasPrompt
        isInterrupting -> false
        isRunning -> activeTurnId?.isNotBlank() == true
        else -> hasDraft
    }
    val action = when {
        isSlashCommand -> onStartTurn
        isRunning && !hasDraft -> onInterruptTurn
        isRunning -> onSteerTurn
        else -> onStartTurn
    }
    val actionIcon = when {
        isRunning && !hasDraft -> R.drawable.ic_stop_square
        isInterrupting -> R.drawable.ic_stop_square
        else -> R.drawable.ic_send_arrow_up
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OperatorColors.deck)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            toolInstallState is ToolInstallUiState.Installing -> {
                FloatingComposerStatusBar(
                    text = toolInstallState.detail,
                    attention = false,
                )
            }

            toolInstallState is ToolInstallUiState.Failed -> {
                FloatingComposerStatusBar(
                    text = toolInstallState.message,
                    attention = true,
                )
            }

            accountState == AccountPanelState.Loading -> {
                FloatingComposerStatusBar(
                    text = "Reading account state",
                    attention = false,
                )
            }

            accountState is AccountPanelState.SignedOut -> {
                FloatingComposerStatusBar(
                    text = accountState.detail,
                    actionLabel = "Sign in",
                    onAction = onStartDeviceLogin,
                )
            }

            accountState is AccountPanelState.LoginPending -> {
                LoginComposerCard(
                    accountState = accountState,
                    onCancelLogin = onCancelLogin,
                    onOpenLoginUrl = onOpenLoginUrl,
                )
            }

            accountState is AccountPanelState.Error -> {
                FloatingComposerStatusBar(
                    text = accountState.message,
                    attention = true,
                    actionLabel = "Retry",
                    onAction = onRefreshAccount,
                )
            }

            accountState is AccountPanelState.SignedIn -> {
                val slashSuggestions = slashCommandSuggestions(prompt)
                slashInteraction?.let { interaction ->
                    SlashInteractionPanel(
                        interaction = interaction,
                        onDismiss = onDismissSlashInteraction,
                        onSelectModel = onSelectModel,
                        onSelectReasoningEffort = onSelectReasoningEffort,
                        onToggleExperimental = onToggleExperimental,
                        onSelectOption = onSelectSlashOption,
                    )
                }
                if (slashSuggestions.isNotEmpty()) {
                    SlashCommandPalette(
                        commands = slashSuggestions,
                        onCommand = onRunSlashCommand,
                    )
                }
                FloatingPromptBar(
                    prompt = prompt,
                    promptFocusRequester = promptFocusRequester,
                    imageAttachments = imageAttachments,
                    imageAttachmentNotice = imageAttachmentNotice,
                    imageAttachmentBusy = imageAttachmentBusy,
                    planModeEnabled = planModeEnabled,
                    actionIcon = actionIcon,
                    actionLabel = actionLabel,
                    actionEnabled = actionEnabled,
                    onPromptChange = onPromptChange,
                    onAttachImages = onAttachImages,
                    onRemoveImageAttachment = onRemoveImageAttachment,
                    onTogglePlanMode = onTogglePlanMode,
                    onAction = action,
                )
            }
        }
    }
}

@Composable
private fun FloatingPromptBar(
    prompt: String,
    promptFocusRequester: FocusRequester,
    imageAttachments: List<PendingImageAttachment>,
    imageAttachmentNotice: String?,
    imageAttachmentBusy: Boolean,
    planModeEnabled: Boolean,
    actionIcon: Int,
    actionLabel: String,
    actionEnabled: Boolean,
    onPromptChange: (String) -> Unit,
    onAttachImages: () -> Unit,
    onRemoveImageAttachment: (String) -> Unit,
    onTogglePlanMode: () -> Unit,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(OperatorColors.composerSurface)
            .border(1.dp, OperatorColors.composerLine, RoundedCornerShape(28.dp))
            .padding(start = 7.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (imageAttachments.isNotEmpty() || !imageAttachmentNotice.isNullOrBlank()) {
            AttachmentStrip(
                attachments = imageAttachments,
                notice = imageAttachmentNotice,
                onRemove = onRemoveImageAttachment,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerRoundControlButton(
                iconRes = R.drawable.ic_lucide_plus,
                contentDescription = if (imageAttachmentBusy) "Adding images" else "Attach images",
                enabled = !imageAttachmentBusy,
                onClick = onAttachImages,
            )
            PlanModeToggleChip(
                enabled = planModeEnabled,
                onClick = onTogglePlanMode,
            )
            PromptEditor(
                value = prompt,
                focusRequester = promptFocusRequester,
                enabled = true,
                onValueChange = onPromptChange,
                modifier = Modifier.weight(1f),
            )
            ComposerRoundActionButton(
                iconRes = actionIcon,
                contentDescription = actionLabel,
                enabled = actionEnabled,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<PendingImageAttachment>,
    notice: String?,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 2.dp, end = 6.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEachIndexed { index, attachment ->
            ImageAttachmentChip(
                index = index + 1,
                attachment = attachment,
                onRemove = onRemove,
            )
        }
        if (!notice.isNullOrBlank()) {
            Text(
                text = notice,
                color = OperatorColors.textTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImageAttachmentChip(
    index: Int,
    attachment: PendingImageAttachment,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .widthIn(max = 190.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(OperatorColors.composerControl)
            .border(1.dp, OperatorColors.composerLine, RoundedCornerShape(999.dp))
            .padding(start = 10.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Image $index",
            color = OperatorColors.ok,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            text = attachment.name,
            color = OperatorColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { onRemove(attachment.id) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_minus),
                contentDescription = "Remove ${attachment.name}",
                tint = OperatorColors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun FloatingComposerStatusBar(
    text: String,
    attention: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(OperatorColors.composerSurface)
            .border(1.dp, OperatorColors.composerLine, RoundedCornerShape(28.dp))
            .padding(start = 16.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (attention) OperatorColors.warn else OperatorColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            ComposerTextActionButton(
                label = actionLabel,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun LoginComposerCard(
    accountState: AccountPanelState.LoginPending,
    onCancelLogin: (String) -> Unit,
    onOpenLoginUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OperatorColors.composerSurface)
            .border(1.dp, OperatorColors.composerLine, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = accountState.userCode,
            color = OperatorColors.textPrimary,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = accountState.verificationUrl,
            color = OperatorColors.textTertiary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryActionButton(label = "Open", onClick = { onOpenLoginUrl(accountState.verificationUrl) })
            SecondaryActionButton(label = "Cancel", onClick = { onCancelLogin(accountState.loginId) })
        }
    }
}

@Composable
private fun SessionHistoryDrawer(
    accountState: AccountPanelState,
    threadListState: ThreadListState,
    projectFolders: List<String>,
    threads: List<ThreadSummary>,
    activeThreadId: String?,
    activeProjectCwd: String,
    searchQuery: String,
    notice: String?,
    onNewThread: () -> Unit,
    onNewProject: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onResumeThread: (ThreadSummary) -> Unit,
    onArchiveThread: (String) -> Unit,
    onRefreshAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val projects = remember(threads, projectFolders) {
        sessionProjectsFromSources(threads, projectFolders)
    }
    val projectSections = remember(projects, threads, searchQuery) {
        sessionProjectSections(
            projects = projects,
            threads = threads,
            query = searchQuery,
        )
    }
    val visibleThreadCount = remember(projectSections) {
        projectSections.sumOf { section -> section.threads.size }
    }
    var collapsedProjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    val knownProjectKeys = remember(projectSections) { projectSections.map(SessionProjectSection::key).toSet() }
    LaunchedEffect(knownProjectKeys) {
        collapsedProjects = collapsedProjects.intersect(knownProjectKeys)
    }
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
        drawerContainerColor = OperatorColors.drawerSurface,
        drawerContentColor = OperatorColors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(316.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerCompactButton(
                    label = "New session",
                    onClick = onNewThread,
                    modifier = Modifier.weight(1f),
                )
                DrawerCompactButton(
                    label = "New project",
                    onClick = onNewProject,
                    modifier = Modifier.weight(1f),
                )
            }
            SessionSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
            )
            Text(
                text = sessionListEyebrow(threadListState, visibleThreadCount).uppercase(),
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            notice?.let { value ->
                DrawerNoticeLine(value)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (threadListState) {
                    ThreadListState.Loading -> DrawerStatusLine("loading saved Codex threads")
                    ThreadListState.Ready -> {
                        if (projectSections.isEmpty()) {
                            DrawerStatusLine("no matching Codex projects")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                projectSections.forEach { section ->
                                    item(key = "project-${section.key}") {
                                        val expanded = searchQuery.isNotBlank() ||
                                            section.key !in collapsedProjects
                                        DrawerProjectSectionHeader(
                                            section = section,
                                            expanded = expanded,
                                            onClick = {
                                                collapsedProjects = if (expanded) {
                                                    collapsedProjects + section.key
                                                } else {
                                                    collapsedProjects - section.key
                                                }
                                            },
                                        )
                                    }
                                    val expanded = searchQuery.isNotBlank() ||
                                        section.key !in collapsedProjects
                                    if (expanded) {
                                        if (section.threads.isEmpty()) {
                                            item(key = "empty-${section.key}") {
                                                DrawerProjectEmptyLine()
                                            }
                                        } else {
                                            items(
                                                count = section.threads.size,
                                                key = { index ->
                                                    val thread = section.threads[index]
                                                    "${section.key}-thread-${thread.id.ifBlank { index.toString() }}"
                                                }
                                            ) { index ->
                                                val thread = section.threads[index]
                                                DrawerThreadTreeRow(
                                                    thread = thread,
                                                    active = thread.id == activeThreadId,
                                                    last = index == section.threads.lastIndex,
                                                    onClick = { onResumeThread(thread) },
                                                    onArchive = { onArchiveThread(thread.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is ThreadListState.Error -> DrawerStatusLine(threadListState.message)
                }
            }
            AccountManagementPill(
                accountState = accountState,
                onRefreshAccount = onRefreshAccount,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ProjectCreationOverlay(
    value: String,
    directoryName: String?,
    error: String?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canCreate = directoryName != null && !busy
    val density = LocalDensity.current
    var panelShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        panelShown = true
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (panelShown) 0.94f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "project creator scrim",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (panelShown) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "project creator alpha",
    )
    val panelScale by animateFloatAsState(
        targetValue = if (panelShown) 1f else 0.985f,
        animationSpec = tween(durationMillis = 170),
        label = "project creator scale",
    )
    val panelOffset by animateDpAsState(
        targetValue = if (panelShown) 0.dp else 14.dp,
        animationSpec = tween(durationMillis = 170),
        label = "project creator offset",
    )
    val panelOffsetPx = with(density) { panelOffset.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OperatorColors.deck.copy(alpha = scrimAlpha))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .graphicsLayer {
                    alpha = panelAlpha
                    scaleX = panelScale
                    scaleY = panelScale
                    translationY = panelOffsetPx
                }
                .clip(RoundedCornerShape(24.dp))
                .background(OperatorColors.panel)
                .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "New project",
                    color = OperatorColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "APP-PRIVATE WORKSPACE",
                    color = OperatorColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "Project name",
                    color = OperatorColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = OperatorColors.textPrimary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(OperatorColors.ok),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OperatorColors.panelRaised)
                        .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (value.isBlank()) {
                                Text(
                                    text = "example: API repair",
                                    color = OperatorColors.textTertiary,
                                    fontSize = 15.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            directoryName?.let { name ->
                SettingsLine("folder", name)
            }
            error?.let { message ->
                Text(
                    text = message,
                    color = OperatorColors.danger,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryActionButton(label = "Cancel", enabled = !busy, onClick = onDismiss)
                PrimaryActionButton(
                    label = if (busy) "Creating" else "Create",
                    enabled = canCreate,
                    onClick = onCreate,
                )
            }
        }
    }
}

@Composable
private fun DrawerNoticeLine(value: String) {
    Text(
        text = value,
        color = OperatorColors.textSecondary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
    )
}

@Composable
private fun SessionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = OperatorColors.textPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        ),
        cursorBrush = SolidColor(OperatorColors.ok),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isBlank()) {
                    Text(
                        text = "Search sessions",
                        color = OperatorColors.textTertiary,
                        fontSize = 13.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun DrawerProjectSectionHeader(
    section: SessionProjectSection,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val countLabel = when (section.threadCount) {
        1 -> "1 session"
        else -> "${section.threadCount} sessions"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerProjectDisclosureDot(
            expanded = expanded,
            selected = false,
        )
        Text(
            text = section.name,
            color = OperatorColors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = countLabel,
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DrawerProjectDisclosureDot(
    expanded: Boolean,
    selected: Boolean,
) {
    val dotColor = if (selected) OperatorColors.ok else OperatorColors.textTertiary
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (expanded) Color.Transparent else dotColor)
                .border(1.dp, dotColor, CircleShape)
        )
    }
}

@Composable
private fun DrawerProjectEmptyLine() {
    Text(
        text = "no saved sessions",
        color = OperatorColors.textTertiary,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, top = 2.dp, bottom = 7.dp),
    )
}

@Composable
private fun SessionProjectRail(
    projects: List<SessionProjectSummary>,
    selectedProjectCwd: String?,
    totalThreads: Int,
    onSelectProject: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "PROJECTS",
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SessionProjectChip(
                label = "All",
                detail = totalThreads.toString(),
                selected = selectedProjectCwd == null,
                onClick = { onSelectProject(null) },
            )
            projects.forEach { project ->
                SessionProjectChip(
                    label = project.name,
                    detail = project.threadCount.toString(),
                    selected = selectedProjectCwd == project.cwd,
                    onClick = { onSelectProject(project.cwd) },
                )
            }
        }
    }
}

@Composable
private fun SessionProjectChip(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) OperatorColors.selected else OperatorColors.panel)
            .border(
                1.dp,
                if (selected) OperatorColors.userLine else OperatorColors.line,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = OperatorColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail,
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SettingsScreen(
    accountState: AccountPanelState,
    activeThreadId: String?,
    activeProjectCwd: String,
    appFilesDir: String,
    engineStatus: String,
    runtimeExtensionStatus: String,
    startupServerRequestCount: Int,
    modelOverride: String?,
    reasoningEffortOverride: String?,
    fastServiceTier: Boolean,
    approvalPolicyOverride: String?,
    approvalsReviewerOverride: String?,
    permissionSelectionOverrideJson: String?,
    personalityOverride: String?,
    planModeEnabled: Boolean,
    oledThemeEnabled: Boolean,
    markdownCodeTheme: MarkdownCodeTheme,
    statusBarItems: List<MobileStatusBarItem>,
    statusBarContext: MobileStatusBarContext,
    onClose: () -> Unit,
    onRefreshAccount: () -> Unit,
    onOledThemeChange: (Boolean) -> Unit,
    onMarkdownCodeThemeChange: (MarkdownCodeTheme) -> Unit,
    onToggleStatusBarItem: (MobileStatusBarItem) -> Unit,
    onOpenTerminalCommand: (String) -> Unit,
    onOpenArchivedChats: () -> Unit,
) {
    var selectedPage by remember { mutableStateOf<SettingsPage?>(null) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val settingsScope = rememberCoroutineScope()
    var githubAuthState by remember(appFilesDir) {
        mutableStateOf(readGitHubGitAuthState(appFilesDir))
    }
    var githubAuthNotice by remember { mutableStateOf<String?>(null) }
    var githubAuthBusy by remember { mutableStateOf(false) }

    fun refreshGitHubAuthState(notice: String? = githubAuthNotice) {
        githubAuthState = readGitHubGitAuthState(appFilesDir)
        githubAuthNotice = notice
    }

    fun startGitHubCliAuth() {
        if (!githubAuthState.ghAvailable) {
            githubAuthNotice = "GitHub CLI is not bundled in this build. Set gh.path for the full profile and rebuild."
            return
        }
        onOpenTerminalCommand(githubCliLoginCommand())
    }

    fun testGitHubAuth() {
        githubAuthBusy = true
        githubAuthNotice = "Checking GitHub credentials..."
        settingsScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    testGitHubGitAuth(
                        context = context.applicationContext,
                        appFilesDir = appFilesDir,
                    )
                }.getOrElse { "GitHub check failed: ${it.readableMessage()}" }
            }
            githubAuthBusy = false
            refreshGitHubAuthState(message)
        }
    }

    fun removeGitHubAuth() {
        githubAuthBusy = true
        githubAuthNotice = null
        settingsScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    removeGitHubGitAuth(
                        context = context.applicationContext,
                        appFilesDir = appFilesDir,
                    )
                }.fold(
                    onSuccess = { "GitHub CLI auth removed from the app shell." },
                    onFailure = { "GitHub removal failed: ${it.readableMessage()}" },
                )
            }
            githubAuthBusy = false
            refreshGitHubAuthState(message)
        }
    }

    BackHandler(enabled = selectedPage != null) {
        selectedPage = null
    }
    LaunchedEffect(selectedPage) {
        scrollState.scrollTo(0)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OperatorColors.deck)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = selectedPage?.title ?: "Settings",
                    color = OperatorColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectedPage?.detail ?: "Operator preferences",
                    color = OperatorColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedPage != null) {
                    SettingsHeaderIconButton(
                        iconRes = R.drawable.ic_lucide_arrow_left,
                        contentDescription = "Back",
                        onClick = { selectedPage = null },
                    )
                }
                SettingsHeaderIconButton(
                    iconRes = R.drawable.ic_lucide_x,
                    contentDescription = "Close settings",
                    onClick = onClose,
                )
            }
        }

        if (selectedPage == null) {
            SettingsOverview(
                accountState = accountState,
                activeThreadId = activeThreadId,
                activeProjectCwd = activeProjectCwd,
                githubAuthState = githubAuthState,
                planModeEnabled = planModeEnabled,
                oledThemeEnabled = oledThemeEnabled,
                markdownCodeTheme = markdownCodeTheme,
                runtimeExtensionStatus = runtimeExtensionStatus,
                onSelectPage = { selectedPage = it },
            )
        } else {
            SettingsDetailPage(
                page = selectedPage!!,
                accountState = accountState,
                activeThreadId = activeThreadId,
                activeProjectCwd = activeProjectCwd,
                appFilesDir = appFilesDir,
                engineStatus = engineStatus,
                runtimeExtensionStatus = runtimeExtensionStatus,
                startupServerRequestCount = startupServerRequestCount,
                modelOverride = modelOverride,
                reasoningEffortOverride = reasoningEffortOverride,
                fastServiceTier = fastServiceTier,
                approvalPolicyOverride = approvalPolicyOverride,
                approvalsReviewerOverride = approvalsReviewerOverride,
                permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                personalityOverride = personalityOverride,
                planModeEnabled = planModeEnabled,
                oledThemeEnabled = oledThemeEnabled,
                markdownCodeTheme = markdownCodeTheme,
                statusBarItems = statusBarItems,
                statusBarContext = statusBarContext,
                githubAuthState = githubAuthState,
                githubAuthNotice = githubAuthNotice,
                githubAuthBusy = githubAuthBusy,
                onRefreshAccount = onRefreshAccount,
                onStartGitHubCliAuth = ::startGitHubCliAuth,
                onTestGitHubAuth = ::testGitHubAuth,
                onRemoveGitHubAuth = ::removeGitHubAuth,
                onOledThemeChange = onOledThemeChange,
                onMarkdownCodeThemeChange = onMarkdownCodeThemeChange,
                onToggleStatusBarItem = onToggleStatusBarItem,
                onOpenTerminalCommand = onOpenTerminalCommand,
                onOpenArchivedChats = onOpenArchivedChats,
            )
        }
    }
}

private data class GitHubGitAuthState(
    val configured: Boolean,
    val username: String?,
    val ghAvailable: Boolean,
    val configPath: String,
)

private data class LocalProcessResult(
    val exitCode: Int,
    val output: String,
)

private fun readGitHubGitAuthState(appFilesDir: String): GitHubGitAuthState {
    val gh = File(appFilesDir, "tools/bin/gh")
    val configFile = githubGhHostsFile(appFilesDir)
    val username = readGitHubCliUsername(configFile)
    return GitHubGitAuthState(
        configured = username != null,
        username = username,
        ghAvailable = gh.isFile && gh.canExecute(),
        configPath = configFile.absolutePath,
    )
}

private fun testGitHubGitAuth(
    context: Context,
    appFilesDir: String,
): String {
    val env = githubCliEnvironment(
        context = context,
        appFilesDir = appFilesDir,
    )
    val gh = findExecutableInPath("gh", env["PATH"].orEmpty())
        ?: error("GitHub CLI is not bundled in this build")
    val status = runLocalProcess(
        command = listOf(gh.absolutePath, "auth", "status", "--hostname", "github.com"),
        env = env,
        cwd = File(appFilesDir),
        timeoutMillis = 20_000L,
    )
    if (status.exitCode != 0) {
        error(status.output.trim().ifBlank { "gh auth status failed" }.take(320))
    }
    val setup = runLocalProcess(
        command = listOf(gh.absolutePath, "auth", "setup-git", "--hostname", "github.com"),
        env = env,
        cwd = File(appFilesDir),
        timeoutMillis = 20_000L,
    )
    return if (setup.exitCode == 0) {
        "GitHub CLI auth is configured, and git is using gh as its credential helper."
    } else {
        "GitHub CLI auth works, but setup-git failed (${setup.exitCode}): ${setup.output.trim().take(240)}"
    }
}

private fun removeGitHubGitAuth(
    context: Context,
    appFilesDir: String,
) {
    val env = githubCliEnvironment(
        context = context,
        appFilesDir = appFilesDir,
    )
    val username = readGitHubGitAuthState(appFilesDir).username
    val gh = findExecutableInPath("gh", env["PATH"].orEmpty())
    if (gh != null && username != null) {
        runLocalProcess(
            command = listOf(gh.absolutePath, "auth", "logout", "--hostname", "github.com", "--user", username),
            env = env,
            cwd = File(appFilesDir),
            timeoutMillis = 15_000L,
        )
    }
    githubGhConfigDir(appFilesDir).deleteRecursively()
    removeGitHubCliCredentialHelper(context, appFilesDir)
}

private fun removeGitHubCliCredentialHelper(
    context: Context,
    appFilesDir: String,
) {
    val env = githubCliEnvironment(
        context = context,
        appFilesDir = appFilesDir,
    )
    val git = findExecutableInPath("git", env["PATH"].orEmpty()) ?: return
    runCatching {
        runLocalProcess(
            command = listOf(git.absolutePath, "config", "--global", "--unset-all", "credential.https://github.com.helper"),
            env = env,
            cwd = File(appFilesDir),
            timeoutMillis = 10_000L,
        )
    }
}

private fun githubCliLoginCommand(): String =
    "GH_BROWSER=echo BROWSER=echo gh auth login --web --git-protocol https --hostname github.com --scopes repo,workflow --insecure-storage && gh auth setup-git --hostname github.com"

private fun githubGhConfigDir(appFilesDir: String): File =
    File(appFilesDir, "xdg/config/gh")

private fun githubGhHostsFile(appFilesDir: String): File =
    File(githubGhConfigDir(appFilesDir), "hosts.yml")

private fun githubCliEnvironment(
    context: Context,
    appFilesDir: String,
): MutableMap<String, String> =
    OperatorToolInstaller.runtimeEnvironment(
        context = context,
        appFilesDir = appFilesDir,
    ).toMutableMap().apply {
        this["GH_CONFIG_DIR"] = githubGhConfigDir(appFilesDir).absolutePath
        this["GIT_TERMINAL_PROMPT"] = "0"
        this["PAGER"] = "cat"
        this["GIT_PAGER"] = "cat"
    }

private fun readGitHubCliUsername(configFile: File): String? {
    if (!configFile.isFile) {
        return null
    }
    val lines = configFile.readLines(Charsets.UTF_8)
    val hasGithubHost = lines.any { line -> line.trim() == "github.com:" }
    if (!hasGithubHost) {
        return null
    }
    return lines.firstNotNullOfOrNull { line ->
        line.trim()
            .removePrefix("user:")
            .trim()
            .takeIf { value -> line.trim().startsWith("user:") && value.isNotBlank() }
    } ?: lines.firstOrNull { line -> line.trim().startsWith("oauth_token:") }
        ?.let { "authenticated" }
}

private fun runLocalProcess(
    command: List<String>,
    env: Map<String, String>,
    cwd: File,
    timeoutMillis: Long,
): LocalProcessResult {
    cwd.mkdirs()
    val process = ProcessBuilder(command)
        .directory(cwd)
        .redirectErrorStream(true)
        .apply {
            environment().putAll(env)
        }
        .start()
    val outputBuffer = StringBuffer()
    val reader = Thread {
        runCatching {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (outputBuffer.length < 4096) {
                        outputBuffer.append(line).append('\n')
                    }
                }
            }
        }
    }.apply { start() }
    val exited = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
    if (!exited) {
        process.destroy()
        if (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
        reader.join(500L)
        error("${command.firstOrNull() ?: "command"} timed out")
    }
    reader.join(1_000L)
    return LocalProcessResult(
        exitCode = process.exitValue(),
        output = outputBuffer.toString(),
    )
}

private fun findExecutableInPath(
    name: String,
    path: String,
): File? =
    path.split(':')
        .asSequence()
        .map { entry -> File(if (entry.isBlank()) "." else entry, name) }
        .firstOrNull { candidate -> candidate.isFile && candidate.canExecute() }

@Composable
private fun GitHubGitAuthSettings(
    state: GitHubGitAuthState,
    notice: String?,
    busy: Boolean,
    onStartCliAuth: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val loginCommand = remember { githubCliLoginCommand() }
    SettingsSection(
        title = "GitHub",
        eyebrow = if (state.configured) "configured" else "not set",
    ) {
        SettingsLine("status", state.username?.let { "Signed in as $it" } ?: "Not signed in with GitHub CLI")
        SettingsLine("gh", if (state.ghAvailable) "Bundled" else "Missing from this build")
        SettingsLine("config", state.configPath)

        SettingsActionButton(
            title = "Sign in with gh",
            detail = if (state.ghAvailable) {
                "Open a terminal and run GitHub CLI browser login for the app shell"
            } else {
                "Bundle GitHub CLI with gh.path, then rebuild the full profile"
            },
            iconRes = R.drawable.ic_lucide_external_link,
            iconRotation = 0f,
            onClick = onStartCliAuth,
        )

        Text(
            text = loginCommand,
            color = OperatorColors.textTertiary,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(OperatorColors.panelRaised)
                .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
                .padding(10.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsHeaderIconButton(
                iconRes = R.drawable.ic_lucide_copy,
                contentDescription = "Copy gh login command",
                onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("gh login", loginCommand))
                },
            )
            SettingsHeaderIconButton(
                iconRes = R.drawable.ic_lucide_rotate_ccw,
                contentDescription = "Test GitHub CLI auth",
                enabled = state.ghAvailable && !busy,
                onClick = onTest,
            )
            SettingsHeaderIconButton(
                iconRes = R.drawable.ic_lucide_trash_2,
                contentDescription = "Remove GitHub CLI auth",
                danger = true,
                enabled = state.configured && !busy,
                onClick = onRemove,
            )
        }

        notice?.let { message ->
            Text(
                text = message,
                color = if (message.contains("failed", ignoreCase = true)) OperatorColors.warn else OperatorColors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SettingsInputField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label.uppercase(),
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = TextStyle(
                color = OperatorColors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(OperatorColors.ok),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OperatorColors.panelRaised)
                .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 11.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = OperatorColors.textTertiary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

private enum class SettingsPage(
    val title: String,
    val detail: String,
) {
    Account("Account", "Sign-in and account state"),
    GitHub("GitHub", "Git authentication for the app shell"),
    Codex("Codex", "Model, collaboration, and permissions"),
    Appearance("Appearance", "Status bar and rendering"),
    Workspace("Workspace", "Project folder and chats"),
    Runtime("Runtime", "Local tools and runtime storage"),
    About("About", "Build and diagnostic details"),
}

@Composable
private fun SettingsOverview(
    accountState: AccountPanelState,
    activeThreadId: String?,
    activeProjectCwd: String,
    githubAuthState: GitHubGitAuthState,
    planModeEnabled: Boolean,
    oledThemeEnabled: Boolean,
    markdownCodeTheme: MarkdownCodeTheme,
    runtimeExtensionStatus: String,
    onSelectPage: (SettingsPage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsGroupLabel("General")
        SettingsCategoryRow(
            title = SettingsPage.Account.title,
            detail = accountStateSummary(accountState),
            value = accountState.eyebrow,
            onClick = { onSelectPage(SettingsPage.Account) },
        )
        SettingsCategoryRow(
            title = SettingsPage.GitHub.title,
            detail = "Git HTTPS credentials for shell tools",
            value = if (githubAuthState.configured) "signed in" else "not set",
            onClick = { onSelectPage(SettingsPage.GitHub) },
        )
        SettingsCategoryRow(
            title = SettingsPage.Codex.title,
            detail = if (planModeEnabled) "Plan mode" else "Code mode",
            value = if (planModeEnabled) "plan" else "code",
            onClick = { onSelectPage(SettingsPage.Codex) },
        )
        SettingsCategoryRow(
            title = SettingsPage.Appearance.title,
            detail = "Status bar, code blocks, and rendering",
            value = if (oledThemeEnabled) "OLED" else markdownCodeTheme.label,
            onClick = { onSelectPage(SettingsPage.Appearance) },
        )
        Spacer(modifier = Modifier.height(4.dp))
        SettingsGroupLabel("Workspace")
        SettingsCategoryRow(
            title = SettingsPage.Workspace.title,
            detail = projectNameFromCwd(activeProjectCwd),
            value = activeThreadId?.let(::shortId) ?: "new",
            onClick = { onSelectPage(SettingsPage.Workspace) },
        )
        SettingsCategoryRow(
            title = SettingsPage.Runtime.title,
            detail = operatorLocalRuntimeLabel(),
            value = runtimeExtensionStatus,
            onClick = { onSelectPage(SettingsPage.Runtime) },
        )
        Spacer(modifier = Modifier.height(4.dp))
        SettingsGroupLabel("System")
        SettingsCategoryRow(
            title = SettingsPage.About.title,
            detail = BuildConfig.OPERATOR_DISTRIBUTION_LABEL,
            value = BuildConfig.VERSION_NAME,
            onClick = { onSelectPage(SettingsPage.About) },
        )
    }
}

@Composable
private fun SettingsDetailPage(
    page: SettingsPage,
    accountState: AccountPanelState,
    activeThreadId: String?,
    activeProjectCwd: String,
    appFilesDir: String,
    engineStatus: String,
    runtimeExtensionStatus: String,
    startupServerRequestCount: Int,
    modelOverride: String?,
    reasoningEffortOverride: String?,
    fastServiceTier: Boolean,
    approvalPolicyOverride: String?,
    approvalsReviewerOverride: String?,
    permissionSelectionOverrideJson: String?,
    personalityOverride: String?,
    planModeEnabled: Boolean,
    oledThemeEnabled: Boolean,
    markdownCodeTheme: MarkdownCodeTheme,
    statusBarItems: List<MobileStatusBarItem>,
    statusBarContext: MobileStatusBarContext,
    githubAuthState: GitHubGitAuthState,
    githubAuthNotice: String?,
    githubAuthBusy: Boolean,
    onRefreshAccount: () -> Unit,
    onStartGitHubCliAuth: () -> Unit,
    onTestGitHubAuth: () -> Unit,
    onRemoveGitHubAuth: () -> Unit,
    onOledThemeChange: (Boolean) -> Unit,
    onMarkdownCodeThemeChange: (MarkdownCodeTheme) -> Unit,
    onToggleStatusBarItem: (MobileStatusBarItem) -> Unit,
    onOpenTerminalCommand: (String) -> Unit,
    onOpenArchivedChats: () -> Unit,
) {
    when (page) {
        SettingsPage.Account -> SettingsSection(title = "Account", eyebrow = accountState.eyebrow) {
            SettingsLine("status", accountStateSummary(accountState))
            SettingsActionButton(
                title = "Refresh account",
                detail = "Refresh sign-in state and Codex account metadata",
                iconRes = R.drawable.ic_lucide_rotate_ccw,
                iconRotation = 0f,
                onClick = onRefreshAccount,
            )
        }

        SettingsPage.GitHub -> GitHubGitAuthSettings(
            state = githubAuthState,
            notice = githubAuthNotice,
            busy = githubAuthBusy,
            onStartCliAuth = onStartGitHubCliAuth,
            onTest = onTestGitHubAuth,
            onRemove = onRemoveGitHubAuth,
        )

        SettingsPage.Codex -> SettingsSection(
            title = "Codex Behavior",
            eyebrow = if (planModeEnabled) "plan" else "code",
        ) {
            SettingsLine("mode", if (planModeEnabled) "Plan mode" else "Code mode")
            SettingsLine("model", modelOverride ?: "Config default")
            SettingsLine("thinking", reasoningEffortOverride ?: "Model default")
            SettingsLine("service tier", if (fastServiceTier) "Fast" else "Default")
            SettingsLine(
                "permissions",
                settingsPermissionSummary(
                    approvalPolicyOverride = approvalPolicyOverride,
                    approvalsReviewerOverride = approvalsReviewerOverride,
                    permissionSelectionOverrideJson = permissionSelectionOverrideJson,
                ),
            )
            SettingsLine("personality", settingsPersonalitySummary(personalityOverride))
        }

        SettingsPage.Workspace -> SettingsSection(title = "Workspace and Chats", eyebrow = activeThreadId?.let(::shortId) ?: "new") {
            SettingsLine("project", projectNameFromCwd(activeProjectCwd))
            SettingsLine("active chat", activeThreadId?.let(::shortId) ?: "New chat")
            SettingsLine("folder", activeProjectCwd)
            SettingsActionButton(
                title = "Archived chats",
                detail = "Restore or permanently delete archived sessions",
                onClick = onOpenArchivedChats,
            )
        }

        SettingsPage.Appearance -> SettingsSection(
            title = "Appearance",
            eyebrow = if (oledThemeEnabled) "oled" else markdownCodeTheme.label.lowercase(),
        ) {
            SettingsToggleRow(
                title = "OLED theme",
                detail = "Use true-black surfaces for OLED displays",
                checked = oledThemeEnabled,
                onCheckedChange = onOledThemeChange,
            )
            SettingsLine("theme", if (oledThemeEnabled) "OLED black" else "Codex dark")
            SettingsLine("code blocks", markdownCodeTheme.label)
            SettingsStatusBarPicker(
                selectedItems = statusBarItems,
                statusBarContext = statusBarContext,
                onToggle = onToggleStatusBarItem,
            )
            SettingsSegmentedOptions(
                label = "code theme",
                options = MarkdownCodeTheme.entries,
                selected = markdownCodeTheme,
                onSelect = onMarkdownCodeThemeChange,
            )
        }

        SettingsPage.Runtime -> SettingsSection(title = "Runtime and Tools", eyebrow = BuildConfig.OPERATOR_DISTRIBUTION_LABEL) {
            SettingsLine("distribution", BuildConfig.OPERATOR_DISTRIBUTION_LABEL)
            SettingsLine("local tools", operatorToolProfileLabel())
            SettingsLine("runtime", operatorLocalRuntimeLabel())
            SettingsLine("runtime extension", runtimeExtensionStatus)
            SettingsLine("workspace", "$appFilesDir/workspaces/default")
            SettingsLine("codex home", "$appFilesDir/codex-home")
            SettingsActionButton(
                title = "Run diagnostics",
                detail = "Open terminal with operator-doctor",
                iconRes = R.drawable.ic_lucide_rotate_ccw,
                iconRotation = 0f,
                onClick = { onOpenTerminalCommand("operator-doctor") },
            )
            SettingsActionButton(
                title = "Network diagnostics",
                detail = "Check TLS, Git HTTPS, and runtime network paths",
                iconRes = R.drawable.ic_lucide_external_link,
                iconRotation = 0f,
                onClick = { onOpenTerminalCommand("operator-doctor --network") },
            )
        }

        SettingsPage.About -> SettingsSection(title = "About", eyebrow = if (engineStatus.startsWith("{")) "ready" else "starting") {
            SettingsLine("version", BuildConfig.VERSION_NAME)
            SettingsLine("distribution", BuildConfig.OPERATOR_DISTRIBUTION_LABEL)
            SettingsLine("engine", if (engineStatus.startsWith("{")) "Local embedded runtime" else "Starting")
            SettingsLine("package", BuildConfig.APPLICATION_ID)
            SettingsLine("startup requests", startupServerRequestCount.toString())
        }
    }
}

private fun settingsPermissionSummary(
    approvalPolicyOverride: String?,
    approvalsReviewerOverride: String?,
    permissionSelectionOverrideJson: String?,
): String =
    listOf(
        "approval ${approvalPolicyOverride ?: "config default"}",
        "reviewer ${approvalsReviewerOverride?.let(::approvalReviewerLabel) ?: "config default"}",
        "profile ${permissionSelectionOverrideJson.permissionSelectionId() ?: "workspace"}",
    ).joinToString(" / ")

private fun settingsPersonalitySummary(personalityOverride: String?): String =
    when (personalityOverride) {
        null -> "Config default"
        "none" -> "No personality-specific instruction"
        "pragmatic" -> "Pragmatic engineering style"
        "friendly" -> "Friendly conversational style"
        else -> personalityOverride
    }

@Composable
private fun InspectorScreen(
    content: InspectorContent,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OperatorColors.deck)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = content.title,
                    color = OperatorColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = content.eyebrow,
                    color = OperatorColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HeaderActionButton(label = "Close", onClick = onClose)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OperatorColors.panel)
                .border(1.dp, OperatorColors.line, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = content.summary,
                color = OperatorColors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            if (content.body.isNotBlank() && content.body != content.summary) {
                Text(
                    text = content.body,
                    color = OperatorColors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ReviewDiffStatStrip(
    changedFiles: Int,
    addedLines: Int,
    deletedLines: Int,
    modifier: Modifier = Modifier,
) {
    val totalChangedLines = (addedLines + deletedLines).coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiffMetricPill(
            label = when (changedFiles) {
                1 -> "1 file"
                else -> "$changedFiles files"
            },
            color = OperatorColors.ok,
        )
        DiffMetricPill(label = "+$addedLines", color = OperatorColors.success)
        DiffMetricPill(label = "-$deletedLines", color = OperatorColors.danger)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(OperatorColors.panelRaised)
                .border(1.dp, OperatorColors.line.copy(alpha = 0.66f), RoundedCornerShape(999.dp)),
        ) {
            if (addedLines > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(addedLines.toFloat() / totalChangedLines)
                        .background(OperatorColors.success.copy(alpha = 0.55f))
                )
            }
            if (deletedLines > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(deletedLines.toFloat() / totalChangedLines)
                        .background(OperatorColors.danger.copy(alpha = 0.48f))
                )
            }
            if (addedLines == 0 && deletedLines == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OperatorColors.line.copy(alpha = 0.44f))
                )
            }
        }
    }
}

@Composable
private fun DiffMetricPill(
    label: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            color = OperatorColors.textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun FileStatusPill(status: String) {
    val color = fileStatusColor(status)
    Text(
        text = status.ifBlank { "changed" }.uppercase(),
        color = color,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private fun fileStatusColor(status: String): Color {
    val lower = status.lowercase()
    return when {
        "added" in lower || "new" in lower -> OperatorColors.success
        "deleted" in lower || "removed" in lower -> OperatorColors.danger
        "renamed" in lower || "copied" in lower -> OperatorColors.ok
        else -> OperatorColors.textTertiary
    }
}

@Composable
private fun ReviewInspectorScreen(
    content: ReviewInspectorContent,
    actionBusy: Boolean,
    pendingAction: ReviewGitAction?,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onSelectScope: (ReviewDiffScope) -> Unit,
    onAction: (ReviewGitAction) -> Unit,
    onCancelPendingAction: () -> Unit,
    onSubmitComment: (String, Int?, String, String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val gitActionsAvailable = content.hasGitRepository
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OperatorColors.deck)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Review",
                    color = OperatorColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = listOf(
                        content.scope.label.lowercase(),
                        content.repository?.display ?: "${projectNameFromCwd(content.cwd)} / no git repo",
                        "${content.changedFileCount} files",
                        "+${content.addedLineCount} -${content.deletedLineCount}",
                    ).joinToString(" / "),
                    color = OperatorColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gitActionsAvailable) {
                    SecondaryActionButton(label = "Refresh", enabled = !actionBusy, onClick = onRefresh)
                }
                HeaderActionButton(label = "Close", onClick = onClose)
            }
        }

        if (gitActionsAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ReviewDiffScope.selectable.forEach { scope ->
                    ReviewScopeChip(
                        scope = scope,
                        selected = content.scope == scope,
                        enabled = !actionBusy,
                        onClick = { onSelectScope(scope) },
                    )
                }
            }
        }

        ReviewDiffStatStrip(
            changedFiles = content.changedFileCount,
            addedLines = content.addedLineCount,
            deletedLines = content.deletedLineCount,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(OperatorColors.panel)
                .border(1.dp, OperatorColors.line, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReviewRepositoryLine(content = content)
            Text(
                text = if (gitActionsAvailable || content.scope == ReviewDiffScope.Event) {
                    content.scope.detail
                } else {
                    "Git review actions are unavailable outside a repository."
                },
                color = OperatorColors.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = content.statusSummary,
                color = OperatorColors.textTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
            )
            content.notice?.takeIf(String::isNotBlank)?.let { notice ->
                Text(
                    text = notice,
                    color = if (notice.contains("failed", ignoreCase = true) || notice.contains("error", ignoreCase = true)) {
                        OperatorColors.warn
                    } else {
                        OperatorColors.textSecondary
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            if (pendingAction != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton(
                        label = "Confirm",
                        enabled = !actionBusy,
                        onClick = { onAction(pendingAction) },
                    )
                    SecondaryActionButton(
                        label = "Cancel",
                        enabled = !actionBusy,
                        onClick = onCancelPendingAction,
                    )
                }
            }
        }

        when {
            content.isLoading -> {
                DrawerStatusLine("loading ${content.scope.label.lowercase()} diff")
            }

            content.files.isEmpty() -> {
                DrawerStatusLine(content.notice ?: "no diff rows")
            }

            else -> {
                content.files.forEach { file ->
                    ReviewDiffFileCard(
                        file = file,
                        actionBusy = actionBusy,
                        gitActionsAvailable = gitActionsAvailable,
                        onAction = onAction,
                        onSubmitComment = onSubmitComment,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewRepositoryLine(content: ReviewInspectorContent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (content.hasGitRepository) OperatorColors.ok else OperatorColors.warn),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = content.repository?.display ?: "No Git repository",
                color = OperatorColors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.repository?.root ?: content.cwd,
                color = OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReviewScopeChip(
    scope: ReviewDiffScope,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) OperatorColors.selected else OperatorColors.panel)
            .border(
                1.dp,
                if (selected) OperatorColors.userLine else OperatorColors.line,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = scope.label,
            color = if (selected) OperatorColors.textPrimary else OperatorColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReviewDiffFileCard(
    file: ReviewDiffFile,
    actionBusy: Boolean,
    gitActionsAvailable: Boolean,
    onAction: (ReviewGitAction) -> Unit,
    onSubmitComment: (String, Int?, String, String) -> Unit,
) {
    var expanded by remember(file.path, file.rawPatch) { mutableStateOf(true) }
    var commentDraft by remember(file.path) { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(12.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FileStatusPill(file.status)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = file.path,
                        color = OperatorColors.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!file.oldPath.isNullOrBlank()) {
                        Text(
                            text = "from ${file.oldPath}",
                            color = OperatorColors.textTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DiffMetricPill(
                            label = when (file.hunks.size) {
                                1 -> "1 hunk"
                                else -> "${file.hunks.size} hunks"
                            },
                            color = OperatorColors.textTertiary,
                        )
                        DiffMetricPill(label = "+${file.addedLineCount}", color = OperatorColors.success)
                        DiffMetricPill(label = "-${file.deletedLineCount}", color = OperatorColors.danger)
                    }
                }
            }
            Icon(
                painter = painterResource(
                    if (expanded) {
                        R.drawable.ic_lucide_chevron_up
                    } else {
                        R.drawable.ic_lucide_chevron_down
                    }
                ),
                contentDescription = if (expanded) "Collapse file diff" else "Expand file diff",
                tint = OperatorColors.textTertiary,
                modifier = Modifier
                    .padding(start = 8.dp, top = 1.dp)
                    .size(17.dp),
            )
        }

        if (gitActionsAvailable) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactReviewAction(
                    label = "Stage",
                    enabled = !actionBusy,
                    iconRes = R.drawable.ic_lucide_plus,
                ) {
                    onAction(ReviewGitAction(ReviewGitActionKind.StageFile, file.path))
                }
                CompactReviewAction(
                    label = "Unstage",
                    enabled = !actionBusy,
                    iconRes = R.drawable.ic_lucide_minus,
                ) {
                    onAction(ReviewGitAction(ReviewGitActionKind.UnstageFile, file.path))
                }
                CompactReviewAction(
                    label = "Revert",
                    enabled = !actionBusy,
                    danger = true,
                    iconRes = R.drawable.ic_lucide_rotate_ccw,
                ) {
                    onAction(ReviewGitAction(ReviewGitActionKind.RevertFile, file.path))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            BasicTextField(
                value = commentDraft,
                onValueChange = { commentDraft = it },
                textStyle = TextStyle(
                    color = OperatorColors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                cursorBrush = SolidColor(OperatorColors.ok),
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OperatorColors.deck.copy(alpha = 0.72f))
                    .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (commentDraft.isBlank()) {
                            Text(
                                text = "Add a review comment for this file",
                                color = OperatorColors.textTertiary,
                                fontSize = 13.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactReviewAction(
                    label = "Send comment",
                    enabled = commentDraft.isNotBlank(),
                    onClick = {
                        onSubmitComment(
                            file.path,
                            file.hunks.firstOrNull()?.firstNewLine ?: file.hunks.firstOrNull()?.firstOldLine,
                            commentDraft,
                            file.rawPatch.take(4_000),
                        )
                        commentDraft = ""
                    },
                )
            }
        }

        if (expanded) {
            file.hunks.forEach { hunk ->
                ReviewDiffHunkView(
                    file = file,
                    hunk = hunk,
                    actionBusy = actionBusy,
                    gitActionsAvailable = gitActionsAvailable,
                    onAction = onAction,
                )
            }
            if (file.hunks.isEmpty()) {
                Text(
                    text = "No text hunks for this file.",
                    color = OperatorColors.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ReviewDiffHunkView(
    file: ReviewDiffFile,
    hunk: ReviewDiffHunk,
    actionBusy: Boolean,
    gitActionsAvailable: Boolean,
    onAction: (ReviewGitAction) -> Unit,
) {
    val patch = remember(file.path, hunk.rawLines) { file.patchFor(hunk) }
    val addedLines = remember(hunk.lines) { hunk.lines.count { it.kind == ReviewDiffLineKind.Added } }
    val deletedLines = remember(hunk.lines) { hunk.lines.count { it.kind == ReviewDiffLineKind.Deleted } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(9.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = hunkRangeLabel(hunk),
                    color = OperatorColors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hunk.header,
                    color = OperatorColors.textTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiffMetricPill(label = "+$addedLines", color = OperatorColors.success)
                    DiffMetricPill(label = "-$deletedLines", color = OperatorColors.danger)
                }
            }
            if (gitActionsAvailable) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactReviewAction(
                        label = "Stage",
                        enabled = !actionBusy,
                        iconRes = R.drawable.ic_lucide_plus,
                    ) {
                        onAction(ReviewGitAction(ReviewGitActionKind.StageHunk, file.path, patch))
                    }
                    CompactReviewAction(
                        label = "Unstage",
                        enabled = !actionBusy,
                        iconRes = R.drawable.ic_lucide_minus,
                    ) {
                        onAction(ReviewGitAction(ReviewGitActionKind.UnstageHunk, file.path, patch))
                    }
                    CompactReviewAction(
                        label = "Revert",
                        enabled = !actionBusy,
                        danger = true,
                        iconRes = R.drawable.ic_lucide_rotate_ccw,
                    ) {
                        onAction(ReviewGitAction(ReviewGitActionKind.RevertHunk, file.path, patch))
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(OperatorColors.deck.copy(alpha = 0.52f))
                .border(1.dp, OperatorColors.line.copy(alpha = 0.76f), RoundedCornerShape(7.dp))
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 3.dp),
        ) {
            hunk.lines.forEach { line ->
                ReviewDiffLineRow(line)
            }
        }
    }
}

private fun hunkRangeLabel(hunk: ReviewDiffHunk): String =
    "old ${hunk.firstOldLine ?: hunk.oldStart} -> new ${hunk.firstNewLine ?: hunk.newStart}"

@Composable
private fun CompactReviewAction(
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    iconRes: Int? = null,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> OperatorColors.textTertiary
        danger -> OperatorColors.danger
        else -> OperatorColors.textSecondary
    }
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (danger) OperatorColors.errorSurface.copy(alpha = 0.72f) else OperatorColors.panel)
            .border(
                1.dp,
                if (danger) OperatorColors.danger.copy(alpha = 0.36f) else OperatorColors.line,
                RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = if (iconRes == null) 8.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconRes?.let { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReviewDiffLineRow(line: ReviewDiffLine) {
    val background = when (line.kind) {
        ReviewDiffLineKind.Added -> OperatorColors.diffSurface
        ReviewDiffLineKind.Deleted -> OperatorColors.errorSurface
        ReviewDiffLineKind.Context,
        ReviewDiffLineKind.Note -> OperatorColors.panelRaised
    }
    val marker = when (line.kind) {
        ReviewDiffLineKind.Added -> "+"
        ReviewDiffLineKind.Deleted -> "-"
        ReviewDiffLineKind.Context -> " "
        ReviewDiffLineKind.Note -> "\\"
    }
    Row(
        modifier = Modifier
            .widthIn(min = 760.dp)
            .background(background)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ReviewDiffLineNumber(line.oldNumber)
        ReviewDiffLineNumber(line.newNumber)
        Text(
            text = marker,
            color = when (line.kind) {
                ReviewDiffLineKind.Added -> OperatorColors.success
                ReviewDiffLineKind.Deleted -> OperatorColors.danger
                else -> OperatorColors.textTertiary
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .width(20.dp)
                .padding(top = 2.dp),
        )
        Text(
            text = line.text,
            color = when (line.kind) {
                ReviewDiffLineKind.Added -> OperatorColors.textPrimary
                ReviewDiffLineKind.Deleted -> OperatorColors.textPrimary.copy(alpha = 0.90f)
                ReviewDiffLineKind.Note -> OperatorColors.textTertiary
                ReviewDiffLineKind.Context -> OperatorColors.textSecondary
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun ReviewDiffLineNumber(value: Int?) {
    Text(
        text = value?.toString().orEmpty(),
        color = OperatorColors.textTertiary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        modifier = Modifier
            .width(42.dp)
            .background(OperatorColors.deck.copy(alpha = 0.32f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsGroupLabel(label: String) {
    Text(
        text = label.uppercase(),
        color = OperatorColors.textTertiary,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 1.dp),
    )
}

@Composable
private fun SettingsHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> OperatorColors.textTertiary.copy(alpha = 0.54f)
        danger -> OperatorColors.danger
        else -> OperatorColors.textSecondary
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) OperatorColors.panelRaised else OperatorColors.disabledControl)
            .border(
                1.dp,
                if (danger && enabled) OperatorColors.danger.copy(alpha = 0.52f) else OperatorColors.line,
                RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    detail: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = OperatorColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            color = OperatorColors.textTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 88.dp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_lucide_chevron_down),
            contentDescription = null,
            tint = OperatorColors.textTertiary,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer(rotationZ = -90f),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (checked) OperatorColors.selected else OperatorColors.panelRaised)
            .border(
                1.dp,
                if (checked) OperatorColors.ok else OperatorColors.line,
                RoundedCornerShape(9.dp),
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = OperatorColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) OperatorColors.ok.copy(alpha = 0.32f) else OperatorColors.deck.copy(alpha = 0.58f))
                .border(
                    1.dp,
                    if (checked) OperatorColors.ok else OperatorColors.line,
                    RoundedCornerShape(999.dp),
                )
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) OperatorColors.ok else OperatorColors.textTertiary),
            )
        }
    }
}

@Composable
private fun SettingsActionButton(
    title: String,
    detail: String,
    iconRes: Int = R.drawable.ic_lucide_chevron_down,
    iconRotation: Float = -90f,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = OperatorColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(OperatorColors.deck.copy(alpha = 0.42f))
                .border(1.dp, OperatorColors.line, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = title,
                tint = OperatorColors.ok,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer(rotationZ = iconRotation),
            )
        }
    }
}

@Composable
private fun ArchivedChatsScreen(
    threadListState: ThreadListState,
    threads: List<ThreadSummary>,
    selectedProjectCwd: String?,
    notice: String?,
    pendingDeleteThreadId: String?,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onSelectProject: (String?) -> Unit,
    onRestoreThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
    onCancelDelete: () -> Unit,
) {
    val visibleThreads = remember(threads, selectedProjectCwd) {
        filterThreadsForSessionDrawer(threads, selectedProjectCwd, query = "")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OperatorColors.deck)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Archived chats",
                    color = OperatorColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sessionListEyebrow(threadListState, visibleThreads.size),
                    color = OperatorColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderActionButton(label = "Refresh", onClick = onRefresh)
                HeaderActionButton(label = "Close", onClick = onClose)
            }
        }

        SessionProjectRail(
            projects = sessionProjectsFromThreads(threads),
            selectedProjectCwd = selectedProjectCwd,
            totalThreads = threads.size,
            onSelectProject = onSelectProject,
        )

        notice?.let { DrawerNoticeLine(it) }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (threadListState) {
                ThreadListState.Loading -> DrawerStatusLine("loading archived Codex threads")
                ThreadListState.Ready -> {
                    if (visibleThreads.isEmpty()) {
                        DrawerStatusLine("no archived Codex threads")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                count = visibleThreads.size,
                                key = { index -> visibleThreads[index].id }
                            ) { index ->
                                val thread = visibleThreads[index]
                                ArchivedThreadRow(
                                    thread = thread,
                                    pendingDelete = pendingDeleteThreadId == thread.id,
                                    onRestore = { onRestoreThread(thread.id) },
                                    onDelete = { onDeleteThread(thread.id) },
                                    onCancelDelete = onCancelDelete,
                                )
                            }
                        }
                    }
                }
                is ThreadListState.Error -> DrawerStatusLine(threadListState.message)
            }
        }
    }
}

@Composable
private fun ArchivedThreadRow(
    thread: ThreadSummary,
    pendingDelete: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panel)
            .border(
                1.dp,
                if (pendingDelete) OperatorColors.danger else OperatorColors.line,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = thread.title,
            color = OperatorColors.textPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = thread.detail,
            color = OperatorColors.textTertiary,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = thread.source?.uppercase() ?: "CODEX",
                color = OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pendingDelete) {
                    Text(
                        text = "Cancel",
                        color = OperatorColors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { onCancelDelete() },
                    )
                }
                Text(
                    text = "Restore",
                    color = OperatorColors.ok,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(enabled = thread.id.isNotBlank()) { onRestore() },
                )
                Text(
                    text = if (pendingDelete) "Delete forever" else "Delete",
                    color = OperatorColors.danger,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(enabled = thread.id.isNotBlank()) { onDelete() },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    eyebrow: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = OperatorColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = eyebrow,
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        content()
    }
}

@Composable
private fun SettingsLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label.uppercase(),
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            color = OperatorColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsStatusBarPicker(
    selectedItems: List<MobileStatusBarItem>,
    statusBarContext: MobileStatusBarContext,
    onToggle: (MobileStatusBarItem) -> Unit,
) {
    val selected = selectedItems.take(MAX_STATUS_BAR_ITEMS)
    val orderedItems = selected + MobileStatusBarItem.entries.filterNot { it in selected }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STATUS BAR",
                color = OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${selected.size}/$MAX_STATUS_BAR_ITEMS",
                color = OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = statusBarPreviewLine(selected, statusBarContext),
            color = OperatorColors.textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(OperatorColors.panelRaised)
                .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp),
        )
        orderedItems.forEach { item ->
            val active = item in selected
            val capped = !active && selected.size >= MAX_STATUS_BAR_ITEMS
            StatusBarOptionRow(
                item = item,
                active = active,
                capped = capped,
                order = selected.indexOf(item).takeIf { it >= 0 }?.plus(1),
                onClick = { if (!capped) onToggle(item) },
            )
        }
    }
}

@Composable
private fun StatusBarOptionRow(
    item: MobileStatusBarItem,
    active: Boolean,
    capped: Boolean,
    order: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) OperatorColors.selected else OperatorColors.panelRaised)
            .border(
                1.dp,
                if (active) OperatorColors.ok else OperatorColors.line,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = !capped || active) { onClick() }
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = when {
                active -> "[${order ?: "x"}]"
                capped -> "[ ]"
                else -> "[ ]"
            },
            color = if (active) OperatorColors.ok else OperatorColors.textTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.id,
                color = OperatorColors.textPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (capped) "Remove another item before adding this." else item.description,
                color = if (active) OperatorColors.ok else OperatorColors.textTertiary,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsSegmentedOptions(
    label: String,
    options: List<MarkdownCodeTheme>,
    selected: MarkdownCodeTheme,
    onSelect: (MarkdownCodeTheme) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = label.uppercase(),
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            options.forEach { option ->
                val active = option == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) OperatorColors.selected else OperatorColors.panelRaised)
                        .border(
                            1.dp,
                            if (active) OperatorColors.ok else OperatorColors.line,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = option.label,
                        color = OperatorColors.textPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = option.detail,
                        color = if (active) OperatorColors.ok else OperatorColors.textTertiary,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerThreadTreeRow(
    thread: ThreadSummary,
    active: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        DrawerTreeGuide(last = last)
        DrawerThreadRow(
            thread = thread,
            active = active,
            onClick = onClick,
            onArchive = onArchive,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 2.dp),
            treeStyle = true,
        )
    }
}

@Composable
private fun DrawerTreeGuide(last: Boolean) {
    val lineColor = OperatorColors.lineStrong.copy(alpha = 0.72f)
    val branchY = 19.dp
    Box(
        modifier = Modifier
            .width(23.dp)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .padding(start = 9.dp)
                .width(1.dp)
                .height(branchY)
                .background(lineColor)
        )
        if (!last) {
            Box(
                modifier = Modifier
                    .padding(start = 9.dp, top = branchY)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(lineColor)
            )
        }
        Row(
            modifier = Modifier
                .padding(top = branchY)
                .height(1.dp),
        ) {
            Spacer(modifier = Modifier.width(9.dp))
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(1.dp)
                    .background(lineColor)
            )
        }
    }
}

@Composable
private fun DrawerThreadRow(
    thread: ThreadSummary,
    active: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
    treeStyle: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(8.dp)
    val rowBackground = when {
        active -> OperatorColors.selected
        treeStyle -> Color.Transparent
        else -> OperatorColors.drawerRow
    }
    val rowBorder = when {
        active -> OperatorColors.ok
        treeStyle -> Color.Transparent
        else -> OperatorColors.line
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBackground)
            .border(1.dp, rowBorder, rowShape)
            .clickable(enabled = thread.id.isNotBlank() && !active) { onClick() }
            .padding(horizontal = if (treeStyle) 7.dp else 10.dp, vertical = if (treeStyle) 5.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (treeStyle) 2.dp else 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = thread.title,
                    color = OperatorColors.textPrimary,
                    fontSize = if (treeStyle) 12.sp else 13.sp,
                    lineHeight = if (treeStyle) 15.sp else 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (treeStyle) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = thread.detail,
                    color = OperatorColors.textTertiary,
                    fontSize = if (treeStyle) 9.sp else 10.sp,
                    lineHeight = if (treeStyle) 12.sp else 14.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(enabled = thread.id.isNotBlank()) { menuExpanded = true }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_more_horizontal),
                        contentDescription = "Session actions",
                        tint = OperatorColors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = OperatorColors.panelRaised,
                ) {
                    Text(
                        text = "Archive",
                        color = OperatorColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .clickable {
                            menuExpanded = false
                            onArchive()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerStatusLine(value: String) {
    Text(
        text = value,
        color = OperatorColors.textSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun AccountManagementPill(
    accountState: AccountPanelState,
    onRefreshAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = when (accountState) {
                is AccountPanelState.SignedIn -> accountState.label
                is AccountPanelState.LoginPending -> "login pending"
                is AccountPanelState.SignedOut -> "signed out"
                is AccountPanelState.Error -> "account error"
                AccountPanelState.Loading -> "checking account"
            },
            color = OperatorColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerCompactButton(
                label = "Account",
                onClick = onRefreshAccount,
                modifier = Modifier.weight(1f),
            )
            DrawerCompactButton(
                label = "Settings",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DrawerCompactButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) OperatorColors.panelRaised else OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) OperatorColors.textSecondary else OperatorColors.textTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SlashInteractionPanel(
    interaction: SlashInteraction,
    onDismiss: () -> Unit,
    onSelectModel: (String?) -> Unit,
    onSelectReasoningEffort: (String?) -> Unit,
    onToggleExperimental: (String, Boolean) -> Unit,
    onSelectOption: (SlashOptionAction, SlashPickerOption) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (interaction) {
                    is SlashInteraction.ModelPicker -> "MODEL / THINKING"
                    is SlashInteraction.ExperimentalPicker -> "EXPERIMENTAL FLAGS"
                    is SlashInteraction.OptionPicker -> interaction.title.uppercase()
                },
                color = OperatorColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Close",
                color = OperatorColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 236.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (interaction) {
                is SlashInteraction.ModelPicker -> {
                    val activeModel = interaction.modelFor(interaction.selectedModelId)
                    InteractionSectionLabel("MODEL")
                    ModelOptionRow(
                        label = "Config default",
                        detail = "Use the model from Codex config",
                        selected = interaction.selectedModelId == null,
                        onClick = { onSelectModel(null) },
                    )
                    interaction.models.forEach { model ->
                        ModelOptionRow(
                            label = model.label,
                            detail = listOfNotNull(
                                model.id,
                                model.description.takeIf(String::isNotBlank),
                                if (model.isDefault) "default" else null,
                            ).joinToString(" / "),
                            selected = interaction.selectedModelId == model.id,
                            onClick = { onSelectModel(model.id) },
                        )
                    }
                    InteractionSectionLabel("THINKING MODE")
                    ModelOptionRow(
                        label = "Config default",
                        detail = activeModel?.defaultReasoningEffort
                            ?.let { "Use ${activeModel.label} default: ${reasoningEffortLabel(it)}" }
                            ?: "Use the reasoning effort from Codex config",
                        selected = interaction.selectedReasoningEffort == null,
                        onClick = { onSelectReasoningEffort(null) },
                    )
                    if (activeModel == null || activeModel.reasoningEfforts.isEmpty()) {
                        InteractionEmptyLine("selected model does not advertise thinking modes")
                    } else {
                        activeModel.reasoningEfforts.forEach { effort ->
                            ModelOptionRow(
                                label = effort.label,
                                detail = listOfNotNull(
                                    effort.value,
                                    effort.description.takeIf(String::isNotBlank),
                                    if (effort.value == activeModel.defaultReasoningEffort) "model default" else null,
                                ).joinToString(" / "),
                                selected = interaction.selectedReasoningEffort == effort.value,
                                onClick = { onSelectReasoningEffort(effort.value) },
                            )
                        }
                    }
                }

                is SlashInteraction.ExperimentalPicker -> {
                    interaction.features.forEach { feature ->
                        ExperimentalOptionRow(
                            feature = feature,
                            onToggle = {
                                onToggleExperimental(feature.name, !feature.enabled)
                            },
                        )
                    }
                }

                is SlashInteraction.OptionPicker -> {
                    interaction.detail.takeIf(String::isNotBlank)?.let { detail ->
                        InteractionEmptyLine(detail)
                    }
                    if (interaction.options.isEmpty()) {
                        InteractionEmptyLine("no compatible mobile options are available from the current config requirements")
                    }
                    interaction.options.forEach { option ->
                        ModelOptionRow(
                            label = option.label,
                            detail = option.detail,
                            selected = option.selected,
                            selectedLabel = "Active",
                            actionLabel = "Use",
                            onClick = { onSelectOption(interaction.action, option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractionSectionLabel(label: String) {
    Text(
        text = label,
        color = OperatorColors.textTertiary,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 3.dp),
    )
}

@Composable
private fun InteractionEmptyLine(text: String) {
    Text(
        text = text,
        color = OperatorColors.textTertiary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}

@Composable
private fun ModelOptionRow(
    label: String,
    detail: String,
    selected: Boolean,
    selectedLabel: String = "Active",
    actionLabel: String = "Use",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) OperatorColors.selected else OperatorColors.panelRaised)
            .border(
                1.dp,
                if (selected) OperatorColors.userLine else OperatorColors.line,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                color = OperatorColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (selected) selectedLabel else actionLabel,
            color = if (selected) OperatorColors.ok else OperatorColors.textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ExperimentalOptionRow(
    feature: ExperimentalFeatureOption,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OperatorColors.panelRaised)
            .border(
                1.dp,
                if (feature.enabled) OperatorColors.ok else OperatorColors.line,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = feature.label,
                color = OperatorColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    feature.name,
                    feature.stage.takeIf(String::isNotBlank),
                    feature.description.takeIf(String::isNotBlank),
                ).joinToString(" / "),
                color = OperatorColors.textTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (feature.enabled) "Disable" else "Enable",
            color = if (feature.enabled) OperatorColors.warn else OperatorColors.ok,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(OperatorColors.panelRaised)
                .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(5.dp))
                .clickable { onToggle() }
                .padding(horizontal = 7.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SlashCommandPalette(
    commands: List<SlashCommandSpec>,
    onCommand: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "SLASH COMMANDS",
            color = OperatorColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        commands.forEach { command ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onCommand(command.name) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = command.name,
                        color = OperatorColors.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = command.summary,
                        color = OperatorColors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                Text(
                    text = command.kind,
                    color = OperatorColors.ok,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun PromptEditor(
    value: String,
    focusRequester: FocusRequester,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(
            color = OperatorColors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        cursorBrush = SolidColor(OperatorColors.ok),
        modifier = modifier
            .focusRequester(focusRequester)
            .heightIn(min = 40.dp, max = 128.dp)
            .padding(horizontal = 2.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "Ask Codex",
                        color = OperatorColors.textTertiary,
                        fontSize = 15.sp
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun WorkspaceTranscript(
    turnState: TurnPanelState,
    assistantText: String,
    rows: List<TranscriptRow>,
) {
    if (assistantText.isBlank() && rows.isEmpty() && turnState == TurnPanelState.Idle) {
        return
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = turnState.label,
        color = turnState.color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium
    )

    if (assistantText.isNotBlank()) {
        Spacer(modifier = Modifier.height(9.dp))
        MarkdownText(
            value = assistantText,
            color = OperatorColors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
    }

    if (rows.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            rows.takeLast(6).forEach { row ->
                LedgerLine(label = row.label, value = row.value)
            }
        }
    }
}

@Composable
private fun ServerRequestTray(
    requests: List<PendingServerRequest>,
    onAccept: (PendingServerRequest) -> Unit,
    onDecline: (PendingServerRequest) -> Unit,
    onCancel: (PendingServerRequest) -> Unit,
) {
    Panel(
        title = "Approvals",
        eyebrow = "${requests.size} pending"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            requests.forEach { request ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(OperatorColors.panelRaised)
                        .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(7.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    LedgerLine(label = "request", value = request.shortMethod)
                    request.scope.takeIf(String::isNotBlank)?.let { scope ->
                        LedgerLine(label = "scope", value = scope)
                    }
                    Text(
                        text = request.summary,
                        color = OperatorColors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                    request.detail.takeIf(String::isNotBlank)?.let { detail ->
                        Text(
                            text = detail,
                            color = OperatorColors.textTertiary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                    if (request.canUseQuickActions) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryActionButton(
                                label = "Accept",
                                onClick = { onAccept(request) },
                            )
                            SecondaryActionButton(
                                label = "Decline",
                                onClick = { onDecline(request) },
                            )
                            SecondaryActionButton(
                                label = "Cancel",
                                onClick = { onCancel(request) },
                            )
                        }
                    } else {
                        Text(
                            text = "requires structured input",
                            color = OperatorColors.warn,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentThreadsDock(
    state: ThreadListState,
    threads: List<ThreadSummary>,
    activeThreadId: String?,
    onRefresh: () -> Unit,
    onNewThread: () -> Unit,
    onResumeThread: (String) -> Unit,
) {
    Panel(
        title = "Threads",
        eyebrow = state.eyebrow
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SecondaryActionButton(label = "Refresh", onClick = onRefresh)
            SecondaryActionButton(label = "New", onClick = onNewThread)
        }

        Spacer(modifier = Modifier.height(10.dp))
        when (state) {
            ThreadListState.Loading -> {
                Text(
                    text = if (threads.isEmpty()) "loading saved Codex threads" else "refreshing",
                    color = OperatorColors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            ThreadListState.Ready -> {
                if (threads.isEmpty()) {
                    Text(
                        text = "no saved Codex threads",
                        color = OperatorColors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    ThreadList(
                        threads = threads,
                        activeThreadId = activeThreadId,
                        onResumeThread = onResumeThread,
                    )
                }
            }

            is ThreadListState.Error -> {
                Text(
                    text = state.message,
                    color = OperatorColors.warn,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
                if (threads.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ThreadList(
                        threads = threads,
                        activeThreadId = activeThreadId,
                        onResumeThread = onResumeThread,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadList(
    threads: List<ThreadSummary>,
    activeThreadId: String?,
    onResumeThread: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        threads.forEach { thread ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = thread.title,
                        color = OperatorColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = thread.detail,
                        color = OperatorColors.textTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                SecondaryActionButton(
                    label = if (thread.id == activeThreadId) "Open" else "Resume",
                    enabled = thread.id.isNotBlank() && thread.id != activeThreadId,
                    onClick = { onResumeThread(thread.id) },
                )
            }
        }
    }
}

@Composable
private fun LedgerLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label.uppercase(),
            color = OperatorColors.textTertiary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            color = OperatorColors.textSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OperatorColors.ok,
            contentColor = OperatorColors.deck,
            disabledContainerColor = OperatorColors.disabledControl,
            disabledContentColor = OperatorColors.textTertiary,
        )
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlanModeToggleChip(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) OperatorColors.userBubble else OperatorColors.composerControl)
            .border(
                1.dp,
                if (enabled) OperatorColors.userLine else OperatorColors.composerLine,
                RoundedCornerShape(999.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (enabled) "PLAN" else "CODE",
            color = if (enabled) OperatorColors.ok else OperatorColors.textSecondary,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ComposerRoundActionButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) OperatorColors.composerAction else OperatorColors.disabledControl)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) OperatorColors.deck else OperatorColors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ComposerRoundControlButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) OperatorColors.composerControl else OperatorColors.disabledControl)
            .border(1.dp, OperatorColors.composerLine, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) OperatorColors.textSecondary else OperatorColors.textTertiary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ComposerTextActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(OperatorColors.ok)
            .clickable { onClick() }
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = OperatorColors.deck,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, OperatorColors.lineStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OperatorColors.textSecondary,
            disabledContentColor = OperatorColors.textTertiary,
        )
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CompactStatus(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = OperatorColors.textTertiary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = OperatorColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Panel(
    title: String,
    eyebrow: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.line, RoundedCornerShape(8.dp))
            .padding(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = OperatorColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = eyebrow,
                color = OperatorColors.textTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(13.dp))
        content()
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(OperatorColors.panelRaised)
            .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            color = OperatorColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HeaderMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_menu_compact),
            contentDescription = "Open sessions",
            tint = OperatorColors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun HeaderActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(OperatorColors.panel)
            .border(1.dp, OperatorColors.lineStrong, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = OperatorColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeaderTerminalButton(color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_terminal_status),
            contentDescription = "Open terminal",
            tint = if (enabled) color else OperatorColors.textTertiary,
            modifier = Modifier
                .width(20.dp)
                .height(16.dp),
        )
    }
}

private object OperatorColors {
    var oledEnabled: Boolean = false
    val deck: Color get() = if (oledEnabled) Color(0xFF000000) else Color(0xFF181818)
    val panel: Color get() = if (oledEnabled) Color(0xFF070707) else Color(0xFF1F1F1F)
    val panelRaised: Color get() = if (oledEnabled) Color(0xFF101010) else Color(0xFF262626)
    val line: Color get() = if (oledEnabled) Color(0xFF242424) else Color(0xFF343434)
    val lineStrong: Color get() = if (oledEnabled) Color(0xFF30333B) else Color(0xFF3C4253)
    val disabledControl: Color get() = if (oledEnabled) Color(0xFF151515) else Color(0xFF292B30)
    val composerSurface: Color get() = if (oledEnabled) Color(0xFF070707) else Color(0xFF222222)
    val composerControl: Color get() = if (oledEnabled) Color(0xFF111111) else Color(0xFF2A2A2A)
    val composerAction = Color(0xFFE3E3E3)
    val composerLine: Color get() = if (oledEnabled) Color(0xFF242424) else Color(0xFF383838)
    val drawerSurface: Color get() = if (oledEnabled) Color(0xFF000000) else Color(0xFF1C1C1C)
    val drawerRow: Color get() = if (oledEnabled) Color(0xFF0D0D0D) else Color(0xFF242424)
    val userBubble: Color get() = if (oledEnabled) Color(0xFF071523) else Color(0xFF202A34)
    val userLine = Color(0xFF339CFF)
    val thinkingLine: Color get() = if (oledEnabled) Color(0xFF333844) else Color(0xFF4A4F5C)
    val textPrimary = Color(0xFFF4F4F4)
    val textSecondary = Color(0xFFB7B7B7)
    val textTertiary = Color(0xFF858585)
    val ok = Color(0xFF339CFF)
    val warn = Color(0xFFD9A441)
    val success = Color(0xFF8ACB9B)
    val danger = Color(0xFFFF7A70)
    val selected: Color get() = if (oledEnabled) Color(0xFF111820) else Color(0xFF2B3038)
    val diffSurface: Color get() = if (oledEnabled) Color(0xFF061208) else Color(0xFF132217)
    val approvalSurface: Color get() = if (oledEnabled) Color(0xFF071321) else Color(0xFF172232)
    val errorSurface: Color get() = if (oledEnabled) Color(0xFF1A1008) else Color(0xFF2A1F14)
}

private fun eventColor(kind: TimelineKind): Color = when (kind) {
    TimelineKind.User -> OperatorColors.userLine
    TimelineKind.Assistant -> OperatorColors.textSecondary
    TimelineKind.Tool -> OperatorColors.ok
    TimelineKind.Thinking -> OperatorColors.textTertiary
    TimelineKind.Approval -> OperatorColors.ok
    TimelineKind.Status -> OperatorColors.textTertiary
    TimelineKind.Mode -> OperatorColors.textTertiary
    TimelineKind.System -> OperatorColors.textTertiary
    TimelineKind.Error -> OperatorColors.warn
}

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

private fun buildOperatorMarkwon(
    context: Context,
    settings: MarkdownRenderSettings,
): Markwon {
    val tableBorder = OperatorColors.lineStrong.toArgb()
    val tableHeader = OperatorColors.panelRaised.toArgb()
    val tableOdd = OperatorColors.panel.toArgb()
    val tableEven = OperatorColors.deck.toArgb()
    val tableRadius = context.dpToPx(8).toFloat()
    val inlineCodeRadius = context.dpToPx(4).toFloat()
    val inlineCodeHorizontalPadding = context.dpToPx(4).toFloat()
    val inlineCodeVerticalPadding = context.dpToPx(2).toFloat()
    val prism4j = Prism4j(OperatorGrammarLocator())
    val prismTheme = settings.codeTheme.toPrismTheme()

    return Markwon.builder(context)
        .usePlugin(SyntaxHighlightPlugin.create(prism4j, prismTheme, "clike"))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(
            TablePlugin.create(
                TableTheme.buildWithDefaults(context)
                    .tableBorderColor(android.graphics.Color.TRANSPARENT)
                    .tableBorderWidth(0)
                    .tableHeaderRowBackgroundColor(android.graphics.Color.TRANSPARENT)
                    .tableOddRowBackgroundColor(android.graphics.Color.TRANSPARENT)
                    .tableEvenRowBackgroundColor(android.graphics.Color.TRANSPARENT)
                    .tableCellPadding(context.dpToPx(10))
                    .build()
            )
        )
        .usePlugin(HtmlPlugin.create())
        .usePlugin(ImagesPlugin.create())
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
        .usePlugin(
            object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(OperatorColors.ok.toArgb())
                        .codeTextColor(OperatorColors.textPrimary.toArgb())
                        .codeBlockTextColor(settings.codeTheme.codeBlockTextColor())
                        .codeBackgroundColor(OperatorColors.panelRaised.toArgb())
                        .codeBlockBackgroundColor(settings.codeTheme.codeBlockBackgroundColor())
                        .codeBlockMargin(context.dpToPx(10))
                        .blockQuoteColor(OperatorColors.thinkingLine.toArgb())
                        .headingBreakColor(OperatorColors.lineStrong.toArgb())
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.imageSizeResolver(OperatorImageSizeResolver())
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(Code::class.java) { configuration, _ ->
                        RoundedInlineCodeSpan(
                            theme = configuration.theme(),
                            backgroundColor = OperatorColors.panelRaised.toArgb(),
                            horizontalPadding = inlineCodeHorizontalPadding,
                            verticalPadding = inlineCodeVerticalPadding,
                            radius = inlineCodeRadius,
                        )
                    }
                }

                override fun beforeSetText(textView: TextView, markdown: Spanned) {
                    if (markdown is Spannable) {
                        installRoundedTableDecorations(
                            markdown = markdown,
                            radius = tableRadius,
                            borderColor = tableBorder,
                            headerColor = tableHeader,
                            oddColor = tableOdd,
                            evenColor = tableEven,
                        )
                    }
                }
            }
        )
        .build()
}

private class RoundedInlineCodeSpan(
    private val theme: MarkwonTheme,
    private val backgroundColor: Int,
    private val horizontalPadding: Float,
    private val verticalPadding: Float,
    private val radius: Float,
) : ReplacementSpan() {
    private val rect = RectF()
    private val textBounds = Rect()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val state = paint.saveOperatorTextState()
        theme.applyCodeTextStyle(paint)
        val width = paint.measureText(text, start, end) + (horizontalPadding * 2f)
        fm?.let {
            val codeMetrics = paint.fontMetricsInt
            it.ascent = codeMetrics.ascent
            it.descent = codeMetrics.descent
            it.top = codeMetrics.top
            it.bottom = codeMetrics.bottom
        }
        paint.restoreOperatorTextState(state)
        return width.roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val state = paint.saveOperatorTextState()
        theme.applyCodeTextStyle(paint)
        val textColor = paint.color
        val codeText = text.subSequence(start, end).toString()
        paint.getTextBounds(codeText, 0, codeText.length, textBounds)
        val textWidth = paint.measureText(text, start, end)
        rect.set(
            x,
            y + textBounds.top - verticalPadding,
            x + textWidth + (horizontalPadding * 2f),
            y + textBounds.bottom + verticalPadding,
        )
        paint.style = Paint.Style.FILL
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x + horizontalPadding, y.toFloat(), paint)
        paint.restoreOperatorTextState(state)
    }
}

private data class OperatorTextPaintState(
    val color: Int,
    val style: Paint.Style,
    val strokeWidth: Float,
    val typeface: Typeface?,
    val textSize: Float,
    val isFakeBoldText: Boolean,
)

private fun Paint.saveOperatorTextState(): OperatorTextPaintState =
    OperatorTextPaintState(
        color = color,
        style = style,
        strokeWidth = strokeWidth,
        typeface = typeface,
        textSize = textSize,
        isFakeBoldText = isFakeBoldText,
    )

private fun Paint.restoreOperatorTextState(state: OperatorTextPaintState) {
    color = state.color
    style = state.style
    strokeWidth = state.strokeWidth
    typeface = state.typeface
    textSize = state.textSize
    isFakeBoldText = state.isFakeBoldText
}

private class OperatorGrammarLocator : GrammarLocator {
    private val generated = GeneratedPrismGrammarLocator()
    private val aliases = mapOf(
        "bash" to "clike",
        "console" to "clike",
        "kt" to "kotlin",
        "rs" to "clike",
        "rust" to "clike",
        "shell" to "clike",
        "sh" to "clike",
        "toml" to "clike",
        "ts" to "javascript",
        "tsx" to "javascript",
        "typescript" to "javascript",
        "zsh" to "clike",
    )

    override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? {
        val normalized = language.substringBefore(' ').trim().lowercase()
        if (normalized.isBlank()) {
            return null
        }
        return generated.grammar(prism4j, normalized)
            ?: aliases[normalized]?.let { generated.grammar(prism4j, it) }
    }

    override fun languages(): Set<String> =
        generated.languages() + aliases.keys
}

private fun MarkdownCodeTheme.toPrismTheme(): Prism4jTheme =
    when (this) {
        MarkdownCodeTheme.Operator -> OperatorPrismTheme()
        MarkdownCodeTheme.Darkula -> Prism4jThemeDarkula.create()
        MarkdownCodeTheme.Default -> Prism4jThemeDefault.create()
    }

private fun MarkdownCodeTheme.codeBlockBackgroundColor(): Int =
    when (this) {
        MarkdownCodeTheme.Operator -> OperatorColors.panelRaised.toArgb()
        MarkdownCodeTheme.Darkula -> 0xFF2B2B2B.toInt()
        MarkdownCodeTheme.Default -> 0xFFF6F8FA.toInt()
    }

private fun MarkdownCodeTheme.codeBlockTextColor(): Int =
    when (this) {
        MarkdownCodeTheme.Operator -> OperatorColors.textPrimary.toArgb()
        MarkdownCodeTheme.Darkula -> 0xFFA9B7C6.toInt()
        MarkdownCodeTheme.Default -> 0xFF24292F.toInt()
    }

private class OperatorPrismTheme : Prism4jTheme {
    override fun background(): Int = OperatorColors.panelRaised.toArgb()

    override fun textColor(): Int = OperatorColors.textPrimary.toArgb()

    override fun apply(
        language: String,
        syntax: Prism4j.Syntax,
        builder: SpannableStringBuilder,
        start: Int,
        end: Int,
    ) {
        builder.setSpan(
            ForegroundColorSpan(syntax.type().operatorTokenColor()),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (syntax.type() == "keyword" || syntax.type() == "important") {
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun String.operatorTokenColor(): Int =
        when (this) {
            "annotation",
            "atrule",
            "builtin",
            "class-name",
            "function",
            "namespace",
            "tag" -> OperatorColors.ok.toArgb()
            "attr-name",
            "property",
            "selector",
            "symbol" -> 0xFFA7C7FF.toInt()
            "attr-value",
            "char",
            "regex",
            "string" -> 0xFFBEE6A7.toInt()
            "boolean",
            "constant",
            "number" -> 0xFFFFC47D.toInt()
            "comment",
            "prolog" -> OperatorColors.textTertiary.toArgb()
            "deleted" -> OperatorColors.danger.toArgb()
            "inserted" -> OperatorColors.success.toArgb()
            "keyword",
            "important" -> 0xFFFFD166.toInt()
            "operator" -> OperatorColors.textSecondary.toArgb()
            "punctuation" -> OperatorColors.textTertiary.toArgb()
            else -> OperatorColors.textPrimary.toArgb()
        }
}

private fun highlightCodeBlock(
    language: String?,
    code: String,
    settings: MarkdownRenderSettings,
): CharSequence =
    Prism4jSyntaxHighlight
        .create(Prism4j(OperatorGrammarLocator()), settings.codeTheme.toPrismTheme(), "clike")
        .highlight(language?.takeIf { it.isNotBlank() }, code)

private class OperatorImageSizeResolver : ImageSizeResolverDef() {
    override fun resolveImageSize(drawable: AsyncDrawable): Rect {
        val resolved = super.resolveImageSize(drawable)
        val maxWidth = drawable.lastKnownCanvasWidth
        if (maxWidth <= 0 || resolved.width() <= maxWidth) {
            return resolved
        }
        val ratio = resolved.height().toFloat() / resolved.width().toFloat()
        val height = (maxWidth * ratio).roundToInt().coerceAtLeast(1)
        return Rect(0, 0, maxWidth, height)
    }
}

private fun installRoundedTableDecorations(
    markdown: Spannable,
    radius: Float,
    borderColor: Int,
    headerColor: Int,
    oddColor: Int,
    evenColor: Int,
) {
    markdown.getSpans(0, markdown.length, RoundedTableRowDecorationSpan::class.java)
        .forEach(markdown::removeSpan)
    val tableSpans = markdown.getSpans(0, markdown.length, TableSpan::class.java)
    tableSpans.forEach { tableSpan ->
        val tableStart = markdown.getSpanStart(tableSpan)
        val tableEnd = markdown.getSpanEnd(tableSpan)
        val rows = markdown.getSpans(tableStart, tableEnd, TableRowSpan::class.java)
            .sortedBy { markdown.getSpanStart(it) }
        rows.forEachIndexed { index, row ->
            val start = markdown.getSpanStart(row)
            val end = markdown.getSpanEnd(row)
            val background = when {
                index == 0 -> headerColor
                index % 2 == 1 -> oddColor
                else -> evenColor
            }
            markdown.setSpan(
                RoundedTableRowDecorationSpan(
                    rowIndex = index,
                    rowCount = rows.size,
                    radius = radius,
                    backgroundColor = background,
                    borderColor = borderColor,
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

private class RoundedTableRowDecorationSpan(
    private val rowIndex: Int,
    private val rowCount: Int,
    private val radius: Float,
    private val backgroundColor: Int,
    private val borderColor: Int,
) : LineBackgroundSpan {
    private val rect = RectF()

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val oldStyle = paint.style
        val oldColor = paint.color
        val oldStrokeWidth = paint.strokeWidth
        val inset = 1f
        rect.set(left + inset, top.toFloat(), right - inset, bottom.toFloat())
        paint.style = Paint.Style.FILL
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = borderColor
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = oldStyle
        paint.color = oldColor
        paint.strokeWidth = oldStrokeWidth
    }
}

private sealed class MarkdownSegment {
    data class Markdown(val value: String) : MarkdownSegment()
    data class CodeBlock(val language: String?, val code: String) : MarkdownSegment()
}

private fun splitMarkdownIntoSegments(source: String): List<MarkdownSegment> {
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')
    val segments = mutableListOf<MarkdownSegment>()
    val text = mutableListOf<String>()
    var index = 0

    fun flushText() {
        if (text.isNotEmpty()) {
            segments += MarkdownSegment.Markdown(text.joinToString("\n"))
            text.clear()
        }
    }

    while (index < lines.size) {
        val fence = parseFenceStart(lines[index])
        if (fence == null) {
            text += lines[index]
            index += 1
            continue
        }

        flushText()
        val code = mutableListOf<String>()
        index += 1
        while (index < lines.size && !isFenceClose(lines[index], fence)) {
            code += lines[index]
            index += 1
        }
        if (index < lines.size) {
            index += 1
        }
        segments += MarkdownSegment.CodeBlock(
            language = fence.info.substringBefore(' ').trim().takeIf { it.isNotBlank() },
            code = code.joinToString("\n"),
        )
    }

    flushText()
    return segments.ifEmpty { listOf(MarkdownSegment.Markdown(normalized)) }
}

private data class MarkdownFence(
    val marker: Char,
    val length: Int,
    val info: String,
)

private fun parseFenceStart(line: String): MarkdownFence? {
    val trimmed = line.trimStart()
    val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    val length = trimmed.takeWhile { it == marker }.length
    if (length < 3) {
        return null
    }
    return MarkdownFence(
        marker = marker,
        length = length,
        info = trimmed.drop(length).trim(),
    )
}

private fun isFenceClose(line: String, fence: MarkdownFence): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith(fence.marker.toString().repeat(fence.length))) {
        return false
    }
    return trimmed.dropWhile { it == fence.marker }.isBlank()
}

private fun prepareMarkdownForMarkwon(source: String): String {
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')
    val output = mutableListOf<String>()
    var index = 0
    var inFence = false
    while (index < lines.size) {
        val line = lines[index]
        if (isFenceLine(line)) {
            inFence = !inFence
            output += line
            index += 1
            continue
        }
        if (!inFence && index + 1 < lines.size && isMarkdownTableHeader(lines[index], lines[index + 1])) {
            if (output.lastOrNull()?.isNotBlank() == true) {
                output += ""
            }
            output += lines[index]
            output += normalizedTableDivider(lines[index + 1])
            index += 2
            while (index < lines.size && isMarkdownTableRow(lines[index])) {
                output += lines[index]
                index += 1
            }
            if (index < lines.size && lines[index].isNotBlank()) {
                output += ""
            }
            continue
        }
        output += line
        index += 1
    }
    return output.joinToString("\n")
}

private fun isFenceLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("```") || trimmed.startsWith("~~~")
}

private fun isMarkdownTableHeader(header: String, divider: String): Boolean {
    val headerCells = splitMarkdownTableCells(header)
    val dividerCells = splitMarkdownTableCells(divider)
    return headerCells.size >= 2 &&
        dividerCells.size >= 2 &&
        headerCells.size >= dividerCells.size &&
        dividerCells.all(::isMarkdownTableDividerCell)
}

private fun isMarkdownTableRow(line: String): Boolean =
    splitMarkdownTableCells(line).size >= 2 && !splitMarkdownTableCells(line).all(::isMarkdownTableDividerCell)

private fun normalizedTableDivider(line: String): String {
    val trimmed = line.trim()
    val leading = trimmed.startsWith("|")
    val trailing = trimmed.endsWith("|")
    val normalized = splitMarkdownTableCells(line)
        .joinToString(" | ") { cell ->
            val compact = cell.trim().replace(" ", "")
            val left = compact.startsWith(":")
            val right = compact.endsWith(":")
            "${if (left) ":" else ""}---${if (right) ":" else ""}"
        }
    return "${if (leading) "| " else ""}$normalized${if (trailing) " |" else ""}"
}

private fun isMarkdownTableDividerCell(cell: String): Boolean {
    val compact = cell.trim().replace(" ", "")
    return compact.length >= 3 && compact.matches(Regex(":?-{3,}:?"))
}

private fun splitMarkdownTableCells(line: String): List<String> {
    if (unescapedPipeCount(line) == 0) {
        return emptyList()
    }
    val cells = mutableListOf<String>()
    val builder = StringBuilder()
    var escaped = false
    line.trim().forEach { char ->
        if (char == '|' && !escaped) {
            cells += builder.toString()
            builder.clear()
        } else {
            builder.append(char)
        }
        escaped = char == '\\' && !escaped
    }
    cells += builder.toString()
    return cells
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .map { it.trim() }
}

private fun unescapedPipeCount(line: String): Int {
    var count = 0
    var escaped = false
    line.forEach { char ->
        if (char == '|' && !escaped) {
            count += 1
        }
        escaped = char == '\\' && !escaped
    }
    return count
}

private fun Context.dpToPx(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

private fun TextOverflow.toTextUtilsTruncateAt(): TextUtils.TruncateAt? =
    when (this) {
        TextOverflow.Clip,
        TextOverflow.Visible -> null
        TextOverflow.Ellipsis -> TextUtils.TruncateAt.END
        else -> TextUtils.TruncateAt.END
    }

private sealed class AccountPanelState {
    abstract val eyebrow: String

    data object Loading : AccountPanelState() {
        override val eyebrow = "account/read"
    }

    data class SignedOut(
        val detail: String,
    ) : AccountPanelState() {
        override val eyebrow = "no auth"
    }

    data class SignedIn(
        val label: String,
        val detail: String,
    ) : AccountPanelState() {
        override val eyebrow = "authenticated"
    }

    data class LoginPending(
        val loginId: String,
        val verificationUrl: String,
        val userCode: String,
    ) : AccountPanelState() {
        override val eyebrow = "device code"
    }

    data class Error(
        val message: String,
    ) : AccountPanelState() {
        override val eyebrow = "error"
    }
}

private sealed class TurnPanelState {
    abstract val label: String
    abstract val color: Color

    data object Idle : TurnPanelState() {
        override val label = "idle"
        override val color = OperatorColors.textTertiary
    }

    data object Starting : TurnPanelState() {
        override val label = "starting turn"
        override val color = OperatorColors.warn
    }

    data object Running : TurnPanelState() {
        override val label = "turn running"
        override val color = OperatorColors.ok
    }

    data object Interrupting : TurnPanelState() {
        override val label = "interrupting turn"
        override val color = OperatorColors.warn
    }

    data class Completed(
        private val status: String,
    ) : TurnPanelState() {
        override val label = "turn $status"
        override val color = OperatorColors.textSecondary
    }

    data class Error(
        private val message: String,
    ) : TurnPanelState() {
        override val label = "turn error: $message"
        override val color = OperatorColors.warn
    }
}

private val TurnPanelState.headerPillLabel: String
    get() = when (this) {
        TurnPanelState.Idle -> "IDLE"
        TurnPanelState.Starting -> "STARTING"
        TurnPanelState.Running -> "RUNNING"
        TurnPanelState.Interrupting -> "STOPPING"
        is TurnPanelState.Completed -> label.removePrefix("turn ").uppercase()
        is TurnPanelState.Error -> "ERROR"
    }

private val TurnPanelState.isActiveTurn: Boolean
    get() = when (this) {
        TurnPanelState.Starting,
        TurnPanelState.Running,
        TurnPanelState.Interrupting -> true
        TurnPanelState.Idle,
        is TurnPanelState.Completed,
        is TurnPanelState.Error -> false
    }

private data class TranscriptRow(
    val label: String,
    val value: String,
    val detail: String = "",
    val kind: TimelineKind = TimelineKind.System,
)

private data class InspectorContent(
    val title: String,
    val eyebrow: String,
    val summary: String,
    val body: String,
)

private fun TranscriptRow.toInspectorContent(): InspectorContent =
    InspectorContent(
        title = label.ifBlank { kind.name.lowercase() },
        eyebrow = kind.name.lowercase(),
        summary = value.ifBlank { "received" },
        body = detail.ifBlank { value },
    )

private fun TranscriptRow.toReviewInspectorContent(cwd: String): ReviewInspectorContent? {
    if (!label.contains("file", ignoreCase = true) && !label.contains("patch", ignoreCase = true)) {
        return null
    }
    val diff = diffTextFromTranscriptRow(this)
    if (diff.isBlank()) {
        return null
    }
    return reviewContentFromRawDiff(
        scope = ReviewDiffScope.Event,
        cwd = cwd,
        statusSummary = "Codex ${label.ifBlank { "file" }} event",
        diffText = diff,
        notice = "Opened from transcript event.",
    )
}

private fun diffTextFromTranscriptRow(row: TranscriptRow): String {
    val sources = listOf(row.detail, row.value).filter(String::isNotBlank)
    val jsonDiffs = sources
        .flatMap(::diffTextsFromJsonValue)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (jsonDiffs.isNotEmpty()) {
        return jsonDiffs.joinToString("\n\n")
    }
    return sources
        .map { source -> extractUnifiedDiff(source.normalizedEscapedDiffText()) }
        .firstOrNull(String::isNotBlank)
        .orEmpty()
}

private fun diffTextsFromJsonValue(value: String): List<String> {
    val trimmed = value.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        return emptyList()
    }
    return runCatching {
        val diffs = mutableListOf<String>()
        val parsed: Any = if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONArray(trimmed)
        collectDiffTexts(parsed, diffs)
        diffs
    }.getOrDefault(emptyList())
}

private fun collectDiffTexts(value: Any?, diffs: MutableList<String>) {
    when (value) {
        null, JSONObject.NULL -> Unit
        is JSONObject -> {
            val keys = value.keys()
            while (keys.hasNext()) {
                collectDiffTexts(value.opt(keys.next()), diffs)
            }
        }
        is JSONArray -> {
            for (index in 0 until value.length()) {
                collectDiffTexts(value.opt(index), diffs)
            }
        }
        is String -> {
            extractUnifiedDiff(value.normalizedEscapedDiffText())
                .takeIf(String::isNotBlank)
                ?.let { diffs += it }
        }
    }
}

private fun PendingServerRequest.toInspectorContent(): InspectorContent =
    InspectorContent(
        title = "${cardKind} / $cardSubject",
        eyebrow = "request ${shortId(requestId)}",
        summary = listOf(summary, detail, expandedDetail)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n\n")
            .ifBlank { method },
        body = listOfNotNull(
            scope.takeIf(String::isNotBlank)?.let { "scope: $it" },
            params?.toString(2),
        ).joinToString("\n\n").ifBlank { requestId },
    )

private enum class TimelineKind {
    User,
    Assistant,
    Tool,
    Thinking,
    Approval,
    Status,
    Mode,
    System,
    Error,
}

private data class CommandExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

private enum class ReviewDiffScope(
    val label: String,
    val detail: String,
    val gitCommand: List<String>,
) {
    Head(
        label = "Changes",
        detail = "tracked changes against HEAD",
        gitCommand = listOf("git", "diff", "--no-ext-diff", "--unified=80", "HEAD", "--"),
    ),
    Unstaged(
        label = "Unstaged",
        detail = "working tree changes",
        gitCommand = listOf("git", "diff", "--no-ext-diff", "--unified=80", "--"),
    ),
    Staged(
        label = "Staged",
        detail = "index changes",
        gitCommand = listOf("git", "diff", "--cached", "--no-ext-diff", "--unified=80", "--"),
    ),
    Branch(
        label = "Upstream",
        detail = "current branch compared with its upstream branch",
        gitCommand = listOf("git", "diff", "--no-ext-diff", "--unified=80", "@{upstream}...HEAD", "--"),
    ),
    Event(
        label = "Event",
        detail = "diff from Codex event",
        gitCommand = emptyList(),
    );

    val refreshScope: ReviewDiffScope
        get() = if (this == Event) Head else this

    companion object {
        val selectable = listOf(Head, Unstaged, Staged, Branch)
    }
}

private data class GitRepositoryContext(
    val root: String,
    val branch: String?,
) {
    val name: String = root.substringAfterLast('/').ifBlank { root }
    val display: String = listOfNotNull(name, branch?.takeIf(String::isNotBlank))
        .joinToString(" / ")
        .ifBlank { name }
}

private data class ReviewInspectorContent(
    val scope: ReviewDiffScope,
    val cwd: String,
    val statusSummary: String,
    val diffText: String,
    val files: List<ReviewDiffFile>,
    val repository: GitRepositoryContext? = null,
    val notice: String? = null,
    val isLoading: Boolean = false,
) {
    val changedFileCount: Int = files.size
    val addedLineCount: Int = files.sumOf { it.addedLineCount }
    val deletedLineCount: Int = files.sumOf { it.deletedLineCount }
    val hasGitRepository: Boolean = repository != null

    companion object {
        fun loading(scope: ReviewDiffScope, cwd: String, notice: String? = null): ReviewInspectorContent =
            ReviewInspectorContent(
                scope = scope,
                cwd = cwd,
                statusSummary = "loading ${scope.label.lowercase()} diff",
                diffText = "",
                files = emptyList(),
                notice = notice,
                isLoading = true,
            )

        fun empty(
            scope: ReviewDiffScope,
            cwd: String,
            statusSummary: String,
            notice: String? = null,
        ): ReviewInspectorContent =
            ReviewInspectorContent(
                scope = scope,
                cwd = cwd,
                statusSummary = statusSummary,
                diffText = "",
                files = emptyList(),
                notice = notice,
            )
    }
}

private data class ReviewDiffFile(
    val path: String,
    val oldPath: String?,
    val status: String,
    val headerLines: List<String>,
    val hunks: List<ReviewDiffHunk>,
) {
    val rawPatch: String = (headerLines + hunks.flatMap { it.rawLines })
        .joinToString("\n")
        .ensureTrailingNewline()
    val addedLineCount: Int = hunks.sumOf { hunk -> hunk.lines.count { it.kind == ReviewDiffLineKind.Added } }
    val deletedLineCount: Int = hunks.sumOf { hunk -> hunk.lines.count { it.kind == ReviewDiffLineKind.Deleted } }

    fun patchFor(hunk: ReviewDiffHunk): String =
        (headerLines + hunk.rawLines).joinToString("\n").ensureTrailingNewline()
}

private data class ReviewDiffHunk(
    val header: String,
    val oldStart: Int,
    val newStart: Int,
    val rawLines: List<String>,
    val lines: List<ReviewDiffLine>,
) {
    val firstNewLine: Int? = lines.firstOrNull { it.newNumber != null }?.newNumber
    val firstOldLine: Int? = lines.firstOrNull { it.oldNumber != null }?.oldNumber
}

private data class ReviewDiffLine(
    val kind: ReviewDiffLineKind,
    val oldNumber: Int?,
    val newNumber: Int?,
    val text: String,
)

private enum class ReviewDiffLineKind {
    Context,
    Added,
    Deleted,
    Note,
}

private enum class ReviewGitActionKind(
    val label: String,
    val requiresConfirmation: Boolean = false,
) {
    StageFile("Stage file"),
    UnstageFile("Unstage file"),
    RevertFile("Revert file", requiresConfirmation = true),
    StageHunk("Stage hunk"),
    UnstageHunk("Unstage hunk"),
    RevertHunk("Revert hunk", requiresConfirmation = true),
}

private data class ReviewGitAction(
    val kind: ReviewGitActionKind,
    val path: String,
    val patch: String = "",
)

private fun commandExecResult(response: String, commandLabel: String): CommandExecResult {
    val json = JSONObject(response)
    if (!json.optBoolean("ok")) {
        throw IllegalStateException(json.optErrorMessage("$commandLabel failed"))
    }
    val result = json.optJSONObject("result")
        ?: throw IllegalStateException("$commandLabel returned no result")
    return CommandExecResult(
        exitCode = result.optInt("exitCode", -1),
        stdout = result.optString("stdout"),
        stderr = result.optString("stderr"),
    )
}

private fun doctorSummary(result: CommandExecResult): String {
    val output = listOf(result.stdout, result.stderr).joinToString("\n")
    val pass = Regex("^PASS ", RegexOption.MULTILINE).findAll(output).count()
    val warn = Regex("^WARN ", RegexOption.MULTILINE).findAll(output).count()
    val fail = Regex("^FAIL ", RegexOption.MULTILINE).findAll(output).count()
    return "doctor: $pass pass / $warn warn / $fail fail / exit ${result.exitCode}"
}

private fun reviewContentFromGit(
    scope: ReviewDiffScope,
    cwd: String,
    status: CommandExecResult,
    diff: CommandExecResult,
    repository: GitRepositoryContext,
    notice: String?,
): ReviewInspectorContent {
    val files = parseUnifiedDiff(diff.stdout)
    val statusSummary = listOf(
        status.stdout.trim(),
        status.stderr.trim().takeIf { status.exitCode != 0 },
    )
        .filterNotNull()
        .filter(String::isNotBlank)
        .joinToString("\n")
        .ifBlank { "clean status for ${projectNameFromCwd(cwd)}" }
    val generatedNotice = when {
        status.exitCode != 0 -> status.stderr.ifBlank { "git status exited ${status.exitCode}" }
        diff.exitCode != 0 -> diff.stderr.ifBlank { "git diff exited ${diff.exitCode}" }
        files.isEmpty() -> "No ${scope.label.lowercase()} diff found."
        else -> notice
    }
    return ReviewInspectorContent(
        scope = scope,
        cwd = cwd,
        statusSummary = statusSummary,
        diffText = diff.stdout,
        files = files,
        repository = repository,
        notice = notice ?: generatedNotice,
    )
}

private fun reviewContentFromRawDiff(
    scope: ReviewDiffScope,
    cwd: String,
    statusSummary: String,
    diffText: String,
    repository: GitRepositoryContext? = null,
    notice: String?,
): ReviewInspectorContent {
    val files = parseUnifiedDiff(diffText)
    return ReviewInspectorContent(
        scope = scope,
        cwd = cwd,
        statusSummary = statusSummary,
        diffText = diffText,
        files = files,
        repository = repository,
        notice = notice ?: if (files.isEmpty()) "No diff rows found." else null,
    )
}

private fun executeReviewGitAction(
    activeClient: OperatorAppServerClient,
    cwd: String,
    action: ReviewGitAction,
): String {
    val patchFile = if (action.patch.isNotBlank()) {
        writeReviewPatchFile(cwd, action.patch)
    } else {
        null
    }
    val command = when (action.kind) {
        ReviewGitActionKind.StageFile -> listOf("git", "add", "--", action.path)
        ReviewGitActionKind.UnstageFile -> listOf("git", "restore", "--staged", "--", action.path)
        ReviewGitActionKind.RevertFile -> listOf("git", "restore", "--staged", "--worktree", "--", action.path)
        ReviewGitActionKind.StageHunk -> listOf("git", "apply", "--cached", "--whitespace=nowarn", patchFile?.absolutePath.orEmpty())
        ReviewGitActionKind.UnstageHunk -> listOf("git", "apply", "--cached", "--reverse", "--whitespace=nowarn", patchFile?.absolutePath.orEmpty())
        ReviewGitActionKind.RevertHunk -> listOf("git", "apply", "--reverse", "--whitespace=nowarn", patchFile?.absolutePath.orEmpty())
    }
    try {
        val result = commandExecResult(
            response = activeClient.execCommand(
                command = command,
                cwd = cwd,
                timeoutMs = 30_000L,
                outputBytesCap = 120_000,
            ),
            commandLabel = command.joinToString(" "),
        )
        if (result.exitCode != 0) {
            throw IllegalStateException(
                result.stderr
                    .ifBlank { result.stdout }
                    .ifBlank { "${action.kind.label} exited ${result.exitCode}" }
                    .trim(),
            )
        }
        return "${action.kind.label} completed for ${action.path}"
    } finally {
        patchFile?.delete()
        patchFile?.parentFile?.takeIf { it.name == ".operator-review" && it.list().isNullOrEmpty() }?.delete()
    }
}

private fun writeReviewPatchFile(cwd: String, patch: String): File {
    val directory = File(cwd, ".operator-review")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Could not create ${directory.absolutePath}")
    }
    val file = File(directory, "patch-${System.currentTimeMillis()}.diff")
    file.writeText(patch.ensureTrailingNewline())
    return file
}

private fun parseUnifiedDiff(diffText: String): List<ReviewDiffFile> {
    val text = extractUnifiedDiff(diffText)
    if (text.isBlank()) {
        return emptyList()
    }
    val files = mutableListOf<ReviewDiffFile>()
    var path: String? = null
    var oldPath: String? = null
    var headerLines = mutableListOf<String>()
    var hunks = mutableListOf<ReviewDiffHunk>()
    var currentHeader: String? = null
    var currentOldLine = 0
    var currentNewLine = 0
    var currentRawLines = mutableListOf<String>()
    var currentLines = mutableListOf<ReviewDiffLine>()

    fun finishHunk() {
        val header = currentHeader ?: return
        val starts = parseHunkStarts(header)
        hunks += ReviewDiffHunk(
            header = header,
            oldStart = starts.first,
            newStart = starts.second,
            rawLines = currentRawLines.toList(),
            lines = currentLines.toList(),
        )
        currentHeader = null
        currentRawLines = mutableListOf()
        currentLines = mutableListOf()
    }

    fun finishFile() {
        finishHunk()
        val currentPath = path ?: return
        files += ReviewDiffFile(
            path = currentPath,
            oldPath = oldPath?.takeIf { it != currentPath },
            status = diffFileStatus(headerLines),
            headerLines = headerLines.toList(),
            hunks = hunks.toList(),
        )
        path = null
        oldPath = null
        headerLines = mutableListOf()
        hunks = mutableListOf()
    }

    text.lineSequence().forEach { line ->
        when {
            line.startsWith("diff --git ") -> {
                finishFile()
                val paths = parseDiffGitPaths(line)
                oldPath = paths.first
                path = paths.second
                headerLines += line
            }

            path == null && line.startsWith("@@ ") -> {
                path = "patch"
                oldPath = null
                headerLines += "diff --git a/patch b/patch"
                headerLines += "--- a/patch"
                headerLines += "+++ b/patch"
                startParsedHunk(
                    line = line,
                    setHeader = { currentHeader = it },
                    setOldLine = { currentOldLine = it },
                    setNewLine = { currentNewLine = it },
                    rawLines = currentRawLines,
                )
            }

            path == null -> Unit

            line.startsWith("@@ ") -> {
                finishHunk()
                startParsedHunk(
                    line = line,
                    setHeader = { currentHeader = it },
                    setOldLine = { currentOldLine = it },
                    setNewLine = { currentNewLine = it },
                    rawLines = currentRawLines,
                )
            }

            currentHeader != null -> {
                currentRawLines += line
                val marker = line.firstOrNull()
                when (marker) {
                    '+' -> {
                        currentLines += ReviewDiffLine(
                            kind = ReviewDiffLineKind.Added,
                            oldNumber = null,
                            newNumber = currentNewLine,
                            text = line.drop(1),
                        )
                        currentNewLine += 1
                    }

                    '-' -> {
                        currentLines += ReviewDiffLine(
                            kind = ReviewDiffLineKind.Deleted,
                            oldNumber = currentOldLine,
                            newNumber = null,
                            text = line.drop(1),
                        )
                        currentOldLine += 1
                    }

                    '\\' -> {
                        currentLines += ReviewDiffLine(
                            kind = ReviewDiffLineKind.Note,
                            oldNumber = null,
                            newNumber = null,
                            text = line,
                        )
                    }

                    else -> {
                        currentLines += ReviewDiffLine(
                            kind = ReviewDiffLineKind.Context,
                            oldNumber = currentOldLine,
                            newNumber = currentNewLine,
                            text = if (line.startsWith(" ")) line.drop(1) else line,
                        )
                        currentOldLine += 1
                        currentNewLine += 1
                    }
                }
            }

            else -> {
                headerLines += line
                when {
                    line.startsWith("--- ") -> oldPath = parseDiffMarkerPath(line.removePrefix("--- "))
                    line.startsWith("+++ ") -> {
                        parseDiffMarkerPath(line.removePrefix("+++ "))
                            ?.let { path = it }
                    }
                }
            }
        }
    }
    finishFile()
    return files
}

private fun startParsedHunk(
    line: String,
    setHeader: (String) -> Unit,
    setOldLine: (Int) -> Unit,
    setNewLine: (Int) -> Unit,
    rawLines: MutableList<String>,
) {
    val starts = parseHunkStarts(line)
    setHeader(line)
    setOldLine(starts.first)
    setNewLine(starts.second)
    rawLines += line
}

private val hunkHeaderRegex = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*""")

private fun parseHunkStarts(header: String): Pair<Int, Int> {
    val match = hunkHeaderRegex.matchEntire(header)
    return if (match == null) {
        0 to 0
    } else {
        match.groupValues[1].toIntOrNull().orZero() to match.groupValues[2].toIntOrNull().orZero()
    }
}

private fun parseDiffGitPaths(line: String): Pair<String?, String> {
    val body = line.removePrefix("diff --git ")
    val parts = body.split(" b/", limit = 2)
    val old = parts.firstOrNull()?.removePrefix("a/")?.takeIf(String::isNotBlank)
    val next = parts.getOrNull(1)?.takeIf(String::isNotBlank)
        ?: old
        ?: "patch"
    return old to next
}

private fun parseDiffMarkerPath(value: String): String? =
    value.trim()
        .removePrefix("a/")
        .removePrefix("b/")
        .takeIf { it != "/dev/null" && it.isNotBlank() }

private fun diffFileStatus(headerLines: List<String>): String {
    val header = headerLines.joinToString("\n")
    return when {
        "new file mode" in header -> "added"
        "deleted file mode" in header -> "deleted"
        "rename from" in header || "rename to" in header -> "renamed"
        else -> "modified"
    }
}

private fun extractUnifiedDiff(value: String): String {
    val diffIndex = value.indexOf("diff --git ")
    if (diffIndex >= 0) {
        return value.substring(diffIndex).trimDiffPayloadTail()
    }
    val hunkIndex = value.indexOf("@@ -")
    if (hunkIndex >= 0) {
        return value.substring(hunkIndex).trimDiffPayloadTail()
    }
    return ""
}

private fun String.normalizedEscapedDiffText(): String =
    replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")

private fun String.trimDiffPayloadTail(): String {
    val jsonTail = listOf("\n\n{", "\n{\"", "\n\n[", "\n[{")
        .map { marker -> indexOf(marker) }
        .filter { it > 0 }
        .minOrNull()
    return if (jsonTail != null) substring(0, jsonTail) else this
}

private fun String.ensureTrailingNewline(): String =
    if (endsWith("\n")) this else "$this\n"

private fun Int?.orZero(): Int = this ?: 0

private sealed class ThreadListState {
    data object Loading : ThreadListState()
    data object Ready : ThreadListState()
    data class Error(val message: String) : ThreadListState()
}

private val ThreadListState.eyebrow: String
    get() = when (this) {
        ThreadListState.Loading -> "thread/list"
        ThreadListState.Ready -> "saved"
        is ThreadListState.Error -> "error"
    }

private data class ThreadSummary(
    val id: String,
    val title: String,
    val detail: String,
    val cwd: String? = null,
    val projectName: String = projectNameFromCwd(cwd),
    val status: String = "notLoaded",
    val updatedAt: Long? = null,
    val source: String? = null,
    val ephemeral: Boolean = false,
    val forkedFromId: String? = null,
)

private data class SessionProjectSummary(
    val cwd: String,
    val name: String,
    val threadCount: Int,
    val updatedAt: Long?,
)

private data class SessionProjectSection(
    val key: String,
    val cwd: String?,
    val name: String,
    val threadCount: Int,
    val updatedAt: Long?,
    val threads: List<ThreadSummary>,
)

private sealed class SlashInteraction {
    data class ModelPicker(
        val models: List<ModelOption>,
        val selectedModelId: String?,
        val selectedReasoningEffort: String?,
    ) : SlashInteraction()

    data class ExperimentalPicker(
        val features: List<ExperimentalFeatureOption>,
    ) : SlashInteraction()

    data class OptionPicker(
        val title: String,
        val detail: String,
        val action: SlashOptionAction,
        val options: List<SlashPickerOption>,
    ) : SlashInteraction()
}

private enum class SlashOptionAction {
    CollaborationMode,
    Personality,
    PermissionPreset,
}

private data class SlashPickerOption(
    val token: String,
    val label: String,
    val detail: String,
    val selected: Boolean = false,
    val payload: JSONObject? = null,
)

private data class ModelOption(
    val id: String,
    val label: String,
    val description: String,
    val isDefault: Boolean,
    val defaultReasoningEffort: String?,
    val reasoningEfforts: List<ThinkingEffortOption>,
)

private data class ThinkingEffortOption(
    val value: String,
    val label: String,
    val description: String,
)

private fun SlashInteraction.ModelPicker.modelFor(modelId: String?): ModelOption? =
    modelId?.let { id -> models.firstOrNull { it.id == id } }
        ?: models.firstOrNull { it.isDefault }
        ?: models.firstOrNull()

private data class ExperimentalFeatureOption(
    val name: String,
    val label: String,
    val description: String,
    val stage: String,
    val enabled: Boolean,
    val defaultEnabled: Boolean,
)

private data class SlashCommandSpec(
    val name: String,
    val summary: String,
    val kind: String,
    val aliases: List<String> = emptyList(),
    val availableDuringTurn: Boolean = true,
)

private val slashCommands = listOf(
    SlashCommandSpec("/help", "show the Codex slash command catalog", "LOCAL"),
    SlashCommandSpec("/model", "list models, or set the model for next turns with /model <id>", "CODEX"),
    SlashCommandSpec("/fast", "toggle fast service tier for next turns", "CODEX"),
    SlashCommandSpec("/approvals", "choose approval, reviewer, and sandbox preset", "CODEX"),
    SlashCommandSpec("/permissions", "choose permission and approval preset", "CODEX"),
    SlashCommandSpec("/keymap", "open keyboard shortcut selection", "PENDING"),
    SlashCommandSpec("/setup-default-sandbox", "set up Windows degraded-sandbox support", "DESKTOP"),
    SlashCommandSpec("/sandbox-add-read-dir", "add an extra sandbox read directory", "PENDING"),
    SlashCommandSpec("/experimental", "list experimental features", "CODEX"),
    SlashCommandSpec("/autoreview", "manage automatic review-denial settings", "PENDING"),
    SlashCommandSpec("/memories", "inspect and manage Codex memories", "PENDING"),
    SlashCommandSpec("/skills", "list available skills", "CODEX"),
    SlashCommandSpec("/hooks", "list configured hooks", "CODEX"),
    SlashCommandSpec("/review", "start a Codex review", "CODEX", availableDuringTurn = false),
    SlashCommandSpec("/rename", "rename the active thread", "CODEX"),
    SlashCommandSpec("/new", "start a fresh thread", "LOCAL", availableDuringTurn = false),
    SlashCommandSpec("/resume", "open session history", "LOCAL", availableDuringTurn = false),
    SlashCommandSpec("/fork", "fork the active thread", "CODEX", availableDuringTurn = false),
    SlashCommandSpec("/init", "ask Codex to generate AGENTS.md", "CODEX", availableDuringTurn = false),
    SlashCommandSpec("/compact", "request thread compaction", "CODEX", availableDuringTurn = false),
    SlashCommandSpec("/plan", "toggle plan collaboration mode or run /plan <prompt>", "CODEX", availableDuringTurn = false),
    SlashCommandSpec("/goal", "show, set, or clear the thread goal", "CODEX"),
    SlashCommandSpec("/collab", "list collaboration modes", "CODEX"),
    SlashCommandSpec("/agent", "open agent picker", "PENDING"),
    SlashCommandSpec("/side", "start a side conversation", "PENDING"),
    SlashCommandSpec("/copy", "copy the last assistant message", "ANDROID"),
    SlashCommandSpec("/diff", "request git diff through the active Codex thread", "CODEX"),
    SlashCommandSpec("/mention", "insert a file mention marker", "LOCAL"),
    SlashCommandSpec("/status", "show current Codex account, thread, and config status", "CODEX"),
    SlashCommandSpec("/doctor", "check Android development runtimes and command tools", "ANDROID"),
    SlashCommandSpec("/debug-config", "show raw Codex config metadata", "CODEX"),
    SlashCommandSpec("/title", "configure terminal title behavior", "DESKTOP"),
    SlashCommandSpec("/statusline", "configure terminal status line", "DESKTOP"),
    SlashCommandSpec("/theme", "open theme picker", "PENDING"),
    SlashCommandSpec("/mcp", "list MCP servers and tools", "CODEX"),
    SlashCommandSpec("/apps", "list apps and connectors", "CODEX"),
    SlashCommandSpec("/plugins", "list installed and available plugins", "CODEX"),
    SlashCommandSpec("/logout", "sign out of Codex", "CODEX"),
    SlashCommandSpec("/quit", "move the Android app to the background", "ANDROID"),
    SlashCommandSpec("/exit", "move the Android app to the background", "ANDROID"),
    SlashCommandSpec("/feedback", "upload feedback with /feedback <text>", "CODEX"),
    SlashCommandSpec("/ps", "request a process listing through the active Codex thread", "CODEX"),
    SlashCommandSpec("/stop", "interrupt the active turn and clean background terminals", "CODEX", aliases = listOf("/clean")),
    SlashCommandSpec("/clear", "clear the visible transcript while idle", "LOCAL", availableDuringTurn = false),
    SlashCommandSpec("/personality", "open personality picker", "CODEX"),
    SlashCommandSpec("/realtime", "toggle realtime audio conversation", "PENDING"),
    SlashCommandSpec("/subagents", "open subagent picker", "PENDING"),
)

private fun slashCommandFor(token: String): SlashCommandSpec? =
    slashCommands.firstOrNull { command ->
        command.name == token || command.aliases.any { alias -> alias == token }
    }

private fun slashCommandSuggestions(prompt: String): List<SlashCommandSpec> {
    val trimmed = prompt.trimStart()
    if (!trimmed.startsWith("/")) {
        return emptyList()
    }
    val query = trimmed
        .removePrefix("/")
        .substringBefore(" ")
        .lowercase()
    return slashCommands
        .filter { command ->
            command.name.removePrefix("/").startsWith(query) ||
                command.aliases.any { alias -> alias.removePrefix("/").startsWith(query) }
        }
        .take(9)
}

private fun slashHelpText(): String =
    slashCommands.joinToString("\n") { command ->
        val aliases = command.aliases
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = " (", postfix = ")")
            .orEmpty()
        "${command.name}$aliases [${command.kind}] - ${command.summary}"
    }

private const val INIT_AGENTS_PROMPT = """
Generate a file named AGENTS.md that serves as a contributor guide for this repository.
Your goal is to produce a clear, concise, and well-structured document with descriptive headings and actionable explanations for each section.
Follow the outline below, but adapt as needed. Add sections if relevant, and omit those that do not apply to this project.

Document Requirements

- Title the document "Repository Guidelines".
- Use Markdown headings (#, ##, etc.) for structure.
- Keep the document concise. 200-400 words is optimal.
- Keep explanations short, direct, and specific to this repository.
- Provide examples where helpful (commands, directory paths, naming patterns).
- Maintain a professional, instructional tone.

Recommended Sections

Project Structure & Module Organization

- Outline the project structure, including where the source code, tests, and assets are located.

Build, Test, and Development Commands

- List key commands for building, testing, and running locally.
- Briefly explain what each command does.

Coding Style & Naming Conventions

- Specify indentation rules, language-specific style preferences, and naming patterns.
- Include any formatting or linting tools used.

Testing Guidelines

- Identify testing frameworks and coverage requirements.
- State test naming conventions and how to run tests.

Commit & Pull Request Guidelines

- Summarize commit message conventions found in the project's Git history.
- Outline pull request requirements.

Optional: Add other sections if relevant, such as Security & Configuration Tips, Architecture Overview, or Agent-Specific Instructions.
"""

private fun accountStateSummary(accountState: AccountPanelState): String = when (accountState) {
    AccountPanelState.Loading -> "checking account"
    is AccountPanelState.SignedOut -> accountState.detail
    is AccountPanelState.SignedIn -> accountState.label
    is AccountPanelState.LoginPending -> "device-code login pending"
    is AccountPanelState.Error -> accountState.message
}

private fun mcpStatusSummary(response: JSONObject): String {
    val data = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    var toolCount = 0
    val names = mutableListOf<String>()
    for (index in 0 until data.length()) {
        val server = data.optJSONObject(index) ?: continue
        val tools = server.optJSONObject("tools")
        toolCount += tools?.length() ?: 0
        if (names.size < 4) {
            val name = server.optString("name").takeIf(String::isNotBlank) ?: "server"
            names += "$name:${tools?.length() ?: 0}"
        }
    }
    if (data.length() == 0) {
        return "no MCP servers returned"
    }
    return "${data.length()} servers / $toolCount tools (${names.joinToString(", ")})"
}

private fun appListSummary(response: JSONObject): String {
    val data = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    var accessible = 0
    val names = mutableListOf<String>()
    for (index in 0 until data.length()) {
        val app = data.optJSONObject(index) ?: continue
        if (app.optBoolean("isAccessible")) {
            accessible += 1
        }
        if (names.size < 4) {
            app.optString("name").takeIf(String::isNotBlank)?.let(names::add)
        }
    }
    if (data.length() == 0) {
        return "no apps returned"
    }
    return "${data.length()} apps / $accessible accessible (${names.joinToString(", ")})"
}

private fun pluginListSummary(response: JSONObject): String {
    val marketplaces = response.optJSONObject("result")?.optJSONArray("marketplaces") ?: JSONArray()
    var pluginCount = 0
    var installedCount = 0
    val names = mutableListOf<String>()
    for (marketIndex in 0 until marketplaces.length()) {
        val marketplace = marketplaces.optJSONObject(marketIndex) ?: continue
        val plugins = marketplace.optJSONArray("plugins") ?: JSONArray()
        pluginCount += plugins.length()
        for (pluginIndex in 0 until plugins.length()) {
            val plugin = plugins.optJSONObject(pluginIndex) ?: continue
            if (plugin.optBoolean("installed")) {
                installedCount += 1
            }
            if (names.size < 4) {
                plugin.optString("name").takeIf(String::isNotBlank)?.let(names::add)
            }
        }
    }
    if (pluginCount == 0) {
        return "no plugins returned"
    }
    return "$pluginCount plugins / $installedCount installed (${names.joinToString(", ")})"
}

private fun skillsListSummary(response: JSONObject): String {
    val entries = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    var skillCount = 0
    var enabledCount = 0
    val names = mutableListOf<String>()
    for (entryIndex in 0 until entries.length()) {
        val entry = entries.optJSONObject(entryIndex) ?: continue
        val skills = entry.optJSONArray("skills") ?: JSONArray()
        skillCount += skills.length()
        for (skillIndex in 0 until skills.length()) {
            val skill = skills.optJSONObject(skillIndex) ?: continue
            if (skill.optBoolean("enabled")) {
                enabledCount += 1
            }
            if (names.size < 4) {
                skill.optString("name").takeIf(String::isNotBlank)?.let(names::add)
            }
        }
    }
    if (skillCount == 0) {
        return "no skills returned"
    }
    return "$skillCount skills / $enabledCount enabled (${names.joinToString(", ")})"
}

private fun modelListSummary(response: JSONObject): String {
    val models = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    val names = mutableListOf<String>()
    var defaultModel: String? = null
    var thinkingModeCount = 0
    for (index in 0 until models.length()) {
        val model = models.optJSONObject(index) ?: continue
        thinkingModeCount += model.optJSONArray("supportedReasoningEfforts")
            ?.length()
            ?: model.optJSONArray("supported_reasoning_efforts")?.length()
            ?: 0
        if (model.optBoolean("isDefault")) {
            defaultModel = model.optString("id").takeIf(String::isNotBlank)
                ?: model.optString("model").takeIf(String::isNotBlank)
        }
        if (names.size < 5) {
            val label = model.optString("displayName").takeIf(String::isNotBlank)
                ?: model.optString("id").takeIf(String::isNotBlank)
                ?: model.optString("model").takeIf(String::isNotBlank)
            label?.let(names::add)
        }
    }
    if (models.length() == 0) {
        return "no models returned"
    }
    return "${models.length()} models / $thinkingModeCount thinking modes / default ${defaultModel ?: "unknown"} (${names.joinToString(", ")})"
}

private fun modelOptionsFromResponse(response: JSONObject): List<ModelOption> {
    val models = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    return (0 until models.length()).mapNotNull { index ->
        val model = models.optJSONObject(index) ?: return@mapNotNull null
        val id = model.optNonBlankString("id")
            ?: model.optNonBlankString("model")
            ?: return@mapNotNull null
        val defaultReasoningEffort = model.optNonBlankString("defaultReasoningEffort")
            ?: model.optNonBlankString("default_reasoning_effort")
        ModelOption(
            id = id,
            label = model.optNonBlankString("displayName") ?: id,
            description = model.optNonBlankString("description").orEmpty(),
            isDefault = model.optBoolean("isDefault"),
            defaultReasoningEffort = defaultReasoningEffort,
            reasoningEfforts = thinkingEffortOptionsFromModel(model, defaultReasoningEffort),
        )
    }
}

private fun thinkingEffortOptionsFromModel(
    model: JSONObject,
    defaultReasoningEffort: String?,
): List<ThinkingEffortOption> {
    val efforts = model.optJSONArray("supportedReasoningEfforts")
        ?: model.optJSONArray("supported_reasoning_efforts")
        ?: JSONArray()
    val parsed = (0 until efforts.length()).mapNotNull { index ->
        val effort = efforts.optJSONObject(index) ?: return@mapNotNull null
        val value = effort.optNonBlankString("reasoningEffort")
            ?: effort.optNonBlankString("reasoning_effort")
            ?: effort.optNonBlankString("effort")
            ?: return@mapNotNull null
        ThinkingEffortOption(
            value = value,
            label = reasoningEffortLabel(value),
            description = effort.optNonBlankString("description").orEmpty(),
        )
    }
    if (parsed.isNotEmpty() || defaultReasoningEffort.isNullOrBlank()) {
        return parsed
    }
    return listOf(
        ThinkingEffortOption(
            value = defaultReasoningEffort,
            label = reasoningEffortLabel(defaultReasoningEffort),
            description = "Model default",
        )
    )
}

private fun reasoningEffortLabel(effort: String): String =
    when (effort.lowercase()) {
        "none" -> "None"
        "minimal" -> "Minimal"
        "low" -> "Low"
        "medium" -> "Medium"
        "high" -> "High"
        "xhigh" -> "X High"
        else -> effort
    }

private fun configRequirementsSummary(response: JSONObject): String {
    val requirements = response.optJSONObject("result")?.optJSONObject("requirements")
        ?: return "no managed config requirements"
    val approvals = requirements.optJSONArray("allowedApprovalPolicies")?.stringItems().orEmpty()
    val sandboxes = requirements.optJSONArray("allowedSandboxModes")?.stringItems().orEmpty()
    val reviewers = requirements.optJSONArray("allowedApprovalsReviewers")?.stringItems().orEmpty()
    return listOf(
        approvals.takeIf { it.isNotEmpty() }?.joinToString(prefix = "approvals=", separator = ","),
        sandboxes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "sandboxes=", separator = ","),
        reviewers.takeIf { it.isNotEmpty() }?.joinToString(prefix = "reviewers=", separator = ","),
    ).filterNotNull().joinToString(" / ").ifBlank { "requirements returned with no restrictions" }
}

private fun experimentalFeatureSummary(response: JSONObject): String {
    val features = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    var enabled = 0
    val names = mutableListOf<String>()
    for (index in 0 until features.length()) {
        val feature = features.optJSONObject(index) ?: continue
        if (!feature.isCliVisibleExperimentalFeature()) {
            continue
        }
        if (feature.optBoolean("enabled")) {
            enabled += 1
        }
        if (names.size < 5) {
            val label = feature.optString("displayName").takeIf(String::isNotBlank)
                ?: feature.optString("name").takeIf(String::isNotBlank)
            label?.let(names::add)
        }
    }
    if (features.length() == 0) {
        return "no experimental features returned"
    }
    val visibleCount = experimentalOptionsFromResponse(response).size
    if (visibleCount == 0) {
        return "no CLI-visible experimental features returned"
    }
    return "$visibleCount features / $enabled enabled (${names.joinToString(", ")})"
}

private fun experimentalOptionsFromResponse(response: JSONObject): List<ExperimentalFeatureOption> {
    val features = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    return (0 until features.length()).mapNotNull { index ->
        val feature = features.optJSONObject(index) ?: return@mapNotNull null
        if (!feature.isCliVisibleExperimentalFeature()) {
            return@mapNotNull null
        }
        val name = feature.optNonBlankString("name") ?: return@mapNotNull null
        ExperimentalFeatureOption(
            name = name,
            label = feature.optNonBlankString("displayName") ?: name,
            description = feature.optNonBlankString("description").orEmpty(),
            stage = feature.optNonBlankString("stage").orEmpty(),
            enabled = feature.optBoolean("enabled"),
            defaultEnabled = feature.optBoolean("defaultEnabled"),
        )
    }
}

private fun JSONObject.isCliVisibleExperimentalFeature(): Boolean =
    optNonBlankString("stage").equals("beta", ignoreCase = true)

private fun hooksListSummary(response: JSONObject): String {
    val entries = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    var hookCount = 0
    var errorCount = 0
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        hookCount += entry.optJSONArray("hooks")?.length() ?: 0
        errorCount += entry.optJSONArray("errors")?.length() ?: 0
    }
    return when {
        entries.length() == 0 -> "no hook entries returned"
        hookCount == 0 && errorCount == 0 -> "${entries.length()} workspaces / no hooks"
        else -> "${entries.length()} workspaces / $hookCount hooks / $errorCount errors"
    }
}

private fun collaborationModeSummary(response: JSONObject): String {
    val modes = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    val names = mutableListOf<String>()
    for (index in 0 until modes.length()) {
        modes.optJSONObject(index)
            ?.optString("name")
            ?.takeIf(String::isNotBlank)
            ?.let {
                if (names.size < 6) {
                    names += it
                }
            }
    }
    if (modes.length() == 0) {
        return "no collaboration modes returned"
    }
    return "${modes.length()} collaboration modes (${names.joinToString(", ")})"
}

private fun collaborationModeOptionsFromResponse(
    response: JSONObject,
    planModeEnabled: Boolean,
): List<SlashPickerOption> {
    val modes = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    val options = (0 until modes.length()).mapNotNull { index ->
        val mode = modes.optJSONObject(index) ?: return@mapNotNull null
        val token = mode.optString("mode")
            .takeIf(String::isNotBlank)
            ?: mode.optString("name").lowercase().takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val normalized = token.lowercase()
        SlashPickerOption(
            token = normalized,
            label = mode.optString("name").takeIf(String::isNotBlank)
                ?: normalized.replaceFirstChar { it.uppercase() },
            detail = listOfNotNull(
                if (normalized == "plan") "Ask Codex to propose/confirm plans before coding" else "Default Codex execution behavior",
                mode.optString("reasoning_effort").takeIf(String::isNotBlank)?.let { "thinking $it" },
                mode.optString("model").takeIf(String::isNotBlank)?.let { "model $it" },
            ).joinToString(" / "),
            selected = (normalized == "plan" && planModeEnabled) ||
                (normalized != "plan" && !planModeEnabled),
        )
    }
    return options.ifEmpty {
        listOf(
            SlashPickerOption(
                token = "default",
                label = "Default",
                detail = "Default Codex execution behavior",
                selected = !planModeEnabled,
            ),
            SlashPickerOption(
                token = "plan",
                label = "Plan",
                detail = "Ask Codex to propose/confirm plans before coding",
                selected = planModeEnabled,
            ),
        )
    }
}

private fun personalityOptions(selected: String?): List<SlashPickerOption> =
    listOf(
        SlashPickerOption(
            token = "default",
            label = "Config default",
            detail = "Use the personality from Codex config",
            selected = selected == null,
        ),
        SlashPickerOption(
            token = "pragmatic",
            label = "Pragmatic",
            detail = "Direct, concise engineering style",
            selected = selected == "pragmatic",
        ),
        SlashPickerOption(
            token = "friendly",
            label = "Friendly",
            detail = "Warmer conversational style",
            selected = selected == "friendly",
        ),
        SlashPickerOption(
            token = "none",
            label = "None",
            detail = "No personality-specific instruction",
            selected = selected == "none",
        ),
    )

private fun permissionPresetOptionsFromResponse(
    response: JSONObject,
    approvalPolicyOverride: String?,
    permissionSelectionOverrideJson: String?,
    approvalsReviewerOverride: String?,
): List<SlashPickerOption> {
    val presets = response.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
    val currentPermissionId = permissionSelectionOverrideJson.permissionSelectionId()
    return (0 until presets.length()).mapNotNull { index ->
        val preset = presets.optJSONObject(index) ?: return@mapNotNull null
        if (!preset.optNonBlankString("disabledReason").isNullOrBlank()) {
            return@mapNotNull null
        }
        val id = preset.optNonBlankString("id") ?: return@mapNotNull null
        val label = preset.optNonBlankString("label") ?: id
        val permissions = preset.optJSONObject("permissions")
        val permissionId = permissions?.optNonBlankString("id")
        val approval = preset.optNonBlankString("approvalPolicy")
        val reviewer = preset.optNonBlankString("approvalsReviewer")
        val selected = if (
            approvalPolicyOverride != null ||
            approvalsReviewerOverride != null ||
            currentPermissionId != null
        ) {
            approval == approvalPolicyOverride &&
                reviewer == approvalsReviewerOverride &&
                permissionId == currentPermissionId
        } else {
            preset.optBoolean("isCurrent")
        }
        SlashPickerOption(
            token = id,
            label = label,
            detail = listOfNotNull(
                approval?.let { "approval $it" },
                reviewer?.let { "reviewer ${approvalReviewerLabel(it)}" },
                permissionId?.let { "profile $it" },
                preset.optNonBlankString("description"),
            ).joinToString(" / "),
            selected = selected,
            payload = preset,
        )
    }
}

private fun slashOptionMatchesArg(option: SlashPickerOption, arg: String): Boolean {
    val normalizedArg = arg.trim().lowercase()
    return normalizedArg.isNotBlank() &&
        (option.token.lowercase() == normalizedArg || option.label.lowercase() == normalizedArg)
}

private fun String?.permissionSelectionId(): String? =
    this?.jsonObjectOrNull()?.optNonBlankString("id")

private fun activePermissionSelection(permissionSelectionOverrideJson: String?): JSONObject =
    permissionSelectionOverrideJson?.jsonObjectOrNull()
        ?: operatorPermissionProfileSelection()

private fun approvalReviewerLabel(value: String): String = when (value) {
    "auto_review", "guardian_subagent" -> "auto-review"
    "user" -> "user"
    else -> value
}

private fun String.jsonObjectOrNull(): JSONObject? =
    runCatching { JSONObject(this) }.getOrNull()

private fun threadGoalSummary(response: JSONObject): String {
    val goal = response.optJSONObject("result")?.optJSONObject("goal")
        ?: return "no active thread goal"
    val objective = goal.optString("objective").takeIf(String::isNotBlank) ?: "goal"
    val status = goal.optString("status").takeIf(String::isNotBlank) ?: "active"
    val tokens = goal.optLong("tokensUsed", -1L).takeIf { it >= 0L }
    return listOfNotNull(
        status,
        objective,
        tokens?.let { "$it tokens used" },
    ).joinToString(" / ")
}

private fun threadReadSummary(response: JSONObject): String {
    val thread = response.optJSONObject("result")?.optJSONObject("thread")
        ?: return "thread metadata unavailable"
    val id = thread.optString("id").takeIf(String::isNotBlank)
    val title = thread.optString("name").takeIf(String::isNotBlank)
        ?: thread.optString("preview").takeIf(String::isNotBlank)
    val status = threadStatusLabel(thread.opt("status"))
    return listOfNotNull(
        id?.let { shortId(it) },
        title,
        status,
        thread.optString("cwd").takeIf(String::isNotBlank),
    ).joinToString(" / ").ifBlank { "thread metadata returned" }
}

private fun statusBarItemsFromPreference(raw: String?): List<MobileStatusBarItem> {
    if (raw == null) {
        return listOf(
            MobileStatusBarItem.ProjectRoot,
            MobileStatusBarItem.SessionId,
            MobileStatusBarItem.ModelWithReasoning,
            MobileStatusBarItem.ContextRemaining,
        )
    }
    if (raw.isBlank()) {
        return emptyList()
    }
    return raw.split(',')
        .mapNotNull { MobileStatusBarItem.fromId(it) }
        .distinct()
        .take(MAX_STATUS_BAR_ITEMS)
}

private fun toggledStatusBarItems(
    current: List<MobileStatusBarItem>,
    item: MobileStatusBarItem,
): List<MobileStatusBarItem> =
    if (item in current) {
        current - item
    } else {
        (current + item).distinct().take(MAX_STATUS_BAR_ITEMS)
    }

private fun statusBarLine(
    items: List<MobileStatusBarItem>,
    context: MobileStatusBarContext,
): String =
    items
        .take(MAX_STATUS_BAR_ITEMS)
        .mapNotNull { item -> statusBarValue(item, context) }
        .joinToString(" / ")

private fun statusBarPreviewLine(
    items: List<MobileStatusBarItem>,
    context: MobileStatusBarContext,
): String =
    items
        .take(MAX_STATUS_BAR_ITEMS)
        .map { item -> statusBarValue(item, context) ?: item.placeholder }
        .joinToString(" / ")
        .ifBlank { "hidden" }

private fun statusBarValue(
    item: MobileStatusBarItem,
    context: MobileStatusBarContext,
): String? =
    when (item) {
        MobileStatusBarItem.ModelName -> statusModelName(context)
        MobileStatusBarItem.ModelWithReasoning -> {
            val model = statusModelName(context) ?: return null
            val reasoning = statusReasoningEffort(context) ?: "default"
            val fast = if (statusFastModeEnabled(context)) " fast" else ""
            "$model $reasoning$fast"
        }
        MobileStatusBarItem.CurrentDir -> compactDirectoryDisplay(context.activeProjectCwd)
        MobileStatusBarItem.ProjectRoot -> projectNameFromCwd(context.activeProjectCwd)
        MobileStatusBarItem.GitBranch -> context.gitBranch
        MobileStatusBarItem.RunState -> runStateStatusText(context.turnState)
        MobileStatusBarItem.ContextRemaining -> {
            val remaining = contextRemainingPercent(context) ?: return null
            "Context $remaining% left"
        }
        MobileStatusBarItem.ContextUsed -> {
            val remaining = contextRemainingPercent(context) ?: return null
            "Context ${(100 - remaining).coerceIn(0, 100)}% used"
        }
        MobileStatusBarItem.FiveHourLimit -> statusLimitDisplay(context, primary = true)
        MobileStatusBarItem.WeeklyLimit -> statusLimitDisplay(context, primary = false)
        MobileStatusBarItem.CodexVersion -> BuildConfig.VERSION_NAME
        MobileStatusBarItem.ContextWindowSize -> statusContextWindow(context)
            ?.let { "${formatTokensCompact(it)} window" }
        MobileStatusBarItem.UsedTokens -> context.tokenUsage
            ?.total
            ?.tokensInContextWindow()
            ?.takeIf { it > 0L }
            ?.let { "${formatTokensCompact(it)} used" }
        MobileStatusBarItem.TotalInputTokens -> {
            val tokens = context.tokenUsage?.total?.inputTokens ?: 0L
            "${formatTokensCompact(tokens)} in"
        }
        MobileStatusBarItem.TotalOutputTokens -> {
            val tokens = context.tokenUsage?.total?.outputTokens ?: 0L
            "${formatTokensCompact(tokens)} out"
        }
        MobileStatusBarItem.SessionId -> context.activeThreadId?.let(::shortId)
        MobileStatusBarItem.FastMode -> if (statusFastModeEnabled(context)) "Fast on" else "Fast off"
        MobileStatusBarItem.ThreadTitle -> context.activeThreadTitle?.takeIf(String::isNotBlank)
        MobileStatusBarItem.TaskProgress -> context.taskProgress
    }

private fun statusModelName(context: MobileStatusBarContext): String? =
    context.modelOverride
        ?: context.config.optConfigString("model")
        ?: "default"

private fun statusReasoningEffort(context: MobileStatusBarContext): String? =
    context.reasoningEffortOverride
        ?: context.config.optConfigString("model_reasoning_effort", "modelReasoningEffort")

private fun statusFastModeEnabled(context: MobileStatusBarContext): Boolean =
    context.fastServiceTier ||
        context.config.optConfigString("service_tier", "serviceTier") == "fast"

private fun JSONObject?.optConfigString(vararg keys: String): String? {
    if (this == null) {
        return null
    }
    return keys.firstNotNullOfOrNull { key -> jsonValueAsString(opt(key)) }
        ?.takeIf { value -> value.isNotBlank() && !value.equals("null", ignoreCase = true) }
}

private fun runStateStatusText(turnState: TurnPanelState): String =
    when (turnState) {
        TurnPanelState.Idle -> "Ready"
        TurnPanelState.Starting -> "Starting"
        TurnPanelState.Running -> "Working"
        TurnPanelState.Interrupting -> "Waiting"
        is TurnPanelState.Completed -> "Ready"
        is TurnPanelState.Error -> "Action required"
    }

private fun contextRemainingPercent(context: MobileStatusBarContext): Int? {
    val window = statusContextWindow(context) ?: return 100
    if (window <= BASELINE_CONTEXT_TOKENS) {
        return 0
    }
    val used = ((context.tokenUsage?.last ?: StatusTokenBreakdown.Zero).tokensInContextWindow() -
        BASELINE_CONTEXT_TOKENS).coerceAtLeast(0L)
    val effectiveWindow = window - BASELINE_CONTEXT_TOKENS
    val remaining = (effectiveWindow - used).coerceAtLeast(0L)
    return ((remaining.toDouble() / effectiveWindow.toDouble()) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}

private fun statusContextWindow(context: MobileStatusBarContext): Long? =
    context.tokenUsage?.modelContextWindow
        ?: context.config.optConfigLong("model_context_window", "modelContextWindow")

private fun JSONObject?.optConfigLong(vararg keys: String): Long? {
    if (this == null) {
        return null
    }
    return keys.firstNotNullOfOrNull { key -> optNullableLong(key) }
}

private fun statusLimitDisplay(context: MobileStatusBarContext, primary: Boolean): String? {
    val snapshot = context.rateLimits.byLimitId["codex"] ?: context.rateLimits.primary
    val window = if (primary) snapshot?.primary else snapshot?.secondary
    window ?: return null
    val label = window.windowDurationMins?.let(::limitDurationLabel)
        ?: if (primary) "5h" else "weekly"
    val remaining = (100.0 - window.usedPercent).coerceIn(0.0, 100.0).roundToInt()
    return "$label $remaining%"
}

private fun limitDurationLabel(windowMinutes: Long): String {
    val minutesPerHour = 60L
    val minutesPerDay = 24L * minutesPerHour
    val minutesPerWeek = 7L * minutesPerDay
    val minutesPerMonth = 30L * minutesPerDay
    val roundingBiasMinutes = 3L
    val minutes = windowMinutes.coerceAtLeast(0L)
    return when {
        minutes <= minutesPerDay + roundingBiasMinutes -> {
            val hours = ((minutes + roundingBiasMinutes) / minutesPerHour).coerceAtLeast(1L)
            "${hours}h"
        }
        minutes <= minutesPerWeek + roundingBiasMinutes -> "weekly"
        minutes <= minutesPerMonth + roundingBiasMinutes -> "monthly"
        else -> "annual"
    }
}

private fun compactDirectoryDisplay(directory: String): String {
    val home = System.getProperty("user.home")?.trimEnd('/')
    return if (!home.isNullOrBlank() && directory == home) {
        "~"
    } else if (!home.isNullOrBlank() && directory.startsWith("$home/")) {
        "~/${directory.removePrefix("$home/")}"
    } else {
        directory
    }
}

private fun taskProgressFromTranscriptRows(rows: List<TranscriptRow>): String? {
    val plan = rows.asReversed()
        .firstOrNull { row -> row.label.equals("plan", ignoreCase = true) }
        ?.value
        ?: return null
    val checkboxRegex = Regex("""(?m)^\s*[-*]\s+\[(x|X| )]\s+""")
    val matches = checkboxRegex.findAll(plan).toList()
    if (matches.isEmpty()) {
        return null
    }
    val completed = matches.count { match -> match.groupValues[1].equals("x", ignoreCase = true) }
    return "Tasks $completed/${matches.size}"
}

private fun formatTokensCompact(value: Long): String {
    val safeValue = value.coerceAtLeast(0L)
    if (safeValue == 0L) {
        return "0"
    }
    if (safeValue < 1_000L) {
        return safeValue.toString()
    }
    val scaled: Double
    val suffix: String
    when {
        safeValue >= 1_000_000_000_000L -> {
            scaled = safeValue / 1_000_000_000_000.0
            suffix = "T"
        }
        safeValue >= 1_000_000_000L -> {
            scaled = safeValue / 1_000_000_000.0
            suffix = "B"
        }
        safeValue >= 1_000_000L -> {
            scaled = safeValue / 1_000_000.0
            suffix = "M"
        }
        else -> {
            scaled = safeValue / 1_000.0
            suffix = "K"
        }
    }
    val decimals = when {
        scaled < 10.0 -> 2
        scaled < 100.0 -> 1
        else -> 0
    }
    val formatted = "%.${decimals}f".format(scaled)
        .trimEnd('0')
        .trimEnd('.')
    return "$formatted$suffix"
}

private fun tokenUsageFromParams(params: JSONObject?): StatusTokenUsage? {
    val tokenUsage = params?.optJSONObject("tokenUsage") ?: return null
    val total = statusTokenBreakdownFromJson(tokenUsage.optJSONObject("total")) ?: return null
    val last = statusTokenBreakdownFromJson(tokenUsage.optJSONObject("last")) ?: StatusTokenBreakdown.Zero
    return StatusTokenUsage(
        total = total,
        last = last,
        modelContextWindow = tokenUsage.optNullableLong("modelContextWindow"),
    )
}

private fun statusTokenBreakdownFromJson(json: JSONObject?): StatusTokenBreakdown? {
    json ?: return null
    return StatusTokenBreakdown(
        totalTokens = json.optLong("totalTokens", 0L),
        inputTokens = json.optLong("inputTokens", 0L),
        cachedInputTokens = json.optLong("cachedInputTokens", 0L),
        outputTokens = json.optLong("outputTokens", 0L),
        reasoningOutputTokens = json.optLong("reasoningOutputTokens", 0L),
    )
}

private fun statusRateLimitStateFromReadResponse(json: JSONObject): StatusRateLimitState? {
    val result = json.optJSONObject("result") ?: return null
    val primary = statusRateLimitSnapshotFromJson(result.optJSONObject("rateLimits"))
    val byLimitIdJson = result.optJSONObject("rateLimitsByLimitId")
    val byLimitId = byLimitIdJson
        ?.keyList()
        ?.mapNotNull { key ->
            statusRateLimitSnapshotFromJson(byLimitIdJson.optJSONObject(key))?.let { key to it }
        }
        ?.toMap()
        .orEmpty()
    return StatusRateLimitState(primary = primary, byLimitId = byLimitId)
}

private fun statusRateLimitStateFromNotification(params: JSONObject?): StatusRateLimitState? {
    val snapshot = statusRateLimitSnapshotFromJson(params?.optJSONObject("rateLimits")) ?: return null
    val key = snapshot.limitId?.takeIf(String::isNotBlank) ?: "codex"
    return StatusRateLimitState(primary = snapshot, byLimitId = mapOf(key to snapshot))
}

private fun statusRateLimitSnapshotFromJson(json: JSONObject?): StatusRateLimitSnapshot? {
    json ?: return null
    return StatusRateLimitSnapshot(
        limitId = json.optNonBlankString("limitId"),
        primary = statusRateLimitWindowFromJson(json.optJSONObject("primary")),
        secondary = statusRateLimitWindowFromJson(json.optJSONObject("secondary")),
    )
}

private fun statusRateLimitWindowFromJson(json: JSONObject?): StatusRateLimitWindow? {
    json ?: return null
    return StatusRateLimitWindow(
        usedPercent = json.optDouble("usedPercent", 0.0),
        windowDurationMins = json.optNullableLong("windowDurationMins"),
    )
}

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return runCatching { optLong(name) }.getOrNull()
}

private fun localStatusSummary(
    json: JSONObject,
    threadId: String?,
    modelOverride: String?,
    reasoningEffortOverride: String?,
    fastServiceTier: Boolean,
    approvalPolicyOverride: String?,
    approvalsReviewerOverride: String?,
    permissionSelectionOverrideJson: String?,
    personalityOverride: String?,
    planModeEnabled: Boolean,
    accountState: AccountPanelState,
): String {
    val config = json.optJSONObject("result")?.optJSONObject("config")
    val configuredModel = config?.optString("model")?.takeIf(String::isNotBlank)
    val configuredReasoning = jsonValueAsString(config?.opt("model_reasoning_effort"))
        ?: jsonValueAsString(config?.opt("modelReasoningEffort"))
    val configuredApproval = jsonValueAsString(config?.opt("approval_policy"))
    val configuredPersonality = jsonValueAsString(config?.opt("personality"))
    val configuredSandbox = jsonValueAsString(config?.opt("sandbox_mode"))
    val configuredReviewer = jsonValueAsString(config?.opt("approvals_reviewer"))
        ?: jsonValueAsString(config?.opt("approvalsReviewer"))
    return listOf(
        "account=${accountStateSummary(accountState)}",
        "thread=${threadId?.let(::shortId) ?: "new"}",
        "model=${modelOverride ?: configuredModel ?: "default"}",
        "thinking=${reasoningEffortOverride ?: configuredReasoning ?: "default"}",
        "tier=${if (fastServiceTier) "fast" else jsonValueAsString(config?.opt("service_tier")) ?: "default"}",
        "approval=${approvalPolicyOverride ?: configuredApproval ?: "default"}",
        "reviewer=${approvalsReviewerOverride ?: configuredReviewer ?: "default"}",
        "permission=${permissionSelectionOverrideJson.permissionSelectionId() ?: ":workspace"}",
        "personality=${personalityOverride ?: configuredPersonality ?: "default"}",
        "sandbox=${configuredSandbox ?: "default"}",
        "mode=${if (planModeEnabled) "plan" else "default"}",
    ).joinToString(" / ")
}

private fun debugConfigSummary(response: JSONObject): String {
    val result = response.optJSONObject("result") ?: return "config/read returned no result"
    val config = result.optJSONObject("config")
    val layers = result.optJSONArray("layers")
    val origins = result.optJSONObject("origins")
    return listOf(
        "configKeys=${config?.length() ?: 0}",
        "origins=${origins?.length() ?: 0}",
        "layers=${layers?.length() ?: 0}",
    ).joinToString(" / ")
}

private fun approvalPolicyFromSlashArg(arg: String): String? = when (arg.trim().lowercase()) {
    "untrusted", "unless-trusted", "unless trusted" -> "untrusted"
    "on-failure", "on_failure", "failure" -> "on-failure"
    "on-request", "on_request", "request" -> "on-request"
    "never" -> "never"
    else -> null
}

private fun reviewTargetFromSlashArgs(args: String): JSONObject {
    val trimmed = args.trim()
    return when {
        trimmed.isBlank() || trimmed.equals("working-tree", ignoreCase = true) ->
            JSONObject().put("type", "uncommittedChanges")

        trimmed.startsWith("base ", ignoreCase = true) ->
            JSONObject()
                .put("type", "baseBranch")
                .put("branch", trimmed.substringAfter(" ").trim())

        trimmed.startsWith("commit ", ignoreCase = true) ->
            JSONObject()
                .put("type", "commit")
                .put("sha", trimmed.substringAfter(" ").trim())
                .put("title", JSONObject.NULL)

        else ->
            JSONObject()
                .put("type", "custom")
                .put("instructions", trimmed)
    }
}

private data class PendingServerRequest(
    val requestId: String,
    val method: String,
    val summary: String,
    val detail: String,
    val expandedDetail: String,
    val scope: String,
    val params: JSONObject?,
) {
    val shortMethod: String
        get() = method.substringAfterLast('/').ifBlank { method }

    val cardKind: String
        get() = when (method) {
            "item/tool/requestUserInput" -> "Decision"
            "item/tool/call" -> "Tool"
            "mcpServer/elicitation/request" -> {
                if (params?.optJSONObject("_meta")?.optNonBlankString("codex_approval_kind") == "mcp_tool_call") {
                    "Approval"
                } else {
                    "Input"
                }
            }
            else -> "Approval"
        }

    val cardSubject: String
        get() = when (method) {
            "mcpServer/elicitation/request" ->
                params?.optJSONObject("_meta")?.optNonBlankString("connector_name")
                    ?: params.optNonBlankString("serverName")
                    ?: "MCP"
            "item/tool/requestUserInput" -> "Plan"
            "item/commandExecution/requestApproval", "execCommandApproval" -> "Command"
            "item/fileChange/requestApproval", "applyPatchApproval" -> "File"
            "item/permissions/requestApproval" -> "Permissions"
            "item/tool/call" -> listOfNotNull(
                params.optNonBlankString("namespace"),
                params.optNonBlankString("tool"),
            ).joinToString(".").ifBlank { "Tool" }
            "account/chatgptAuthTokens/refresh" -> "Account"
            else -> shortMethod
        }

    val canUseQuickActions: Boolean
        get() = method in quickActionServerRequestMethods
}

private const val INTERACTIVE_OTHER_VALUE = "__operator_other__"
private const val INTERACTIVE_OTHER_LABEL = "None of the above"
private const val INTERACTIVE_NOTE_PREFIX = "user_note: "

private enum class InteractiveFormKind {
    RequestUserInput,
    McpElicitation,
}

private data class InteractiveFormSpec(
    val kind: InteractiveFormKind,
    val title: String,
    val message: String,
    val fields: List<InteractiveFieldSpec>,
    val submitLabel: String = "Submit",
    val allowDecline: Boolean = false,
    val allowCancel: Boolean = false,
)

private data class InteractiveFieldSpec(
    val id: String,
    val label: String,
    val description: String,
    val kind: InteractiveFieldKind,
    val required: Boolean,
    val options: List<InteractiveOption> = emptyList(),
    val allowsOther: Boolean = false,
    val inputHint: String = "Type answer",
    val defaultValues: List<String> = emptyList(),
    val notes: InteractiveNotesSpec? = null,
)

private data class InteractiveNotesSpec(
    val hint: String = "Add notes",
    val visibility: InteractiveNotesVisibility = InteractiveNotesVisibility.Always,
)

private enum class InteractiveNotesVisibility {
    Always,
    WhenAnswered,
}

private enum class InteractiveFieldKind {
    Text,
    Secret,
    Choice,
    MultiChoice,
    Boolean,
    Number,
    Integer,
}

private val InteractiveFieldKind.keyboardType: KeyboardType
    get() = when (this) {
        InteractiveFieldKind.Secret -> KeyboardType.Password
        InteractiveFieldKind.Number,
        InteractiveFieldKind.Integer -> KeyboardType.Number
        else -> KeyboardType.Text
    }

private data class InteractiveOption(
    val value: String,
    val label: String,
    val description: String,
)

private data class InteractiveFieldValue(
    val values: List<String> = emptyList(),
    val noteText: String = "",
)

private enum class ServerRequestAction(
    val label: String,
    val decision: String,
    val legacyDecision: String,
) {
    Accept("accept", "accept", "approved"),
    Decline("decline", "decline", "denied"),
    Cancel("cancel", "cancel", "abort"),
}

private val quickActionServerRequestMethods = setOf(
    "item/commandExecution/requestApproval",
    "item/fileChange/requestApproval",
    "item/permissions/requestApproval",
    "mcpServer/elicitation/request",
    "execCommandApproval",
    "applyPatchApproval",
)

private data class RuntimeSummary(
    val eyebrow: String,
    val engine: String,
    val thread: String,
    val account: String,
    val turn: String,
)

private data class MobileStatusBarContext(
    val activeThreadId: String?,
    val activeThreadTitle: String?,
    val activeProjectCwd: String,
    val turnState: TurnPanelState,
    val modelOverride: String?,
    val reasoningEffortOverride: String?,
    val fastServiceTier: Boolean,
    val config: JSONObject?,
    val tokenUsage: StatusTokenUsage?,
    val rateLimits: StatusRateLimitState,
    val gitBranch: String?,
    val taskProgress: String?,
)

private data class StatusTokenUsage(
    val total: StatusTokenBreakdown,
    val last: StatusTokenBreakdown,
    val modelContextWindow: Long?,
)

private data class StatusTokenBreakdown(
    val totalTokens: Long,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
) {
    fun tokensInContextWindow(): Long = totalTokens

    companion object {
        val Zero = StatusTokenBreakdown(
            totalTokens = 0L,
            inputTokens = 0L,
            cachedInputTokens = 0L,
            outputTokens = 0L,
            reasoningOutputTokens = 0L,
        )
    }
}

private data class StatusRateLimitState(
    val primary: StatusRateLimitSnapshot? = null,
    val byLimitId: Map<String, StatusRateLimitSnapshot> = emptyMap(),
)

private data class StatusRateLimitSnapshot(
    val limitId: String?,
    val primary: StatusRateLimitWindow?,
    val secondary: StatusRateLimitWindow?,
)

private data class StatusRateLimitWindow(
    val usedPercent: Double,
    val windowDurationMins: Long?,
)

private enum class MobileStatusBarItem(
    val id: String,
    val label: String,
    val description: String,
    val placeholder: String,
) {
    ModelName(
        id = "model",
        label = "model",
        description = "Current model name",
        placeholder = "gpt-5.2-codex",
    ),
    ModelWithReasoning(
        id = "model-with-reasoning",
        label = "model + thinking",
        description = "Current model name with reasoning level",
        placeholder = "gpt-5.2-codex medium",
    ),
    CurrentDir(
        id = "current-dir",
        label = "current dir",
        description = "Current working directory",
        placeholder = "~/my-project/subdir",
    ),
    ProjectRoot(
        id = "project-name",
        label = "project",
        description = "Project name (omitted when unavailable)",
        placeholder = "my-project",
    ),
    GitBranch(
        id = "git-branch",
        label = "git branch",
        description = "Current Git branch (omitted when unavailable)",
        placeholder = "feat/awesome-feature",
    ),
    RunState(
        id = "run-state",
        label = "run state",
        description = "Compact session run-state text (Ready, Working, Thinking)",
        placeholder = "Working",
    ),
    ContextRemaining(
        id = "context-remaining",
        label = "context left",
        description = "Percentage of context window remaining (omitted when unknown)",
        placeholder = "Context 100% left",
    ),
    ContextUsed(
        id = "context-used",
        label = "context used",
        description = "Percentage of context window used (omitted when unknown)",
        placeholder = "Context 0% used",
    ),
    FiveHourLimit(
        id = "five-hour-limit",
        label = "5h limit",
        description = "Remaining usage on 5-hour usage limit (omitted when unavailable)",
        placeholder = "5h 100%",
    ),
    WeeklyLimit(
        id = "weekly-limit",
        label = "weekly limit",
        description = "Remaining usage on weekly usage limit (omitted when unavailable)",
        placeholder = "weekly 100%",
    ),
    CodexVersion(
        id = "codex-version",
        label = "version",
        description = "Codex application version",
        placeholder = "0.1.0",
    ),
    ContextWindowSize(
        id = "context-window-size",
        label = "context window",
        description = "Total context window size in tokens (omitted when unknown)",
        placeholder = "200K window",
    ),
    UsedTokens(
        id = "used-tokens",
        label = "used tokens",
        description = "Total tokens used in session (omitted when zero)",
        placeholder = "12K used",
    ),
    TotalInputTokens(
        id = "total-input-tokens",
        label = "input tokens",
        description = "Total input tokens used in session",
        placeholder = "10K in",
    ),
    TotalOutputTokens(
        id = "total-output-tokens",
        label = "output tokens",
        description = "Total output tokens generated in session",
        placeholder = "2K out",
    ),
    SessionId(
        id = "session-id",
        label = "session id",
        description = "Current session identifier (omitted until session starts)",
        placeholder = "550e8400-e29b-41d4",
    ),
    FastMode(
        id = "fast-mode",
        label = "fast mode",
        description = "Whether Fast mode is currently active",
        placeholder = "Fast on",
    ),
    ThreadTitle(
        id = "thread-title",
        label = "thread title",
        description = "Current thread title (omitted when unavailable)",
        placeholder = "thread title",
    ),
    TaskProgress(
        id = "task-progress",
        label = "task progress",
        description = "Latest task progress from update_plan (omitted until available)",
        placeholder = "Tasks 2/4",
    );

    companion object {
        fun fromId(value: String): MobileStatusBarItem? =
            when (value.trim()) {
                "model", "model-name" -> ModelName
                "model-with-reasoning" -> ModelWithReasoning
                "current-dir" -> CurrentDir
                "project", "project-root", "project-name" -> ProjectRoot
                "git-branch" -> GitBranch
                "status", "run-state" -> RunState
                "context-remaining" -> ContextRemaining
                "context-usage", "context-used" -> ContextUsed
                "five-hour-limit" -> FiveHourLimit
                "weekly-limit" -> WeeklyLimit
                "codex-version" -> CodexVersion
                "context-window-size" -> ContextWindowSize
                "used-tokens" -> UsedTokens
                "total-input-tokens" -> TotalInputTokens
                "total-output-tokens" -> TotalOutputTokens
                "session-id" -> SessionId
                "fast-mode" -> FastMode
                "thread", "thread-title" -> ThreadTitle
                "task-progress" -> TaskProgress
                else -> null
            }
    }
}

private const val MAX_CHAT_IMAGE_ATTACHMENTS = 8

private data class PendingImageAttachment(
    val id: String,
    val name: String,
    val path: String,
)

private fun copyImageAttachmentToAppStorage(
    context: Context,
    appFilesDir: String,
    uri: Uri,
): PendingImageAttachment {
    val resolver = context.contentResolver
    val displayName = imageDisplayName(context, uri)
    val extension = imageExtension(context, uri, displayName)
    val safeName = attachmentFileName(displayName, extension)
    val directory = File(appFilesDir, "attachments/images").apply {
        if (!exists() && !mkdirs()) {
            throw IllegalStateException("could not create image attachment directory")
        }
    }
    val target = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}-$safeName")
    resolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw IllegalArgumentException("could not read selected image")

    return PendingImageAttachment(
        id = UUID.randomUUID().toString(),
        name = displayName?.takeIf(String::isNotBlank) ?: safeName,
        path = target.absolutePath,
    )
}

private fun imageDisplayName(context: Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            cursor.getString(index)
        } else {
            null
        }
    }
}

private fun imageExtension(
    context: Context,
    uri: Uri,
    displayName: String?,
): String {
    val nameExtension = displayName
        ?.substringAfterLast('.', "")
        ?.lowercase()
        ?.takeIf { value -> value.length in 1..8 && value.all { it.isLetterOrDigit() } }
    if (nameExtension != null) {
        return nameExtension
    }

    return context.contentResolver.getType(uri)
        ?.let { type -> MimeTypeMap.getSingleton().getExtensionFromMimeType(type) }
        ?.lowercase()
        ?.takeIf { value -> value.length in 1..8 && value.all { it.isLetterOrDigit() } }
        ?: "img"
}

private fun attachmentFileName(displayName: String?, extension: String): String {
    val base = displayName
        ?.takeIf(String::isNotBlank)
        ?: "image.$extension"
    val safe = base
        .map { char ->
            if (char.isLetterOrDigit() || char == '.' || char == '-' || char == '_') char else '_'
        }
        .joinToString("")
        .trim('_', '.', '-')
        .take(80)
        .ifBlank { "image.$extension" }
    return if (safe.contains('.') && safe.substringAfterLast('.', "").isNotBlank()) {
        safe
    } else {
        "$safe.$extension"
    }
}

private fun userSubmissionSummary(
    text: String,
    attachments: List<PendingImageAttachment>,
): String {
    val parts = mutableListOf<String>()
    text.takeIf(String::isNotBlank)?.let(parts::add)
    attachments.forEachIndexed { index, attachment ->
        parts += "Image ${index + 1}: ${attachment.name}"
    }
    return parts.joinToString("\n").ifBlank { "image attachment" }
}

private fun workspaceEyebrow(
    accountState: AccountPanelState,
    turnState: TurnPanelState,
): String = when {
    accountState is AccountPanelState.LoginPending -> "device code"
    accountState is AccountPanelState.SignedIn && turnState.isActiveTurn -> "turn/running"
    accountState is AccountPanelState.SignedIn -> "ready"
    accountState is AccountPanelState.Error -> "error"
    accountState is AccountPanelState.Loading -> "account/read"
    else -> "no auth"
}

private fun transcriptRowsFromThread(thread: JSONObject): List<TranscriptRow> {
    val turns = thread.optJSONArray("turns") ?: return emptyList()
    return (0 until turns.length())
        .mapNotNull { index -> turns.optJSONObject(index) }
        .sortedWith(
            compareBy<JSONObject> { turn ->
                turn.optLong("startedAt", 0L).takeIf { it > 0L } ?: Long.MAX_VALUE
            }.thenBy { turn -> turn.optString("id") }
        )
        .flatMap(::transcriptRowsFromTurn)
}

private fun transcriptRowsFromTurn(turn: JSONObject): List<TranscriptRow> {
    val items = turn.optJSONArray("items") ?: JSONArray()
    val rows = (0 until items.length()).mapNotNull { index ->
        val item = items.optJSONObject(index) ?: return@mapNotNull null
        when (item.optString("type")) {
            "userMessage" -> TranscriptRow(
                label = "you",
                value = userMessageSummary(item).ifBlank { "user message" },
                detail = jsonDetail(item),
                kind = TimelineKind.User,
            )
            "agentMessage" -> item.optNonBlankString("text")?.let { text ->
                TranscriptRow(
                    label = "codex",
                    value = text,
                    detail = jsonDetail(item),
                    kind = TimelineKind.Assistant,
                )
            }
            else -> completedItemRow(item)
        }
    }
    val status = turn.optNonBlankString("status").orEmpty()
    if (status.isBlank() || status == "completed" || status == "inProgress") {
        return rows
    }
    return rows + TranscriptRow(
        label = "turn",
        value = status,
        detail = jsonDetail(turn),
        kind = if (status == "failed") TimelineKind.Error else TimelineKind.System,
    )
}

private fun userMessageSummary(item: JSONObject): String {
    val content = item.optJSONArray("content") ?: return ""
    return (0 until content.length())
        .mapNotNull { index ->
            val input = content.optJSONObject(index)
                ?: return@mapNotNull jsonValueAsString(content.opt(index))
            when (input.optString("type")) {
                "text" -> input.optNonBlankString("text")
                "image" -> input.optNonBlankString("url")?.let { "image: $it" } ?: "image"
                "localImage" -> input.optNonBlankString("path")?.let { "image: $it" } ?: "local image"
                "skill" -> input.optNonBlankString("name")?.let { "skill: $it" } ?: "skill"
                "mention" -> input.optNonBlankString("name") ?: input.optNonBlankString("path")
                else -> input.optNonBlankString("text")
                    ?: input.optNonBlankString("name")
                    ?: input.optNonBlankString("path")
                    ?: input.optNonBlankString("type")
            }
        }
        .joinToString("\n")
}

private fun completedItemRow(item: JSONObject?): TranscriptRow? {
    item ?: return null
    val type = item.optString("type").takeIf(String::isNotBlank) ?: return null
    val detail = jsonDetail(item)
    return when (type) {
        "userMessage", "agentMessage" -> null
        "hookPrompt" -> TranscriptRow(
            label = "hook prompt",
            value = hookPromptSummary(item).ifBlank { "hook prompt" },
            detail = detail,
            kind = TimelineKind.Tool,
        )
        "plan" -> TranscriptRow(
            label = "plan",
            value = item.optNonBlankString("text") ?: "plan updated",
            detail = detail,
            kind = TimelineKind.Thinking,
        )
        "reasoning" -> reasoningSummary(item)
            .takeIf(String::isNotBlank)
            ?.let { summary ->
                TranscriptRow(
                    label = "thinking",
                    value = summary,
                    detail = detail,
                    kind = TimelineKind.Thinking,
                )
            }
        "commandExecution" -> TranscriptRow(
            label = "command",
            value = commandExecutionSummary(item),
            detail = commandExecutionDetail(item),
            kind = if (item.optString("status") == "failed") TimelineKind.Error else TimelineKind.Tool,
        )
        "fileChange" -> TranscriptRow(
            label = "file change",
            value = fileChangeSummary(item),
            detail = fileChangeDetail(item),
            kind = if (item.optString("status") == "failed") TimelineKind.Error else TimelineKind.Tool,
        )
        "mcpToolCall" -> TranscriptRow(
            label = "mcp tool",
            value = toolCallSummary(item, includeServer = true),
            detail = detail,
            kind = if (item.optString("status") == "failed") TimelineKind.Error else TimelineKind.Tool,
        )
        "dynamicToolCall" -> TranscriptRow(
            label = "tool",
            value = toolCallSummary(item, includeServer = false),
            detail = detail,
            kind = if (item.optString("status") == "failed") TimelineKind.Error else TimelineKind.Tool,
        )
        "collabAgentToolCall" -> TranscriptRow(
            label = "agent tool",
            value = collabToolSummary(item),
            detail = detail,
            kind = if (item.optString("status") == "failed") TimelineKind.Error else TimelineKind.Tool,
        )
        "webSearch" -> TranscriptRow(
            label = "web search",
            value = item.optNonBlankString("query") ?: "web search",
            detail = detail,
            kind = TimelineKind.Tool,
        )
        "imageView" -> TranscriptRow(
            label = "image",
            value = item.optNonBlankString("path") ?: "image opened",
            detail = detail,
            kind = TimelineKind.Tool,
        )
        "imageGeneration" -> TranscriptRow(
            label = "image generation",
            value = listOfNotNull(
                item.optNonBlankString("status"),
                item.optNonBlankString("savedPath") ?: item.optNonBlankString("result"),
            ).joinToString(" / ").ifBlank { "image generation" },
            detail = detail,
            kind = if (item.optString("status") == "failed") TimelineKind.Error else TimelineKind.Tool,
        )
        "enteredReviewMode" -> TranscriptRow(
            label = "review mode",
            value = item.optNonBlankString("review") ?: "entered review mode",
            detail = detail,
            kind = TimelineKind.System,
        )
        "exitedReviewMode" -> TranscriptRow(
            label = "review mode",
            value = item.optNonBlankString("review") ?: "exited review mode",
            detail = detail,
            kind = TimelineKind.System,
        )
        "contextCompaction" -> TranscriptRow(
            label = "context",
            value = "context compacted",
            detail = detail,
            kind = TimelineKind.System,
        )
        else -> TranscriptRow(
            label = type,
            value = item.optNonBlankString("status") ?: type,
            detail = detail,
            kind = TimelineKind.System,
        )
    }
}

private fun hookPromptSummary(item: JSONObject): String =
    item.optJSONArray("fragments")
        ?.let { fragments ->
            (0 until fragments.length()).mapNotNull { index ->
                val fragment = fragments.optJSONObject(index) ?: return@mapNotNull null
                fragment.optNonBlankString("message")
                    ?: fragment.optNonBlankString("command")
                    ?: fragment.optNonBlankString("type")
            }.joinToString("\n")
        }
        .orEmpty()

private fun reasoningSummary(item: JSONObject): String {
    val summary = item.optJSONArray("summary")?.stringItems().orEmpty()
    val content = item.optJSONArray("content")?.stringItems().orEmpty()
    return (summary.ifEmpty { content })
        .joinToString("\n")
        .take(800)
}

private fun commandExecutionSummary(item: JSONObject): String {
    val command = item.optNonBlankString("command") ?: "command"
    val metadata = listOfNotNull(
        item.optNonBlankString("status"),
        jsonValueAsString(item.opt("exitCode"))?.let { "exit $it" },
        jsonValueAsString(item.opt("durationMs"))?.let { "${it}ms" },
    ).joinToString(" / ")
    return listOf(command, metadata)
        .filter(String::isNotBlank)
        .joinToString("\n")
}

private fun commandExecutionDetail(item: JSONObject): String =
    listOfNotNull(
        item.optNonBlankString("cwd")?.let { "cwd: $it" },
        item.optNonBlankString("source")?.let { "source: $it" },
        item.optNonBlankString("aggregatedOutput"),
        jsonDetail(item),
    ).joinToString("\n\n")

private fun fileChangeSummary(item: JSONObject): String {
    val status = item.optNonBlankString("status")
    val changes = item.optJSONArray("changes") ?: JSONArray()
    val paths = (0 until changes.length()).mapNotNull { index ->
        val change = changes.optJSONObject(index) ?: return@mapNotNull null
        val kind = change.optJSONObject("kind")?.optString("type")
            ?: change.optString("kind").takeIf(String::isNotBlank)
        val path = change.optNonBlankString("path")
        listOfNotNull(kind, path).joinToString(" ").takeIf(String::isNotBlank)
    }
    return listOfNotNull(status, paths.joinToString(", ").takeIf(String::isNotBlank))
        .joinToString(" / ")
        .ifBlank { "file change" }
}

private fun fileChangeDetail(item: JSONObject): String {
    val changes = item.optJSONArray("changes") ?: return jsonDetail(item)
    val rendered = (0 until changes.length()).mapNotNull { index ->
        val change = changes.optJSONObject(index) ?: return@mapNotNull null
        listOfNotNull(
            change.optNonBlankString("path"),
            change.optJSONObject("kind")?.optString("type")?.takeIf(String::isNotBlank),
            change.optNonBlankString("diff"),
        ).joinToString("\n").takeIf(String::isNotBlank)
    }.joinToString("\n\n")
    return listOf(rendered, jsonDetail(item))
        .filter(String::isNotBlank)
        .joinToString("\n\n")
}

private fun toolCallSummary(item: JSONObject, includeServer: Boolean): String {
    val toolName = listOfNotNull(
        item.optNonBlankString("server").takeIf { includeServer },
        item.optNonBlankString("namespace"),
        item.optNonBlankString("tool"),
    ).joinToString(".").ifBlank { "tool" }
    val status = item.optNonBlankString("status")
    val success = jsonValueAsString(item.opt("success"))?.let { "success=$it" }
    val duration = jsonValueAsString(item.opt("durationMs"))?.let { "${it}ms" }
    return listOf(toolName, listOfNotNull(status, success, duration).joinToString(" / "))
        .filter(String::isNotBlank)
        .joinToString("\n")
}

private fun collabToolSummary(item: JSONObject): String =
    listOfNotNull(
        item.optNonBlankString("tool"),
        item.optNonBlankString("status"),
        item.optJSONArray("receiverThreadIds")?.let { "receivers=${it.length()}" },
    ).joinToString(" / ").ifBlank { "agent tool call" }

private fun jsonDetail(value: JSONObject): String =
    runCatching { value.toString(2) }.getOrElse { value.toString() }

private fun accountPanelStateFromReadResponse(response: String): AccountPanelState {
    val json = JSONObject(response)
    if (!json.optBoolean("ok")) {
        throw IllegalStateException(json.optErrorMessage("account/read failed"))
    }

    val result = json.optJSONObject("result")
        ?: throw IllegalStateException("account/read returned no result")
    val account = result.optJSONObject("account")
    val requiresOpenaiAuth = result.optBoolean("requiresOpenaiAuth", true)

    if (account == null) {
        val detail = if (requiresOpenaiAuth) {
            "OpenAI auth required"
        } else {
            "OpenAI auth not required for the active provider"
        }
        return AccountPanelState.SignedOut(detail)
    }

    val type = account.optString("type").takeIf(String::isNotBlank) ?: "account"
    val email = account.optString("email").takeIf(String::isNotBlank)
    val planType = account.optString("planType").takeIf(String::isNotBlank)
    val label = email ?: type
    val detail = listOf(type, planType)
        .filterNotNull()
        .joinToString(" / ")
        .ifBlank { "authenticated" }
    return AccountPanelState.SignedIn(label, detail)
}

private fun accountPanelStateFromLoginStartResponse(response: String): AccountPanelState.LoginPending {
    val json = JSONObject(response)
    if (!json.optBoolean("ok")) {
        throw IllegalStateException(json.optErrorMessage("account/login/start failed"))
    }

    val result = json.optJSONObject("result")
        ?: throw IllegalStateException("account/login/start returned no result")
    if (result.optString("type") != "chatgptDeviceCode") {
        throw IllegalStateException("account/login/start returned ${result.optString("type")}")
    }

    val loginId = result.optString("loginId")
    val verificationUrl = result.optString("verificationUrl")
    val userCode = result.optString("userCode")
    if (loginId.isBlank() || verificationUrl.isBlank() || userCode.isBlank()) {
        throw IllegalStateException("account/login/start returned incomplete device code data")
    }

    return AccountPanelState.LoginPending(
        loginId = loginId,
        verificationUrl = verificationUrl,
        userCode = userCode,
    )
}

private fun accountLoginCompletedPayload(event: JSONObject?): JSONObject? =
    event?.optJSONObject("payload")?.let { payload ->
        payload.optJSONObject("account/login/completed")
            ?: payload.optJSONObject("params")
            ?: payload
    }
        ?: event?.optJSONObject("params")

private fun threadSummariesFromListResponse(response: String): List<ThreadSummary> {
    val json = JSONObject(response)
    if (!json.optBoolean("ok")) {
        throw IllegalStateException(json.optErrorMessage("thread/list failed"))
    }

    val result = json.optJSONObject("result")
        ?: throw IllegalStateException("thread/list returned no result")
    val data = result.optJSONArray("data") ?: JSONArray()
    return (0 until data.length())
        .mapNotNull { index -> data.optJSONObject(index)?.let(::threadSummaryFromJson) }
}

private fun threadSummaryFromJson(thread: JSONObject): ThreadSummary {
    val id = thread.optString("id").takeIf(String::isNotBlank)
        ?: return ThreadSummary(
            id = "",
            title = "unknown thread",
            detail = "thread/list returned no id",
        )
    val title = threadTitleFromFirstMessage(thread, id)
    val status = threadStatusLabel(thread.opt("status"))
    val updatedAt = thread.optLong("updatedAt", 0L).takeIf { it > 0L }
    val cwd = thread.optString("cwd").takeIf(String::isNotBlank)
    val source = sessionSourceLabel(thread.opt("source"))
    val forkedFromId = thread.optString("forkedFromId").takeIf(String::isNotBlank)
    val ephemeral = thread.optBoolean("ephemeral", false)
    val detail = listOfNotNull(
        status,
        updatedAt?.let(::relativeThreadTime),
        source,
        cwd?.let(::projectNameFromCwd),
    ).joinToString(" / ")

    return ThreadSummary(
        id = id,
        title = title,
        detail = detail.ifBlank { shortId(id) },
        cwd = cwd,
        projectName = projectNameFromCwd(cwd),
        status = status,
        updatedAt = updatedAt,
        source = source,
        ephemeral = ephemeral,
        forkedFromId = forkedFromId,
    )
}

private fun threadTitleFromFirstMessage(thread: JSONObject, id: String): String =
    listOf(
        thread.optString("preview"),
        thread.optString("firstUserMessage"),
        thread.optString("first_user_message"),
        thread.optString("name"),
    )
        .firstNotNullOfOrNull { value ->
            value
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf(String::isNotBlank)
        }
        ?.let(::cutThreadTitleExcerpt)
        ?: "Thread ${shortId(id)}"

private fun cutThreadTitleExcerpt(value: String, maxLength: Int = 72): String {
    if (value.length <= maxLength) {
        return value
    }
    return value.take(maxLength).trimEnd() + "..."
}

private fun threadStatusLabel(status: Any?): String = when (status) {
    is String -> status
    is JSONObject -> status.optString("type").takeIf(String::isNotBlank) ?: "status"
    else -> "notLoaded"
}

private fun sessionProjectsFromSources(
    threads: List<ThreadSummary>,
    projectFolders: List<String>,
): List<SessionProjectSummary> {
    val threadsByCwd = threads
        .mapNotNull { thread -> thread.cwd?.takeIf(String::isNotBlank)?.let { cwd -> cwd to thread } }
        .groupBy({ it.first }, { it.second })
    return (projectFolders + threadsByCwd.keys)
        .distinct()
        .map { cwd ->
            val projectThreads = threadsByCwd[cwd].orEmpty()
            SessionProjectSummary(
                cwd = cwd,
                name = projectNameFromCwd(cwd),
                threadCount = projectThreads.size,
                updatedAt = projectThreads.mapNotNull(ThreadSummary::updatedAt).maxOrNull(),
            )
        }
        .sortedWith(
            compareByDescending<SessionProjectSummary> { it.updatedAt ?: 0L }
                .thenBy { it.name.lowercase() }
        )
}

private fun sessionProjectsFromThreads(threads: List<ThreadSummary>): List<SessionProjectSummary> =
    sessionProjectsFromSources(threads, emptyList())

private const val NO_PROJECT_SECTION_KEY = "__operator_no_project__"

private fun sessionProjectSections(
    projects: List<SessionProjectSummary>,
    threads: List<ThreadSummary>,
    query: String,
): List<SessionProjectSection> {
    val normalizedQuery = query.trim().lowercase()
    val projectsByCwd = projects.associateBy(SessionProjectSummary::cwd)
    val threadsByCwd = threads.groupBy { thread ->
        thread.cwd?.takeIf(String::isNotBlank) ?: NO_PROJECT_SECTION_KEY
    }
    val keys = (projects.map(SessionProjectSummary::cwd) + threadsByCwd.keys)
        .distinct()

    return keys
        .mapNotNull { key ->
            val cwd = key.takeUnless { it == NO_PROJECT_SECTION_KEY }
            val project = cwd?.let(projectsByCwd::get)
            val projectThreads = threadsByCwd[key].orEmpty()
                .sortedWith(
                    compareByDescending<ThreadSummary> { it.updatedAt ?: 0L }
                        .thenBy { it.title.lowercase() }
                )
            if (cwd == null && projectThreads.isEmpty()) {
                return@mapNotNull null
            }
            val name = when {
                cwd == null -> "No Project"
                project != null -> project.name
                else -> projectNameFromCwd(cwd)
            }
            val projectMatchesQuery = normalizedQuery.isBlank() ||
                name.lowercase().contains(normalizedQuery) ||
                cwd?.lowercase()?.contains(normalizedQuery) == true
            val visibleThreads = if (projectMatchesQuery) {
                projectThreads
            } else {
                projectThreads.filter { thread -> thread.matchesSessionQuery(normalizedQuery) }
            }
            if (
                normalizedQuery.isNotBlank() &&
                !projectMatchesQuery &&
                visibleThreads.isEmpty()
            ) {
                return@mapNotNull null
            }
            if (normalizedQuery.isBlank() && project == null && projectThreads.isEmpty()) {
                return@mapNotNull null
            }
            SessionProjectSection(
                key = key,
                cwd = cwd,
                name = name,
                threadCount = visibleThreads.size,
                updatedAt = project?.updatedAt
                    ?: visibleThreads.mapNotNull(ThreadSummary::updatedAt).maxOrNull(),
                threads = visibleThreads,
            )
        }
        .sortedWith(
            compareByDescending<SessionProjectSection> { it.updatedAt ?: 0L }
                .thenBy { it.name.lowercase() }
        )
}

private fun ThreadSummary.matchesSessionQuery(normalizedQuery: String): Boolean =
    normalizedQuery.isBlank() ||
        listOfNotNull(
            title,
            detail,
            cwd,
            projectName,
            source,
            id,
        ).any { value -> value.lowercase().contains(normalizedQuery) }

private fun SharedPreferences.nonBlankString(key: String): String? =
    getString(key, null)
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun SharedPreferences.Editor.putNullableString(
    key: String,
    value: String?,
): SharedPreferences.Editor {
    if (value.isNullOrBlank()) {
        remove(key)
    } else {
        putString(key, value)
    }
    return this
}

private fun restoredWorkspaceCwd(
    workspacesRoot: String,
    defaultProjectCwd: String,
    storedCwd: String?,
): String =
    restoredWorkspaceCwdOrNull(workspacesRoot, storedCwd) ?: defaultProjectCwd

private fun restoredWorkspaceCwdOrNull(
    workspacesRoot: String,
    storedCwd: String?,
): String? {
    val rawCwd = storedCwd
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val rootPath = File(workspacesRoot).absoluteFile.normalizePath()
    val cwdPath = File(rawCwd).absoluteFile.normalizePath()
    return cwdPath.takeIf { it == rootPath || it.startsWith("$rootPath/") }
}

private fun File.normalizePath(): String =
    absolutePath.trimEnd('/')

private fun ensureProjectDirectories(
    workspacesRoot: String,
    defaultProjectCwd: String,
): List<String> {
    ensureProjectDirectory(defaultProjectCwd)
    val defaultProject = File(defaultProjectCwd)
    val projectsRoot = File(workspacesRoot, "projects")
    ensureProjectDirectory(projectsRoot.absolutePath)
    return (listOf(defaultProject) + projectsRoot.listProjectDirectories())
        .map { it.absolutePath }
        .distinct()
}

private fun createNamedProjectDirectory(
    workspacesRoot: String,
    directoryName: String,
): String {
    val projectsRoot = File(workspacesRoot, "projects")
    ensureProjectDirectory(projectsRoot.absolutePath)
    if (directoryName in RESERVED_PROJECT_DIRECTORY_NAMES) {
        throw IllegalArgumentException("Choose a different project name.")
    }
    val directory = File(projectsRoot, directoryName)
    if (directory.exists()) {
        throw IllegalStateException("Project ${projectNameFromCwd(directory.absolutePath)} already exists.")
    }
    if (!directory.mkdirs()) {
        throw IllegalStateException("failed to create ${directory.absolutePath}")
    }
    return directory.absolutePath
}

private fun ensureProjectDirectory(cwd: String) {
    val directory = File(cwd)
    if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
        throw IllegalStateException("failed to create ${directory.absolutePath}")
    }
    if (!directory.isDirectory) {
        throw IllegalStateException("${directory.absolutePath} is not a directory")
    }
}

private fun File.listProjectDirectories(): List<File> =
    listFiles()
        ?.filter { file -> file.isDirectory && !file.name.startsWith(".") }
        ?.sortedBy { file -> file.name.lowercase() }
        .orEmpty()

private val RESERVED_PROJECT_DIRECTORY_NAMES = setOf("default", "projects")

private fun projectDirectoryNameCandidate(rawName: String): String? {
    val candidate = rawName
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9._ -]+"), " ")
        .replace(Regex("[\\s_]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-', '.', ' ')
        .take(48)
        .trim('-', '.', ' ')
    return candidate
        .takeIf(String::isNotBlank)
        ?.takeIf { it !in RESERVED_PROJECT_DIRECTORY_NAMES }
}

private fun filterThreadsForSessionDrawer(
    threads: List<ThreadSummary>,
    selectedProjectCwd: String?,
    query: String,
): List<ThreadSummary> {
    val normalizedQuery = query.trim().lowercase()
    return threads
        .filter { thread -> selectedProjectCwd == null || thread.cwd == selectedProjectCwd }
        .filter { thread -> thread.matchesSessionQuery(normalizedQuery) }
}

private fun sessionListEyebrow(
    state: ThreadListState,
    visibleCount: Int,
): String = when (state) {
    ThreadListState.Loading -> state.eyebrow
    ThreadListState.Ready -> "$visibleCount saved"
    is ThreadListState.Error -> state.eyebrow
}

private fun projectNameFromCwd(cwd: String?): String {
    val normalized = cwd
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf(String::isNotBlank)
        ?: return "workspace"
    val leaf = normalized.substringAfterLast('/').ifBlank { normalized }
    if (leaf == "default") {
        return "Default"
    }
    return leaf
        .replace('-', ' ')
        .replace('_', ' ')
        .split(Regex("\\s+"))
        .joinToString(" ") { part ->
            part.replaceFirstChar { char -> char.uppercase() }
        }
        .ifBlank { leaf }
}

private fun relativeThreadTime(updatedAtSeconds: Long): String {
    val ageSeconds = ((System.currentTimeMillis() / 1000L) - updatedAtSeconds).coerceAtLeast(0L)
    return when {
        ageSeconds < 60L -> "just now"
        ageSeconds < 60L * 60L -> "${ageSeconds / 60L}m ago"
        ageSeconds < 60L * 60L * 24L -> "${ageSeconds / (60L * 60L)}h ago"
        ageSeconds < 60L * 60L * 24L * 30L -> "${ageSeconds / (60L * 60L * 24L)}d ago"
        else -> "${ageSeconds / (60L * 60L * 24L * 30L)}mo ago"
    }
}

private fun sessionSourceLabel(source: Any?): String? = when (source) {
    is String -> source
    is JSONObject -> source.optString("custom").takeIf(String::isNotBlank)
        ?: source.optJSONObject("subAgent")?.optString("type")?.takeIf(String::isNotBlank)
        ?: "subAgent".takeIf { source.has("subAgent") }
    else -> null
}

private fun pendingServerRequestFromEvent(event: JSONObject?): PendingServerRequest? {
    val payload = event?.optJSONObject("payload")
    val params = payload?.optJSONObject("params") ?: event?.optJSONObject("params")
    val requestId = jsonValueAsString(event?.opt("requestId"))
        ?: jsonValueAsString(payload?.opt("id"))
        ?: return null
    val method = event?.optString("method").takeIf { !it.isNullOrBlank() }
        ?: payload?.optString("method").takeIf { !it.isNullOrBlank() }
        ?: "server.request"

    return PendingServerRequest(
        requestId = requestId,
        method = method,
        summary = serverRequestSummary(method, params),
        detail = serverRequestDetail(method, params),
        expandedDetail = serverRequestExpandedDetail(method, params),
        scope = serverRequestScope(params),
        params = params,
    )
}

private fun notificationTranscriptRow(
    method: String,
    params: JSONObject?,
    detail: String,
): TranscriptRow {
    val error = params?.optJSONObject("error")
    val message = error.optNonBlankString("message")
        ?: params.optNonBlankString("message")
        ?: method
    if (method == "error" && params.isRetryingStreamDisconnect()) {
        return TranscriptRow(
            label = "reconnecting",
            value = message.ifBlank { "Reconnecting..." },
            detail = detail,
            kind = TimelineKind.Status,
        )
    }
    return TranscriptRow(
        label = when (method) {
            "guardianWarning" -> "guardian"
            else -> method.ifBlank { "event" }
        },
        value = message,
        detail = detail,
        kind = if (method == "error") TimelineKind.Error else TimelineKind.System,
    )
}

private fun JSONObject?.isRetryingStreamDisconnect(): Boolean {
    this ?: return false
    if (!optBoolean("willRetry", false)) {
        return false
    }
    val info = optJSONObject("error")?.opt("codexErrorInfo")
    return when (info) {
        is String -> info == "responseStreamDisconnected" || info == "responseStreamConnectionFailed"
        is JSONObject -> info.has("responseStreamDisconnected") || info.has("responseStreamConnectionFailed")
        else -> false
    }
}

private fun serverRequestSummary(method: String, params: JSONObject?): String = when (method) {
    "item/commandExecution/requestApproval" -> commandDisplay(params)
        ?: params.optNonBlankString("reason")
        ?: "command approval"
    "execCommandApproval" -> commandDisplay(params)
        ?: params.optNonBlankString("reason")
        ?: "legacy command approval"
    "item/fileChange/requestApproval", "applyPatchApproval" ->
        params.optNonBlankString("reason")
            ?: params.optNonBlankString("grantRoot")?.let { "file access for $it" }
            ?: "file change approval"
    "item/permissions/requestApproval" ->
        params.optNonBlankString("reason")
            ?: permissionSummary(params?.optJSONObject("permissions"))
            ?: "permission request"
    "item/tool/requestUserInput" ->
        params?.optJSONArray("questions")
            ?.optJSONObject(0)
            ?.let { question ->
                question.optNonBlankString("question")
                    ?: question.optNonBlankString("header")
                    ?: question.optNonBlankString("id")
            }
            ?: "user input request"
    "mcpServer/elicitation/request" ->
        mcpToolApprovalHeadline(params)
            ?: params.optNonBlankString("message")
            ?: params.optNonBlankString("url")
            ?: params.optNonBlankString("serverName")?.let { "$it request" }
            ?: "MCP elicitation"
    "item/tool/call" ->
        listOfNotNull(
            params.optNonBlankString("namespace"),
            params.optNonBlankString("tool"),
        ).joinToString(".").ifBlank { "dynamic tool call" }
    "account/chatgptAuthTokens/refresh" -> "refresh ChatGPT auth tokens"
    else -> params.optNonBlankString("reason") ?: method
}

private fun serverRequestDetail(method: String, params: JSONObject?): String {
    val detail = when (method) {
        "item/commandExecution/requestApproval", "execCommandApproval" -> listOfNotNull(
            params.optNonBlankString("cwd"),
            params.optNonBlankString("reason"),
        )
        "item/fileChange/requestApproval", "applyPatchApproval" -> listOfNotNull(
            params.optNonBlankString("grantRoot"),
            params.optNonBlankString("reason"),
        )
        "item/permissions/requestApproval" -> listOfNotNull(
            params.optNonBlankString("cwd"),
            permissionSummary(params?.optJSONObject("permissions")),
        )
        "item/tool/requestUserInput" -> userInputQuestionHeaders(params)
        "mcpServer/elicitation/request" -> mcpElicitationDetailLines(params)
        else -> listOfNotNull(
            params.optNonBlankString("threadId") ?: params.optNonBlankString("conversationId"),
            params.optNonBlankString("turnId"),
            params.optNonBlankString("itemId") ?: params.optNonBlankString("callId"),
        )
    }
    return detail.distinct().joinToString("\n")
}

private fun serverRequestExpandedDetail(method: String, params: JSONObject?): String =
    when (method) {
        "mcpServer/elicitation/request" ->
            mcpElicitationExpandedDetailLines(params).distinct().joinToString("\n")
        "item/commandExecution/requestApproval", "execCommandApproval" ->
            listOfNotNull(
                commandDisplay(params)?.let { "Command: $it" },
                params.optNonBlankString("cwd")?.let { "cwd: $it" },
                params.optNonBlankString("reason"),
            ).distinct().joinToString("\n")
        "item/fileChange/requestApproval", "applyPatchApproval" ->
            listOfNotNull(
                params.optNonBlankString("grantRoot")?.let { "Root: $it" },
                params.optNonBlankString("reason"),
            ).distinct().joinToString("\n")
        "item/permissions/requestApproval" ->
            listOfNotNull(
                params.optNonBlankString("cwd")?.let { "cwd: $it" },
                permissionSummary(params?.optJSONObject("permissions")),
                params.optNonBlankString("reason"),
            ).distinct().joinToString("\n")
        else -> serverRequestDetail(method, params)
    }

private fun mcpToolApprovalHeadline(params: JSONObject?): String? {
    val meta = params?.optJSONObject("_meta") ?: return null
    if (meta.optNonBlankString("codex_approval_kind") != "mcp_tool_call") {
        return null
    }
    val owner = meta.optNonBlankString("connector_name")
        ?: params.optNonBlankString("serverName")
        ?: "MCP"
    val action = meta.optNonBlankString("tool_title")
        ?: meta.optNonBlankString("tool_description")
        ?: return "$owner tool request"
    return "$owner wants to ${action.toActionPhrase()}"
}

private fun mcpElicitationDetailLines(params: JSONObject?): List<String> {
    val meta = params?.optJSONObject("_meta")
    val lines = mutableListOf<String>()
    params.optNonBlankString("message")?.let { lines += it }
    meta?.optNonBlankString("tool_description")?.let { lines += it }
    lines += mcpToolParamsDisplayLines(meta)
    if (lines.isEmpty()) {
        lines += listOfNotNull(
            params.optNonBlankString("serverName"),
            params.optNonBlankString("url"),
        )
    }
    return lines
}

private fun mcpElicitationExpandedDetailLines(params: JSONObject?): List<String> {
    val meta = params?.optJSONObject("_meta")
    val lines = mutableListOf<String>()
    mcpToolApprovalHeadline(params)?.let { lines += it }
    lines += mcpElicitationDetailLines(params)
    params?.optJSONObject("requestedSchema")
        ?.optJSONObject("properties")
        ?.keyList()
        ?.takeIf { it.isNotEmpty() }
        ?.let { fields -> lines += "Fields: ${fields.joinToString(", ")}" }
    params.optNonBlankString("url")?.let { lines += "URL: $it" }
    return lines
}

private fun mcpToolParamsDisplayLines(meta: JSONObject?): List<String> {
    val display = meta?.optJSONArray("tool_params_display") ?: return emptyList()
    return (0 until display.length()).mapNotNull { index ->
        val item = display.optJSONObject(index) ?: return@mapNotNull null
        val label = item.optNonBlankString("display_name")
            ?: item.optNonBlankString("name")
            ?: return@mapNotNull null
        val value = item.optNonBlankString("value")
            ?: return@mapNotNull null
        "$label: ${value.compactDisplayValue()}"
    }
}

private fun String.toActionPhrase(): String =
    replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .ifBlank { this }

private fun String.compactDisplayValue(maxLength: Int = 72): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    if (normalized.length <= maxLength) {
        return normalized
    }
    return normalized.take(maxLength).trimEnd() + "..."
}

private fun serverRequestScope(params: JSONObject?): String = listOfNotNull(
    params.optNonBlankString("threadId")
        ?: params.optNonBlankString("conversationId")
        ?: params.optNonBlankString("turnId"),
    params.optNonBlankString("itemId")
        ?: params.optNonBlankString("callId")
        ?: params.optNonBlankString("approvalId"),
).joinToString(" / ")

private fun serverRequestResponse(
    request: PendingServerRequest,
    action: ServerRequestAction,
): JSONObject? = when (request.method) {
    "item/commandExecution/requestApproval",
    "item/fileChange/requestApproval" -> JSONObject()
        .put("decision", action.decision)

    "execCommandApproval",
    "applyPatchApproval" -> JSONObject()
        .put("decision", action.legacyDecision)

    "item/permissions/requestApproval" -> {
        if (action != ServerRequestAction.Accept) {
            null
        } else {
            JSONObject()
                .put(
                    "permissions",
                    request.params
                        ?.optJSONObject("permissions")
                        ?.deepCopy()
                        ?: JSONObject()
                )
                .put("scope", "turn")
        }
    }

    "mcpServer/elicitation/request" -> JSONObject()
        .put("action", action.decision)
        .put("content", JSONObject.NULL)
        .put("_meta", JSONObject.NULL)

    else -> null
}

private fun serverRequestErrorResponse(
    request: PendingServerRequest,
    action: ServerRequestAction,
): JSONObject = JSONObject()
    .put("code", -32000)
    .put("message", "${action.label} ${request.shortMethod}")

private fun commandDisplay(params: JSONObject?): String? {
    params ?: return null
    params.optNonBlankString("command")?.let { return it }
    val command = params.optJSONArray("command") ?: return null
    return command.stringItems().joinToString(" ").takeIf(String::isNotBlank)
}

private fun permissionSummary(permissions: JSONObject?): String? {
    permissions ?: return null
    val parts = mutableListOf<String>()
    permissions.optJSONObject("network")?.let { network ->
        jsonValueAsString(network.opt("enabled"))?.let { parts += "network=$it" }
    }
    permissions.optJSONObject("fileSystem")?.let { fileSystem ->
        fileSystem.optJSONArray("read")?.let { parts += "read=${it.length()}" }
        fileSystem.optJSONArray("write")?.let { parts += "write=${it.length()}" }
        fileSystem.optJSONArray("entries")?.let { parts += "entries=${it.length()}" }
    }
    return parts.joinToString(" ").takeIf(String::isNotBlank)
}

private fun userInputQuestionHeaders(params: JSONObject?): List<String> {
    val questions = params?.optJSONArray("questions") ?: return emptyList()
    return (0 until questions.length()).mapNotNull { index ->
        questions.optJSONObject(index)?.let { question ->
            question.optNonBlankString("header")
                ?: question.optNonBlankString("question")
                ?: question.optNonBlankString("id")
        }
    }
}

private fun interactiveFormSpec(request: PendingServerRequest): InteractiveFormSpec? =
    when (request.method) {
        "item/tool/requestUserInput" -> requestUserInputFormSpec(request.params)
        "mcpServer/elicitation/request" -> mcpElicitationFormSpec(request.params)
        else -> null
    }

private fun requestUserInputFormSpec(params: JSONObject?): InteractiveFormSpec? {
    val questions = params?.optJSONArray("questions") ?: return null
    val fields = (0 until questions.length()).mapNotNull { index ->
        val question = questions.optJSONObject(index) ?: return@mapNotNull null
        val id = question.optNonBlankString("id") ?: return@mapNotNull null
        val options = question.optJSONArray("options")
            ?.let(::interactiveOptionsFromLabels)
            .orEmpty()
        val kind = when {
            options.isNotEmpty() -> InteractiveFieldKind.Choice
            question.optBoolean("isSecret") -> InteractiveFieldKind.Secret
            else -> InteractiveFieldKind.Text
        }
        InteractiveFieldSpec(
            id = id,
            label = question.optNonBlankString("header")
                ?: question.optNonBlankString("question")
                ?: id,
            description = question.optNonBlankString("question")
                ?.takeIf { it != question.optNonBlankString("header") }
                .orEmpty(),
            kind = kind,
            required = true,
            options = options,
            allowsOther = question.optBoolean("isOther") && options.isNotEmpty(),
            inputHint = if (question.optBoolean("isSecret")) {
                "Type secret answer"
            } else {
                "Type your answer"
            },
            notes = if (options.isNotEmpty()) {
                InteractiveNotesSpec(
                    hint = "Add notes",
                    visibility = InteractiveNotesVisibility.WhenAnswered,
                )
            } else {
                null
            },
        )
    }
    if (fields.isEmpty()) {
        return null
    }
    return InteractiveFormSpec(
        kind = InteractiveFormKind.RequestUserInput,
        title = "request_user_input",
        message = if (fields.size > 1) "${fields.size} questions" else "",
        fields = fields,
    )
}

private fun mcpElicitationFormSpec(params: JSONObject?): InteractiveFormSpec? {
    params ?: return null
    if (params.optString("mode") != "form") {
        return null
    }
    val schema = params.optJSONObject("requestedSchema") ?: return null
    val properties = schema.optJSONObject("properties") ?: return null
    val required = schema.optJSONArray("required")?.stringItems().orEmpty().toSet()
    val fields = properties.keyList().mapNotNull { id ->
        val property = properties.optJSONObject(id) ?: return@mapNotNull null
        mcpFieldSpec(
            id = id,
            schema = property,
            required = id in required,
        )
    }
    if (fields.isEmpty()) {
        return null
    }
    return InteractiveFormSpec(
        kind = InteractiveFormKind.McpElicitation,
        title = listOfNotNull(
            params.optNonBlankString("serverName"),
            "MCP elicitation",
        ).joinToString(" / "),
        message = params.optNonBlankString("message").orEmpty(),
        fields = fields,
        submitLabel = "Accept",
        allowDecline = true,
        allowCancel = true,
    )
}

private fun mcpFieldSpec(
    id: String,
    schema: JSONObject,
    required: Boolean,
): InteractiveFieldSpec {
    val type = schema.optNonBlankString("type").orEmpty()
    val directOptions = mcpEnumOptions(schema)
    val arrayOptions = schema.optJSONObject("items")?.let(::mcpEnumOptions).orEmpty()
    val kind = when {
        type == "array" && arrayOptions.isNotEmpty() -> InteractiveFieldKind.MultiChoice
        directOptions.isNotEmpty() -> InteractiveFieldKind.Choice
        type == "boolean" -> InteractiveFieldKind.Boolean
        type == "integer" -> InteractiveFieldKind.Integer
        type == "number" -> InteractiveFieldKind.Number
        else -> InteractiveFieldKind.Text
    }
    val options = when (kind) {
        InteractiveFieldKind.MultiChoice -> arrayOptions
        InteractiveFieldKind.Boolean -> listOf(
            InteractiveOption("true", "Yes", ""),
            InteractiveOption("false", "No", ""),
        )
        else -> directOptions
    }
    return InteractiveFieldSpec(
        id = id,
        label = schema.optNonBlankString("title") ?: id,
        description = schema.optNonBlankString("description").orEmpty(),
        kind = kind,
        required = required,
        options = options,
        inputHint = when (kind) {
            InteractiveFieldKind.Number,
            InteractiveFieldKind.Integer -> "Type number"
            else -> "Type value"
        },
        defaultValues = interactiveDefaultValues(schema.opt("default"), kind),
    )
}

private fun mcpEnumOptions(schema: JSONObject): List<InteractiveOption> {
    val titled = schema.optJSONArray("oneOf") ?: schema.optJSONArray("anyOf")
    if (titled != null && titled.length() > 0) {
        return (0 until titled.length()).mapNotNull { index ->
            val option = titled.optJSONObject(index) ?: return@mapNotNull null
            val value = option.optNonBlankString("const") ?: return@mapNotNull null
            InteractiveOption(
                value = value,
                label = option.optNonBlankString("title") ?: value,
                description = option.optNonBlankString("description").orEmpty(),
            )
        }
    }
    val values = schema.optJSONArray("enum") ?: return emptyList()
    val names = schema.optJSONArray("enumNames")
    return (0 until values.length()).mapNotNull { index ->
        val value = jsonValueAsString(values.opt(index)) ?: return@mapNotNull null
        InteractiveOption(
            value = value,
            label = names?.optString(index)?.takeIf(String::isNotBlank) ?: value,
            description = "",
        )
    }
}

private fun interactiveOptionsFromLabels(options: JSONArray): List<InteractiveOption> =
    (0 until options.length()).mapNotNull { index ->
        val option = options.optJSONObject(index) ?: return@mapNotNull null
        val label = option.optNonBlankString("label") ?: return@mapNotNull null
        InteractiveOption(
            value = label,
            label = label,
            description = option.optNonBlankString("description").orEmpty(),
        )
    }

private fun InteractiveFormSpec.initialValues(): Map<String, InteractiveFieldValue> =
    fields
        .filter { it.defaultValues.isNotEmpty() }
        .associate { field -> field.id to InteractiveFieldValue(values = field.defaultValues) }

private fun InteractiveFieldSpec.renderOptions(): List<InteractiveOption> {
    if (!allowsOther) {
        return options
    }
    return options + InteractiveOption(
        value = INTERACTIVE_OTHER_VALUE,
        label = INTERACTIVE_OTHER_LABEL,
        description = "Optionally, add details in notes.",
    )
}

private fun InteractiveFormSpec.canSubmit(values: Map<String, InteractiveFieldValue>): Boolean =
    fields.all { field ->
        val answerValues = field.answerValues(values[field.id])
        val hasRequiredAnswer = !field.required || answerValues.isNotEmpty()
        val numericValid = when (field.kind) {
            InteractiveFieldKind.Integer -> answerValues.firstOrNull()?.toLongOrNull() != null
                || answerValues.isEmpty()
            InteractiveFieldKind.Number -> answerValues.firstOrNull()?.toDoubleOrNull() != null
                || answerValues.isEmpty()
            else -> true
        }
        hasRequiredAnswer && numericValid
    }

private fun interactiveFormResponse(
    spec: InteractiveFormSpec,
    values: Map<String, InteractiveFieldValue>,
): JSONObject = when (spec.kind) {
    InteractiveFormKind.RequestUserInput -> JSONObject()
        .put(
            "answers",
            JSONObject().also { answerMap ->
                spec.fields.forEach { field ->
                    answerMap.put(
                        field.id,
                        JSONObject().put(
                            "answers",
                            JSONArray().also { array ->
                                field.requestUserInputAnswerValues(values[field.id])
                                    .forEach { answer -> array.put(answer) }
                            }
                        )
                    )
                }
            }
        )

    InteractiveFormKind.McpElicitation -> JSONObject()
        .put("action", "accept")
        .put(
            "content",
            JSONObject().also { content ->
                spec.fields.forEach { field ->
                    field.jsonAnswerValue(values[field.id])?.let { answer ->
                        content.put(field.id, answer)
                    }
                }
            }
        )
        .put("_meta", JSONObject.NULL)
}

private fun interactiveFormSummary(
    spec: InteractiveFormSpec,
    values: Map<String, InteractiveFieldValue>,
): String =
    spec.fields.joinToString(" / ") { field ->
        val answers = when (spec.kind) {
            InteractiveFormKind.RequestUserInput -> field.requestUserInputDisplayValues(values[field.id])
            InteractiveFormKind.McpElicitation -> field.answerValues(values[field.id])
        }
        val display = when {
            field.kind == InteractiveFieldKind.Secret &&
                answers.isNotEmpty() -> "secret entered"
            else -> answers.joinToString(", ").ifBlank { "empty" }
        }
        "${field.label}=$display"
    }

private fun InteractiveFieldSpec.answerValues(value: InteractiveFieldValue?): List<String> {
    val fieldValue = value ?: return emptyList()
    return when (kind) {
        InteractiveFieldKind.Text,
        InteractiveFieldKind.Secret,
        InteractiveFieldKind.Number,
        InteractiveFieldKind.Integer -> fieldValue.values
            .firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(::listOf)
            .orEmpty()

        InteractiveFieldKind.Choice,
        InteractiveFieldKind.Boolean -> fieldValue.values
            .firstOrNull()
            ?.let(::optionAnswerLabel)
            ?.let(::listOf)
            .orEmpty()

        InteractiveFieldKind.MultiChoice -> fieldValue.values
            .mapNotNull(::optionAnswerLabel)
    }
}

private fun InteractiveFieldSpec.requestUserInputAnswerValues(
    value: InteractiveFieldValue?,
): List<String> {
    val fieldValue = value ?: return emptyList()
    val selectedAnswers = when (kind) {
        InteractiveFieldKind.Text,
        InteractiveFieldKind.Number,
        InteractiveFieldKind.Integer -> emptyList()
        InteractiveFieldKind.Secret -> answerValues(fieldValue)
        InteractiveFieldKind.Choice,
        InteractiveFieldKind.Boolean,
        InteractiveFieldKind.MultiChoice -> answerValues(fieldValue)
    }
    val noteText = when (kind) {
        InteractiveFieldKind.Text,
        InteractiveFieldKind.Number,
        InteractiveFieldKind.Integer -> fieldValue.values.firstOrNull().orEmpty()
        InteractiveFieldKind.Secret -> ""
        InteractiveFieldKind.Choice,
        InteractiveFieldKind.Boolean,
        InteractiveFieldKind.MultiChoice -> fieldValue.noteText
    }.trim()
    return buildList {
        addAll(selectedAnswers)
        if (noteText.isNotBlank()) {
            add("$INTERACTIVE_NOTE_PREFIX$noteText")
        }
    }
}

private fun InteractiveFieldSpec.requestUserInputDisplayValues(
    value: InteractiveFieldValue?,
): List<String> {
    val answers = requestUserInputAnswerValues(value)
    return answers.map { answer ->
        if (answer.startsWith(INTERACTIVE_NOTE_PREFIX)) {
            "note entered"
        } else {
            answer
        }
    }
}

private fun InteractiveFieldSpec.optionAnswerLabel(value: String): String? {
    if (value.isBlank()) {
        return null
    }
    return if (allowsOther && value == INTERACTIVE_OTHER_VALUE) {
        INTERACTIVE_OTHER_LABEL
    } else {
        value
    }
}

private fun InteractiveFieldSpec.jsonAnswerValue(value: InteractiveFieldValue?): Any? {
    val answers = answerValues(value)
    if (answers.isEmpty()) {
        return null
    }
    return when (kind) {
        InteractiveFieldKind.Boolean -> answers.first().toBooleanOrNull()
        InteractiveFieldKind.Integer -> answers.first().toLongOrNull()
        InteractiveFieldKind.Number -> answers.first().toDoubleOrNull()
        InteractiveFieldKind.MultiChoice -> JSONArray().also { array ->
            answers.forEach { answer -> array.put(answer) }
        }
        else -> answers.first()
    }
}

private fun interactiveDefaultValues(value: Any?, kind: InteractiveFieldKind): List<String> =
    when (value) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> (0 until value.length()).mapNotNull { index ->
            jsonValueAsString(value.opt(index))
        }
        else -> jsonValueAsString(value)?.let(::listOf).orEmpty()
    }.let { defaults ->
        if (kind == InteractiveFieldKind.Boolean) {
            defaults.map { it.lowercase() }
        } else {
            defaults
        }
    }

private fun String.toBooleanOrNull(): Boolean? =
    when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

private fun JSONObject.keyList(): List<String> {
    val names = names() ?: return emptyList()
    return (0 until names.length()).mapNotNull { index ->
        names.optString(index).takeIf(String::isNotBlank)
    }
}

private fun JSONObject?.optNonBlankString(name: String): String? =
    jsonValueAsString(this?.opt(name))
        ?.takeIf { value -> value.isNotBlank() && !value.equals("null", ignoreCase = true) }

private fun JSONArray.stringItems(): List<String> =
    (0 until length()).mapNotNull { index -> jsonValueAsString(opt(index)) }

private fun JSONObject.deepCopy(): JSONObject =
    JSONObject(toString())

private fun jsonValueAsString(value: Any?): String? = when (value) {
    null, JSONObject.NULL -> null
    is String -> value.takeIf(String::isNotBlank)
    else -> value.toString().takeIf(String::isNotBlank)
}

private fun JSONObject.optErrorMessage(fallback: String): String =
    optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
        ?: optString("error").takeIf(String::isNotBlank)
        ?: fallback

private val turnScopedNotificationMethods = setOf(
    "turn/started",
    "turn/completed",
    "thread/tokenUsage/updated",
    "item/agentMessage/delta",
    "item/reasoning/summaryTextDelta",
    "item/reasoning/textDelta",
    "item/reasoning/summaryPartAdded",
    "item/plan/delta",
    "turn/diff/updated",
    "item/commandExecution/outputDelta",
    "item/fileChange/outputDelta",
    "item/fileChange/patchUpdated",
    "item/commandExecution/terminalInteraction",
    "item/mcpToolCall/progress",
    "item/completed",
    "error",
    "warning",
    "guardianWarning",
)

private fun notificationParams(event: JSONObject?): JSONObject? =
    event
        ?.optJSONObject("payload")
        ?.optJSONObject("params")
        ?: event?.optJSONObject("params")

private fun notificationMatchesThread(params: JSONObject?, activeThreadId: String): Boolean {
    val eventThreadId = scopedThreadId(params)
    return eventThreadId == null || eventThreadId == activeThreadId
}

private fun notificationMatchesTurn(
    params: JSONObject?,
    activeThreadId: String,
    activeTurnId: String,
): Boolean {
    if (!notificationMatchesThread(params, activeThreadId)) {
        return false
    }
    val eventTurnId = scopedTurnId(params)
    return eventTurnId == null || eventTurnId == activeTurnId
}

private fun PendingServerRequest.matchesThreadTurn(
    activeThreadId: String,
    activeTurnId: String,
): Boolean {
    val requestThreadId = scopedThreadId(params)
    if (requestThreadId != null && requestThreadId != activeThreadId) {
        return false
    }
    val requestTurnId = scopedTurnId(params)
    return requestTurnId == null || requestTurnId == activeTurnId
}

private fun PendingServerRequest.matchesActiveScope(
    activeThreadId: String?,
    activeTurnId: String?,
): Boolean {
    val requestThreadId = scopedThreadId(params)
    if (requestThreadId != null && requestThreadId != activeThreadId) {
        return false
    }
    val requestTurnId = scopedTurnId(params)
    return requestTurnId == null || requestTurnId == activeTurnId
}

private fun scopedThreadId(params: JSONObject?): String? =
    params.optNonBlankString("threadId")
        ?: params.optNonBlankString("conversationId")

private fun scopedTurnId(params: JSONObject?): String? =
    params.optNonBlankString("turnId")
        ?: params?.optJSONObject("turn")?.optNonBlankString("id")

private fun Throwable.readableMessage(): String =
    message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

private fun runtimeSummaryFromSmokeStatus(status: String, threadId: String?): RuntimeSummary {
    val engine = when {
        status == "starting embedded runtime" -> "starting"
        status.contains("initialize=startEngine(in-process)") -> "in-process"
        status.startsWith("embedded Codex start failed") -> "start failed"
        else -> "unknown"
    }
    val thread = threadId?.let(::shortId) ?: "not verified"
    val account = smokeLineValue(status, "account=")
        ?.let { response ->
            runCatching {
                when (val accountState = accountPanelStateFromReadResponse(response)) {
                    is AccountPanelState.SignedIn -> accountState.detail
                    is AccountPanelState.SignedOut -> "signed out"
                    is AccountPanelState.Error -> "error"
                    AccountPanelState.Loading -> "reading"
                    is AccountPanelState.LoginPending -> "login pending"
                }
            }.getOrDefault("unavailable")
        }
        ?: "pending"
    val turn = smokeLineValue(status, "turnStart=")
        ?.let { response ->
            if (response.startsWith("not attempted:")) {
                response.removePrefix("not attempted:").trim()
            } else if (response.contains("\"ok\":true")) {
                "started"
            } else {
                "not ready"
            }
        }
        ?: "idle"

    return RuntimeSummary(
        eyebrow = if (status == "starting embedded runtime") "starting" else "app-server",
        engine = engine,
        thread = thread,
        account = account,
        turn = turn,
    )
}

private fun operatorToolProfileLabel(): String =
    when (BuildConfig.OPERATOR_TOOL_PROFILE) {
        "fullLocal" -> "full local tools"
        "playTiny" -> "tiny shell tools"
        else -> BuildConfig.OPERATOR_TOOL_PROFILE
    }

private fun operatorLocalRuntimeLabel(): String =
    if (BuildConfig.OPERATOR_FULL_LOCAL_RUNTIME) {
        "bundled full runtime"
    } else {
        "tiny tools until runtime extension is paired"
    }

private fun smokeLineValue(status: String, prefix: String): String? =
    status.lineSequence()
        .firstOrNull { it.startsWith(prefix) }
        ?.substringAfter(prefix)
        ?.takeIf(String::isNotBlank)

private fun shortId(id: String): String =
    if (id.length <= 8) id else id.take(8)

private fun threadIdFromSmokeStatus(status: String): String? =
    status.lineSequence()
        .firstOrNull { it.startsWith("threadId=") }
        ?.substringAfter("threadId=")
        ?.takeIf(String::isNotBlank)

private fun serverRequestCountFromSmokeStatus(status: String): Int =
    status.lineSequence().count { line ->
        line.contains("\"type\":\"server.request\"")
    }

@Preview(showBackground = true)
@Composable
private fun OperatorHomePreview() {
    OperatorApp()
}
