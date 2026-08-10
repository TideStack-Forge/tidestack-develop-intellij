package com.ouroboros.idea.metadata

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataFileTemplateServiceTest {
    @Test
    fun `provides valid create-file templates for every metadata type`() {
        val expectedTemplateFiles = listOf(
            "example.authority.json",
            "example.menu.json",
            "example.dev-menu.json",
            "example.ui-model.json",
            "example.ui-schema.json",
            "app-modules.json",
            "configuration.json",
            "configuration-groups.json"
        )

        assertEquals(expectedTemplateFiles, MetadataContributionRegistry.all.map { it.fileTemplate?.defaultFileName })

        MetadataContributionRegistry.all.forEach { contribution ->
            val fileName = contribution.fileTemplate?.defaultFileName
            assertNotNull(fileName)
            val path = "/repo/src/main/metadata/$fileName"

            assertEquals(contribution.type, MetadataFileMatcher.match(path)?.type)
            assertEquals(emptyList<MetadataSchemaDiagnostic>(), MetadataSchemaDiagnostics.validate(path, MetadataFileTemplateService.templateText(contribution)))
        }
    }

    @Test
    fun `normalizes suffix metadata file names into src main metadata`() {
        val menu = MetadataContributionRegistry.byType("menu")!!

        assertEquals(
            "src/main/metadata/example.menu.json",
            MetadataFileTemplateService.normalizeInputPath("example", menu).path
        )
        assertEquals(
            "src/main/metadata/nested/example.menu.json",
            MetadataFileTemplateService.normalizeInputPath("nested/example.menu.json", menu).path
        )
        assertTrue(MetadataFileTemplateService.normalizeInputPath("example.menu-model.json", menu).error!!.contains(".menu.json"))
    }

    @Test
    fun `keeps create paths relative when invoked inside the metadata directory`() {
        val menu = MetadataContributionRegistry.byType("menu")!!
        val appModule = MetadataContributionRegistry.byType("app-module")!!

        assertEquals(
            "example.menu.json",
            MetadataFileTemplateService.normalizeInputPath(
                "example.menu.json",
                menu,
                "/repo/module/src/main/metadata"
            ).path
        )
        assertEquals(
            "nested/example.menu.json",
            MetadataFileTemplateService.normalizeInputPath(
                "nested/example.menu.json",
                menu,
                "/repo/module/src/main/metadata/pages"
            ).path
        )
        assertEquals(
            "app-modules.json",
            MetadataFileTemplateService.normalizeInputPath(
                "app-modules.json",
                appModule,
                "/repo/module/src/main/metadata"
            ).path
        )
    }

    @Test
    fun `keeps fixed collection metadata at metadata root`() {
        val appModule = MetadataContributionRegistry.byType("app-module")!!

        assertEquals(
            "src/main/metadata/app-modules.json",
            MetadataFileTemplateService.normalizeInputPath("app-modules.json", appModule).path
        )
        assertTrue(MetadataFileTemplateService.normalizeInputPath("modules/app-modules.json", appModule).error!!.contains("src/main/metadata/app-modules.json"))
    }

    @Test
    fun `applies create-file field values through the shared template contract`() {
        val uiSchema = MetadataContributionRegistry.byType("ui-schema")!!
        val nameField = uiSchema.fileTemplate?.fields?.first { it.name == "name" }
        val defaults = MetadataFileTemplateService.defaultTemplateFieldValues(
            uiSchema,
            "src/main/metadata/admin/user-list.ui-schema.json"
        )

        assertEquals("按低代码页面命名习惯填写点分名称，例如 Admin.UserList；系统运行时会映射成 /admin/user-list。", nameField?.labelRemark)
        assertEquals("Admin.UserList", defaults["name"])
        assertEquals("User List", defaults["title"])

        val json = JsonParser.parseString(
            MetadataFileTemplateService.templateText(
                uiSchema,
                mapOf(
                    "name" to "Admin.UserList",
                    "title" to "用户列表",
                    "description" to "用户列表页面"
                )
            )
        ).asJsonObject

        assertEquals("Admin.UserList", json.get("name").asString)
        assertEquals("用户列表", json.get("title").asString)
        assertEquals("用户列表页面", json.get("description").asString)
        assertEquals("page", json.getAsJsonObject("schema").get("type").asString)
    }
}
