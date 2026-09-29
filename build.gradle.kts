plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

allprojects {
    group = "com.imagepreptool"
    version = "0.1.0"

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    apply(plugin = "io.gitlab.arturbosch.detekt")

    dependencies {
        "detektPlugins"("io.nlopez.compose.rules:detekt:0.4.23")
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude { it.file.path.contains("generated") }
        }
    }
}
