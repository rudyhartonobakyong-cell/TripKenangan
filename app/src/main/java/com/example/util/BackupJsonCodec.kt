package com.example.util

import java.io.IOException

class BackupCorruptException(message: String, cause: Throwable? = null) : IOException(message, cause)

class BackupInsufficientSpaceException(message: String) : IOException(message)

/**
 * Pure-Kotlin RFC-8259 JSON encoder and recursive-descent parser.
 * Works identically on Android runtime and pure JVM unit tests without external dependencies.
 */
object BackupJsonCodec {

    fun encodePretty(value: Map<String, Any?>): String {
        val sb = StringBuilder()
        appendValue(sb, value, indentLevel = 0)
        return sb.toString()
    }

    fun decodeObject(json: String): Map<String, Any?> {
        val parser = Parser(json)
        val parsed = parser.parseRootObject()
        return parsed
    }

    private fun appendValue(sb: StringBuilder, value: Any?, indentLevel: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Number -> sb.append(value.toString())
            is String -> appendEscapedString(sb, value)
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                val entries = value.entries.toList()
                entries.forEachIndexed { idx, (k, v) ->
                    appendIndent(sb, indentLevel + 1)
                    appendEscapedString(sb, k.toString())
                    sb.append(": ")
                    appendValue(sb, v, indentLevel + 1)
                    if (idx < entries.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                appendIndent(sb, indentLevel)
                sb.append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                items.forEachIndexed { idx, item ->
                    appendIndent(sb, indentLevel + 1)
                    appendValue(sb, item, indentLevel + 1)
                    if (idx < items.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                appendIndent(sb, indentLevel)
                sb.append(']')
            }
            else -> appendEscapedString(sb, value.toString())
        }
    }

    private fun appendIndent(sb: StringBuilder, indentLevel: Int) {
        repeat(indentLevel * 2) { sb.append(' ') }
    }

    private fun appendEscapedString(sb: StringBuilder, raw: String) {
        sb.append('"')
        for (ch in raw) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format("\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun parseRootObject(): Map<String, Any?> {
            skipWhitespace()
            if (pos >= input.length || input[pos] != '{') {
                throw BackupCorruptException("Format JSON tidak valid: harus diawali objek '{'.")
            }
            val result = parseObject()
            skipWhitespace()
            if (pos < input.length) {
                throw BackupCorruptException("Format JSON rusak: terdapat karakter sisa yang tidak valid.")
            }
            return result
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) {
                throw BackupCorruptException("Format JSON tidak lengkap.")
            }
            return when (val ch = input[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> expectKeyword("true", true)
                'f' -> expectKeyword("false", false)
                'n' -> expectKeyword("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw BackupCorruptException("Karakter JSON tidak dikenali '$ch' pada posisi $pos.")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // skip '{'
            skipWhitespace()
            val map = LinkedHashMap<String, Any?>()
            if (pos < input.length && input[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (pos >= input.length || input[pos] != '"') {
                    throw BackupCorruptException("Kunci objek JSON tidak valid pada posisi $pos.")
                }
                val key = parseString()
                skipWhitespace()
                if (pos >= input.length || input[pos] != ':') {
                    throw BackupCorruptException("Diharapkan ':' setelah kunci '$key'.")
                }
                pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos >= input.length) {
                    throw BackupCorruptException("Objek JSON terpotong sebelum selesai.")
                }
                when (input[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw BackupCorruptException("Diharapkan ',' atau '}' pada posisi $pos.")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // skip '['
            skipWhitespace()
            val list = ArrayList<Any?>()
            if (pos < input.length && input[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                if (pos >= input.length) {
                    throw BackupCorruptException("Array JSON terpotong sebelum selesai.")
                }
                when (input[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw BackupCorruptException("Diharapkan ',' atau ']' pada posisi $pos.")
                }
            }
        }

        private fun parseString(): String {
            pos++ // skip opening '"'
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= input.length) {
                            throw BackupCorruptException("Escape sequence JSON terpotong.")
                        }
                        when (val esc = input[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > input.length) {
                                    throw BackupCorruptException("Unicode escape JSON tidak lengkap.")
                                }
                                val hex = input.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw BackupCorruptException("Unicode escape JSON tidak valid: \\u$hex")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw BackupCorruptException("Escape JSON tidak valid: \\$esc")
                        }
                    }
                    else -> {
                        if (ch.code < 0x20) {
                            throw BackupCorruptException("Karakter kontrol tidak valid dalam string JSON.")
                        }
                        sb.append(ch)
                    }
                }
            }
            throw BackupCorruptException("String JSON tidak ditutup dengan tanda kutip.")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (input[pos] == '-') pos++
            if (pos >= input.length || input[pos] !in '0'..'9') {
                throw BackupCorruptException("Angka JSON tidak valid pada posisi $start.")
            }
            while (pos < input.length && input[pos] in '0'..'9') pos++
            var isFloating = false
            if (pos < input.length && input[pos] == '.') {
                isFloating = true
                pos++
                if (pos >= input.length || input[pos] !in '0'..'9') {
                    throw BackupCorruptException("Angka desimal JSON tidak valid.")
                }
                while (pos < input.length && input[pos] in '0'..'9') pos++
            }
            if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                isFloating = true
                pos++
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                if (pos >= input.length || input[pos] !in '0'..'9') {
                    throw BackupCorruptException("Eksponen angka JSON tidak valid.")
                }
                while (pos < input.length && input[pos] in '0'..'9') pos++
            }
            val token = input.substring(start, pos)
            return if (isFloating) {
                token.toDoubleOrNull()
                    ?: throw BackupCorruptException("Angka desimal JSON tidak dapat dibaca: $token")
            } else {
                token.toLongOrNull()
                    ?: token.toDoubleOrNull()
                    ?: throw BackupCorruptException("Angka JSON tidak dapat dibaca: $token")
            }
        }

        private fun expectKeyword(keyword: String, result: Any?): Any? {
            if (!input.startsWith(keyword, pos)) {
                throw BackupCorruptException("Kata kunci JSON tidak valid pada posisi $pos.")
            }
            pos += keyword.length
            return result
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
