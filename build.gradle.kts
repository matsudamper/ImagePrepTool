plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

allprojects {
    group = "com.imagepreptool"
    version = "0.1.0"

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude { it.file.path.contains("generated") }
        }
    }
}
