package com.ouroboros.idea.metadata

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.net.JarURLConnection
import java.util.Comparator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

data class MetadataEditorAssetUris(
    val htmlTemplate: String,
    val runtimeScriptUri: String,
    val hostRuntimeScriptUri: String,
    val amisSdkScriptUri: String,
    val amisSdkCssUri: String,
    val amisThemeCssUri: String,
    val amisDarkThemeCssUri: String,
    val amisEditorRuntimeScriptUri: String? = null,
    val amisEditorCssUri: String? = null
)

object MetadataEditorAssets {
    private const val DEV_ASSETS_FALLBACK_PROPERTY = "ouroboros.metadata.editor.devAssets"
    private const val RESOURCE_ROOT = "ouroboros-metadata-editor"
    private const val PAGE_TEMP_ROOT = "ouroboros-metadata-editor-pages"
    private const val RUNTIME_RESOURCE = "$RESOURCE_ROOT/runtime/metadata-editor-runtime.js"
    private const val AMIS_EDITOR_RUNTIME_RESOURCE = "$RESOURCE_ROOT/runtime/metadata-editor-amis-editor.js"
    private const val AMIS_EDITOR_CSS_RESOURCE = "$RESOURCE_ROOT/runtime/metadata-editor-amis-editor.css"
    private const val WEBVIEW_TEMPLATE_RESOURCE = "$RESOURCE_ROOT/runtime/metadata-editor-webview.html"

    private val packagedAssetRoot: Path? by lazy { findPackagedAssetRoot() }

    fun locate(project: Project, file: VirtualFile): MetadataEditorAssetUris {
        return locateWithRoots(
            packagedRoot = packagedAssetRoot,
            devAssetsEnabled = java.lang.Boolean.getBoolean(DEV_ASSETS_FALLBACK_PROPERTY),
            repoHint = project.basePath ?: file.path
        )
    }

    internal fun materializePage(html: String, tempRoot: Path = defaultPageTempRoot()): Path {
        Files.createDirectories(tempRoot)
        val page = Files.createTempFile(tempRoot, "metadata-editor-", ".html")
        return try {
            Files.writeString(page, html, Charsets.UTF_8)
            page.toFile().deleteOnExit()
            page
        } catch (e: Exception) {
            Files.deleteIfExists(page)
            throw e
        }
    }

    internal fun locateWithRoots(
        packagedRoot: Path?,
        devAssetsEnabled: Boolean,
        repoHint: String
    ): MetadataEditorAssetUris {
        packagedRoot?.let { assetRoot -> return assetUrisFromRoot(assetRoot) }

        if (!devAssetsEnabled) {
            error("Missing packaged metadata editor asset: $RUNTIME_RESOURCE")
        }
        val repoRoot = findRepoRoot(repoHint)
        return MetadataEditorAssetUris(
            htmlTemplate = readAssetText(
                WEBVIEW_TEMPLATE_RESOURCE,
                repoRoot.resolve("common/metadata-editor-runtime/runtime/metadata-editor-webview.html")
            ),
            runtimeScriptUri = assetUri(
                RUNTIME_RESOURCE,
                repoRoot.resolve("common/metadata-editor-runtime/runtime/metadata-editor-runtime.js")
            ),
            hostRuntimeScriptUri = assetUri(
                "$RESOURCE_ROOT/runtime/metadata-editor-host.js",
                repoRoot.resolve("common/metadata-editor-runtime/dist/browser/metadata-editor-host.js")
            ),
            amisEditorRuntimeScriptUri = assetUri(
                AMIS_EDITOR_RUNTIME_RESOURCE,
                repoRoot.resolve("common/metadata-editor-runtime/runtime/metadata-editor-amis-editor.js")
            ),
            amisEditorCssUri = assetUri(
                AMIS_EDITOR_CSS_RESOURCE,
                repoRoot.resolve("common/metadata-editor-runtime/runtime/metadata-editor-amis-editor.css")
            ),
            amisSdkScriptUri = assetUri(
                "ouroboros-metadata-editor/amis-sdk/sdk.js",
                repoRoot.resolve("common/amis-sdk/sdk.js")
            ),
            amisSdkCssUri = assetUri(
                "ouroboros-metadata-editor/amis-sdk/sdk.css",
                repoRoot.resolve("common/amis-sdk/sdk.css")
            ),
            amisThemeCssUri = assetUri(
                "ouroboros-metadata-editor/amis-sdk/cxd.css",
                repoRoot.resolve("common/amis-sdk/cxd.css")
            ),
            amisDarkThemeCssUri = assetUri(
                "ouroboros-metadata-editor/amis-sdk/dark.css",
                repoRoot.resolve("common/amis-sdk/dark.css")
            )
        )
    }

