package com.ouroboros.idea.metadata

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

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
    fun validate(path: String, text: String): List<MetadataSchemaDiagnostic> {
        val match = MetadataFileMatcher.match(path)
        val pathDiagnostics = match?.diagnostics ?: emptyList()
        val jsonDiagnostics = try {
            val document = JsonParser.parseString(text)
            if (match == null) {
                emptyList()
            } else {
                validateAgainstSchema(document, match.contribution.jsonSchema, "")
            }
        } catch (e: JsonSyntaxException) {
            listOf(
                MetadataSchemaDiagnostic(
                    code = "invalid-json",
                    message = e.message ?: "Invalid JSON document.",
                    severity = MetadataSchemaDiagnosticSeverity.ERROR
                )
            )
        }
        return pathDiagnostics + jsonDiagnostics
    }

    private fun validateAgainstSchema(
        value: JsonElement,
        schema: JsonObject,
        path: String
    ): List<MetadataSchemaDiagnostic> {
        val diagnostics = mutableListOf<MetadataSchemaDiagnostic>()
        val enumValues = schema.getAsJsonArray("enum")
        if (enumValues != null && !enumContains(enumValues, value)) {
            diagnostics.add(enumDiagnostic(path, enumValues.map { it.asString }))
            return diagnostics
        }

        val schemaTypes = schemaTypes(schema.get("type"))
        val matchingType = schemaTypes.firstOrNull { schemaTypeMatches(value, it) }
        if (schemaTypes.isNotEmpty() && matchingType == null) {
            diagnostics.add(typeDiagnostic(path, schemaTypes))
            return diagnostics
        }

        when (matchingType) {
            "object" -> {
                if (!value.isJsonObject) {
                    diagnostics.add(typeDiagnostic(path, "object"))
                    return diagnostics
                }
                val document = value.asJsonObject
                schema.getAsJsonArray("required")?.forEach { required ->
                    val name = required.asString
                    if (!document.has(name)) {
                        val propertyPath = joinPath(path, name)
                        diagnostics.add(
                            MetadataSchemaDiagnostic(
                                code = "schema-required-property",
                                message = "Metadata document is missing required property '$propertyPath'.",
                                severity = MetadataSchemaDiagnosticSeverity.ERROR,
                                path = propertyPath
                            )
                        )
                    }
                }
                schema.getAsJsonObject("properties")?.entrySet()?.forEach { entry ->
                    if (document.has(entry.key) && entry.value.isJsonObject) {
                        diagnostics.addAll(validateAgainstSchema(document.get(entry.key), entry.value.asJsonObject, joinPath(path, entry.key)))
                    }
                }
            }

            "array" -> {
                if (!value.isJsonArray) {
                    diagnostics.add(typeDiagnostic(path, "array"))
                    return diagnostics
                }
                val itemSchema = schema.get("items")
                if (itemSchema != null && itemSchema.isJsonObject) {
                    value.asJsonArray.forEachIndexed { index, item ->
                        diagnostics.addAll(validateAgainstSchema(item, itemSchema.asJsonObject, "$path[$index]"))
                    }
                }
            }

            "string" -> if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
                diagnostics.add(typeDiagnostic(path, "string"))
            }

            "number" -> if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
                diagnostics.add(typeDiagnostic(path, "number"))
            }

            "integer" -> if (!isInteger(value)) {
                diagnostics.add(typeDiagnostic(path, "integer"))
            }

            "boolean" -> if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
                diagnostics.add(typeDiagnostic(path, "boolean"))
            }
        }
        return diagnostics
    }

    private fun typeDiagnostic(path: String, expectedType: String): MetadataSchemaDiagnostic {
        return typeDiagnostic(path, listOf(expectedType))
    }

    private fun typeDiagnostic(path: String, expectedTypes: List<String>): MetadataSchemaDiagnostic {
        val expected = expectedTypes.joinToString(" or ") { if (it == "null") "null" else "a $it" }
        return MetadataSchemaDiagnostic(
            code = "schema-property-type",
            message = "Metadata property '$path' must be $expected.",
            severity = MetadataSchemaDiagnosticSeverity.ERROR,
            path = path.ifEmpty { null }
        )
    }

    private fun enumDiagnostic(path: String, values: List<String>): MetadataSchemaDiagnostic {
        return MetadataSchemaDiagnostic(
            code = "schema-enum-value",
            message = "Metadata property '$path' must be one of: ${values.joinToString(", ")}.",
            severity = MetadataSchemaDiagnosticSeverity.ERROR,
            path = path.ifEmpty { null }
        )
    }

    private fun joinPath(prefix: String, name: String): String {
        return if (prefix.isEmpty()) name else "$prefix.$name"
    }

    private fun schemaTypes(typeElement: JsonElement?): List<String> {
        return when {
            typeElement == null -> emptyList()
            typeElement.isJsonPrimitive -> listOf(typeElement.asString)
            typeElement.isJsonArray -> typeElement.asJsonArray.mapNotNull { type ->
                if (type.isJsonPrimitive) type.asString else null
            }

            else -> emptyList()
        }
    }

    private fun schemaTypeMatches(value: JsonElement, expectedType: String): Boolean {
        return when (expectedType) {
            "object" -> value.isJsonObject
            "array" -> value.isJsonArray
            "string" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
            "number" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber
            "integer" -> isInteger(value)
            "boolean" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
            "null" -> value.isJsonNull
            else -> false
        }
    }

    private fun enumContains(enumValues: Iterable<JsonElement>, value: JsonElement): Boolean {
        return enumValues.any { allowed -> allowed == value || (allowed.isJsonPrimitive && value.isJsonPrimitive && allowed.asString == value.asString) }
    }

    private fun isInteger(value: JsonElement): Boolean {
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            return false
        }
        return try {
            value.asBigDecimal.stripTrailingZeros().scale() <= 0
        } catch (e: NumberFormatException) {
            false
        }
    }
}
