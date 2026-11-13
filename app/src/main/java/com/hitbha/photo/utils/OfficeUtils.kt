package com.hitbha.photo.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OfficeUtils {

    private const val EMU_PER_INCH = 914400L
    private const val PX_TO_EMU = 9525L
    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val XML_NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val XML_NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val XML_NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val XML_NS_XDR = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
    private const val XML_NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"

    private sealed class Page {
        class TextTable(val rows: List<List<String>>) : Page()
        class EmbeddedImage(val bitmap: Bitmap) : Page()
    }

    private val EMPTY_SHEET_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="$XML_NS_MAIN">
<dimension ref="A1"/>
<sheetViews><sheetView workbookViewId="0"/></sheetViews>
<sheetFormatPr defaultRowHeight="15"/>
<sheetData/>
</worksheet>"""

    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="$XML_NS_MAIN">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
<dxfs count="0"/>
<tableStyles count="0" defaultTableStyle="TableStyleMedium9" defaultPivotStyle="PivotStyleLight16"/>
</styleSheet>"""

    fun saveDocxToGallery(context: Context, bitmaps: List<Bitmap>, fileName: String? = null): Uri? {
        if (bitmaps.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "Scan_${System.currentTimeMillis()}") + ".docx"
        return writeToMediaStore(context, name,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ) { writeDocx(bitmaps, it) }
    }

    fun createDocxCacheFile(context: Context, bitmaps: List<Bitmap>, fileName: String? = null): Uri? {
        if (bitmaps.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "scan_share_${System.currentTimeMillis()}") + ".docx"
        return writeToCache(context, name) { writeDocx(bitmaps, it) }
    }

    fun saveXlsxToGallery(context: Context, bitmaps: List<Bitmap>, fileName: String? = null): Uri? {
        if (bitmaps.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "Scan_${System.currentTimeMillis()}") + ".xlsx"
        return writeToMediaStore(context, name, XLSX_MIME) {
            writeXlsx(bitmaps, it)
        }
    }

    fun createXlsxCacheFile(context: Context, bitmaps: List<Bitmap>, fileName: String? = null): Uri? {
        if (bitmaps.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "scan_share_${System.currentTimeMillis()}") + ".xlsx"
        return writeToCache(context, name) {
            writeXlsx(bitmaps, it)
        }
    }

    fun saveXlsxTableToGallery(
        context: Context,
        tables: List<List<List<String>>>,
        fileName: String? = null
    ): Uri? {
        if (tables.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "Scan_${System.currentTimeMillis()}") + ".xlsx"
        return writeToMediaStore(context, name, XLSX_MIME) {
            writeXlsxTables(tables, it)
        }
    }

    fun createXlsxTableCacheFile(
        context: Context,
        tables: List<List<List<String>>>,
        fileName: String? = null
    ): Uri? {
        if (tables.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "scan_share_${System.currentTimeMillis()}") + ".xlsx"
        return writeToCache(context, name) {
            writeXlsxTables(tables, it)
        }
    }

    /**
     * Builds a single workbook with one sheet per scanned page. Pages that contain
     * recognized text become proper tables; pages without any text keep the scanned
     * image embedded so no page is lost.
     */
    fun saveXlsxPagesToGallery(
        context: Context,
        bitmaps: List<Bitmap>,
        tables: List<List<List<String>>>,
        fileName: String? = null
    ): Uri? {
        if (bitmaps.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "Scan_${System.currentTimeMillis()}") + ".xlsx"
        return writeToMediaStore(context, name, XLSX_MIME) {
            writeXlsxPages(bitmaps, tables, it)
        }
    }

    fun createXlsxPagesCacheFile(
        context: Context,
        bitmaps: List<Bitmap>,
        tables: List<List<List<String>>>,
        fileName: String? = null
    ): Uri? {
        if (bitmaps.isEmpty()) return null
        val name = FileUtils.sanitizeFileName(fileName ?: "scan_share_${System.currentTimeMillis()}") + ".xlsx"
        return writeToCache(context, name) {
            writeXlsxPages(bitmaps, tables, it)
        }
    }

    private fun writeToMediaStore(
        context: Context,
        filename: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Hitbha")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), contentValues
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { stream -> writer(stream) }
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val documentsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS
                )
                val hitbhaDir = File(documentsDir, "Hitbha")
                if (!hitbhaDir.exists()) hitbhaDir.mkdirs()
                val file = File(hitbhaDir, filename)
                FileOutputStream(file).use { stream -> writer(stream) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e("OfficeUtils", "Failed to save $filename", e)
            null
        }
    }

    private fun writeToCache(
        context: Context,
        filename: String,
        writer: (OutputStream) -> Unit
    ): Uri? {
        return try {
            val dir = File(context.cacheDir, "share")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { stream -> writer(stream) }
            context.getFileUri(file)
        } catch (e: Exception) {
            Log.e("OfficeUtils", "Failed to create share file $filename", e)
            null
        }
    }

    private fun emuSize(bmp: Bitmap): Pair<Long, Long> {
        val w = bmp.width * PX_TO_EMU
        val h = bmp.height * PX_TO_EMU
        val maxW = 6.5 * EMU_PER_INCH
        val maxH = 9.0 * EMU_PER_INCH
        val scale = listOf(1.0, maxW / w, maxH / h).minOrNull() ?: 1.0
        return (w * scale).toLong() to (h * scale).toLong()
    }

    private fun writePng(bmp: Bitmap, stream: OutputStream) {
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }

    private fun writeDocx(bitmaps: List<Bitmap>, stream: OutputStream) {
        val sizes = bitmaps.map { emuSize(it) }
        ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(buildDocxDocumentXml(sizes).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("word/_rels/document.xml.rels"))
            val relSb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            for (i in bitmaps.indices) {
                relSb.append("\n<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image${i + 1}.png\"/>")
            }
            relSb.append("\n</Relationships>")
            zip.write(relSb.toString().toByteArray())
            zip.closeEntry()

            bitmaps.forEachIndexed { i, bmp ->
                zip.putNextEntry(java.util.zip.ZipEntry("word/media/image${i + 1}.png"))
                writePng(bmp, zip)
                zip.closeEntry()
            }
        }
    }

    private fun buildDocxDocumentXml(sizes: List<Pair<Long, Long>>): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
