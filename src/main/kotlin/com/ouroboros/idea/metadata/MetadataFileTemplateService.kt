package com.ouroboros.idea.metadata

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.ouroboros.idea.TideStackMessages

data class MetadataFileTemplatePath(
    val path: String? = null,
    val error: String? = null
)

object MetadataFileTemplateService {
    private const val SOURCE_ROOT = "src/main/metadata"
    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    fun templateText(contribution: MetadataContributionDefinition, values: Map<String, String> = emptyMap()): String {
        val template = contribution.fileTemplate ?: error("Metadata type ${contribution.type} does not declare a file template.")
        return gson.toJson(applyTemplateValues(template.document, template.fields, values)) + "\n"
    }

    fun defaultTemplateFieldValues(contribution: MetadataContributionDefinition, targetPath: String): Map<String, String> {
        val template = contribution.fileTemplate ?: return emptyMap()
        return template.fields.associate { field ->
            val value = field.defaultFromPath?.let { createPathDefaultValue(targetPath, contribution, it) }
                ?: getPathValue(template.document, field.documentPath)?.asString.orEmpty()
            field.name to value
        }
    }

    fun defaultInputPath(selectedDirectoryPath: String, contribution: MetadataContributionDefinition): String {
        val defaultFileName = contribution.fileTemplate?.defaultFileName
            ?: contribution.fixedFileName
            ?: "metadata.json"
        return if (isMetadataSourceDirectory(selectedDirectoryPath)) {
            defaultFileName
        } else {
            "$SOURCE_ROOT/$defaultFileName"
        }
    }

    fun normalizeInputPath(
        input: String,
        contribution: MetadataContributionDefinition,
        selectedDirectoryPath: String? = null
    ): MetadataFileTemplatePath {
        val normalizedInput = input.trim()
            .replace('\\', '/')
            .removePrefix("./")
        val directoryInput = normalizedInput.endsWith('/')
        val selectedMetadataDirectory = selectedDirectoryPath?.let(::isMetadataSourceDirectory) == true
        val trimmed = if (selectedMetadataDirectory) {
            normalizedInput.removePrefix("$SOURCE_ROOT/")
        } else {
            normalizedInput
        }
            .trim('/')
        if (trimmed.isEmpty()) {
            return MetadataFileTemplatePath(error = TideStackMessages.message("metadata.error.enterPath"))
        }

        if (contribution.fixedFileName != null) {
            val path = if (selectedMetadataDirectory || trimmed.contains('/')) trimmed else "$SOURCE_ROOT/$trimmed"
            val expectedPath = if (selectedMetadataDirectory) contribution.fixedFileName else "$SOURCE_ROOT/${contribution.fixedFileName}"
            if (baseName(path) != contribution.fixedFileName || path != expectedPath) {
                return MetadataFileTemplatePath(
                    error = TideStackMessages.message(
                        "metadata.error.fixedFilePath",
                        mapOf(
                            "displayName" to contribution.displayName,
                            "fixedFileName" to contribution.fixedFileName
                        )
                    )
                )
            }
            return MetadataFileTemplatePath(path = path)
        }

        val fileSuffix = contribution.fileSuffix
            ?: return MetadataFileTemplatePath(error = TideStackMessages.message("metadata.error.noTemplate"))
        val defaultFileName = contribution.fileTemplate?.defaultFileName ?: "metadata$fileSuffix"
        val filePath = if (directoryInput) "$trimmed/$defaultFileName" else trimmed
        val withSuffix = if (filePath.endsWith(".json")) filePath else "$filePath$fileSuffix"
        if (!withSuffix.endsWith(fileSuffix)) {
            return MetadataFileTemplatePath(
                error = TideStackMessages.message(
                    "metadata.error.fileSuffix",
                    mapOf("displayName" to contribution.displayName, "fileSuffix" to fileSuffix)
                )
            )
        }
        return MetadataFileTemplatePath(path = if (selectedMetadataDirectory || withSuffix.startsWith("$SOURCE_ROOT/")) withSuffix else "$SOURCE_ROOT/$withSuffix")
    }

