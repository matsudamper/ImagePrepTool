package com.imagepreptool.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelativeOutputPathTest {

    @Test
    fun resolvesInsideSourceFolder() {
        assertEquals(File("/photos/output/web").absoluteFile, RelativeOutputPath.resolve(File("/photos"), "output/web"))
        assertEquals(File("/photos/output").absoluteFile, RelativeOutputPath.resolve(File("/photos"), "./output"))
    }

    @Test
    fun rejectsParentAndAbsolutePaths() {
        assertFalse(RelativeOutputPath.isValid(".."))
        assertFalse(RelativeOutputPath.isValid("../output"))
        assertFalse(RelativeOutputPath.isValid("output/../../x"))
        assertFalse(RelativeOutputPath.isValid("/tmp/output"))
        assertFalse(RelativeOutputPath.isValid(" "))
        assertNull(RelativeOutputPath.resolve(File("/photos"), "../output"))
        assertTrue(RelativeOutputPath.isValid("output"))
    }
}