    private fun assetUrisFromRoot(assetRoot: Path): MetadataEditorAssetUris {
        return MetadataEditorAssetUris(
            htmlTemplate = readAssetText(
                WEBVIEW_TEMPLATE_RESOURCE,
                assetRoot.resolve("runtime/metadata-editor-webview.html")
            ),
            runtimeScriptUri = fileUri(assetRoot.resolve("runtime/metadata-editor-runtime.js")),
            hostRuntimeScriptUri = fileUri(assetRoot.resolve("runtime/metadata-editor-host.js")),
            amisEditorRuntimeScriptUri = fileUri(assetRoot.resolve("runtime/metadata-editor-amis-editor.js")),
            amisEditorCssUri = fileUri(assetRoot.resolve("runtime/metadata-editor-amis-editor.css")),
            amisSdkScriptUri = fileUri(assetRoot.resolve("amis-sdk/sdk.js")),
            amisSdkCssUri = fileUri(assetRoot.resolve("amis-sdk/sdk.css")),
            amisThemeCssUri = fileUri(assetRoot.resolve("amis-sdk/cxd.css")),
            amisDarkThemeCssUri = fileUri(assetRoot.resolve("amis-sdk/dark.css"))
        )
    }

    private fun fileUri(path: Path): String {
        if (!Files.isRegularFile(path)) {
            error("Missing packaged metadata editor asset: $path")
        }
        return path.toUri().toString()
    }

    private fun readAssetText(resourcePath: String, path: Path): String {
        if (!Files.isRegularFile(path)) {
            error("Missing packaged metadata editor asset: $resourcePath at $path")
        }
        return Files.readString(path, Charsets.UTF_8)
    }

    private fun findPackagedAssetRoot(): Path? {
        val resource = MetadataEditorAssets::class.java.classLoader.getResource(RUNTIME_RESOURCE) ?: return null
        return when (resource.protocol) {
            "file" -> Paths.get(resource.toURI()).parent.parent
            "jar" -> extractJarAssets(resource.openConnection() as JarURLConnection)
            else -> error("Unsupported metadata editor asset protocol: ${resource.protocol}")
        }
    }

    private fun extractJarAssets(connection: JarURLConnection): Path {
        connection.useCaches = false
        val rootPrefix = connection.entryName.removeSuffix("runtime/metadata-editor-runtime.js")
        val tempRoot = Paths.get(PathManager.getTempPath()).resolve("ouroboros-metadata-editor-assets")
        Files.createDirectories(tempRoot)
        val outputRoot = Files.createTempDirectory(tempRoot, "assets-")
        try {
            connection.jarFile.use { jarFile ->
                val entries = jarFile.entries()
                var copied = false
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.startsWith(rootPrefix)) {
                        continue
                    }
                    val relativeName = entry.name.removePrefix(rootPrefix)
                    val target = outputRoot.resolve(relativeName)
                    Files.createDirectories(target.parent)
                    jarFile.getInputStream(entry).use { input ->
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                    copied = true
                }
                if (!copied) {
                    error("No packaged metadata editor assets found under $rootPrefix")
                }
            }
            registerDeleteOnExit(outputRoot)
            return outputRoot
        } catch (e: Exception) {
            deleteRecursively(outputRoot)
            throw e
        }
    }

    private fun registerDeleteOnExit(root: Path) {
        val paths = deletionRegistrationOrder(root)
        try {
            paths.forEach { path -> path.toFile().deleteOnExit() }
        } finally {
            paths.close()
        }
    }

    internal fun deletionRegistrationOrder(root: Path): java.util.stream.Stream<Path> {
        return Files.walk(root).sorted()
    }

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) {
            return
        }
        val paths = Files.walk(root)
        try {
            paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) }
        } finally {
            paths.close()
        }
    }

    private fun assetUri(resourcePath: String, workspacePath: Path): String {
        if (!Files.isRegularFile(workspacePath)) {
            error("Missing development metadata editor asset: $resourcePath at $workspacePath")
        }
        return workspacePath.toUri().toString()
    }

    private fun defaultPageTempRoot(): Path = Paths.get(PathManager.getTempPath()).resolve(PAGE_TEMP_ROOT)

    private fun findRepoRoot(start: String): Path {
        var current = Paths.get(start).toAbsolutePath()
        if (!Files.isDirectory(current)) {
            current = current.parent ?: current
        }
        while (current.parent != null) {
            if (Files.exists(current.resolve("common/metadata-editor-runtime/runtime/metadata-editor-runtime.js"))) {
                return current
            }
            current = current.parent
        }
        return Paths.get(start).toAbsolutePath()
    }
}
