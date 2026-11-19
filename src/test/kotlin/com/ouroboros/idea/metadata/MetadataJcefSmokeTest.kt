package com.ouroboros.idea.metadata

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame

/** Separate graphical lane: unsupported JCEF is a failure, never a passing text fallback. */
class MetadataJcefSmokeTest : BasePlatformTestCase() {
    fun testBusinessBeforeAndAfterScriptsUseMonacoWithoutAnOnlineService() {
        assertTrue("JCEF-capable JBR and a graphical session are required", JBCefApp.isSupported())
        val gson = Gson()
        val corpus = gson.fromJson(Files.readString(Path.of("testing/common/fixtures/data/ide-metadata-workspace/documents.json")), JsonObject::class.java)
        val original = corpus.getAsJsonObject("business").getAsJsonObject("value")
        val browser = JBCefBrowser()
        val frame = JFrame("Business script JCEF regression")
        val probe = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val result = AtomicReference<String>()
        val saved = AtomicReference<JsonObject>()
        probe.addHandler { payload -> result.set(payload); JBCefJSQuery.Response("{}") }
        val save = JBCefJSQuery.create(browser as JBCefBrowserBase)
        save.addHandler { request ->
            saved.set(gson.fromJson(gson.fromJson(request, JsonObject::class.java).get("text").asString, JsonObject::class.java))
            JBCefJSQuery.Response("{\"revision\":\"saved\"}")
        }
        try {
            frame.contentPane.add(browser.component)
            frame.setSize(1280, 900)
            frame.isVisible = true
            val html = MetadataEditorHtml.render(
                assets = MetadataEditorAssets.locateWithRoots(Path.of("build/resources/main/ouroboros-metadata-editor").toAbsolutePath(), false, "."),
                uri = "file:///fixture/src/main/metadata/fixture.business.json", schemaName = "business",
                initialDocumentJson = gson.toJson(mapOf("text" to original.toString(), "revision" to "1")),
                initialTheme = "cxd", locale = "zh-CN",
                saveScript = "window.jcefSavedMetadata = JSON.parse(text); " + save.inject("JSON.stringify({text, revision})", "response => resolve(JSON.parse(response))", "(code, message) => reject(new Error(message))"),
                readyScript = probe.inject("'ready'", "() => {}", "(code, message) => console.error(message)")
            )
            val page = MetadataEditorAssets.materializePage(html)
            try {
                browser.loadURL(page.toUri().toString())
                PlatformTestUtil.waitWithEventsDispatching("business JCEF ready", {result.get() == "ready"}, 45000)
                result.set(null)
                val script = """
                    (async () => {
                      const mounted = [];
                      window.__OUROBOROS_SCRIPT_EDITOR_DEBUG__ = {onMounted: value => mounted.push(value)};
                      const until = async read => {
                        for (let i = 0; i < 200; i++) { const value = read(); if (value) return value; await new Promise(resolve => setTimeout(resolve, 50)); }
                        throw new Error('Business code editor did not mount');
                      };
                      const tab = await until(() => Array.from(document.querySelectorAll('.cxd-Tabs-link-text')).find(el => el.textContent === '接口配置'));
                      tab.click();
                      for (const [phase, label, language] of [['before', '前', 'groovy'], ['after', '后', 'javascript']]) {
                        const heading = await until(() => Array.from(document.querySelectorAll('legend')).find(el => el.textContent.trim() === '查询执行' + label + '脚本'));
                        heading.click();
                        const control = await until(() => mounted.find(value => value.props.name === 'apis.search.' + phase + 'Script'));
                        if (control.editor.getModel().getLanguageId() !== language) throw new Error('Wrong code language');
                        if (!control.editor.getDomNode().getBoundingClientRect().height) throw new Error('Code editor is invisible');
                        const code = 'return "JCEF-' + phase + '"';
                        control.editor.setValue(code);
                        await until(() => window.jcefSavedMetadata?.apis.search[phase + 'Script'] === code);
                      }
                      await window.OuroborosMetadataEditorApp.flushPendingChanges();
                      ${probe.inject("'scripts-saved'", "() => {}", "(code, message) => console.error(message)")}
                    })().catch(error => { ${probe.inject("String(error)", "() => {}", "(code, message) => console.error(message)")} });
                """.trimIndent()
                browser.cefBrowser.executeJavaScript(script, page.toUri().toString(), 0)
                PlatformTestUtil.waitWithEventsDispatching("JCEF business Monaco and save", {result.get() != null}, 30000)
                assertEquals("scripts-saved", result.get())
                val search = requireNotNull(saved.get()).getAsJsonObject("apis").getAsJsonObject("search")
                assertEquals("return \"JCEF-before\"", search.get("beforeScript").asString)
                assertEquals("return \"JCEF-after\"", search.get("afterScript").asString)
                assertEquals(original.get("custom"), saved.get().get("custom"))
            } finally { Files.deleteIfExists(page) }
        } finally {
            save.dispose(); probe.dispose(); browser.dispose(); frame.dispose()
        }
    }

