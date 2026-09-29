package com.imagepreptool.service

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal object ProcessRunner {

    data class Result(val exitCode: Int, val output: String)

    /**
     * 外部コマンドを実行する。出力は別スレッドで読み続けるので、出力量が多くてもブロックしない。
     * @throws IOException コマンドが見つからない・起動できない場合
     * @throws ExternalCommandException タイムアウトした場合
     * @throws InterruptedException 待機中にスレッドが割り込まれた場合（キャンセル）。外部プロセスは終了させる
     */
    fun run(command: List<String>, timeoutSeconds: Long): Result {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        val output = StringBuilder()
        val reader = thread(isDaemon = true, name = "process-output") {
            runCatching {
                process.inputStream.bufferedReader().use { r -> r.lineSequence().forEach { output.appendLine(it) } }
            }
        }
        val finished = try {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            throw e
        }
        if (!finished) {
            process.destroyForcibly()
            throw ExternalCommandException("${command.first()} が $timeoutSeconds 秒以内に終了しませんでした")
        }
        reader.join(2_000)
        return Result(process.exitValue(), output.toString())
    }
}

class ExternalCommandException(message: String) : Exception(message)
