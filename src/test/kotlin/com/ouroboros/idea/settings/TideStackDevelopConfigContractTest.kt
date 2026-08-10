package com.ouroboros.idea.settings

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TideStackDevelopConfigContractTest {
    @Test
    fun `provides shared paths and legacy field aliases`() {
        val imported = JsonParser.parseString(
            """{"platformUrl":"http://localhost:88","debugKey":"local-key"}"""
        ).asJsonObject

        assertEquals(".tidestack/develop.json", TideStackDevelopConfigContracts.projectPath)
        assertEquals(".tidestack/develop.local.json", TideStackDevelopConfigContracts.localPath)
        assertEquals(
            "http://localhost:88",
            TideStackDevelopConfigContracts.readProjectValue(imported, "developmentServerUrl")
        )
        assertEquals("local-key", TideStackDevelopConfigContracts.readLocalValue(imported, "devKey"))
        assertEquals(
            "http://localhost:88",
            TideStackDevelopConfigContracts.projectConfig(imported).developmentServerUrl
        )
        assertEquals("local-key", TideStackDevelopConfigContracts.localConfig(imported).devKey)
        assertEquals(
            listOf("schemaVersion", "developmentServerUrl", "appName"),
            TideStackDevelopConfigContracts.projectDocument(
                TideStackDevelopProjectConfig(developmentServerUrl = "http://localhost:88", appName = "demo")
            ).keySet().toList()
        )
    }

    @Test
    fun `preserves unknown project fields while canonicalizing known fields`() {
        val existing = JsonParser.parseString(
            """{"platformUrl":"http://legacy","appName":"old","future":{"enabled":true}}"""
        ).asJsonObject

        val document = TideStackDevelopConfigContracts.projectDocument(
            existing,
            TideStackDevelopProjectConfig(developmentServerUrl = "http://current", appName = "demo")
        )

        assertEquals(
            listOf("schemaVersion", "developmentServerUrl", "appName", "future"),
            document.keySet().toList()
        )
        assertEquals("http://current", document.get("developmentServerUrl").asString)
        assertEquals(true, document.getAsJsonObject("future").get("enabled").asBoolean)
        assertEquals(false, document.has("platformUrl"))
    }

    @Test
    fun `preserves unknown local fields while canonicalizing legacy dev key`() {
        val existing = JsonParser.parseString(
            """{"debugKey":"legacy","profileId":"old-profile","containerId":"old-container","future":"keep"}"""
        ).asJsonObject

        val document = TideStackDevelopConfigContracts.localDocument(
            existing,
            TideStackDevelopLocalConfig(devKey = "current")
        )

        assertEquals(
            listOf("schemaVersion", "devKey", "future"),
            document.keySet().toList()
        )
        assertEquals("current", document.get("devKey").asString)
        assertEquals("keep", document.get("future").asString)
        assertEquals(false, document.has("debugKey"))
        assertEquals(false, document.has("profileId"))
        assertEquals(false, document.has("containerId"))
    }
}
