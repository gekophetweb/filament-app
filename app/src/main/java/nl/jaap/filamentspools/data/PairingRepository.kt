package nl.jaap.filamentspools.data

import nl.jaap.filamentspools.api.ApiProvider
import nl.jaap.filamentspools.api.PairRequest
import nl.jaap.filamentspools.api.ServerUrl
import nl.jaap.filamentspools.api.apiCall
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.data.session.SessionStore
import javax.inject.Inject

interface Pairer {
    /** Trades a pairing code for a token and signs in. Fails with IllegalArgumentException for a bad URL. */
    suspend fun pair(serverUrl: String, code: String, deviceName: String): Result<Unit>
}

class PairingRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val session: SessionStore,
    private val sync: SyncRepository,
) : Pairer {

    override suspend fun pair(serverUrl: String, code: String, deviceName: String): Result<Unit> {
        val baseUrl = runCatching { ServerUrl.normalize(serverUrl) }.getOrElse { return Result.failure(it) }
        val paired = apiCall {
            apiProvider.unauthenticated(baseUrl).pair(PairRequest(code.trim(), deviceName.trim().ifBlank { null })).data
        }.getOrElse { return Result.failure(it) }

        // Use the address the user can reach; the response's `api` may say localhost.
        session.signIn(Session(baseUrl, paired.token, paired.abilities.toSet(), paired.user.name))
        sync.sync() // A failed first sync is shown on the home screen; pairing itself succeeded.
        return Result.success(Unit)
    }
}
