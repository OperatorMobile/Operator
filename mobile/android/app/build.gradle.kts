plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties
import java.io.File
import java.util.zip.ZipFile

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val androidSdkDir = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: error("Android SDK not found. Set sdk.dir in local.properties.")
val androidNdkDir = "$androidSdkDir/ndk/30.0.14904198"
val cargoExecutable = localProperties.getProperty("cargo.path")?.takeIf { it.isNotBlank() }
    ?: System.getenv("CARGO")
    ?: "cargo"
val operatorRoot = rootProject.layout.projectDirectory.dir("../..")
val engineManifest = operatorRoot.file("operator-rs/mobile-android-engine/Cargo.toml")
val generatedJniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")
val generatedToolJniLibsDir = layout.buildDirectory.dir("generated/operatorToolJniLibs")
val generatedRuntimeAssetsDir = layout.buildDirectory.dir("generated/operatorRuntimeAssets")
val nativeAbis = localProperties.getProperty("native.abis")
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.takeIf { it.isNotEmpty() }
    ?: listOf("arm64-v8a")
val releaseSigningStoreFile = localProperties.getProperty("operator.signing.storeFile")?.takeIf { it.isNotBlank() }
val releaseSigningStorePassword = localProperties.getProperty("operator.signing.storePassword")?.takeIf { it.isNotBlank() }
val releaseSigningKeyAlias = localProperties.getProperty("operator.signing.keyAlias")?.takeIf { it.isNotBlank() }
val releaseSigningKeyPassword = localProperties.getProperty("operator.signing.keyPassword")?.takeIf { it.isNotBlank() }
val releaseSigningConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword,
).all { it != null }
data class PackagedAndroidTool(
    val propertyPrefix: String,
    val libraryName: String,
    val profiles: Set<String>,
)

val playTinyProfile = "playTiny"
val fullLocalProfile = "fullLocal"
val allLocalProfiles = setOf(playTinyProfile, fullLocalProfile)
val fullOnlyProfile = setOf(fullLocalProfile)