<w:body>""")
        sizes.forEachIndexed { i, (cx, cy) ->
            val num = i + 1
            sb.append("""
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing>
<wp:inline distT="0" distB="0" distL="0" distR="0">
<wp:extent cx="$cx" cy="$cy"/>
<wp:effectExtent l="0" t="0" r="0" b="0"/>
<wp:docPr id="$num" name="Picture $num"/>
<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>
<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
<pic:pic>
<pic:nvPicPr><pic:cNvPr id="$num" name="Picture $num"/><pic:cNvPicPr/></pic:nvPicPr>
<pic:blipFill><a:blip r:embed="rId$num"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
</pic:pic>
</a:graphicData></a:graphic>
</wp:inline>
</w:drawing></w:r></w:p>""")
        }
        sb.append("\n</w:body>\n</w:document>")
        return sb.toString()
    }

    private fun writeXlsx(bitmaps: List<Bitmap>, stream: OutputStream) {
        val pages = bitmaps.map { Page.EmbeddedImage(it) }
        writeXlsxWorkbook(pages, stream)
    }

    private fun writeXlsxTables(tables: List<List<List<String>>>, stream: OutputStream) {
        val pages = tables.map { rows ->
            if (hasContent(rows)) Page.TextTable(rows) else Page.TextTable(emptyList())
        }
        writeXlsxWorkbook(pages, stream)
    }

    private fun writeXlsxPages(
        bitmaps: List<Bitmap>,
        tables: List<List<List<String>>>,
        stream: OutputStream
    ) {
        val pages = mutableListOf<Page>()
        bitmaps.forEachIndexed { i, bmp ->
            val rows = tables.getOrNull(i).orEmpty()
            if (hasContent(rows)) {
                pages.add(Page.TextTable(rows))
            } else {
                pages.add(Page.EmbeddedImage(bmp))
            }
        }
        writeXlsxWorkbook(pages, stream)
    }

    private fun hasContent(rows: List<List<String>>): Boolean =
        rows.any { row -> row.any { it.isNotBlank() } }

    private fun writeXlsxWorkbook(pages: List<Page>, stream: OutputStream) {
        val n = pages.size
        if (n == 0) return
        val imageIndexes = pages.mapIndexedNotNull { i, p ->
            if (p is Page.EmbeddedImage) i else null
        }

        ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zip.write(buildContentTypes(n, imageIndexes).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$XML_NS_REL">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("xl/workbook.xml"))
            zip.write(buildWorkbookXml(n).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(buildWorkbookRels(n).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("xl/styles.xml"))
            zip.write(STYLES_XML.toByteArray())
            zip.closeEntry()

            pages.forEachIndexed { i, page ->
                val idx = i + 1
                when (page) {
                    is Page.TextTable -> {
                        zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet$idx.xml"))
                        zip.write(buildWorksheetXml(page.rows).toByteArray())
                        zip.closeEntry()
                    }
                    is Page.EmbeddedImage -> {
                        val bmp = page.bitmap
                        val (cx, cy) = emuSize(bmp)

                        zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet$idx.xml"))
                        zip.write(buildImageSheetXml(cx, cy).toByteArray())
                        zip.closeEntry()

                        zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/_rels/sheet$idx.xml.rels"))
                        zip.write(
                            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$XML_NS_REL">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$idx.xml"/>
</Relationships>""".toByteArray()
                        )
                        zip.closeEntry()

                        zip.putNextEntry(java.util.zip.ZipEntry("xl/drawings/drawing$idx.xml"))
                        zip.write(buildDrawingXml(cx, cy).toByteArray())
                        zip.closeEntry()

                        zip.putNextEntry(java.util.zip.ZipEntry("xl/drawings/_rels/drawing$idx.xml.rels"))
                        zip.write(
                            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$XML_NS_REL">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$idx.png"/>
</Relationships>""".toByteArray()
                        )
                        zip.closeEntry()

                        zip.putNextEntry(java.util.zip.ZipEntry("xl/media/image$idx.png"))
                        writePng(bmp, zip)
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private fun buildContentTypes(n: Int, imageIndexes: List<Int>): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) {
            sb.append("\n<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        for (idx in imageIndexes) {
            sb.append("\n<Override PartName=\"/xl/drawings/drawing${idx + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
        }
        sb.append("\n</Types>")
        return sb.toString()
    }

    private fun buildWorkbookXml(n: Int): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="$XML_NS_MAIN" xmlns:r="$XML_NS_R">
<bookViews><workbookView/></bookViews>
<sheets>""")
        for (i in 1..n) {
            sb.append("\n<sheet name=\"Sheet$i\" sheetId=\"$i\" r:id=\"rId$i\"/>")
        }
        sb.append("\n</sheets>\n</workbook>")
        return sb.toString()
    }

    private fun buildWorkbookRels(n: Int): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$XML_NS_REL">""")
        for (i in 1..n) {
            sb.append("\n<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        sb.append("\n<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        sb.append("\n</Relationships>")
        return sb.toString()
    }

    private fun buildImageSheetXml(cx: Long, cy: Long): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="$XML_NS_MAIN" xmlns:r="$XML_NS_R">
<dimension ref="A1"/>
<sheetViews><sheetView workbookViewId="0"/></sheetViews>
<sheetFormatPr defaultRowHeight="15"/>
<sheetData/>
<drawing r:id="rId1"/>
</worksheet>"""
    }

    private fun buildDrawingXml(cx: Long, cy: Long): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr xmlns:xdr="$XML_NS_XDR" xmlns:a="$XML_NS_A" xmlns:r="$XML_NS_R">
<xdr:oneCellAnchor>
<xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>
<xdr:ext cx="$cx" cy="$cy"/>
<xdr:pic>
<xdr:nvPicPr><xdr:cNvPr id="1" name="Picture 1"/><xdr:cNvPicPr/></xdr:nvPicPr>
<xdr:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>
<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr>
</xdr:pic>
<xdr:clientData/>
</xdr:oneCellAnchor>
</xdr:wsDr>"""
    }

    private fun buildWorksheetXml(rows: List<List<String>>): String {
        if (rows.isEmpty()) return EMPTY_SHEET_XML

        val rowCount = rows.size
        val colCount = rows.maxOf { it.size }
        if (colCount == 0) return EMPTY_SHEET_XML

        val dimension = if (rowCount == 1 && colCount == 1) {
            "A1"
        } else {
            "A1:${colLetter(colCount - 1)}$rowCount"
        }

        val widthPerCol = DoubleArray(colCount) { 8.0 }
        rows.forEach { row ->
            row.forEachIndexed { cIdx, cell ->
                val len = displayWidth(cell)
                if (len > widthPerCol[cIdx]) widthPerCol[cIdx] = len.toDouble()
            }
        }

        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="$XML_NS_MAIN">
<dimension ref="$dimension"/>
<sheetViews><sheetView workbookViewId="0"/></sheetViews>
<sheetFormatPr defaultRowHeight="15"/>
<cols>""")
        for (cIdx in 0 until colCount) {
            val width = (widthPerCol[cIdx] + 2).coerceIn(8.0, 50.0)
            val n = cIdx + 1
            sb.append("<col min=\"$n\" max=\"$n\" width=\"$width\" customWidth=\"1\"/>")
        }
        sb.append("</cols>\n<sheetData>")

        rows.forEachIndexed { rIdx, cells ->
            val r = rIdx + 1
            sb.append("\n<row r=\"$r\">")
            cells.forEachIndexed { cIdx, cellText ->
                if (cellText.isNotBlank()) {
                    val ref = colLetter(cIdx) + r
                    sb.append("<c r=\"$ref\" t=\"inlineStr\" s=\"1\"><is><t xml:space=\"preserve\">${xmlEscape(cellText)}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("\n</sheetData>\n</worksheet>")
        return sb.toString()
    }

    private fun displayWidth(text: String): Int {
        var len = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            len += if (cp > 0x1100) 2 else 1
            i += Character.charCount(cp)
        }
        return len
    }

    private fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A'.code + (i % 26)).toChar())
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("\t", " ")
}
