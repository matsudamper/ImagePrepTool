import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.imagepreptool"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.imagepreptool.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            // 単体 EXE（JRE 同梱インストーラ）。ポータブル版は packagePortableWindowsExe
            targetFormats(TargetFormat.Exe)
            packageName = "ImagePrepTool"
            packageVersion = "0.1.0"
            description = "画像の処理を行い、公開する形に整える"
            vendor = "ImagePrepTool"

            windows {
                menuGroup = "ImagePrepTool"
                upgradeUuid = "18189999-4335-4149-8624-9A27244E9F91"
                console = false
                perUserInstall = true
                shortcut = true
                menu = true
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

val portableAppDir = layout.buildDirectory.dir("compose/portable/ImagePrepTool")
val isWindowsHost = providers.systemProperty("os.name").map { it.lowercase().contains("windows") }

tasks.register("preparePortableWindowsApp") {
    group = "compose"
    description = "Windows 用ポータブル配布（ImagePrepTool.exe + 同梱 JRE）"
    dependsOn("createReleaseDistributable")
    onlyIf { isWindowsHost.get() }
    doLast {
        val releaseApp = layout.buildDirectory.dir("compose/binaries/main-release/app").get().asFile
        check(releaseApp.exists()) { "Release app folder not found: $releaseApp" }
        val dest = portableAppDir.get().asFile
        dest.deleteRecursively()
        dest.mkdirs()
        copy {
            from(releaseApp)
            into(dest)
        }
        logger.lifecycle("Portable app: ${dest.absolutePath}")
    }
}

tasks.register<Zip>("packagePortableWindowsExe") {
    group = "compose"
    description = "ポータブル版 ZIP（展開後 exe を直接起動、Java インストール不要）"
    dependsOn("preparePortableWindowsApp")
    onlyIf { isWindowsHost.get() }
    from(portableAppDir)
    archiveFileName.set("ImagePrepTool-${project.version}-portable-win.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main-release/portable"))
}

