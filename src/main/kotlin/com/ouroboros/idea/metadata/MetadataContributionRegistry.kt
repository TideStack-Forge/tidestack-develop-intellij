package com.ouroboros.idea.metadata

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class MetadataContributionDefinition(
    val type: String = "",
    val fileSuffix: String? = null,
    val fixedFileName: String? = null,
    val legacyFixedFileNames: List<String> = emptyList(),
    val displayName: String = "",
    val sourcePathPatterns: List<String> = emptyList(),
    val packagedPathPrefix: String = "",
    val packagedPathPatterns: List<String> = emptyList(),
    val documentShape: String = "object",
    val editorKind: String = "form",
    val editorKinds: List<String> = emptyList(),
    val jsonSchema: JsonObject = JsonObject(),
    val legacyJsonSchema: JsonObject? = null,
    val editorSchema: JsonElement = JsonObject(),
    val fileTemplate: MetadataFileTemplateDefinition? = null
)

data class MetadataFileTemplateDefinition(
    val defaultFileName: String = "",
    val document: JsonElement = JsonObject(),
    val fields: List<MetadataFileTemplateFieldDefinition> = emptyList()
)

data class MetadataFileTemplateFieldDefinition(
    val name: String = "",
    val label: String = "",
    val labelRemark: String? = null,
    val documentPath: List<String> = emptyList(),
    val required: Boolean = false,
    val defaultFromPath: String? = null
)

object MetadataContributionRegistry {
    private const val RESOURCE_PATH = "ouroboros-metadata-editor/metadataTypeContributions.json"

    private val manifest: MetadataContributionManifest by lazy {
        val stream = MetadataContributionRegistry::class.java.classLoader.getResourceAsStream(RESOURCE_PATH)
            ?: error("Missing metadata contribution resource: $RESOURCE_PATH")
        stream.reader(Charsets.UTF_8).use { reader ->
            Gson().fromJson(reader, MetadataContributionManifest::class.java)
        }
    }

    val all: List<MetadataContributionDefinition>
        get() = manifest.types

    val schemas: List<JsonObject>
        get() = manifest.schemas

    fun byType(type: String?): MetadataContributionDefinition? {
        if (type == null) {
            return null
        }
        return all.firstOrNull { it.type == type }
    }

    fun bySuffix(path: String): MetadataContributionDefinition? {
        return all.firstOrNull { suffixContribution ->
            suffixContribution.fileSuffix?.let(path::endsWith) == true
        }
    }

    fun byPackagedPath(relativePath: String): MetadataContributionDefinition? {
        val suffixContribution = bySuffix(relativePath)
        if (suffixContribution != null && suffixContribution.ownsPackagedRelativePath(relativePath)) {
            return suffixContribution
        }
        return all.firstOrNull { contribution ->
            contribution.ownsPackagedRelativePath(relativePath)
        }
    }

    private fun MetadataContributionDefinition.ownsPackagedRelativePath(relativePath: String): Boolean {
        val runtimeDirectory = packagedPathPrefix.removePrefix("META-INF/ouroboros/")
        return packagedPathPatterns.contains(relativePath) || relativePath.startsWith("$runtimeDirectory/")
    }

    private data class MetadataContributionManifest(
        val types: List<MetadataContributionDefinition> = emptyList(),
        val schemas: List<JsonObject> = emptyList()
    )
}
