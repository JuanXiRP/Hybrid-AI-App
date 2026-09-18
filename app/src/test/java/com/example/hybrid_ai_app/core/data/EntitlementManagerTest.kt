package com.example.hybrid_ai_app.core.data

import app.cash.turbine.test
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.expiredEntitlement
import com.example.hybrid_ai_app.testing.premiumEntitlement
import com.example.hybrid_ai_app.testing.trialEntitlement
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * App-wide entitlement state and its DataStore cache.
 *
 * The governing principle throughout: the backend re-checks every quota on every write, so this
 * cache exists only to avoid flashing wrong numbers on a cold start. Every ambiguous case
 * therefore resolves in the permissive direction — locking a paying user out because the network
 * blipped is far worse than briefly showing an enabled button.
 */
class EntitlementManagerTest {

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var userRepository: UserRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var manager: EntitlementManager

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        every { preferencesManager.entitlementFlow } returns flowOf(null)
        manager = EntitlementManager(userRepository, preferencesManager)
    }

    // ==================== Publishing and caching ====================

    @Test
    fun `it starts permissive so a cold start never locks a paying user out`() {
        // Arrange, Act & Assert
        val initial = manager.entitlement.value
        assertFalse(initial.isReadOnly)
        assertTrue(initial.canSendChatMessage)
        assertTrue(initial.canGeneratePlan)
    }

    @Test
    fun `a refresh publishes the server entitlement and caches it`() = runTest {
        // Arrange
        val fromServer = trialEntitlement(trialDaysLeft = 9)
        coEvery { userRepository.getEntitlement() } returns Result.success(fromServer)

        // Act
        val result = manager.refresh()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(fromServer, manager.entitlement.value)
        coVerify(exactly = 1) { preferencesManager.saveEntitlement(any()) }
    }

    @Test
    fun `the cache is written as the snake_case wire shape`() = runTest {
        // It is read back by the same DTO the backend's response uses, so the stored JSON has to
        // carry the wire names, not the domain's.
        //
        // A premium entitlement is used deliberately: `encodeDefaults = false` means the encoder
        // omits every property equal to its DTO default, so a trial entitlement writes no
        // `is_premium` key at all (false is the default) and no `plans.used` (0 is the default).
        // Asserting on those would be asserting on absence. Round-tripping still works because
        // the same defaults fill them back in — `EntitlementMapperTest` pins that.
        // Arrange
        coEvery { userRepository.getEntitlement() } returns
            Result.success(premiumEntitlement(plansUsed = 3, chatUsed = 12))
        val stored = slot<String>()
        coEvery { preferencesManager.saveEntitlement(capture(stored)) } returns Unit

        // Act
        manager.refresh()

        // Assert
        assertTrue(
            "expected is_premium in: ${stored.captured}",
            stored.captured.contains("\"is_premium\":true"),
        )
        assertTrue(stored.captured.contains("\"status\":\"premium\""))
        assertTrue(stored.captured.contains("\"used\":3"))
        assertTrue(stored.captured.contains("\"used\":12"))
    }

    @Test
    fun `a value equal to its default is omitted from the cache entirely`() = runTest {
        // Pinning the consequence of `encodeDefaults = false` on the cache, because the stored
        // JSON looks alarmingly sparse and someone will eventually wonder whether it is corrupt.
        // Arrange
        coEvery { userRepository.getEntitlement() } returns
            Result.success(trialEntitlement(plansUsed = 0, chatUsed = 1))
        val stored = slot<String>()
        coEvery { preferencesManager.saveEntitlement(capture(stored)) } returns Unit

        // Act
        manager.refresh()

        // Assert
        assertFalse(
            "is_premium=false equals the DTO default, so it is not written",
            stored.captured.contains("is_premium"),
        )
        assertTrue("a non-default value is written", stored.captured.contains("\"used\":1"))
    }

    @Test
    fun `the published state is observable as a flow`() = runTest {
        // Arrange
        coEvery { userRepository.getEntitlement() } returns
            Result.success(premiumEntitlement())

        // Act & Assert
        manager.entitlement.test {
            assertEquals(Entitlement.Unknown, awaitItem())
            manager.refresh()
            assertTrue(awaitItem().isPremium)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed refresh keeps the last known value rather than downgrading the user`() = runTest {
        // Arrange
        val known = trialEntitlement(trialDaysLeft = 9)
        coEvery { userRepository.getEntitlement() } returns Result.success(known)
        manager.refresh()

        // Act
        coEvery { userRepository.getEntitlement() } returns
            Result.failure(Exception("offline"))
        val result = manager.refresh()

        // Assert
        assertTrue(result.isFailure)
        assertEquals(known, manager.entitlement.value)
    }

    @Test
    fun `a failed refresh does not overwrite the cache with a guess`() = runTest {
        // Arrange
        coEvery { userRepository.getEntitlement() } returns Result.failure(Exception("offline"))

        // Act
        manager.refresh()

        // Assert
        coVerify(exactly = 0) { preferencesManager.saveEntitlement(any()) }
    }

    // ==================== The cache ====================

    @Test
    fun `loadFromCache restores a previously persisted entitlement`() = runTest {
        // Arrange — the exact shape `refresh` writes, i.e. the backend's wire format
        val cached = """
            {"status":"trial","is_premium":false,"trial_days_left":9,
             "plans":{"used":1,"limit":1},
             "chat":{"used":1,"limit":2,"resets_at":"2026-07-09T00:00:00.000Z"}}
        """.trimIndent()
        every { preferencesManager.entitlementFlow } returns flowOf(cached)

        // Act
        manager.loadFromCache()

        // Assert
        val loaded = manager.entitlement.value
        assertEquals(EntitlementStatus.TRIAL, loaded.status)
        assertEquals(9, loaded.trialDaysLeft)
        assertEquals(1, loaded.chatUsed)
        assertEquals(2, loaded.chatLimit)
        assertEquals(1, loaded.chatMessagesLeft)
    }

    @Test
    fun `a cached premium entitlement keeps its null limits`() = runTest {
        // Arrange
        val cached = """
            {"status":"premium","is_premium":true,"trial_days_left":0,
             "plans":{"used":3,"limit":null},
             "chat":{"used":12,"limit":null,"resets_at":null}}
        """.trimIndent()
        every { preferencesManager.entitlementFlow } returns flowOf(cached)

        // Act
        manager.loadFromCache()

        // Assert
        val loaded = manager.entitlement.value
        assertTrue(loaded.isPremium)
        assertNull(loaded.plansLimit)
        assertNull(loaded.chatLimit)
    }

    @Test
    fun `an unreadable cache is discarded rather than crashing the app`() = runTest {
        // Arrange
        every { preferencesManager.entitlementFlow } returns flowOf("{ not json")

        // Act
        manager.loadFromCache()

        // Assert
        assertEquals(Entitlement.Unknown, manager.entitlement.value)
    }

    @Test
    fun `an empty cache leaves the permissive default in place`() = runTest {
        // Arrange — a first launch, nothing stored yet
        every { preferencesManager.entitlementFlow } returns flowOf(null)

        // Act
        manager.loadFromCache()

        // Assert
        assertEquals(Entitlement.Unknown, manager.entitlement.value)
    }

    @Test
    fun `a cache written by a newer build is still readable`() = runTest {
        // The cache is decoded with ignoreUnknownKeys, so a field added later does not strand
        // users on an older build with an unreadable cache.
        // Arrange
        val cached = """
            {"status":"trial","is_premium":false,"trial_days_left":5,
             "plans":{"used":0,"limit":1},
             "chat":{"used":0,"limit":2,"resets_at":null},
             "referral_credits":3}
        """.trimIndent()
        every { preferencesManager.entitlementFlow } returns flowOf(cached)

        // Act
        manager.loadFromCache()

        // Assert
        assertEquals(5, manager.entitlement.value.trialDaysLeft)
    }

    @Test
    fun `clear wipes both the flow and the cache on sign-out`() = runTest {
        // Entitlement is per-account: leaving it behind would show the next user the previous
        // user's premium status.
        // Arrange
        coEvery { userRepository.getEntitlement() } returns Result.success(premiumEntitlement())
        manager.refresh()
        assertTrue(manager.entitlement.value.isPremium)

        // Act
        manager.clear()

        // Assert
        assertEquals(Entitlement.Unknown, manager.entitlement.value)
        coVerify(exactly = 1) { preferencesManager.clearEntitlement() }
    }

    // ==================== Quota rules on the domain model ====================

    @Test
    fun `a free user with a spent chat quota cannot send`() {
        // Arrange
        val spent = trialEntitlement(chatUsed = 2, chatLimit = 2)

        // Act & Assert
        assertFalse(spent.canSendChatMessage)
        assertEquals(0, spent.chatMessagesLeft)
    }

    @Test
    fun `the remaining count never goes negative`() {
        // A server that reports more used than the limit must not render as "-1 left".
        // Arrange
        val overspent = trialEntitlement(chatUsed = 5, chatLimit = 2)

        // Act & Assert
        assertEquals(0, overspent.chatMessagesLeft)
        assertFalse(overspent.canSendChatMessage)
    }

    @Test
    fun `a free user who used their one plan cannot generate another`() {
        // Arrange
        val spent = trialEntitlement(plansUsed = 1, plansLimit = 1)

        // Act & Assert
        assertFalse(spent.canGeneratePlan)
    }

    @Test
    fun `premium means null limits, not large ones`() {
        // Arrange
        val premium = premiumEntitlement()

        // Act & Assert
        assertTrue(premium.isPremium)
        assertTrue(premium.canGeneratePlan)
        assertTrue(premium.canSendChatMessage)
        assertNull("unlimited means no counter to show", premium.chatMessagesLeft)
    }

    @Test
    fun `an expired trial blocks writes even when quotas are unspent`() {
        // Arrange
        val expired = expiredEntitlement()

        // Act & Assert
        assertTrue(expired.isReadOnly)
        assertFalse(expired.canGeneratePlan)
        assertFalse(expired.canSendChatMessage)
    }

    @Test
    fun `only the expired status is read-only`() {
        // Arrange, Act & Assert
        assertFalse(trialEntitlement().isReadOnly)
        assertFalse(premiumEntitlement().isReadOnly)
        assertTrue(expiredEntitlement().isReadOnly)
    }
}
