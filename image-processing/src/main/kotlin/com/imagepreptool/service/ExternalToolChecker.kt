package com.imagepreptool.service

import java.io.IOException
import com.imagepreptool.model.ExternalTool
import com.imagepreptool.model.ExternalToolStatus
import com.imagepreptool.model.ExternalTools

object ExternalToolChecker {

    fun checkAll(): ExternalTools = ExternalTools(ExternalTool.entries.map(::check))

    fun check(tool: ExternalTool): ExternalToolStatus = try {
        // 起動できれば PATH 上に存在するとみなす（--version の終了コードはツールごとにまちまち）
        val result = ProcessRunner.run(listOf(tool.command, tool.versionArg), timeoutSeconds = 10)
        val firstLine = result.output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        ExternalToolStatus(tool, available = true, detail = firstLine ?: "利用可能")
    } catch (e: IOException) {
        ExternalToolStatus(tool, available = false, detail = "PATH に見つかりません")
    } catch (e: ExternalCommandException) {
        ExternalToolStatus(tool, available = false, detail = e.message.orEmpty())
    }
}
