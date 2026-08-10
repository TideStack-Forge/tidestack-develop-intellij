package com.ouroboros.idea.services

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.ouroboros.idea.settings.TideStackDevelopConfig
import com.ouroboros.idea.settings.TideStackDevelopConfigService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
class OuroborosApiService(private val project: Project) {
    private val configService = project.service<TideStackDevelopConfigService>()
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun testConnection(): Result<Unit> {
        val config = configService.loadEffectiveConfig().getOrElse { return Result.failure(it) }
        return testConnection(config)
    }

    fun testConnection(config: TideStackDevelopConfig): Result<Unit> {
        val url = "${config.developmentServerUrl}/api/app/container"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 401) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Connection failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resolveDevelopTarget(config: TideStackDevelopConfig): Result<DevelopTarget> {
        val authorized = authorize(config).getOrElse { return Result.failure(it) }
        val profileId = authorized.profileId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IOException("Authorization response did not include profileId"))
        val containers = getContainers(config, profileId).getOrElse { return Result.failure(it) }
        val container = selectTargetContainer(config, profileId, containers).getOrElse {
            return Result.failure(it)
        }
        return Result.success(
            DevelopTarget(
                profileId = profileId,
                containerId = container.id,
                containerName = container.name,
                containerStatus = container.status,
                backendType = container.backendType
            )
        )
    }

    fun getContainers(appName: String, profileId: String): Result<List<Container>> {
        val config = configService.loadEffectiveConfig().getOrElse { return Result.failure(it) }
        return getContainers(config.developmentServerUrl, appName, profileId)
    }

    fun getContainers(config: TideStackDevelopConfig, profileId: String): Result<List<Container>> {
        return getContainers(config.developmentServerUrl, config.appName, profileId)
    }

    private fun getContainers(developmentServerUrl: String, appName: String, profileId: String): Result<List<Container>> {
        val url = "$developmentServerUrl/api/app/container"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Query containers failed: ${response.code}"))
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(IOException("Empty response body"))

                val data = parseApiResultData(responseBody, "Query containers").getOrElse { return Result.failure(it) }

                val itemsType = object : TypeToken<List<AppContainer>>() {}.type
                val itemsJson = if (data.isJsonObject && data.asJsonObject.has("items")) {
                    data.asJsonObject.get("items")
                } else {
                    data
                }
                val allContainers: List<AppContainer> = gson.fromJson(
                    itemsJson,
                    itemsType
                ) ?: emptyList()

                val matchedContainers = allContainers
                    .filter { it.appName == appName }
                    .flatMap { app ->
                        app.children.orEmpty().filter { child ->
                            child.profile == profileId || profileId.isBlank()
                        }.map { child ->
                            Container(
                                id = child.id,
                                name = child.name ?: "${app.appName}-${child.profile}",
                                status = child.status,
                                appName = app.appName,
                                profile = child.profile,
                                backendType = child.backendType
                            )
                        }
                    }

                Result.success(matchedContainers)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun authorize(): Result<AuthorizedProfile> {
        val config = configService.loadEffectiveConfig().getOrElse { return Result.failure(it) }
        return authorize(config)
    }

    fun authorize(config: TideStackDevelopConfig): Result<AuthorizedProfile> {
        val url = "${config.developmentServerUrl}/api/dev/${apiPathSegment(config.appName)}/authorize"
        val json = gson.toJson(mapOf("devKey" to config.devKey))
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Authorization failed: ${response.code}"))
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(IOException("Authorization failed: empty response body"))
                val data = parseApiResultData(responseBody, "Authorization").getOrElse { return Result.failure(it) }
                Result.success(gson.fromJson(data, AuthorizedProfile::class.java))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun uploadJar(jarFile: File): Result<String> {
        val config = configService.loadEffectiveConfig().getOrElse { return Result.failure(it) }
        return uploadJar(jarFile, config)
    }

    fun uploadJar(jarFile: File, config: TideStackDevelopConfig): Result<String> {
        val url = "${config.developmentServerUrl}/api/dev/${config.appName}/extend-jar"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                jarFile.name,
                jarFile.asRequestBody("application/java-archive".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Upload successful")
                } else {
                    Result.failure(IOException("Upload failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun restartContainer(containerId: String): Result<Unit> {
        val config = configService.loadEffectiveConfig().getOrElse { return Result.failure(it) }
        return restartContainer(containerId, config)
    }

    fun restartContainer(containerId: String, config: TideStackDevelopConfig): Result<Unit> {
        val url = "${config.developmentServerUrl}/api/app/container/$containerId/restart"
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody())
            .build()

        return executeUnitRequest(request, "Restart")
    }

    fun createDebugSession(): Result<DebugSession> {
        val config = configService.loadEffectiveConfig().getOrElse { return Result.failure(it) }
        val target = resolveDevelopTarget(config).getOrElse { return Result.failure(it) }
        return createDebugSession(config, target)
    }

    fun createDebugSession(config: TideStackDevelopConfig, target: DevelopTarget): Result<DebugSession> {
        val url = "${config.developmentServerUrl}/api/dev/runtime/debug-sessions"
        val json = gson.toJson(
            mapOf(
                "appName" to config.appName,
                "profile" to target.profileId,
                "runtimeKind" to "TEST_APP",
                "backendType" to (target.backendType ?: "DOCKER")
            )
        )
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Create session failed: ${response.code}"))
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(IOException("Create session failed: empty response body"))

                val data = parseApiResultData(responseBody, "Create session").getOrElse { return Result.failure(it) }
                Result.success(gson.fromJson(data, DebugSession::class.java))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun selectTargetContainer(
        config: TideStackDevelopConfig,
        profileId: String,
        containers: List<Container>
    ): Result<Container> {
        if (containers.isEmpty()) {
            return Result.failure(IOException("No containers found for app '${config.appName}' and profile '$profileId'"))
        }
        return Result.success(containers.firstOrNull { it.status.equals("RUNNING", ignoreCase = true) } ?: containers.first())
    }

    private fun parseApiResultData(responseBody: String, actionName: String): Result<JsonElement> {
        return try {
            val apiResult = gson.fromJson(responseBody, ApiResult::class.java)
                ?: return Result.failure(IOException("$actionName failed: empty API result"))
            if (apiResult.resultCode() != 0) {
                return Result.failure(IOException("$actionName failed: ${apiResult.resultMessage()}"))
            }
            Result.success(gson.toJsonTree(apiResult.data))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeUnitRequest(request: Request, actionName: String): Result<Unit> {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("$actionName failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun apiPathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }

    data class Container(
        val id: String,
        val name: String,
        val status: String,
        val appName: String,
        val profile: String,
        val backendType: String?
    )

    data class AuthorizedProfile(
        val profileId: String?,
        val authorized: Boolean?,
        val runtimeStrategy: String?,
        val expiresAt: String?
    )

    data class DevelopTarget(
        val profileId: String,
        val containerId: String,
        val containerName: String,
        val containerStatus: String,
        val backendType: String?
    )

    data class ApiResult(
        val code: Int?,
        val status: Int?,
        val msg: String?,
        val message: String?,
        val data: Any?
    ) {
        fun resultCode(): Int = code ?: status ?: 0

        fun resultMessage(): String = msg ?: message ?: "Unknown API error"
    }

    data class AppContainer(
        val appName: String,
        val children: List<ContainerChild>?
    )

    data class ContainerChild(
        val id: String,
        val name: String?,
        val profile: String,
        val status: String,
        val backendType: String?
    )

    data class DebugSession(
        val sessionId: String,
        val endpoint: String,
        val host: String,
        val port: Int
    )
}
