package com.imagepreptool.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import com.imagepreptool.presentation.ImageGroup
import com.imagepreptool.presentation.ImageItem

class VerticalMoveTest {

    // 3 列表示で trip は 5 枚（2 行目が 2 枚）、misc は 3 枚
    private val trip = (1..5).map { File("/trip/$it.jpg") }
    private val misc = (1..3).map { File("/misc/$it.jpg") }
    private val groups = listOf(
        ImageGroup(File("/trip"), trip.map(::ImageItem)),
        ImageGroup(File("/misc"), misc.map(::ImageItem)),
    )

    @Test
    fun movesWithinFolderByColumns() {
        assertEquals(3, verticalMoveDelta(groups, trip[0], columns = 3, downward = true))
        assertEquals(-3, verticalMoveDelta(groups, trip[4], columns = 3, downward = false))
    }

    @Test
    fun movingDownIntoShorterLastRowStopsAtLastImage() {
        assertEquals(2, verticalMoveDelta(groups, trip[2], columns = 3, downward = true))
    }

    @Test
    fun movingDownFromLastRowGoesToSameColumnOfNextFolder() {
        // trip の 4 枚目（2 行目 1 列目）の真下は misc の 1 枚目
        assertEquals(2, verticalMoveDelta(groups, trip[3], columns = 3, downward = true))
        assertEquals(2, verticalMoveDelta(groups, trip[4], columns = 3, downward = true))
    }

    @Test
    fun movingUpFromFirstRowGoesToSameColumnOfPreviousFolder() {
        assertEquals(-2, verticalMoveDelta(groups, misc[0], columns = 3, downward = false))
        // 前のフォルダの最終行に同じ列が無ければ最終行の末尾へ
        assertEquals(-3, verticalMoveDelta(groups, misc[2], columns = 3, downward = false))
    }

    @Test
    fun staysAtEdgesOfList() {
        assertEquals(0, verticalMoveDelta(groups, trip[1], columns = 3, downward = false))
        assertEquals(0, verticalMoveDelta(groups, misc[1], columns = 3, downward = true))
    }
}
