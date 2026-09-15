package io.github.willywonka644.fintracker.sync

sealed class SyncError {
    data object InvalidJson : SyncError()
    data object UnknownVersion : SyncError()
    data class MissingField(val fieldName: String) : SyncError()
    data object WriteFailed : SyncError()
}

sealed class SyncImportResult {
    data class Error(val error: SyncError) : SyncImportResult()
    data class Preview(val preview: SyncPreview) : SyncImportResult()
}

sealed class SyncResult {
    data class Success(val newCount: Int, val updatedCount: Int) : SyncResult()
    data class Error(val error: SyncError) : SyncResult()
}
