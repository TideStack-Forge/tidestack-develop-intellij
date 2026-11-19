package com.ouroboros.idea.metadata

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Exports the production Kotlin renderer, never a TypeScript imitation of the IDEA host. */
class MetadataBrowserFixtureTest {
    @Test
    fun `export production pages for the shared browser cases`() {
        val repo = Path.of(".").toAbsolutePath().normalize()
        val output = repo.resolve("build/ide-metadata-fixture/intellij")
        Files.createDirectories(output)
        val gson = Gson()
        val corpus = gson.fromJson(
            Files.readString(repo.resolve("testing/common/fixtures/data/ide-metadata-workspace/documents.json")),
            JsonObject::class.java
        )
        val packaged = MetadataEditorAssets.locateWithRoots(
            packagedRoot = Path.of("build/resources/main/ouroboros-metadata-editor").toAbsolutePath(),
            devAssetsEnabled = false,
            repoHint = repo.toString()
        )
        fun served(uri: String): String = "/" + repo.relativize(Path.of(java.net.URI(uri))).toString()
        val assets = packaged.copy(
            runtimeScriptUri = served(packaged.runtimeScriptUri),
            hostRuntimeScriptUri = served(packaged.hostRuntimeScriptUri),
            amisSdkScriptUri = served(packaged.amisSdkScriptUri),
            amisSdkCssUri = served(packaged.amisSdkCssUri),
            amisThemeCssUri = served(packaged.amisThemeCssUri),
            amisDarkThemeCssUri = served(packaged.amisDarkThemeCssUri),
            xlsxRuntimeScriptUri = served(packaged.xlsxRuntimeScriptUri),
            amisEditorRuntimeScriptUri = packaged.amisEditorRuntimeScriptUri?.let(::served),
            amisEditorCssUri = packaged.amisEditorCssUri?.let(::served),
            editorIntegrationScriptUri = packaged.editorIntegrationScriptUri?.let(::served),
            editorBundleScriptUris = packaged.editorBundleScriptUris.map(::served),
            editorBundleStyleUris = packaged.editorBundleStyleUris.map(::served)
        )
        corpus.entrySet().forEach { (name, entry) ->
            val fixture = entry.asJsonObject
            val document = mapOf("text" to "__FIXTURE_TEXT__", "revision" to "__FIXTURE_REVISION__")
            listOf(true, false).forEach { online ->
                val html = MetadataEditorHtml.render(
                    assets = assets,
                    uri = "file:///fixture/src/main/metadata/${fixture.get("fileName").asString}",
                    schemaName = fixture.get("metadataType").asString,
                    initialDocumentJson = gson.toJson(document),
                    initialTheme = "cxd", locale = "zh-CN",
                    saveScript = """
                        window.fixtureSave(text, revision).then(payload => {
                          if (payload.ok) resolve(payload.result);
                          else reject(Object.assign(new Error(payload.error), {code: payload.errorCode}));
                        }, reject);
                    """.trimIndent(),
                    readyScript = "window.fixtureReady();",
                    serviceAppName = if (online) "fixture" else null,
                    serviceScript = "window.fixtureService(request).then(resolve, reject);"
                )
                Files.writeString(output.resolve(if (online) "$name.html" else "$name-offline.html"), html)
            }
        }
        check(Files.size(output.resolve("page.html")) > 0)
    }
}
