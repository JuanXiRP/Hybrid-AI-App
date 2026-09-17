package com.example.hybrid_ai_app.core.data

import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementManagerTest {

    private lateinit var userRepository: UserRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var manager: EntitlementManager

    private val trial = Entitlement(
        status = EntitlementStatus.TRIAL,
        trialDaysLeft = 9,
        plansUsed = 1,
        plansLimit = 1,
        chatUsed = 1,
        chatLimit = 2,
        chatResetsAt = "2026-07-09T00:00:00.000Z",
    )

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        every { preferencesManager.entitlementFlow } returns flowOf(null)
        manager = EntitlementManager(userRepository, preferencesManager)
    }

    @Test
    fun `starts permissive so a cold start never locks a paying user out`() {
        val initial = manager.entitlement.value
        assertFalse(initial.isReadOnly)
        assertTrue(initial.canSendChatMessage)
        assertTrue(initial.canGeneratePlan)
    }

    @Test
    fun `refresh publishes the server entitlement and caches it`() = runTest {
        coEvery { userRepository.getEntitlement() } returns Result.success(trial)

        val result = manager.refresh()

        assertTrue(result.isSuccess)
        assertEquals(trial, manager.entitlement.value)
        coVerify { preferencesManager.saveEntitlement(any()) }
    }

    @Test
    fun `a failed refresh keeps the last known value rather than downgrading the user`() = runTest {
        coEvery { userRepository.getEntitlement() } returns Result.success(trial)
        manager.refresh()

        coEvery { userRepository.getEntitlement() } returns Result.failure(Exception("offline"))
        val result = manager.refresh()

        assertTrue(result.isFailure)
        assertEquals(trial, manager.entitlement.value)
    }

    @Test
    fun `loadFromCache restores a previously persisted entitlement`() = runTest {
        val cached = """
            {"status":"trial","is_premium":false,"trial_days_left":9,
             "plans":{"used":1,"limit":1},
             "chat":{"used":1,"limit":2,"resets_at":"2026-07-09T00:00:00.000Z"}}
        """.trimIndent()
        every { preferencesManager.entitlementFlow } returns flowOf(cached)

        manager.loadFromCache()

        val loaded = manager.entitlement.value
        assertEquals(EntitlementStatus.TRIAL, loaded.status)
        assertEquals(9, loaded.trialDaysLeft)
        assertEquals(1, loaded.chatUsed)
        assertEquals(2, loaded.chatLimit)
        assertEquals(1, loaded.chatMessagesLeft)
    }

    @Test
    fun `an unreadable cache is discarded rather than crashing the app`() = runTest {
        every { preferencesManager.entitlementFlow } returns flowOf("{ not json")

        manager.loadFromCache()

        assertEquals(Entitlement.Unknown, manager.entitlement.value)
    }

    @Test
    fun `clear wipes both the flow and the cache on sign-out`() = runTest {
        coEvery { userRepository.getEntitlement() } returns Result.success(trial)
        manager.refresh()

        manager.clear()

        assertEquals(Entitlement.Unknown, manager.entitlement.value)
        coVerify { preferencesManager.clearEntitlement() }
    }

    // ==================== Entitlement quota rules ====================

    @Test
    fun `a free user with a spent chat quota cannot send`() {
        val spent = trial.copy(chatUsed = 2, chatLimit = 2)
        assertFalse(spent.canSendChatMessage)
        assertEquals(0, spent.chatMessagesLeft)
    }

    @Test
    fun `a free user who used their one plan cannot generate another`() {
        val spent = trial.copy(plansUsed = 1, plansLimit = 1)
        assertFalse(spent.canGeneratePlan)
    }

    @Test
    fun `premium means null limits, not large ones`() {
        val premium = trial.copy(
            status = EntitlementStatus.PREMIUM,
            plansLimit = null,
            chatLimit = null,
        )
        assertTrue(premium.isPremium)
        assertTrue(premium.canGeneratePlan)
        assertTrue(premium.canSendChatMessage)
        assertNull(premium.chatMessagesLeft)
    }

    @Test
    fun `an expired trial blocks writes even when quotas are unspent`() {
        val expired = trial.copy(
            status = EntitlementStatus.EXPIRED,
            plansUsed = 0,
            chatUsed = 0,
        )
        assertTrue(expired.isReadOnly)
        assertFalse(expired.canGeneratePlan)
        assertFalse(expired.canSendChatMessage)
    }
}
