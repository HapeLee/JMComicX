package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.api.WeekType

/** 服务端 /week 元数据里已核对过的三种类型 ID 的中文兜底；线上 ID 原样保留。 */
internal fun WeekType.displayTitle(): String {
    val name = title?.trim().orEmpty()
    if (name.isNotBlank() && !name.equals(id, ignoreCase = true)) return name
    return when (id.lowercase()) {
        "manga" -> "日漫"
        "another" -> "其他"
        "hanman" -> "韩漫"
        else -> name.ifBlank { "类型 $id" }
    }
}
