package io.github.willywonka644.fintracker.attachment

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Manages receipt/image attachment files in app-private storage.
 * Images are stored in context.filesDir/attachments/.
 */
object AttachmentStorage {

    private const val ATTACHMENTS_DIR = "attachments"

    /**
     * Returns the attachments directory, creating it if necessary.
     */
    fun getAttachmentsDir(context: Context): File {
        return File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
    }

    /**
     * Copies an image from a content URI to internal app storage.
     * Returns the absolute path of the stored file, or null on failure.
     */
    fun copyImageToStorage(
        context: Context,
        sourceUri: Uri,
        bookingId: String
    ): String? {
        return try {
            val dir = getAttachmentsDir(context)
            val fileName = "${bookingId}_${System.currentTimeMillis()}.jpg"
            val destFile = File(dir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a file for camera capture (used with FileProvider URI).
     */
    fun createCameraFile(context: Context, bookingId: String): File {
        val dir = getAttachmentsDir(context)
        val fileName = "${bookingId}_${System.currentTimeMillis()}.jpg"
        return File(dir, fileName)
    }

    /**
     * Deletes a single attachment file by its absolute path.
     */
    fun deleteAttachment(path: String?): Boolean {
        if (path == null) return false
        return try {
            File(path).delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks if an attachment file exists at the given path.
     */
    fun exists(path: String): Boolean = File(path).exists()
}
