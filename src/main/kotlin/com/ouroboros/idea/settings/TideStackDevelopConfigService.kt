package com.ouroboros.idea.settings

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException

@Service(Service.Level.PROJECT)
class TideStackDevelopConfigService(private val project: Project) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun loadEffectiveConfig(): Result<TideStackDevelopConfig> {
        val projectConfig = loadProjectConfig().getOrElse { return Result.failure(it) }
        val localConfig = loadLocalConfig().getOrElse { return Result.failure(it) }
        val legacySettings = OuroborosSettings.getInstance()

        return Result.success(
            TideStackDevelopConfig(
                developmentServerUrl = projectConfig.developmentServerUrl.ifBlank { legacySettings.platformUrl },
                appName = projectConfig.appName.ifBlank { legacySettings.appName },
                devKey = localConfig.devKey.ifBlank { legacySettings.devKey.ifBlank { legacySettings.debugKey } }
            )
        )
    }

    fun loadProjectConfig(): Result<TideStackDevelopProjectConfig> {
        val file = configFile(PROJECT_CONFIG_RELATIVE_PATH).getOrElse { return Result.failure(it) }
        if (!file.isFile) {
            return Result.success(TideStackDevelopProjectConfig())
        }

        return readJsonObject(file).map(TideStackDevelopConfigContracts::projectConfig)
    }

    fun loadLocalConfig(): Result<TideStackDevelopLocalConfig> {
        val file = configFile(LOCAL_CONFIG_RELATIVE_PATH).getOrElse { return Result.failure(it) }
        if (!file.isFile) {
            return Result.success(TideStackDevelopLocalConfig())
        }

        return readJsonObject(file).map(TideStackDevelopConfigContracts::localConfig)
    }

    fun saveProjectConfig(config: TideStackDevelopProjectConfig): Result<Unit> {
        val existing = loadExistingDocument(TideStackDevelopConfigContracts.projectPath)
            .getOrElse { return Result.failure(it) }
        return writeJson(
            TideStackDevelopConfigContracts.projectPath,
            TideStackDevelopConfigContracts.projectDocument(existing, config)
        )
    }

    fun saveLocalConfig(config: TideStackDevelopLocalConfig): Result<Unit> {
        val existing = loadExistingDocument(TideStackDevelopConfigContracts.localPath)
            .getOrElse { return Result.failure(it) }
        return writeJson(
            TideStackDevelopConfigContracts.localPath,
            TideStackDevelopConfigContracts.localDocument(existing, config)
        )
    }

    fun projectConfigPath(): Result<String> = configFile(TideStackDevelopConfigContracts.projectPath).map { it.path }

    fun localConfigPath(): Result<String> = configFile(TideStackDevelopConfigContracts.localPath).map { it.path }

    private fun readJsonObject(file: File): Result<com.google.gson.JsonObject> {
        return try {
            val parsed = gson.fromJson(file.readText(), com.google.gson.JsonObject::class.java)
                ?: return Result.failure(IllegalStateException("Empty TideStack develop config JSON: ${file.path}"))
            Result.success(parsed)
        } catch (e: JsonSyntaxException) {
            Result.failure(IllegalStateException("Invalid TideStack develop config JSON: ${file.path}", e))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Failed to read TideStack develop config: ${file.path}", e))
        }
    }

    private fun loadExistingDocument(relativePath: String): Result<JsonObject> {
        val file = configFile(relativePath).getOrElse { return Result.failure(it) }
        return if (file.isFile) readJsonObject(file) else Result.success(JsonObject())
    }

    private fun writeJson(relativePath: String, value: Any): Result<Unit> {
        val file = configFile(relativePath).getOrElse { return Result.failure(it) }
        return try {
            if (!file.parentFile.isDirectory && !file.parentFile.mkdirs()) {
                return Result.failure(IllegalStateException("Failed to create TideStack develop config directory: ${file.parentFile.path}"))
            }
            file.writeText(gson.toJson(value) + "\n")
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Failed to write TideStack develop config: ${file.path}", e))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Failed to write TideStack develop config: ${file.path}", e))
        }
    }

    private fun configFile(relativePath: String): Result<File> {
        val basePath = project.basePath
            ?: return Result.failure(IllegalStateException("Project path is not available"))
        return Result.success(File(basePath, relativePath))
    }

    companion object {
        val PROJECT_CONFIG_RELATIVE_PATH: String
            get() = TideStackDevelopConfigContracts.projectPath
        val LOCAL_CONFIG_RELATIVE_PATH: String
            get() = TideStackDevelopConfigContracts.localPath
    }
}

data class TideStackDevelopProjectConfig(
    var schemaVersion: Int = TideStackDevelopConfigContracts.schemaVersion,
    var developmentServerUrl: String = "",
    var appName: String = ""
)

data class TideStackDevelopLocalConfig(
    var schemaVersion: Int = TideStackDevelopConfigContracts.schemaVersion,
    var devKey: String = ""
)

data class TideStackDevelopConfig(
    val developmentServerUrl: String,
    val appName: String,
    val devKey: String
)