    fun testProductionPageLoadsAndSavesThroughRealJcefQuery() {
        assertTrue("JCEF-capable JBR and a graphical session are required", JBCefApp.isSupported())
        val gson = Gson()
        val corpus = gson.fromJson(Files.readString(Path.of("testing/common/fixtures/data/ide-metadata-workspace/documents.json")), JsonObject::class.java)
        val original = corpus.getAsJsonObject("page").get("value").toString()
        val file = myFixture.addFileToProject("src/main/metadata/page.ui-schema.json", original).virtualFile
        val documents = FileDocumentManager.getInstance()
        val document = requireNotNull(documents.getDocument(file))
        val browser = JBCefBrowser()
        val frame = JFrame("Metadata JCEF smoke")
        val probe = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val result = AtomicReference<String>()
        probe.addHandler { payload -> result.set(payload); JBCefJSQuery.Response("{}") }
        val save = JBCefJSQuery.create(browser as JBCefBrowserBase)
        save.addHandler { request ->
            val payload = gson.fromJson(request, JsonObject::class.java)
            var outcome: MetadataDocumentSaveOutcome? = null
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                WriteCommandAction.runWriteCommandAction(project) {
                    outcome = MetadataDocumentSaveCoordinator.apply(
                        MetadataDocumentSaveAttempt(payload.get("text").asString, payload.get("revision").asString),
                        { document.modificationStamp.toString() }, { document.setText(it) }
                    )
                }
            }
            if (outcome == MetadataDocumentSaveOutcome.REVISION_CONFLICT) JBCefJSQuery.Response(null, 409, "revision-conflict")
            else JBCefJSQuery.Response(gson.toJson(mapOf("revision" to document.modificationStamp.toString())))
        }
        try {
            frame.contentPane.add(browser.component)
            frame.setSize(1280, 900)
            frame.isVisible = true
            val html = MetadataEditorHtml.render(
                assets = MetadataEditorAssets.locateWithRoots(Path.of("build/resources/main/ouroboros-metadata-editor").toAbsolutePath(), false, "."),
                uri = file.path, schemaName = "ui-schema",
                initialDocumentJson = gson.toJson(mapOf("text" to original, "revision" to document.modificationStamp.toString())),
                initialTheme = "cxd", locale = "zh-CN",
                saveScript = save.inject("JSON.stringify({text, revision})", "response => resolve(JSON.parse(response))", "(code, message) => reject(new Error(message))"),
                readyScript = probe.inject("'ready'", "() => {}", "(code, message) => console.error(message)")
            )
            val page = MetadataEditorAssets.materializePage(html)
            try {
                browser.loadURL(page.toUri().toString())
                PlatformTestUtil.waitWithEventsDispatching("production JCEF page ready", {result.get() == "ready"}, 45000)
                result.set(null)
                browser.cefBrowser.executeJavaScript(
                    "setTimeout(async () => { await window.OuroborosMetadataEditorApp.flushPendingChanges(); ${probe.inject("'settled'", "() => {}", "(code, message) => console.error(message)")} }, 800);",
                    page.toUri().toString(), 0
                )
                PlatformTestUtil.waitWithEventsDispatching("JCEF initialization settled", {result.get() == "settled"}, 15000)
                assertEquals(original, document.text)
                assertFalse(documents.isDocumentUnsaved(document))
                result.set(null)
                // Interact with the rendered field, then let the production runtime flush its pending save.
                val script = """
                    (async () => {
                      const title = document.querySelector('.metadata-ui-schema-wrapper-input[name="title"]');
                      if (!title) throw new Error('Rendered title input missing');
                      title.value = 'JCEF saved'; title.dispatchEvent(new Event('input', {bubbles: true}));
                      await window.OuroborosMetadataEditorApp.flushPendingChanges();
                      ${probe.inject("'saved'", "() => {}", "(code, message) => console.error(message)")}
                    })().catch(error => { ${probe.inject("String(error)", "() => {}", "(code, message) => console.error(message)")} });
                """.trimIndent()
                browser.cefBrowser.executeJavaScript(script, page.toUri().toString(), 0)
                PlatformTestUtil.waitWithEventsDispatching("JCEF save roundtrip", {result.get() != null}, 15000)
                assertEquals("saved", result.get())
                val expected = corpus.getAsJsonObject("page").getAsJsonObject("value").deepCopy()
                expected.addProperty("title", "JCEF saved")
                assertEquals(expected, gson.fromJson(document.text, JsonObject::class.java))
                documents.saveDocument(document)
                assertEquals(document.text, String(file.contentsToByteArray(), Charsets.UTF_8))
            } finally { Files.deleteIfExists(page) }
        } finally {
            save.dispose(); probe.dispose(); browser.dispose(); frame.dispose()
        }
    }
}
