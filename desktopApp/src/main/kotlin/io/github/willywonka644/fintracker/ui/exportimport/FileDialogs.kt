package io.github.willywonka644.fintracker.ui.exportimport

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Opens a save-file dialog and returns the chosen [File], or null if cancelled. */
fun showSaveDialog(title: String, extension: String, description: String): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter("$description (*.$extension)", extension)
        isAcceptAllFileFilterUsed = false
    }
    val result = chooser.showSaveDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    var file = chooser.selectedFile
    if (!file.name.endsWith(".$extension", ignoreCase = true)) {
        file = File(file.absolutePath + ".$extension")
    }
    return file
}

/** Opens an open-file dialog and returns the chosen [File], or null if cancelled. */
fun showOpenDialog(title: String, extension: String, description: String): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter("$description (*.$extension)", extension)
        isAcceptAllFileFilterUsed = false
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile
}
