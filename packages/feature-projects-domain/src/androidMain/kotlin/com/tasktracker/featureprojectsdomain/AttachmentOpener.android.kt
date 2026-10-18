package com.tasktracker.featureprojectsdomain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.tasktracker.core.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android opener: a scratch copy in the cache directory, exposed through a `FileProvider`,
 * handed to `ACTION_VIEW`.
 *
 * ## Why a provider and not the file
 *
 * Since API 24 a `file://` URI in an `Intent` throws `FileUriExposedException`: a receiving
 * app cannot be trusted with a path into this app's private storage. A `content://` URI from
 * `FileProvider` is the supported replacement, and it grants read access to exactly one file
 * for exactly one receiving app. That is why the provider is declared in this module's own
 * manifest — the code and its requirement travel together, the same way `:packages:network`
 * ships the permission its connectivity callback needs.
 *
 * ## Why the cache directory
 *
 * `cacheDir` is where a hand-off copy belongs: the system may reclaim it under storage
 * pressure, and the copy is disposable by construction — the blob store holds the original.
 * A `filesDir` copy would be a second copy the app is responsible for deleting.
 *
 * The copy is keyed by the display name and overwritten, so tapping the same file twice does
 * not leave two copies behind. Two *different* attachments with the same name collide, which
 * is harmless: each open writes its own bytes before launching.
 */
actual fun createAttachmentOpener(host: Any?): AttachmentOpener {
    val context = host as? Context
        ?: throw AppError.Unknown(
            "Android needs an application Context to open files. Pass one to " +
                "AttachmentOpener creation.",
        )
    return AndroidAttachmentOpener(context.applicationContext)
}

private class AndroidAttachmentOpener(private val context: Context) : AttachmentOpener {

    override suspend fun open(fileName: String, mimeType: String?, bytes: ByteArray) {
        val file = withContext(Dispatchers.IO) { writeScratchCopy(fileName, bytes) }
        // A `null` type would make `ACTION_VIEW` match nothing, so the extension is the
        // fallback and `*/*` is the last resort — a picker with more apps in it beats a
        // press that silently matches no activity.
        val type = mimeType ?: guessMimeType(file.name) ?: "*/*"
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        } catch (failure: IllegalArgumentException) {
            // Thrown when no provider holds that authority, or when the path is outside the
            // roots it declares. Both mean this build cannot hand the file over at all.
            throw AppError.Unknown(
                "This build cannot hand ${file.name} to another app. ${failure.message.orEmpty()}",
            )
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            // The receiving app gets read access to this one URI, and nothing else.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Required because the launch comes from the application context rather than
            // from a task: without it the system has no task to put the viewer in.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (failure: ActivityNotFoundException) {
            throw AppError.Unknown("No app on this device can open ${file.name}.")
        }
    }

    private fun writeScratchCopy(fileName: String, bytes: ByteArray): File {
        val directory = File(context.cacheDir, SCRATCH_DIRECTORY)
        val target = File(directory, handOffFileName(fileName))
        try {
            directory.mkdirs()
            target.writeBytes(bytes)
        } catch (failure: Exception) {
            // Not `Storage`: the user's file is already saved. This is a copy of it.
            throw AppError.Unknown(
                "Could not prepare ${target.name} to open. ${failure.message.orEmpty()}",
            )
        }
        return target
    }

    private companion object {
        /** Must match the `path` in `res/xml/attachment_handoff_paths.xml`. */
        const val SCRATCH_DIRECTORY = "attachments"

        /** Must match `android:authorities` in this module's `AndroidManifest.xml`. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
