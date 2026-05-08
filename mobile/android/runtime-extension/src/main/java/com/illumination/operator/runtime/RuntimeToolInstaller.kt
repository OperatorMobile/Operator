package com.illumination.operator.runtime

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.system.Os
import com.github.luben.zstd.ZstdInputStream
import org.json.JSONObject
import java.io.File
import java.io.InputStream

object RuntimeToolInstaller {
    private const val TOOL_ROOT_DIR = "tools"
    private const val TOOL_BIN_DIR = "tools/bin"
    private const val TOOL_LIB_DIR = "tools/lib"
    private const val INSTALL_MARKER = ".operator-runtime-tools-install"
    private const val ENV_SCRIPT_FILE = "operator-env.sh"
    private const val CA_BUNDLE_ASSET = "cacert.pem"
    private const val CA_BUNDLE_FILE = "cacert.pem"
    private const val RUNTIME_SDK_EXPANDED_ASSET_DIR = "operator-runtime-sdk-expanded"
    private const val RUNTIME_SDK_ARCHIVE_ASSET_DIR = "operator-runtime-sdk-archives"
    private const val RUNTIME_SDK_ROOT_DIR = "tools/runtime-sdk"
    private const val RUNTIME_SDK_CURRENT_DIR = "tools/runtime-sdk/current"
    private const val ZSTD_LIBRARY_NAME = "liboperator_tool_zstd.so"
    private const val WRAPPER_VERSION = "android-runtime-wrappers-v5"

    private val bundledTools = listOf(
        BundledTool("rg", "liboperator_tool_rg.so"),
        BundledTool("apply_patch", "liboperator_tool_apply_patch.so"),
        BundledTool("git", "liboperator_tool_git.so"),
        BundledTool("git-remote-http", "liboperator_tool_git_remote_http.so"),
        BundledTool("git-remote-https", "liboperator_tool_git_remote_http.so"),
        BundledTool(
            "busybox",
            "liboperator_tool_busybox.so",
            aliases = listOf(
                "awk",
                "basename",
                "clear",
                "date",
                "df",
                "diff",
                "dirname",
                "du",
                "echo",
                "env",
                "head",
                "hexdump",
                "id",
                "kill",
                "less",
                "md5sum",
                "mktemp",
                "od",
                "patch",
                "pidof",
                "ps",
                "readlink",
                "realpath",
                "sha1sum",
                "sha256sum",
                "ash",
                "sh",
                "sleep",
                "sort",
                "stat",
                "stty",
                "tail",
                "tee",
                "tr",
                "tty",
                "uname",
                "uniq",
                "uptime",
                "vi",
                "wc",
                "wget",
                "whoami",
                "which",
                "yes",
            ),
        ),
        BundledTool("ssh", "liboperator_tool_ssh.so"),
        BundledTool("scp", "liboperator_tool_scp.so"),
        BundledTool("sftp", "liboperator_tool_sftp.so"),
        BundledTool("ssh-add", "liboperator_tool_ssh_add.so"),
        BundledTool("ssh-agent", "liboperator_tool_ssh_agent.so"),
        BundledTool("ssh-keygen", "liboperator_tool_ssh_keygen.so"),
        BundledTool("ssh-keyscan", "liboperator_tool_ssh_keyscan.so"),
        BundledTool(
            "python3",
            "liboperator_tool_python3.so",
            aliases = listOf("python", "python3.13", "pip", "pip3", "pip3.13"),
        ),
        BundledTool("node", "liboperator_tool_node.so"),
        BundledTool("npm", "liboperator_tool_npm.so"),
        BundledTool("npx", "liboperator_tool_npx.so"),
        BundledTool("make", "liboperator_tool_make.so"),
        BundledTool("clang", "liboperator_tool_clang.so"),
        BundledTool("clang++", "liboperator_tool_clangxx.so"),
        BundledTool("cc", "liboperator_tool_cc.so"),
        BundledTool("c++", "liboperator_tool_cxx.so"),
        BundledTool("ld.lld", "liboperator_tool_ld_lld.so"),
        BundledTool("lld", "liboperator_tool_lld.so"),
        BundledTool("llvm-ar", "liboperator_tool_llvm_ar.so"),
        BundledTool("llvm-ranlib", "liboperator_tool_llvm_ranlib.so"),
        BundledTool("llvm-strip", "liboperator_tool_llvm_strip.so"),
        BundledTool("pkg-config", "liboperator_tool_pkg_config.so"),
        BundledTool("cmake", "liboperator_tool_cmake.so"),
        BundledTool("ctest", "liboperator_tool_ctest.so"),
        BundledTool("cpack", "liboperator_tool_cpack.so"),
        BundledTool("ninja", "liboperator_tool_ninja.so"),
        BundledTool("m4", "liboperator_tool_m4.so"),
        BundledTool("bison", "liboperator_tool_bison.so"),
        BundledTool("flex", "liboperator_tool_flex.so"),
        BundledTool("patchelf", "liboperator_tool_patchelf.so"),
        BundledTool("file", "liboperator_tool_file.so"),
        BundledTool("jq", "liboperator_tool_jq.so"),
        BundledTool("tree", "liboperator_tool_tree.so"),
        BundledTool("rsync", "liboperator_tool_rsync.so"),
        BundledTool("zip", "liboperator_tool_zip.so"),
        BundledTool("unzip", "liboperator_tool_unzip.so"),
        BundledTool("tar", "liboperator_tool_tar.so"),
        BundledTool("zstd", ZSTD_LIBRARY_NAME),
        BundledTool("gdb", "liboperator_tool_gdb.so"),
        BundledTool("strace", "liboperator_tool_strace.so"),
        BundledTool("rustc", "liboperator_tool_rustc.so"),
        BundledTool("cargo", "liboperator_tool_cargo.so"),
        BundledTool("rustdoc", "liboperator_tool_rustdoc.so"),
        BundledTool("rustfmt", "liboperator_tool_rustfmt.so"),
        BundledTool("perl", "liboperator_tool_perl.so"),
        BundledTool("bash", "liboperator_tool_bash.so"),
        BundledTool("zsh", "liboperator_tool_zsh.so"),
    )

