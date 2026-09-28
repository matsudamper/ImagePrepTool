package com.imagepreptool

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.imagepreptool.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ImagePrepTool",
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        App()
    }
}
