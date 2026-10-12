package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * iOS picker: a `UIDocumentPickerViewController` for opening any item, as a copy.
 *
 * ## Why the presenting view controller is hunted, not injected
 *
 * The graph's host on iOS is whatever the shell passed at start — commonly `null` — and
 * unlike Android there is no lifecycle registry that tracks "the frontmost controller
 * right now". So the picker walks to the key window's root controller at call time; a
 * reference captured earlier is the classic UIKit crash ("presenting on a controller
 * that is not in the window hierarchy"), and a walk at call time cannot go stale — it
 * is computed from what is on screen at that instant.
 *
 * The delegate is held by the picker object across the suspension: UIKit's `delegate` is
 * a *weak* reference, and a delegate released mid-pick is the silent "nothing happens"
 * failure the port's KDoc refuses to allow.
 *
 * `asCopy = true` means the URL names a copy the app owns outright, so no security-
 * scoped access dance is needed — the one honest simplification this platform asks for,
 * paid for with a duplicate of the bytes in tmp for the pick's duration.
 */
actual fun createFilePicker(host: Any?): FilePicker = IosFilePicker()

private class IosFilePicker : FilePicker {

    /** UIKit holds `delegate` weakly; this strong reference is what keeps the pick alive. */
    private var activeDelegate: UIDocumentPickerDelegateProtocol? = null

    override suspend fun pick(): PickedFile? {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: throw AppError.Unknown("No window is available to present the file picker from.")

        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeItem),
            asCopy = true,
        )

        return suspendCancellableCoroutine { continuation ->
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentAtURL: NSURL,
                ) {
                    activeDelegate = null
                    continuation.resume(readPickedFile(didPickDocumentAtURL))
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    activeDelegate = null
                    continuation.resume(null)
                }
            }
            activeDelegate = delegate
            picker.delegate = delegate
            root.presentViewController(picker, animated = true, completion = null)
        }
    }

    private fun readPickedFile(url: NSURL): PickedFile {
        val data: NSData = NSData.dataWithContentsOfURL(url)
            ?: throw AppError.Storage("Could not read the selected file.")
        val name = url.lastPathComponent ?: "attachment"
        return PickedFile(
            fileName = name,
            mimeType = guessMimeType(name),
            bytes = data.toByteArray(),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    // `getBytes` is absent under this Kotlin version's `NSData` projection — the same
    // gap `user-defaults` documents — so the read goes through `bytes` + `memcpy`, like
    // `SystemKeychain.readData`. One indirection deeper than the naive call, and the one
    // that compiles.
    val source = bytes ?: return ByteArray(0)
    return ByteArray(size).also { out ->
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, size.toULong())
        }
    }
}

