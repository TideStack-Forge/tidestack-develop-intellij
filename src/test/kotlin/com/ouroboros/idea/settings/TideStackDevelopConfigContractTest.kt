package com.ouroboros.idea.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TideStackDevelopConfigContractTest {
    private val cases: TideStackDevelopConfigCases by lazy { loadCases() }

    @Test
    fun `matches shared config contract shape`() {
        assertEquals(cases.contract.schemaVersion, TideStackDevelopConfigContracts.schemaVersion)
        assertEquals(cases.contract.projectPath, TideStackDevelopConfigContracts.projectPath)
        assertEquals(cases.contract.localPath, TideStackDevelopConfigContracts.localPath)
        assertEquals(cases.contract.projectFields, TideStackDevelopConfigContracts.projectFields)
        assertEquals(cases.contract.localFields, TideStackDevelopConfigContracts.localFields)
    }

    @Test
    fun `matches shared project config read contract cases`() {
        cases.projectReadCases.forEach { readCase ->
            assertEquals(
                readCase.expected,
                TideStackDevelopConfigContracts.projectConfig(readCase.document, readCase.legacy),
                readCase.name
            )
        }
    }

    @Test
    fun `matches shared local config read contract cases`() {
        cases.localReadCases.forEach { readCase ->
            assertEquals(
                readCase.expected,
                TideStackDevelopConfigContracts.localConfig(readCase.document, readCase.legacy),
                readCase.name
            )
        }
    }

    @Test
    fun `matches shared config document write contract cases`() {
        cases.projectDocumentCases.forEach { documentCase ->
            val document = TideStackDevelopConfigContracts.projectDocument(documentCase.existing, documentCase.config)

            assertEquals(documentCase.expectedKeys, document.keySet().toList(), documentCase.name)
            assertEquals(documentCase.expected, document, documentCase.name)
        }

        cases.localDocumentCases.forEach { documentCase ->
            val document = TideStackDevelopConfigContracts.localDocument(documentCase.existing, documentCase.config)

            assertEquals(documentCase.expectedKeys, document.keySet().toList(), documentCase.name)
            assertEquals(documentCase.expected, document, documentCase.name)
        }
    }

    @Test
    fun `matches shared effective config contract cases`() {
        cases.effectiveCases.forEach { effectiveCase ->
            assertEquals(
                effectiveCase.expected,
                TideStackDevelopConfigContracts.effectiveConfig(effectiveCase.project, effectiveCase.local),
                effectiveCase.name
            )
        }
    }

    private fun loadCases(): TideStackDevelopConfigCases {
        val resourcePath = "ouroboros-ide-contracts/tidestack-develop-config-cases.json"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Missing TideStack develop config cases resource: $resourcePath")
        return stream.reader(Charsets.UTF_8).use { reader ->
            Gson().fromJson(reader, TideStackDevelopConfigCases::class.java)
                ?: error("Empty TideStack develop config cases resource: $resourcePath")
        }
    }

    private data class TideStackDevelopConfigCases(
        val contract: TideStackDevelopConfigShape = TideStackDevelopConfigShape(),
        val projectReadCases: List<ProjectReadCase> = emptyList(),
        val localReadCases: List<LocalReadCase> = emptyList(),
        val projectDocumentCases: List<ProjectDocumentCase> = emptyList(),
        val localDocumentCases: List<LocalDocumentCase> = emptyList(),
        val effectiveCases: List<EffectiveCase> = emptyList()
    )

    private data class TideStackDevelopConfigShape(
        val schemaVersion: Int = 1,
        val projectPath: String = "",
        val localPath: String = "",
        val projectFields: List<String> = emptyList(),
        val localFields: List<String> = emptyList()
    )

    private data class ProjectReadCase(
        val name: String = "",
        val document: JsonObject = JsonObject(),
        val legacy: TideStackDevelopProjectConfig = TideStackDevelopProjectConfig(),
        val expected: TideStackDevelopProjectConfig = TideStackDevelopProjectConfig()
    )

    private data class LocalReadCase(
        val name: String = "",
        val document: JsonObject = JsonObject(),
        val legacy: TideStackDevelopLocalConfig = TideStackDevelopLocalConfig(),
        val expected: TideStackDevelopLocalConfig = TideStackDevelopLocalConfig()
    )

    private data class ProjectDocumentCase(
        val name: String = "",
        val existing: JsonObject = JsonObject(),
        val config: TideStackDevelopProjectConfig = TideStackDevelopProjectConfig(),
        val expectedKeys: List<String> = emptyList(),
        val expected: JsonObject = JsonObject()
    )

    private data class LocalDocumentCase(
        val name: String = "",
        val existing: JsonObject = JsonObject(),
        val config: TideStackDevelopLocalConfig = TideStackDevelopLocalConfig(),
        val expectedKeys: List<String> = emptyList(),
        val expected: JsonObject = JsonObject()
    )

    private data class EffectiveCase(
        val name: String = "",
        val project: TideStackDevelopProjectConfig = TideStackDevelopProjectConfig(),
        val local: TideStackDevelopLocalConfig = TideStackDevelopLocalConfig(),
        val expected: TideStackDevelopConfig = TideStackDevelopConfig("", "", "")
    )
}