val packagedAndroidTools = listOf(
    PackagedAndroidTool("ripgrep.path", "liboperator_tool_rg.so", allLocalProfiles),
    PackagedAndroidTool("applyPatch.path", "liboperator_tool_apply_patch.so", allLocalProfiles),
    PackagedAndroidTool("git.path", "liboperator_tool_git.so", fullOnlyProfile),
    PackagedAndroidTool("gitRemoteHttp.path", "liboperator_tool_git_remote_http.so", fullOnlyProfile),
    PackagedAndroidTool("gh.path", "liboperator_tool_gh.so", fullOnlyProfile),
    PackagedAndroidTool("busybox.path", "liboperator_tool_busybox.so", allLocalProfiles),
    PackagedAndroidTool("ssh.path", "liboperator_tool_ssh.so", allLocalProfiles),
    PackagedAndroidTool("scp.path", "liboperator_tool_scp.so", allLocalProfiles),
    PackagedAndroidTool("sftp.path", "liboperator_tool_sftp.so", allLocalProfiles),
    PackagedAndroidTool("sshAdd.path", "liboperator_tool_ssh_add.so", allLocalProfiles),
    PackagedAndroidTool("sshAgent.path", "liboperator_tool_ssh_agent.so", allLocalProfiles),
    PackagedAndroidTool("sshKeygen.path", "liboperator_tool_ssh_keygen.so", allLocalProfiles),
    PackagedAndroidTool("sshKeyscan.path", "liboperator_tool_ssh_keyscan.so", allLocalProfiles),
    PackagedAndroidTool("python3.path", "liboperator_tool_python3.so", fullOnlyProfile),
    PackagedAndroidTool("node.path", "liboperator_tool_node.so", fullOnlyProfile),
    PackagedAndroidTool("npm.path", "liboperator_tool_npm.so", fullOnlyProfile),
    PackagedAndroidTool("npx.path", "liboperator_tool_npx.so", fullOnlyProfile),
    PackagedAndroidTool("make.path", "liboperator_tool_make.so", fullOnlyProfile),
    PackagedAndroidTool("patch.path", "liboperator_tool_patch.so", fullOnlyProfile),
    PackagedAndroidTool("diff.path", "liboperator_tool_diff.so", fullOnlyProfile),
    PackagedAndroidTool("androidClang.path", "liboperator_tool_clang.so", fullOnlyProfile),
    PackagedAndroidTool("androidClangxx.path", "liboperator_tool_clangxx.so", fullOnlyProfile),
    PackagedAndroidTool("androidCc.path", "liboperator_tool_cc.so", fullOnlyProfile),
    PackagedAndroidTool("androidCxx.path", "liboperator_tool_cxx.so", fullOnlyProfile),
    PackagedAndroidTool("androidLdLld.path", "liboperator_tool_ld_lld.so", fullOnlyProfile),
    PackagedAndroidTool("androidLld.path", "liboperator_tool_lld.so", fullOnlyProfile),
    PackagedAndroidTool("androidLlvmAr.path", "liboperator_tool_llvm_ar.so", fullOnlyProfile),
    PackagedAndroidTool("androidLlvmRanlib.path", "liboperator_tool_llvm_ranlib.so", fullOnlyProfile),
    PackagedAndroidTool("androidLlvmStrip.path", "liboperator_tool_llvm_strip.so", fullOnlyProfile),
    PackagedAndroidTool("androidPkgConfig.path", "liboperator_tool_pkg_config.so", fullOnlyProfile),
    PackagedAndroidTool("androidCmake.path", "liboperator_tool_cmake.so", fullOnlyProfile),
    PackagedAndroidTool("androidCtest.path", "liboperator_tool_ctest.so", fullOnlyProfile),
    PackagedAndroidTool("androidCpack.path", "liboperator_tool_cpack.so", fullOnlyProfile),
    PackagedAndroidTool("androidNinja.path", "liboperator_tool_ninja.so", fullOnlyProfile),
    PackagedAndroidTool("androidM4.path", "liboperator_tool_m4.so", fullOnlyProfile),
    PackagedAndroidTool("androidBison.path", "liboperator_tool_bison.so", fullOnlyProfile),
    PackagedAndroidTool("androidFlex.path", "liboperator_tool_flex.so", fullOnlyProfile),
    PackagedAndroidTool("androidPatchelf.path", "liboperator_tool_patchelf.so", fullOnlyProfile),
    PackagedAndroidTool("androidFile.path", "liboperator_tool_file.so", fullOnlyProfile),
    PackagedAndroidTool("androidJq.path", "liboperator_tool_jq.so", fullOnlyProfile),
    PackagedAndroidTool("androidTree.path", "liboperator_tool_tree.so", fullOnlyProfile),
    PackagedAndroidTool("androidRsync.path", "liboperator_tool_rsync.so", fullOnlyProfile),
    PackagedAndroidTool("androidZip.path", "liboperator_tool_zip.so", fullOnlyProfile),
    PackagedAndroidTool("androidUnzip.path", "liboperator_tool_unzip.so", fullOnlyProfile),
    PackagedAndroidTool("androidTar.path", "liboperator_tool_tar.so", fullOnlyProfile),
    PackagedAndroidTool("androidZstd.path", "liboperator_tool_zstd.so", fullOnlyProfile),
    PackagedAndroidTool("androidGdb.path", "liboperator_tool_gdb.so", fullOnlyProfile),
    PackagedAndroidTool("androidStrace.path", "liboperator_tool_strace.so", fullOnlyProfile),
    PackagedAndroidTool("androidRustc.path", "liboperator_tool_rustc.so", fullOnlyProfile),
    PackagedAndroidTool("androidCargo.path", "liboperator_tool_cargo.so", fullOnlyProfile),
    PackagedAndroidTool("androidRustdoc.path", "liboperator_tool_rustdoc.so", fullOnlyProfile),
    PackagedAndroidTool("androidRustfmt.path", "liboperator_tool_rustfmt.so", fullOnlyProfile),
    PackagedAndroidTool("androidPerl.path", "liboperator_tool_perl.so", fullOnlyProfile),
    PackagedAndroidTool("androidBash.path", "liboperator_tool_bash.so", fullOnlyProfile),
    PackagedAndroidTool("androidZsh.path", "liboperator_tool_zsh.so", fullOnlyProfile),
)
val packagedAndroidRuntimeAssets = listOf(
    "pythonHome.path" to "python",
    "pythonDevLibs.path" to "python-dev-libs",
    "pythonWheelhouse.path" to "python-wheelhouse",
    "nodeHome.path" to "node",
    "toolchain.path" to "toolchain",
)
val packagedAndroidRuntimeSdkAssets = listOf(
    "runtimeSdk.path" to "operator-runtime-sdk-expanded",
)
val packagedAndroidRuntimeSdkArchives = listOf(
    "runtimeSdkArchive.path" to "operator-runtime-sdk-archives",
)

