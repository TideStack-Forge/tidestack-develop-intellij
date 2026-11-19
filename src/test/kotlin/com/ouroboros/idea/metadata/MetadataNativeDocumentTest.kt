package com.ouroboros.idea.metadata

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class MetadataNativeDocumentTest : BasePlatformTestCase() {
    fun testNativeDocumentConflictUndoRedoAndSave() {
        val corpus = Gson().fromJson(Files.readString(Path.of("testing/common/fixtures/data/ide-metadata-workspace/documents.json")), JsonObject::class.java)
        val original = corpus.getAsJsonObject("menu").get("value").toString()
        val file = myFixture.addFileToProject("src/main/metadata/menu.menu.json", original).virtualFile
        val documents = FileDocumentManager.getInstance()
        val document = requireNotNull(documents.getDocument(file))
        val editor = requireNotNull(FileEditorManager.getInstance(project).openFile(file, true).firstOrNull())
        val initialRevision = document.modificationStamp.toString()
        assertFalse(documents.isDocumentUnsaved(document))
        val value = corpus.getAsJsonObject("menu").getAsJsonObject("value").deepCopy()
        value.addProperty("title", "Native saved")
        val updated = value.toString()
        var result: MetadataDocumentSaveOutcome? = null
        WriteCommandAction.runWriteCommandAction(project) {
            result = MetadataDocumentSaveCoordinator.apply(
                MetadataDocumentSaveAttempt(updated, initialRevision),
                { document.modificationStamp.toString() }, { document.setText(it) }
            )
        }
        assertEquals(MetadataDocumentSaveOutcome.APPLIED, result)
        assertEquals(updated, document.text)
        assertTrue(documents.isDocumentUnsaved(document))
        WriteCommandAction.runWriteCommandAction(project) {
            result = MetadataDocumentSaveCoordinator.apply(
                MetadataDocumentSaveAttempt(original, initialRevision),
                { document.modificationStamp.toString() }, { document.setText(it) }
            )
        }
        assertEquals(MetadataDocumentSaveOutcome.REVISION_CONFLICT, result)
        assertEquals(updated, document.text)
        val undo = UndoManager.getInstance(project)
        assertTrue(undo.isUndoAvailable(editor))
        undo.undo(editor)
        assertEquals(original, document.text)
        assertTrue(undo.isRedoAvailable(editor))
        undo.redo(editor)
        assertEquals(updated, document.text)
        documents.saveDocument(document)
        assertFalse(documents.isDocumentUnsaved(document))
        assertEquals(updated, String(file.contentsToByteArray(), Charsets.UTF_8))
    }
}
