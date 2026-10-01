package com.imagepreptool.service

import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import com.imagepreptool.model.ExternalTool

/**
 * HEIF を外部ツールで変換したファイルを使い回す。
 * サムネイル・プレビュー・書き出しで同じ画像を何度も外部プロセスで変換すると遅いため
 */
internal object HeifConversionCache {

    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    private data class Key(val path: String, val lastModified: Long, val length: Long, val decoder: ExternalTool)

    private val convertedFiles = LinkedHashMap<Key, File>(16, 0.75f, true)
    private val conversionLocks = ConcurrentHashMap<Key, ReentrantLock>()

    /**
     * 変換済みのファイルを返す。無ければ [convert] で一時ファイルへ書き出す。
     * 同じ画像の変換が同時に走らないよう、変換中の呼び出しは待たせて結果を共有する
     */
    fun getOrConvert(file: File, decoder: ExternalTool, outputExtension: String, convert: (output: File) -> Unit): File {
        val key = Key(file.absolutePath, file.lastModified(), file.length(), decoder)
        val lock = conversionLocks.computeIfAbsent(key) { ReentrantLock() }
        // キャンセルされたサムネイル読み込みが、他の変換の完了を待ち続けないようにする
        lock.lockInterruptibly()
        try {
            val cached = cachedFile(key)
            if (cached != null) return cached
            val output = Files.createTempFile("imageprep-heif-", outputExtension).toFile().apply { deleteOnExit() }
            try {
                convert(output)
            } catch (e: Throwable) {
                output.delete()
                throw e
            }
            store(key, output)
            return output
        } finally {
            lock.unlock()
        }
    }

    private fun cachedFile(key: Key): File? = synchronized(convertedFiles) {
        val cached = convertedFiles[key]
        if (cached != null && !cached.isFile) convertedFiles.remove(key)
        cached?.takeIf { it.isFile }
    }

    private fun store(key: Key, output: File) = synchronized(convertedFiles) {
        convertedFiles[key] = output
        val iterator = convertedFiles.entries.iterator()
        while (convertedFiles.values.sumOf { it.length() } > MAX_TOTAL_BYTES && convertedFiles.size > 1) {
            val eldest = iterator.next()
            iterator.remove()
            eldest.value.delete()
        }
    }
}
