package io.kazusa.jmcomic.core.api

import io.kazusa.jmcomic.core.crypto.AesEcbPkcs7
import io.kazusa.jmcomic.core.crypto.JmxHash
import io.kazusa.jmcomic.core.json.WireJson
import io.kazusa.jmcomic.core.json.parseWireElement
import io.kazusa.jmcomic.core.network.ApiEndpointManager
import io.kazusa.jmcomic.core.network.DefaultRetryPolicy
import io.kazusa.jmcomic.core.network.JmxApiClient
import io.kazusa.jmcomic.core.network.JmxHttpClient
import io.kazusa.jmcomic.core.protocol.ApiClock
import io.kazusa.jmcomic.core.protocol.ApiTokenProvider
import io.kazusa.jmcomic.core.protocol.JmxMagicConstants
import io.kazusa.jmcomic.core.protocol.JmxProtocolConstants
import io.kazusa.jmcomic.core.result.JmxResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoveryApiTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun missingOrMalformedListsNeverBecomeSuccessfulEmptyPages() {
        listOf(
            "{}",
            "null",
            "false",
            """{"content":{}}""",
            "[false]",
            """[{"name":"x"}]""",
            """[{"id":true}]""",
            """[{"id":"han-1"}]""",
        ).forEach {
            assertTrue(it, parse(it).discoveryPage() is JmxResult.Failure)
        }
    }

    @Test
    fun explicitEmptyArraysAreAccepted() {
        listOf("[]", """{"content":[],"total":0}""").forEach {
            val result = parse(it).discoveryPage()
            assertTrue(result is JmxResult.Success)
            assertTrue((result as JmxResult.Success).value.content.isEmpty())
        }
    }

    @Test
    fun pageCountIsNotTotalAndBadTotalsAreRejected() {
        val result = parse("""{"count":1,"content":[{"id":"123","name":"x"}]}""").discoveryPage() as JmxResult.Success
        assertNull(result.value.total)
        for (total in listOf("-1", "0", "1.5", "true", "{}", "\"unknown\"")) {
            assertTrue(
                total,
                parse("""{"total":$total,"content":[{"id":"123"}]}""").discoveryPage() is JmxResult.Failure,
            )
        }
    }

    @Test
    fun weekMetadataRequiresExplicitArraysAndValidIds() {
        listOf(
            "{}",
            """{"category":[]}""",
            """{"category":{},"type":[]}""",
            """{"category":[{"id":true}],"type":[]}""",
            """{"category":[],"type":[{}]}""",
        ).forEach {
            val root = parse(it).asObjectOrNull()
            val result = root?.discoveryWeekInfo()
            assertTrue(it, result == null || result is JmxResult.Failure)
        }
    }

    @Test
    fun weekMetadataPreservesServerOrderDeduplicatesIdsAndAcceptsExplicitEmpty() {
        val data = parse(
            """{"category":[{"id":"edition 23","title":"最新"},{"id":"22"},{"id":"22"}],"type":[{"id":2,"title":"分类二"},{"id":"1"}]}"""
        ).asObjectOrNull()!!.discoveryWeekInfo() as JmxResult.Success
        assertEquals(listOf("edition 23", "22"), data.value.categories.map { it.id })
        assertEquals(listOf("2", "1"), data.value.types.map { it.id })
        val empty = parse("""{"categories":[],"category":[{"id":"9"}],"type":[]}""")
            .asObjectOrNull()!!.discoveryWeekInfo() as JmxResult.Success
        assertTrue(empty.value.categories.isEmpty())
        assertTrue(empty.value.types.isEmpty())
    }

    @Test
    fun weekFilterUsesExactServerIdsAndOneBasedPageWithoutWriting() = runBlocking {
        val api = LibraryApi(createClient())
        server.enqueue(encryptedResponse("""{"content":[{"id":"123"}],"total":1}"""))
        assertTrue(
            api.weekFilter(0, "edition 23", "2", forceRefresh = true, strictList = true) is JmxResult.Success
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/week/filter", request.requestUrl!!.encodedPath)
        assertEquals("edition 23", request.requestUrl!!.queryParameter("id"))
        assertEquals("2", request.requestUrl!!.queryParameter("type"))
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
    }

    @Test
    fun missingWeekFilterSelectionFailsBeforeNetwork() = runBlocking {
        val api = LibraryApi(createClient())
        assertTrue(api.weekFilter(1, "", "2") is JmxResult.Failure)
        assertTrue(api.weekFilter(1, "23", " ") is JmxResult.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun allFourRankPeriodsAndCanonicalCategoriesUseTheOfficialFilterContract() = runBlocking {
        val api = LibraryApi(createClient())
        val categories = listOf(
            "0", "doujin", "single", "short", "hanman", "meiman", "another", "doujin_cosplay", "3D", "english_site",
        )
        for ((time, expected) in listOf("t" to "mv_t", "w" to "mv_w", "m" to "mv_m", "a" to "mv")) {
            for (category in categories) {
                server.enqueue(encryptedResponse("""{"content":[{"id":"123"}],"total":1}"""))
                assertTrue(
                    api.categoriesFilter(
                        CategoryFilter(1, time, category, order = JmxMagicConstants.ORDER_BY_VIEW),
                        forceRefresh = true,
                        strictList = true,
                    ) is JmxResult.Success
                )
                val request = server.takeRequest()
                assertEquals("GET", request.method)
                assertEquals("/categories/filter", request.requestUrl!!.encodedPath)
                assertEquals(category, request.requestUrl!!.queryParameter("c"))
                assertEquals("", request.requestUrl!!.queryParameter("order"))
                assertEquals(expected, request.requestUrl!!.queryParameter("o"))
            }
        }
        assertEquals(40, server.requestCount)
    }

    private fun parse(json: String) = WireJson.parseWireElement(json)

    private fun createClient(): JmxApiClient {
        val tokenProvider = ApiTokenProvider(
            clock = object : ApiClock {
                override fun nowSeconds(): Long = TS
            },
            versionProvider = { JmxProtocolConstants.DefaultApiVersion }
        )
        return JmxApiClient(
            JmxHttpClient(
                endpointManager = ApiEndpointManager(listOf(server.url("/").toString())),
                tokenProvider = tokenProvider,
                retryPolicy = DefaultRetryPolicy(maxAttempts = 1)
            )
        )
    }

    private fun encryptedResponse(dataJson: String): MockResponse {
        val encrypted = AesEcbPkcs7.encryptStringToBase64(
            dataJson,
            JmxHash.md5Hex("$TS${JmxProtocolConstants.DataSecret}")
        )
        return MockResponse().setResponseCode(200).setBody("""{"code":200,"data":"$encrypted"}""")
    }

    private companion object {
        const val TS = 1700566805L
    }
}
