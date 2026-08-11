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

data class MetadataFileCreationFieldModel(
    val name: String,
    val label: String,
    val labelRemark: String? = null,
    val required: Boolean = false,
    val defaultValue: String = "",
    val prompt: String
)

data class MetadataFileCreationFlowModel(
    val metadataType: String,
    val displayName: String,
    val creationDirectory: String,
    val defaultPath: String,
    val defaultPathInput: String,
    val pathInputLocked: Boolean,
    val pathPrompt: String,
    val fields: List<MetadataFileCreationFieldModel>
)

data class MetadataFileCreationPlan(
    val relativePath: String,
    val templateValues: Map<String, String>,
    val text: String
)

object MetadataFileTemplateService {
    private val contract: MetadataCreationContract get() = MetadataCreationContracts.current
    private val sourceRoot: String get() = contract.sourceRoot.trim('/')
    private val pathRules: MetadataCreationPathRules get() = contract.pathRules
    private val templateDefaultRules: MetadataCreationTemplateDefaultRules get() = contract.templateDefaultRules
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

    fun creationFlowModel(
        selectedDirectoryPath: String?,
        contribution: MetadataContributionDefinition
    ): MetadataFileCreationFlowModel {
        val creationDirectory = creationDirectoryPath(selectedDirectoryPath, contribution)
        val defaultPath = defaultInputPath(creationDirectory, contribution)
        return MetadataFileCreationFlowModel(
            metadataType = contribution.type,
            displayName = contribution.displayName,
            creationDirectory = creationDirectory,
            defaultPath = defaultPath,
            defaultPathInput = inputForPath(defaultPath, creationDirectory),
            pathInputLocked = contribution.fixedFileName != null,
            pathPrompt = TideStackMessages.message(
                "metadata.new.filePath",
                mapOf("displayName" to contribution.displayName)
            ),
            fields = fieldModels(contribution, defaultPath)
        )
    }

    fun fieldModels(
        contribution: MetadataContributionDefinition,
        targetPath: String
    ): List<MetadataFileCreationFieldModel> {
        val template = contribution.fileTemplate ?: return emptyList()
        val defaults = defaultTemplateFieldValues(contribution, targetPath)
        return template.fields.map { field ->
            MetadataFileCreationFieldModel(
                name = field.name,
                label = field.label,
                labelRemark = field.labelRemark,
                required = field.required,
                defaultValue = defaults[field.name].orEmpty(),
                prompt = fieldPrompt(contribution, field)
            )
        }
    }

    fun normalizeInputPath(
        input: String,
        contribution: MetadataContributionDefinition,
        creationModel: MetadataFileCreationFlowModel
    ): MetadataFileTemplatePath {
        return normalizeInputPath(input, contribution, creationModel.creationDirectory)
    }

    fun validateField(field: MetadataFileCreationFieldModel, value: String): String? {
        if (field.required && value.trim().isEmpty()) {
            return TideStackMessages.message("metadata.error.fieldRequired", mapOf("label" to field.label))
        }
        return null
    }

    fun creationPlan(
        contribution: MetadataContributionDefinition,
        pathInput: String,
        creationModel: MetadataFileCreationFlowModel,
        values: Map<String, String> = emptyMap()
    ): Result<MetadataFileCreationPlan> {
        val normalizedPath = normalizeInputPath(pathInput, contribution, creationModel)
        val relativePath = normalizedPath.path
            ?: return Result.failure(IllegalArgumentException(normalizedPath.error ?: TideStackMessages.message("metadata.error.enterPath")))

        val fields = fieldModels(contribution, relativePath)
        val templateValues = linkedMapOf<String, String>()
        fields.forEach { field ->
            val value = values[field.name] ?: field.defaultValue
            validateField(field, value)?.let { error -> return Result.failure(IllegalArgumentException(error)) }
            templateValues[field.name] = value.trim()
        }

        return Result.success(
            MetadataFileCreationPlan(
                relativePath = relativePath,
                templateValues = templateValues,
                text = templateText(contribution, templateValues)
            )
        )
    }

    fun defaultInputPath(selectedDirectoryPath: String, contribution: MetadataContributionDefinition): String {
        val defaultFileName = contribution.fileTemplate?.defaultFileName
            ?: contribution.fixedFileName
            ?: pathRules.fallbackFileName
        return "${creationDirectoryPath(selectedDirectoryPath, contribution)}/$defaultFileName"
    }

    fun inputForPath(targetPath: String, creationDirectoryPath: String): String {
        val normalizedTargetPath = normalizePath(targetPath).trim('/')
        val normalizedCreationDirectory = normalizePath(creationDirectoryPath).trim('/')
        val creationPrefix = "$normalizedCreationDirectory/"
        return if (normalizedTargetPath.startsWith(creationPrefix)) {
            normalizedTargetPath.removePrefix(creationPrefix)
        } else {
            normalizedTargetPath
        }
    }

    fun creationDirectoryPath(
        selectedDirectoryPath: String?,
        contribution: MetadataContributionDefinition
    ): String {
        val sourceRootDirectory = metadataSourceRootForDirectory(selectedDirectoryPath)
        val scope = metadataDirectoryScope(selectedDirectoryPath)
        return if (contribution.fixedFileName != null || scope == null) {
            sourceRootDirectory
        } else {
            normalizePath(selectedDirectoryPath.orEmpty()).trim('/')
        }
    }