configurations.configureEach {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

fun String.toTaskSuffix(): String = split('-', '_')
    .filter { it.isNotBlank() }
    .joinToString("") { token -> token.replaceFirstChar { it.uppercaseChar() } }

fun localPath(value: String): String {
    val file = File(value)
    return if (file.isAbsolute) file.absolutePath else rootProject.file(value).absolutePath
}

fun androidNdkPrebuiltDir(): File {
    val prebuiltRoot = File(androidNdkDir, "toolchains/llvm/prebuilt")
    return prebuiltRoot.listFiles()
        ?.firstOrNull { file ->
            file.isDirectory && (file.name.startsWith("darwin-") || file.name.startsWith("linux-"))
        }
        ?: error("Android NDK prebuilt toolchain not found under ${prebuiltRoot.absolutePath}")
}

fun androidNdkTriple(abi: String): String =
    when (abi) {
        "arm64-v8a" -> "aarch64-linux-android"
        "x86_64" -> "x86_64-linux-android"
        "armeabi-v7a" -> "arm-linux-androideabi"
        "x86" -> "i686-linux-android"
        else -> error("Unsupported Android ABI for libc++ packaging: $abi")
    }

fun androidNdkLibcxxShared(abi: String): File =
    File(androidNdkPrebuiltDir(), "sysroot/usr/lib/${androidNdkTriple(abi)}/libc++_shared.so")

android {
    namespace = "com.illumination.operator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.illumination.operator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += nativeAbis
        }
    }

    flavorDimensions += "distribution"

    signingConfigs {
        if (releaseSigningConfigured) {
            create("operatorRelease") {
                storeFile = file(localPath(releaseSigningStoreFile!!))
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    productFlavors {
        create("play") {
            dimension = "distribution"
            applicationId = "com.illumination.operator.gplay"
            targetSdk = 35
            versionNameSuffix = "-play"
            buildConfigField("String", "OPERATOR_DISTRIBUTION", "\"play\"")
            buildConfigField("String", "OPERATOR_DISTRIBUTION_LABEL", "\"Google Play UI\"")
            buildConfigField("String", "OPERATOR_TOOL_PROFILE", "\"$playTinyProfile\"")
            buildConfigField("Boolean", "OPERATOR_FULL_LOCAL_RUNTIME", "false")
        }

        create("full") {
            dimension = "distribution"
            applicationId = "com.illumination.operator"
            targetSdk = 28
            versionNameSuffix = "-full"
            buildConfigField("String", "OPERATOR_DISTRIBUTION", "\"full\"")
            buildConfigField("String", "OPERATOR_DISTRIBUTION_LABEL", "\"Full Operator\"")
            buildConfigField("String", "OPERATOR_TOOL_PROFILE", "\"$fullLocalProfile\"")
            buildConfigField("Boolean", "OPERATOR_FULL_LOCAL_RUNTIME", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("operatorRelease")
            }
        }
    }

    ndkVersion = "30.0.14904198"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.gitignore:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!__pycache__:!*.pyc:!*~"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val buildOperatorEngineAbiTasks = nativeAbis.map { abi ->
    tasks.register<Exec>("buildOperatorEngine${abi.toTaskSuffix()}") {
        group = "build"
        description = "Builds the Operator Rust engine JNI library for $abi with cargo-ndk."
        workingDir = engineManifest.asFile.parentFile
        inputs.files(fileTree(operatorRoot.dir("operator-rs/mobile-android-engine/src")))
        inputs.files(fileTree(operatorRoot.dir("third_party/codex/codex-rs")) {
            include("**/*.rs")
            include("**/Cargo.toml")
            exclude("**/target/**")
        })
        inputs.file(engineManifest)
        inputs.file(operatorRoot.file("operator-rs/mobile-android-engine/build.rs"))
        outputs.dir(layout.projectDirectory.dir("src/main/jniLibs/$abi"))

        environment("ANDROID_HOME", androidSdkDir)
        environment("ANDROID_NDK_HOME", androidNdkDir)

        localProperties.getProperty("rustyV8Archive.$abi")
            ?.takeIf { it.isNotBlank() }
            ?.let { environment("RUSTY_V8_ARCHIVE", localPath(it)) }

        localProperties.getProperty("rustyV8Binding.$abi")
            ?.takeIf { it.isNotBlank() }
            ?.let { environment("RUSTY_V8_SRC_BINDING_PATH", localPath(it)) }

        val buildV8FromSource = localProperties.getProperty("rustyV8FromSource.$abi")
            ?: localProperties.getProperty("rustyV8FromSource")
        if (buildV8FromSource.equals("true", ignoreCase = true)) {
            environment("V8_FROM_SOURCE", "1")
        }

        commandLine(
            cargoExecutable,
            "ndk",
            "--manifest-path",
            engineManifest.asFile.absolutePath,
            "--platform",
            "26",
            "-t",
            abi,
            "-o",
            generatedJniLibsDir.asFile.absolutePath,
            "build"
        )
    }
}

val buildOperatorEngine by tasks.registering {
    group = "build"
    description = "Builds the Operator Rust engine JNI libraries with cargo-ndk."
    dependsOn(buildOperatorEngineAbiTasks)
}

fun registerPrepareOperatorToolJniLibsTask(
    flavorName: String,
    toolProfile: String,
) = tasks.register<Sync>("prepare${flavorName.toTaskSuffix()}OperatorToolJniLibs") {
    group = "build"
    description = "Stages $toolProfile Android command-line tools as executable native libraries."
    into(generatedToolJniLibsDir.map { it.dir(flavorName) })

    nativeAbis.forEach { abi ->
        packagedAndroidTools
            .filter { tool -> toolProfile in tool.profiles }
            .forEach { tool ->
                localProperties.getProperty("${tool.propertyPrefix}.$abi")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { toolPath ->
                        from(localPath(toolPath)) {
                            into(abi)
                            rename { tool.libraryName }
                        }
                    }
            }
        if (toolProfile == fullLocalProfile) {
            val libcxx = androidNdkLibcxxShared(abi)
            if (libcxx.isFile) {
                from(libcxx) {
                    into(abi)
                    rename { "libc++_shared.so" }
                }
            }
        }
    }
}

fun registerPrepareOperatorRuntimeAssetsTask(
    flavorName: String,
    includeRuntimeAssets: Boolean,
) = tasks.register<Sync>("prepare${flavorName.toTaskSuffix()}OperatorRuntimeAssets") {
    group = "build"
    description = "Stages optional Android runtime data trees as APK assets for $flavorName."
    into(generatedRuntimeAssetsDir.map { it.dir(flavorName) })

    if (includeRuntimeAssets) {
        nativeAbis.forEach { abi ->
            packagedAndroidRuntimeSdkAssets.forEach { (propertyPrefix, assetName) ->
                val runtimePath = localProperties.getProperty("$propertyPrefix.$abi")
                    ?: localProperties.getProperty(propertyPrefix)
                runtimePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sourcePath ->
                        from(localPath(sourcePath)) {
                            into("$assetName/$abi")
                        }
                    }
            }
            packagedAndroidRuntimeSdkArchives.forEach { (propertyPrefix, assetName) ->
                val archivePath = localProperties.getProperty("$propertyPrefix.$abi")
                    ?: localProperties.getProperty(propertyPrefix)
                archivePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sourcePath ->
                        from(localPath(sourcePath)) {
                            into("$assetName/$abi")
                        }
                    }
            }
            packagedAndroidRuntimeAssets.forEach { (propertyPrefix, assetName) ->
                val runtimePath = localProperties.getProperty("$propertyPrefix.$abi")
                    ?: localProperties.getProperty(propertyPrefix)
                runtimePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sourcePath ->
                        from(localPath(sourcePath)) {
                            into("operator-runtimes/$abi/$assetName")
                        }
                    }
            }
        }
    }
}

