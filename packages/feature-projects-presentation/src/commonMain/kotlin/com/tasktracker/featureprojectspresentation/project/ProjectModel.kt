package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * What every model in this feature has: somewhere to report a failure, and a way to run a
 * write that turns one into text rather than into a throw.
 *
 * ## Why a base class and not a helper
 *
 * `errorMessage` is Compose state and [reporting] mutates it, so the two belong together;
 * a free function taking a `MutableState<String?>` would push the same three-line dance
 * into seven files, and one of them would get the `CancellationException` rethrow wrong.
 *
 * ## Errors are never re-worded
 *
 * [errorMessage] is [AppError.message] verbatim. That property is already the
 * user-facing sentence, and a model that wrapped it in its own phrasing would create a
 * second source of truth for text `:packages:core` owns - and the copy that drifted would
 * be the one a user saw. The one case a model does add wording for is a non-[AppError]
 * failure, which is a bug rather than a condition the product describes.
 */
abstract class ProjectModel {

    /**
     * The last failure, as text a banner can show verbatim.
     *
     * Null until something fails, and cleared by the next attempt - a stale error above a
     * fresh form is a banner about a problem the user has already dealt with.
     */
    var errorMessage: String? by mutableStateOf(null)
        private set

    /** Clears [errorMessage]. A screen calls this when a banner is dismissed. */
    fun dismissError() {
        errorMessage = null
    }

    /** Reports a failure. Overridable so a model can add context to the text. */
    protected open fun onFailure(failure: AppError) {
        errorMessage = failure.message
    }

    /**
     * Runs a write, turning a failure into [errorMessage] rather than into a throw.
     *
     * Returns `null` when the write failed, so a caller can skip a navigation that should
     * only happen on success. A write is a button press rather than a read, so its failure
     * has to land somewhere visible; handing the error back to a caller that is an
     * `onClick` lambda would either crash or be dropped on the floor.
     */
    protected suspend fun <T> reporting(block: suspend () -> T): T? {
        return try {
            errorMessage = null
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: AppError) {
            onFailure(failure)
            null
        } catch (unexpected: Exception) {
            reportUnexpected(unexpected)
            null
        }
    }

    private fun reportUnexpected(unexpected: Exception) {
        // A non-AppError is a bug rather than a condition the product describes, and
        // `Unknown` is the only honest bucket for it. Naming the type is what makes it
        // diagnosable from a screenshot of the banner.
        onFailure(AppError.Unknown(unexpected::class.simpleName ?: "an unknown failure"))
    }
}

/**
 * A [ProjectModel] that also keeps itself in step with the store.
 *
 * ## The two rules this class exists to enforce
 *
 * **1. A model observes on the scope the view hands it, and stops when told to.**
 *
 * There is no `viewModelScope` here - an AndroidX `ViewModel` is untestable on the JVM and
 * iOS targets this build ships - so the scope comes from the composition, and *something*
 * has to cancel the collection. The trap is specific: a project switcher mid-view builds a
 * new model, and if the old one is still observing then every write anywhere in the
 * feature keeps waking a board nobody is showing. So [observe] cancels whatever it started
 * last, [stopObserving] cancels it for good, and a screen calling [observe] again for a
 * different project cannot leak the previous subscription.
 *
 * **2. Cancellation is never a failure.**
 *
 * A coroutine can be cancelled at any suspension point, and a `catch (e: AppError)` that
 * swallowed it would turn "the user navigated away" into a red banner on a screen that has
 * already gone. That is why the catch order in [ProjectModel.reporting] - and in every
 * use case - rethrows `CancellationException` first.
 *
 * @see ProjectRepositoryPort.changes for why the read happens *before* the collect.
 */
abstract class ObservingProjectModel(
    protected val repository: ProjectRepositoryPort,
) : ProjectModel() {

    private var observation: Job? = null

    /** Whether a read is in flight, so a screen can tell "loading" from "empty". */
    var isLoading: Boolean by mutableStateOf(true)
        private set

    /**
     * Reads the current state, then re-reads on every relevant change until stopped.
     *
     * Safe to call repeatedly: the previous observation is cancelled first. The first
     * action inside the job is a reload rather than a collect, because
     * [ProjectRepositoryPort.changes] is a cold flow with no replay - every new subscriber
     * has to read the current state itself, and a model that only collected would show an
     * empty screen until somebody happened to write.
     */
    fun observe(scope: CoroutineScope) {
        observation?.cancel()
        observation = scope.launch {
            reloadCatching()
            repository.changes().collect { change ->
                if (isRelevant(change)) reloadCatching()
            }
        }
    }

    /** Cancels observation. A screen calls this when it leaves the composition. */
    fun stopObserving() {
        observation?.cancel()
        observation = null
    }

    /** Re-reads now, outside any observation. */
    suspend fun refresh() {
        reloadCatching()
    }

    /**
     * Whether a change is worth re-reading for. Defaults to everything.
     *
     * Public rather than protected because it is a per-model contract worth stating in a
     * KDoc and worth asserting on directly: a model that filters a change out has made a
     * decision a test can only otherwise infer from how many reads happened. Half of this
     * feature's models override it and half do not, which is the shape of a rule that
     * drifted - so it is one declaration with one visibility.
     */
    open fun isRelevant(change: ProjectChange): Boolean = true

    /** Re-reads the store into this model's state. Throws on failure; see [reloadCatching]. */
    protected abstract suspend fun reload()

    private suspend fun reloadCatching() {
        isLoading = true
        try {
            reload()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: AppError) {
            onFailure(failure)
        } catch (unexpected: Exception) {
            onFailure(AppError.Unknown(unexpected::class.simpleName ?: "an unknown failure"))
        } finally {
            isLoading = false
        }
    }
}
