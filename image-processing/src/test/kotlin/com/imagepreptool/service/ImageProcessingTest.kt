package com.imagepreptool.service

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import com.imagepreptool.model.CaptionField
import com.imagepreptool.model.CaptionStyle
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.model.ResizeMode

class ImageProcessingTest {

    private val dir: File = Files.createTempDirectory("imageprep-test").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun targetSizeNeverUpscales() {
        val options = EditOptions(resizeMode = ResizeMode.LongEdge, longEdge = 2048)
        assertEquals(ImageSize(2048, 1536), Resizer.targetSize(ImageSize(4032, 3024), options))
        assertEquals(ImageSize(1536, 2048), Resizer.targetSize(ImageSize(3024, 4032), options))
        assertEquals(ImageSize(800, 600), Resizer.targetSize(ImageSize(800, 600), options))
        val fit = EditOptions(resizeMode = ResizeMode.Fit, fitWidth = 1920, fitHeight = 1080)
        assertEquals(ImageSize(1440, 1080), Resizer.targetSize(ImageSize(4032, 3024), fit))
        assertEquals(ImageSize(4032, 3024), Resizer.targetSize(ImageSize(4032, 3024), EditOptions(resizeMode = ResizeMode.None)))
        // 縮小のみを外すと小さい画像も指定サイズまで拡大する
        assertEquals(ImageSize(2048, 1536), Resizer.targetSize(ImageSize(800, 600), options.copy(onlyScaleDown = false)))
        assertEquals(ImageSize(1440, 1080), Resizer.targetSize(ImageSize(800, 600), fit.copy(onlyScaleDown = false)))
    }

