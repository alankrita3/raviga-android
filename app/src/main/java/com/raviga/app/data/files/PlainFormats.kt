package com.raviga.app.data.files

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

/** Text out of Word, RTF and plain files, with no Android dependencies (unit-tested on the JVM). */
object PlainFormats {

    data class Read(val text: String, val pageCount: Int?, val pagesCut: Boolean)

    /** UTF-8 (with or without BOM), UTF-16 with BOM, else Windows-1252. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.size - start))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    /** word/document.xml: paragraphs, line breaks, tabs and table cells, in reading order. */
    fun docx(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use ""
            docxXml(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText())
        }
    }.getOrDefault("")

    fun docxXml(xml: String): String = xml
        .replace(Regex("<w:tab/>"), "\t")
        .replace(Regex("<w:br[^>]*/>"), "\n")
        .replace(Regex("</w:tc>"), "\t")
        .replace(Regex("</w:p>"), "\n")
        .replace(Regex("<w:instrText[^>]*>.*?</w:instrText>"), "")
        .replace(Regex("<[^>]+>"), "")
        .let(::unescapeXml)

    /**
     * Review #22 (Android): a numeric reference is the whole code point (an emoji past U+FFFF was cut to one UTF-16
     * unit), and one that isn't a valid code point is dropped instead of throwing, which emptied the whole document.
     */
    internal fun unescapeXml(s: String): String = s
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> codePoint(m.groupValues[1].toLongOrNull(16)) }
        .replace(Regex("&#([0-9]+);")) { m -> codePoint(m.groupValues[1].toLongOrNull()) }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun codePoint(value: Long?): String =
        if (value != null && value in 1..0x10FFFF && value !in 0xD800..0xDFFF) String(Character.toChars(value.toInt())) else ""

    /** Plain text from RTF: groups like fonts and pictures dropped, escapes decoded. */
    fun rtf(rtf: String): String {
        val out = StringBuilder()
        var i = 0
        var depth = 0
        var skipDepth = -1   // inside a destination group we don't want ({\*..}, fonttbl, colortbl, pict…)
        var ucSkip = 1
        var pendingSkip = 0
        val skipWords = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "listtable", "listoverridetable", "rsidtbl", "generator", "themedata", "datastore", "latentstyles")
        while (i < rtf.length) {
            val c = rtf[i]
            when {
                c == '{' -> { depth++; i++ }
                c == '}' -> { if (depth == skipDepth) skipDepth = -1; depth--; i++ }
                c == '\\' && i + 1 < rtf.length -> {
                    val n = rtf[i + 1]
                    when {
                        n == '\\' || n == '{' || n == '}' -> { if (skipDepth < 0) out.append(n); i += 2 }
                        n == '*' -> { if (skipDepth < 0) skipDepth = depth; i += 2 }
                        n == '\'' && i + 3 < rtf.length -> {
                            val hex = rtf.substring(i + 2, i + 4)
                            if (skipDepth < 0) {
                                if (pendingSkip > 0) pendingSkip-- else
                                    runCatching { out.append(String(byteArrayOf(hex.toInt(16).toByte()), Charset.forName("windows-1252"))) }
                            }
                            i += 4
                        }
                        n == '~' -> { if (skipDepth < 0) out.append(' '); i += 2 }
                        n == '-' || n == '_' -> { i += 2 }
                        n.isLetter() -> {
                            var j = i + 1
                            while (j < rtf.length && rtf[j].isLetter()) j++
                            val word = rtf.substring(i + 1, j)
                            var k = j
                            if (k < rtf.length && (rtf[k] == '-' || rtf[k].isDigit())) { k++; while (k < rtf.length && rtf[k].isDigit()) k++ }
                            val param = rtf.substring(j, k).toIntOrNull()
                            if (k < rtf.length && rtf[k] == ' ') k++
                            if (word in skipWords && skipDepth < 0) skipDepth = depth
                            if (skipDepth < 0) when (word) {
                                "par", "line", "row" -> out.append('\n')
                                "tab", "cell" -> out.append('\t')
                                "uc" -> ucSkip = param ?: 1
                                "u" -> if (param != null) {
                                    out.append((if (param < 0) param + 65536 else param).toChar())
                                    pendingSkip = ucSkip
                                }
                            }
                            i = k
                        }
                        else -> i += 2
                    }
                }
                c == '\r' || c == '\n' -> i++
                else -> {
                    if (skipDepth < 0) { if (pendingSkip > 0) pendingSkip-- else out.append(c) }
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Trims each line, collapses runs of blank lines, drops control characters. */
    fun tidy(text: String): String = text
        .replace("\r\n", "\n").replace('\r', '\n')
        .filter { it == '\n' || it == '\t' || !it.isISOControl() }
        .lines().joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