    private val runtimeDirectories = listOf(
        "tmp",
        "tmp-run",
        "tmp-run/npm",
        "tmp-run/python-pycache",
        "xdg/cache",
        "xdg/config",
        "xdg/config/gh",
        "xdg/config/git",
        "xdg/data",
        "workspaces/default",
        "codex-home/.ssh",
        "tools/python",
        "tools/python/bin",
        "tools/python/lib",
        "tools/python-dev-libs",
        "tools/python-dev-libs/include",
        "tools/python-dev-libs/lib",
        "tools/python-dev-libs/lib/pkgconfig",
        "tools/python-user",
        "tools/python-user/bin",
        "tools/python-wheelhouse",
        "tools/node",
        "tools/node-global",
        "tools/node-global/bin",
        "tools/cargo",
        "tools/cargo/bin",
        "tools/rustup",
        "tools/runtime-sdk",
        "tools/runtime-sdk/versions",
        "tools/toolchain",
        "tools/toolchain/usr",
        "tools/toolchain/usr/bin",
        "tools/toolchain/usr/include",
        "tools/toolchain/usr/lib",
        "tools/toolchain/usr/lib/pkgconfig",
        "tools/toolchain/usr/tmp",
        "tools/share/git-core/templates",
        TOOL_LIB_DIR,
    )

    private val runtimeAssets = listOf(
        RuntimeAsset("python", "tools/python"),
        RuntimeAsset("python-dev-libs", "tools/python-dev-libs"),
        RuntimeAsset("python-wheelhouse", "tools/python-wheelhouse"),
        RuntimeAsset("node", "tools/node"),
        RuntimeAsset("toolchain", "tools/toolchain"),
    )

    fun install(context: Context): List<File> {
        val appFilesDir = context.filesDir
        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        val toolsDir = File(appFilesDir, TOOL_ROOT_DIR)
        val binDir = File(appFilesDir, TOOL_BIN_DIR)
        require(toolsDir.exists() || toolsDir.mkdirs()) {
            "failed to create ${toolsDir.absolutePath}"
        }
        require(binDir.exists() || binDir.mkdirs()) {
            "failed to create ${binDir.absolutePath}"
        }
        runtimeDirectories.forEach { relativePath ->
            val directory = File(appFilesDir, relativePath)
            require(directory.exists() || directory.mkdirs()) {
                "failed to create ${directory.absolutePath}"
            }
        }
        installCaBundle(context, File(toolsDir, CA_BUNDLE_FILE))
        installSharedNativeLibraries(nativeLibraryDir, File(appFilesDir, TOOL_LIB_DIR))
        val runtimeSdk = installRuntimeSdk(context, appFilesDir)
        runtimeAssets.forEach { asset ->
            if (runtimeSdk == null || asset.targetRelativePath != "tools/toolchain") {
                installRuntimeAsset(context, asset)
            }
        }
        installRuntimeEnvironmentScript(
            appFilesDir = appFilesDir,
            nativeLibraryDir = nativeLibraryDir,
            target = File(toolsDir, ENV_SCRIPT_FILE),
        )

        val availableTools = bundledTools.mapNotNull { tool ->
            val library = File(nativeLibraryDir, tool.libraryName)
            if (library.isFile && library.canExecute()) {
                AvailableTool(tool, library)
            } else {
                null
            }
        }
        val marker = installMarker(context, nativeLibraryDir, availableTools)
        val markerFile = File(binDir, INSTALL_MARKER)
        val generatedCommands = listOf("mktemp", "which", "with-temp-dir", "operator-doctor")
        val existingTools = availableTools.flatMap { tool ->
            tool.tool.commandNames.map { commandName -> File(binDir, commandName) }
        } + generatedCommands.map { commandName -> File(binDir, commandName) }
        if (
            markerFile.isFile &&
            markerFile.readText(Charsets.UTF_8) == marker &&
            existingTools.all { it.isFile && it.canExecute() }
        ) {
            return existingTools
        }

        availableTools.forEach { tool ->
            tool.tool.commandNames.forEach { commandName ->
                installToolCommand(
                    appFilesDir = appFilesDir,
                    nativeTarget = tool.library,
                    commandName = commandName,
                    link = File(binDir, commandName),
                    preferRuntimeSdk = true,
                )
            }
        }
        installGeneratedCommands(appFilesDir, binDir, generatedCommands)
        markerFile.writeText(marker, Charsets.UTF_8)
        markerFile.setReadable(true, true)
        markerFile.setWritable(true, true)

        return availableTools.flatMap { tool ->
            tool.tool.commandNames.map { commandName -> File(binDir, commandName) }
        } + generatedCommands.map { commandName -> File(binDir, commandName) }
    }

    private fun installCaBundle(context: Context, target: File) {
        val bytes = runCatching {
            context.assets.open(CA_BUNDLE_ASSET).use { it.readBytes() }
        }.getOrNull() ?: return
        if (target.isFile && target.length() == bytes.size.toLong()) {
            return
        }
        target.writeBytes(bytes)
        target.setReadable(true, true)
        target.setWritable(true, true)
    }

    private fun installSharedNativeLibraries(nativeLibraryDir: File, targetDir: File) {
        require(targetDir.exists() || targetDir.mkdirs()) {
            "failed to create ${targetDir.absolutePath}"
        }
        listOf("libc++_shared.so").forEach { libraryName ->
            val source = File(nativeLibraryDir, libraryName)
            val target = File(targetDir, libraryName)
            if (!source.isFile) {
                if (target.exists()) {
                    target.delete()
                }
                return@forEach
            }
            if (
                target.isFile &&
                target.length() == source.length() &&
                target.lastModified() >= source.lastModified()
            ) {
                return@forEach
            }
            source.copyTo(target, overwrite = true)
            target.setReadable(true, true)
            target.setWritable(true, true)
        }
    }

    private fun installRuntimeAsset(context: Context, asset: RuntimeAsset) {
        val assetRoot = runtimeAssetRoot(context, asset.assetName) ?: return
        val target = File(context.filesDir, asset.targetRelativePath)
        require(target.exists() || target.mkdirs()) {
            "failed to create ${target.absolutePath}"
        }
        val marker = File(target, ".operator-runtime-asset-${asset.assetName}")
        val markerValue = runtimeAssetMarker(context, assetRoot)
        if (marker.isFile && marker.readText(Charsets.UTF_8) == markerValue) {
            return
        }
        clearDirectoryContents(target)
        copyAssetTree(context, assetRoot, target)
        marker.writeText(markerValue, Charsets.UTF_8)
        marker.setReadable(true, true)
        marker.setWritable(true, true)
    }

