package com.ouroboros.idea.metadata

internal data class MetadataDocumentSaveAttempt(
    val text: String,
    val baseRevision: String
)

internal enum class MetadataDocumentSaveOutcome {
    APPLIED,
    REVISION_CONFLICT
}

internal object MetadataDocumentSaveCoordinator {
    fun apply(
        attempt: MetadataDocumentSaveAttempt,
        currentRevision: () -> String,
        writeText: (String) -> Unit
    ): MetadataDocumentSaveOutcome {
        if (attempt.baseRevision != currentRevision()) {
            return MetadataDocumentSaveOutcome.REVISION_CONFLICT
        }
        writeText(attempt.text)
        return MetadataDocumentSaveOutcome.APPLIED
    }
}
