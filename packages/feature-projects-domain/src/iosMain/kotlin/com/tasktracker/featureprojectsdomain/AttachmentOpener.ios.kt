package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController

/**
 * iOS opener: a scratch copy in the temporary directory, handed to the document interaction
 * controller's "Open in" menu.
 *
 * ## Why `presentOpenInMenu` and not a preview
 *
 * The press means "open this file", and on iOS that is a hand-off to whichever app owns the
 * type — Pages for a document, Photos for an image. `presentOpenInMenu` is exactly that menu.
 * `presentOptionsMenu` would add Quick Look preview on top, which needs a
 * `UIDocumentInteractionControllerDelegate` to be useful; that is a previewer, and a previewer
 * is a different feature from an opener.
 *
 * ## Why the controller is held
 *
 * UIKit does not retain a `UIDocumentInteractionController` for you, and the menu is
 * dismissed asynchronously. A controller released the moment [open] returns is the silent
 * "nothing happened" the port's KDoc refuses — so the reference lives on the opener until the
 * next open replaces it.
 *
 * ## Why the temporary directory
 *
 * `NSTemporaryDirectory()` is the iOS counterpart of Android's `cacheDir`: reclaimable by the
 * system, and disposable because the blob store holds the original. The copy is keyed by the
 * display name and overwritten, so repeated opens do not accumulate.
 *
 * The file is named by `UIDocumentInteractionController.name` too, because the "Open in" menu
 * shows that string and a UUID-shaped scratch name would be the wrong thing to show.
 */
actual fun createAttachmentOpener(host: Any?): AttachmentOpener = IosAttachmentOpener()

private class IosAttachmentOpener : AttachmentOpener {

    /** Held across the presentation; see the class KDoc. */
    private var activeController: UIDocumentInteractionController? = null

    // `view.bounds` is a `CValue<CGRect>`, so the presentation needs the cinterop opt-in even
    // though nothing here touches memory directly.
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun open(fileName: String, mimeType: String?, bytes: ByteArray) {
        // UIKit must be touched on the main queue. The scratch write is file I/O, so the two
        // halves run on different dispatchers rather than making the main queue wait for a
        // copy of up to 25 MiB.
        val url = withContext(Dispatchers.Default) { writeScratchCopy(fileName, bytes) }
        val name = handOffFileName(fileName)
        withContext(Dispatchers.Main) {
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
                ?: throw AppError.Unknown("No window is available to open $name from.")
            val view = root.view
                ?: throw AppError.Unknown("No window is available to open $name from.")
            val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
            controller.name = name
            // `UTI` is what decides which apps the menu offers; `mimeType` is the closest
            // thing this app stored, and a `null` leaves UIKit to infer it from the extension.
            controller.UTI = mimeType
            activeController = controller
            val presented = controller.presentOpenInMenuFromRect(
                rect = view.bounds,
                inView = view,
                animated = true,
            )
            if (!presented) {
                // Returned, not thrown, when no installed app claims the type.
                activeController = null
                throw AppError.Unknown("No app on this device can open $name.")
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeScratchCopy(fileName: String, bytes: ByteArray): NSURL {
        val directory = NSTemporaryDirectory() + SCRATCH_DIRECTORY
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val name = handOffFileName(fileName)
        val path = "$directory/$name"
        val written = bytes.toNSData().writeToFile(path, atomically = true)
        if (!written) {
            // Not `Storage`: the user's file is already saved. This is a copy of it.
            throw AppError.Unknown("Could not prepare $name to open.")
        }
        return NSURL.fileURLWithPath(path)
    }

    private companion object {
        /** Must match the directory [writeScratchCopy] creates. */
        const val SCRATCH_DIRECTORY = "/tasktracker-attachments"
    }
}

/**
 * `NSData` from these bytes, by copy.
 *
 * `NSData.create(bytes:length:)` copies, which is what this needs: the pinned array is
 * released when `usePinned` returns, and a non-copying wrapper would leave `NSData` pointing
 * at freed memory. The same `bytes` + `memcpy` route `FilePicker.ios` documents, because
 * `NSData.getBytes` is absent from this Kotlin version's projection.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
