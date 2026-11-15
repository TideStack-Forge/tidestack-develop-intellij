package com.ouroboros.idea.metadata

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class TypedMetadataFileEditorProvider(
    private val metadataType: String
) : FileEditorProvider, DumbAware {
    private val registration: MetadataEditorRegistrationDefinition by lazy {
        MetadataEditorRegistry.byMetadataType(metadataType)
            ?: error("Missing metadata editor registration for type: $metadataType")
    }

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return acceptsPath(file.path)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return MetadataFileEditor(project, file, registration.schemaName)
    }

    override fun getEditorTypeId(): String = registration.intellijEditorTypeId

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR

    internal fun acceptsPath(path: String): Boolean {
        return MetadataEditorRegistry.match(path)?.metadataType == metadataType
    }
}

class AuthorityMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "authority"
)

class MenuMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "menu"
)

class DevMenuMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "dev-menu"
)

class UiModelMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "ui-model"
)

class UiSchemaMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "ui-schema"
)

class BusinessMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "business"
)

class LogicflowMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "logicflow"
)

class ApprovalWorkflowMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "approval-workflow"
)

class FieldTypeMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "field-type"
)

class FlowNodeMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "flow-node"
)

class WidgetMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "widget"
)

class MicroWebMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "micro-web"
)

class RpcMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "rpc"
)

class RpcAuthMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "rpc-auth"
)

class WebapiMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "webapi"
)

class NotificationMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "notification"
)

class BusinessLogMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "business-log"
)

class DataInitMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "data-init"
)

class MqMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "mq"
)

class ScheduleMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "schedule"
)

class I18nMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "i18n"
)

class DataDictMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "data-dict"
)

class PositionMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "position"
)

class ErrorCodeMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "error-code"
)

class AppMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "app-metadata"
)

class ModulesDiffMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "modules-diff"
)

class AppModuleMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "app-module"
)

class ConfigurationMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "configuration"
)

class ConfigurationGroupMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "configuration-group"
)

class ConfigurationItemMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "configuration-item"
)

class ConfigurationGroupItemMetadataFileEditorProvider : TypedMetadataFileEditorProvider(
    metadataType = "configuration-group-item"
)
