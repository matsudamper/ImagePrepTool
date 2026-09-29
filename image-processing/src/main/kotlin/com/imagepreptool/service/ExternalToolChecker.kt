package com.imagepreptool.service

import java.io.IOException
import java.util.concurrent.TimeUnit
import com.imagepreptool.model.ExternalToolStatus

object ExternalToolChecker {

    private val trackedTools = listOf(
        "cwebp" to listOf("-version"),
        "dwebp" to listOf("-version"),
        "magick" to listOf("-version"),
        "heif-convert" to listOf("--help"),
    )

    fun checkAll(): List<ExternalToolStatus> =
        trackedTools.map { (name, args) -> checkTool(name, args) }

    fun checkTool(name: String, versionArgs: List<String> = listOf("-version")): ExternalToolStatus {
        val command = resolveCommand(name)
        return try {
            val process = ProcessBuilder(listOf(command) + versionArgs)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                ExternalToolStatus(name, command, false, "タイムアウト")
            } else if (process.exitValue() == 0) {
                val output = process.inputStream.bufferedReader().readText().lineSequence().firstOrNull().orEmpty()
                ExternalToolStatus(name, command, true, output.ifBlank { "利用可能" })
            } else {
                ExternalToolStatus(name, command, false, "終了コード ${process.exitValue()}")
            }
        } catch (e: IOException) {
            ExternalToolStatus(name, command, false, e.message ?: "実行不可")
        }
    }

    fun resolveCommand(name: String): String {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            return "$name.exe"
        }
        return name
    }

    fun isAvailable(name: String): Boolean = checkTool(name).available
}
