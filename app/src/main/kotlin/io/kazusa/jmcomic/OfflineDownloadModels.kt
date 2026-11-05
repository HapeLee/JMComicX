package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.api.AlbumChapter
import io.kazusa.jmcomic.core.api.AlbumDetail
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class OfflineDownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

internal data class OfflinePage(
    val index: Int,
    val relativePath: String,
    val byteCount: Long,
    val sha256: String,
)

internal data class OfflineChapter(
    val id: String,
    val name: String,
    val sort: String?,
    val expectedPageCount: Int = 0,
    val pages: List<OfflinePage> = emptyList(),
    // Identity of the template's ordered filenames + scramble parameters, never URLs/cookies.
    val sourceFingerprint: String? = null,
) {
    val completed: Boolean get() = expectedPageCount > 0 && pages.size == expectedPageCount &&
        pages.withIndex().all { (index, page) -> page.index == index && page.byteCount > 0 }
    val downloadedPages: Int get() = pages.size
    val byteCount: Long get() = pages.sumOf { it.byteCount }
}

internal data class OfflineAlbum(
    val id: String,
    val title: String,
    val detail: AlbumDetail,
    val chapters: List<OfflineChapter>,
    val status: OfflineDownloadStatus = OfflineDownloadStatus.QUEUED,
    val coverUrl: String = "",
    val currentChapterId: String? = null,
    val currentChapterName: String? = null,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val totalChapters: Int get() = chapters.size
    val completedChapters: Int get() = chapters.count { it.completed }
    val downloadedPages: Int get() = chapters.sumOf { it.downloadedPages }
    val totalPages: Int get() = chapters.sumOf { it.expectedPageCount }
    val byteCount: Long get() = chapters.sumOf { it.byteCount }
    val completed: Boolean get() = chapters.isNotEmpty() && chapters.all { it.completed }
    fun toHomeAlbum() = HomeAlbum(id, title, detail.authors.joinToString(" / "), coverUrl, "")
}

internal class OfflineStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** These helpers are JVM-only so disk integrity and persistence can be tested without Android. */
internal object OfflineFiles {
    const val MIN_FREE_BYTES = 8L * 1024 * 1024
    private val safeId = Regex("[0-9]{1,18}")
    private val pagePath = Regex("[0-9]{1,18}/[0-9]{1,18}/[0-9]{6}\\.(png|jpg|webp|gif|avif|heif|heic)")

    fun requireId(id: String) { require(safeId.matches(id)) { "无效的漫画或章节 ID" } }

    fun resolve(root: File, relative: String): File {
        require(pagePath.matches(relative)) { "无效的离线页面路径" }
        val base = root.canonicalFile.toPath()
        val result = File(root, relative).canonicalFile
        require(result.toPath().startsWith(base) && result.toPath() != base) { "离线路径超出下载目录" }
        return result
    }

    fun albumDirectory(root: File, id: String): File {
        requireId(id)
        val result = File(root, id).canonicalFile
        require(result.parentFile == root.canonicalFile) { "离线路径超出下载目录" }
        return result
    }

    fun ensureDirectory(directory: File) {
        // mkdirs returns false when another worker created the same directory after our first check.
        if (!(directory.isDirectory || directory.mkdirs() || directory.isDirectory)) {
            throw OfflineStorageException("无法创建下载目录，请检查存储空间和访问权限")
        }
    }

    fun ensureSpace(root: File, additional: Long = 0) {
        if (root.usableSpace < MIN_FREE_BYTES + additional.coerceAtLeast(0)) {
            throw OfflineStorageException("存储空间不足，请释放空间后继续下载")
        }
    }

