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
 * Base presentation model providing Compose-backed [errorMessage] state and safe write execution.
 * Standardizes [AppError] capture and ensures [CancellationException] always propagates cleanly.
 */
abstract class ProjectModel {

    /** Current error message for banner display, or null when clear. */
    var errorMessage: String? by mutableStateOf(null)
        private set

    fun dismissError() {
        errorMessage = null
    }

    protected open fun onFailure(failure: AppError) {
        errorMessage = failure.message
    }

    /**
     * Executes write [block], mapping [AppError] into [errorMessage] and re-throwing [CancellationException].
     * Returns null on failure.
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
        onFailure(AppError.Unknown(unexpected::class.simpleName ?: "an unknown failure"))
    }
}

/**
 * Base model that observes [ProjectRepositoryPort.changes] using a caller-managed [CoroutineScope].
 * Re-reads state immediately on subscription and refreshes whenever [isRelevant] matches.
 */
abstract class ObservingProjectModel(
    protected val repository: ProjectRepositoryPort,
) : ProjectModel() {

    private var observation: Job? = null

    var isLoading: Boolean by mutableStateOf(true)
        private set

    /** Cancels any previous observation and starts listening for repository changes. */
    fun observe(scope: CoroutineScope) {
        observation?.cancel()
        observation = scope.launch {
            reloadCatching()
            repository.changes().collect { change ->
                if (isRelevant(change)) reloadCatching()
            }
        }
    }

    fun stopObserving() {
        observation?.cancel()
        observation = null
    }

    suspend fun refresh() {
        reloadCatching()
    }

    /** Predicate determining whether [change] should trigger a [reload]. Defaults to true. */
    open fun isRelevant(change: ProjectChange): Boolean = true

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