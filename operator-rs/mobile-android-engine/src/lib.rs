use codex_app_server_client::legacy_core::config::ConfigBuilder;
use codex_app_server_client::{
    EnvironmentManager, InProcessAppServerClient, InProcessAppServerRequestHandle,
    InProcessClientStartArgs, DEFAULT_IN_PROCESS_CHANNEL_CAPACITY,
};
use codex_app_server_protocol::{
    ClientInfo, ClientRequest, JSONRPCErrorError, RequestId, ServerNotification, ServerRequest,
};
use codex_arg0::Arg0DispatchPaths;
use codex_config::{CloudRequirementsLoader, LoaderOverrides};
use codex_feedback::CodexFeedback;
use codex_protocol::protocol::SessionSource;
use jni::objects::{JObject, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;
use serde_json::{json, Value};
use std::collections::{HashMap, VecDeque};
use std::path::PathBuf;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::Arc;
use std::sync::{Mutex, OnceLock};
use std::time::Duration;
use tokio::runtime::{Builder as TokioRuntimeBuilder, Runtime};
use toml::Value as TomlValue;

static NEXT_ENGINE_HANDLE: AtomicI64 = AtomicI64::new(1);
static ENGINES: OnceLock<Mutex<HashMap<i64, EngineState>>> = OnceLock::new();
static LAST_START_ERROR: OnceLock<Mutex<Option<String>>> = OnceLock::new();
const ANDROID_SHELL_PATH: &str = "/system/bin/sh";
const ANDROID_SYSTEM_PATH: &str =
    "/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin";
const ANDROID_CA_BUNDLE_RELATIVE_PATH: &[&str] = &["tools", "cacert.pem"];

struct EngineState {
    config_json: String,
    codex_home: String,
    workspace_root: String,
    runtime: Arc<Runtime>,
    request_handle: InProcessAppServerRequestHandle,
    client: Option<InProcessAppServerClient>,
    events: VecDeque<String>,
}

fn engines() -> &'static Mutex<HashMap<i64, EngineState>> {
    ENGINES.get_or_init(|| Mutex::new(HashMap::new()))
}

fn last_start_error() -> &'static Mutex<Option<String>> {
    LAST_START_ERROR.get_or_init(|| Mutex::new(None))
}