    private fun installRuntimeSdk(context: Context, appFilesDir: File): File? {
        val assetRoot = runtimeSdkExpandedAssetRoot(context)
        val tarAsset = runtimeSdkTarAsset(context)
        if (assetRoot == null && tarAsset == null) {
            return null
        }

        val runtimeRoot = File(appFilesDir, RUNTIME_SDK_ROOT_DIR)
        val versionsDir = File(runtimeRoot, "versions")
        require(versionsDir.exists() || versionsDir.mkdirs()) {
            "failed to create ${versionsDir.absolutePath}"
        }

        val markerValue = runtimeSdkMarker(context, assetRoot ?: tarAsset.orEmpty())
        val versionName = runtimeSdkVersionName(context, assetRoot, tarAsset, markerValue)
        val target = File(versionsDir, versionName)
        val marker = File(target, ".operator-runtime-sdk-install")

        if (!target.isDirectory || !marker.isFile || marker.readText(Charsets.UTF_8) != markerValue) {
            deleteStaleRuntimeSdkTempDirs(versionsDir, versionName)
            val tmp = File(versionsDir, "$versionName.tmp-${System.nanoTime()}")
            if (tmp.exists()) {
                require(tmp.deleteRecursively()) {
                    "failed to remove stale runtime SDK temp directory ${tmp.absolutePath}"
                }
            }
            require(tmp.mkdirs()) {
                "failed to create runtime SDK temp directory ${tmp.absolutePath}"
            }
            if (tarAsset != null) {
                extractRuntimeSdkArchive(context, tarAsset, tmp)
            } else if (assetRoot != null) {
                copyAssetTree(context, assetRoot, tmp)
            }
            validateRuntimeSdkRoot(tmp)
            val tmpMarker = File(tmp, ".operator-runtime-sdk-install")
            tmpMarker.writeText(markerValue, Charsets.UTF_8)
            tmpMarker.setReadable(true, true)
            tmpMarker.setWritable(true, true)
            if (target.exists()) {
                require(target.deleteRecursively()) {
                    "failed to replace runtime SDK version ${target.absolutePath}"
                }
            }
            require(tmp.renameTo(target)) {
                "failed to install runtime SDK version ${target.absolutePath}"
            }
        }

        validateRuntimeSdkRoot(target)
        replaceWithSymlink(File(appFilesDir, RUNTIME_SDK_CURRENT_DIR), target)
        replaceWithSymlink(File(appFilesDir, "tools/toolchain"), target)
        File(runtimeRoot, ".operator-runtime-sdk-current").writeText(markerValue, Charsets.UTF_8)
        return target
    }

    private fun deleteStaleRuntimeSdkTempDirs(versionsDir: File, versionName: String) {
        versionsDir.listFiles().orEmpty()
            .filter { it.name.startsWith("$versionName.tmp-") }
            .forEach { tempDir ->
                require(tempDir.deleteRecursively()) {
                    "failed to remove stale runtime SDK temp directory ${tempDir.absolutePath}"
                }
            }
    }

    private fun runtimeSdkExpandedAssetRoot(context: Context): String? {
        val candidates = Build.SUPPORTED_ABIS.map { abi ->
            "$RUNTIME_SDK_EXPANDED_ASSET_DIR/$abi"
        } + RUNTIME_SDK_EXPANDED_ASSET_DIR
        return candidates.firstOrNull { candidate ->
            context.assets.list(candidate).orEmpty().contains("usr")
        }
    }

    private fun runtimeSdkTarAsset(context: Context): String? {
        val candidates = Build.SUPPORTED_ABIS.flatMap { abi ->
            runtimeSdkArchiveNames(context, "$RUNTIME_SDK_ARCHIVE_ASSET_DIR/$abi")
                .map { "$RUNTIME_SDK_ARCHIVE_ASSET_DIR/$abi/$it" }
        } + runtimeSdkArchiveNames(context, RUNTIME_SDK_ARCHIVE_ASSET_DIR)
            .map { "$RUNTIME_SDK_ARCHIVE_ASSET_DIR/$it" }
        return candidates.firstOrNull()
    }

    private fun runtimeSdkArchiveNames(context: Context, assetDir: String): List<String> =
        context.assets.list(assetDir).orEmpty()
            .filter { it.endsWith(".tar") || it.endsWith(".tar.zst") }
            .sortedWith(compareBy<String> { !it.endsWith(".tar.zst") }.thenBy { it })

    private fun extractRuntimeSdkArchive(context: Context, assetPath: String, targetRoot: File) {
        when {
            assetPath.endsWith(".tar") -> {
                context.assets.open(assetPath).use { input ->
                    extractTar(input, targetRoot)
                }
            }
            assetPath.endsWith(".tar.zst") -> {
                extractZstdTar(context, assetPath, targetRoot)
            }
            else -> error("unsupported runtime SDK archive asset: $assetPath")
        }
    }

    private fun extractZstdTar(context: Context, assetPath: String, targetRoot: File) {
        context.assets.open(assetPath).use { input ->
            ZstdInputStream(input).use { zstd ->
                extractTar(zstd, targetRoot)
            }
        }
    }

    private fun runtimeSdkMarker(context: Context, source: String): String {
        val packageInfo = packageInfo(context)
        return buildString {
            append("package=").append(context.packageName).append('\n')
            append("lastUpdateTime=").append(packageInfo.lastUpdateTime).append('\n')
            append("source=").append(source).append('\n')
        }
    }

    private fun runtimeSdkVersionName(
        context: Context,
        expandedAssetRoot: String?,
        tarAsset: String?,
        markerValue: String,
    ): String {
        val manifestPath = when {
            expandedAssetRoot != null -> "$expandedAssetRoot/manifest/operator-runtime-sdk.json"
            tarAsset != null -> null
            else -> null
        }
        val manifest = manifestPath?.let { path ->
            runCatching {
                context.assets.open(path).use { input ->
                    input.reader(Charsets.UTF_8).readText()
                }
            }.getOrNull()
        }
        val json = manifest?.let { runCatching { JSONObject(it) }.getOrNull() }
        val profile = safePathToken(json?.optString("profile").orEmpty().ifBlank { "runtime-sdk" })
        val version = safePathToken(json?.optString("version").orEmpty().ifBlank { "asset" })
        val abi = safePathToken(json?.optString("abi").orEmpty().ifBlank { Build.SUPPORTED_ABIS.firstOrNull().orEmpty() })
        return "$profile-$version-$abi-${Integer.toHexString(markerValue.hashCode())}"
    }

    private fun validateRuntimeSdkRoot(root: File) {
        require(File(root, "usr/bin").isDirectory) {
            "runtime SDK missing usr/bin: ${root.absolutePath}"
        }
        require(File(root, "manifest/operator-runtime-sdk.json").isFile) {
            "runtime SDK missing manifest/operator-runtime-sdk.json: ${root.absolutePath}"
        }
    }

