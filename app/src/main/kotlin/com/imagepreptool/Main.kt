package com.imagepreptool

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.io.File
import javax.swing.UIManager
import com.imagepreptool.presentation.ExportState
import com.imagepreptool.presentation.ImagePrepViewModel
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_photo_library
import com.imagepreptool.ui.App
import com.imagepreptool.ui.DesktopDialogs
import com.imagepreptool.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

fun main(args: Array<String>) {
    // ファイル選択ダイアログを OS 標準の見た目にする
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val initial = args.map(::File).filter { it.exists() }

    application {
        val viewModel = remember { ImagePrepViewModel().also { vm -> if (initial.isNotEmpty()) vm.uiStateFlow.value.listener.addFiles(initial) } }
        var composeWindow: java.awt.Window? = null
        val state = rememberWindowState(width = 1360.dp, height = 860.dp, position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center))
        Window(
            onCloseRequest = {
                viewModel.uiStateFlow.value.listener.cancelExport()
                exitApplication()
            },
            title = "ImagePrepTool",
            icon = painterResource(Res.drawable.ic_photo_library),
            state = state,
            onPreviewKeyEvent = { event ->
                val ctrl = event.isCtrlPressed || event.isMetaPressed
                if (event.type != KeyEventType.KeyDown || !ctrl) return@Window false
                val ui = viewModel.uiStateFlow.value
                when (event.key) {
                    Key.O -> {
                        if (ui.export == ExportState.Idle) {
                            DesktopDialogs.pickDirectory(composeWindow, "画像のあるフォルダを選択", ui.sourcePath?.let(::File))
                                ?.let(ui.listener::openFolder)
                        }
                        true
                    }
                    Key.Enter -> {
                        ui.listener.requestExport()
                        true
                    }
                    else -> false
                }
            },
        ) {
            composeWindow = window
            window.minimumSize = Dimension(1100, 680)
            AppTheme {
                App(viewModel, dialogParent = window)
            }
        }
    }
}
