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
        val appModule = AppModuleMetadataFileEditorProvider()
        val configuration = ConfigurationMetadataFileEditorProvider()
        val configurationGroup = ConfigurationGroupMetadataFileEditorProvider()

        assertEquals("ouroboros.metadata.editor.authority", authority.editorTypeId)
        assertEquals("ouroboros.metadata.editor.menu", menu.editorTypeId)
        assertEquals("ouroboros.metadata.editor.devMenu", devMenu.editorTypeId)
        assertEquals("ouroboros.metadata.editor.uiModel", uiModel.editorTypeId)
        assertEquals("ouroboros.metadata.editor.uiSchema", uiSchema.editorTypeId)
        assertEquals("ouroboros.metadata.editor.appModule", appModule.editorTypeId)
        assertEquals("ouroboros.metadata.editor.configuration", configuration.editorTypeId)
        assertEquals("ouroboros.metadata.editor.configurationGroup", configurationGroup.editorTypeId)
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
        val appModule = AppModuleMetadataFileEditorProvider()
        val configuration = ConfigurationMetadataFileEditorProvider()
        val configurationGroup = ConfigurationGroupMetadataFileEditorProvider()

        assertTrue(uiModel.acceptsPath("/repo/src/main/metadata/ui-model/home.ui-model.json"))
        assertTrue(uiModel.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/ui-model/home.json"))
        assertFalse(uiModel.acceptsPath("/repo/src/main/metadata/ui-schema/home.ui-schema.json"))

        assertTrue(uiSchema.acceptsPath("/repo/src/main/metadata/ui-schema/home.ui-schema.json"))
        assertTrue(uiSchema.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/ui-schema/home.json"))
        assertFalse(uiSchema.acceptsPath("/repo/src/main/metadata/ui-model/home.ui-model.json"))

        assertTrue(appModule.acceptsPath("/repo/src/main/metadata/app-modules.json"))
        assertTrue(appModule.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/app-modules.json"))
        assertFalse(appModule.acceptsPath("/repo/src/main/metadata/app-module/home.app-module.json"))
        assertFalse(appModule.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration.json"))

        assertTrue(configuration.acceptsPath("/repo/src/main/metadata/configuration.json"))
        assertTrue(configuration.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration.json"))
        assertFalse(configuration.acceptsPath("/repo/src/main/metadata/configuration/home.configuration.json"))
        assertFalse(configuration.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration-groups.json"))

        assertTrue(configurationGroup.acceptsPath("/repo/src/main/metadata/configuration-groups.json"))
        assertTrue(configurationGroup.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration-groups.json"))
        assertFalse(configurationGroup.acceptsPath("/repo/src/main/metadata/configuration-group/home.configuration-group.json"))
        assertFalse(configurationGroup.acceptsPath("/repo/src/main/resources/META-INF/ouroboros/configuration.json"))
    }
}
