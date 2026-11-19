package com.ouroboros.idea.metadata

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.ouroboros.idea.TideStackMessages
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class MetadataFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val schemaName: String
) : UserDataHolderBase(), FileEditor {
    private val gson = Gson()
    private val panel = JPanel(BorderLayout())
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private var fallbackEditor: Editor? = null
    private var browser: JBCefBrowser? = null
    private var saveQuery: JBCefJSQuery? = null
    private var serviceQuery: JBCefJSQuery? = null
    private val service = runCatching { project.basePath?.let { EditorServiceClient.load(it, file.path) } }
    @Volatile private var disposed = false
    private var readyQuery: JBCefJSQuery? = null
    private var diagnosticsLabel: JLabel? = null
    private var lastModified = false

    init {
        val document = FileDocumentManager.getInstance().getDocument(file)
        val text = document?.text ?: String(file.contentsToByteArray(), Charsets.UTF_8)
        if (document != null) {
            watchDocument(document)
        }
        lastModified = isModified()

        if (JBCefApp.isSupported()) {
            val jcefBrowser = JBCefBrowser()
            val save = JBCefJSQuery.create(jcefBrowser as JBCefBrowserBase)
            val ready = JBCefJSQuery.create(jcefBrowser as JBCefBrowserBase)
            val online = JBCefJSQuery.create(jcefBrowser as JBCefBrowserBase)
            online.addHandler { raw ->
                ApplicationManager.getApplication().executeOnPooledThread {
                    val envelope = gson.fromJson(raw, com.google.gson.JsonObject::class.java)
                    val id = envelope.get("requestId").asString
                    val result = runCatching {
                        val client = service.getOrThrow() ?: error("Configure an editor service in .tidestack/develop.json")
                        client.request(envelope.getAsJsonObject("request"))
                    }
                    val response = result.fold(
                        { mapOf("ok" to true, "result" to it) },
                        { mapOf("ok" to false, "error" to (it.message ?: "Editor service request failed")) }
                    )
                    ApplicationManager.getApplication().invokeLater {
                        if (!disposed) jcefBrowser.cefBrowser.executeJavaScript(
                            "window.OuroborosEditorServiceComplete(${gson.toJson(id)}, ${gson.toJson(response)});", jcefBrowser.cefBrowser.url, 0)
                    }
                }
                JBCefJSQuery.Response("{}")
            }
            serviceQuery = online
            save.addHandler { request -> handleSave(document, request) }
            ready.addHandler { handleReady(document) }
            saveQuery = save
            readyQuery = ready
            val page = MetadataEditorAssets.materializePage(
                MetadataEditorHtml.render(
                    assets = MetadataEditorAssets.locate(project, file),
                    uri = file.path,
                    schemaName = schemaName,
                    initialDocumentJson = toBrowserDocumentJson(text, document),
                    initialTheme = currentAmisTheme(),
                    locale = TideStackMessages.currentLocale(),
                    saveScript = save.inject(
                        "JSON.stringify({text: text, revision: revision})",
                        "function(response) { resolve(JSON.parse(response)); }",
                        "function(code, message) { const error = new Error(message); error.code = code === 409 ? 'revision-conflict' : 'host-save'; reject(error); }"
                    ),
                    serviceAppName = service.getOrNull()?.appName,
                    serviceScript = online.inject("JSON.stringify({requestId, request})", "function() {}", "function(code, message) { reject(new Error(message)); }"),
                    readyScript = ready.inject(
                        "''",
                        "function() {}",
                        "function(code, message) { console.error(message); }"
                    )
                )
            )
            jcefBrowser.loadURL(page.toUri().toString())
            browser = jcefBrowser
            subscribeThemeChanges()
            panel.add(jcefBrowser.component, BorderLayout.CENTER)
        } else if (document != null) {
            val diagnostics = MetadataSchemaDiagnostics.validate(file.path, text)
            val editor = EditorFactory.getInstance().createEditor(document, project, file, false)
            val label = JLabel(formatDiagnostics(diagnostics))
            fallbackEditor = editor
            diagnosticsLabel = label
            panel.add(editor.component, BorderLayout.CENTER)
            panel.add(label, BorderLayout.SOUTH)
        } else {
            val diagnostics = MetadataSchemaDiagnostics.validate(file.path, text)
            panel.add(JLabel(formatDiagnostics(diagnostics)), BorderLayout.CENTER)
        }
    }

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent? = fallbackEditor?.contentComponent ?: browser?.component

    override fun getName(): String = "可视化编辑"

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = FileDocumentManager.getInstance().isFileModified(file)

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        disposed = true
        service.getOrNull()?.close()
        serviceQuery?.dispose()
        fallbackEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
        saveQuery?.dispose()
        readyQuery?.dispose()
        browser?.dispose()
    }

    private fun watchDocument(document: Document) {
        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                publishDocumentState(document)
            }
        }
        document.addDocumentListener(listener, this)
    }

    private fun publishDocumentState(document: Document) {
        diagnosticsLabel?.text = formatDiagnostics(MetadataSchemaDiagnostics.validate(file.path, document.text))
        val documentJson = toBrowserDocumentJson(document.text, document)
        browser?.cefBrowser?.executeJavaScript(
            "window.OuroborosMetadataEditorHost && window.OuroborosMetadataEditorHost.applyExternalDocument($documentJson);",
            browser?.cefBrowser?.url ?: file.url,
            0
        )
        fireModifiedChangeIfNeeded()
    }

    private fun subscribeThemeChanges() {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            LafManagerListener.TOPIC,
            LafManagerListener { publishThemeState() }
        )
    }

    private fun publishThemeState() {
        val theme = currentAmisTheme()
        browser?.cefBrowser?.executeJavaScript(
            "window.OuroborosMetadataEditorApp && window.OuroborosMetadataEditorApp.setTheme('$theme');",
            browser?.cefBrowser?.url ?: file.url,
            0
        )
    }

    private fun currentAmisTheme(): String = if (JBColor.isBright()) "cxd" else "dark"

    private fun handleReady(document: Document?): JBCefJSQuery.Response {
        if (document != null) {
            ApplicationManager.getApplication().invokeLater {
                if (isValid()) {
                    publishDocumentState(document)
                }
            }
        }
        return JBCefJSQuery.Response("{}")
    }

    private fun handleSave(document: Document?, request: String): JBCefJSQuery.Response {
        if (document == null) {
            return JBCefJSQuery.Response(null, 400, "Metadata document is unavailable.")
        }
        val saveRequest = try {
            gson.fromJson(request, SaveRequest::class.java)
        } catch (e: JsonSyntaxException) {
            return JBCefJSQuery.Response(null, 400, e.message ?: "Invalid metadata save request.")
        }
        val requestedText = saveRequest.text
        if (requestedText == null) {
            return JBCefJSQuery.Response(null, 400, "Metadata save request text is required.")
        }
        val requestRevision = saveRequest.revision
        if (requestRevision.isNullOrBlank()) {
            return JBCefJSQuery.Response(null, 400, "Metadata save request revision is required.")
        }
        val attempt = MetadataDocumentSaveAttempt(requestedText, requestRevision)
        var outcome: MetadataDocumentSaveOutcome? = null
        runWriteCommandOnEdt {
            outcome = MetadataDocumentSaveCoordinator.apply(
                attempt = attempt,
                currentRevision = { document.modificationStamp.toString() },
                writeText = { document.setText(it) }
            )
        }
        if (outcome == MetadataDocumentSaveOutcome.REVISION_CONFLICT) {
            return JBCefJSQuery.Response(null, 409, "Metadata document was modified outside the editor.")
        }
        fireModifiedChangeIfNeeded()
        return JBCefJSQuery.Response(gson.toJson(SaveResult(document.modificationStamp.toString())))
    }

    private fun runWriteCommandOnEdt(action: () -> Unit) {
        val writeCommand = Runnable {
            WriteCommandAction.runWriteCommandAction(project, Runnable { action() })
        }
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            writeCommand.run()
        } else {
            application.invokeAndWait(writeCommand)
        }
    }

    private fun fireModifiedChangeIfNeeded() {
        val modified = isModified()
        if (modified != lastModified) {
            val previous = lastModified
            lastModified = modified
            propertyChangeSupport.firePropertyChange(FileEditor.PROP_MODIFIED, previous, modified)
        }
    }

    private fun toBrowserDocumentJson(text: String, document: Document?): String {
        return gson.toJson(
            BrowserDocument(
                text = text,
                revision = document?.modificationStamp?.toString() ?: file.modificationStamp.toString()
            )
        )
    }

    private data class SaveRequest(
        val text: String? = null,
        val revision: String? = null
    )

    private data class SaveResult(val revision: String)

    private data class BrowserDocument(
        val text: String,
        val revision: String
    )

    private fun formatDiagnostics(diagnostics: List<MetadataSchemaDiagnostic>): String {
        if (diagnostics.isEmpty()) {
            return "Schema diagnostics: OK"
        }
        return diagnostics.joinToString("; ") { "${it.severity}: ${it.code}" }
    }
}
