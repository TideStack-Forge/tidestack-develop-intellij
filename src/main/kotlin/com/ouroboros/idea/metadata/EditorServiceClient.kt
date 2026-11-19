package com.ouroboros.idea.metadata

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.ouroboros.idea.settings.TideStackDevelopConfigContracts
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/** An editor owns one authenticated HTTP owner and all of its language sessions. */
class EditorServiceClient(val appName: String, private val baseUrl: String, private val token: String, private val devKey: String = "") : AutoCloseable {
    private val gson = Gson()
    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private val cookies = mutableListOf<Cookie>()
    private val ownerLock = Any()
    private val lifecycleLock = Any()
    @Volatile private var closed = false
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(125, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) = synchronized(cookies) {
                received.forEach { cookie -> cookies.removeAll { it.name == cookie.name }; cookies.add(cookie) }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
                cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
            }
        }).build()

    init {
        val parsed = baseUrl.toHttpUrl()
        require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) { "Invalid editor service URL" }
        require(appName.matches(Regex("[A-Za-z0-9_.-]+"))) { "Configure appName in .tidestack/develop.json" }
    }

    // Serialize the first session creation so parallel code editors cannot acquire different owners.
    fun request(request: JsonObject): JsonElement {
        check(!closed) { "Editor service client is closed" }
        val path = request.get("url")?.asString ?: error("Editor API path is required")
        val method = request.get("method")?.asString?.uppercase() ?: "GET"
        validatePath(appName, path, method)
        return if (synchronized(cookies) { cookies.isEmpty() }) synchronized(ownerLock) {
            check(!closed) { "Editor service client is closed" }
            send(path, method, request.get("data"))
        } else send(path, method, request.get("data"))
    }

    private fun send(path: String, method: String, data: JsonElement? = null): JsonElement {
        val body = if (method in listOf("POST", "PUT", "PATCH"))
            (data?.toString() ?: "{}").toRequestBody("application/json".toMediaType()) else null
        val url = (baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
        if (method == "GET" && data?.isJsonObject == true) {
            fun append(name: String, value: JsonElement) {
                when {
                    value.isJsonNull -> Unit
                    value.isJsonArray -> value.asJsonArray.forEach { append(if (name.endsWith("[]")) name else "$name[]", it) }
                    value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, item) -> append("$name[$key]", item) }
                    else -> url.addQueryParameter(name, value.asString)
                }
            }
            data.asJsonObject.entrySet().forEach { (name, value) -> append(name, value) }
        }
        val builder = Request.Builder().url(url.build()).method(method, body)
            .header("Accept", "application/json")
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        else if (devKey.isNotBlank()) builder.header("X-TideStack-Dev-Key", devKey)
        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "Editor service HTTP ${response.code}" + if (response.code == 401) ": development key or editor token is invalid or expired" else "" }
            val payload = gson.fromJson(response.body?.string() ?: error("Empty editor response"), JsonElement::class.java)
            val envelope = payload.asJsonObject
            val status = envelope.get("status") ?: envelope.get("code") ?: error("Invalid editor service response")
            envelope.add("status", status)
            if (method == "POST" && path.endsWith("/sessions") && payload.asJsonObject.get("status")?.asInt == 0) {
                val id = payload.asJsonObject.getAsJsonObject("data")?.get("sessionId")?.asString
                if (id != null) {
                    val sessionPath = "$path/$id"
                    val closeImmediately = synchronized(lifecycleLock) {
                        if (closed) true else { sessions.add(sessionPath); false }
                    }
                    if (closeImmediately) send(sessionPath, "DELETE")
                }
            }
            if (method == "DELETE") sessions.remove(path)
            return payload
        }
    }

    override fun close() {
        val closingSessions = synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            client.dispatcher.cancelAll()
            sessions.toList().also { sessions.clear() }
        }
        closingSessions.forEach { path ->
            val builder = Request.Builder().url(baseUrl.trimEnd('/') + path).delete()
            if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            else if (devKey.isNotBlank()) builder.header("X-TideStack-Dev-Key", devKey)
            client.newCall(builder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, error: java.io.IOException) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(EditorServiceClient::class.java).warn("Could not close editor language session", error)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) com.intellij.openapi.diagnostic.Logger.getInstance(EditorServiceClient::class.java)
                            .warn("Could not close editor language session: HTTP ${it.code}")
                    }
                }
            })
        }
    }

    companion object {
        fun load(projectRoot: String, documentPath: String): EditorServiceClient? {
            val root = File(projectRoot).canonicalFile.toPath()
            var directory = File(documentPath).canonicalFile.parentFile
            while (directory != null && directory.toPath().startsWith(root)) {
                val projectFile = File(directory, TideStackDevelopConfigContracts.projectPath)
                if (projectFile.isFile) {
                    val gson = Gson()
                    val project = gson.fromJson(projectFile.readText(), JsonObject::class.java)
                    val localFile = File(directory, TideStackDevelopConfigContracts.localPath)
                    val local = if (localFile.isFile) gson.fromJson(localFile.readText(), JsonObject::class.java) else JsonObject()
                    fun value(source: JsonObject, name: String) = source.get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    val url = value(local, "editorServiceUrl").ifBlank { value(project, "editorServiceUrl") }
                        .ifBlank { TideStackDevelopConfigContracts.readProjectValue(project, "developmentServerUrl").orEmpty() }
                    if (url.isBlank()) return null
                    return EditorServiceClient(value(project, "appName"), url, value(local, "editorAccessToken"),
                        TideStackDevelopConfigContracts.readLocalValue(local, "devKey").orEmpty())
                }
                directory = directory.parentFile
            }
            return null
        }

        fun validatePath(appName: String, path: String, method: String) {
            require(path.startsWith('/') && !path.startsWith("//") && !Regex("[\\\\#]|%2e|%2f|%5c", RegexOption.IGNORE_CASE).containsMatchIn(path)
                && path.split('/', '?').none { it == "." || it == ".." }) { "Invalid editor API path" }
            val route = path.substringBefore('?')
            val dev = "/api/dev/$appName/"
            val app = "/api/app/$appName/"
            val allowed = if (route.startsWith(dev)) {
                val suffix = route.removePrefix(dev)
                when (method) {
                    "POST" -> Regex("code-intelligence/(context|sessions|sessions/[A-Za-z0-9-]+/(messages|context))").matches(suffix)
                    "GET" -> Regex("code-intelligence/(environment|sessions/[A-Za-z0-9-]+/notifications)|logicflow/node-define|approval-workflow/(candidate(/[^/]+/(items|category))?|form-fields)").matches(suffix)
                    "DELETE" -> Regex("code-intelligence/sessions/[A-Za-z0-9-]+").matches(suffix)
                    else -> false
                }
            } else route.startsWith(app) && method == "GET" && Regex("field-types/(primary|normal|[^/]+/ui-schema)").matches(route.removePrefix(app))
            require(allowed) { "Unsupported editor API or application" }
        }
    }
}
