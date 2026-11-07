package nl.jaap.filamentspools.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.data.SyncState
import nl.jaap.filamentspools.data.Syncer

class FakeSyncer : Syncer {
    override val state = MutableStateFlow(SyncState())
    var calls = 0
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun sync(): Result<Unit> {
        calls++
        state.value = SyncState(running = false, lastError = result.exceptionOrNull() as? ApiError)
        return result
    }
}
