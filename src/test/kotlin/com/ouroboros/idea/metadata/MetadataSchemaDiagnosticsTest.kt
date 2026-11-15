package com.ouroboros.idea.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataSchemaDiagnosticsTest {
    @Test
    fun `valid JSON in a matching metadata path has no diagnostics`() {
        val diagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/metadata/authority/user.authority.json",
            "{\"fullName\":\"user\",\"title\":\"User\"}"
        )

        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun `invalid JSON keeps a schema diagnostic available for fallback editor`() {
        val diagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/metadata/dev-menu-model/all.dev-menu.json",
            "{"
        )

        assertEquals("invalid-json", diagnostics.single().code)
        assertEquals(MetadataSchemaDiagnosticSeverity.ERROR, diagnostics.single().severity)
    }

    @Test
    fun `required schema fields are diagnosed for JCEF and fallback editors`() {
        val diagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/metadata/menu-model/main.menu.json",
            "{\"fullName\":7,\"placement\":\"side\"}"
        )

        assertEquals(
            listOf("schema-required-property", "schema-property-type", "schema-enum-value"),
            diagnostics.map { it.code }
        )
    }

    @Test
    fun `runtime menu and authority structures are diagnosed`() {
        val menuDiagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/metadata/menu-model/main.menu.json",
            "{\"fullName\":\"main\",\"title\":\"Main\",\"order\":\"first\"}"
        )
        val authorityDiagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/metadata/authority/user.authority.json",
            "{\"fullName\":\"user\",\"title\":\"User\",\"apis\":[9]}"
        )
        val devMenuDiagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/metadata/dev-menu-model/all.dev-menu.json",
            "{\"fullName\":\"dev\",\"title\":\"Dev\",\"stage\":\"prod\",\"children\":[{\"title\":\"Child\"}]}"
        )

        assertEquals(listOf("schema-property-type"), menuDiagnostics.map { it.code })
        assertEquals(listOf("schema-property-type"), authorityDiagnostics.map { it.code })
        assertEquals(listOf("schema-enum-value", "schema-required-property"), devMenuDiagnostics.map { it.code })
    }

    @Test
    fun `validates array metadata item structures`() {
        val appModuleDiagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/resources/META-INF/ouroboros/app-modules.json",
            "[{\"fullName\":7}]"
        )
        val configurationDiagnostics = MetadataSchemaDiagnostics.validate(
            "/repo/src/main/resources/META-INF/ouroboros/configurations.json",
            "[{\"key\":\"platform.enabled\",\"label\":\"Enabled\",\"valueType\":\"unknown\"}]"
        )

        assertEquals(listOf("schema-required-property", "schema-property-type"), appModuleDiagnostics.map { it.code })
        assertEquals(listOf("schema-enum-value"), configurationDiagnostics.map { it.code })
    }
}
