package io.kazusa.jmcomic

/** 更新优先只影响"更新时间"排序；其余排序仍按本地元数据，方向不参与分组。 */
internal fun projectBookshelf(
    entries: List<BookshelfEntry>,
    groupId: String,
    order: BookshelfSortOrder,
    direction: BookshelfSortDirection,
    records: Map<String, AlbumUpdateRecord>,
): List<BookshelfEntry> {
    val ordered = sortBookshelf(
        entries.filter { groupId == ALL_BOOKSHELF_GROUP_ID || groupId in it.groupIds },
        order,
        direction,
    )
    if (order != BookshelfSortOrder.UPDATED) return ordered
    val (pending, other) = ordered.partition { records[it.albumId]?.hasUpdate == true }
    return pending + other
}

/**
 * 只确认"点下确认那一刻已经看到"的观测。
 *
 * 用 [AlbumUpdateRecord.generation] 认身份而不是时间戳：确认期间扫描到的新观测仍属于同一条记录，
 * 只把基线抬到快照当时的话数，之后的新章节继续待提示。记录被重建则 generation 变化，旧快照失效。
 */
internal fun acknowledgeAlbumUpdates(
    current: Map<String, AlbumUpdateRecord>,
    snapshot: Map<String, AlbumUpdateRecord>,
): Map<String, AlbumUpdateRecord> = current.mapValues { (id, record) ->
    val clicked = snapshot[id]
    if (clicked == null || clicked.generation != record.generation) {
        record
    } else {
        record.copy(
            seenChapterCount = maxOf(
                record.seenChapterCount,
                minOf(clicked.latestChapterCount, record.latestChapterCount),
            ),
        )
    }
}