fn new_java_string(env: &JNIEnv<'_>, value: String) -> jstring {
    match env.new_string(value) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn read_java_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> Result<String, String> {
    env.get_string(&value)
        .map(Into::into)
        .map_err(|error| error.to_string())
}

fn error_payload(message: impl Into<String>) -> String {
    json!({
        "ok": false,
        "error": message.into(),
    })
    .to_string()
}

fn set_last_start_error(value: Option<String>) {
    if let Ok(mut error) = last_start_error().lock() {
        *error = value;
    }
}

fn string_field(value: &Value, key: &str) -> Option<String> {
    value
        .get(key)
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
}

fn path_from_json_or_default(config: &Value, key: &str, fallback: PathBuf) -> PathBuf {
    string_field(config, key)
        .map(PathBuf::from)
        .unwrap_or(fallback)
}

fn request_id_from_string(value: String) -> RequestId {
    match value.parse::<i64>() {
        Ok(value) => RequestId::Integer(value),
        Err(_) => RequestId::String(value),
    }
}

fn android_tool_path(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("bin")
}

fn android_tool_lib_path(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("lib")
}

fn android_ca_bundle_path(app_files_dir: &std::path::Path) -> PathBuf {
    ANDROID_CA_BUNDLE_RELATIVE_PATH
        .iter()
        .fold(app_files_dir.to_path_buf(), |path, segment| {
            path.join(segment)
        })
}

fn android_tmp_path(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tmp-run")
}

fn android_xdg_cache_path(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("xdg").join("cache")
}

fn android_xdg_config_path(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("xdg").join("config")
}

fn android_xdg_data_path(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("xdg").join("data")
}

fn android_python_home(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("python")
}

fn android_python_user_base(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("python-user")
}

fn android_python_dev_libs(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("python-dev-libs")
}

fn android_python_wheelhouse(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("python-wheelhouse")
}

fn android_node_home(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("node")
}

fn android_node_global_prefix(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("node-global")
}

fn android_toolchain_prefix(app_files_dir: &std::path::Path) -> PathBuf {
    app_files_dir.join("tools").join("toolchain").join("usr")
}

fn android_shell_path(app_files_dir: &std::path::Path) -> String {
    format!(
        "{}:node_modules/.bin:{}:{}:{}:{}:{}",
        android_tool_path(app_files_dir).display(),
        android_python_home(app_files_dir).join("bin").display(),
        android_python_user_base(app_files_dir)
            .join("bin")
            .display(),
        android_node_global_prefix(app_files_dir)
            .join("bin")
            .display(),
        android_toolchain_prefix(app_files_dir)
            .join("bin")
            .display(),
        ANDROID_SYSTEM_PATH
    )
}

fn android_tool_command_exists(app_files_dir: &std::path::Path, command: &str) -> bool {
    android_tool_path(app_files_dir).join(command).is_file()
}

fn directory_contains_extension(directory: &std::path::Path, extension: &str) -> bool {
    std::fs::read_dir(directory)
        .ok()
        .into_iter()
        .flatten()
        .filter_map(Result::ok)
        .any(|entry| {
            entry
                .path()
                .extension()
                .is_some_and(|value| value.to_string_lossy() == extension)
        })
}

fn android_cli_overrides(
    app_files_dir: &std::path::Path,
    codex_home: &std::path::Path,
) -> Vec<(String, TomlValue)> {
    let tools_bin = android_tool_path(app_files_dir).display().to_string();
    let tools_lib = android_tool_lib_path(app_files_dir).display().to_string();
    let ca_bundle = android_ca_bundle_path(app_files_dir).display().to_string();
    let tmp_dir = android_tmp_path(app_files_dir).display().to_string();
    let xdg_cache = android_xdg_cache_path(app_files_dir).display().to_string();
    let xdg_config = android_xdg_config_path(app_files_dir).display().to_string();
    let xdg_data = android_xdg_data_path(app_files_dir).display().to_string();
    let python_home = android_python_home(app_files_dir);
    let python_home_display = python_home.display().to_string();
    let python_user_base = android_python_user_base(app_files_dir)
        .display()
        .to_string();
    let python_dev_libs = android_python_dev_libs(app_files_dir);
    let python_dev_libs_display = python_dev_libs.display().to_string();
    let python_wheelhouse = android_python_wheelhouse(app_files_dir);
    let python_wheelhouse_display = python_wheelhouse.display().to_string();
    let node_home = android_node_home(app_files_dir);
    let node_home_display = node_home.display().to_string();
    let node_global_prefix = android_node_global_prefix(app_files_dir)
        .display()
        .to_string();
    let toolchain_prefix = android_toolchain_prefix(app_files_dir);
    let toolchain_prefix_display = toolchain_prefix.display().to_string();
    let toolchain_pkg_config = format!("{toolchain_prefix_display}/lib/pkgconfig");
    let python_pkg_config = format!("{python_dev_libs_display}/lib/pkgconfig");
    let mut pkg_config_paths = vec![python_pkg_config.clone()];
    if toolchain_prefix.join("lib").join("pkgconfig").is_dir() {
        pkg_config_paths.push(toolchain_pkg_config);
    }
    let pkg_config_path = pkg_config_paths.join(":");
    let ssh_known_hosts = codex_home
        .join(".ssh")
        .join("known_hosts")
        .display()
        .to_string();
    let git_template_dir = app_files_dir
        .join("tools")
        .join("share")
        .join("git-core")
        .join("templates")
        .display()
        .to_string();
    let mut overrides = vec![
        ("allow_login_shell".to_string(), TomlValue::Boolean(false)),
        (
            "shell_environment_policy.inherit".to_string(),
            TomlValue::String("none".to_string()),
        ),
        (
            "shell_environment_policy.set.HOME".to_string(),
            TomlValue::String(codex_home.display().to_string()),
        ),
        (
            "shell_environment_policy.set.USER".to_string(),
            TomlValue::String("operator".to_string()),
        ),
        (
            "shell_environment_policy.set.LOGNAME".to_string(),
            TomlValue::String("operator".to_string()),
        ),
        (
            "shell_environment_policy.set.PATH".to_string(),
            TomlValue::String(android_shell_path(app_files_dir)),
        ),
        (
            "shell_environment_policy.set.TMPDIR".to_string(),
            TomlValue::String(tmp_dir.clone()),
        ),
        (
            "shell_environment_policy.set.TMP".to_string(),
            TomlValue::String(tmp_dir.clone()),
        ),
        (
            "shell_environment_policy.set.TEMP".to_string(),
            TomlValue::String(tmp_dir.clone()),
        ),
        (
            "shell_environment_policy.set.XDG_CACHE_HOME".to_string(),
            TomlValue::String(xdg_cache.clone()),
        ),
        (
            "shell_environment_policy.set.XDG_CONFIG_HOME".to_string(),
            TomlValue::String(xdg_config),
        ),
        (
            "shell_environment_policy.set.XDG_DATA_HOME".to_string(),
            TomlValue::String(xdg_data.clone()),
        ),
        (
            "shell_environment_policy.set.GIT_EXEC_PATH".to_string(),
            TomlValue::String(tools_bin.clone()),
        ),
        (
            "shell_environment_policy.set.GIT_CONFIG_NOSYSTEM".to_string(),
            TomlValue::String("1".to_string()),
        ),
        (
            "shell_environment_policy.set.GIT_TERMINAL_PROMPT".to_string(),
            TomlValue::String("0".to_string()),
        ),
        (
            "shell_environment_policy.set.GIT_TEMPLATE_DIR".to_string(),
            TomlValue::String(git_template_dir),
        ),
        (
            "shell_environment_policy.set.GIT_SSH".to_string(),
            TomlValue::String("ssh".to_string()),
        ),
        (
            "shell_environment_policy.set.GIT_SSH_COMMAND".to_string(),
            TomlValue::String(format!(
                "ssh -o UserKnownHostsFile={} -o StrictHostKeyChecking=accept-new",
                shell_quote(&ssh_known_hosts)
            )),
        ),
        (
            "shell_environment_policy.set.GIT_PAGER".to_string(),
            TomlValue::String("cat".to_string()),
        ),
        (
            "shell_environment_policy.set.PAGER".to_string(),
            TomlValue::String("cat".to_string()),
        ),
        (
            "shell_environment_policy.set.GIT_SSL_CAINFO".to_string(),
            TomlValue::String(ca_bundle.clone()),
        ),
        (
            "shell_environment_policy.set.SSL_CERT_FILE".to_string(),
            TomlValue::String(ca_bundle.clone()),
        ),
        (
            "shell_environment_policy.set.REQUESTS_CA_BUNDLE".to_string(),
            TomlValue::String(ca_bundle.clone()),
        ),
        (
            "shell_environment_policy.set.PIP_CERT".to_string(),
            TomlValue::String(ca_bundle.clone()),
        ),
        (
            "shell_environment_policy.set.CURL_CA_BUNDLE".to_string(),
            TomlValue::String(ca_bundle),
        ),
        (
            "shell_environment_policy.set.GIT_SSL_CAPATH".to_string(),
            TomlValue::String("/system/etc/security/cacerts".to_string()),
        ),
        (
            "shell_environment_policy.set.SSL_CERT_DIR".to_string(),
            TomlValue::String("/system/etc/security/cacerts".to_string()),
        ),
        (
            "shell_environment_policy.set.SHELL".to_string(),
            TomlValue::String(ANDROID_SHELL_PATH.to_string()),
        ),
        (
            "shell_environment_policy.set.PYTHONUSERBASE".to_string(),
            TomlValue::String(python_user_base),
        ),
        (
            "shell_environment_policy.set.PYTHONPYCACHEPREFIX".to_string(),
            TomlValue::String(format!("{tmp_dir}/python-pycache")),
        ),
        (
            "shell_environment_policy.set.PIP_CACHE_DIR".to_string(),
            TomlValue::String(format!("{xdg_cache}/pip")),
        ),
        (
            "shell_environment_policy.set.PIP_DISABLE_PIP_VERSION_CHECK".to_string(),
            TomlValue::String("1".to_string()),
        ),
        (
            "shell_environment_policy.set.PKG_CONFIG_LIBDIR".to_string(),
            TomlValue::String(pkg_config_path.clone()),
        ),
        (
            "shell_environment_policy.set.PKG_CONFIG_PATH".to_string(),
            TomlValue::String(pkg_config_path),
        ),
        (
            "shell_environment_policy.set.CPPFLAGS".to_string(),
            TomlValue::String(format!(
                "-I{python_home_display}/include/python3.13 -I{python_dev_libs_display}/include"
            )),
        ),
        (
            "shell_environment_policy.set.CFLAGS".to_string(),
            TomlValue::String(format!(
                "-fPIC -I{python_home_display}/include/python3.13 -I{python_dev_libs_display}/include"
            )),
        ),
        (
            "shell_environment_policy.set.LDFLAGS".to_string(),
            TomlValue::String(format!(
                "-L{python_dev_libs_display}/lib -L{toolchain_prefix_display}/lib"
            )),
        ),
        (
            "shell_environment_policy.set.NODE_PATH".to_string(),
            TomlValue::String(format!("{node_home_display}/lib/node_modules")),
        ),
        (
            "shell_environment_policy.set.NODE_HOME".to_string(),
            TomlValue::String(node_home_display),
        ),
        (
            "shell_environment_policy.set.NPM_CONFIG_CACHE".to_string(),
            TomlValue::String(format!("{xdg_cache}/npm")),
        ),
        (
            "shell_environment_policy.set.NPM_CONFIG_PREFIX".to_string(),
            TomlValue::String(node_global_prefix),
        ),
        (
            "shell_environment_policy.set.NPM_CONFIG_TMP".to_string(),
            TomlValue::String(format!("{tmp_dir}/npm")),
        ),
        (
            "shell_environment_policy.set.NPM_CONFIG_UPDATE_NOTIFIER".to_string(),
            TomlValue::String("false".to_string()),
        ),
        (
            "shell_environment_policy.set.NPM_CONFIG_FUND".to_string(),
            TomlValue::String("false".to_string()),
        ),
        (
            "shell_environment_policy.set.NPM_CONFIG_AUDIT".to_string(),
            TomlValue::String("false".to_string()),
        ),
        (
            "shell_environment_policy.set.LD_LIBRARY_PATH".to_string(),
            TomlValue::String(format!(
                "{tools_lib}:{python_home_display}/lib:{python_dev_libs_display}/lib:{toolchain_prefix_display}/lib"
            )),
        ),
    ];

    if android_tool_command_exists(app_files_dir, "clang") {
        overrides.extend([
            (
                "shell_environment_policy.set.TERMUX_PREFIX".to_string(),
                TomlValue::String(toolchain_prefix_display.clone()),
            ),
            (
                "shell_environment_policy.set.PREFIX".to_string(),
                TomlValue::String(toolchain_prefix_display.clone()),
            ),
            (
                "shell_environment_policy.set.CC".to_string(),
                TomlValue::String("clang".to_string()),
            ),
            (
                "shell_environment_policy.set.CXX".to_string(),
                TomlValue::String("clang++".to_string()),
            ),
            (
                "shell_environment_policy.set.AR".to_string(),
                TomlValue::String("llvm-ar".to_string()),
            ),
            (
                "shell_environment_policy.set.RANLIB".to_string(),
                TomlValue::String("llvm-ranlib".to_string()),
            ),
            (
                "shell_environment_policy.set.STRIP".to_string(),
                TomlValue::String("llvm-strip".to_string()),
            ),
        ]);
    }

    if directory_contains_extension(&python_wheelhouse, "whl") {
        overrides.extend([
            (
                "shell_environment_policy.set.PIP_FIND_LINKS".to_string(),
                TomlValue::String(python_wheelhouse_display),
            ),
            (
                "shell_environment_policy.set.PIP_PREFER_BINARY".to_string(),
                TomlValue::String("1".to_string()),
            ),
        ]);
    }

    if android_tool_command_exists(app_files_dir, "cargo") {
        overrides.extend([
            (
                "shell_environment_policy.set.CARGO_HOME".to_string(),
                TomlValue::String(format!("{xdg_data}/cargo")),
            ),
            (
                "shell_environment_policy.set.RUSTUP_HOME".to_string(),
                TomlValue::String(format!("{xdg_data}/rustup")),
            ),
            (
                "shell_environment_policy.set.CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER".to_string(),
                TomlValue::String("clang".to_string()),
            ),
        ]);
    }

    overrides
}

fn shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', "'\\''"))
}

