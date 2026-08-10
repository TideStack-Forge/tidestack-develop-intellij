package com.ouroboros.idea.metadata

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.ouroboros.idea.TideStackMessages

abstract class NewTypedMetadataFileAction(
    private val metadataType: String
) : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && hasTargetDirectory(e)
        MetadataContributionRegistry.byType(metadataType)
            ?.let(TideStackMessages::localizeContribution)
            ?.let { contribution ->
                e.presentation.text = contribution.displayName
            }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targetDirectory = targetDirectory(e) ?: return
        val contribution = MetadataContributionRegistry.byType(metadataType)
            ?.let(TideStackMessages::localizeContribution)
            ?: return
        val creationRoot = creationRootDirectory(targetDirectory, contribution)
        val defaultPath = MetadataFileTemplateService.defaultInputPath(creationRoot.path, contribution)
        val dialog = NewMetadataFileDialog(project, contribution, creationRoot, defaultPath)
        if (!dialog.showAndGet()) {
            return
        }

        val result = dialog.result() ?: return
        val relativePath = result.relativePath
        if (creationRoot.findFileByRelativePath(relativePath) != null) {
            Messages.showErrorDialog(
                project,
                TideStackMessages.message("metadata.error.fileExists", mapOf("targetPath" to relativePath)),
                "File Exists"
            )
            return
        }

        val createdFile = createMetadataFile(
            project,
            creationRoot,
            relativePath,
            MetadataFileTemplateService.templateText(contribution, result.templateValues)
        )
        FileEditorManager.getInstance(project).openFile(createdFile, true)
    }

    private fun createMetadataFile(
        project: Project,
        rootDirectory: VirtualFile,
        relativePath: String,
        text: String
    ): VirtualFile {
        var createdFile: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Create TideStack Metadata File", null, Runnable {
            val pathParts = relativePath.split('/').filter { it.isNotBlank() }
            val fileName = pathParts.last()
            val parentDirectory = ensureDirectory(rootDirectory, pathParts.dropLast(1))
            val file = parentDirectory.createChildData(this, fileName)
            file.setBinaryContent(text.toByteArray(Charsets.UTF_8))
            createdFile = file
        })
        return checkNotNull(createdFile) { "TideStack metadata file was not created." }
    }

    private fun ensureDirectory(rootDirectory: VirtualFile, pathParts: List<String>): VirtualFile {
        var current = rootDirectory
        for (pathPart in pathParts) {
            val child = current.findChild(pathPart) ?: current.createChildDirectory(this, pathPart)
            if (!child.isDirectory) {
                error("Cannot create metadata directory because ${child.path} is a file.")
            }
            current = child
        }
        return current
    }

    private fun hasTargetDirectory(e: AnActionEvent): Boolean {
        return e.getData(LangDataKeys.IDE_VIEW) != null || e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    private fun creationRootDirectory(targetDirectory: VirtualFile, contribution: MetadataContributionDefinition): VirtualFile {
        if (contribution.fixedFileName == null) {
            return targetDirectory
        }
        return metadataSourceRootDirectory(targetDirectory) ?: targetDirectory
    }

    private fun metadataSourceRootDirectory(directory: VirtualFile): VirtualFile? {
        var current: VirtualFile? = directory
        while (current != null) {
            val parent = current.parent
            val grandParent = parent?.parent
            if (current.name == "metadata" && parent?.name == "main" && grandParent?.name == "src") {
                return current
            }
            current = parent
        }
        return null
    }

    private fun targetDirectory(e: AnActionEvent): VirtualFile? {
        val ideViewDirectory = e.getData(LangDataKeys.IDE_VIEW)?.getOrChooseDirectory()?.virtualFile
        if (ideViewDirectory != null) {
            return ideViewDirectory
        }
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return if (selectedFile.isDirectory) selectedFile else selectedFile.parent
    }
}

class NewAuthorityMetadataFileAction : NewTypedMetadataFileAction("authority")

class NewMenuMetadataFileAction : NewTypedMetadataFileAction("menu")

class NewDevMenuMetadataFileAction : NewTypedMetadataFileAction("dev-menu")

class NewUiModelMetadataFileAction : NewTypedMetadataFileAction("ui-model")

class NewUiSchemaMetadataFileAction : NewTypedMetadataFileAction("ui-schema")

class NewAppModuleMetadataFileAction : NewTypedMetadataFileAction("app-module")

class NewConfigurationMetadataFileAction : NewTypedMetadataFileAction("configuration")

class NewConfigurationGroupMetadataFileAction : NewTypedMetadataFileAction("configuration-group")
