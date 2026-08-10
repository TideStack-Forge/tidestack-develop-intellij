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
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.UIUtil
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
    private val metadataType: String
) : UserDataHolderBase(), FileEditor {
    private val gson = Gson()
    private val panel = JPanel(BorderLayout())
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private var fallbackEditor: Editor? = null
    private var browser: JBCefBrowser? = null
    private var saveQuery: JBCefJSQuery? = null
    private var readyQuery: JBCefJSQuery? = null
    private var diagnosticsLabel: JLabel? = null
    private var watchedDocument: Document? = null
    private var documentListener: DocumentListener? = null
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
            save.addHandler { request -> handleSave(document, request) }
            ready.addHandler { handleReady(document) }
            saveQuery = save
            readyQuery = ready
            val page = MetadataEditorAssets.materializePage(
                MetadataEditorHtml.render(
                    assets = MetadataEditorAssets.locate(project, file),
                    uri = file.path,
                    metadataType = metadataType,
                    initialDocumentJson = toBrowserDocumentJson(text, document),
                    initialTheme = currentAmisTheme(),
                    locale = TideStackMessages.currentLocale(),
                    saveScript = save.inject(
                        "JSON.stringify({text: text, revision: revision})",
                        "function(response) { resolve(JSON.parse(response)); }",
                        "function(code, message) { const error = new Error(message); error.code = code === 409 ? 'revision-conflict' : 'host-save'; reject(error); }"
                    ),
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
        documentListener?.let { listener -> watchedDocument?.removeDocumentListener(listener) }
        fallbackEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
        saveQuery?.dispose()
        readyQuery?.dispose()
        browser?.dispose()
    }

    private fun watchDocument(document: Document) {
        watchedDocument = document
        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                publishDocumentState(document)
            }
        }
        document.addDocumentListener(listener)
        documentListener = listener
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

    private fun currentAmisTheme(): String = if (UIUtil.isUnderDarcula()) "dark" else "cxd"

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
