package com.example.hybrid_ai_app.core.data.mapper

import com.example.hybrid_ai_app.core.data.remote.dto.ChatQuotaDto
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementDto
import com.example.hybrid_ai_app.core.data.remote.dto.QuotaDto
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus
import com.example.hybrid_ai_app.testing.expiredEntitlement
import com.example.hybrid_ai_app.testing.premiumEntitlement
import com.example.hybrid_ai_app.testing.trialEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DTO-to-domain conversion on the money path, now that both call sites share one copy.
 *
 * Round-tripping is **deliberately asymmetric** and these tests assert the asymmetry rather than
 * papering over it. Two fields do not survive a trip through the domain model, and both are
 * intentional; the tests exist so the intent stays visible and a future change to either has to
 * be explicit.
 */
class EntitlementMapperTest {

    private fun dto(
        status: String = "trial",
        isPremium: Boolean = false,
        trialEndsAt: String? = "2026-10-02T12:00:00.000Z",
        trialDaysLeft: Int = 14,
        plansUsed: Int = 0,
        plansLimit: Int? = 1,
        chatUsed: Int = 1,
        chatLimit: Int? = 2,
        resetsAt: String? = "2026-09-19T00:00:00.000Z",
    ) = EntitlementDto(
        status = status,
        isPremium = isPremium,
        trialEndsAt = trialEndsAt,
        trialDaysLeft = trialDaysLeft,
        plans = QuotaDto(used = plansUsed, limit = plansLimit),
        chat = ChatQuotaDto(used = chatUsed, limit = chatLimit, resetsAt = resetsAt),
    )

    // ------------------------------------------------------------------------------------
    // toDomain
    // ------------------------------------------------------------------------------------

    @Test
    fun `the nested quota objects flatten into scalars`() {
        // Arrange
        val wire = dto(plansUsed = 1, plansLimit = 3, chatUsed = 4, chatLimit = 10)

        // Act
        val domain = wire.toDomain()

        // Assert
        assertEquals(1, domain.plansUsed)
        assertEquals(3, domain.plansLimit)
        assertEquals(4, domain.chatUsed)
        assertEquals(10, domain.chatLimit)
        assertEquals("2026-09-19T00:00:00.000Z", domain.chatResetsAt)
    }

    @Test
    fun `each wire status maps to its enum`() {
        // Arrange, Act & Assert
        assertEquals(EntitlementStatus.PREMIUM, dto(status = "premium").toDomain().status)
        assertEquals(EntitlementStatus.TRIAL, dto(status = "trial").toDomain().status)
        assertEquals(EntitlementStatus.EXPIRED, dto(status = "expired").toDomain().status)
    }

    @Test
    fun `an unknown status falls back to trial rather than locking the user out`() {
        // The permissive choice. The backend re-checks every quota on every write, so guessing
        // "trial" for a status this client version does not know costs nothing, whereas guessing
        // "expired" would put a paying user into read-only mode over a vocabulary mismatch.
        // Arrange
        val unknownStatuses = listOf("grace_period", "paused", "", "PREMIUM")

        // Act & Assert
        unknownStatuses.forEach { status ->
            assertEquals(
                "\"$status\" should degrade to TRIAL",
                EntitlementStatus.TRIAL,
                dto(status = status).toDomain().status,
            )
        }
    }

    @Test
    fun `null limits stay null so premium reads as unlimited`() {
        // Arrange
        val wire = dto(status = "premium", isPremium = true, plansLimit = null, chatLimit = null)

        // Act
        val domain = wire.toDomain()

        // Assert
        assertNull(domain.plansLimit)
        assertNull(domain.chatLimit)
        assertTrue(domain.canGeneratePlan)
        assertTrue(domain.canSendChatMessage)
        assertNull("unlimited means no countdown to show", domain.chatMessagesLeft)
    }

    @Test
    fun `premium is derived from status, not from the is_premium flag`() {
        // The two can disagree only through a backend bug, and status wins. One source of truth
        // beats two, and status is what every other rule in Entitlement keys off.
        // Arrange
        val contradictory = dto(status = "trial", isPremium = true)

        // Act
        val domain = contradictory.toDomain()

        // Assert
        assertEquals(EntitlementStatus.TRIAL, domain.status)
        assertFalse("status trial wins over is_premium true", domain.isPremium)
    }

