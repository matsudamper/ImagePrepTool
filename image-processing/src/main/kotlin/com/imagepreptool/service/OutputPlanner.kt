package com.imagepreptool.service

import java.io.File
import java.nio.file.Path
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.PenStroke
import com.imagepreptool.model.Rotation

data class PlannedOutput(
    val source: File,
    val target: File,
    val format: OutputFormat,
    /** 同名ファイルが出力先に既にある */
    val exists: Boolean,
    val rotation: Rotation,
    /** [rotation] で回した後の画像に対する範囲 */
    val crop: CropRect?,
    /** [rotation] で回した後、切り抜く前の画像に描く線 */
    val strokes: List<PenStroke>,
    val skip: Boolean = false,
)

object OutputPlanner {

    private const val MAX_NAME_UNITS = 255
    private const val RENAME_MARGIN_UNITS = 12
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    // Windows（NTFS）と macOS（APFS の既定）はファイル名の大文字小文字を区別しない
    private val caseInsensitiveFileNames = isWindows || System.getProperty("os.name").orEmpty().lowercase().contains("mac")

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
        val tail = "${sanitize(suffix)}.$ext"
        return fitNameLength(source.nameWithoutExtension, tail)
    }

    /**
     * ファイル名の上限（Windows/NTFS は UTF-16 で 255 単位、多くの Unix 系は UTF-8 で 255 バイト）を超えないよう、
     * 元の名前を削って収める。重複時に付ける「 (2)」などの分の余裕を残す
     */
    private fun fitNameLength(base: String, tail: String): String {
        val limit = MAX_NAME_UNITS - RENAME_MARGIN_UNITS
        var trimmed = base
        while (trimmed.isNotEmpty() && nameLength(trimmed + tail, isWindows) > limit) {
            // サロゲートペア（絵文字など）を途中で切らないよう、コードポイント単位で削る
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(trimmed.length, -1))
        }
        return trimmed + tail
    }

    internal fun nameLength(name: String, windows: Boolean): Int =
        if (windows) name.length else name.toByteArray(Charsets.UTF_8).size

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
            PlannedOutput(source, target, format, exists = target.exists(), rotation = Rotation.None, crop = null, strokes = listOf())
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

    private fun File.key(): String = if (caseInsensitiveFileNames) path.lowercase() else path

    private fun sanitize(suffix: String): String = suffix.replace(Regex("""[\\/:*?"<>|]"""), "_").trimEnd('.', ' ')
}
