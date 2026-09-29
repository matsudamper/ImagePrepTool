package com.imagepreptool

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 同じアプリを新しいプロセスで起動する。外部ツールをインストールした後の PATH を反映させるために使う。
 *
 * 子プロセスは親の環境変数を引き継ぐため、そのままでは起動時の古い PATH のままになる。
 * Windows ではレジストリから PATH を読み直して渡す。
 */
object AppRelauncher {

    private const val MAIN_CLASS = "com.imagepreptool.MainKt"
    private const val SYSTEM_ENVIRONMENT_KEY = """HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment"""
    private const val USER_ENVIRONMENT_KEY = """HKCU\Environment"""

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")

    /** @throws IOException 新しいプロセスを起動できなかった場合 */
    fun launchNewInstance() {
        val builder = ProcessBuilder(relaunchCommand())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        if (isWindows) {
            val freshPath = readWindowsPathFromRegistry()
            if (freshPath != null) {
                val environment = builder.environment()
                // Windows の環境変数名は大文字小文字を区別しないが、Map のキーは区別するため元のキー名を使う
                val pathKey = environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "Path"
                environment[pathKey] = mergePath(freshPath, environment[pathKey].orEmpty())
            }
        }
        builder.start()
    }

    private fun relaunchCommand(): List<String> {
        // jpackage で作った exe から起動した場合に設定される
        val packagedAppPath = System.getProperty("jpackage.app-path")
        if (packagedAppPath != null) return listOf(packagedAppPath)
        val javaExecutable = File(System.getProperty("java.home"), "bin/java").absolutePath
        return listOf(javaExecutable, "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), MAIN_CLASS)
    }

    /** システムとユーザーの PATH をこの順に連結する（Windows がログオン時に組み立てるのと同じ順） */
    private fun readWindowsPathFromRegistry(): String? {
        val entries = listOfNotNull(
            readRegistryValue(SYSTEM_ENVIRONMENT_KEY, "Path"),
            readRegistryValue(USER_ENVIRONMENT_KEY, "Path"),
        ).map(::expandEnvironmentVariables)
        return entries.takeIf { it.isNotEmpty() }?.joinToString(File.pathSeparator)
    }

    private fun readRegistryValue(key: String, name: String): String? {
        val output = try {
            val process = ProcessBuilder("reg", "query", key, "/v", name).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) return null
            text
        } catch (e: IOException) {
            return null
        }
        // 例: "    Path    REG_EXPAND_SZ    C:\Windows;%USERPROFILE%\bin"
        val valuePattern = Regex("""^\s*${Regex.escape(name)}\s+REG_(?:EXPAND_)?SZ\s+(.*)$""", RegexOption.IGNORE_CASE)
        return output.lineSequence().firstNotNullOfOrNull { line -> valuePattern.find(line)?.groupValues?.get(1)?.trim() }
    }

    private fun expandEnvironmentVariables(value: String): String =
        Regex("%([^%]+)%").replace(value) { match -> System.getenv(match.groupValues[1]) ?: match.value }

    /** 起動時の PATH にしかない項目（ランチャーが足したものなど）も失わないよう後ろに残す */
    private fun mergePath(freshPath: String, currentPath: String): String {
        val freshEntries = freshPath.split(File.pathSeparator).filter { it.isNotBlank() }
        val freshEntrySet = freshEntries.map { it.trimEnd('\\').lowercase() }.toSet()
        val currentOnlyEntries = currentPath.split(File.pathSeparator)
            .filter { it.isNotBlank() && it.trimEnd('\\').lowercase() !in freshEntrySet }
        return (freshEntries + currentOnlyEntries).joinToString(File.pathSeparator)
    }
}
