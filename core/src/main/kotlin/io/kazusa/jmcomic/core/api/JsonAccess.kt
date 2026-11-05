package io.kazusa.jmcomic.core.api

import io.kazusa.jmcomic.core.json.primitiveBoolean
import io.kazusa.jmcomic.core.json.primitiveIntOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.stringOrNull(vararg names: String): String? {
    for (name in names) {
        val value = get(name)
        if (value is JsonPrimitive && value != JsonNull) {
            return value.content
        }
    }
    return null
}

internal fun JsonObject.intOrNull(vararg names: String): Int? {
    for (name in names) {
        val value = get(name)
        if (value is JsonPrimitive && value != JsonNull) {
            return value.primitiveIntOrNull()
        }
    }
    return null
}

internal fun JsonObject.doubleOrNull(vararg names: String): Double? {
    for (name in names) {
        val value = get(name)
        if (value is JsonPrimitive && value != JsonNull) {
            return value.doubleOrNull
        }
    }
    return null
}

internal fun JsonObject.booleanOrNull(vararg names: String): Boolean? {
    for (name in names) {
        val value = get(name)
        if (value is JsonPrimitive && value != JsonNull) {
            return value.primitiveBoolean()
        }
    }
    return null
}

internal fun JsonObject.stringListOrEmpty(vararg names: String): List<String> {
    for (name in names) {
        val value = get(name) ?: continue
        if (value is JsonArray) {
            return value
                .mapNotNull { item ->
                    item.takeIf { it is JsonPrimitive && it != JsonNull }?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                }
                .filter { it.isNotBlank() }
        }
        if (value is JsonPrimitive && value != JsonNull) {
            val text = value.content
            if (text.isNotBlank()) return listOf(text)
        }
    }
    return emptyList()
}

internal fun JsonElement?.asObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

internal fun JsonElement?.asObjectListOrEmpty(): List<JsonObject> {
    return (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
}

internal fun JsonObject.toRawMap(): Map<String, Any?> = entries.associate { (key, value) ->
    key to when (value) {
        JsonNull -> null
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.booleanOrNull != null -> value.boolean
            else -> value.content.toBigDecimalOrNull()
        }
        else -> value.toString()
    }
}
