package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.parseProjectID
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.featureprojectsdomain.Project
import com.tasktracker.network.Endpoint
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import com.tasktracker.network.jsonBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * The admin projects API, over the authenticated client.
 *
 * The transport half of [AdminProjectsPort]: this class knows the paths, the verbs and the
 * JSON, and nothing about the local store. [AdminProjectsRepository] composes it with the
 * domain port's read contract to satisfy the whole interface.
 *
 * Every call sets `requiresAuth = true`, which is what routes it through `AuthInterceptor`:
 * an admin endpoint without a bearer token answers `401` and the interceptor refreshes once
 * before giving up, exactly as the sync transport relies on.
 *
 * @param client the **authenticated** client — the one built with `AuthInterceptor`. The plain
 *   client `HttpAuthRemote` is handed would send these with no token at all.
 */
class HttpAdminProjectsRemote(private val client: HttpClient) {

    /** `GET /v1/admin/projects?status=…`. */
    private class ListProjectsEndpoint(private val status: ProjectAdminStatus) : Endpoint {
        override val path: String = "/v1/admin/projects"
        override val method: HttpMethod = HttpMethod.Get
        override val queryItems: List<HttpQueryItem> =
            listOf(HttpQueryItem(name = "status", value = status.wireValue))
    }

    /**
     * `PATCH /v1/admin/projects/{id}`.
     *
     * The body is the whole project as the client holds it; `id` and `createdAt` are taken
     * from the stored row server-side, and `version` is the compare-and-swap.
     *
     * Opted into replay: setting a project's fields to the same values twice leaves the same
     * row standing, so a dropped connection read as a failed save is the only thing the default
     * would buy — an admin told a project did not save when it in fact did.
     */
    private class UpdateProjectEndpoint(
        private val id: ProjectID,
        private val payload: ProjectRequestBody,
    ) : Endpoint {
        override val path: String = "/v1/admin/projects/${id.stringValue}"
        override val method: HttpMethod = HttpMethod.Patch
        override val body: ByteArray? = jsonBody(payload)
        override val isIdempotent: Boolean? = true
    }

    /**
     * The server's `project` object, as the admin endpoints return it.
     *
     * This is the same shape the sync feed carries — the client's project model decodes either
     * — translated field by field. `createdAt` and `updatedAt` are ISO-8601 UTC whole-second
     * strings here, not epoch millis, because the admin API sits on the sync entity contract
     * rather than the older user-object contract.
     *
     * `version` is declared because the server sends it, and it is the value the caller saves
     * against next time. It is then dropped when mapping into the domain [Project], which has
     * no such property — the same thing `MemberResponse` does with `created_at`.
     */
    @Serializable
    private data class ProjectResponseBody(
        val id: String,
        val keyPrefix: String,
        val name: String,
        val details: String,
        val memberIds: List<String>,
        val isArchived: Boolean,
        val nextItemNumber: Int,
        @SerialName("createdAt") val createdAt: String,
        @SerialName("updatedAt") val updatedAt: String,
        val version: String,
    )

    /** The domain row this payload describes. */
    private fun ProjectResponseBody.toProject(): Project = Project(
        id = projectID(),
        keyPrefix = keyPrefix,
        name = name,
        details = details,
        memberIDs = memberIds.mapNotNull { parseUserIDOrNull(it) },
        isArchived = isArchived,
        nextItemNumber = nextItemNumber,
        createdAt = createdAtInstant(),
        updatedAt = updatedAtInstant(),
    )

    /** An id the API cannot parse is a decoding failure, not a `null` project. */
    private fun ProjectResponseBody.projectID(): ProjectID =
        parseProjectID(id) ?: throw AppError.Decoding("Project id \"$id\" is not a UUID.")

    private fun ProjectResponseBody.createdAtInstant(): Instant =
        DateCoding.parse(createdAt)?.roundedToSecond()
            ?: throw AppError.Decoding("Project createdAt \"$createdAt\" is not a valid date.")

    private fun ProjectResponseBody.updatedAtInstant(): Instant =
        DateCoding.parse(updatedAt)?.roundedToSecond()
            ?: throw AppError.Decoding("Project updatedAt \"$updatedAt\" is not a valid date.")

    /** A UUID the API cannot parse is a decoding failure, not a `null` member. */
    private fun parseUserIDOrNull(raw: String): com.tasktracker.core.UserID? =
        com.tasktracker.core.parseUserID(raw)

    /** `project` as the client holds it, ready for the `PATCH` body. */
    @Serializable
    private data class ProjectRequestBody(
        val id: String,
        val keyPrefix: String,
        val name: String,
        val details: String,
        val memberIds: List<String>,
        val isArchived: Boolean,
        val nextItemNumber: Int,
        @SerialName("createdAt") val createdAt: String,
        @SerialName("updatedAt") val updatedAt: String,
        val version: String,
    )

    private fun ProjectRequestBody.create(
        project: Project,
        version: String,
    ): ProjectRequestBody = ProjectRequestBody(
        id = project.id.stringValue,
        keyPrefix = project.keyPrefix,
        name = project.name,
        details = project.details,
        memberIds = project.memberIDs.map { it.stringValue },
        isArchived = project.isArchived,
        nextItemNumber = project.nextItemNumber,
        createdAt = DateCoding.format(project.createdAt),
        updatedAt = DateCoding.format(project.updatedAt),
        version = version,
    )

    /**
     * The projects in one bucket.
     *
     * The server returns newest first, and the domain's [Project] is mutable, so the list is
     * returned as freshly built copies — the same discipline the sync feed uses when it hands
     * a project back to a client that might otherwise hold a reference and watch it change.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun projects(status: ProjectAdminStatus): List<Project> = client.send(
        ListProjectsEndpoint(status).request,
        decoding = ListSerializer(ProjectResponseBody.serializer()),
    ).map { it.toProject() }

    /**
     * One project, by id.
     *
     * The edit screen is seeded from this, so a `404` is surfaced rather than silently turned
     * into an empty project the caller would then edit against nothing.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun project(id: ProjectID): Project? {
        return try {
            client.send(
                HttpRequest(
                    method = HttpMethod.Get,
                    path = "/v1/admin/projects/${id.stringValue}",
                    requiresAuth = true,
                ),
                decoding = ProjectResponseBody.serializer(),
            ).toProject()
        } catch (e: AppError) {
            throw e
        }
    }

    /**
     * Applies an edit to a project and returns the stored version.
     *
     * The response carries the server's `version` and `updatedAt`, which are what the caller
     * saves against next time.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun updateProject(project: Project, version: String): Project =
        client.send(
            UpdateProjectEndpoint(project.id, ProjectRequestBody.create(project, version)).request,
            decoding = ProjectResponseBody.serializer(),
        ).toProject()
}
