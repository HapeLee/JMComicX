package io.kazusa.jmcomic.core.api

import io.kazusa.jmcomic.core.protocol.JmId
import io.kazusa.jmcomic.core.result.JmxError
import io.kazusa.jmcomic.core.result.JmxResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 明确的空数组才是"空"的证据；缺失或格式异常不算。 */
internal fun JsonElement.discoveryPage(): JmxResult<AlbumPage> {
    val root = asObjectOrNull()
    val list = if (this is JsonArray) {
        this
    } else {
        root?.explicitArray("content", "list", "data", "albums", "photos")
            ?: return discoverySchema("发现列表缺失或格式异常", "content")
    }
    if (list.any { it !is JsonObject }) return discoverySchema("发现列表包含不明条目", "content")
    val albums = ArrayList<AlbumSummary>(list.size)
    for (item in list) {
        val album = (item as JsonObject).toAlbumSummary()
        val id = when (val parsed = JmId.parse(album.id)) {
            is JmxResult.Success -> parsed.value
            is JmxResult.Failure -> return discoverySchema("发现列表缺少有效漫画编号", "id")
        }
        albums.add(album.copy(id = id))
    }
    val totalElement = root?.get("total")?.takeUnless { it is JsonNull }
    val total = (totalElement as? JsonPrimitive)?.content?.toIntOrNull()
    if (totalElement != null && total == null) return discoverySchema("发现列表总数格式异常", "total")
    if (total != null && (total < 0 || total < albums.size)) {
        return discoverySchema("发现列表总数不一致", "total")
    }
    return JmxResult.Success(AlbumPage(total, albums, root?.toRawMap() ?: emptyMap()))
}

internal fun JsonObject.discoveryWeekInfo(): JmxResult<WeekInfo> {
    val categories = explicitArray("categories", "category")
        ?: return discoverySchema("每周必看期刊列表缺失", "categories")
    val types = explicitArray("type", "types")
        ?: return discoverySchema("每周必看类型列表缺失", "type")
    val categoryItems = categories.map { it as? JsonObject }
    val typeItems = types.map { it as? JsonObject }
    if (categoryItems.any { it?.explicitId() == null } || typeItems.any { it?.explicitId() == null }) {
        return discoverySchema("每周必看筛选项格式异常", "id")
    }
    return JmxResult.Success(
        WeekInfo(
            categories = categoryItems.mapNotNull { item ->
                item?.let {
                    WeekCategory(
                        id = it.explicitId().orEmpty(),
                        time = it.stringOrNull("time"),
                        title = it.stringOrNull("title", "name"),
                    )
                }
            }.distinctBy { it.id },
            types = typeItems.mapNotNull { item ->
                item?.let { WeekType(id = it.explicitId().orEmpty(), title = it.stringOrNull("title", "name")) }
            }.distinctBy { it.id },
            raw = toRawMap(),
        )
    )
}

private fun JsonObject.explicitArray(vararg keys: String): JsonArray? {
    for (key in keys) {
        val value = get(key)?.takeUnless { it is JsonNull } ?: continue
        return value as? JsonArray
    }
    return null
}

/** 只接受字符串或数值；布尔 id 视为格式异常，不接受。 */
private fun JsonObject.explicitId(): String? = (get("id") as? JsonPrimitive)
    ?.takeUnless { !it.isString && (it.content == "true" || it.content == "false") }
    ?.content
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

private fun discoverySchema(message: String, field: String): JmxResult.Failure =
    JmxResult.Failure(JmxError.Schema(message, field = field))
