package com.imagepreptool.service

import java.io.File
import java.nio.file.Path
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.OutputFormat

data class PlannedOutput(
    val source: File,
    val target: File,
    val format: OutputFormat,
    /** 同名ファイルが出力先に既にある */
    val exists: Boolean,
    val skip: Boolean = false,
)

object OutputPlanner {

    fun resolveFormat(source: File, selected: OutputFormat): OutputFormat {
        if (selected != OutputFormat.Original) return selected
        return when (source.extension.lowercase()) {
            "jpg", "jpeg" -> OutputFormat.Jpeg
            "webp" -> OutputFormat.Webp
            "png", "gif", "bmp" -> OutputFormat.Png
            else -> OutputFormat.Jpeg
        }
    }

    fun outputName(source: File, format: OutputFormat, suffix: String): String {
        val sourceExt = source.extension.lowercase()
        val ext = when {
            format == OutputFormat.Jpeg && sourceExt in setOf("jpg", "jpeg") -> sourceExt
            else -> format.extension
        }
        return "${source.nameWithoutExtension}${sanitize(suffix)}.$ext"
    }

    /**
     * 出力ファイルを決める。バッチ内で名前が重なる場合と、[protectedFiles]（元画像）を上書きしそうな場合は常に別名にする。
     */
    fun plan(
        sources: List<File>,
        outputDir: File,
        options: EditOptions,
        protectedFiles: Collection<File> = sources,
    ): List<PlannedOutput> {
        val originals = sources + protectedFiles
        val sourcePaths = originals.map { it.absoluteFile.normalize() }.toSet()
        // シンボリックリンクや Windows のジャンクション経由で同じファイルを指す場合も元画像とみなす
        val realPaths = originals.mapNotNull(::realPath).toSet()
        fun isOriginal(file: File) = file in sourcePaths || (file.exists() && realPath(file) in realPaths)
        val used = mutableSetOf<String>()
        return sources.map { source ->
            val format = resolveFormat(source, options.outputFormat)
            var target = File(outputDir, outputName(source, format, options.fileNameSuffix)).absoluteFile.normalize()
            if (target.key() in used || isOriginal(target)) {
                target = nextFreeName(target) { it.key() in used || isOriginal(it) || it.exists() }
            }
            used += target.key()
            PlannedOutput(source, target, format, exists = target.exists())
        }
    }

    fun applyPolicy(plan: List<PlannedOutput>, policy: ConflictPolicy): List<PlannedOutput> {
        val used = plan.map { it.target.key() }.toMutableSet()
        return plan.map { item ->
            if (!item.exists) return@map item
            when (policy) {
                ConflictPolicy.Overwrite -> item
                ConflictPolicy.Skip -> item.copy(skip = true)
                ConflictPolicy.Rename -> {
                    val renamed = nextFreeName(item.target) { it.key() in used || it.exists() }
                    used += renamed.key()
                    item.copy(target = renamed, exists = false)
                }
            }
        }
    }

    private fun realPath(file: File): Path? = runCatching { file.toPath().toRealPath() }.getOrNull()

    private fun nextFreeName(file: File, taken: (File) -> Boolean): File {
        val base = file.nameWithoutExtension
        val ext = file.extension
        var index = 2
        while (true) {
            val candidate = File(file.parentFile, "$base ($index).$ext")
            if (!taken(candidate)) return candidate
            index++
        }
    }

    // Windows はファイル名の大文字小文字を区別しない
    private fun File.key(): String = path.lowercase()

    private fun sanitize(suffix: String): String = suffix.replace(Regex("""[\\/:*?"<>|]"""), "_").trimEnd('.', ' ')
}
