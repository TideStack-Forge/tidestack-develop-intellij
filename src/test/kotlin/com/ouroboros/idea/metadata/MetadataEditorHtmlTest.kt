package com.ouroboros.idea.metadata

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataEditorHtmlTest {
    @Test
    fun `renders the metadata editor shell`() {
        val html = MetadataEditorHtml.render(
            assets = MetadataEditorAssetUris(
                htmlTemplate = MetadataEditorHtmlTest::class.java.classLoader
                    .getResource("ouroboros-metadata-editor/runtime/metadata-editor-webview.html")!!
                    .readText(),
                runtimeScriptUri = "file:///runtime/metadata-editor-runtime.js",
                hostRuntimeScriptUri = "file:///runtime/metadata-editor-host.js",
                amisSdkScriptUri = "file:///amis/sdk.js",
                amisSdkCssUri = "file:///amis/sdk.css",
                amisThemeCssUri = "file:///amis/cxd.css",
                amisDarkThemeCssUri = "file:///amis/dark.css"
            ),
            uri = "file:///main.menu.json",
            schemaName = "menu",
            initialDocumentJson = "{\"text\":\"{}\",\"revision\":\"7\"}",
            initialTheme = "dark",
            locale = "en-US",
            saveScript = "resolve({revision: '8'});",
            readyScript = "window.__ready = true;"
        )

        assertTrue(html.contains("id=\"metadata-amis-root\""))
        assertTrue(html.contains("width: 100%; height: 100%;"))
        assertTrue(html.contains("id=\"metadata-amis-theme-cxd\""))
        assertTrue(html.contains("id=\"metadata-amis-theme-dark\""))
        assertTrue(html.contains("theme: \"dark\""))
        assertTrue(html.contains("locale: \"en-US\""))
    }
}
