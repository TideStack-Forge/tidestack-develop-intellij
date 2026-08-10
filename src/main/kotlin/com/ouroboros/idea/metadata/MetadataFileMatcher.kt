package com.ouroboros.idea.metadata

data class MetadataFileMatch(
    val type: String,
    val suffix: String?,
    val sourcePath: String,
    val packagedPath: String,
    val diagnostics: List<MetadataSchemaDiagnostic>,
    val contribution: MetadataContributionDefinition
)

object MetadataFileMatcher {
    private const val SOURCE_ROOT = "src/main/metadata/"
    private const val PACKAGED_RESOURCE_ROOT = "resources/META-INF/ouroboros/"
    private const val METADATA_RESOURCE_DIRECTORY = "metadata/"
    private val FIXED_METADATA_FILE_TYPES = mapOf(
        "app-modules.json" to "app-module",
        "configuration.json" to "configuration",
        "configuration-groups.json" to "configuration-group"
    )
    private val FIXED_METADATA_TYPES = FIXED_METADATA_FILE_TYPES.values.toSet()

    fun match(path: String): MetadataFileMatch? {
        val normalizedPath = path.replace('\\', '/')
        return matchPackagedResourcePath(normalizedPath) ?: matchSourcePath(normalizedPath)
    }

    private fun matchSourcePath(normalizedPath: String): MetadataFileMatch? {
        val sourceRootIndex = normalizedPath.lastIndexOf(SOURCE_ROOT)
        val relativePath = if (sourceRootIndex >= 0) {
            normalizedPath.substring(sourceRootIndex + SOURCE_ROOT.length)
        } else {
            normalizedPath.substringAfterLast('/')
        }
        val contribution = contributionByMetadataRelativePath(relativePath) ?: return null

        return MetadataFileMatch(
            type = contribution.type,
            suffix = contribution.fileSuffix,
            sourcePath = normalizedPath,
            packagedPath = "META-INF/ouroboros/metadata/$relativePath",
            diagnostics = emptyList(),
            contribution = contribution
        )
    }

    private fun matchPackagedResourcePath(normalizedPath: String): MetadataFileMatch? {
        val resourceRootIndex = normalizedPath.lastIndexOf(PACKAGED_RESOURCE_ROOT)
        if (resourceRootIndex < 0 || !normalizedPath.endsWith(".json")) {
            return null
        }

        val relativePath = normalizedPath.substring(resourceRootIndex + PACKAGED_RESOURCE_ROOT.length)
        val contribution = if (relativePath.startsWith(METADATA_RESOURCE_DIRECTORY)) {
            contributionByMetadataRelativePath(relativePath.removePrefix(METADATA_RESOURCE_DIRECTORY))
        } else {
            MetadataContributionRegistry.byPackagedPath(relativePath)
        } ?: return null
        val suffixContribution = MetadataContributionRegistry.bySuffix(normalizedPath)
        val diagnostics = if (suffixContribution != null && suffixContribution.type != contribution.type) {
            listOf(
                MetadataSchemaDiagnostic(
                    code = "metadata-path-suffix-conflict",
                    message = "Packaged path ${contribution.packagedPathPrefix} resolves this file as ${contribution.type}, but suffix ${suffixContribution.fileSuffix} resolves it as ${suffixContribution.type}.",
                    severity = MetadataSchemaDiagnosticSeverity.ERROR
                )
            )
        } else {
            emptyList()
        }

        return MetadataFileMatch(
            type = contribution.type,
            suffix = contribution.fileSuffix,
            sourcePath = normalizedPath,
            packagedPath = "META-INF/ouroboros/$relativePath",
            diagnostics = diagnostics,
            contribution = contribution
        )
    }

    private fun contributionByMetadataRelativePath(relativePath: String): MetadataContributionDefinition? {
        val fixedType = FIXED_METADATA_FILE_TYPES[relativePath]
        if (fixedType != null) {
            return MetadataContributionRegistry.byType(fixedType)
        }
        val contribution = MetadataContributionRegistry.bySuffix(relativePath)
        return contribution?.takeUnless { FIXED_METADATA_TYPES.contains(it.type) }
    }
}
