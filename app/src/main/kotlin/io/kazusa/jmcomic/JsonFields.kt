package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.json.JmxJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun parseJsonArray(text: String): JsonArray = JmxJson.parseToJsonElement(text).jsonArray
internal fun parseJsonObject(text: String): JsonObject = JmxJson.parseToJsonElement(text).jsonObject
internal fun Iterable<String>.toJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))
internal val PrettyJson = Json(JmxJson) { prettyPrint = true }

// Keep the tolerant reads used by older persisted bookshelf/update records.
internal fun JsonElement?.stringValue(default: String = ""): String = when (this) {
    null, JsonNull -> default
    is JsonPrimitive -> content
    else -> toString()
}
internal fun JsonObject.stringValue(key: String, default: String = ""): String = this[key].stringValue(default)
internal fun JsonArray.stringValue(index: Int): String = getOrNull(index).stringValue()
internal fun JsonObject.longValue(key: String, default: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.let { value ->
        value.content.toLongOrNull() ?: value.content.toDoubleOrNull()?.toLong()
    } ?: default
internal fun JsonObject.intValue(key: String, default: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.let { value ->
        value.content.toIntOrNull() ?: value.content.toDoubleOrNull()?.toInt()
    } ?: default
internal fun JsonObject.doubleValue(key: String, default: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: default
internal fun JsonObject.booleanValue(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.content?.let {
        when { it.equals("true", true) -> true; it.equals("false", true) -> false; else -> null }
    } ?: default
internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonArray.objectOrNull(index: Int): JsonObject? = getOrNull(index) as? JsonObject

internal fun JsonObject.requiredString(key: String): String =
    (getValue(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: error("Expected string field: $key")
internal fun JsonObject.requiredInt(key: String): Int =
    getValue(key).jsonPrimitive.content.let { it.toIntOrNull() ?: it.toDouble().toInt() }
internal fun JsonObject.requiredLong(key: String): Long =
    getValue(key).jsonPrimitive.content.let { it.toLongOrNull() ?: it.toDouble().toLong() }
internal fun JsonObject.requiredArray(key: String): JsonArray = getValue(key).jsonArray
internal fun JsonObject.requiredObject(key: String): JsonObject = getValue(key).jsonObject
internal fun JsonArray.requiredObject(index: Int): JsonObject = get(index).jsonObject
internal fun JsonArray.requiredString(index: Int): String =
    get(index).jsonPrimitive.takeIf { it.isString }?.content ?: error("Expected string at $index")
