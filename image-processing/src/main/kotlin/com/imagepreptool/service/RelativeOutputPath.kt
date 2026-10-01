package com.imagepreptool.service

import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path

object RelativeOutputPath {
    const val DEFAULT = "output"

    fun isValid(relativePath: String): Boolean {
        if (relativePath.isBlank()) return false
        val path = try {
            Path.of(relativePath)
        } catch (_: InvalidPathException) {
            return false
        }
        // Windows の「C:foo」や「\foo」は isAbsolute が false でも root を持つ
        return !path.isAbsolute && path.root == null
    }

    fun resolve(baseDir: File, relativePath: String): File? =
        if (isValid(relativePath)) File(baseDir, relativePath).absoluteFile.normalize() else null
}
