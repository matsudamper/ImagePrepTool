plugins {
    kotlin("jvm") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

allprojects {
    group = "com.imagepreptool"
    version = "0.1.0"

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    apply(plugin = "dev.detekt")

    dependencies {
        "detektPlugins"("io.nlopez.compose.rules:detekt:0.6.8")
    }

    configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        failOnSeverity.set(dev.detekt.gradle.extensions.FailOnSeverity.Info)
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude { it.file.path.contains("generated") }
        }
    }
}
