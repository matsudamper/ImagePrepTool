package com.imagepreptool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalToolCheckerTest {

    @Test
    fun resolveCommandUsesExeSuffixOnWindows() {
        val original = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Windows 11")
            assertEquals("cwebp.exe", com.imagepreptool.service.ExternalToolChecker.resolveCommand("cwebp"))
        } finally {
            System.setProperty("os.name", original)
        }
    }

    @Test
    fun checkAllReturnsTrackedTools() {
        val statuses = com.imagepreptool.service.ExternalToolChecker.checkAll()
        assertTrue(statuses.size >= 4)
        assertTrue(statuses.any { it.name == "cwebp" })
    }
}
