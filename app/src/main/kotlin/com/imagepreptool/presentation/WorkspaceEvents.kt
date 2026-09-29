package com.imagepreptool.presentation

import java.io.File
import com.imagepreptool.model.EditOptions

/** 作業画面から ViewModel へ伝える操作 */
interface WorkspaceEvents {
    fun clickImage(file: File, mode: SelectMode)
    fun toggleIncluded(file: File)
    fun toggleFocusedIncluded()
    fun setSelectionIncluded(included: Boolean)
    fun setAllIncluded(included: Boolean)
    fun selectAll()
    fun clearSelection()
    fun moveFocus(delta: Int)
    fun removeImage(file: File)
    fun excludeUnreadable()
    fun updateOptions(transform: (EditOptions) -> EditOptions)
    fun chooseOutputDirectory(dir: File)
    fun resetOutputDirectory()
    fun requestExport()
    fun closeAll()
}