    private fun replaceWithSymlink(link: File, target: File) {
        val existingTarget = runCatching { Os.readlink(link.absolutePath) }.getOrNull()
        if (existingTarget == target.absolutePath) {
            return
        }
        if (existingTarget != null) {
            require(link.delete()) {
                "failed to replace ${link.absolutePath}"
            }
        } else if (link.exists()) {
            require(link.deleteRecursively()) {
                "failed to replace ${link.absolutePath}"
            }
        }
        link.parentFile?.mkdirs()
        Os.symlink(target.absolutePath, link.absolutePath)
    }

    private fun runtimeAssetRoot(context: Context, assetName: String): String? {
        val candidates = Build.SUPPORTED_ABIS.map { abi ->
            "operator-runtimes/$abi/$assetName"
        } + "operator-runtimes/$assetName"
        return candidates.firstOrNull { candidate ->
            context.assets.list(candidate).orEmpty().isNotEmpty()
        }
    }

    private fun runtimeAssetMarker(context: Context, assetRoot: String): String {
        val packageInfo = packageInfo(context)
        return buildString {
            append("package=").append(context.packageName).append('\n')
            append("lastUpdateTime=").append(packageInfo.lastUpdateTime).append('\n')
            append("assetRoot=").append(assetRoot).append('\n')
        }
    }

