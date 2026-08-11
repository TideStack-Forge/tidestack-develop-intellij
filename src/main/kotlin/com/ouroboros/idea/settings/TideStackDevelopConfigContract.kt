package com.ouroboros.idea.settings

import com.google.gson.Gson
import com.google.gson.JsonObject

internal data class TideStackDevelopConfigSectionContract(
    val path: String = "",
    val fields: List<String> = emptyList(),
    val legacyAliases: Map<String, List<String>> = emptyMap(),
    val removedFields: List<String> = emptyList()
)

internal data class TideStackDevelopConfigContract(
    val schemaVersion: Int = 1,
    val project: TideStackDevelopConfigSectionContract = TideStackDevelopConfigSectionContract(),
    val local: TideStackDevelopConfigSectionContract = TideStackDevelopConfigSectionContract()
)

internal object TideStackDevelopConfigContracts {
    private const val RESOURCE_PATH = "ouroboros-ide-contracts/tidestack-develop-config.json"
    private val gson = Gson()

    private val contract: TideStackDevelopConfigContract by lazy {
        val stream = TideStackDevelopConfigContracts::class.java.classLoader.getResourceAsStream(RESOURCE_PATH)
            ?: error("Missing TideStack develop config contract: $RESOURCE_PATH")
        stream.reader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, TideStackDevelopConfigContract::class.java)
                ?: error("Empty TideStack develop config contract: $RESOURCE_PATH")
        }.also { loaded ->
            check(loaded.project.path.isNotBlank() && loaded.local.path.isNotBlank()) {
                "Invalid TideStack develop config contract: $RESOURCE_PATH"
            }
        }
    }

    val schemaVersion: Int
        get() = contract.schemaVersion

    val projectPath: String
        get() = contract.project.path

    val localPath: String
        get() = contract.local.path

    val projectFields: List<String>
        get() = contract.project.fields

    val localFields: List<String>
        get() = contract.local.fields

    fun readProjectValue(document: JsonObject, field: String): String? {
        return readValue(document, contract.project, field)
    }

    fun readLocalValue(document: JsonObject, field: String): String? {
        return readValue(document, contract.local, field)
    }

    fun projectConfig(
        document: JsonObject,
        legacy: TideStackDevelopProjectConfig = TideStackDevelopProjectConfig()
    ): TideStackDevelopProjectConfig {
        return TideStackDevelopProjectConfig(
            schemaVersion = readSchemaVersion(document),
            developmentServerUrl = readProjectValue(document, "developmentServerUrl") ?: legacy.developmentServerUrl,
            appName = readProjectValue(document, "appName") ?: legacy.appName
        )
    }

    fun localConfig(
        document: JsonObject,
        legacy: TideStackDevelopLocalConfig = TideStackDevelopLocalConfig()
    ): TideStackDevelopLocalConfig {
        return TideStackDevelopLocalConfig(
            schemaVersion = readSchemaVersion(document),
            devKey = readLocalValue(document, "devKey") ?: legacy.devKey
        )
    }

    fun projectDocument(config: TideStackDevelopProjectConfig): JsonObject {
        return projectDocument(JsonObject(), config)
    }

    fun projectDocument(existing: JsonObject, config: TideStackDevelopProjectConfig): JsonObject {
        return orderedDocument(
            contract.project,
            existing,
            mapOf(
                "schemaVersion" to config.schemaVersion,
                "developmentServerUrl" to config.developmentServerUrl,
                "appName" to config.appName
            )
        )
    }

    fun localDocument(config: TideStackDevelopLocalConfig): JsonObject {
        return localDocument(JsonObject(), config)
    }

    fun localDocument(existing: JsonObject, config: TideStackDevelopLocalConfig): JsonObject {
        return orderedDocument(
            contract.local,
            existing,
            mapOf(
                "schemaVersion" to config.schemaVersion,
                "devKey" to config.devKey
            )
        )
    }

    fun effectiveConfig(
        projectConfig: TideStackDevelopProjectConfig,
        localConfig: TideStackDevelopLocalConfig
    ): TideStackDevelopConfig {
        return TideStackDevelopConfig(
            developmentServerUrl = projectConfig.developmentServerUrl,
            appName = projectConfig.appName,
            devKey = localConfig.devKey
        )
    }

    private fun readValue(
        document: JsonObject,
        section: TideStackDevelopConfigSectionContract,
        field: String
    ): String? {
        return sequenceOf(field)
            .plus(section.legacyAliases[field].orEmpty().asSequence())
            .mapNotNull { name -> document.get(name)?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }

    private fun orderedDocument(
        section: TideStackDevelopConfigSectionContract,
        existing: JsonObject,
        values: Map<String, Any>
    ): JsonObject {
        val knownNames = section.fields + section.legacyAliases.values.flatten() + section.removedFields
        return JsonObject().apply {
            section.fields.forEach { field ->
                values[field]?.let { value -> add(field, gson.toJsonTree(value)) }
            }
            existing.entrySet()
                .filterNot { (name, _) -> name in knownNames }
                .forEach { (name, value) -> add(name, value.deepCopy()) }
        }
    }

    private fun readSchemaVersion(document: JsonObject): Int {
        val value = document.get("schemaVersion")
        return if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            value.asInt
        } else {
            contract.schemaVersion
        }
    }
}
