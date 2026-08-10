package com.ouroboros.idea.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetadataDocumentSaveCoordinatorTest {
    @Test
    fun `applies text when base revision still matches current revision`() {
        var text = "before"

        val outcome = MetadataDocumentSaveCoordinator.apply(
            attempt = MetadataDocumentSaveAttempt(text = "after", baseRevision = "7"),
            currentRevision = { "7" },
            writeText = { text = it }
        )

        assertEquals(MetadataDocumentSaveOutcome.APPLIED, outcome)
        assertEquals("after", text)
    }

    @Test
    fun `rejects stale base revision before writing text`() {
        var text = "external-change"

        val outcome = MetadataDocumentSaveCoordinator.apply(
            attempt = MetadataDocumentSaveAttempt(text = "stale-editor-text", baseRevision = "7"),
            currentRevision = { "8" },
            writeText = { text = it }
        )

        assertEquals(MetadataDocumentSaveOutcome.REVISION_CONFLICT, outcome)
        assertEquals("external-change", text)
    }

    @Test
    fun `rejects blank base revision before writing text`() {
        var text = "external-change"

        val outcome = MetadataDocumentSaveCoordinator.apply(
            attempt = MetadataDocumentSaveAttempt(text = "missing-revision-text", baseRevision = ""),
            currentRevision = { "8" },
            writeText = { text = it }
        )

        assertEquals(MetadataDocumentSaveOutcome.REVISION_CONFLICT, outcome)
        assertEquals("external-change", text)
    }
}
