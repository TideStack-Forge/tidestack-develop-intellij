package com.ouroboros.idea.metadata

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.ouroboros.idea.TideStackMessages

abstract class NewTypedMetadataFileAction(
    private val metadataType: String
) : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && hasTargetDirectory(e)
        MetadataContributionRegistry.byType(metadataType)
            ?.let { contribution ->
                e.presentation.text = TideStackMessages.actionText("metadata.type.${contribution.type}.command")
            }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targetDirectory = targetDirectory(e) ?: return
        val contribution = MetadataContributionRegistry.byType(metadataType)
            ?.let(TideStackMessages::localizeContribution)
            ?: return
        val creationRoot = creationRootDirectory(project, targetDirectory)
        val selectedDirectoryPath = selectedDirectoryPath(creationRoot, targetDirectory)
        val creationModel = MetadataFileTemplateService.creationFlowModel(selectedDirectoryPath, contribution)
        val dialog = NewMetadataFileDialog(project, contribution, creationRoot, creationModel)
        if (!dialog.showAndGet()) {
            return
        }

        val result = dialog.result() ?: return
        val creationPlan = MetadataFileTemplateService.creationPlan(
            contribution,
            result.relativePath,
            creationModel,
            result.templateValues
        ).getOrElse { error ->
            Messages.showErrorDialog(project, error.message, TideStackMessages.message("metadata.new.dialogTitle", mapOf("displayName" to contribution.displayName)))
            return
        }
        if (creationRoot.findFileByRelativePath(creationPlan.relativePath) != null) {
            Messages.showErrorDialog(
                project,
                TideStackMessages.message("metadata.error.fileExists", mapOf("targetPath" to creationPlan.relativePath)),
                "File Exists"
            )
            return
        }

        val createdFile = createMetadataFile(
            project,
            creationRoot,
            creationPlan.relativePath,
            creationPlan.text
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

    private fun creationRootDirectory(project: Project, targetDirectory: VirtualFile): VirtualFile {
        return metadataModuleRootDirectory(targetDirectory)
            ?: ProjectFileIndex.getInstance(project).getContentRootForFile(targetDirectory)
            ?: project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?: targetDirectory
    }

    private fun metadataModuleRootDirectory(directory: VirtualFile): VirtualFile? {
        var current: VirtualFile? = directory
        while (current != null) {
            val parent = current.parent
            if (current.name == "main" && parent?.name == "src") {
                return parent.parent
            }
            current = parent
        }
        return null
    }

    private fun selectedDirectoryPath(rootDirectory: VirtualFile, targetDirectory: VirtualFile): String {
        return VfsUtilCore.getRelativePath(targetDirectory, rootDirectory, '/')?.trim('/').orEmpty()
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

class NewBusinessMetadataFileAction : NewTypedMetadataFileAction("business")

class NewLogicflowMetadataFileAction : NewTypedMetadataFileAction("logicflow")

class NewApprovalWorkflowMetadataFileAction : NewTypedMetadataFileAction("approval-workflow")

class NewFieldTypeMetadataFileAction : NewTypedMetadataFileAction("field-type")

class NewFlowNodeMetadataFileAction : NewTypedMetadataFileAction("flow-node")

class NewWidgetMetadataFileAction : NewTypedMetadataFileAction("widget")

class NewMicroWebMetadataFileAction : NewTypedMetadataFileAction("micro-web")

class NewRpcMetadataFileAction : NewTypedMetadataFileAction("rpc")

class NewRpcAuthMetadataFileAction : NewTypedMetadataFileAction("rpc-auth")

class NewWebapiMetadataFileAction : NewTypedMetadataFileAction("webapi")

class NewNotificationMetadataFileAction : NewTypedMetadataFileAction("notification")

class NewBusinessLogMetadataFileAction : NewTypedMetadataFileAction("business-log")

class NewDataInitMetadataFileAction : NewTypedMetadataFileAction("data-init")

class NewMqMetadataFileAction : NewTypedMetadataFileAction("mq")

class NewScheduleMetadataFileAction : NewTypedMetadataFileAction("schedule")

class NewI18nMetadataFileAction : NewTypedMetadataFileAction("i18n")

class NewDataDictMetadataFileAction : NewTypedMetadataFileAction("data-dict")

class NewPositionMetadataFileAction : NewTypedMetadataFileAction("position")

class NewErrorCodeMetadataFileAction : NewTypedMetadataFileAction("error-code")

class NewAppMetadataFileAction : NewTypedMetadataFileAction("app-metadata")

class NewModulesDiffMetadataFileAction : NewTypedMetadataFileAction("modules-diff")

class NewAppModuleMetadataFileAction : NewTypedMetadataFileAction("app-module")

class NewConfigurationMetadataFileAction : NewTypedMetadataFileAction("configuration")

class NewConfigurationGroupMetadataFileAction : NewTypedMetadataFileAction("configuration-group")

class NewConfigurationItemMetadataFileAction : NewTypedMetadataFileAction("configuration-item")

class NewConfigurationGroupItemMetadataFileAction : NewTypedMetadataFileAction("configuration-group-item")
