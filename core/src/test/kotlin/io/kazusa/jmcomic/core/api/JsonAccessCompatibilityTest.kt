package io.kazusa.jmcomic.core.api

import io.kazusa.jmcomic.core.json.JmxJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class JsonAccessCompatibilityTest {
    @Test fun retainsRemoteFieldAliasesAndPrimitiveConversions() {
        val root = JmxJson.parseToJsonElement("""{
            "id":123,"count":"42","numeric":2.9,"invalid":"2.9",
            "signed":"TrUe","empty":null,"alternate":"fallback","images":[1,"a",null,{}]
        }""").jsonObject
        assertEquals("123", root.stringOrNull("id"))
        assertEquals(42, root.intOrNull("count"))
        assertEquals(2, root.intOrNull("numeric"))
        assertNull(root.intOrNull("invalid"))
        assertEquals(true, root.booleanOrNull("signed"))
        assertEquals("fallback", root.stringOrNull("empty", "alternate"))
        assertEquals(listOf("1", "a"), root.stringListOrEmpty("images"))
    }
}