val preparePlayOperatorToolJniLibs = registerPrepareOperatorToolJniLibsTask("play", playTinyProfile)
val prepareFullOperatorToolJniLibs = registerPrepareOperatorToolJniLibsTask("full", fullLocalProfile)
val preparePlayOperatorRuntimeAssets = registerPrepareOperatorRuntimeAssetsTask(
    flavorName = "play",
    includeRuntimeAssets = false,
)
val prepareFullOperatorRuntimeAssets = registerPrepareOperatorRuntimeAssetsTask(
    flavorName = "full",
    includeRuntimeAssets = true,
)

android.sourceSets.named("play") {
    jniLibs.srcDir(generatedToolJniLibsDir.map { it.dir("play") })
    assets.srcDir(generatedRuntimeAssetsDir.map { it.dir("play") })
}

android.sourceSets.named("full") {
    jniLibs.srcDir(generatedToolJniLibsDir.map { it.dir("full") })
    assets.srcDir(generatedRuntimeAssetsDir.map { it.dir("full") })
}

tasks.named("preBuild") {
    dependsOn(buildOperatorEngine)
}

val playPreBuildTasks = setOf("prePlayDebugBuild", "prePlayReleaseBuild")
tasks.matching { it.name in playPreBuildTasks }.configureEach {
    dependsOn(preparePlayOperatorToolJniLibs)
    dependsOn(preparePlayOperatorRuntimeAssets)
}

