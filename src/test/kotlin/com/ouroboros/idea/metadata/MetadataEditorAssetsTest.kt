package com.ouroboros.idea.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

class MetadataEditorAssetsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `uses packaged assets when they are available`() {
        val packagedRoot = tempDir.resolve("packaged")
        writeRequiredPackagedAssets(packagedRoot)

        val uris = MetadataEditorAssets.locateWithRoots(
            packagedRoot = packagedRoot,
            devAssetsEnabled = false,
            repoHint = tempDir.toString()
        )

        assertTrue(uris.runtimeScriptUri.startsWith(packagedRoot.toUri().toString()))
        assertTrue(uris.amisDarkThemeCssUri.endsWith("/amis-sdk/dark.css"))
    }

    @Test
    fun `rejects development fallback unless the flag is enabled`() {
        writeDevelopmentAssets(tempDir)

        assertThrows(IllegalStateException::class.java) {
            MetadataEditorAssets.locateWithRoots(
                packagedRoot = null,
                devAssetsEnabled = false,
                repoHint = tempDir.toString()
            )
        }
    }

    @Test
    fun `uses development assets only when the fallback flag is enabled`() {
        writeDevelopmentAssets(tempDir)

        val uris = MetadataEditorAssets.locateWithRoots(
            packagedRoot = null,
            devAssetsEnabled = true,
            repoHint = tempDir.toString()
        )

        assertTrue(uris.runtimeScriptUri.endsWith("/common/amis-schema/runtime/metadata-editor-runtime.js"))
        assertTrue(uris.amisSdkScriptUri.endsWith("/common/amis-sdk/sdk.js"))
        assertTrue(uris.amisDarkThemeCssUri.endsWith("/common/amis-sdk/dark.css"))
    }

    @Test
    fun `materializes the editor page as a local file`() {
        val page = MetadataEditorAssets.materializePage("<html>editor</html>", tempDir.resolve("pages"))

        assertTrue(page.toUri().toString().startsWith("file:"))
        assertEquals("<html>editor</html>", Files.readString(page))
    }

    @Test
    fun `registers asset paths parent-first so delete-on-exit removes children first`() {
        val root = tempDir.resolve("asset-root")
        val child = root.resolve("runtime/metadata-editor-runtime.js")
        write(child)

        val ordered = MetadataEditorAssets.deletionRegistrationOrder(root).use { paths -> paths.toList() }

        assertEquals(root, ordered.first())
        assertEquals(child, ordered.last())
    }

    private fun writeRequiredPackagedAssets(root: Path) {
        write(root.resolve("runtime/metadata-editor-runtime.js"))
        write(root.resolve("runtime/metadata-editor-webview.html"))
        write(root.resolve("runtime/metadata-editor-host.js"))
        write(root.resolve("runtime/metadata-editor-amis-editor.js"))
        write(root.resolve("runtime/metadata-editor-amis-editor.css"))
        write(root.resolve("amis-sdk/sdk.js"))
        write(root.resolve("amis-sdk/sdk.css"))
        write(root.resolve("amis-sdk/cxd.css"))
        write(root.resolve("amis-sdk/dark.css"))
    }

    private fun writeDevelopmentAssets(root: Path) {
        write(root.resolve("common/amis-schema/runtime/metadata-editor-runtime.js"))
        write(root.resolve("common/amis-schema/runtime/metadata-editor-webview.html"))
        write(root.resolve("common/amis-schema/runtime/metadata-editor-amis-editor.js"))
        write(root.resolve("common/amis-schema/runtime/metadata-editor-amis-editor.css"))
        write(root.resolve("common/amis-schema/dist/browser/metadata-editor-host.js"))
        write(root.resolve("common/amis-sdk/sdk.js"))
        write(root.resolve("common/amis-sdk/sdk.css"))
        write(root.resolve("common/amis-sdk/cxd.css"))
        write(root.resolve("common/amis-sdk/dark.css"))
    }

    private fun write(path: Path) {
        Files.createDirectories(path.parent)
        Files.writeString(path, "asset")
    }
}
