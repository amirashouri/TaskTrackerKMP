package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop picker: a modal [JFileChooser].
 *
 * `showOpenDialog` is modal and blocks its thread, so the whole pick runs on
 * [Dispatchers.IO] — blocking the Swing EDT with a native modal is exactly what freezes
 * a desktop app's repaint loop. The chooser itself is constructed and invoked there too;
 * Swing is not thread-safe, but a chooser used only from one background thread and
 * never touched by the EDT directly is the same contract every headless file-dialog
 * helper relies on.
 */
actual fun createFilePicker(host: Any?): FilePicker = JvmFilePicker()

private class JvmFilePicker : FilePicker {

    override suspend fun pick(): PickedFile? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            // No filter: the port attaches documents as well as images, and a filter that
            // guessed wrong would hide a file the domain would have accepted.
            dialogTitle = "Attach a file"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = true
            addChoosableFileFilter(FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "webp"))
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return@withContext null
        val file: File = chooser.selectedFile ?: return@withContext null
        val bytes = try {
            file.readBytes()
        } catch (failure: Exception) {
            throw AppError.Storage("Could not read ${file.name}. ${failure.message.orEmpty()}")
        }
        PickedFile(
            fileName = file.name,
            mimeType = guessMimeType(file.name),
            bytes = bytes,
        )
    }
}
