package com.ouroboros.idea.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetadataFileEditorContractTest {
    @Test
    fun `declares file association for IDEA document history`() {
        val getFile = MetadataFileEditor::class.java.getMethod("getFile")

        assertEquals(MetadataFileEditor::class.java, getFile.declaringClass)
    }
}