val fullPreBuildTasks = setOf("preFullDebugBuild", "preFullReleaseBuild")
tasks.matching { it.name in fullPreBuildTasks }.configureEach {
    dependsOn(prepareFullOperatorToolJniLibs)
    dependsOn(prepareFullOperatorRuntimeAssets)
}

fun configuredArtifact(propertyPrefix: String): Boolean =
    localProperties.getProperty(propertyPrefix)?.isNotBlank() == true ||
        nativeAbis.any { abi -> localProperties.getProperty("$propertyPrefix.$abi")?.isNotBlank() == true }

fun zipEntries(apk: File): Set<String> =
    ZipFile(apk).use { zip ->
        zip.entries().asSequence().map { it.name }.toSet()
    }

fun Set<String>.containsEntryFragment(fragment: String): Boolean =
    any { entry -> entry.contains(fragment) }

fun registerVerifyApkProfileTask(
    taskName: String,
    assembleTaskPath: String,
    apk: Provider<File>,
    configuredRequiredFragments: List<Pair<String, String>>,
    forbiddenFragments: List<String>,
) = tasks.register(taskName) {
    group = "verification"
    description = "Verifies Android distribution profile contents for $taskName."
    dependsOn(assembleTaskPath)
    inputs.file(apk)
    doLast {
        val apkFile = apk.get()
        require(apkFile.isFile) { "APK not found: ${apkFile.absolutePath}" }
        val entries = zipEntries(apkFile)
        configuredRequiredFragments.forEach { (propertyPrefix, entryFragment) ->
            if (configuredArtifact(propertyPrefix)) {
                require(entries.containsEntryFragment(entryFragment)) {
                    "${apkFile.name} is missing configured artifact $entryFragment from $propertyPrefix"
                }
            }
        }
        forbiddenFragments.forEach { fragment ->
            require(!entries.containsEntryFragment(fragment)) {
                "${apkFile.name} must not contain $fragment"
            }
        }
    }
}