    private fun copyAssetTree(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target.setReadable(true, true)
            target.setWritable(true, true)
            if (assetPath.contains("/bin/")) {
                target.setExecutable(true, true)
            }
            return
        }
        require(target.exists() || target.mkdirs()) {
            "failed to create ${target.absolutePath}"
        }
        children.forEach { child ->
            copyAssetTree(context, "$assetPath/$child", File(target, child))
        }
    }

    private fun clearDirectoryContents(directory: File) {
        directory.listFiles().orEmpty().forEach { child ->
            require(child.deleteRecursively()) {
                "failed to remove stale runtime asset ${child.absolutePath}"
            }
        }
    }

    private fun installToolLink(target: File, link: File) {
        if (link.exists() || isSymlink(link)) {
            require(link.delete()) {
                "failed to replace ${link.absolutePath}"
            }
        }
        Os.symlink(target.absolutePath, link.absolutePath)
    }

    private fun installToolCommand(
        appFilesDir: File,
        nativeTarget: File,
        commandName: String,
        link: File,
        preferRuntimeSdk: Boolean,
    ) {
        when (commandName) {
            "python", "python3", "python3.13" -> installScript(
                link,
                pythonScript(appFilesDir, nativeTarget),
            )
            "pip", "pip3", "pip3.13" -> installScript(
                link,
                pipScript(appFilesDir),
            )
            "node" -> installScript(
                link,
                nodeScript(appFilesDir, nativeTarget),
            )
            "npm" -> installScript(
                link,
                npmScript(appFilesDir, "npm-cli.js"),
            )
            "npx" -> installScript(
                link,
                npmScript(appFilesDir, "npx-cli.js"),
            )
            else -> if (preferRuntimeSdk) {
                installScript(
                    link,
                    runtimeSdkToolScript(appFilesDir, nativeTarget, commandName),
                )
            } else {
                installToolLink(nativeTarget, link)
            }
        }
    }

    private fun installGeneratedCommands(
        appFilesDir: File,
        binDir: File,
        commandNames: List<String>,
    ) {
        commandNames.forEach { commandName ->
            val target = File(binDir, commandName)
            when (commandName) {
                "mktemp" -> installScript(target, mktempScript(appFilesDir))
                "which" -> installScript(target, whichScript(appFilesDir))
                "with-temp-dir" -> installScript(target, withTempDirScript(appFilesDir))
                "operator-doctor" -> installScript(target, operatorDoctorScript(appFilesDir))
            }
        }
    }

    private fun installRuntimeEnvironmentScript(
        appFilesDir: File,
        nativeLibraryDir: File,
        target: File,
    ) {
        installTextFile(
            target = target,
            content = environmentScript(appFilesDir, nativeLibraryDir),
            executable = false,
        )
    }

    private fun installScript(target: File, content: String) {
        installTextFile(target, content, executable = true)
    }

    private fun installTextFile(target: File, content: String, executable: Boolean) {
        if (target.exists() || isSymlink(target)) {
            val current = if (target.isFile && !isSymlink(target)) {
                runCatching { target.readText(Charsets.UTF_8) }.getOrNull()
            } else {
                null
            }
            if (current != content || target.canExecute() != executable) {
                require(target.delete()) {
                    "failed to replace ${target.absolutePath}"
                }
            } else {
                return
            }
        }
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        target.setReadable(true, true)
        target.setWritable(true, true)
        target.setExecutable(executable, true)
    }

    private fun environmentScript(appFilesDir: File, nativeLibraryDir: File): String {
        val appDir = shellQuote(appFilesDir.absolutePath)
        val nativeDir = shellQuote(nativeLibraryDir.absolutePath)
        return """
            #!/system/bin/sh
            # Generated by Operator. Source this file before running bundled tools.
            OPERATOR_APP_FILES_DIR=$appDir
            OPERATOR_NATIVE_LIBRARY_DIR=$nativeDir
            OPERATOR_TOOLS="${'$'}OPERATOR_APP_FILES_DIR/tools"
            OPERATOR_TOOL_BIN="${'$'}OPERATOR_TOOLS/bin"
            OPERATOR_TOOL_LIB="${'$'}OPERATOR_TOOLS/lib"
            OPERATOR_CODEX_HOME="${'$'}OPERATOR_APP_FILES_DIR/codex-home"
            OPERATOR_TMP_ROOT="${'$'}OPERATOR_APP_FILES_DIR/tmp-run"
            OPERATOR_XDG_CACHE="${'$'}OPERATOR_APP_FILES_DIR/xdg/cache"
            OPERATOR_XDG_CONFIG="${'$'}OPERATOR_APP_FILES_DIR/xdg/config"
            OPERATOR_XDG_DATA="${'$'}OPERATOR_APP_FILES_DIR/xdg/data"
            OPERATOR_PYTHON_HOME="${'$'}OPERATOR_TOOLS/python"
            OPERATOR_PYTHON_DEV_LIBS="${'$'}OPERATOR_TOOLS/python-dev-libs"
            OPERATOR_PYTHON_USER_BASE="${'$'}OPERATOR_TOOLS/python-user"
            OPERATOR_NODE_HOME="${'$'}OPERATOR_TOOLS/node"
            OPERATOR_NODE_GLOBAL_PREFIX="${'$'}OPERATOR_TOOLS/node-global"
            OPERATOR_CARGO_HOME="${'$'}OPERATOR_TOOLS/cargo"
            OPERATOR_RUSTUP_HOME="${'$'}OPERATOR_TOOLS/rustup"
            OPERATOR_TOOLCHAIN_PREFIX="${'$'}OPERATOR_TOOLS/toolchain/usr"
            OPERATOR_GIT_EXEC_PATH="${'$'}OPERATOR_TOOLCHAIN_PREFIX/libexec/git-core"
            mkdir -p "${'$'}OPERATOR_CODEX_HOME" "${'$'}OPERATOR_TMP_ROOT" "${'$'}OPERATOR_TMP_ROOT/npm" "${'$'}OPERATOR_TMP_ROOT/python-pycache" "${'$'}OPERATOR_XDG_CACHE/pip" "${'$'}OPERATOR_XDG_CACHE/npm" "${'$'}OPERATOR_XDG_CONFIG/gh" "${'$'}OPERATOR_XDG_CONFIG/git" "${'$'}OPERATOR_XDG_DATA" "${'$'}OPERATOR_PYTHON_USER_BASE/bin" "${'$'}OPERATOR_NODE_GLOBAL_PREFIX/bin" "${'$'}OPERATOR_CARGO_HOME/bin" "${'$'}OPERATOR_RUSTUP_HOME" 2>/dev/null
            SYSTEM_PATH="/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin"
            export HOME="${'$'}{HOME:-${'$'}OPERATOR_CODEX_HOME}"
            export TMPDIR="${'$'}OPERATOR_TMP_ROOT"
            export TMP="${'$'}OPERATOR_TMP_ROOT"
            export TEMP="${'$'}OPERATOR_TMP_ROOT"
            export XDG_CACHE_HOME="${'$'}OPERATOR_XDG_CACHE"
            export XDG_CONFIG_HOME="${'$'}OPERATOR_XDG_CONFIG"
            export XDG_DATA_HOME="${'$'}OPERATOR_XDG_DATA"
            export PYTHONUSERBASE="${'$'}OPERATOR_PYTHON_USER_BASE"
            export PYTHONPYCACHEPREFIX="${'$'}OPERATOR_TMP_ROOT/python-pycache"
            export PIP_CACHE_DIR="${'$'}OPERATOR_XDG_CACHE/pip"
            export PIP_DISABLE_PIP_VERSION_CHECK=1
            export NODE_HOME="${'$'}OPERATOR_NODE_HOME"
            export NODE_PATH="${'$'}OPERATOR_NODE_HOME/lib/node_modules"
            export NPM_CONFIG_CACHE="${'$'}OPERATOR_XDG_CACHE/npm"
            export NPM_CONFIG_PREFIX="${'$'}OPERATOR_NODE_GLOBAL_PREFIX"
            export NPM_CONFIG_TMP="${'$'}OPERATOR_TMP_ROOT/npm"
            export NPM_CONFIG_UPDATE_NOTIFIER=false
            export NPM_CONFIG_FUND=false
            export NPM_CONFIG_AUDIT=false
            export CARGO_HOME="${'$'}OPERATOR_CARGO_HOME"
            export RUSTUP_HOME="${'$'}OPERATOR_RUSTUP_HOME"
            export TERMUX_PREFIX="${'$'}OPERATOR_TOOLCHAIN_PREFIX"
            export PREFIX="${'$'}OPERATOR_TOOLCHAIN_PREFIX"
            export GIT_CONFIG_GLOBAL="${'$'}OPERATOR_XDG_CONFIG/git/config"
            export GIT_EXEC_PATH="${'$'}OPERATOR_GIT_EXEC_PATH"
            case ",${'$'}{GODEBUG:-}," in
              *,netdns=*) ;;
              *) export GODEBUG="${'$'}{GODEBUG:+${'$'}GODEBUG,}netdns=cgo" ;;
            esac
            export PKG_CONFIG="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/pkg-config"
            export PKG_CONFIG_PATH="${'$'}OPERATOR_TOOLCHAIN_PREFIX/lib/pkgconfig:${'$'}OPERATOR_TOOLCHAIN_PREFIX/share/pkgconfig:${'$'}OPERATOR_PYTHON_DEV_LIBS/lib/pkgconfig${'$'}{PKG_CONFIG_PATH:+:${'$'}PKG_CONFIG_PATH}"
            export PKG_CONFIG_LIBDIR="${'$'}OPERATOR_TOOLCHAIN_PREFIX/lib/pkgconfig:${'$'}OPERATOR_TOOLCHAIN_PREFIX/share/pkgconfig:${'$'}OPERATOR_PYTHON_DEV_LIBS/lib/pkgconfig"
            export CMAKE_PREFIX_PATH="${'$'}OPERATOR_TOOLCHAIN_PREFIX:${'$'}OPERATOR_PYTHON_DEV_LIBS${'$'}{CMAKE_PREFIX_PATH:+:${'$'}CMAKE_PREFIX_PATH}"
            export CC="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/clang"
            export CXX="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/clang++"
            export AR="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/llvm-ar"
            export RANLIB="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/llvm-ranlib"
            export LD="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/ld.lld"
            export STRIP="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/llvm-strip"
            export MAKE="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/make"
            export npm_config_python="${'$'}OPERATOR_TOOL_BIN/python3"
            export npm_config_nodedir="${'$'}OPERATOR_NODE_HOME"
            export npm_config_cache="${'$'}NPM_CONFIG_CACHE"
            export npm_config_prefix="${'$'}NPM_CONFIG_PREFIX"
            export npm_config_tmp="${'$'}NPM_CONFIG_TMP"
            export OPERATOR_RUST_TARGET="${'$'}{OPERATOR_RUST_TARGET:-aarch64-linux-android}"
            export CARGO_BUILD_TARGET="${'$'}OPERATOR_RUST_TARGET"
            export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/clang"
            export CARGO_TARGET_AARCH64_LINUX_ANDROID_AR="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/llvm-ar"
            OPERATOR_CERT="${'$'}OPERATOR_TOOLS/cacert.pem"
            if [ -f "${'$'}OPERATOR_CERT" ]; then
              export SSL_CERT_FILE="${'$'}OPERATOR_CERT"
              export REQUESTS_CA_BUNDLE="${'$'}OPERATOR_CERT"
              export PIP_CERT="${'$'}OPERATOR_CERT"
              export CURL_CA_BUNDLE="${'$'}OPERATOR_CERT"
              export GIT_SSL_CAINFO="${'$'}OPERATOR_CERT"
            fi
            export PATH="${'$'}OPERATOR_TOOL_BIN:${'$'}OPERATOR_PYTHON_HOME/bin:${'$'}OPERATOR_PYTHON_USER_BASE/bin:${'$'}OPERATOR_NODE_GLOBAL_PREFIX/bin:${'$'}OPERATOR_CARGO_HOME/bin:${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin:${'$'}OPERATOR_GIT_EXEC_PATH:${'$'}{PATH:-${'$'}SYSTEM_PATH}"
            export LD_LIBRARY_PATH="${'$'}OPERATOR_TOOL_LIB:${'$'}OPERATOR_NATIVE_LIBRARY_DIR:${'$'}OPERATOR_PYTHON_HOME/lib:${'$'}OPERATOR_PYTHON_DEV_LIBS/lib:${'$'}OPERATOR_TOOLCHAIN_PREFIX/lib${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"
        """.trimIndent() + "\n"
    }

    private fun pythonScript(appFilesDir: File, nativeTarget: File): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        val fallback = shellQuote(nativeTarget.absolutePath)
        return """
            #!/system/bin/sh
            . $envScript
            PYTHON_BIN="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/python3"
            if [ ! -x "${'$'}PYTHON_BIN" ]; then
              PYTHON_BIN="${'$'}OPERATOR_PYTHON_HOME/bin/python3.13"
              if [ ! -x "${'$'}PYTHON_BIN" ]; then
                PYTHON_BIN=$fallback
                export PYTHONHOME="${'$'}OPERATOR_PYTHON_HOME"
              else
                unset PYTHONHOME
              fi
            fi
            unset PYTHONPATH
            exec "${'$'}PYTHON_BIN" "${'$'}@"
        """.trimIndent() + "\n"
    }

    private fun pipScript(appFilesDir: File): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        return """
            #!/system/bin/sh
            . $envScript
            if [ -z "${'$'}{VIRTUAL_ENV:-}" ]; then
              export PIP_USER="${'$'}{PIP_USER:-1}"
            fi
            exec "${'$'}OPERATOR_TOOL_BIN/python3" -m pip "${'$'}@"
        """.trimIndent() + "\n"
    }

    private fun nodeScript(appFilesDir: File, nativeTarget: File): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        val nativeNode = shellQuote(nativeTarget.absolutePath)
        return """
            #!/system/bin/sh
            . $envScript
            NODE_BIN="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/node"
            if [ ! -x "${'$'}NODE_BIN" ]; then
              NODE_BIN=$nativeNode
              if [ ! -x "${'$'}NODE_BIN" ]; then
                NODE_BIN="${'$'}OPERATOR_NODE_HOME/bin/node"
              fi
            fi
            exec "${'$'}NODE_BIN" "${'$'}@"
        """.trimIndent() + "\n"
    }

    private fun npmScript(appFilesDir: File, scriptName: String): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        val command = if (scriptName == "npx-cli.js") "npx" else "npm"
        return """
            #!/system/bin/sh
            . $envScript
            SDK_NPM="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/$command"
            if [ -x "${'$'}SDK_NPM" ]; then
              exec "${'$'}SDK_NPM" "${'$'}@"
            fi
            NPM_CLI="${'$'}OPERATOR_NODE_HOME/lib/node_modules/npm/bin/$scriptName"
            exec "${'$'}OPERATOR_TOOL_BIN/node" "${'$'}NPM_CLI" "${'$'}@"
        """.trimIndent() + "\n"
    }

    private fun runtimeSdkToolScript(appFilesDir: File, nativeTarget: File, commandName: String): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        val fallback = shellQuote(nativeTarget.absolutePath)
        val command = shellQuote(commandName)
        return """
            #!/system/bin/sh
            . $envScript
            SDK_COMMAND="${'$'}OPERATOR_TOOLCHAIN_PREFIX/bin/$commandName"
            if [ -x "${'$'}SDK_COMMAND" ]; then
              exec "${'$'}SDK_COMMAND" "${'$'}@"
            fi
            if [ -x $fallback ]; then
              exec $fallback "${'$'}@"
            fi
            printf '%s: command not installed in Operator runtime SDK\n' $command >&2
            exit 127
        """.trimIndent() + "\n"
    }

    private fun withTempDirScript(appFilesDir: File): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        return """
            #!/system/bin/sh
            . $envScript
            run_tmp="${'$'}(mktemp -d "${'$'}OPERATOR_TMP_ROOT/operator.XXXXXX")" || exit 1
            cleanup() { rm -rf "${'$'}run_tmp"; }
            trap cleanup EXIT HUP INT TERM
            export TMPDIR="${'$'}run_tmp"
            export TMP="${'$'}run_tmp"
            export TEMP="${'$'}run_tmp"
            if [ "${'$'}#" -eq 0 ]; then
              printf 'usage: with-temp-dir command [args...]\n' >&2
              exit 64
            fi
            "${'$'}@"
        """.trimIndent() + "\n"
    }

    private fun mktempScript(appFilesDir: File): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        return """
            #!/system/bin/sh
            . $envScript
            make_dir=0
            template=""
            parent=""
            while [ "${'$'}#" -gt 0 ]; do
              case "${'$'}1" in
                -d)
                  make_dir=1
                  ;;
                -p)
                  shift
                  [ "${'$'}#" -gt 0 ] || { printf 'mktemp: option requires an argument -- p\n' >&2; exit 64; }
                  parent="${'$'}1"
                  ;;
                -p*)
                  parent="${'$'}{1#-p}"
                  ;;
                -t)
                  shift
                  [ "${'$'}#" -gt 0 ] || { printf 'mktemp: option requires an argument -- t\n' >&2; exit 64; }
                  template="${'$'}{TMPDIR:-${'$'}OPERATOR_TMP_ROOT}/${'$'}1"
                  ;;
                --)
                  shift
                  break
                  ;;
                -*)
                  printf 'mktemp: unsupported option: %s\n' "${'$'}1" >&2
                  exit 64
                  ;;
                *)
                  template="${'$'}1"
                  ;;
              esac
              shift
            done
            if [ "${'$'}#" -gt 0 ]; then
              [ -z "${'$'}template" ] || { printf 'mktemp: too many templates\n' >&2; exit 64; }
              template="${'$'}1"
            fi
            if [ -z "${'$'}template" ]; then
              template="tmp.XXXXXX"
            fi
            if [ -n "${'$'}parent" ]; then
              case "${'$'}template" in
                */*) ;;
                *) template="${'$'}parent/${'$'}template" ;;
              esac
            fi
            case "${'$'}template" in
              */*) ;;
              *) template="${'$'}{TMPDIR:-${'$'}OPERATOR_TMP_ROOT}/${'$'}template" ;;
            esac
            mkdir -p "${'$'}{template%/*}" 2>/dev/null || true
            i=0
            while [ "${'$'}i" -lt 100 ]; do
              token="${'$'}${'$'}.${'$'}i"
              case "${'$'}template" in
                *XXXXXX*)
                  prefix="${'$'}{template%%XXXXXX*}"
                  suffix="${'$'}{template#*XXXXXX}"
                  candidate="${'$'}prefix${'$'}token${'$'}suffix"
                  ;;
                *)
                  candidate="${'$'}template.${'$'}token"
                  ;;
              esac
              if [ "${'$'}make_dir" -eq 1 ]; then
                if mkdir "${'$'}candidate" 2>/dev/null; then
                  printf '%s\n' "${'$'}candidate"
                  exit 0
                fi
              else
                if ( set -C; : > "${'$'}candidate" ) 2>/dev/null; then
                  printf '%s\n' "${'$'}candidate"
                  exit 0
                fi
              fi
              i="${'$'}((i + 1))"
            done
            printf 'mktemp: failed to create temporary path from template: %s\n' "${'$'}template" >&2
            exit 1
        """.trimIndent() + "\n"
    }

    private fun whichScript(appFilesDir: File): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        return """
            #!/system/bin/sh
            . $envScript
            [ "${'$'}#" -gt 0 ] || exit 1
            status=0
            for name in "${'$'}@"; do
              found=0
              case "${'$'}name" in
                */*)
                  if [ -x "${'$'}name" ]; then
                    printf '%s\n' "${'$'}name"
                    found=1
                  fi
                  ;;
                *)
                  old_ifs="${'$'}IFS"
                  IFS=:
                  for dir in ${'$'}PATH; do
                    [ -n "${'$'}dir" ] || dir=.
                    if [ -x "${'$'}dir/${'$'}name" ]; then
                      printf '%s\n' "${'$'}dir/${'$'}name"
                      found=1
                      break
                    fi
                  done
                  IFS="${'$'}old_ifs"
                  ;;
              esac
              [ "${'$'}found" -eq 1 ] || status=1
            done
            exit "${'$'}status"
        """.trimIndent() + "\n"
    }

    private fun operatorDoctorScript(appFilesDir: File): String {
        val envScript = shellQuote(File(appFilesDir, "$TOOL_ROOT_DIR/$ENV_SCRIPT_FILE").absolutePath)
        return """
            #!/system/bin/sh
            . $envScript
            network=0
            json=0
            for arg in "${'$'}@"; do
              case "${'$'}arg" in
                --network) network=1 ;;
                --json) json=1 ;;
                --help|-h)
                  printf 'usage: operator-doctor [--network] [--json]\n'
                  exit 0
                  ;;
              esac
            done
            check_command() {
              name="${'$'}1"
              if command -v "${'$'}name" >/dev/null 2>&1; then
                printf 'ok command %s %s\n' "${'$'}name" "$(command -v "${'$'}name")"
              else
                printf 'missing command %s\n' "${'$'}name"
                return 1
              fi
            }
            if [ "${'$'}json" -eq 1 ]; then
              printf '{"home":"%s","tmpdir":"%s","prefix":"%s"}\n' "${'$'}HOME" "${'$'}TMPDIR" "${'$'}OPERATOR_TOOLCHAIN_PREFIX"
              exit 0
            fi
            status=0
            printf 'Operator runtime doctor\n'
            printf 'home %s\n' "${'$'}HOME"
            printf 'tmpdir %s\n' "${'$'}TMPDIR"
            printf 'prefix %s\n' "${'$'}OPERATOR_TOOLCHAIN_PREFIX"
            [ -d "${'$'}HOME" ] || { printf 'missing home directory\n'; status=1; }
            [ -d "${'$'}TMPDIR" ] || { printf 'missing tmpdir\n'; status=1; }
            if tmp_probe="$(mktemp -d "${'$'}TMPDIR/doctor.XXXXXX" 2>/dev/null)"; then
              rmdir "${'$'}tmp_probe" 2>/dev/null || true
              printf 'ok mktemp %s\n' "${'$'}TMPDIR"
            else
              printf 'failed mktemp in %s\n' "${'$'}TMPDIR"
              status=1
            fi
            for name in sh which mktemp git ssh scp curl python3 pip node npm clang clang++ make cmake ninja pkg-config rustc cargo; do
              check_command "${'$'}name" || true
            done
            if [ -f "${'$'}OPERATOR_APP_FILES_DIR/tools/runtime-sdk/current/manifest/operator-runtime-sdk.json" ]; then
              printf 'ok runtime manifest %s\n' "${'$'}OPERATOR_APP_FILES_DIR/tools/runtime-sdk/current/manifest/operator-runtime-sdk.json"
            else
              printf 'missing runtime sdk manifest\n'
            fi
            if [ "${'$'}network" -eq 1 ]; then
              if command -v curl >/dev/null 2>&1; then
                curl -fsSI --max-time 15 https://github.com >/dev/null && printf 'ok tls curl github.com\n' || { printf 'failed tls curl github.com\n'; status=1; }
              fi
              if command -v git >/dev/null 2>&1; then
                git ls-remote https://github.com/git/git.git HEAD >/dev/null 2>&1 && printf 'ok git https\n' || printf 'warn git https failed\n'
              fi
              if command -v ssh >/dev/null 2>&1; then
                ssh -V 2>&1 | sed 's/^/ssh version /'
              fi
            fi
            exit "${'$'}status"
        """.trimIndent() + "\n"
    }

    private fun extractTar(input: InputStream, targetRoot: File) {
        val header = ByteArray(512)
        var globalPaxHeaders = emptyMap<String, String>()
        var pendingPaxHeaders = emptyMap<String, String>()
        while (true) {
            if (!readFully(input, header)) {
                return
            }
            if (header.all { it.toInt() == 0 }) {
                return
            }

            val name = tarString(header, 0, 100)
            val prefix = tarString(header, 345, 155)
            val headerPath = if (prefix.isBlank()) name else "$prefix/$name"
            val size = tarOctal(header, 124, 12)
            val mode = tarOctal(header, 100, 8)
            val type = header[156].toInt().toChar()
            val headerLinkName = tarString(header, 157, 100)

            if (type == 'x' || type == 'g') {
                val paxHeaders = parsePaxHeaders(readEntryString(input, size))
                if (type == 'g') {
                    globalPaxHeaders = globalPaxHeaders + paxHeaders
                } else {
                    pendingPaxHeaders = paxHeaders
                }
                skipTarPadding(input, size)
                continue
            }

            val paxHeaders = globalPaxHeaders + pendingPaxHeaders
            val path = paxHeaders["path"] ?: headerPath
            val linkName = paxHeaders["linkpath"] ?: headerLinkName
            pendingPaxHeaders = emptyMap()

            when (type) {
                '0', '\u0000' -> {
                    val file = safeTarTarget(targetRoot, path)
                    file.parentFile?.mkdirs()
                    if (file.exists() && file.isDirectory && !isSymlink(file)) {
                        require(file.deleteRecursively()) {
                            "failed to replace runtime SDK directory with file ${file.absolutePath}"
                        }
                    }
                    file.outputStream().use { output ->
                        copyExactly(input, output, size)
                    }
                    file.setReadable(true, true)
                    file.setWritable(true, true)
                    if ((mode and 0b001001001L) != 0L || path.contains("/bin/")) {
                        file.setExecutable(true, true)
                    }
                    skipTarPadding(input, size)
                }
                '5' -> {
                    val directory = safeTarTarget(targetRoot, path)
                    require(directory.exists() || directory.mkdirs()) {
                        "failed to create runtime SDK tar directory ${directory.absolutePath}"
                    }
                    skipExactly(input, size)
                    skipTarPadding(input, size)
                }
                '2' -> {
                    val link = safeTarTarget(targetRoot, path)
                    link.parentFile?.mkdirs()
                    if (isSymlink(link)) {
                        require(link.delete()) {
                            "failed to replace runtime SDK symlink ${link.absolutePath}"
                        }
                    } else if (link.exists()) {
                        require(link.deleteRecursively()) {
                            "failed to replace runtime SDK symlink ${link.absolutePath}"
                        }
                    }
                    Os.symlink(linkName, link.absolutePath)
                    skipExactly(input, size)
                    skipTarPadding(input, size)
                }
                else -> {
                    skipExactly(input, size)
                    skipTarPadding(input, size)
                }
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) {
                return offset == 0
            }
            offset += read
        }
        return true
    }

    private fun readEntryString(input: InputStream, size: Long): String =
        readEntryBytes(input, size).toString(Charsets.UTF_8)

    private fun readEntryBytes(input: InputStream, size: Long): ByteArray {
        require(size <= Int.MAX_VALUE) {
            "runtime SDK tar entry is too large to buffer: $size"
        }
        val buffer = ByteArray(size.toInt())
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            require(read >= 0) {
                "unexpected end of runtime SDK tar entry"
            }
            offset += read
        }
        return buffer
    }

    private fun parsePaxHeaders(payload: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var offset = 0
        while (offset < payload.length) {
            val space = payload.indexOf(' ', offset)
            if (space < 0) {
                break
            }
            val length = payload.substring(offset, space).toIntOrNull() ?: break
            val recordEnd = offset + length
            if (length <= 0 || recordEnd > payload.length) {
                break
            }
            val record = payload.substring(space + 1, recordEnd).trimEnd('\n')
            val equals = record.indexOf('=')
            if (equals > 0) {
                headers[record.substring(0, equals)] = record.substring(equals + 1)
            }
            offset = recordEnd
        }
        return headers
    }

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(read >= 0) {
                "unexpected end of runtime SDK tar entry"
            }
            output.write(buffer, 0, read)
            remaining -= read.toLong()
        }
    }

    private fun skipExactly(input: InputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(read >= 0) {
                "unexpected end of runtime SDK tar entry"
            }
            remaining -= read.toLong()
        }
    }

    private fun skipTarPadding(input: InputStream, size: Long) {
        val padding = (512 - (size % 512)) % 512
        if (padding > 0) {
            skipExactly(input, padding)
        }
    }

    private fun tarString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val max = offset + length
        while (end < max && buffer[end].toInt() != 0) {
            end += 1
        }
        return buffer.copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun tarOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        val value = tarString(buffer, offset, length).trim()
        if (value.isBlank()) {
            return 0L
        }
        return value.toLong(8)
    }

    private fun safeTarTarget(root: File, path: String): File {
        require(path.isNotBlank() && !path.startsWith("/")) {
            "unsafe absolute runtime SDK tar path: $path"
        }
        val file = File(root, path)
        val rootPath = root.canonicalPath
        val filePath = file.canonicalPath
        require(filePath == rootPath || filePath.startsWith("$rootPath/")) {
            "unsafe runtime SDK tar path escapes root: $path"
        }
        return file
    }

    private fun safePathToken(value: String): String {
        val token = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return token.ifBlank { "unknown" }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun isSymlink(file: File): Boolean =
        runCatching { Os.readlink(file.absolutePath) }.isSuccess

    private fun installMarker(
        context: Context,
        nativeLibraryDir: File,
        tools: List<AvailableTool>,
    ): String {
        val packageInfo = packageInfo(context)
        return buildString {
            append("package=").append(context.packageName).append('\n')
            append("wrapperVersion=").append(WRAPPER_VERSION).append('\n')
            append("lastUpdateTime=").append(packageInfo.lastUpdateTime).append('\n')
            append("nativeLibraryDir=").append(nativeLibraryDir.absolutePath).append('\n')
            tools.forEach { tool ->
                append(tool.tool.commandName)
                    .append('=')
                    .append(tool.library.absolutePath)
                    .append(':')
                    .append(tool.library.length())
                    .append(':')
                    .append(tool.library.lastModified())
                    .append(':')
                    .append(tool.tool.aliases.joinToString(","))
                    .append('\n')
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context): PackageInfo =
        context.packageManager.getPackageInfo(context.packageName, 0)

    private data class BundledTool(
        val commandName: String,
        val libraryName: String,
        val aliases: List<String> = emptyList(),
    ) {
        val commandNames: List<String> = (listOf(commandName) + aliases).distinct()
    }

    private data class RuntimeAsset(
        val assetName: String,
        val targetRelativePath: String,
    )

    private data class AvailableTool(
        val tool: BundledTool,
        val library: File,
    )
}
