package com.example.hybrid_ai_app.home.presentation

import android.app.Activity
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.example.hybrid_ai_app.core.data.BillingManager
import com.example.hybrid_ai_app.core.data.ConnectionState
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PurchaseEvent
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.TestIds
import com.example.hybrid_ai_app.testing.premiumEntitlement
import com.example.hybrid_ai_app.testing.trialEntitlement
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The subscription paywall: buying, restoring, and never granting premium the backend has not
 * confirmed.
 *
 * Events are asserted with Turbine rather than by launching a job that collects into a
 * `MutableList`, which is how this suite used to work. The difference matters: a hand-collected
 * list can only be inspected after the fact and silently passes when an expected event never
 * arrives, whereas `awaitItem()` fails at the point the event was due.
 *
 * The rule throughout: Play saying a purchase happened is not enough. Only a backend
 * `verifyPurchase` that returns a premium entitlement counts, because the server is what gates
 * the features.
 */
class PaywallViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var billingManager: BillingManager
    private lateinit var userRepository: UserRepository
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var viewModel: PaywallViewModel

    private lateinit var purchaseEvents: MutableSharedFlow<PurchaseEvent>
    private lateinit var productDetails: MutableStateFlow<ProductDetails?>
    private lateinit var connectionState: MutableStateFlow<ConnectionState>

    private val monthlyProduct = "hybrid_ai_pro_monthly"

    @Before
    fun setUp() {
        billingManager = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        entitlementManager = mockk(relaxed = true)

        // init launches three collectors, so these must be real flows before construction.
        purchaseEvents = MutableSharedFlow(extraBufferCapacity = 8)
        productDetails = MutableStateFlow(null)
        connectionState = MutableStateFlow(ConnectionState.Connected)

        every { billingManager.purchaseEvents } returns purchaseEvents
        every { billingManager.productDetails } returns productDetails
        every { billingManager.connectionState } returns connectionState
        coEvery { entitlementManager.refresh() } returns Result.success(premiumEntitlement())

        viewModel = PaywallViewModel(billingManager, userRepository, entitlementManager)
    }

    /** A Play purchase carrying a fresh token for one product. */
    private fun purchase(token: String, product: String = monthlyProduct): Purchase = mockk {
        every { purchaseToken } returns token
        every { products } returns listOf(product)
    }

    // ==================== PURCHASE ====================

    @Test
    fun `a completed purchase is verified with the backend before success is reported`() = runTest {
        // Arrange
        val token = TestIds.uniquePurchaseToken()
        coEvery { userRepository.verifyPurchase(token, monthlyProduct) } returns
            Result.success(premiumEntitlement())

        // Act & Assert
        viewModel.events.test {
            purchaseEvents.emit(PurchaseEvent.Purchased(token, listOf(monthlyProduct)))
            advanceUntilIdle()

            assertEquals(PaywallEvent.PurchaseSucceeded, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { userRepository.verifyPurchase(token, monthlyProduct) }
        coVerify { entitlementManager.refresh() }
        assertFalse(viewModel.uiState.value.isPurchasing)
    }

    @Test
    fun `a purchase the backend rejects does not report success`() = runTest {
        // Arrange
        val token = TestIds.uniquePurchaseToken()
        coEvery { userRepository.verifyPurchase(any(), any()) } returns Result.failure(
            Exception("This subscription is already linked to another account."),
        )

        // Act & Assert
        viewModel.events.test {
            purchaseEvents.emit(PurchaseEvent.Purchased(token, listOf(monthlyProduct)))
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue("expected an error event, got $event", event is PaywallEvent.Error)
            assertEquals(
                "This subscription is already linked to another account.",
                (event as PaywallEvent.Error).message,
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.isPurchasing)
    }

    @Test
    fun `a rejection with no message still reports something actionable`() = runTest {
        // Arrange
        coEvery { userRepository.verifyPurchase(any(), any()) } returns
            Result.failure(Exception())

        // Act & Assert
        viewModel.events.test {
            purchaseEvents.emit(
                PurchaseEvent.Purchased(TestIds.uniquePurchaseToken(), listOf(monthlyProduct)),
            )
            advanceUntilIdle()

            assertEquals(
                PaywallEvent.Error("Could not verify the purchase."),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the Play dialog clears the spinner and reports cancellation`() = runTest {
        // Arrange
        viewModel.launchPurchaseFlow(mockk<Activity>(relaxed = true))
        assertTrue("the spinner is on while Play is open", viewModel.uiState.value.isPurchasing)

        // Act & Assert
        viewModel.events.test {
            purchaseEvents.emit(PurchaseEvent.Cancelled)
            advanceUntilIdle()

            assertEquals(PaywallEvent.PurchaseCancelled, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.isPurchasing)
        coVerify(exactly = 0) { userRepository.verifyPurchase(any(), any()) }
    }

    @Test
    fun `a pending purchase does not grant premium`() = runTest {
        // A pending purchase is one Play has accepted but not charged — e.g. cash payment.
        // Arrange & Act & Assert
        viewModel.events.test {
            purchaseEvents.emit(PurchaseEvent.Pending)
            advanceUntilIdle()

            assertEquals(PaywallEvent.PurchasePending, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { userRepository.verifyPurchase(any(), any()) }
    }

    @Test
    fun `a Play-side failure is surfaced and clears the spinner`() = runTest {
        // Arrange
        viewModel.launchPurchaseFlow(mockk<Activity>(relaxed = true))

        // Act & Assert
        viewModel.events.test {
            purchaseEvents.emit(PurchaseEvent.Failed("Billing unavailable"))
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PaywallEvent.Error)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.isPurchasing)
    }

    // ==================== RESTORE ====================

    @Test
    fun `restore with no Play purchases reports nothing to restore`() = runTest {
        // Arrange
        coEvery { billingManager.queryPurchases() } returns emptyList()

        // Act & Assert
        viewModel.events.test {
            viewModel.restorePurchases()
            advanceUntilIdle()

            assertEquals(PaywallEvent.NothingToRestore, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.isRestoring)
        coVerify(exactly = 0) { userRepository.verifyPurchase(any(), any()) }
    }

    @Test
    fun `restore replays each owned token through the backend`() = runTest {
        // Arrange
        val token = TestIds.uniquePurchaseToken()
        coEvery { billingManager.queryPurchases() } returns listOf(purchase(token))
        coEvery { userRepository.verifyPurchase(token, monthlyProduct) } returns
            Result.success(premiumEntitlement())

        // Act & Assert
        viewModel.events.test {
            viewModel.restorePurchases()
            advanceUntilIdle()

            assertEquals(PaywallEvent.PurchaseRestored, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { userRepository.verifyPurchase(token, monthlyProduct) }
        assertFalse(viewModel.uiState.value.isRestoring)
    }

    @Test
    fun `restore reports nothing when the backend refuses to honour the token`() = runTest {
        // Play believes the account owns it, but the server says it is expired or bound elsewhere.
        // Arrange
        coEvery { billingManager.queryPurchases() } returns
            listOf(purchase(TestIds.uniquePurchaseToken()))
        coEvery { userRepository.verifyPurchase(any(), any()) } returns Result.failure(
            Exception("This subscription is already linked to another account."),
        )

        // Act & Assert
        viewModel.events.test {
            viewModel.restorePurchases()
            advanceUntilIdle()

            assertEquals(PaywallEvent.NothingToRestore, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restore does not grant premium when the backend returns a non-premium entitlement`() = runTest {
        // The server is authoritative: a token it accepts but does not treat as premium (an
        // expired subscription, say) must not unlock the app.
        // Arrange
        coEvery { billingManager.queryPurchases() } returns
            listOf(purchase(TestIds.uniquePurchaseToken()))
        coEvery { userRepository.verifyPurchase(any(), any()) } returns
            Result.success(trialEntitlement())

        // Act & Assert
        viewModel.events.test {
            viewModel.restorePurchases()
            advanceUntilIdle()

            assertEquals(PaywallEvent.NothingToRestore, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restore succeeds when any one of several tokens verifies`() = runTest {
        // Arrange — an old expired token alongside the live one
        val staleToken = TestIds.uniquePurchaseToken()
        val liveToken = TestIds.uniquePurchaseToken()
        coEvery { billingManager.queryPurchases() } returns
            listOf(purchase(staleToken), purchase(liveToken))
        coEvery { userRepository.verifyPurchase(staleToken, monthlyProduct) } returns
            Result.success(trialEntitlement())
        coEvery { userRepository.verifyPurchase(liveToken, monthlyProduct) } returns
            Result.success(premiumEntitlement())

        // Act & Assert
        viewModel.events.test {
            viewModel.restorePurchases()
            advanceUntilIdle()

            assertEquals(PaywallEvent.PurchaseRestored, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ITEM_ALREADY_OWNED is treated as a restore`() = runTest {
        // Play refuses to sell twice, so the only way forward is to re-verify what is owned.
        // Arrange
        val token = TestIds.uniquePurchaseToken()
        coEvery { billingManager.queryPurchases() } returns listOf(purchase(token))
        coEvery { userRepository.verifyPurchase(token, monthlyProduct) } returns
            Result.success(premiumEntitlement())

        // Act & Assert
        viewModel.events.test {
            purchaseEvents.emit(PurchaseEvent.AlreadyOwned)
            advanceUntilIdle()

            assertEquals(PaywallEvent.PurchaseRestored, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { billingManager.queryPurchases() }
    }

    @Test
    fun `the busy guard only holds once the restore coroutine has actually started`() = runTest {
        // A real re-entrancy gap, pinned rather than papered over. `restorePurchases()` checks
        // `isBusy` synchronously, but sets `isRestoring = true` *inside* the launched coroutine —
        // so two calls that land before the coroutine body runs both pass the guard, and the
        // backend sees two rounds of verification. On a device that is a fast double-tap on
        // "Restore purchases". Harmless today (verification is idempotent), but it is the kind of
        // thing worth knowing before adding a non-idempotent step to that flow.
        //
        // Closing it means moving the flag set to before `launch`.
        // Arrange
        coEvery { billingManager.queryPurchases() } returns emptyList()

        // Act
        viewModel.restorePurchases()
        viewModel.restorePurchases()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 2) { billingManager.queryPurchases() }
    }

    @Test
    fun `a restore requested while a Play query is in flight is ignored`() = runTest {
        // The complement to the test above: once the coroutine is actually suspended mid-flight,
        // the guard does its job. `queryPurchases` is made to suspend because that is what it does
        // in production — it is a round trip to Play. With an instantly-returning mock the whole
        // body completes inside the first `runCurrent()` and there is no in-flight window at all,
        // which is what made an earlier version of this test assert something untestable.
        // Arrange
        coEvery { billingManager.queryPurchases() } coAnswers {
            delay(PLAY_ROUND_TRIP_MILLIS)
            listOf(purchase(TestIds.uniquePurchaseToken()))
        }
        coEvery { userRepository.verifyPurchase(any(), any()) } returns
            Result.success(premiumEntitlement())

        // Act
        viewModel.restorePurchases()
        runCurrent()
        assertTrue("the restore is in flight", viewModel.uiState.value.isBusy)
        viewModel.restorePurchases()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { billingManager.queryPurchases() }
    }

    private companion object {
        /** Long enough to leave the restore suspended across a `runCurrent()`. */
        const val PLAY_ROUND_TRIP_MILLIS = 50L
    }

    // ==================== OFFER / CONNECTION ====================

    @Test
    fun `the buy button has no offer until Play resolves the product`() = runTest {
        // Arrange, Act & Assert
        assertNull(viewModel.uiState.value.offer)
        assertTrue(viewModel.uiState.value.isOfferLoading)
        assertNull(viewModel.uiState.value.offerError)
    }

    @Test
    fun `a billing connection failure surfaces as an offer error, not a silent dead button`() = runTest {
        // Arrange & Act
        connectionState.value = ConnectionState.Error("Product not found in Play Console.")
        advanceUntilIdle()

        // Assert
        assertEquals(
            "Product not found in Play Console.",
            viewModel.uiState.value.offerError,
        )
        assertFalse(viewModel.uiState.value.isOfferLoading)
    }

    @Test
    fun `the busy flag covers both purchasing and restoring`() = runTest {
        // Arrange
        coEvery { billingManager.queryPurchases() } returns emptyList()
        assertFalse(viewModel.uiState.value.isBusy)

        // Act
        viewModel.launchPurchaseFlow(mockk<Activity>(relaxed = true))

        // Assert
        assertTrue(viewModel.uiState.value.isBusy)
    }
}