    private fun isMetadataSourceDirectory(path: String): Boolean {
        val normalizedPath = path.replace('\\', '/')
        return normalizedPath == SOURCE_ROOT || normalizedPath.endsWith("/$SOURCE_ROOT") || normalizedPath.contains("/$SOURCE_ROOT/")
    }

    private fun applyTemplateValues(
        document: JsonElement,
        fields: List<MetadataFileTemplateFieldDefinition>,
        values: Map<String, String>
    ): JsonElement {
        val nextDocument = document.deepCopy()
        fields.forEach { field ->
            if (values.containsKey(field.name)) {
                setPathValue(nextDocument, field.documentPath, JsonPrimitive(values.getValue(field.name)))
            }
        }
        return nextDocument
    }

    private fun getPathValue(document: JsonElement, path: List<String>): JsonElement? {
        var current: JsonElement = document
        path.forEach { segment ->
            current = when {
                current is JsonArray -> current.asJsonArray.get(segment.toIntOrNull() ?: return null) ?: return null
                current is JsonObject -> current.asJsonObject.get(segment) ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun setPathValue(document: JsonElement, path: List<String>, value: JsonElement) {
        if (path.isEmpty()) {
            return
        }
        var current = document
        path.dropLast(1).forEach { segment ->
            current = when {
                current is JsonArray -> current.asJsonArray.get(segment.toIntOrNull() ?: return) ?: return
                current is JsonObject -> current.asJsonObject.get(segment) ?: return
                else -> return
            }
        }
        val lastSegment = path.last()
        when (current) {
            is JsonArray -> current.asJsonArray.set(lastSegment.toIntOrNull() ?: return, value)
            is JsonObject -> current.asJsonObject.add(lastSegment, value)
        }
    }

    private fun createPathDefaultValue(
        targetPath: String,
        contribution: MetadataContributionDefinition,
        defaultFormat: String
    ): String {
        val parts = createNameParts(targetPath, contribution)
        return when (defaultFormat) {
            "title" -> parts.lastOrNull()?.words?.joinToString(" ") ?: "Example"
            "slash-path" -> "/" + (parts.joinToString("/") { it.kebab }.ifBlank { "example" })
            "dot-name" -> parts.joinToString(".") { it.pascal }.ifBlank { "Example" }
            else -> parts.joinToString(".") { it.camel }.ifBlank { "example" }
        }
    }

    private data class NamePart(
        val words: List<String>,
        val kebab: String,
        val camel: String,
        val pascal: String
    )

    private fun createNameParts(targetPath: String, contribution: MetadataContributionDefinition): List<NamePart> {
        return createNameStem(targetPath, contribution)
            .split('/', '.')
            .map(::wordsFromSegment)
            .filter { it.isNotEmpty() }
            .map { words ->
                val normalizedWords = words.map { it.lowercase() }
                val pascalWords = normalizedWords.map(::capitalize)
                NamePart(
                    words = pascalWords,
                    kebab = normalizedWords.joinToString("-"),
                    camel = normalizedWords.mapIndexed { index, word -> if (index == 0) word else capitalize(word) }.joinToString(""),
                    pascal = pascalWords.joinToString("")
                )
            }
    }

    private fun createNameStem(targetPath: String, contribution: MetadataContributionDefinition): String {
        var stem = targetPath.trim().replace('\\', '/').removePrefix("./")
        val sourceRoot = "$SOURCE_ROOT/"
        val sourceRootIndex = stem.lastIndexOf(sourceRoot)
        if (sourceRootIndex >= 0) {
            stem = stem.substring(sourceRootIndex + sourceRoot.length)
        }
        val fileSuffix = contribution.fileSuffix
        return when {
            fileSuffix != null && stem.endsWith(fileSuffix) -> stem.removeSuffix(fileSuffix)
            contribution.fixedFileName != null && stem.endsWith(contribution.fixedFileName) -> "example"
            else -> stem.removeSuffix(".json")
        }
    }

    private fun wordsFromSegment(segment: String): List<String> {
        return segment
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .split(Regex("[-_\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun capitalize(value: String): String = value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun baseName(path: String): String = path.substringAfterLast('/')
}
