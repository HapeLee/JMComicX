package dev.jmx.client.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Retains fields and defaults in persisted data so older app versions can read it too. */
val JmxJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

/** The remote API sometimes returns numeric fields as strings. Numeric tokens may be fractional. */
internal fun JsonElement.primitiveIntOrNull(): Int? {
    val value = this as? JsonPrimitive ?: return null
    if (value == JsonNull) return null
    return if (value.isString) value.content.toIntOrNull()
    else runCatching { value.content.toBigDecimal().toInt() }.getOrNull()
}

internal fun JsonElement.primitiveBoolean(): Boolean =
    (this as? JsonPrimitive)?.content.equals("true", ignoreCase = true)
