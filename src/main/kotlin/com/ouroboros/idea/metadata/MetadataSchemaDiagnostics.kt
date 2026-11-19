package com.ouroboros.idea.metadata

import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.path.NodePath
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

enum class MetadataSchemaDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class MetadataSchemaDiagnostic(
    val code: String,
    val message: String,
    val severity: MetadataSchemaDiagnosticSeverity,
    val path: String? = null
)

object MetadataSchemaDiagnostics {
    private val mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
    // Resolve bundled references only. Networknt does not fetch remote resources by default.
    private val schemas: Map<String, Schema> by lazy {
        val resources = MetadataContributionRegistry.schemas.associate { it.get("\$id").asString to it.toString() }
        val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { builder ->
            builder.schemas(resources)
        }
        resources.keys.associateWith { id ->
            registry.getSchema(SchemaLocation.of(id)).also { it.initializeValidators() }
        }
    }

    fun validate(path: String, text: String): List<MetadataSchemaDiagnostic> {
        val match = MetadataFileMatcher.match(path)
        val pathDiagnostics = match?.diagnostics ?: emptyList()
        val document = try {
            mapper.readValue(text, JsonNode::class.java)
        } catch (e: JacksonException) {
            return pathDiagnostics + MetadataSchemaDiagnostic(
                code = "invalid-json",
                message = e.message ?: "Invalid JSON document.",
                severity = MetadataSchemaDiagnosticSeverity.ERROR
            )
        }
        if (match == null) {
            return pathDiagnostics
        }
        val contribution = match.contribution
        val definition = contribution.legacyJsonSchema?.takeIf {
            contribution.fileSuffix != null && !path.endsWith(contribution.fileSuffix)
        } ?: contribution.jsonSchema
        val schema = schemas.getValue(definition.get("\$id").asString)
        return pathDiagnostics + schema.validate(document).map { error ->
            val diagnosticPath = diagnosticPath(error.instanceLocation, if (error.keyword == "required") error.property else null)
            MetadataSchemaDiagnostic(
                code = when (error.keyword) {
                    "required" -> "schema-required-property"
                    "type" -> "schema-property-type"
                    "enum" -> "schema-enum-value"
                    else -> "schema-${error.keyword}"
                },
                message = error.message,
                severity = MetadataSchemaDiagnosticSeverity.ERROR,
                path = diagnosticPath.ifEmpty { null }
            )
        }
    }

    private fun diagnosticPath(location: NodePath, missingProperty: String?): String {
        val result = StringBuilder()
        for (index in 0 until location.nameCount) {
            val element = location.getElement(index)
            if (element is Number) {
                result.append("[$element]")
            } else {
                if (result.isNotEmpty()) result.append('.')
                result.append(element)
            }
        }
        if (missingProperty != null) {
            if (result.isNotEmpty()) result.append('.')
            result.append(missingProperty)
        }
        return result.toString()
    }
}
