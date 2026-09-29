package com.imagepreptool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.service.ImageLoader

sealed interface ThumbnailState {
    data object Loading : ThumbnailState
    data class Ready(val bitmap: ImageBitmap) : ThumbnailState
    data class Failed(val reason: String) : ThumbnailState
}

object ThumbnailLoader {
    private const val SIZE = 320

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(3)

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ThumbnailState>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ThumbnailState>?) = size > 400
        },
    )

    private fun key(file: File) = "${file.absolutePath}:${file.lastModified()}"

    suspend fun load(file: File, tools: ExternalTools): ThumbnailState {
        cache[key(file)]?.let { return it }
        val state = runInterruptible(dispatcher) {
            try {
                ThumbnailState.Ready(ImageLoader.load(file, tools, maxDimension = SIZE).image.toComposeImageBitmap())
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Throwable) {
                ThumbnailState.Failed(e.message ?: "読み込めません")
            }
        }
        if (state is ThumbnailState.Ready) cache[key(file)] = state
        return state
    }

    fun clear() = cache.clear()
}

@Composable
fun rememberThumbnail(file: File, tools: ExternalTools?): State<ThumbnailState> =
    produceState<ThumbnailState>(ThumbnailState.Loading, file, tools) {
        // ツール確認前に HEIC を読むと失敗扱いになるので待つ
        if (tools == null && ImageLoader.isHeif(file)) return@produceState
        value = ThumbnailLoader.load(file, tools ?: ExternalTools.None)
    }
