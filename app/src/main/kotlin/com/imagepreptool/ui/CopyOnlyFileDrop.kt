package com.imagepreptool.ui

import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener

/**
 * Compose のドロップ受け付けを、常にコピー操作として受け取るように差し替える。
 *
 * Compose はドラッグ元が提示した操作（移動など）をそのまま受け入れる。
 * Windows のエクスプローラー（特にアドレスバー）からのドラッグを移動として受け取ると、
 * エクスプローラーが移動完了とみなして元のフォルダ・ファイルをゴミ箱へ送ってしまう。
 */
internal object CopyOnlyFileDrop {
    fun install(window: Window) {
        val composeDropTarget = findComposeDropTarget(window) ?: return
        val component = composeDropTarget.component
        component.dropTarget = DropTarget(component, DnDConstants.ACTION_COPY, CopyOnlyDropTargetListener(composeDropTarget), true)
    }

    private fun findComposeDropTarget(component: Component): DropTarget? {
        val dropTarget = component.dropTarget
        if (dropTarget != null && dropTarget.javaClass == DropTarget::class.java) return dropTarget
        if (component !is Container) return null
        return component.components.firstNotNullOfOrNull(::findComposeDropTarget)
    }
}

private class CopyOnlyDropTargetListener(private val delegate: DropTargetListener) : DropTargetListener {
    override fun dragEnter(event: DropTargetDragEvent) {
        if (event.canCopy()) delegate.dragEnter(event.asCopy()) else event.rejectDrag()
    }

    override fun dragOver(event: DropTargetDragEvent) {
        if (event.canCopy()) delegate.dragOver(event.asCopy()) else event.rejectDrag()
    }

    override fun dropActionChanged(event: DropTargetDragEvent) {
        if (event.canCopy()) delegate.dropActionChanged(event.asCopy()) else event.rejectDrag()
    }

    override fun dragExit(event: DropTargetEvent) {
        delegate.dragExit(event)
    }

    override fun drop(event: DropTargetDropEvent) {
        if (event.sourceActions and DnDConstants.ACTION_COPY != 0) {
            delegate.drop(DropTargetDropEvent(event.dropTargetContext, event.location, DnDConstants.ACTION_COPY, event.sourceActions, event.isLocalTransfer))
        } else {
            event.rejectDrop()
        }
    }

    private fun DropTargetDragEvent.canCopy(): Boolean = sourceActions and DnDConstants.ACTION_COPY != 0

    private fun DropTargetDragEvent.asCopy(): DropTargetDragEvent = DropTargetDragEvent(dropTargetContext, location, DnDConstants.ACTION_COPY, sourceActions)
}