    @Test
    fun orientationRotatesPixels() {
        // 2x1: 左が赤、右が青
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFF0000)
        image.setRGB(1, 0, 0x0000FF)
        val cw = ImageLoader.applyOrientation(image, 6)
        assertEquals(1, cw.width)
        assertEquals(2, cw.height)
        assertEquals(0xFF0000, cw.getRGB(0, 0) and 0xFFFFFF)
        assertEquals(0x0000FF, cw.getRGB(0, 1) and 0xFFFFFF)
        val ccw = ImageLoader.applyOrientation(image, 8)
        assertEquals(0x0000FF, ccw.getRGB(0, 0) and 0xFFFFFF)
        val flipped = ImageLoader.applyOrientation(image, 2)
        assertEquals(0x0000FF, flipped.getRGB(0, 0) and 0xFFFFFF)
        for (o in 1..8) {
            val out = ImageLoader.applyOrientation(image, o)
            val colors = setOf(out.getRGB(0, 0) and 0xFFFFFF, out.getRGB(out.width - 1, out.height - 1) and 0xFFFFFF)
            assertEquals(setOf(0xFF0000, 0x0000FF), colors, "orientation $o")
        }
    }

    @Test
    fun exposureAndApertureFormatting() {
        assertEquals("1/250s", ExifService.formatExposure(0.004))
        assertEquals("2s", ExifService.formatExposure(2.0))
        assertEquals("0.5s", ExifService.formatExposure(0.5))
        assertEquals("2.8", ExifService.formatDecimal(2.8))
        assertEquals("8", ExifService.formatDecimal(8.0))
    }

    @Test
    fun captionTemplateSkipsMissingFields() {
        val fields = mapOf(
            CaptionField.Camera to "SONY ILCE-7M4",
            CaptionField.Aperture to "f/2.8",
            CaptionField.Iso to "ISO 400",
        )
        assertEquals(
            "SONY ILCE-7M4\nf/2.8  ·  ISO 400",
            CaptionTemplate.render("{camera}  ·  {lens}\n{focal}  ·  {aperture}  ·  {shutter}  ·  {iso}", fields),
        )
        // 項目がすべて空の行は消え、項目の無い行（固定テキスト）は残る
        assertEquals("© Me", CaptionTemplate.render("{lens}\n© Me", fields))
        // 未知の項目はそのまま
        assertEquals("{unknown} f/2.8", CaptionTemplate.render("{unknown} {aperture}", fields))
        assertEquals("", CaptionTemplate.render("{lens}", fields))
        assertEquals(listOf(0..7), CaptionTemplate.tokenRanges("{camera} {nope}"))
        // エスケープ
        assertEquals("{camera} = SONY ILCE-7M4", CaptionTemplate.render("{{camera}} = {camera}", fields))
        assertEquals("{ } {{", CaptionTemplate.render("{{ }} {{{{", fields))
        assertEquals("{SONY ILCE-7M4}", CaptionTemplate.render("{{{camera}}}", fields))
        assertEquals(listOf(10..17), CaptionTemplate.tokenRanges("{{camera}}{camera}"))
        assertEquals("{{a}}", CaptionTemplate.escape("{a}"))
    }

    @Test
    fun exposureBiasFormatting() {
        assertEquals("+0.7EV", ExifService.formatExposureBias(0.67))
        assertEquals("-1EV", ExifService.formatExposureBias(-1.0))
        assertEquals("±0EV", ExifService.formatExposureBias(0.0))
    }

    @Test
    fun planKeepsOriginalsSafeAndAvoidsBatchCollisions() {
        val a = writeImage("photo.jpg")
        val b = writeImage("photo.png")
        val plan = OutputPlanner.plan(listOf(a, b), dir, EditOptions(outputFormat = OutputFormat.Jpeg))
        // 同じフォルダ・接尾辞なし → 元画像は上書きしない
        assertNotEquals(a.absoluteFile, plan[0].target)
        assertNotEquals(plan[0].target, plan[1].target)
        assertTrue(plan.none { it.target.exists() })
    }

    @Test
    fun planProtectsOriginalsThatAreNotExported() {
        val png = writeImage("a.png")
        val jpg = writeImage("a.jpg")
        // a.jpg は書き出さないが読み込まれている元画像なので、上書きを選んでも守る
        val plan = OutputPlanner.plan(listOf(png), dir, EditOptions(outputFormat = OutputFormat.Jpeg), protectedFiles = listOf(png, jpg))
        val resolved = OutputPlanner.applyPolicy(plan, ConflictPolicy.Overwrite).single()
        assertNotEquals(jpg.absoluteFile, resolved.target)
        assertEquals("a (2).jpg", resolved.target.name)
    }

    @Test
    fun planProtectsOriginalsReachedThroughSymlink() {
        val src = File(dir, "src").apply { mkdirs() }
        val png = File(src, "a.png").also { ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", it) }
        val jpg = File(src, "a.jpg").also { ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", it) }
        val alias = File(dir, "alias")
        try {
            Files.createSymbolicLink(alias.toPath(), src.toPath())
        } catch (e: Exception) {
            return // シンボリックリンクを作れない環境（権限の無い Windows など）では確認しない
        }
        val plan = OutputPlanner.plan(listOf(png), alias, EditOptions(outputFormat = OutputFormat.Jpeg), protectedFiles = listOf(png, jpg))
        val resolved = OutputPlanner.applyPolicy(plan, ConflictPolicy.Overwrite).single()
        assertEquals("a (2).jpg", resolved.target.name)
    }

    @Test
    fun interruptStopsExternalProcess() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        var error: Throwable? = null
        val worker = Thread {
            try {
                ProcessRunner.run(listOf("sleep", "30"), timeoutSeconds = 60)
            } catch (e: Throwable) {
                error = e
            }
        }
        val started = System.nanoTime()
        worker.start()
        Thread.sleep(300)
        worker.interrupt()
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertTrue(error is InterruptedException, "error = $error")
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun conflictPolicies() {
        val src = writeImage("a.png")
        val out = File(dir, "out").apply { mkdirs() }
        File(out, "a.jpg").writeText("existing")
        val plan = OutputPlanner.plan(listOf(src), out, EditOptions(outputFormat = OutputFormat.Jpeg))
        assertTrue(plan.single().exists)
        assertEquals("a (2).jpg", OutputPlanner.applyPolicy(plan, ConflictPolicy.Rename).single().target.name)
        assertTrue(OutputPlanner.applyPolicy(plan, ConflictPolicy.Skip).single().skip)
        assertEquals("a.jpg", OutputPlanner.applyPolicy(plan, ConflictPolicy.Overwrite).single().target.name)
    }

    @Test
    fun exportsTransparentPngAsJpegWithCaption() {
        val src = File(dir, "alpha.png")
        ImageIO.write(BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB), "png", src)
        val options = EditOptions(
            resizeMode = ResizeMode.LongEdge,
            longEdge = 600,
            outputFormat = OutputFormat.Jpeg,
            captionEnabled = true,
            captionTemplate = "テスト caption\n{filename}",
            captionStyle = CaptionStyle.Shadow,
        )
        val out = File(dir, "out")
        val item = OutputPlanner.plan(listOf(src), out, options.copy(fileNameSuffix = "_web")).single()
        val result = ImageProcessor(ExternalTools.None).export(item, options)
        assertEquals(ProcessResult.Status.Success, result.status, result.message)
        val written = ImageIO.read(File(out, "alpha_web.jpg"))
        assertEquals(600, written.width)
        assertEquals(400, written.height)
        assertFalse(out.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        // 出力は通常の新規ファイルと同じ権限になる（一時ファイル用の 0600 を引き継がない）
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            val reference = File(out, "reference").apply { createNewFile() }
            assertEquals(
                Files.getPosixFilePermissions(reference.toPath()),
                Files.getPosixFilePermissions(File(out, "alpha_web.jpg").toPath()),
            )
        }
    }

    @Test
    fun exportsFileWithLongName() {
        // 一時ファイル名に出力名を含めると、名前長の上限（多くの環境で 255 バイト）を超える
        val src = writeImage("${"a".repeat(230)}.png")
        val item = OutputPlanner.plan(listOf(src), File(dir, "out"), EditOptions(outputFormat = OutputFormat.Png)).single()
        val result = ImageProcessor(ExternalTools.None).export(item, EditOptions(outputFormat = OutputFormat.Png))
        assertEquals(ProcessResult.Status.Success, result.status, result.message)
    }

    @Test
    fun longNameWithSuffixIsShortened() {
        val name = OutputPlanner.outputName(File("${"あ".repeat(80)}.jpg"), OutputFormat.Jpeg, "_" + "x".repeat(39))
        assertTrue(OutputPlanner.nameLength(name, windows = System.getProperty("os.name").lowercase().contains("win")) <= 255 - 12, name)
        assertTrue(name.endsWith("_" + "x".repeat(39) + ".jpg"))
    }

    @Test
    fun nameLengthUsesUtf16OnWindows() {
        // Windows では日本語 120 文字も 120 単位として数え、不要に短くしない
        assertEquals(120, OutputPlanner.nameLength("あ".repeat(120), windows = true))
        assertEquals(360, OutputPlanner.nameLength("あ".repeat(120), windows = false))
    }

    @Test
    fun longNameWithEmojiIsCutAtCodePointBoundary() {
        val name = OutputPlanner.outputName(File("${"😀".repeat(60)}.png"), OutputFormat.Png, "_web")
        val loneHighSurrogate = name.indices.any { i -> name[i].isHighSurrogate() && name.getOrNull(i + 1)?.isLowSurrogate() != true }
        assertFalse(loneHighSurrogate, name)
        assertTrue(name.endsWith("_web.png"))
    }

    @Test
    fun cameraNameAvoidsDuplicatedMaker() {
        assertEquals("NIKON D850", ExifService.cameraName("NIKON CORPORATION", "NIKON D850"))
        assertEquals("Canon EOS R5", ExifService.cameraName("Canon", "Canon EOS R5"))
        assertEquals("SONY ILCE-7M4", ExifService.cameraName("SONY", "ILCE-7M4"))
        assertEquals("FUJIFILM X-T5", ExifService.cameraName("FUJIFILM", "X-T5"))
        assertEquals("X-T5", ExifService.cameraName(null, "X-T5"))
    }

    @Test
    fun doesNotReplaceFileCreatedDuringExport() {
        val src = writeImage("c.png")
        val out = File(dir, "o2")
        val item = OutputPlanner.plan(listOf(src), out, EditOptions(outputFormat = OutputFormat.Png)).single()
        // 計画後に別のアプリが同じ名前のファイルを作った状況
        out.mkdirs()
        item.target.writeText("other")
        val result = ImageProcessor(ExternalTools.None).export(item, EditOptions(outputFormat = OutputFormat.Png))
        assertEquals(ProcessResult.Status.Failed, result.status)
        assertEquals("other", item.target.readText())
    }

    @Test
    fun renderDoesNotMutateSource() {
        val src = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val out = ImageProcessor(ExternalTools.None).render(src, "abc", EditOptions(), ImageSize(100, 100))
        assertNotEquals(src, out)
        assertTrue((0 until 100).all { x -> (0 until 100).all { y -> src.getRGB(x, y) and 0xFFFFFF == 0 } })
    }

    @Test
    fun webpFailsClearlyWithoutCwebp() {
        val src = writeImage("x.png")
        val item = OutputPlanner.plan(listOf(src), File(dir, "o"), EditOptions(outputFormat = OutputFormat.Webp)).single()
        val result = ImageProcessor(ExternalTools.None).export(item, EditOptions(outputFormat = OutputFormat.Webp))
        assertEquals(ProcessResult.Status.Failed, result.status)
        assertTrue("cwebp" in result.message)
    }

    private fun writeImage(name: String): File {
        val file = File(dir, name)
        ImageIO.write(BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), file.extension.replace("jpg", "jpeg"), file)
        return file
    }
}
