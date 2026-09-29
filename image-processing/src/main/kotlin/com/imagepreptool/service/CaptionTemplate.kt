package com.imagepreptool.service

import com.imagepreptool.model.CaptionField

object CaptionTemplate {

    // `{{` と `}}` は `{` `}` そのものを表すエスケープ。左から順に読むので `{{camera}}` は項目にならない
    private val token = Regex("""\{\{|\}\}|\{(\w+)\}""")
    private const val SEPARATORS = """[·•|/,、]"""
    private val repeatedSeparators = Regex("""(\s*$SEPARATORS\s*)(?:$SEPARATORS\s*)+""")
    private val edgeSeparators = Regex("""^\s*$SEPARATORS\s*|\s*$SEPARATORS\s*$""")

    /** テンプレート中の項目の位置（入力欄の強調表示用） */
    fun tokenRanges(template: String): List<IntRange> =
        token.findAll(template).filter { CaptionField.fromKey(it.groupValues[1]) != null }.map { it.range }.toList()

    /** 文字列をそのまま表示されるようエスケープする */
    fun escape(text: String): String = text.replace("{", "{{").replace("}", "}}")

    /**
     * `{camera}` などを撮影情報に置き換え、`{{` `}}` を `{` `}` に戻す。
     * 項目がすべて空になった行は省き、空の項目の前後に残った区切り文字（· | / , 、）は詰める。
     */
    fun render(template: String, fields: Map<CaptionField, String>): String {
        val lines = template.replace("\r\n", "\n").split('\n').mapNotNull { line ->
            var hasField = false
            var resolved = false
            val replaced = token.replace(line) { match ->
                when (match.value) {
                    "{{" -> return@replace "{"
                    "}}" -> return@replace "}"
                }
                val field = CaptionField.fromKey(match.groupValues[1]) ?: return@replace match.value
                hasField = true
                fields[field]?.also { resolved = true }.orEmpty()
            }
            when {
                !hasField -> replaced.trimEnd()
                !resolved -> null
                else -> replaced
                    .replace(repeatedSeparators) { m ->
                        // 最初の区切りの前の空白と、最後の区切りの後ろの空白を残す
                        m.groupValues[1].trimEnd() + m.value.takeLastWhile(Char::isWhitespace)
                    }
                    .replace(edgeSeparators, "")
                    .trim()
            }
        }
        return lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n")
    }
}
