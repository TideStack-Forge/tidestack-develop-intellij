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
    private val creationContract: MetadataCreationContract get() = MetadataCreationContracts.current
    private val sourceRoot: String get() = creationContract.sourceRootWithSlash
    private val packagedResourceRoot: String get() = creationContract.packagedResourceRootWithSlash
    private val metadataResourceDirectory: String get() = creationContract.metadataResourceDirectoryWithSlash
    private val fixedMetadataFileTypes: Map<String, String>
        get() = MetadataContributionRegistry.all.flatMap { contribution ->
            listOfNotNull(contribution.fixedFileName, *contribution.legacyFixedFileNames.toTypedArray())
                .map { fileName -> fileName to contribution.type }
        }.toMap()
    private val standaloneFixedMetadataFileTypes: Map<String, String>
        get() = MetadataContributionRegistry.all.mapNotNull { contribution ->
            contribution.fixedFileName?.let { fileName -> fileName to contribution.type }
        }.toMap()
    private val fixedMetadataTypes: Set<String>
        get() = fixedMetadataFileTypes.values.toSet()

    fun match(path: String): MetadataFileMatch? {
        val normalizedPath = path.replace('\\', '/')
        return matchPackagedResourcePath(normalizedPath) ?: matchSourcePath(normalizedPath)
    }

    private fun matchSourcePath(normalizedPath: String): MetadataFileMatch? {
        val sourceRootIndex = normalizedPath.lastIndexOf(sourceRoot)
        val hasSourceRoot = sourceRootIndex >= 0
        val relativePath = if (sourceRootIndex >= 0) {
            normalizedPath.substring(sourceRootIndex + sourceRoot.length)
        } else {
            normalizedPath.substringAfterLast('/')
        }
        val contribution = contributionByMetadataRelativePath(relativePath, allowExactLegacyFixedName = hasSourceRoot) ?: return null

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
        val resourceRootIndex = normalizedPath.lastIndexOf(packagedResourceRoot)
        if (resourceRootIndex < 0 || !normalizedPath.endsWith(".json")) {
            return null
        }

        val relativePath = normalizedPath.substring(resourceRootIndex + packagedResourceRoot.length)
        val contribution = if (relativePath.startsWith(metadataResourceDirectory)) {
            contributionByMetadataRelativePath(relativePath.removePrefix(metadataResourceDirectory), allowExactLegacyFixedName = true)
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

    private fun contributionByMetadataRelativePath(
        relativePath: String,
        allowExactLegacyFixedName: Boolean
    ): MetadataContributionDefinition? {
        val fixedType = if (allowExactLegacyFixedName) {
            fixedMetadataFileTypes[relativePath]
        } else {
            standaloneFixedMetadataFileTypes[relativePath]
        }
        if (fixedType != null) {
            return MetadataContributionRegistry.byType(fixedType)
        }
        val standaloneFixedType = standaloneFixedMetadataFileTypes[relativePath.substringAfterLast('/')]
        if (standaloneFixedType != null) {
            return MetadataContributionRegistry.byType(standaloneFixedType)
        }
        val contribution = MetadataContributionRegistry.bySuffix(relativePath)
        return contribution?.takeUnless { fixedMetadataTypes.contains(it.type) }
    }
}
