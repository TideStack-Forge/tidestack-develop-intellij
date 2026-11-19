package com.ouroboros.idea.metadata

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture

class EditorServiceClientTest {
    @Test fun `devKey alone authenticates editor requests and cleanup without a second credential`(@TempDir root: Path) {
        val requests = java.util.concurrent.LinkedBlockingQueue<List<String?>>()
        val deleted = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(listOf(exchange.requestHeaders.getFirst("X-TideStack-Dev-Key"),
                exchange.requestHeaders.getFirst("Authorization"), exchange.requestHeaders.getFirst("Cookie")))
            exchange.responseHeaders.add("Set-Cookie", "JSESSIONID=owner; Path=/; HttpOnly")
            val body = """{"code":0,"data":{"sessionId":"session-1"}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            if (exchange.requestMethod == "DELETE") deleted.countDown()
        }
        server.start()
        try {
            val config = root.resolve(".tidestack").toFile().also { it.mkdirs() }
            config.resolve("develop.json").writeText("""{"appName":"app","developmentServerUrl":"http://127.0.0.1:${server.address.port}"}""")
            config.resolve("develop.local.json").writeText("""{"devKey":"private-key"}""")
            requireNotNull(EditorServiceClient.load(root.toString(), root.resolve("model.ui-schema.json").toString())).use { client ->
                client.request(JsonParser.parseString("""{"url":"/api/dev/app/code-intelligence/sessions","method":"POST"}""").asJsonObject)
                client.request(JsonParser.parseString("""{"url":"/api/dev/app/logicflow/node-define"}""").asJsonObject)
            }
            assertTrue(deleted.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("private-key", null, null), requests.poll())
            assertEquals(listOf("private-key", null, "JSESSIONID=owner"), requests.poll())
            assertEquals(listOf("private-key", null, "JSESSIONID=owner"), requests.poll())
        } finally { server.stop(0) }
    }

    @Test fun `retains host credentials and session owner and releases language sessions`() {
        val requests = mutableListOf<List<String?>>()
        val deleted = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/base/") { exchange ->
            synchronized(requests) { requests.add(listOf(exchange.requestURI.path, exchange.requestMethod,
                exchange.requestHeaders.getFirst("Cookie"), exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestHeaders.getFirst("X-TideStack-Dev-Key"))) }
            val open = exchange.requestURI.path.endsWith("/sessions")
            if (open) exchange.responseHeaders.add("Set-Cookie", "JSESSIONID=owner; Path=/base; HttpOnly")
            val body = if (open) """{"code":0,"data":{"sessionId":"session-1"}}""" else """{"code":0,"data":{"messages":[]}}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
            if (exchange.requestMethod == "DELETE") deleted.countDown()
        }
        server.start()
        try {
            val client = EditorServiceClient("app", "http://127.0.0.1:${server.address.port}/base", "private-token", "overridden-key")
            client.request(JsonParser.parseString("""{"url":"/api/dev/app/code-intelligence/sessions","method":"POST","data":{"draft":"unsaved"}}""").asJsonObject)
            client.request(JsonParser.parseString("""{"url":"/api/dev/app/code-intelligence/sessions/session-1/notifications"}""").asJsonObject)
            client.close()
            assertTrue(deleted.await(5, TimeUnit.SECONDS))
            synchronized(requests) {
                assertEquals(3, requests.size)
                assertTrue(requests.all { it[3] == "Bearer private-token" })
                assertTrue(requests.all { it[4] == null })
                assertEquals("JSESSIONID=owner", requests[1][2])
                assertEquals("DELETE", requests[2][1])
            }
        } finally { server.stop(0) }
    }
    @Test fun `rejects arbitrary hosts traversal writes and other applications`() {
        for (path in listOf("http://evil/steal", "//evil/steal", "/api/dev/other/code-intelligence/context",
            "/api/dev/app/../other/code-intelligence/context", "/api/dev/app/%2e%2e/steal", "/api/dev/app/deploy")) {
            assertThrows(IllegalArgumentException::class.java) { EditorServiceClient.validatePath("app", path, "POST") }
        }
        EditorServiceClient.validatePath("app", "/api/dev/app/code-intelligence/sessions/id-1/messages", "POST")
        assertThrows(IllegalArgumentException::class.java) { EditorServiceClient.validatePath("app", "/api/dev/app/logicflow/node-define", "DELETE") }
    }

    @Test fun `GET search preserves existing query selected values and unicode`() {
        val query = java.util.concurrent.LinkedBlockingQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            query.add(exchange.requestURI.rawQuery.orEmpty())
            val body = """{"code":0,"data":{"items":[]}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            EditorServiceClient("app", "http://127.0.0.1:${server.address.port}", "token").use { client ->
                client.request(JsonParser.parseString("""{"url":"/api/dev/app/approval-workflow/candidate/User/items?existing=kept","data":{"keyword":"Alice & Bob","value":["user-1","user-2"],"category":"部门"}}""").asJsonObject)
                val parameters = query.poll(2, TimeUnit.SECONDS).split('&').map {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }.toSet()
                assertEquals(setOf("existing=kept", "keyword=Alice & Bob", "value[]=user-1", "value[]=user-2", "category=部门"), parameters)
            }
        } finally { server.stop(0) }
    }

    @Test fun `parallel sessions share an owner while separate editors keep their cookies isolated`() {
        var nextOwner = 0
        var nextSession = 0
        val owners = mutableMapOf<String, String>()
        val violations = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val deleted = CountDownLatch(3)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = if (exchange.requestMethod == "POST") {
                val owner = exchange.requestHeaders.getFirst("Cookie") ?: "JSESSIONID=owner-${++nextOwner}"
                val id = "session-${++nextSession}"
                owners[id] = owner
                exchange.responseHeaders.add("Set-Cookie", "$owner; Path=/; HttpOnly")
                """{"code":0,"data":{"sessionId":"$id"}}"""
            } else {
                val id = exchange.requestURI.path.substringAfterLast('/')
                if (owners[id] != exchange.requestHeaders.getFirst("Cookie")) violations.add(id)
                """{"code":0}"""
            }
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
            if (exchange.requestMethod == "DELETE") deleted.countDown()
        }
        server.start()
        val first = EditorServiceClient("app", "http://127.0.0.1:${server.address.port}", "token")
        val second = EditorServiceClient("app", "http://127.0.0.1:${server.address.port}", "token")
        try {
            val open = """{"url":"/api/dev/app/code-intelligence/sessions","method":"POST"}"""
            CompletableFuture.allOf(*listOf(first, first, second).map { client ->
                CompletableFuture.runAsync { client.request(JsonParser.parseString(open).asJsonObject) }
            }.toTypedArray()).get(10, TimeUnit.SECONDS)
            first.close(); second.close()
            assertTrue(deleted.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(1, 2), owners.values.groupingBy { it }.eachCount().values.sorted())
            assertTrue(violations.isEmpty(), "Language sessions must retain their original HTTP owner")
        } finally { first.close(); second.close(); server.stop(0) }
    }
}
