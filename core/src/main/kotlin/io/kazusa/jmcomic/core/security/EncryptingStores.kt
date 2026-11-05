package io.kazusa.jmcomic.core.security

import io.kazusa.jmcomic.core.cache.KeyValueStore
import io.kazusa.jmcomic.core.download.ChapterDownloadTaskStore
import io.kazusa.jmcomic.core.download.PersistedChapterDownloadTask
import io.kazusa.jmcomic.core.json.JmxJson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptingKeyValueStore(
    private val delegate: KeyValueStore,
    private val cipher: ByteCipher,
    private val textCodec: Base64TextCodec = Base64TextCodec()
) : KeyValueStore {
    override fun getString(key: String): String? {
        val raw = delegate.getString(key) ?: return null
        return runCatching {
            val plain = cipher.decrypt(textCodec.decode(raw))
            String(plain, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            delegate.putString(key, null)
            return
        }
        val encrypted = cipher.encrypt(value.toByteArray(StandardCharsets.UTF_8))
        delegate.putString(key, textCodec.encode(encrypted))
    }
}

class EncryptingChapterDownloadTaskStore(
    private val file: Path,
    private val cipher: ByteCipher,
    private val json: Json = JmxJson
) : ChapterDownloadTaskStore {
    private val lock = Any()

    override fun loadAll(): List<PersistedChapterDownloadTask> = synchronized(lock) {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            val decrypted = cipher.decrypt(Files.readAllBytes(file))
            val json = String(decrypted, StandardCharsets.UTF_8)
            if (json.isBlank()) emptyList()
            else this.json.decodeFromString<List<PersistedChapterDownloadTask>>(json)
        }.getOrDefault(emptyList())
    }

    override fun saveAll(tasks: List<PersistedChapterDownloadTask>) = synchronized(lock) {
        writeEncrypted(json.encodeToString(tasks))
    }

    override fun save(task: PersistedChapterDownloadTask) = synchronized(lock) {
        val all = loadAll().associateBy { it.id }.toMutableMap()
        all[task.id] = task
        writeEncrypted(json.encodeToString(all.values.sortedBy { it.createdAtMillis }))
    }

    override fun delete(taskId: String) = synchronized(lock) {
        writeEncrypted(json.encodeToString(loadAll().filterNot { it.id == taskId }))
    }

    override fun clear() = synchronized(lock) {
        writeEncrypted("[]")
    }

    private fun writeEncrypted(json: String) {
        val encrypted = cipher.encrypt(json.toByteArray(StandardCharsets.UTF_8))
        val parent = file.toAbsolutePath().parent ?: Path.of(".").toAbsolutePath()
        Files.createDirectories(parent)
        val temp = Files.createTempFile(parent, "jmx-enc-tasks-", ".bin")
        try {
            Files.write(temp, encrypted)
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: UnsupportedOperationException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(temp) }
            throw t
        }
    }
}
