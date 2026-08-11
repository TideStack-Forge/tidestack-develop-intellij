package com.ouroboros.idea

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.ouroboros.idea.metadata.MetadataContributionDefinition
import com.ouroboros.idea.metadata.MetadataFileTemplateDefinition
import java.util.Locale

object TideStackMessages {
    private const val RESOURCE_ROOT = "tidestack-develop-i18n"
    private const val DEFAULT_LOCALE = "zh-CN"
    private const val ENGLISH_LOCALE = "en-US"
    private val gson = Gson()
    private val catalogs: Map<String, JsonObject> by lazy {
        mapOf(
            DEFAULT_LOCALE to loadCatalog(DEFAULT_LOCALE),
            ENGLISH_LOCALE to loadCatalog(ENGLISH_LOCALE)
        )
    }

    fun currentLocale(): String = if (Locale.getDefault().language.equals("en", ignoreCase = true)) {
        ENGLISH_LOCALE
    } else {
        DEFAULT_LOCALE
    }

    fun message(key: String, data: Map<String, Any?> = emptyMap()): String {
        val locale = currentLocale()
        val template = catalogs[locale]?.get(key)?.asString
            ?: catalogs[DEFAULT_LOCALE]?.get(key)?.asString
            ?: key
        return format(template, data)
    }

    fun actionText(key: String): String {
        return message(key).removeSuffix("...")
    }

    fun localize(value: String?): String? {
        if (value == null) {
            return null
        }
        val locale = currentLocale()
        return catalogs[locale]?.get(value)?.asString ?: value
    }

    fun localizeContribution(contribution: MetadataContributionDefinition): MetadataContributionDefinition {
        return contribution.copy(
            displayName = localize(contribution.displayName).orEmpty(),
            fileTemplate = contribution.fileTemplate?.let { template ->
                MetadataFileTemplateDefinition(
                    defaultFileName = template.defaultFileName,
                    document = localizeJson(template.document),
                    fields = template.fields.map { field ->
                        field.copy(
                            label = localize(field.label).orEmpty(),
                            labelRemark = localize(field.labelRemark)
                        )
                    }
                )
            }
        )
    }

    private fun loadCatalog(locale: String): JsonObject {
        val resourcePath = "$RESOURCE_ROOT/$locale.json"
        val stream = TideStackMessages::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: error("Missing TideStack language resource: $resourcePath")
        return stream.reader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, JsonObject::class.java)
                ?: error("Empty TideStack language resource: $resourcePath")
        }
    }

    private fun localizeJson(value: JsonElement): JsonElement {
        return when {
            value is JsonPrimitive && value.isString -> JsonPrimitive(localize(value.asString))
            value is JsonArray -> JsonArray().apply {
                value.forEach { child -> add(localizeJson(child)) }
            }
            value is JsonObject -> JsonObject().apply {
                value.entrySet().forEach { (name, child) -> add(name, localizeJson(child)) }
            }
            else -> value.deepCopy()
        }
    }

    private fun format(template: String, data: Map<String, Any?>): String {
        return Regex("\\{\\{([\\w.-]+)}}").replace(template) { match ->
            data[match.groupValues[1]]?.toString().orEmpty()
        }
    }
}
