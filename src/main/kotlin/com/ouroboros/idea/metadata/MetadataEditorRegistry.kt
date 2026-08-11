package com.ouroboros.idea.metadata

import com.google.gson.Gson

data class MetadataEditorRegistrationDefinition(
    val editorId: String = "",
    val editorName: String = "",
    val editorNameKey: String = "",
    val metadataType: String = "",
    val schemaName: String = "",
    val viewType: String = "",
    val intellijEditorTypeId: String = "",
    val fileMatchPatterns: List<String> = emptyList(),
    val priority: String = "default"
)

object MetadataEditorRegistry {
    private const val RESOURCE_PATH = "ouroboros-metadata-editor/metadataEditorRegistrations.json"

    private val manifest: MetadataEditorRegistrationManifest by lazy {
        val stream = MetadataEditorRegistry::class.java.classLoader.getResourceAsStream(RESOURCE_PATH)
            ?: error("Missing metadata editor registration resource: $RESOURCE_PATH")
        stream.reader(Charsets.UTF_8).use { reader ->
            Gson().fromJson(reader, MetadataEditorRegistrationManifest::class.java)
        }
    }

    val all: List<MetadataEditorRegistrationDefinition>
        get() = manifest.editors

    fun byMetadataType(metadataType: String): MetadataEditorRegistrationDefinition? {
        return all.firstOrNull { it.metadataType == metadataType }
    }

    fun match(path: String): MetadataEditorRegistrationDefinition? {
        val normalizedPath = path.replace('\\', '/')
        val metadataMatch = MetadataFileMatcher.match(normalizedPath) ?: return null
        return byMetadataType(metadataMatch.type)?.takeIf { registration ->
            registration.fileMatchPatterns.any { pattern -> matchesPattern(normalizedPath, pattern) }
        }
    }

    private fun matchesPattern(path: String, pattern: String): Boolean {
        val normalizedPattern = pattern.replace('\\', '/')
        if (!normalizedPattern.contains('/')) {
            return globToRegex(normalizedPattern).matches(path.substringAfterLast('/'))
        }
        return globToRegex(normalizedPattern).matches(path)
    }

    private fun globToRegex(pattern: String): Regex {
        val builder = StringBuilder("^")
        var index = 0
        while (index < pattern.length) {
            val char = pattern[index]
            if (char == '*') {
                val isDoubleStar = index + 1 < pattern.length && pattern[index + 1] == '*'
                if (isDoubleStar) {
                    if (index + 2 < pattern.length && pattern[index + 2] == '/') {
                        builder.append("(?:.*/)?")
                        index += 3
                    } else {
                        builder.append(".*")
                        index += 2
                    }
                } else {
                    builder.append("[^/]*")
                    index += 1
                }
            } else {
                builder.append(Regex.escape(char.toString()))
                index += 1
            }
        }
        builder.append('$')
        return Regex(builder.toString())
    }

    private data class MetadataEditorRegistrationManifest(
        val editors: List<MetadataEditorRegistrationDefinition> = emptyList()
    )
}