    fun digest(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun valid(root: File, page: OfflinePage): Boolean = runCatching {
        val file = resolve(root, page.relativePath)
        page.byteCount > 0 && file.isFile && file.length() == page.byteCount && digest(file) == page.sha256
    }.getOrDefault(false)

    fun atomicMove(source: File, target: File) {
        // Same-filesystem atomic replacement. Never publish via copy/delete fallback.
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun atomicWrite(target: File, text: String) {
        val parent = requireNotNull(target.parentFile) { "离线元数据缺少上级目录" }
        ensureDirectory(parent)
        val temporary = File(parent, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            atomicMove(temporary, target)
        } finally {
            temporary.delete()
        }
    }

    fun recover(root: File, album: OfflineAlbum): OfflineAlbum {
        val chapters = album.chapters.map { chapter ->
            // Keep only a continuous prefix: gaps never become a readable completed chapter.
            val prefix = chapter.pages.take(chapter.expectedPageCount.coerceAtLeast(0))
                .withIndex().takeWhile { (index, page) ->
                    page.index == index && page.relativePath.startsWith("${album.id}/${chapter.id}/") && valid(root, page)
                }.map { it.value }
            chapter.copy(pages = prefix)
        }
        val damaged = chapters != album.chapters
        val complete = chapters.isNotEmpty() && chapters.all { it.completed }
        val status = when {
            complete -> OfflineDownloadStatus.COMPLETED
            damaged || album.status == OfflineDownloadStatus.COMPLETED -> OfflineDownloadStatus.FAILED
            album.status == OfflineDownloadStatus.DOWNLOADING -> OfflineDownloadStatus.QUEUED
            else -> album.status
        }
        val cover = chapters.firstOrNull { it.pages.isNotEmpty() }?.pages?.firstOrNull()
            ?.let { resolve(root, it.relativePath).toURI().toString() }.orEmpty()
        return album.copy(chapters = chapters, status = status, coverUrl = cover,
            currentChapterId = null, currentChapterName = null,
            error = if (damaged || (album.status == OfflineDownloadStatus.COMPLETED && !complete))
                "离线文件缺失或损坏，请继续下载以修复" else album.error)
    }
}

/** Explicit JSON whitelist; never persist raw API/user/session fields. */
internal object OfflineMetadata {
    fun encode(albums: List<OfflineAlbum>): String = buildJsonObject {
        put("version", 1)
        put("albums", JsonArray(albums.map { album ->
            buildJsonObject {
                put("id", album.id)
                put("title", album.title)
                put("detail", encodeDetail(album.detail))
                put("status", album.status.name)
                album.error?.let { put("error", it) }
                put("updatedAt", album.updatedAt)
                put("chapters", JsonArray(album.chapters.map { chapter ->
                    buildJsonObject {
                        put("id", chapter.id)
                        put("name", chapter.name)
                        chapter.sort?.let { put("sort", it) }
                        put("expected", chapter.expectedPageCount)
                        chapter.sourceFingerprint?.let { put("fingerprint", it) }
                        put("pages", JsonArray(chapter.pages.map { page ->
                            buildJsonObject {
                                put("index", page.index)
                                put("path", page.relativePath)
                                put("bytes", page.byteCount)
                                put("sha256", page.sha256)
                            }
                        }))
                    }
                }))
            }
        }))
    }.toString()

    fun decode(text: String): List<OfflineAlbum> {
        val root = parseJsonObject(text)
        require(root.requiredInt("version") == 1) { "不支持的离线元数据版本" }
        val albums = root.requiredArray("albums")
        val result = (0 until albums.size).map { index ->
            val item = albums.requiredObject(index)
            val id = item.requiredString("id").also(OfflineFiles::requireId)
            val detail = decodeDetail(item.requiredObject("detail"))
            require(detail.id == id)
            val chapters = item.requiredArray("chapters").objects().map { chapter ->
                val chapterId = chapter.requiredString("id").also(OfflineFiles::requireId)
                val expected = chapter.requiredInt("expected").also { require(it in 0..100_000) }
                val pages = chapter.requiredArray("pages").objects().map { page ->
                    OfflinePage(page.requiredInt("index"), page.requiredString("path"), page.requiredLong("bytes"), page.requiredString("sha256"))
                }
                OfflineChapter(chapterId, chapter.requiredString("name"), chapter.nullableString("sort"),
                    expected, pages, chapter.nullableString("fingerprint"))
            }
            require(chapters.map { it.id }.distinct().size == chapters.size)
            OfflineAlbum(id, item.requiredString("title"), detail, chapters,
                OfflineDownloadStatus.valueOf(item.requiredString("status")), error = item.nullableString("error"),
                updatedAt = item.longValue("updatedAt", 0L))
        }
        require(result.map { it.id }.distinct().size == result.size)
        return result
    }

    fun sanitize(detail: AlbumDetail): AlbumDetail = decodeDetail(encodeDetail(detail))

    private fun encodeDetail(detail: AlbumDetail) = buildJsonObject {
        put("id", detail.id)
        detail.name?.let { put("name", it) }
        detail.description?.let { put("description", it) }
        put("authors", detail.authors.toJsonArray())
        detail.imageCount?.let { put("imageCount", it) }
        detail.totalViews?.let { put("totalViews", it) }
        detail.likes?.let { put("likes", it) }
        detail.commentTotal?.let { put("commentTotal", it) }
        put("tags", detail.tags.toJsonArray())
        put("actors", detail.actors.toJsonArray())
        put("works", detail.works.toJsonArray())
        detail.seriesId?.let { put("seriesId", it) }
        detail.price?.let { put("price", it) }
        put("series", JsonArray(detail.series.map { chapter ->
            buildJsonObject {
                put("id", chapter.id)
                chapter.name?.let { put("name", it) }
                chapter.sort?.let { put("sort", it) }
            }
        }))
    }

    private fun decodeDetail(item: JsonObject) = AlbumDetail(
        id = item.requiredString("id"), name = item.nullableString("name"), description = item.nullableString("description"),
        authors = item.strings("authors"), imageCount = item.nullableInt("imageCount"), totalViews = item.nullableInt("totalViews"),
        likes = item.nullableInt("likes"), commentTotal = item.nullableInt("commentTotal"), tags = item.strings("tags"),
        actors = item.strings("actors"), works = item.strings("works"), isFavorite = null, liked = null, related = emptyList(),
        series = item.requiredArray("series").objects().map {
            AlbumChapter(it.requiredString("id").also(OfflineFiles::requireId), it.nullableString("name"), it.nullableString("sort"))
        }, seriesId = item.nullableString("seriesId"), price = item.nullableInt("price"), purchased = null, raw = emptyMap(),
    )

    private fun JsonArray.objects() = (0 until size).map(::requiredObject)
    private fun JsonObject.nullableString(key: String): String? = if (get(key) == null || get(key) == JsonNull) null else requiredString(key)
    private fun JsonObject.nullableInt(key: String): Int? = if (get(key) == null || get(key) == JsonNull) null else requiredInt(key)
    private fun JsonObject.strings(key: String): List<String> = requiredArray(key).let { array ->
        (0 until array.size).map(array::requiredString)
    }
}
