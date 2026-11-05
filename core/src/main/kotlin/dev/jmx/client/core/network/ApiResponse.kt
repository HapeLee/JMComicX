package dev.jmx.client.core.network

import dev.jmx.client.core.result.NetworkExchange
import kotlinx.serialization.json.JsonElement

data class ApiEnvelope(
    val code: Int,
    val data: JsonElement?,
    val errorMessage: String?,
    val rawBody: String
)

data class RawNetworkResponse(
    val statusCode: Int,
    val body: String,
    val contentType: String?,
    val requestUrl: String,
    val tokenTimestampSeconds: Long
)

data class JsonNetworkResponse(
    val data: JsonElement,
    val exchange: NetworkExchange
)

data class TextNetworkResponse(
    val text: String,
    val exchange: NetworkExchange
)
