package com.imagepreptool

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.imagepreptool.ui.App
import com.imagepreptool.ui.DesktopDialogs
import com.imagepreptool.ui.theme.AppTheme

fun main(args: Array<String>) {
    // ファイル選択ダイアログを OS 標準の見た目にする
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val initial = args.map(::File).filter { it.exists() }

    application {
        val viewModel = remember { ImagePrepViewModel().also { vm -> if (initial.isNotEmpty()) vm.addFiles(initial) } }
        var composeWindow: java.awt.Window? = null
        val state = rememberWindowState(width = 1360.dp, height = 860.dp, position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center))
        Window(
            onCloseRequest = {
                viewModel.cancelExport()
                exitApplication()
            },
            title = "ImagePrepTool",
            icon = rememberVectorPainter(Icons.Rounded.PhotoLibrary),
            state = state,
            onPreviewKeyEvent = { event ->
                val ctrl = event.isCtrlPressed || event.isMetaPressed
                if (event.type != KeyEventType.KeyDown || !ctrl) return@Window false
                val ui = viewModel.uiStateFlow.value
                when (event.key) {
                    Key.O -> {
                        if (ui.export == ExportState.Idle) {
                            DesktopDialogs.pickDirectory(composeWindow, "画像のあるフォルダを選択", ui.sourcePath?.let(::File))
                                ?.let(viewModel::openFolder)
                        }
                        true
                    }
                    Key.Enter -> {
                        viewModel.requestExport()
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