    @Test
    fun `trial_ends_at is dropped because the domain counts down from trialDaysLeft instead`() {
        // Arrange
        val wire = dto(trialEndsAt = "2026-10-02T12:00:00.000Z", trialDaysLeft = 14)

        // Act
        val domain = wire.toDomain()

        // Assert
        assertEquals("the day count is what the UI renders", 14, domain.trialDaysLeft)
        // No domain field exists to compare against — that absence is the point.
    }

    // ------------------------------------------------------------------------------------
    // toDto, and the round trip
    // ------------------------------------------------------------------------------------

    @Test
    fun `the quota scalars re-nest cleanly on the way back out`() {
        // Arrange
        val domain = trialEntitlement(
            plansUsed = 1,
            plansLimit = 1,
            chatUsed = 2,
            chatLimit = 2,
            chatResetsAt = "2026-09-19T00:00:00.000Z",
        )

        // Act
        val wire = domain.toDto()

        // Assert
        assertEquals(1, wire.plans.used)
        assertEquals(1, wire.plans.limit)
        assertEquals(2, wire.chat.used)
        assertEquals(2, wire.chat.limit)
        assertEquals("2026-09-19T00:00:00.000Z", wire.chat.resetsAt)
    }

    @Test
    fun `each status round trips to the exact lowercase string the backend sends`() {
        // Arrange, Act & Assert
        assertEquals("premium", premiumEntitlement().toDto().status)
        assertEquals("trial", trialEntitlement().toDto().status)
        assertEquals("expired", expiredEntitlement().toDto().status)
    }

    @Test
    fun `a domain to DTO to domain trip preserves everything the domain models`() {
        // The round trip that DOES hold: whatever the domain carries survives a cache write and
        // read, which is what the DataStore cache depends on.
        // Arrange
        val original = trialEntitlement(
            trialDaysLeft = 9,
            plansUsed = 1,
            plansLimit = 1,
            chatUsed = 1,
            chatLimit = 2,
            chatResetsAt = "2026-09-19T00:00:00.000Z",
        )

        // Act
        val restored = original.toDto().toDomain()

        // Assert
        assertEquals(original, restored)
    }

    @Test
    fun `a premium entitlement round trips with its null limits intact`() {
        // Arrange
        val original = premiumEntitlement()

        // Act
        val restored = original.toDto().toDomain()

        // Assert
        assertEquals(original, restored)
        assertNull(restored.plansLimit)
        assertNull(restored.chatLimit)
    }

    @Test
    fun `a DTO to domain to DTO trip loses trial_ends_at`() {
        // The asymmetry, pinned. The cache write always emits null here, so the server's trial
        // end instant cannot survive a cold start. Acceptable only because trialDaysLeft is what
        // the UI renders and the backend recomputes it on every launch — but if anything ever
        // needs the instant itself, this test is the note explaining why it is missing.
        // Arrange
        val fromServer = dto(trialEndsAt = "2026-10-02T12:00:00.000Z")

        // Act
        val afterRoundTrip = fromServer.toDomain().toDto()

        // Assert
        assertEquals("2026-10-02T12:00:00.000Z", fromServer.trialEndsAt)
        assertNull("dropped on the way back out", afterRoundTrip.trialEndsAt)
    }

    @Test
    fun `a DTO to domain to DTO trip normalises an unknown status to trial`() {
        // Arrange
        val fromServer = dto(status = "grace_period")

        // Act
        val afterRoundTrip = fromServer.toDomain().toDto()

        // Assert
        assertEquals("grace_period", fromServer.status)
        assertEquals("trial", afterRoundTrip.status)
    }

    @Test
    fun `a DTO to domain to DTO trip recomputes is_premium from the status`() {
        // Arrange
        val contradictory = dto(status = "trial", isPremium = true)

        // Act
        val afterRoundTrip = contradictory.toDomain().toDto()

        // Assert
        assertTrue(contradictory.isPremium)
        assertFalse("recomputed from status, so the contradiction is resolved", afterRoundTrip.isPremium)
    }
}
