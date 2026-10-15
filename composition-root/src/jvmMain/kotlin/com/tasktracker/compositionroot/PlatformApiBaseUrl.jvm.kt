package com.tasktracker.compositionroot

/**
 * The API root from the process environment.
 *
 * `TASKTRACKER_API_URL` overrides the fallback, which is how a Desktop app reaches a server on
 * another host or port without a rebuild. A blank value is treated as unset rather than used:
 * `KtorHttpClient` would hand `""` to `URLBuilder`, which has no scheme, and every request
 * would fail before it left the process — so the blank falls back to the same default an unset
 * variable gets.
 *
 * Split into [jvmApiBaseUrl] and this one-line `actual` so the rule is a pure function of a
 * map. A test can then pin both the default and the override without owning the test process's
 * environment, which it cannot mutate safely anyway.
 */
actual fun platformApiBaseUrl(@Suppress("UNUSED_PARAMETER") host: Any?): String =
    jvmApiBaseUrl(System.getenv())

/** The environment variable that overrides [platformApiBaseUrl] on the JVM. */
internal const val API_BASE_URL_ENV_VAR: String = "TASKTRACKER_API_URL"

/**
 * [platformApiBaseUrl] as a function of the environment it was given.
 *
 * The exported *name* is the contract — a user who exported `TASKTRACKER_API_URL` must not be
 * silently ignored — so a test spells the string out rather than reading [API_BASE_URL_ENV_VAR].
 */
internal fun jvmApiBaseUrl(environment: Map<String, String>): String =
    environment[API_BASE_URL_ENV_VAR]?.takeIf { it.isNotBlank() } ?: DEFAULT_LOCAL_API_BASE_URL