    fun normalizeInputPath(
        input: String,
        contribution: MetadataContributionDefinition,
        selectedDirectoryPath: String? = null
    ): MetadataFileTemplatePath {
        val normalizedInput = input.trim()
            .replace('\\', '/')
            .removePrefix(pathRules.relativeCurrentDirectoryPrefix)
        val directoryInput = normalizedInput.endsWith('/')
        val trimmed = normalizedInput.trim('/')
        if (trimmed.isEmpty()) {
            return MetadataFileTemplatePath(error = TideStackMessages.message("metadata.error.enterPath"))
        }

        if (contribution.fixedFileName != null) {
            val creationDirectory = creationDirectoryPath(selectedDirectoryPath, contribution)
            val path = if (trimmed.contains('/')) trimmed else "$creationDirectory/$trimmed"
            if (baseName(path) != contribution.fixedFileName || !isFixedMetadataSourceRootFile(path, contribution.fixedFileName)) {
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
        val withSuffix = if (filePath.endsWith(pathRules.jsonExtension)) filePath else "$filePath$fileSuffix"
        if (!withSuffix.endsWith(fileSuffix)) {
            return MetadataFileTemplatePath(
                error = TideStackMessages.message(
                    "metadata.error.fileSuffix",
                    mapOf("displayName" to contribution.displayName, "fileSuffix" to fileSuffix)
                )
            )
        }
        val creationDirectory = creationDirectoryPath(selectedDirectoryPath, contribution)
        return MetadataFileTemplatePath(
            path = if (metadataSourceRootForExistingPath(withSuffix) != null) withSuffix else "$creationDirectory/$withSuffix"
        )
    }

    private fun fieldPrompt(
        contribution: MetadataContributionDefinition,
        field: MetadataFileTemplateFieldDefinition
    ): String {
        val title = TideStackMessages.message(
            "metadata.new.field",
            mapOf("displayName" to contribution.displayName, "label" to field.label)
        )
        return if (field.labelRemark.isNullOrBlank()) title else "$title\n${field.labelRemark}"
    }

    private fun metadataDirectoryScope(path: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }
        if (isMetadataSourceRootDirectory(path)) {
            return "root"
        }
        return if (metadataSourceRootForExistingPath(path) != null) "nested" else null
    }

    private fun metadataSourceRootForDirectory(path: String?): String {
        val existingSourceRoot = path?.let(::metadataSourceRootForExistingPath)
        if (existingSourceRoot != null) {
            return existingSourceRoot
        }
        val sourceRootFromSrcMain = path?.let(::metadataSourceRootFromSrcMainPath)
        return sourceRootFromSrcMain ?: sourceRoot
    }

    private fun isMetadataSourceRootDirectory(path: String): Boolean {
        val normalizedPath = normalizePath(path).trim('/')
        return normalizedPath == sourceRoot || normalizedPath.endsWith("/$sourceRoot")
    }

    private fun metadataSourceRootForExistingPath(path: String): String? {
        val normalizedPath = normalizePath(path).trim('/')
        if (isMetadataSourceRootDirectory(normalizedPath)) {
            return normalizedPath
        }
        if (normalizedPath.startsWith("$sourceRoot/")) {
            return sourceRoot
        }
        val marker = "/$sourceRoot/"
        val markerIndex = normalizedPath.lastIndexOf(marker)
        if (markerIndex >= 0) {
            return normalizedPath.substring(0, markerIndex + marker.length - 1)
        }
        return null
    }

    private fun metadataSourceRootFromSrcMainPath(path: String): String? {
        val segments = normalizePath(path).trim('/').split('/').filter { it.isNotBlank() }
        val markerSegments = pathRules.sourceMainSegments
        if (markerSegments.isEmpty() || segments.size < markerSegments.size) {
            return null
        }
        for (index in segments.size - markerSegments.size downTo 0) {
            if (markerSegments.indices.all { offset -> segments[index + offset] == markerSegments[offset] }) {
                return (segments.take(index) + sourceRoot.split('/')).joinToString("/")
            }
        }
        return null
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
            "title" -> parts.lastOrNull()?.words?.joinToString(" ") ?: templateFallback("title")
            "slash-path" -> "/" + (parts.joinToString("/") { it.kebab }.ifBlank { templateFallback("slash-path") })
            "dot-name" -> parts.joinToString(".") { it.pascal }.ifBlank { templateFallback("dot-name") }
            else -> parts.joinToString(".") { it.camel }.ifBlank { templateFallback("camel-name") }
        }
    }

    private fun templateFallback(format: String): String = templateDefaultRules.fallbacks[format] ?: "example"

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
        var stem = targetPath.trim().replace('\\', '/').removePrefix(pathRules.relativeCurrentDirectoryPrefix)
        val sourceRoot = "${this.sourceRoot}/"
        val sourceRootIndex = stem.lastIndexOf(sourceRoot)
        if (sourceRootIndex >= 0) {
            stem = stem.substring(sourceRootIndex + sourceRoot.length)
        }
        val fileSuffix = contribution.fileSuffix
        return when {
            fileSuffix != null && stem.endsWith(fileSuffix) -> stem.removeSuffix(fileSuffix)
            contribution.fixedFileName != null && stem.endsWith(contribution.fixedFileName) -> templateDefaultRules.fixedFileNameStem
            stem.lowercase().endsWith(pathRules.jsonExtension.lowercase()) -> stem.dropLast(pathRules.jsonExtension.length)
            else -> stem
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

    private fun directoryName(path: String): String {
        val normalizedPath = normalizePath(path).trim('/')
        val index = normalizedPath.lastIndexOf('/')
        return if (index >= 0) normalizedPath.substring(0, index) else ""
    }

    private fun isFixedMetadataSourceRootFile(path: String, fixedFileName: String): Boolean {
        return baseName(path) == fixedFileName && isMetadataSourceRootDirectory(directoryName(path))
    }

    private fun normalizePath(path: String): String = path.replace('\\', '/')
}
