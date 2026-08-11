package com.ouroboros.idea.metadata

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ouroboros.idea.TideStackMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class MetadataFileTemplateServiceTest {
    private val creationCases: MetadataCreationCases by lazy { loadMetadataCreationCases() }

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
    fun `reads fixed collection menu labels from the shared i18n catalog`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        try {
            assertEquals("应用模块集合", TideStackMessages.actionText("metadata.type.app-module.command"))
            assertEquals("配置项集合", TideStackMessages.actionText("metadata.type.configuration.command"))
            assertEquals("配置分组集合", TideStackMessages.actionText("metadata.type.configuration-group.command"))
        } finally {
            Locale.setDefault(previousLocale)
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
    fun `resolves create paths from metadata directory scope`() {
        val menu = MetadataContributionRegistry.byType("menu")!!
        val appModule = MetadataContributionRegistry.byType("app-module")!!

        assertEquals("src/main/metadata", MetadataFileTemplateService.creationDirectoryPath("src/main/java/com/acme", menu))
        assertEquals("src/main/metadata/pages", MetadataFileTemplateService.creationDirectoryPath("src/main/metadata/pages", menu))
        assertEquals("src/main/metadata", MetadataFileTemplateService.creationDirectoryPath("src/main/metadata/pages", appModule))

        assertEquals(
            "src/main/metadata/example.menu.json",
            MetadataFileTemplateService.normalizeInputPath(
                "example.menu.json",
                menu,
                "src/main/metadata"
            ).path
        )
        assertEquals(
            "src/main/metadata/pages/nested/example.menu.json",
            MetadataFileTemplateService.normalizeInputPath(
                "nested/example.menu.json",
                menu,
                "src/main/metadata/pages"
            ).path
        )
        assertEquals(
            "src/main/metadata/app-modules.json",
            MetadataFileTemplateService.normalizeInputPath(
                "app-modules.json",
                appModule,
                "src/main/metadata/pages"
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

    @Test
    fun `plans metadata creation through the shared creation flow model`() {
        val uiSchema = MetadataContributionRegistry.byType("ui-schema")!!
        val flow = MetadataFileTemplateService.creationFlowModel("src/main/metadata/admin", uiSchema)

        assertEquals("ui-schema", flow.metadataType)
        assertEquals("UI Schema", flow.displayName)
        assertEquals("src/main/metadata/admin", flow.creationDirectory)
        assertEquals("src/main/metadata/admin/example.ui-schema.json", flow.defaultPath)
        assertEquals("example.ui-schema.json", flow.defaultPathInput)
        assertEquals(false, flow.pathInputLocked)
        assertEquals(listOf("name", "title", "description"), flow.fields.map { it.name })
        assertEquals("Admin.Example", flow.fields.first { it.name == "name" }.defaultValue)

        val normalized = MetadataFileTemplateService.normalizeInputPath("user-list", uiSchema, flow)
        assertEquals("src/main/metadata/admin/user-list.ui-schema.json", normalized.path)

        val plan = MetadataFileTemplateService.creationPlan(
            uiSchema,
            "user-list",
            flow,
            mapOf(
                "name" to "Admin.UserList",
                "title" to "用户列表",
                "description" to "用户列表页面"
            )
        ).getOrThrow()

        assertEquals("src/main/metadata/admin/user-list.ui-schema.json", plan.relativePath)
        assertEquals("Admin.UserList", plan.templateValues["name"])
        assertEquals("用户列表", JsonParser.parseString(plan.text).asJsonObject.get("title").asString)

        val invalid = MetadataFileTemplateService.creationPlan(
            uiSchema,
            "user-list",
            flow,
            mapOf("name" to "", "title" to "用户列表")
        )
        assertTrue(invalid.isFailure)
        assertTrue(invalid.exceptionOrNull()?.message?.contains("名称") == true)

        val appModule = MetadataContributionRegistry.byType("app-module")!!
        val fixedFileFlow = MetadataFileTemplateService.creationFlowModel("src/main/metadata/admin", appModule)
        assertEquals("src/main/metadata", fixedFileFlow.creationDirectory)
        assertEquals("src/main/metadata/app-modules.json", fixedFileFlow.defaultPath)
        assertEquals("app-modules.json", fixedFileFlow.defaultPathInput)
        assertEquals(true, fixedFileFlow.pathInputLocked)
    }

    @Test
    fun `loads metadata creation path and default-value rules from the shared contract`() {
        val contract = MetadataCreationContracts.current

        assertEquals("./", contract.pathRules.relativeCurrentDirectoryPrefix)
        assertEquals(".json", contract.pathRules.jsonExtension)
        assertEquals("metadata.json", contract.pathRules.fallbackFileName)
        assertEquals(listOf("src", "main"), contract.pathRules.sourceMainSegments)
        assertEquals("example", contract.templateDefaultRules.fixedFileNameStem)
        assertEquals("example", contract.templateDefaultRules.fallbacks["camel-name"])
        assertEquals("Example", contract.templateDefaultRules.fallbacks["dot-name"])
        assertEquals("example", contract.templateDefaultRules.fallbacks["slash-path"])
        assertEquals("Example", contract.templateDefaultRules.fallbacks["title"])
    }

    @Test
    fun `matches shared metadata creation path contract cases`() {
        creationCases.pathCases.forEach { pathCase ->
            val contribution = MetadataContributionRegistry.byType(pathCase.metadataType)!!
            val creationDirectory = MetadataFileTemplateService.creationDirectoryPath(
                pathCase.selectedDirectory,
                contribution
            )

            assertEquals(pathCase.expectedCreationDirectory, creationDirectory, pathCase.name)
            assertEquals(
                pathCase.expectedDefaultInputPath,
                MetadataFileTemplateService.defaultInputPath(creationDirectory, contribution),
                pathCase.name
            )
            assertEquals(
                pathCase.expectedDefaultPathInput,
                MetadataFileTemplateService.inputForPath(pathCase.expectedDefaultInputPath, creationDirectory),
                pathCase.name
            )

            val result = MetadataFileTemplateService.normalizeInputPath(pathCase.input, contribution, creationDirectory)
            if (pathCase.expectedPath != null) {
                assertEquals(pathCase.expectedPath, result.path, pathCase.name)
            } else {
                assertNotNull(result.error, pathCase.name)
                pathCase.expectedErrorContains.forEach { expected ->
                    assertTrue(result.error!!.contains(expected), "${pathCase.name}: expected error to contain $expected")
                }
            }
        }
    }

    @Test
    fun `matches shared metadata creation template contract cases`() {
        creationCases.templateDefaultCases.forEach { templateCase ->
            val contribution = MetadataContributionRegistry.byType(templateCase.metadataType)!!
            val defaults = MetadataFileTemplateService.defaultTemplateFieldValues(contribution, templateCase.targetPath)

            templateCase.expectedValues.entrySet().forEach { (fieldName, expectedValue) ->
                assertEquals(expectedValue.asString, defaults[fieldName], "${templateCase.name}: $fieldName")
            }
        }

        creationCases.templateSerializationCases.forEach { templateCase ->
            val contribution = MetadataContributionRegistry.byType(templateCase.metadataType)!!
            val values = templateCase.values.entrySet().associate { (fieldName, value) -> fieldName to value.asString }
            val actual = JsonParser.parseString(MetadataFileTemplateService.templateText(contribution, values))

            assertEquals(templateCase.expectedDocument, actual, templateCase.name)
        }
    }

    private fun loadMetadataCreationCases(): MetadataCreationCases {
        val resourcePath = "ouroboros-metadata-editor/metadataCreationCases.json"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Missing metadata creation cases resource: $resourcePath")
        return stream.reader(Charsets.UTF_8).use { reader ->
            Gson().fromJson(reader, MetadataCreationCases::class.java)
                ?: error("Empty metadata creation cases resource: $resourcePath")
        }
    }

    private data class MetadataCreationCases(
        val pathCases: List<MetadataCreationPathCase> = emptyList(),
        val templateDefaultCases: List<MetadataCreationTemplateDefaultCase> = emptyList(),
        val templateSerializationCases: List<MetadataCreationTemplateSerializationCase> = emptyList()
    )

    private data class MetadataCreationPathCase(
        val name: String = "",
        val metadataType: String = "",
        val selectedDirectory: String = "",
        val input: String = "",
        val expectedCreationDirectory: String = "",
        val expectedDefaultInputPath: String = "",
        val expectedDefaultPathInput: String = "",
        val expectedPath: String? = null,
        val expectedErrorContains: List<String> = emptyList()
    )

    private data class MetadataCreationTemplateDefaultCase(
        val name: String = "",
        val metadataType: String = "",
        val targetPath: String = "",
        val expectedValues: JsonObject = JsonObject()
    )

    private data class MetadataCreationTemplateSerializationCase(
        val name: String = "",
        val metadataType: String = "",
        val targetPath: String = "",
        val values: JsonObject = JsonObject(),
        val expectedDocument: JsonElement = JsonObject()
    )
}
