package com.lhzkml.DayWage.util

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 单元格的值。数字会写成 Excel 的数值单元格，文本走 inlineStr。 */
sealed interface Cell {
    data class Text(val value: String) : Cell

    /** 表头：加粗 + 浅色底 */
    data class Header(val value: String) : Cell
    data class Num(val value: Double) : Cell
    data object Empty : Cell
}

/**
 * 极简 xlsx 生成器：手写 OOXML + zip，不引第三方库。
 * 支持表头加粗底色、金额两位小数、按内容估算列宽。
 */
object XlsxWriter {

    /** 1 = 表头（加粗+浅底），2 = 两位小数 */
    private const val STYLE_HEADER = 1
    private const val STYLE_NUMBER = 2

    fun write(file: File, sheetName: String, rows: List<List<Cell>>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.put("[Content_Types].xml", contentTypes())
            zip.put("_rels/.rels", rootRels())
            zip.put("xl/workbook.xml", workbook(sheetName))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels())
            zip.put("xl/styles.xml", styles())
            zip.put("xl/worksheets/sheet1.xml", sheet(rows))
        }
    }

    private fun ZipOutputStream.put(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRels() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbook(sheetName: String) = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <sheets><sheet name="${escape(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRels() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun styles() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <fonts count="2">
        <font><sz val="11"/><color theme="1"/><name val="Calibri"/></font>
        <font><b/><sz val="11"/><color theme="1"/><name val="Calibri"/></font>
        </fonts>
        <fills count="3">
        <fill><patternFill patternType="none"/></fill>
        <fill><patternFill patternType="gray125"/></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FFF0F3F8"/><bgColor indexed="64"/></patternFill></fill>
        </fills>
        <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
        <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
        <cellXfs count="3">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
        <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
        <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
        </cellXfs>
        <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
        </styleSheet>
    """.trimIndent()

    private fun sheet(rows: List<List<Cell>>): String {
        val widths = columnWidths(rows)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        if (widths.isNotEmpty()) {
            sb.append("<cols>")
            widths.forEachIndexed { i, w ->
                sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            val rowNum = r + 1
            if (row.isEmpty()) return@forEachIndexed
            sb.append("<row r=\"$rowNum\">")
            row.forEachIndexed { c, cell ->
                if (cell is Cell.Empty) return@forEachIndexed
                val ref = "${columnName(c)}$rowNum"
                when (cell) {
                    is Cell.Header -> sb.append(
                        "<c r=\"$ref\" t=\"inlineStr\" s=\"$STYLE_HEADER\">" +
                            "<is><t xml:space=\"preserve\">${escape(cell.value)}</t></is></c>",
                    )
                    is Cell.Text -> sb.append(
                        "<c r=\"$ref\" t=\"inlineStr\">" +
                            "<is><t xml:space=\"preserve\">${escape(cell.value)}</t></is></c>",
                    )
                    is Cell.Num -> sb.append(
                        "<c r=\"$ref\" s=\"$STYLE_NUMBER\"><v>${trimNumber(cell.value)}</v></c>",
                    )
                    Cell.Empty -> Unit
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** 按每列最长文本估算宽度，中文按两个字符宽算。 */
    private fun columnWidths(rows: List<List<Cell>>): List<String> {
        val count = rows.maxOfOrNull { it.size } ?: return emptyList()
        return (0 until count).map { i ->
            val longest = rows.maxOfOrNull { row ->
                when (val cell = row.getOrNull(i)) {
                    is Cell.Header -> cell.value.sumOf { ch -> if (ch.code > 0x2E80) 2 else 1 }
                    is Cell.Text -> cell.value.sumOf { ch -> if (ch.code > 0x2E80) 2 else 1 }
                    is Cell.Num -> trimNumber(cell.value).length
                    else -> 0
                }
            } ?: 0
            (longest + 2).coerceIn(8, 42).toString()
        }
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun trimNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v)

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
