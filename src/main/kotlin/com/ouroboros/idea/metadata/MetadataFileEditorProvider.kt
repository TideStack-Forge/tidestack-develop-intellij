package com.ouroboros.idea.metadata

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class TypedMetadataFileEditorProvider(
    private val metadataType: String,
    private val editorTypeId: String
) : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return acceptsPath(file.path)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return MetadataFileEditor(project, file, metadataType)
    }

    override fun getEditorTypeId(): String = editorTypeId

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR

    internal fun acceptsPath(path: String): Boolean {
        return MetadataFileMatcher.match(path)?.type == metadataType
    }
}

class AuthorityMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "authority",
    editorTypeId = "ouroboros.metadata.editor.authority"
)

class MenuMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "menu",
    editorTypeId = "ouroboros.metadata.editor.menu"
)

class DevMenuMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "dev-menu",
    editorTypeId = "ouroboros.metadata.editor.devMenu"
)

class UiModelMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "ui-model",
    editorTypeId = "ouroboros.metadata.editor.uiModel"
)

class UiSchemaMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "ui-schema",
    editorTypeId = "ouroboros.metadata.editor.uiSchema"
)

class AppModuleMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "app-module",
    editorTypeId = "ouroboros.metadata.editor.appModule"
)

class ConfigurationMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "configuration",
    editorTypeId = "ouroboros.metadata.editor.configuration"
)

class ConfigurationGroupMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "configuration-group",
    editorTypeId = "ouroboros.metadata.editor.configurationGroup"
)
