package dev.jmx.client.core.json

import dev.jmx.client.core.cache.InMemoryKeyValueStore
import dev.jmx.client.core.download.FileChapterDownloadTaskStore
import dev.jmx.client.core.security.AesGcmByteCipher
import dev.jmx.client.core.security.EncryptingChapterDownloadTaskStore
import dev.jmx.client.core.session.PersistentCookieStore
import java.nio.file.Files
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyJsonCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun readsLegacyCookiesWithoutLosingSessionOrHostScope() {
        val kv = InMemoryKeyValueStore()
        kv.putString("session.cookies", """[{
            "name":"AVS","value":"legacy-session","expiresAt":2000000000000,
            "domain":"api.test","path":"/","secure":true,"httpOnly":true,"hostOnly":true,
            "futureField":"ignored"
        }]""")
        val store = PersistentCookieStore(kv, nowMillis = { 1000L })
        val cookie = store.load("https://api.test/album".toHttpUrl()).single()
        assertEquals("legacy-session", cookie.value)
        assertEquals(2000000000000L, cookie.expiresAt)
        assertTrue(cookie.httpOnly)
        assertTrue(store.load("https://sub.api.test/album".toHttpUrl()).isEmpty())
        assertTrue(store.load("http://api.test/album".toHttpUrl()).isEmpty())
        store.replace(listOf(cookie))
        assertEquals(cookie, PersistentCookieStore(kv, nowMillis = { 1000L }).snapshot().single())
    }

    @Test fun readsLegacyPlainAndEncryptedTasksWithOmittedNullFields() {
        // The old Gson writer omitted null fields; older records also lack the resume filename list.
        val legacy = """[{
            "id":"task-1","albumId":"10","chapterId":"11","shunt":"1",
            "outputDirectory":"/downloads","state":"Pending",
            "createdAtMillis":9007199254740993,"updatedAtMillis":9007199254740994,
            "futureField":true
        }]"""
        val plain = temporary.newFile("tasks.json").toPath()
        Files.writeString(plain, legacy)
        val store = FileChapterDownloadTaskStore(plain)
        val task = store.loadAll().single()
        assertNull(task.maxImages)
        assertNull(task.errorMessage)
        assertTrue(task.completedImageFileNames.isEmpty())
        assertEquals(9007199254740993L, task.createdAtMillis)
        store.saveAll(listOf(task))
        assertEquals(task, FileChapterDownloadTaskStore(plain).loadAll().single())

        val cipher = AesGcmByteCipher(AesGcmByteCipher.randomKey())
        val encrypted = temporary.newFile("tasks.bin").toPath()
        Files.write(encrypted, cipher.encrypt(legacy.toByteArray(Charsets.UTF_8)))
        val encryptedStore = EncryptingChapterDownloadTaskStore(encrypted, cipher)
        assertEquals(task, encryptedStore.loadAll().single())
        encryptedStore.saveAll(listOf(task))
        assertFalse(String(Files.readAllBytes(encrypted), Charsets.ISO_8859_1).contains("task-1"))
        assertEquals(task, EncryptingChapterDownloadTaskStore(encrypted, cipher).loadAll().single())
    }
}
