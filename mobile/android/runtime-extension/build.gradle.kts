plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.io.File
import java.util.Properties

data class PackagedRuntimeTool(
    val propertyPrefix: String,
    val libraryName: String,
)

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
val generatedToolJniLibsDir = layout.buildDirectory.dir("generated/runtimeToolJniLibs")
val generatedRuntimeAssetsDir = layout.buildDirectory.dir("generated/runtimeAssets")
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

val packagedRuntimeTools = listOf(
    PackagedRuntimeTool("ripgrep.path", "liboperator_tool_rg.so"),
    PackagedRuntimeTool("applyPatch.path", "liboperator_tool_apply_patch.so"),
    PackagedRuntimeTool("git.path", "liboperator_tool_git.so"),
    PackagedRuntimeTool("gitRemoteHttp.path", "liboperator_tool_git_remote_http.so"),
    PackagedRuntimeTool("gh.path", "liboperator_tool_gh.so"),
    PackagedRuntimeTool("busybox.path", "liboperator_tool_busybox.so"),
    PackagedRuntimeTool("ssh.path", "liboperator_tool_ssh.so"),
    PackagedRuntimeTool("scp.path", "liboperator_tool_scp.so"),
    PackagedRuntimeTool("sftp.path", "liboperator_tool_sftp.so"),
    PackagedRuntimeTool("sshAdd.path", "liboperator_tool_ssh_add.so"),
    PackagedRuntimeTool("sshAgent.path", "liboperator_tool_ssh_agent.so"),
    PackagedRuntimeTool("sshKeygen.path", "liboperator_tool_ssh_keygen.so"),
    PackagedRuntimeTool("sshKeyscan.path", "liboperator_tool_ssh_keyscan.so"),
    PackagedRuntimeTool("python3.path", "liboperator_tool_python3.so"),
    PackagedRuntimeTool("node.path", "liboperator_tool_node.so"),
    PackagedRuntimeTool("npm.path", "liboperator_tool_npm.so"),
    PackagedRuntimeTool("npx.path", "liboperator_tool_npx.so"),
    PackagedRuntimeTool("make.path", "liboperator_tool_make.so"),
    PackagedRuntimeTool("patch.path", "liboperator_tool_patch.so"),
    PackagedRuntimeTool("diff.path", "liboperator_tool_diff.so"),
    PackagedRuntimeTool("androidClang.path", "liboperator_tool_clang.so"),
    PackagedRuntimeTool("androidClangxx.path", "liboperator_tool_clangxx.so"),
    PackagedRuntimeTool("androidCc.path", "liboperator_tool_cc.so"),
    PackagedRuntimeTool("androidCxx.path", "liboperator_tool_cxx.so"),
    PackagedRuntimeTool("androidLdLld.path", "liboperator_tool_ld_lld.so"),
    PackagedRuntimeTool("androidLld.path", "liboperator_tool_lld.so"),
    PackagedRuntimeTool("androidLlvmAr.path", "liboperator_tool_llvm_ar.so"),
    PackagedRuntimeTool("androidLlvmRanlib.path", "liboperator_tool_llvm_ranlib.so"),
    PackagedRuntimeTool("androidLlvmStrip.path", "liboperator_tool_llvm_strip.so"),
    PackagedRuntimeTool("androidPkgConfig.path", "liboperator_tool_pkg_config.so"),
    PackagedRuntimeTool("androidCmake.path", "liboperator_tool_cmake.so"),
    PackagedRuntimeTool("androidCtest.path", "liboperator_tool_ctest.so"),
    PackagedRuntimeTool("androidCpack.path", "liboperator_tool_cpack.so"),
    PackagedRuntimeTool("androidNinja.path", "liboperator_tool_ninja.so"),
    PackagedRuntimeTool("androidM4.path", "liboperator_tool_m4.so"),
    PackagedRuntimeTool("androidBison.path", "liboperator_tool_bison.so"),
    PackagedRuntimeTool("androidFlex.path", "liboperator_tool_flex.so"),
    PackagedRuntimeTool("androidPatchelf.path", "liboperator_tool_patchelf.so"),
    PackagedRuntimeTool("androidFile.path", "liboperator_tool_file.so"),
    PackagedRuntimeTool("androidJq.path", "liboperator_tool_jq.so"),
    PackagedRuntimeTool("androidTree.path", "liboperator_tool_tree.so"),
    PackagedRuntimeTool("androidRsync.path", "liboperator_tool_rsync.so"),
    PackagedRuntimeTool("androidZip.path", "liboperator_tool_zip.so"),
    PackagedRuntimeTool("androidUnzip.path", "liboperator_tool_unzip.so"),
    PackagedRuntimeTool("androidTar.path", "liboperator_tool_tar.so"),
    PackagedRuntimeTool("androidZstd.path", "liboperator_tool_zstd.so"),
    PackagedRuntimeTool("androidGdb.path", "liboperator_tool_gdb.so"),
    PackagedRuntimeTool("androidStrace.path", "liboperator_tool_strace.so"),
    PackagedRuntimeTool("androidRustc.path", "liboperator_tool_rustc.so"),
    PackagedRuntimeTool("androidCargo.path", "liboperator_tool_cargo.so"),
    PackagedRuntimeTool("androidRustdoc.path", "liboperator_tool_rustdoc.so"),
    PackagedRuntimeTool("androidRustfmt.path", "liboperator_tool_rustfmt.so"),
    PackagedRuntimeTool("androidPerl.path", "liboperator_tool_perl.so"),
    PackagedRuntimeTool("androidBash.path", "liboperator_tool_bash.so"),
    PackagedRuntimeTool("androidZsh.path", "liboperator_tool_zsh.so"),
)

