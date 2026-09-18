package com.example.hybrid_ai_app.core.data

import android.util.Log
import com.example.hybrid_ai_app.core.data.mapper.toDomain
import com.example.hybrid_ai_app.core.data.mapper.toDto
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementDto
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide entitlement state: what the user may do, and how much of their free quota is left.
 *
 * Refresh points:
 *  - app start (MainActivity)
 *  - after a successful purchase or restore
 *  - after any backend 402, which means our cached counters were stale
 *
 * The cached copy in DataStore only exists so a cold start does not flash "0 days left" before
 * the network answers. The backend re-checks every quota on every write, so this cache being
 * wrong or tampered with grants nothing.
 */
@Singleton
class EntitlementManager @Inject constructor(
    private val userRepository: UserRepository,
    private val preferencesManager: PreferencesManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _entitlement = MutableStateFlow(Entitlement.Unknown)
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    /** Seeds from the DataStore cache. Call once, before the first network refresh. */
    suspend fun loadFromCache() {
        val cached = preferencesManager.entitlementFlow.first() ?: return
        runCatching { json.decodeFromString<EntitlementDto>(cached) }
            .onSuccess { _entitlement.value = it.toDomain() }
            .onFailure { Log.w(TAG, "Discarding unreadable entitlement cache", it) }
    }

    /** Fetches the authoritative entitlement and caches it. Failures leave the cache in place. */
    suspend fun refresh(): Result<Entitlement> = userRepository.getEntitlement().onSuccess { entitlement ->
        _entitlement.value = entitlement
        // Explicit serializer: inside runCatching, inference picks the
        // encodeToString(SerializationStrategy, value) overload instead of the reified one.
        runCatching {
            val encoded = json.encodeToString(EntitlementDto.serializer(), entitlement.toDto())
            preferencesManager.saveEntitlement(encoded)
        }.onFailure { Log.w(TAG, "Failed to cache entitlement", it) }
    }.onFailure {
        Log.w(TAG, "Entitlement refresh failed; keeping cached value", it)
    }

    /** Called on sign-out. Entitlement is per-account and must not survive into the next login. */
    suspend fun clear() {
        _entitlement.value = Entitlement.Unknown
        preferencesManager.clearEntitlement()
    }

    private companion object {
        const val TAG = "EntitlementManager"
    }
}
