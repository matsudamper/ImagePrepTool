import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":image-processing"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(compose.preview)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    testImplementation(kotlin("test"))
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.imagepreptool.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.imagepreptool.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        jvmArgs += listOf("-Xmx3g", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            modules("java.instrument", "java.prefs", "java.naming", "jdk.unsupported")
            packageName = "ImagePrepTool"
            // jpackage の MSI は同じバージョンだと上書きインストールできないため、CI のビルド番号をパッチ番号にする
            packageVersion = "0.1.${providers.environmentVariable("GITHUB_RUN_NUMBER").getOrElse("0")}"
            // jpackage の MSI は en-us（コードページ 1252）で生成されるため、日本語を含めると WiX がエラーになる
            description = "Prepare images for publishing (resize, convert format, write shooting info)"
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
