package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.network.Endpoint
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import kotlinx.serialization.builtins.ListSerializer
import kotlin.coroutines.cancellation.CancellationException

/** The one directory path both the blank listing and a query use. */
private const val USER_DIRECTORY: String = "/v1/users/search"

/**
 * `GET /v1/users/search?q=…`.
 *
 * A blank query is a valid request: the server answers with every non-deleted account, which
 * is the listing the member picker opens on. The endpoint is open to any authenticated user,
 * not just an admin — choosing a collaborator is not an administrative act.
 */
private class DirectorySearchEndpoint(private val query: String) : Endpoint {
    override val path: String = USER_DIRECTORY
    override val method: HttpMethod = HttpMethod.Get
    override val queryItems: List<HttpQueryItem> =
        listOf(HttpQueryItem(name = "q", value = query))
    override val requiresAuth: Boolean = true
}

/**
 * The collaborator directory, over the authenticated client.
 *
 * The transport half of the server-backed `TeamPort`: this class knows the one path and its
 * query, and nothing about caching or offline fallback — [ServerTeamRepository] composes it
 * with the session-backed directory for those.
 *
 * @param client the **authenticated** client — the one built with `AuthInterceptor`. The
 *   directory route rejects an anonymous call with `401`.
 */
class HttpTeamRemote(private val client: HttpClient) {

    @Throws(AppError::class, CancellationException::class)
    suspend fun searchMembers(query: String): List<User> = client.send(
        DirectorySearchEndpoint(query).request,
        decoding = ListSerializer(MemberResponse.serializer()),
    ).map { it.toUser() }
}
