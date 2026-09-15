package io.github.willywonka644.fintracker.storage

interface IAttachmentStorage {
    fun saveAttachment(bookingId: String, data: ByteArray): String
    fun loadAttachment(path: String): ByteArray?
    fun deleteAttachment(path: String)
}
