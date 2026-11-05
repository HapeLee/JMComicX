package io.kazusa.jmcomic.core.api

import io.kazusa.jmcomic.core.chapter.ChapterTemplate
import io.kazusa.jmcomic.core.chapter.ChapterTemplateParser
import io.kazusa.jmcomic.core.chapter.ScrambleIdCache
import io.kazusa.jmcomic.core.network.JmxApiClient
import io.kazusa.jmcomic.core.network.apiRequest
import io.kazusa.jmcomic.core.network.withExchange
import io.kazusa.jmcomic.core.protocol.ApiRoute
import io.kazusa.jmcomic.core.protocol.JmId
import io.kazusa.jmcomic.core.protocol.JmxProtocolConstants
import io.kazusa.jmcomic.core.result.JmxError
import io.kazusa.jmcomic.core.result.JmxResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ChapterApi(
    private val apiClient: JmxApiClient,
    private val parser: ChapterTemplateParser = ChapterTemplateParser(),
    private val scrambleIdCache: ScrambleIdCache = ScrambleIdCache()
) {

    suspend fun detail(chapterId: String): JmxResult<PhotoDetail> {
        val id = when (val parsed = JmId.parse(chapterId)) {
            is JmxResult.Success -> parsed.value
            is JmxResult.Failure -> return parsed
        }
        val data = when (
            val result = apiClient.requestJson(
                apiRequest(ApiRoute.Chapter) {
                    query("id", id)
                }
            )
        ) {
            is JmxResult.Success -> result.value
            is JmxResult.Failure -> return result
        }
        val root = data.asObjectOrNull()
            ?: return JmxResult.Failure(JmxError.Schema("chapter data is not an object"))
        if (root.stringOrNull("name", "title") == null && root.pageArrOrEmpty().isEmpty()) {
            return JmxResult.Failure(
                JmxError.Api(
                    code = 404,
                    message = "章节不存在或无数据：$id"
                )
            )
        }
        val detail = root.toPhotoDetail()
        detail.scrambleId?.let { scrambleIdCache.put(detail.id, it, detail.albumId) }
        return JmxResult.Success(detail)
    }

    suspend fun scrambleId(
        chapterId: String,
        albumId: String? = null,
        shunt: String = "1"
    ): JmxResult<Int> {
        val id = when (val parsed = JmId.parse(chapterId)) {
            is JmxResult.Success -> parsed.value
            is JmxResult.Failure -> return parsed
        }
        val album = albumId?.let { JmId.parseOrNull(it) }
        scrambleIdCache.get(id, album)?.let { return JmxResult.Success(it) }

        val template = when (val result = template(chapterId = id, shunt = shunt)) {
            is JmxResult.Success -> result.value
            is JmxResult.Failure -> {

                val fallback = JmxProtocolConstants.Scramble220980
                scrambleIdCache.put(id, fallback, album)
                return JmxResult.Success(fallback)
            }
        }
        scrambleIdCache.put(id, template.scrambleId, album ?: template.albumId.toString())
        return JmxResult.Success(template.scrambleId)
    }

    suspend fun template(
        chapterId: String,
        shunt: String,
        page: Int = 0,
        mode: String = "vertical",
        express: String = "off",
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): JmxResult<ChapterTemplate> {
        val id = when (val parsed = JmId.parse(chapterId)) {
            is JmxResult.Success -> parsed.value
            is JmxResult.Failure -> return parsed
        }
        val response = when (
            val result = apiClient.requestTextResponse(
                apiRequest(ApiRoute.ChapterViewTemplate) {
                    query("id", id)
                    query("app_img_shunt", shunt)
                    query("mode", mode)
                    queryAtLeast("page", page, minimum = 0)
                    query("express", express)
                    query("v", timestampSeconds)
                }
            )
        ) {
            is JmxResult.Success -> result.value
            is JmxResult.Failure -> return result
        }
        return when (val parsed = parser.parse(response.text)) {
            is JmxResult.Success -> {
                scrambleIdCache.put(
                    photoId = parsed.value.chapterId,
                    scrambleId = parsed.value.scrambleId,
                    albumId = parsed.value.albumId.toString()
                )
                parsed
            }
            is JmxResult.Failure -> JmxResult.Failure(parsed.error.withExchange(response.exchange))
        }
    }

    fun cachedScrambleId(chapterId: String, albumId: String? = null): Int? {
        val id = JmId.parseOrNull(chapterId) ?: return null
        val album = albumId?.let { JmId.parseOrNull(it) }
        return scrambleIdCache.get(id, album)
    }
}

private fun JsonObject.pageArrOrEmpty(): List<String> {
    val direct = get("page_arr") ?: get("images") ?: return emptyList()
    return when {
        direct is JsonArray -> direct.mapNotNull {
            it.takeIf { el -> (el is JsonPrimitive && el != JsonNull) }?.jsonPrimitive?.content
        }
        else -> emptyList()
    }
}
