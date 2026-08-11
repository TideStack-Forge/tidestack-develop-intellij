package com.ouroboros.idea.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataFileMatcherTest {
    @Test
    fun `maps MVP metadata files to packaged paths`() {
        val match = MetadataFileMatcher.match("/repo/src/main/metadata/main.menu.json")

        assertNotNull(match)
        assertEquals("menu", match!!.type)
        assertEquals("META-INF/ouroboros/metadata/main.menu.json", match.packagedPath)
        assertTrue(match.diagnostics.isEmpty())
    }

    @Test
    fun `maps authority metadata to existing runtime authority directory`() {
        val match = MetadataFileMatcher.match("/repo/src/main/metadata/authority/user.authority.json")

        assertNotNull(match)
        assertEquals("authority", match!!.type)
        assertEquals("META-INF/ouroboros/metadata/authority/user.authority.json", match.packagedPath)
        assertTrue(match.diagnostics.isEmpty())
    }

    @Test
    fun `matches new metadata suffixes without source directory prefix`() {
        val match = MetadataFileMatcher.match("/repo/custom/ui-schema/home.ui-schema.json")

        assertNotNull(match)
        assertEquals("ui-schema", match!!.type)
        assertEquals("META-INF/ouroboros/metadata/home.ui-schema.json", match.packagedPath)
        assertTrue(match.diagnostics.isEmpty())
    }

    @Test
    fun `maps new metadata suffixes to their own runtime directories`() {
        val paths = listOf(
            "/repo/src/main/metadata/model-home.home.ui-model.json",
            "/repo/src/main/metadata/pages/home.ui-schema.json",
            "/repo/src/main/metadata/app-modules.json",
            "/repo/src/main/metadata/configuration.json",
            "/repo/src/main/metadata/configuration-groups.json"
        )

        val matches = paths.map { MetadataFileMatcher.match(it) }

        assertEquals(
            listOf("ui-model", "ui-schema", "app-module", "configuration", "configuration-group"),
            matches.map { it?.type }
        )
        assertEquals(
            listOf(
                "META-INF/ouroboros/metadata/model-home.home.ui-model.json",
                "META-INF/ouroboros/metadata/pages/home.ui-schema.json",
                "META-INF/ouroboros/metadata/app-modules.json",
                "META-INF/ouroboros/metadata/configuration.json",
                "META-INF/ouroboros/metadata/configuration-groups.json"
            ),
            matches.map { it?.packagedPath }
        )
        assertTrue(matches.all { it?.diagnostics?.isEmpty() == true })
    }

    @Test
    fun `resolves new metadata by suffix regardless of directory names`() {
        val match = MetadataFileMatcher.match("/repo/src/main/metadata/dev-menu-model/main.menu.json")

        assertNotNull(match)
        assertEquals("menu", match!!.type)
        assertEquals("META-INF/ouroboros/metadata/dev-menu-model/main.menu.json", match.packagedPath)
        assertTrue(match.diagnostics.isEmpty())
    }

    @Test
    fun `matches new packaged metadata resources by metadata directory`() {
        val menu = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/system/menu.menu.json")
        val appModules = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/app-modules.json")
        val configuration = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/configuration.json")
        val groups = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/configuration-groups.json")

        assertEquals("menu", menu?.type)
        assertEquals("app-module", appModules?.type)
        assertEquals("configuration", configuration?.type)
        assertEquals("configuration-group", groups?.type)
        assertTrue(listOf(menu, appModules, configuration, groups).all { it?.diagnostics?.isEmpty() == true })
    }

    @Test
    fun `matches legacy packaged resources by runtime subpath`() {
        val menu = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/menu-model/systemSetting/resourceManagement.json")
        val authority = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/authority/systemSetting/resourceManagement.json")
        val devMenu = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/dev-menu-model/AllModels.Resource.json")

        assertNotNull(menu)
        assertEquals("menu", menu!!.type)
        assertEquals("META-INF/ouroboros/menu-model/systemSetting/resourceManagement.json", menu.packagedPath)
        assertTrue(menu.diagnostics.isEmpty())

        assertNotNull(authority)
        assertEquals("authority", authority!!.type)
        assertTrue(authority.diagnostics.isEmpty())

        assertNotNull(devMenu)
        assertEquals("dev-menu", devMenu!!.type)
        assertTrue(devMenu.diagnostics.isEmpty())
    }

    @Test
    fun `matches legacy root collection resources exactly`() {
        val appModules = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/app-modules.json")
        val configuration = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/configuration.json")
        val groups = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/configuration-groups.json")

        assertEquals("app-module", appModules?.type)
        assertEquals("configuration", configuration?.type)
        assertEquals("configuration-group", groups?.type)
        assertTrue(listOf(appModules, configuration, groups).all { it?.diagnostics?.isEmpty() == true })
    }

    @Test
    fun `diagnoses a suffix conflict for a legacy root collection resource`() {
        val match = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/configuration-group/configuration-groups.menu.json")

        assertEquals("configuration-group", match?.type)
        assertEquals("metadata-path-suffix-conflict", match?.diagnostics?.single()?.code)
    }

    @Test
    fun `diagnoses legacy packaged resource suffix conflicts`() {
        val match = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/dev-menu-model/main.menu.json")

        assertNotNull(match)
        assertEquals("dev-menu", match!!.type)
        assertEquals("metadata-path-suffix-conflict", match.diagnostics.single().code)
    }
}
