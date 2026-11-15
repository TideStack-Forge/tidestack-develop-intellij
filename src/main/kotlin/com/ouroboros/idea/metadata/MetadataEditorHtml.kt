package com.ouroboros.idea.metadata

object MetadataEditorHtml {
    fun render(
        assets: MetadataEditorAssetUris,
        uri: String,
        schemaName: String,
        initialDocumentJson: String,
        initialTheme: String,
        locale: String,
        saveScript: String,
        readyScript: String
    ): String {
        val hostScript = """
            const logicalHost = window.OuroborosMetadataEditorHostCore.createBrowserMetadataEditorHost({
              uri: ${escapeScriptString(uri)},
              metadataType: ${escapeScriptString(schemaName)},
              locale: ${escapeScriptString(locale)},
              initialDocument: ${escapeScriptJson(initialDocumentJson)},
              saveText: (text, revision) => new Promise((resolve, reject) => { $saveScript })
            });
            window.OuroborosMetadataEditorHost = {
              initialize: () => logicalHost.loadState(),
              theme: ${escapeScriptString(initialTheme)},
              locale: ${escapeScriptString(locale)},
              amisEditorDragFallback: true,
              transport: {
                saveText: (text, revision) => logicalHost.saveText(text, revision),
                validate: text => logicalHost.validate(text),
                loadState: () => logicalHost.loadState()
              },
              connect: () => { $readyScript },
              applyExternalDocument: document => logicalHost.applyExternalDocument(document).then(state => {
                const app = window.OuroborosMetadataEditorApp;
                if (app) {
                  app.applyState(state);
                }
                return state;
              })
            };
        """.trimIndent()
        val replacements = mapOf(
            "{{CSP_META}}" to "",
            "{{NONCE_ATTRIBUTE}}" to "",
            "{{AMIS_SDK_SCRIPT_URI}}" to escapeAttr(assets.amisSdkScriptUri),
            "{{AMIS_SDK_CSS_URI}}" to escapeAttr(assets.amisSdkCssUri),
            "{{AMIS_THEME_CSS_URI}}" to escapeAttr(assets.amisThemeCssUri),
            "{{AMIS_DARK_THEME_CSS_URI}}" to escapeAttr(assets.amisDarkThemeCssUri),
            "{{AMIS_EDITOR_CSS_LINK}}" to assets.amisEditorCssUri
                ?.let { "<link rel=\"stylesheet\" href=\"${escapeAttr(it)}\" />" }
                .orEmpty(),
            "{{EDITOR_BUNDLE_CSS_LINKS}}" to assets.editorBundleStyleUris.joinToString("\n") {
                "<link rel=\"stylesheet\" href=\"${escapeAttr(it)}\" />"
            },
            "{{RUNTIME_SCRIPT_URI}}" to escapeAttr(assets.runtimeScriptUri),
            "{{AMIS_EDITOR_RUNTIME_SCRIPT_TAG}}" to if (schemaName == "ui-schema") {
                assets.amisEditorRuntimeScriptUri
                    ?.let { "<script src=\"${escapeAttr(it)}\"></script>" }
                    .orEmpty()
            } else {
                ""
            },
            "{{REACT_GLOBALS_SCRIPT_TAG}}" to reactGlobalsScriptTag(),
            "{{EDITOR_INTEGRATION_SCRIPT_TAG}}" to assets.editorIntegrationScriptUri
                ?.let { "<script src=\"${escapeAttr(it)}\"></script>" }
                .orEmpty(),
            "{{EDITOR_BUNDLE_SCRIPT_TAGS}}" to assets.editorBundleScriptUris.joinToString("\n") {
                "<script src=\"${escapeAttr(it)}\"></script>"
            },
            "{{XLSX_RUNTIME_SCRIPT_TAG}}" to "<script src=\"${escapeAttr(assets.xlsxRuntimeScriptUri)}\"></script>",
            "{{HOST_RUNTIME_SCRIPT_TAG}}" to "<script src=\"${escapeAttr(assets.hostRuntimeScriptUri)}\"></script>",
            "{{HOST_SCRIPT}}" to hostScript
        )
        val html = replacements.entries.fold(assets.htmlTemplate) { current, entry ->
            current.replace(entry.key, entry.value)
        }
        check(!Regex("\\{\\{[A-Z_]+}}").containsMatchIn(html)) {
            "Metadata editor webview template contains unresolved placeholders"
        }
        return html
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun escapeAttr(value: String): String = escape(value)

    private fun reactGlobalsScriptTag(): String = """
        <script>
          (function () {
            if (!window.amisRequire) return;
            try { window.React = window.React || window.amisRequire('react'); } catch (_error) { console.debug('Metadata editor React shim fallback failed for react.', _error); }
            try { window.ReactDOM = window.ReactDOM || window.amisRequire('react-dom/client'); } catch (_error) { console.debug('Metadata editor React shim fallback failed for react-dom/client.', _error); }
            try { window.ReactDOM = window.ReactDOM || window.amisRequire('react-dom'); } catch (_error) { console.debug('Metadata editor React shim fallback failed for react-dom.', _error); }
          })();
        </script>
    """.trimIndent()

    private fun escapeScriptJson(value: String): String = value.replace("</", "<\\/")

    private fun escapeScriptString(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("</", "<\\/") + "\""
}
