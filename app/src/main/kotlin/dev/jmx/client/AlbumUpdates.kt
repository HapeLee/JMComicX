package dev.jmx.client

internal enum class AlbumUpdateSource(val label: String) {
    ALL("全部"), BOOKSHELF("书架"), FAVORITES("收藏"),
}

internal enum class AlbumUpdateSort(val label: String) {
    RECENT("最近发现"), CHAPTERS("新增章节最多"),
}

/** 按关注范围筛选，Map 天然去重；不能让已移出书架/收藏的残留记录出现在更新页。 */
internal fun pendingAlbumUpdates(
    records: Map<String, AlbumUpdateRecord>,
    bookshelfIds: Set<String>,
    favoriteIds: Set<String>,
    source: AlbumUpdateSource = AlbumUpdateSource.ALL,
    sort: AlbumUpdateSort = AlbumUpdateSort.RECENT,
): List<AlbumUpdateRecord> {
    val members = when (source) {
        AlbumUpdateSource.ALL -> bookshelfIds + favoriteIds
        AlbumUpdateSource.BOOKSHELF -> bookshelfIds
        AlbumUpdateSource.FAVORITES -> favoriteIds
    }
    val comparator = when (sort) {
        AlbumUpdateSort.RECENT -> compareByDescending<AlbumUpdateRecord> { it.detectedAt }
            .thenByDescending { it.pendingChapters }
        AlbumUpdateSort.CHAPTERS -> compareByDescending<AlbumUpdateRecord> { it.pendingChapters }
            .thenByDescending { it.detectedAt }
    }.thenBy { it.albumId }
    return records.values.filter { it.hasUpdate && it.albumId in members }.sortedWith(comparator)
}
