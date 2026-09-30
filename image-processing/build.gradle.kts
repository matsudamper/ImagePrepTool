plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.drewnoakes:metadata-extractor:2.21.0")
    // WebP 読み込みと CMYK などの JPEG を ImageIO で扱えるようにする
    api("com.twelvemonkeys.imageio:imageio-webp:3.15.2")
    api("com.twelvemonkeys.imageio:imageio-jpeg:3.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
