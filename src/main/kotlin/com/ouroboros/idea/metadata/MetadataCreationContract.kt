package com.ouroboros.idea.metadata

import com.google.gson.Gson

data class MetadataCreationContract(
    val schemaVersion: Int = 1,
    val sourceRoot: String = "src/main/metadata",
    val packagedResourceRoot: String = "resources/META-INF/ouroboros",
    val metadataResourceDirectory: String = "metadata",
    val pathRules: MetadataCreationPathRules = MetadataCreationPathRules(),
    val templateDefaultRules: MetadataCreationTemplateDefaultRules = MetadataCreationTemplateDefaultRules()
) {
    val sourceRootWithSlash: String get() = "${sourceRoot.trim('/')}/"
    val packagedResourceRootWithSlash: String get() = "${packagedResourceRoot.trim('/')}/"
    val metadataResourceDirectoryWithSlash: String get() = "${metadataResourceDirectory.trim('/')}/"
}

data class MetadataCreationPathRules(
    val relativeCurrentDirectoryPrefix: String = "./",
    val jsonExtension: String = ".json",
    val fallbackFileName: String = "metadata.json",
    val sourceMainSegments: List<String> = listOf("src", "main")
)

data class MetadataCreationTemplateDefaultRules(
    val fixedFileNameStem: String = "example",
    val fallbacks: Map<String, String> = mapOf(
        "camel-name" to "example",
        "dot-name" to "Example",
        "slash-path" to "example",
        "title" to "Example"
    )
)

object MetadataCreationContracts {
    private const val RESOURCE_PATH = "ouroboros-metadata-editor/metadataCreationContract.json"

    val current: MetadataCreationContract by lazy {
        val stream = MetadataCreationContracts::class.java.classLoader.getResourceAsStream(RESOURCE_PATH)
            ?: error("Missing metadata creation contract resource: $RESOURCE_PATH")
        stream.reader(Charsets.UTF_8).use { reader ->
            Gson().fromJson(reader, MetadataCreationContract::class.java)
                ?: error("Empty metadata creation contract resource: $RESOURCE_PATH")
        }
    }
}
