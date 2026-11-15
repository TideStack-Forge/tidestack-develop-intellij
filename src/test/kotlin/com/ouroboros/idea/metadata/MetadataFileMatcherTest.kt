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
    fun `matches formal fixed metadata file names without source directory prefix`() {
        val paths = listOf(
            "/repo/custom/error-codes.error.json",
            "/repo/custom/app.metadata.json",
            "/repo/custom/modules.diff.json",
            "/repo/custom/app-modules.json",
            "/repo/custom/configurations.json",
            "/repo/custom/configuration-groups.json"
        )

        val matches = paths.map { MetadataFileMatcher.match(it) }

        assertEquals(
            listOf("error-code", "app-metadata", "modules-diff", "app-module", "configuration", "configuration-group"),
            matches.map { it?.type }
        )
        assertEquals(
            listOf(
                "META-INF/ouroboros/metadata/error-codes.error.json",
                "META-INF/ouroboros/metadata/app.metadata.json",
                "META-INF/ouroboros/metadata/modules.diff.json",
                "META-INF/ouroboros/metadata/app-modules.json",
                "META-INF/ouroboros/metadata/configurations.json",
                "META-INF/ouroboros/metadata/configuration-groups.json"
            ),
            matches.map { it?.packagedPath }
        )
        assertEquals(null, MetadataFileMatcher.match("/repo/custom/configuration.json"))
    }

    @Test
    fun `maps new metadata suffixes to their own runtime directories`() {
        val paths = listOf(
            "/repo/src/main/metadata/model-home.home.ui-model.json",
            "/repo/src/main/metadata/pages/home.ui-schema.json",
            "/repo/src/main/metadata/business-model/order.business.json",
            "/repo/src/main/metadata/fields/text.field-type.json",
            "/repo/src/main/metadata/logic/start.flow-node.json",
            "/repo/src/main/metadata/widgets/summary.widget.json",
            "/repo/src/main/metadata/web/admin.micro-web.json",
            "/repo/src/main/metadata/rpc/open-api.rpc.json",
            "/repo/src/main/metadata/rpc/auth/shared.rpc.auth.json",
            "/repo/src/main/metadata/webapi/orders.webapi.json",
            "/repo/src/main/metadata/notifications/welcome.notification.json",
            "/repo/src/main/metadata/audit/create-order.business-log.json",
            "/repo/src/main/metadata/seed/status.data-init.json",
            "/repo/src/main/metadata/mq/orders-created.mq.json",
            "/repo/src/main/metadata/schedule/daily-sync.schedule.json",
            "/repo/src/main/metadata/i18n/zh-cn.i18n.json",
            "/repo/src/main/metadata/data-dict/customer-status.dict.json",
            "/repo/src/main/metadata/organization/member.position.json",
            "/repo/src/main/metadata/error-codes.error.json",
            "/repo/src/main/metadata/app.metadata.json",
            "/repo/src/main/metadata/modules.diff.json",
            "/repo/src/main/metadata/app-modules.json",
            "/repo/src/main/metadata/configurations.json",
            "/repo/src/main/metadata/configuration-groups.json",
            "/repo/configuration/key0.config.json",
            "/repo/configuration-group/security-session.config-group.json"
        )

        val matches = paths.map { MetadataFileMatcher.match(it) }

        assertEquals(
            listOf(
                "ui-model",
                "ui-schema",
                "business",
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
            matches.map { it?.type }
        )
        assertEquals(
            listOf(
                "META-INF/ouroboros/metadata/model-home.home.ui-model.json",
                "META-INF/ouroboros/metadata/pages/home.ui-schema.json",
                "META-INF/ouroboros/metadata/business-model/order.business.json",
                "META-INF/ouroboros/metadata/fields/text.field-type.json",
                "META-INF/ouroboros/metadata/logic/start.flow-node.json",
                "META-INF/ouroboros/metadata/widgets/summary.widget.json",
                "META-INF/ouroboros/metadata/web/admin.micro-web.json",
                "META-INF/ouroboros/metadata/rpc/open-api.rpc.json",
                "META-INF/ouroboros/metadata/rpc/auth/shared.rpc.auth.json",
                "META-INF/ouroboros/metadata/webapi/orders.webapi.json",
                "META-INF/ouroboros/metadata/notifications/welcome.notification.json",
                "META-INF/ouroboros/metadata/audit/create-order.business-log.json",
                "META-INF/ouroboros/metadata/seed/status.data-init.json",
                "META-INF/ouroboros/metadata/mq/orders-created.mq.json",
                "META-INF/ouroboros/metadata/schedule/daily-sync.schedule.json",
                "META-INF/ouroboros/metadata/i18n/zh-cn.i18n.json",
                "META-INF/ouroboros/metadata/data-dict/customer-status.dict.json",
                "META-INF/ouroboros/metadata/organization/member.position.json",
                "META-INF/ouroboros/metadata/error-codes.error.json",
                "META-INF/ouroboros/metadata/app.metadata.json",
                "META-INF/ouroboros/metadata/modules.diff.json",
                "META-INF/ouroboros/metadata/app-modules.json",
                "META-INF/ouroboros/metadata/configurations.json",
                "META-INF/ouroboros/metadata/configuration-groups.json",
                "META-INF/ouroboros/metadata/key0.config.json",
                "META-INF/ouroboros/metadata/security-session.config-group.json"
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
    fun `matches legacy fixed configuration metadata source file`() {
        val match = MetadataFileMatcher.match("/repo/src/main/metadata/configuration.json")

        assertNotNull(match)
        assertEquals("configuration", match!!.type)
        assertEquals("META-INF/ouroboros/metadata/configuration.json", match.packagedPath)
        assertTrue(match.diagnostics.isEmpty())
    }

    @Test
    fun `matches new packaged metadata resources by metadata directory`() {
        val menu = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/system/menu.menu.json")
        val business = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/business-model/order.business.json")
        val fieldType = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/fields/text.field-type.json")
        val flowNode = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/logic/start.flow-node.json")
        val widget = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/widgets/summary.widget.json")
        val microWeb = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/web/admin.micro-web.json")
        val rpc = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/rpc/open-api.rpc.json")
        val rpcAuth = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/rpc/auth/shared.rpc.auth.json")
        val webapi = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/webapi/orders.webapi.json")
        val notification = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/notifications/welcome.notification.json")
        val businessLog = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/audit/create-order.business-log.json")
        val dataInit = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/seed/status.data-init.json")
        val mq = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/mq/orders-created.mq.json")
        val schedule = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/schedule/daily-sync.schedule.json")
        val i18n = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/i18n/zh-cn.i18n.json")
        val dataDict = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/data-dict/customer-status.dict.json")
        val position = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/organization/member.position.json")
        val errorCode = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/error-codes.error.json")
        val appMetadata = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/app.metadata.json")
        val modulesDiff = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/modules.diff.json")
        val appModules = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/app-modules.json")
        val configuration = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/configurations.json")
        val groups = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/metadata/configuration-groups.json")
        val configurationItem = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/configuration/key0.config.json")
        val configurationGroupItem = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/configuration-group/security-session.config-group.json")

        assertEquals("menu", menu?.type)
        assertEquals("business", business?.type)
        assertEquals("field-type", fieldType?.type)
        assertEquals("flow-node", flowNode?.type)
        assertEquals("widget", widget?.type)
        assertEquals("micro-web", microWeb?.type)
        assertEquals("rpc", rpc?.type)
        assertEquals("rpc-auth", rpcAuth?.type)
        assertEquals("webapi", webapi?.type)
        assertEquals("notification", notification?.type)
        assertEquals("business-log", businessLog?.type)
        assertEquals("data-init", dataInit?.type)
        assertEquals("mq", mq?.type)
        assertEquals("schedule", schedule?.type)
        assertEquals("i18n", i18n?.type)
        assertEquals("data-dict", dataDict?.type)
        assertEquals("position", position?.type)
        assertEquals("error-code", errorCode?.type)
        assertEquals("app-metadata", appMetadata?.type)
        assertEquals("modules-diff", modulesDiff?.type)
        assertEquals("app-module", appModules?.type)
        assertEquals("configuration", configuration?.type)
        assertEquals("configuration-group", groups?.type)
        assertEquals("configuration-item", configurationItem?.type)
        assertEquals("configuration-group-item", configurationGroupItem?.type)
        assertTrue(listOf(menu, business, fieldType, flowNode, widget, microWeb, rpc, rpcAuth, webapi, notification, businessLog, dataInit, mq, schedule, i18n, dataDict, position, errorCode, appMetadata, modulesDiff, appModules, configuration, groups, configurationItem, configurationGroupItem).all { it?.diagnostics?.isEmpty() == true })
    }

    @Test
    fun `matches legacy packaged resources by runtime subpath`() {
        val menu = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/menu-model/systemSetting/resourceManagement.json")
        val authority = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/authority/systemSetting/resourceManagement.json")
        val devMenu = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/dev-menu-model/AllModels.Resource.json")
        val rpc = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/rpc-model/open-api.rpc.json")
        val rpcAuth = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/rpc-model/auth/shared.rpc.auth.json")

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

        assertNotNull(rpc)
        assertEquals("rpc", rpc!!.type)
        assertTrue(rpc.diagnostics.isEmpty())

        assertNotNull(rpcAuth)
        assertEquals("rpc-auth", rpcAuth!!.type)
        assertTrue(rpcAuth.diagnostics.isEmpty())
    }

    @Test
    fun `matches legacy root collection resources exactly`() {
        val appMetadata = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/app.metadata.json")
        val modulesDiff = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/modules.diff.json")
        val appModules = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/app-modules.json")
        val configuration = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/configuration.json")
        val groups = MetadataFileMatcher.match("/repo/module/src/main/resources/META-INF/ouroboros/configuration-groups.json")

        assertEquals("app-metadata", appMetadata?.type)
        assertEquals("modules-diff", modulesDiff?.type)
        assertEquals("app-module", appModules?.type)
        assertEquals("configuration", configuration?.type)
        assertEquals("configuration-group", groups?.type)
        assertTrue(listOf(appMetadata, modulesDiff, appModules, configuration, groups).all { it?.diagnostics?.isEmpty() == true })
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