fn configure_android_networking(app_files_dir: &std::path::Path) -> Option<PathBuf> {
    let ca_bundle = android_ca_bundle_path(app_files_dir);
    if !ca_bundle.is_file() {
        return None;
    }

    codex_client::set_codex_ca_certificate_path_override(ca_bundle.clone());
    Some(ca_bundle)
}

fn server_event_to_json(event: codex_app_server_client::InProcessServerEvent) -> String {
    match event {
        codex_app_server_client::InProcessServerEvent::Lagged { skipped } => json!({
            "type": "runtime.lagged",
            "skipped": skipped,
        }),
        codex_app_server_client::InProcessServerEvent::ServerNotification(notification) => json!({
            "type": "server.notification",
            "method": server_notification_method(&notification),
            "payload": notification,
        }),
        codex_app_server_client::InProcessServerEvent::ServerRequest(request) => json!({
            "type": "server.request",
            "method": server_request_method(&request),
            "requestId": request.id(),
            "payload": request,
        }),
    }
    .to_string()
}

fn server_notification_method(notification: &ServerNotification) -> String {
    serde_json::to_value(notification)
        .ok()
        .and_then(|value| {
            value
                .get("method")
                .and_then(Value::as_str)
                .map(ToOwned::to_owned)
        })
        .unwrap_or_else(|| "<unknown>".to_string())
}

