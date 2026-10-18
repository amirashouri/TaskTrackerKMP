package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.IOException

/**
 * Desktop opener: a scratch copy in the temp directory, handed to `java.awt.Desktop`.
 *
 * `Desktop` takes a `File`, not bytes, so the copy is unavoidable — and it is a *copy* on
 * purpose. Writing the blob store's own file would put the app's cache under another
 * application's control: a viewer that decides to rewrite what it opened would corrupt the
 * cache, and one that keeps the handle open would block a later delete.
 *
 * The whole open runs on [Dispatchers.IO] because both halves block: the write is a file
 * write, and `Desktop.open` shells out to the platform's launcher and waits for it to be
 * accepted. Neither may run on the Swing EDT.
 *
 * One file per name, overwritten on each open, so repeated taps on the same attachment do
 * not accumulate copies in the temp directory.
 */
actual fun createAttachmentOpener(host: Any?): AttachmentOpener = JvmAttachmentOpener()

private class JvmAttachmentOpener : AttachmentOpener {

    override suspend fun open(fileName: String, mimeType: String?, bytes: ByteArray) {
        // `mimeType` is unused here and that is not an oversight: Desktop's hand-off is
        // decided by the file's extension, so the recorded type could only ever disagree
        // with the name it was stored beside.
        val file = withContext(Dispatchers.IO) { writeScratchCopy(fileName, bytes) }
        withContext(Dispatchers.IO) { openWithDesktop(file) }
    }

    private fun writeScratchCopy(fileName: String, bytes: ByteArray): File {
        val directory = File(System.getProperty("java.io.tmpdir"), SCRATCH_DIRECTORY)
        val target = File(directory, handOffFileName(fileName))
        try {
            directory.mkdirs()
            target.writeBytes(bytes)
        } catch (failure: Exception) {
            // `Storage` would say "Could not save your changes", which is not what failed:
            // the user's file is already saved. What could not be prepared is a copy of it.
            throw AppError.Unknown(
                "Could not prepare ${target.name} to open. ${failure.message.orEmpty()}",
            )
        }
        return target
    }

    private fun openWithDesktop(file: File) {
        // `isDesktopSupported` is false on a headless JVM — a server, a CI run, a test. The
        // check comes first because `getDesktop()` throws `HeadlessException` there.
        if (!Desktop.isDesktopSupported()) {
            throw AppError.Unknown("This system has no desktop to open ${file.name} with.")
        }
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw AppError.Unknown("This system cannot open ${file.name}.")
        }
        try {
            desktop.open(file)
        } catch (failure: IOException) {
            // Thrown when the platform has no association for the extension — the desktop
            // equivalent of "no app can open this".
            throw AppError.Unknown(
                "No application here can open ${file.name}. ${failure.message.orEmpty()}",
            )
        }
    }

    private companion object {
        const val SCRATCH_DIRECTORY = "tasktracker-attachments"
    }
}
