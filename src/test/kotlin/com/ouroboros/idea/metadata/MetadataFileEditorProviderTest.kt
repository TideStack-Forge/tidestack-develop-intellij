package com.ouroboros.idea.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataFileEditorProviderTest {
    @Test
    fun `registers one IDEA editor provider per metadata type`() {
        val authority = AuthorityMetadataFileEditorProvider()
        val menu = MenuMetadataFileEditorProvider()
        val devMenu = DevMenuMetadataFileEditorProvider()
        val uiModel = UiModelMetadataFileEditorProvider()
        val uiSchema = UiSchemaMetadataFileEditorProvider()
        val business = BusinessMetadataFileEditorProvider()
        val logicflow = LogicflowMetadataFileEditorProvider()
        val approvalWorkflow = ApprovalWorkflowMetadataFileEditorProvider()
        val fieldType = FieldTypeMetadataFileEditorProvider()
        val flowNode = FlowNodeMetadataFileEditorProvider()
        val widget = WidgetMetadataFileEditorProvider()
        val microWeb = MicroWebMetadataFileEditorProvider()
        val rpc = RpcMetadataFileEditorProvider()
        val rpcAuth = RpcAuthMetadataFileEditorProvider()
        val webapi = WebapiMetadataFileEditorProvider()
        val notification = NotificationMetadataFileEditorProvider()
        val businessLog = BusinessLogMetadataFileEditorProvider()
        val dataInit = DataInitMetadataFileEditorProvider()
        val mq = MqMetadataFileEditorProvider()
        val schedule = ScheduleMetadataFileEditorProvider()
        val i18n = I18nMetadataFileEditorProvider()
        val dataDict = DataDictMetadataFileEditorProvider()
        val position = PositionMetadataFileEditorProvider()
        val errorCode = ErrorCodeMetadataFileEditorProvider()
        val appMetadata = AppMetadataFileEditorProvider()
        val modulesDiff = ModulesDiffMetadataFileEditorProvider()
        val appModule = AppModuleMetadataFileEditorProvider()
        val configuration = ConfigurationMetadataFileEditorProvider()
        val configurationGroup = ConfigurationGroupMetadataFileEditorProvider()
        val configurationItem = ConfigurationItemMetadataFileEditorProvider()
        val configurationGroupItem = ConfigurationGroupItemMetadataFileEditorProvider()

        assertEquals("ouroboros.metadata.editor.authority", authority.editorTypeId)
        assertEquals("ouroboros.metadata.editor.menu", menu.editorTypeId)
        assertEquals("ouroboros.metadata.editor.devMenu", devMenu.editorTypeId)
        assertEquals("ouroboros.metadata.editor.uiModel", uiModel.editorTypeId)
        assertEquals("ouroboros.metadata.editor.uiSchema", uiSchema.editorTypeId)
        assertEquals("ouroboros.metadata.editor.business", business.editorTypeId)
        assertEquals("ouroboros.metadata.editor.logicflow", logicflow.editorTypeId)
        assertEquals("ouroboros.metadata.editor.approvalWorkflow", approvalWorkflow.editorTypeId)
        assertEquals("ouroboros.metadata.editor.fieldType", fieldType.editorTypeId)
        assertEquals("ouroboros.metadata.editor.flowNode", flowNode.editorTypeId)
        assertEquals("ouroboros.metadata.editor.widget", widget.editorTypeId)
        assertEquals("ouroboros.metadata.editor.microWeb", microWeb.editorTypeId)
        assertEquals("ouroboros.metadata.editor.rpc", rpc.editorTypeId)
        assertEquals("ouroboros.metadata.editor.rpcAuth", rpcAuth.editorTypeId)
        assertEquals("ouroboros.metadata.editor.webapi", webapi.editorTypeId)
        assertEquals("ouroboros.metadata.editor.notification", notification.editorTypeId)
        assertEquals("ouroboros.metadata.editor.businessLog", businessLog.editorTypeId)
        assertEquals("ouroboros.metadata.editor.dataInit", dataInit.editorTypeId)
        assertEquals("ouroboros.metadata.editor.mq", mq.editorTypeId)
        assertEquals("ouroboros.metadata.editor.schedule", schedule.editorTypeId)
        assertEquals("ouroboros.metadata.editor.i18n", i18n.editorTypeId)
        assertEquals("ouroboros.metadata.editor.dataDict", dataDict.editorTypeId)
        assertEquals("ouroboros.metadata.editor.position", position.editorTypeId)
        assertEquals("ouroboros.metadata.editor.errorCode", errorCode.editorTypeId)
        assertEquals("ouroboros.metadata.editor.appMetadata", appMetadata.editorTypeId)
        assertEquals("ouroboros.metadata.editor.modulesDiff", modulesDiff.editorTypeId)
        assertEquals("ouroboros.metadata.editor.appModule", appModule.editorTypeId)
        assertEquals("ouroboros.metadata.editor.configuration", configuration.editorTypeId)
        assertEquals("ouroboros.metadata.editor.configurationGroup", configurationGroup.editorTypeId)
        assertEquals("ouroboros.metadata.editor.configurationItem", configurationItem.editorTypeId)
        assertEquals("ouroboros.metadata.editor.configurationGroupItem", configurationGroupItem.editorTypeId)
        assertEquals(
            listOf(
                "authority",
                "menu",
                "dev-menu",
                "ui-model",
                "ui-schema",
                "business",
                "logicflow",
                "approval-workflow",
                "field-type",
                "flow-node",
                "widget",
                "micro-web",
                "rpc",
                "rpc-auth",
                "webapi",
                "notification",
                "business-log",
                "data-init",
                "mq",
                "schedule",
                "i18n",
                "data-dict",
                "position",
                "error-code",
                "app-metadata",
                "modules-diff",
                "app-module",
                "configuration",
                "configuration-group",
                "configuration-item",
                "configuration-group-item"
            ),
            MetadataEditorRegistry.all.map { it.metadataType }
        )
        assertEquals(
            listOf("*.ui-schema.json", "**/resources/META-INF/ouroboros/ui-schema/**/*.json"),
            MetadataEditorRegistry.byMetadataType("ui-schema")?.fileMatchPatterns
        )
    }

    @Test
    fun `each provider only accepts its own source metadata suffix`() {
        val authority = AuthorityMetadataFileEditorProvider()
        val menu = MenuMetadataFileEditorProvider()
        val devMenu = DevMenuMetadataFileEditorProvider()

        assertTrue(authority.acceptsPath("/repo/src/main/metadata/authority/user.authority.json"))
        assertFalse(authority.acceptsPath("/repo/src/main/metadata/menu-model/main.menu.json"))

        assertTrue(menu.acceptsPath("/repo/src/main/metadata/menu-model/main.menu.json"))
        assertFalse(menu.acceptsPath("/repo/src/main/metadata/dev-menu-model/all.dev-menu.json"))

        assertTrue(devMenu.acceptsPath("/repo/src/main/metadata/dev-menu-model/all.dev-menu.json"))
        assertFalse(devMenu.acceptsPath("/repo/src/main/metadata/authority/user.authority.json"))
    }

    @Test
    fun `each provider only accepts its own legacy packaged metadata path`() {
        val authority = AuthorityMetadataFileEditorProvider()
        val menu = MenuMetadataFileEditorProvider()
        val devMenu = DevMenuMetadataFileEditorProvider()

        assertTrue(authority.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/authority/User.json"))
        assertFalse(authority.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/menu-model/Main.json"))

        assertTrue(menu.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/menu-model/Main.json"))
        assertFalse(menu.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/dev-menu-model/AllModels.Resource.json"))

        assertTrue(devMenu.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/dev-menu-model/AllModels.Resource.json"))
        assertFalse(devMenu.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/authority/User.json"))
    }

    @Test
    fun `new providers accept only their own suffix and legacy paths`() {
        val uiModel = UiModelMetadataFileEditorProvider()
        val uiSchema = UiSchemaMetadataFileEditorProvider()
        val business = BusinessMetadataFileEditorProvider()
        val logicflow = LogicflowMetadataFileEditorProvider()
        val approvalWorkflow = ApprovalWorkflowMetadataFileEditorProvider()
        val appModule = AppModuleMetadataFileEditorProvider()
        val configuration = ConfigurationMetadataFileEditorProvider()
        val configurationGroup = ConfigurationGroupMetadataFileEditorProvider()
        val fieldType = FieldTypeMetadataFileEditorProvider()
        val flowNode = FlowNodeMetadataFileEditorProvider()
        val widget = WidgetMetadataFileEditorProvider()
        val microWeb = MicroWebMetadataFileEditorProvider()
        val rpc = RpcMetadataFileEditorProvider()
        val rpcAuth = RpcAuthMetadataFileEditorProvider()
        val webapi = WebapiMetadataFileEditorProvider()
        val notification = NotificationMetadataFileEditorProvider()
        val businessLog = BusinessLogMetadataFileEditorProvider()
        val dataInit = DataInitMetadataFileEditorProvider()
        val mq = MqMetadataFileEditorProvider()
        val schedule = ScheduleMetadataFileEditorProvider()
        val i18n = I18nMetadataFileEditorProvider()
        val dataDict = DataDictMetadataFileEditorProvider()
        val position = PositionMetadataFileEditorProvider()
        val errorCode = ErrorCodeMetadataFileEditorProvider()
        val appMetadata = AppMetadataFileEditorProvider()
        val modulesDiff = ModulesDiffMetadataFileEditorProvider()
        val configurationItem = ConfigurationItemMetadataFileEditorProvider()
        val configurationGroupItem = ConfigurationGroupItemMetadataFileEditorProvider()

        assertTrue(uiModel.acceptsPath("/repo/src/main/metadata/ui-model/home.ui-model.json"))
        assertTrue(uiModel.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/ui-model/home.json"))
        assertFalse(uiModel.acceptsPath("/repo/src/main/metadata/ui-schema/home.ui-schema.json"))

        assertTrue(uiSchema.acceptsPath("/repo/src/main/metadata/ui-schema/home.ui-schema.json"))
        assertTrue(uiSchema.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/ui-schema/home.json"))
        assertFalse(uiSchema.acceptsPath("/repo/src/main/metadata/ui-model/home.ui-model.json"))

        assertTrue(business.acceptsPath("/repo/src/main/metadata/business-model/order.business.json"))
        assertTrue(business.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/business-model/order.business.json"))
        assertFalse(business.acceptsPath("/repo/src/main/metadata/ui-schema/home.ui-schema.json"))

        assertTrue(logicflow.acceptsPath("/repo/src/main/metadata/logic/order.logicflow.json"))
        assertTrue(logicflow.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/logic/order.logicflow.json"))
        assertFalse(logicflow.acceptsPath("/repo/src/main/metadata/workflow/purchase.workflow.json"))

        assertTrue(approvalWorkflow.acceptsPath("/repo/src/main/metadata/workflow/purchase.workflow.json"))
        assertTrue(approvalWorkflow.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/workflow/purchase.workflow.json"))
        assertFalse(approvalWorkflow.acceptsPath("/repo/src/main/metadata/logic/order.logicflow.json"))

        assertTrue(fieldType.acceptsPath("/repo/src/main/metadata/fields/text.field-type.json"))
        assertTrue(fieldType.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/fields/text.field-type.json"))
        assertFalse(fieldType.acceptsPath("/repo/src/main/metadata/logic/start.flow-node.json"))

        assertTrue(flowNode.acceptsPath("/repo/src/main/metadata/logic/start.flow-node.json"))
        assertTrue(flowNode.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/logic/start.flow-node.json"))
        assertFalse(flowNode.acceptsPath("/repo/src/main/metadata/widgets/summary.widget.json"))

        assertTrue(widget.acceptsPath("/repo/src/main/metadata/widgets/summary.widget.json"))
        assertTrue(widget.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/widgets/summary.widget.json"))
        assertFalse(widget.acceptsPath("/repo/src/main/metadata/web/admin.micro-web.json"))

        assertTrue(microWeb.acceptsPath("/repo/src/main/metadata/web/admin.micro-web.json"))
        assertTrue(microWeb.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/web/admin.micro-web.json"))
        assertFalse(microWeb.acceptsPath("/repo/src/main/metadata/fields/text.field-type.json"))

        assertTrue(rpc.acceptsPath("/repo/src/main/metadata/rpc/open-api.rpc.json"))
        assertTrue(rpc.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/rpc-model/open-api.rpc.json"))
        assertFalse(rpc.acceptsPath("/repo/src/main/metadata/rpc/auth/shared.rpc.auth.json"))

        assertTrue(rpcAuth.acceptsPath("/repo/src/main/metadata/rpc/auth/shared.rpc.auth.json"))
        assertTrue(rpcAuth.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/rpc-model/auth/shared.rpc.auth.json"))
        assertFalse(rpcAuth.acceptsPath("/repo/src/main/metadata/rpc/open-api.rpc.json"))

        assertTrue(webapi.acceptsPath("/repo/src/main/metadata/webapi/orders.webapi.json"))
        assertTrue(webapi.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/webapi/orders.webapi.json"))
        assertFalse(webapi.acceptsPath("/repo/src/main/metadata/rpc/open-api.rpc.json"))

        assertTrue(notification.acceptsPath("/repo/src/main/metadata/notifications/welcome.notification.json"))
        assertTrue(notification.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/notifications/welcome.notification.json"))
        assertFalse(notification.acceptsPath("/repo/src/main/metadata/audit/create-order.business-log.json"))

        assertTrue(businessLog.acceptsPath("/repo/src/main/metadata/audit/create-order.business-log.json"))
        assertTrue(businessLog.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/audit/create-order.business-log.json"))
        assertFalse(businessLog.acceptsPath("/repo/src/main/metadata/seed/status.data-init.json"))

        assertTrue(dataInit.acceptsPath("/repo/src/main/metadata/seed/status.data-init.json"))
        assertTrue(dataInit.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/seed/status.data-init.json"))
        assertFalse(dataInit.acceptsPath("/repo/src/main/metadata/notifications/welcome.notification.json"))

        assertTrue(mq.acceptsPath("/repo/src/main/metadata/mq/orders-created.mq.json"))
        assertTrue(mq.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/mq/orders-created.mq.json"))
        assertFalse(mq.acceptsPath("/repo/src/main/metadata/schedule/daily-sync.schedule.json"))

        assertTrue(schedule.acceptsPath("/repo/src/main/metadata/schedule/daily-sync.schedule.json"))
        assertTrue(schedule.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/schedule/daily-sync.schedule.json"))
        assertFalse(schedule.acceptsPath("/repo/src/main/metadata/i18n/zh-cn.i18n.json"))

        assertTrue(i18n.acceptsPath("/repo/src/main/metadata/i18n/zh-cn.i18n.json"))
        assertTrue(i18n.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/i18n/zh-cn.i18n.json"))
        assertFalse(i18n.acceptsPath("/repo/src/main/metadata/data-dict/customer-status.dict.json"))

        assertTrue(dataDict.acceptsPath("/repo/src/main/metadata/data-dict/customer-status.dict.json"))
        assertTrue(dataDict.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/data-dict/customer-status.dict.json"))
        assertFalse(dataDict.acceptsPath("/repo/src/main/metadata/mq/orders-created.mq.json"))

        assertTrue(position.acceptsPath("/repo/src/main/metadata/organization/member.position.json"))
        assertTrue(position.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/organization/member.position.json"))
        assertFalse(position.acceptsPath("/repo/src/main/metadata/data-dict/customer-status.dict.json"))

        assertTrue(errorCode.acceptsPath("/repo/src/main/metadata/error-codes.error.json"))
        assertTrue(errorCode.acceptsPath("/repo/custom/error-codes.error.json"))
        assertTrue(errorCode.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/error-codes.error.json"))
        assertFalse(errorCode.acceptsPath("/repo/src/main/metadata/admin.error.json"))

        assertTrue(appMetadata.acceptsPath("/repo/src/main/metadata/app.metadata.json"))
        assertTrue(appMetadata.acceptsPath("/repo/custom/app.metadata.json"))
        assertTrue(appMetadata.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/app.metadata.json"))
        assertTrue(appMetadata.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/app.metadata.json"))
        assertFalse(appMetadata.acceptsPath("/repo/src/main/metadata/app-modules.json"))

        assertTrue(modulesDiff.acceptsPath("/repo/src/main/metadata/modules.diff.json"))
        assertTrue(modulesDiff.acceptsPath("/repo/custom/modules.diff.json"))
        assertTrue(modulesDiff.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/metadata/modules.diff.json"))
        assertTrue(modulesDiff.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/modules.diff.json"))
        assertFalse(modulesDiff.acceptsPath("/repo/src/main/metadata/app.metadata.json"))

        assertTrue(appModule.acceptsPath("/repo/src/main/metadata/app-modules.json"))
        assertTrue(appModule.acceptsPath("/repo/custom/app-modules.json"))
        assertTrue(appModule.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/app-modules.json"))
        assertFalse(appModule.acceptsPath("/repo/src/main/metadata/app-module/home.app-module.json"))
        assertFalse(appModule.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration.json"))

        assertTrue(configuration.acceptsPath("/repo/src/main/metadata/configurations.json"))
        assertTrue(configuration.acceptsPath("/repo/src/main/metadata/configuration.json"))
        assertTrue(configuration.acceptsPath("/repo/custom/configurations.json"))
        assertFalse(configuration.acceptsPath("/repo/custom/configuration.json"))
        assertTrue(configuration.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configurations.json"))
        assertTrue(configuration.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration.json"))
        assertFalse(configuration.acceptsPath("/repo/src/main/metadata/configuration/home.configuration.json"))
        assertFalse(configuration.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration-groups.json"))

        assertTrue(configurationGroup.acceptsPath("/repo/src/main/metadata/configuration-groups.json"))
        assertTrue(configurationGroup.acceptsPath("/repo/custom/configuration-groups.json"))
        assertTrue(configurationGroup.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration-groups.json"))
        assertFalse(configurationGroup.acceptsPath("/repo/src/main/metadata/configuration-group/home.configuration-group.json"))
        assertFalse(configurationGroup.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configurations.json"))
        assertFalse(configurationGroup.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration.json"))

        assertTrue(configurationItem.acceptsPath("/repo/configuration/key0.config.json"))
        assertTrue(configurationItem.acceptsPath("/repo/src/main/metadata/configuration/key0.config.json"))
        assertFalse(configurationItem.acceptsPath("/repo/src/main/metadata/configurations.json"))
        assertFalse(configurationItem.acceptsPath("/repo/configuration-group/security-session.config-group.json"))

        assertTrue(configurationGroupItem.acceptsPath("/repo/configuration-group/security-session.config-group.json"))
        assertTrue(configurationGroupItem.acceptsPath("/repo/src/main/metadata/configuration-group/security-session.config-group.json"))
        assertFalse(configurationGroupItem.acceptsPath("/repo/src/main/metadata/configuration-groups.json"))
        assertFalse(configurationGroupItem.acceptsPath("/repo/configuration/key0.config.json"))
    }
}