fn server_request_method(request: &ServerRequest) -> String {
    serde_json::to_value(request)
        .ok()
        .and_then(|value| {
            value
                .get("method")
                .and_then(Value::as_str)
                .map(ToOwned::to_owned)
        })
        .unwrap_or_else(|| "<unknown>".to_string())
}

fn start_embedded_codex(config_json: String, handle: i64) -> Result<EngineState, String> {
    let config_value: Value = serde_json::from_str(&config_json)
        .map_err(|error| format!("invalid config JSON: {error}"))?;
    let app_files_dir = string_field(&config_value, "appFilesDir")
        .ok_or_else(|| "configJson.appFilesDir is required".to_string())
        .map(PathBuf::from)?;
    let codex_home =
        path_from_json_or_default(&config_value, "codexHome", app_files_dir.join("codex-home"));
    let workspace_root = path_from_json_or_default(
        &config_value,
        "workspaceRoot",
        app_files_dir.join("workspaces").join("default"),
    );

    std::fs::create_dir_all(&codex_home)
        .map_err(|error| format!("failed to create Codex home: {error}"))?;
    std::fs::create_dir_all(&workspace_root)
        .map_err(|error| format!("failed to create workspace root: {error}"))?;
    std::fs::create_dir_all(android_tool_path(&app_files_dir))
        .map_err(|error| format!("failed to create Android tools dir: {error}"))?;
    for directory in [
        android_tool_lib_path(&app_files_dir),
        android_tmp_path(&app_files_dir),
        android_tmp_path(&app_files_dir).join("python-pycache"),
        android_tmp_path(&app_files_dir).join("npm"),
        android_xdg_cache_path(&app_files_dir),
        android_xdg_cache_path(&app_files_dir).join("pip"),
        android_xdg_cache_path(&app_files_dir).join("npm"),
        android_xdg_config_path(&app_files_dir),
        android_xdg_data_path(&app_files_dir),
        android_python_home(&app_files_dir),
        android_python_home(&app_files_dir).join("bin"),
        android_python_home(&app_files_dir).join("lib"),
        android_python_dev_libs(&app_files_dir),
        android_python_dev_libs(&app_files_dir).join("include"),
        android_python_dev_libs(&app_files_dir).join("lib"),
        android_python_dev_libs(&app_files_dir).join("lib").join("pkgconfig"),
        android_python_user_base(&app_files_dir),
        android_python_user_base(&app_files_dir).join("bin"),
        android_python_wheelhouse(&app_files_dir),
        android_node_home(&app_files_dir),
        android_node_global_prefix(&app_files_dir),
        android_node_global_prefix(&app_files_dir).join("bin"),
        android_toolchain_prefix(&app_files_dir),
        android_toolchain_prefix(&app_files_dir).join("bin"),
        android_toolchain_prefix(&app_files_dir).join("include"),
        android_toolchain_prefix(&app_files_dir).join("lib"),
        android_toolchain_prefix(&app_files_dir).join("lib").join("pkgconfig"),
        android_toolchain_prefix(&app_files_dir).join("tmp"),
        codex_home.join(".ssh"),
    ] {
        std::fs::create_dir_all(&directory).map_err(|error| {
            format!(
                "failed to create Android runtime directory {}: {error}",
                directory.display()
            )
        })?;
    }
    let ca_bundle = configure_android_networking(&app_files_dir);
    let websocket_tls = if ca_bundle.is_some() {
        "codex_ca_certificate_override"
    } else {
        "default_roots"
    };

    let runtime = Arc::new(
        TokioRuntimeBuilder::new_multi_thread()
            .enable_all()
            .worker_threads(2)
            .thread_name("operator-codex")
            .thread_stack_size(8 * 1024 * 1024)
            .build()
            .map_err(|error| format!("failed to create Tokio runtime: {error}"))?,
    );

    let runtime_for_start = Arc::clone(&runtime);
    let app_files_dir_for_config = app_files_dir.clone();
    let codex_home_for_config = codex_home.clone();
    let codex_home_for_android_config = codex_home.clone();
    let workspace_for_config = workspace_root.clone();
    let client = runtime_for_start
        .block_on(async move {
            let config = ConfigBuilder::default()
                .codex_home(codex_home_for_config)
                .fallback_cwd(Some(workspace_for_config))
                .cli_overrides(android_cli_overrides(
                    &app_files_dir_for_config,
                    &codex_home_for_android_config,
                ))
                .build()
                .await?;
            InProcessAppServerClient::start(InProcessClientStartArgs {
                arg0_paths: Arg0DispatchPaths::default(),
                config: Arc::new(config),
                cli_overrides: Vec::new(),
                loader_overrides: LoaderOverrides::default(),
                cloud_requirements: CloudRequirementsLoader::default(),
                feedback: CodexFeedback::new(),
                log_db: None,
                environment_manager: Arc::new(
                    EnvironmentManager::embedded_local_without_sandbox_helpers(),
                ),
                config_warnings: Vec::new(),
                session_source: SessionSource::Custom("operator_android".to_string()),
                enable_codex_api_key_env: false,
                client_name: "operator_android".to_string(),
                client_version: env!("CARGO_PKG_VERSION").to_string(),
                experimental_api: true,
                opt_out_notification_methods: Vec::new(),
                channel_capacity: DEFAULT_IN_PROCESS_CHANNEL_CAPACITY,
            })
            .await
        })
        .map_err(|error| format!("embedded Codex initialize failed: {error}"))?;
    let request_handle = client.request_handle();

    let codex_home = codex_home.display().to_string();
    let workspace_root = workspace_root.display().to_string();
    let mut events = VecDeque::new();
    events.push_back(
        json!({
            "type": "engine.started",
            "source": "operator_bridge",
            "handle": handle,
            "runtime": "codex_app_server_in_process",
            "codexHome": codex_home,
            "workspaceRoot": workspace_root,
            "network": {
                "caBundle": ca_bundle.as_ref().map(|path| path.display().to_string()),
                "websocketTls": websocket_tls,
                "modelTransport": "responses_websocket_primary_https_fallback"
            },
        })
        .to_string(),
    );

    Ok(EngineState {
        config_json,
        codex_home,
        workspace_root,
        runtime,
        request_handle,
        client: Some(client),
        events,
    })
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeEngineStatus(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    let client_info = ClientInfo {
        name: "operator_android".to_string(),
        title: Some("Operator Android".to_string()),
        version: env!("CARGO_PKG_VERSION").to_string(),
    };

    let status = serde_json::json!({
        "engine": "operator_mobile_engine",
        "bridge": "jni",
        "codexSource": "third_party/codex",
        "codexProtocolClient": client_info,
        "bridgeApi": [
            "startEngine",
            "sendRequest",
            "nextEvent",
            "respondToServerRequest",
            "failServerRequest",
            "shutdownEngine"
        ],
        "runtime": {
            "mode": "embedded",
            "processBoundary": "in_process",
            "startup": "codex_app_server_client"
        },
        "network": {
            "modelTransport": "responses_websocket_primary_https_fallback",
            "websocketTls": "codex_ca_certificate_override"
        },
        "environment": {
            "manager": "embedded_local_without_sandbox_helpers",
            "sandboxHelpers": false,
            "policyBoundary": "android_app"
        }
    })
    .to_string();

    new_java_string(&env, status)
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeStartEngine(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    config_json: JString<'_>,
) -> jlong {
    let config_json = match read_java_string(&mut env, config_json) {
        Ok(value) => value,
        Err(error) => {
            set_last_start_error(Some(error));
            return 0;
        }
    };
    let handle = NEXT_ENGINE_HANDLE.fetch_add(1, Ordering::Relaxed);
    let state = match start_embedded_codex(config_json, handle) {
        Ok(state) => {
            set_last_start_error(None);
            state
        }
        Err(error) => {
            set_last_start_error(Some(error));
            return 0;
        }
    };

    match engines().lock() {
        Ok(mut engines) => {
            engines.insert(handle, state);
            handle as jlong
        }
        Err(_) => {
            set_last_start_error(Some("engine registry is unavailable".to_string()));
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeSendRequest(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
    request_json: JString<'_>,
) -> jstring {
    let request_json = match read_java_string(&mut env, request_json) {
        Ok(value) => value,
        Err(error) => return new_java_string(&env, error_payload(error)),
    };
    let request = match serde_json::from_str::<ClientRequest>(&request_json) {
        Ok(request) => request,
        Err(error) => {
            return new_java_string(
                &env,
                error_payload(format!("invalid app-server request JSON: {error}")),
            );
        }
    };
    let request_id = request.id().clone();
    let method = request.method();

    let handle = handle as i64;
    let (runtime, request_handle) = match engines().lock() {
        Ok(engines) => match engines.get(&handle) {
            Some(engine) => (Arc::clone(&engine.runtime), engine.request_handle.clone()),
            None => {
                return new_java_string(
                    &env,
                    error_payload(format!("unknown engine handle {handle}")),
                )
            }
        },
        Err(_) => return new_java_string(&env, error_payload("engine registry is unavailable")),
    };

    let response = match runtime.block_on(request_handle.request(request)) {
        Ok(Ok(result)) => json!({
            "ok": true,
            "handle": handle,
            "requestId": request_id,
            "method": method,
            "result": result,
        })
        .to_string(),
        Ok(Err(error)) => json!({
            "ok": false,
            "handle": handle,
            "requestId": request_id,
            "method": method,
            "error": {
                "code": error.code,
                "message": error.message,
                "data": error.data,
            },
        })
        .to_string(),
        Err(error) => error_payload(format!("embedded Codex request transport failed: {error}")),
    };

    new_java_string(&env, response)
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeNextEvent(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
) -> jstring {
    let handle = handle as i64;
    let event = match engines().lock() {
        Ok(mut engines) => match engines.get_mut(&handle) {
            Some(engine) => {
                if let Some(event) = engine.events.pop_front() {
                    Some(event)
                } else {
                    match engine.client.as_mut() {
                        Some(client) => match engine.runtime.block_on(async {
                            tokio::time::timeout(Duration::from_millis(10), client.next_event())
                                .await
                        }) {
                            Ok(Some(event)) => Some(server_event_to_json(event)),
                            Ok(None) | Err(_) => None,
                        },
                        None => Some(error_payload(format!(
                            "engine handle {handle} is shut down"
                        ))),
                    }
                }
            }
            None => Some(error_payload(format!("unknown engine handle {handle}"))),
        },
        Err(_) => Some(error_payload("engine registry is unavailable")),
    };

    match event {
        Some(event) => new_java_string(&env, event),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeRespondToServerRequest(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
    request_id: JString<'_>,
    result_json: JString<'_>,
) -> jstring {
    complete_server_request(
        &mut env,
        handle,
        request_id,
        result_json,
        "server_request.completed",
    )
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeFailServerRequest(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
    request_id: JString<'_>,
    error_json: JString<'_>,
) -> jstring {
    complete_server_request(
        &mut env,
        handle,
        request_id,
        error_json,
        "server_request.failed",
    )
}

fn complete_server_request(
    env: &mut JNIEnv<'_>,
    handle: jlong,
    request_id: JString<'_>,
    payload_json: JString<'_>,
    event_type: &str,
) -> jstring {
    let request_id = match read_java_string(env, request_id) {
        Ok(value) => value,
        Err(error) => return new_java_string(env, error_payload(error)),
    };
    let payload_json = match read_java_string(env, payload_json) {
        Ok(value) => value,
        Err(error) => return new_java_string(env, error_payload(error)),
    };
    let payload = serde_json::from_str::<Value>(&payload_json).unwrap_or_else(|_| {
        json!({
            "raw": payload_json,
        })
    });

    let handle = handle as i64;
    let request_id = request_id_from_string(request_id);
    let response = match engines().lock() {
        Ok(engines) => match engines.get(&handle) {
            Some(engine) => {
                let Some(client) = engine.client.as_ref() else {
                    return new_java_string(
                        env,
                        error_payload(format!("engine handle {handle} is shut down")),
                    );
                };
                let result = if event_type == "server_request.failed" {
                    let error = JSONRPCErrorError {
                        code: payload
                            .get("code")
                            .and_then(Value::as_i64)
                            .unwrap_or(-32000),
                        message: payload
                            .get("message")
                            .and_then(Value::as_str)
                            .unwrap_or("server request failed")
                            .to_string(),
                        data: payload.get("data").cloned(),
                    };
                    engine
                        .runtime
                        .block_on(client.reject_server_request(request_id.clone(), error))
                } else {
                    engine
                        .runtime
                        .block_on(client.resolve_server_request(request_id.clone(), payload))
                };

                match result {
                    Ok(()) => json!({
                        "ok": true,
                        "handle": handle,
                        "requestId": request_id,
                    })
                    .to_string(),
                    Err(error) => error_payload(format!(
                        "failed to complete server request {request_id}: {error}"
                    )),
                }
            }
            None => error_payload(format!("unknown engine handle {handle}")),
        },
        Err(_) => error_payload("engine registry is unavailable"),
    };

    new_java_string(env, response)
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeShutdownEngine(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
) -> jstring {
    let handle = handle as i64;
    let state = match engines().lock() {
        Ok(mut engines) => engines.remove(&handle),
        Err(_) => return new_java_string(&env, error_payload("engine registry is unavailable")),
    };

    let response = match state {
        Some(mut engine) => {
            let remaining_events = engine.events.len();
            let shutdown = match engine.client.take() {
                Some(client) => engine.runtime.block_on(client.shutdown()),
                None => Ok(()),
            };
            match shutdown {
                Ok(()) => json!({
                    "ok": true,
                    "handle": handle,
                    "configJson": engine.config_json,
                    "codexHome": engine.codex_home,
                    "workspaceRoot": engine.workspace_root,
                    "remainingEvents": remaining_events,
                })
                .to_string(),
                Err(error) => json!({
                    "ok": false,
                    "handle": handle,
                    "configJson": engine.config_json,
                    "codexHome": engine.codex_home,
                    "workspaceRoot": engine.workspace_root,
                    "remainingEvents": remaining_events,
                    "error": format!("embedded Codex shutdown failed: {error}"),
                })
                .to_string(),
            }
        }
        None => error_payload(format!("unknown engine handle {handle}")),
    };

    new_java_string(&env, response)
}

#[no_mangle]
pub extern "system" fn Java_com_illumination_operator_engine_OperatorEngineBridge_nativeLastStartError(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    let error = match last_start_error().lock() {
        Ok(error) => error.clone(),
        Err(_) => Some("engine start error registry is unavailable".to_string()),
    };

    match error {
        Some(error) => new_java_string(&env, error),
        None => std::ptr::null_mut(),
    }
}
