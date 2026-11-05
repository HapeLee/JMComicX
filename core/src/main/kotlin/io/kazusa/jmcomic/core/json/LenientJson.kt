package io.kazusa.jmcomic.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** The wire parser retains the relaxed JSON accepted from the remote API and chapter templates. */
internal val WireJson = Json(JmxJson) {
    isLenient = true
    allowTrailingComma = true
    allowComments = true
}

internal fun Json.parseWireElement(source: String): JsonElement = parseToJsonElement(normalizeJsonQuotes(source))

/** Converts single-quoted JavaScript strings without changing quoted text or URL contents. */
private fun normalizeJsonQuotes(source: String): String {
    if ('\'' !in source) return source
    return buildString {
        var index = 0
        while (index < source.length) {
            val quote = source[index]
            if (quote == '"') {
                append(source[index++])
                while (index < source.length) {
                    val char = source[index++]
                    if (char == '\\' && index < source.length) {
                        val escaped = source[index++]
                        // JavaScript accepts \' inside either kind of quoted string; JSON doesn't.
                        if (escaped != '\'') append('\\')
                        append(escaped)
                    } else {
                        append(char)
                        if (char == '"') break
                    }
                }
            } else if (quote == '\'') {
                index++
                val encoded = StringBuilder("\"")
                var closed = false
                while (index < source.length) {
                    val char = source[index++]
                    when {
                        char == quote -> { closed = true; break }
                        char == '\\' -> {
                            require(index < source.length) { "Unterminated template escape" }
                            val escaped = source[index++]
                            if (escaped == '\'') encoded.append('\'')
                            else encoded.append('\\').append(escaped)
                        }
                        char == '"' -> encoded.append("\\\"")
                        else -> encoded.append(char)
                    }
                }
                require(closed) { "Unterminated template string" }
                encoded.append('"')
                append(JsonPrimitive(JmxJson.parseToJsonElement(encoded.toString()).jsonPrimitive.content))
            } else if (quote == '/' && source.getOrNull(index + 1) == '/') {
                val end = source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length
                append(source.substring(index, end))
                index = end
            } else if (quote == '/' && source.getOrNull(index + 1) == '*') {
                val end = source.indexOf("*/", index + 2)
                require(end >= 0) { "Unterminated template comment" }
                append(source.substring(index, end + 2))
                index = end + 2
            } else {
                append(quote)
                index++
            }
        }
    }
}
