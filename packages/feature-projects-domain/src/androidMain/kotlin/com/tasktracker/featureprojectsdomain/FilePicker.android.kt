package com.tasktracker.featureprojectsdomain

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.tasktracker.core.AppError
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Android picker: `ACTION_OPEN_DOCUMENT` through the Activity Result API.
 *
 * ## Why the Application has to watch its activities
 *
 * The graph is started from the `Application` ([com.tasktracker.compositionroot.AppGraph]'s
 * host), and an activity-result registration belongs to an Activity — the Application
 * cannot receive one. So the picker registers lifecycle callbacks once and remembers the
 * resumed activity, which is the same technique a `Application.ActivityLifecycleCallbacks`
 * -based navigator uses. The alternative — threading the Activity into the graph — would
 * make the graph hold a reference to an object the system can destroy and recreate at
 * any moment; a remembered *current* activity degrades to "no activity right now"
 * instead of crashing against a dead one.
 *
 * Each [pick] registers its own launcher key and unregisters on completion, so a
 * double-tap cannot pair the second pick with the first pick's callback.
 */
actual fun createFilePicker(host: Any?): FilePicker {
    val application = host as? Application
        ?: throw AppError.Unknown(
            "Android needs an application Context to pick files. " +
                "Pass one to FilePicker creation.",
        )
    AndroidActivityTracker.register(application)
    return AndroidFilePicker(application)
}

private object AndroidActivityTracker {
    @Volatile
    private var currentActivityRef: WeakReference<ComponentActivity>? = null

    @Volatile
    private var isRegistered = false

    val currentActivity: ComponentActivity?
        get() = currentActivityRef?.get()

    fun updateActivity(activity: Activity?) {
        val componentActivity = activity as? ComponentActivity
        if (componentActivity != null) {
            currentActivityRef = WeakReference(componentActivity)
        }
    }

    fun clearActivity(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }

    fun register(application: Application) {
        if (isRegistered) return
        synchronized(this) {
            if (isRegistered) return
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    updateActivity(activity)
                }

                override fun onActivityStarted(activity: Activity) {
                    updateActivity(activity)
                }

                override fun onActivityResumed(activity: Activity) {
                    updateActivity(activity)
                }

                override fun onActivityPaused(activity: Activity) = Unit

                // Do NOT clear on stopped: a stopped activity can still launch pickers
                // (e.g. when a dialog is showing). Only clear when truly destroyed.
                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    clearActivity(activity)
                }
            })
            isRegistered = true
        }
    }

    /**
     * Fallback lookup via ActivityThread reflection for when the tracker was registered
     * after the foreground activity had already been resumed.
     */
    fun findResumedActivityViaReflection(): ComponentActivity? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThreadMethod = activityThreadClass.getMethod("currentActivityThread")
            val activityThread = activityThreadMethod.invoke(null) ?: return null
            val activitiesField = activityThreadClass.getDeclaredField("mActivities").apply { isAccessible = true }
            val activities = activitiesField.get(activityThread) as? Map<*, *> ?: return null

            var fallbackActivity: ComponentActivity? = null
            for (record in activities.values) {
                if (record == null) continue
                val recordClass = record.javaClass
                val activityField = recordClass.getDeclaredField("activity").apply { isAccessible = true }
                val activity = activityField.get(record) as? ComponentActivity ?: continue

                val pausedField = try {
                    recordClass.getDeclaredField("paused").apply { isAccessible = true }
                } catch (_: Throwable) {
                    null
                }
                val isPaused = pausedField?.getBoolean(record) ?: true
                if (!isPaused) {
                    updateActivity(activity)
                    return activity
                }
                fallbackActivity = activity
            }
            if (fallbackActivity != null) {
                updateActivity(fallbackActivity)
            }
            fallbackActivity
        } catch (_: Throwable) {
            null
        }
    }
}

private class AndroidFilePicker(
    private val application: Application,
) : FilePicker {

    init {
        AndroidActivityTracker.register(application)
    }

    /**
     * Synchronously checks for an available activity.
     * First checks the cached reference from lifecycle callbacks or reflection,
     * then polls for an activity reference if not yet available.
     */
    private suspend fun getActivityOrWait(timeoutMs: Long = 5000): ComponentActivity? {
        // Fast path: check immediately via lifecycle tracker
        val tracked = AndroidActivityTracker.currentActivity
        if (tracked != null) return tracked

        // Fallback path: reflection lookup from ActivityThread
        val reflectionActivity = AndroidActivityTracker.findResumedActivityViaReflection()
        if (reflectionActivity != null) return reflectionActivity

        // Wait for lifecycle callback or reflection to populate the reference
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            while (true) {
                val current = AndroidActivityTracker.currentActivity
                    ?: AndroidActivityTracker.findResumedActivityViaReflection()
                if (current != null) return@withTimeoutOrNull current
                kotlinx.coroutines.delay(50)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    override suspend fun pick(): PickedFile? {
        val activity = getActivityOrWait()
            ?: throw AppError.Unknown("No activity is in the foreground to present the file picker from.")

        val contract = ActivityResultContracts.OpenDocument()
        val key = "tasktracker.filepicker.${System.nanoTime()}"

        return suspendCancellableCoroutine { continuation ->
            val registry = activity.activityResultRegistry
            // `ActivityResultLauncher.unregister` (public on the launcher) rather than
            // `ActivityResultRegistry.unregister` (internal to androidx.activity): the
            // registry owns the bookkeeping, the launcher owns its own lifetime.
            lateinit var launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
            launcher = registry.register(key, contract) { uri: Uri? ->
                launcher.unregister()
                if (uri == null) {
                    continuation.resume(null)
                } else {
                    continuation.resume(readPickedFile(activity, uri))
                }
            }
            // The document picker can only offer types OpenDocument can express; "*/*"
            // is honest — the domain accepts documents as well as images.
            launcher.launch(arrayOf("*/*"))
            continuation.invokeOnCancellation { launcher.unregister() }
        }
    }

    /**
     * Materialises the content URI before the callback returns.
     *
     * The read has to happen here, not later: `OpenDocument` grants the read for the
     * lifetime of the result delivery, and a row whose bytes were never read would be
     * the index-without-blob failure the port's KDoc refuses.
     */
    private fun readPickedFile(context: Context, uri: Uri): PickedFile {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AppError.Storage("Could not read the selected file.")
        return PickedFile(
            fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "attachment",
            mimeType = context.contentResolver.getType(uri) ?: guessMimeType(uri.lastPathSegment.orEmpty()),
            bytes = bytes,
        )
    }

    /**
     * The display name from `OpenableColumns`, not from the URI path: a document picked
     * from a cloud provider has a path like `document/primary:Photos/x.jpg` whose last
     * segment is not the name the user chose.
     */
    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
}