val tinyProfileConfiguredArtifacts = listOf(
    "ripgrep.path" to "liboperator_tool_rg.so",
    "applyPatch.path" to "liboperator_tool_apply_patch.so",
    "busybox.path" to "liboperator_tool_busybox.so",
    "ssh.path" to "liboperator_tool_ssh.so",
    "scp.path" to "liboperator_tool_scp.so",
    "sftp.path" to "liboperator_tool_sftp.so",
    "sshAdd.path" to "liboperator_tool_ssh_add.so",
    "sshAgent.path" to "liboperator_tool_ssh_agent.so",
    "sshKeygen.path" to "liboperator_tool_ssh_keygen.so",
    "sshKeyscan.path" to "liboperator_tool_ssh_keyscan.so",
)
val fullProfileConfiguredArtifacts = tinyProfileConfiguredArtifacts + listOf(
    "runtimeSdk.path" to "operator-runtime-sdk-expanded/",
    "runtimeSdkArchive.path" to "operator-runtime-sdk-archives/",
    "androidBash.path" to "liboperator_tool_bash.so",
    "androidZsh.path" to "liboperator_tool_zsh.so",
    "git.path" to "liboperator_tool_git.so",
    "gitRemoteHttp.path" to "liboperator_tool_git_remote_http.so",
    "gh.path" to "liboperator_tool_gh.so",
    "python3.path" to "liboperator_tool_python3.so",
    "node.path" to "liboperator_tool_node.so",
    "node.path" to "libc++_shared.so",
    "npm.path" to "liboperator_tool_npm.so",
    "npx.path" to "liboperator_tool_npx.so",
    "make.path" to "liboperator_tool_make.so",
    "pythonHome.path" to "libpython3.13.so",
    "pythonHome.path" to "/python/",
    "nodeHome.path" to "/node/",
    "toolchain.path" to "/toolchain/",
    "androidClang.path" to "liboperator_tool_clang.so",
    "androidClangxx.path" to "liboperator_tool_clangxx.so",
    "androidCc.path" to "liboperator_tool_cc.so",
    "androidCxx.path" to "liboperator_tool_cxx.so",
    "androidLdLld.path" to "liboperator_tool_ld_lld.so",
    "androidLld.path" to "liboperator_tool_lld.so",
    "androidLlvmAr.path" to "liboperator_tool_llvm_ar.so",
    "androidLlvmRanlib.path" to "liboperator_tool_llvm_ranlib.so",
    "androidLlvmStrip.path" to "liboperator_tool_llvm_strip.so",
    "androidPkgConfig.path" to "liboperator_tool_pkg_config.so",
    "androidCmake.path" to "liboperator_tool_cmake.so",
    "androidNinja.path" to "liboperator_tool_ninja.so",
    "androidZstd.path" to "liboperator_tool_zstd.so",
    "androidRustc.path" to "liboperator_tool_rustc.so",
    "androidCargo.path" to "liboperator_tool_cargo.so",
    "androidRustdoc.path" to "liboperator_tool_rustdoc.so",
    "androidRustfmt.path" to "liboperator_tool_rustfmt.so",
)
val playForbiddenFragments = listOf(
    "assets/operator-runtimes/",
    "liboperator_tool_git.so",
    "liboperator_tool_git_remote_http.so",
    "liboperator_tool_gh.so",
    "liboperator_tool_python3.so",
    "liboperator_tool_node.so",
    "liboperator_tool_npm.so",
    "liboperator_tool_npx.so",
    "liboperator_tool_clang.so",
    "liboperator_tool_bash.so",
    "liboperator_tool_zsh.so",
    "liboperator_tool_cargo.so",
    "liboperator_tool_rustc.so",
)
val verifyPlayDebugDistributionProfile = registerVerifyApkProfileTask(
    taskName = "verifyPlayDebugDistributionProfile",
    assembleTaskPath = "assemblePlayDebug",
    apk = layout.buildDirectory.file("outputs/apk/play/debug/app-play-debug.apk").map { it.asFile },
    configuredRequiredFragments = tinyProfileConfiguredArtifacts,
    forbiddenFragments = playForbiddenFragments,
)
val verifyFullDebugDistributionProfile = registerVerifyApkProfileTask(
    taskName = "verifyFullDebugDistributionProfile",
    assembleTaskPath = "assembleFullDebug",
    apk = layout.buildDirectory.file("outputs/apk/full/debug/app-full-debug.apk").map { it.asFile },
    configuredRequiredFragments = fullProfileConfiguredArtifacts,
    forbiddenFragments = emptyList(),
)
val verifyRuntimeExtensionDebugDistributionProfile = registerVerifyApkProfileTask(
    taskName = "verifyRuntimeExtensionDebugDistributionProfile",
    assembleTaskPath = ":runtime-extension:assembleDebug",
    apk = providers.provider {
        rootProject.layout.projectDirectory.file("runtime-extension/build/outputs/apk/debug/runtime-extension-debug.apk").asFile
    },
    configuredRequiredFragments = fullProfileConfiguredArtifacts,
    forbiddenFragments = emptyList(),
)

tasks.register("verifyAndroidDistributionProfiles") {
    group = "verification"
    description = "Verifies Play, full, and runtime-extension APK profile boundaries."
    dependsOn(
        verifyPlayDebugDistributionProfile,
        verifyFullDebugDistributionProfile,
        verifyRuntimeExtensionDebugDistributionProfile,
    )
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.github.luben:zstd-jni:1.5.7-7@aar")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:syntax-highlight:4.6.2")
    compileOnly("io.noties:prism4j-bundler:2.0.0")
    kapt("io.noties:prism4j-bundler:2.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