val packagedRuntimeAssets = listOf(
    "pythonHome.path" to "python",
    "pythonDevLibs.path" to "python-dev-libs",
    "pythonWheelhouse.path" to "python-wheelhouse",
    "nodeHome.path" to "node",
    "toolchain.path" to "toolchain",
)
val packagedRuntimeSdkAssets = listOf(
    "runtimeSdk.path" to "operator-runtime-sdk-expanded",
)
val packagedRuntimeSdkArchives = listOf(
    "runtimeSdkArchive.path" to "operator-runtime-sdk-archives",
)

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
    namespace = "com.illumination.operator.runtime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.illumination.operator.runtime"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += nativeAbis
        }
    }

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
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val prepareRuntimeExtensionToolJniLibs by tasks.registering(Sync::class) {
    group = "build"
    description = "Stages optional Operator runtime extension tools as executable native libraries."
    into(generatedToolJniLibsDir)

    nativeAbis.forEach { abi ->
        packagedRuntimeTools.forEach { tool ->
            localProperties.getProperty("${tool.propertyPrefix}.$abi")
                ?.takeIf { it.isNotBlank() }
                ?.let { toolPath ->
                    from(localPath(toolPath)) {
                        into(abi)
                        rename { tool.libraryName }
                    }
                }
        }
        val libcxx = androidNdkLibcxxShared(abi)
        if (libcxx.isFile) {
            from(libcxx) {
                into(abi)
                rename { "libc++_shared.so" }
            }
        }
    }
}

val prepareRuntimeExtensionAssets by tasks.registering(Sync::class) {
    group = "build"
    description = "Stages optional Operator runtime extension data trees."
    into(generatedRuntimeAssetsDir)

    nativeAbis.forEach { abi ->
        packagedRuntimeSdkAssets.forEach { (propertyPrefix, assetName) ->
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
        packagedRuntimeSdkArchives.forEach { (propertyPrefix, assetName) ->
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
        packagedRuntimeAssets.forEach { (propertyPrefix, assetName) ->
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

android.sourceSets.named("main") {
    assets.srcDir(rootProject.file("app/src/main/assets"))
    assets.srcDir(generatedRuntimeAssetsDir)
    jniLibs.srcDir(generatedToolJniLibsDir)
}

tasks.named("preBuild") {
    dependsOn(prepareRuntimeExtensionToolJniLibs)
    dependsOn(prepareRuntimeExtensionAssets)
}

dependencies {
    implementation("com.github.luben:zstd-jni:1.5.7-7@aar")
}
