package com.example.hybrid_ai_app.home.presentation

import com.android.billingclient.api.Purchase
import com.example.hybrid_ai_app.core.data.BillingManager
import com.example.hybrid_ai_app.core.data.ConnectionState
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PurchaseEvent
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var billingManager: BillingManager
    private lateinit var userRepository: UserRepository
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var viewModel: PaywallViewModel

    private val purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    private val productDetails = MutableStateFlow<com.android.billingclient.api.ProductDetails?>(null)
    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)

    private val premium = Entitlement(
        status = EntitlementStatus.PREMIUM,
        trialDaysLeft = 0,
        plansUsed = 1,
        plansLimit = null,
        chatUsed = 0,
        chatLimit = null,
        chatResetsAt = null,
    )

    private val freeTrial = premium.copy(
        status = EntitlementStatus.TRIAL,
        plansLimit = 1,
        chatLimit = 2,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        billingManager = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        entitlementManager = mockk(relaxed = true)

        every { billingManager.purchaseEvents } returns purchaseEvents
        every { billingManager.productDetails } returns productDetails
        every { billingManager.connectionState } returns connectionState
        coEvery { entitlementManager.refresh() } returns Result.success(premium)

        viewModel = PaywallViewModel(billingManager, userRepository, entitlementManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectEvents(into: MutableList<PaywallEvent>): Job =
        launch(testDispatcher) { viewModel.events.toList(into) }

    private fun mockPurchase(token: String, product: String): Purchase = mockk {
        every { purchaseToken } returns token
        every { products } returns listOf(product)
    }

    // ==================== PURCHASE ====================

    @Test
    fun `a completed purchase is verified with the backend before success is reported`() = runTest {
        coEvery { userRepository.verifyPurchase("tok-1", "hybrid_ai_pro_monthly") } returns
            Result.success(premium)

        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        purchaseEvents.emit(PurchaseEvent.Purchased("tok-1", listOf("hybrid_ai_pro_monthly")))
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.verifyPurchase("tok-1", "hybrid_ai_pro_monthly") }
        coVerify { entitlementManager.refresh() }
        assertEquals(listOf(PaywallEvent.PurchaseSucceeded), events)
        assertFalse(viewModel.uiState.value.isPurchasing)

        job.cancel()
    }

    @Test
    fun `a purchase the backend rejects does not report success`() = runTest {
        coEvery { userRepository.verifyPurchase(any(), any()) } returns
            Result.failure(Exception("This subscription is already linked to another account."))

        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        purchaseEvents.emit(PurchaseEvent.Purchased("tok-1", listOf("hybrid_ai_pro_monthly")))
        advanceUntilIdle()

        assertEquals(1, events.size)
        val event = events.single()
        assertTrue(event is PaywallEvent.Error)
        assertEquals(
            "This subscription is already linked to another account.",
            (event as PaywallEvent.Error).message,
        )
        assertFalse(viewModel.uiState.value.isPurchasing)

        job.cancel()
    }

    @Test
    fun `cancelling the Play dialog clears the spinner and reports cancellation`() = runTest {
        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        viewModel.launchPurchaseFlow(mockk(relaxed = true))
        assertTrue(viewModel.uiState.value.isPurchasing)

        purchaseEvents.emit(PurchaseEvent.Cancelled)
        advanceUntilIdle()

        assertEquals(listOf(PaywallEvent.PurchaseCancelled), events)
        assertFalse(viewModel.uiState.value.isPurchasing)
        coVerify(exactly = 0) { userRepository.verifyPurchase(any(), any()) }

        job.cancel()
    }

    @Test
    fun `a pending purchase does not grant premium`() = runTest {
        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        purchaseEvents.emit(PurchaseEvent.Pending)
        advanceUntilIdle()

        assertEquals(listOf(PaywallEvent.PurchasePending), events)
        coVerify(exactly = 0) { userRepository.verifyPurchase(any(), any()) }

        job.cancel()
    }

    // ==================== RESTORE ====================

    @Test
    fun `restore with no Play purchases reports nothing to restore`() = runTest {
        coEvery { billingManager.queryPurchases() } returns emptyList()

        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        viewModel.restorePurchases()
        advanceUntilIdle()

        assertEquals(listOf(PaywallEvent.NothingToRestore), events)
        assertFalse(viewModel.uiState.value.isRestoring)
        coVerify(exactly = 0) { userRepository.verifyPurchase(any(), any()) }

        job.cancel()
    }

    @Test
    fun `restore replays each owned token through the backend`() = runTest {
        coEvery { billingManager.queryPurchases() } returns
            listOf(mockPurchase("tok-1", "hybrid_ai_pro_monthly"))
        coEvery { userRepository.verifyPurchase("tok-1", "hybrid_ai_pro_monthly") } returns
            Result.success(premium)

        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        viewModel.restorePurchases()
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.verifyPurchase("tok-1", "hybrid_ai_pro_monthly") }
        assertEquals(listOf(PaywallEvent.PurchaseRestored), events)
        assertFalse(viewModel.uiState.value.isRestoring)

        job.cancel()
    }

    @Test
    fun `restore reports nothing when the backend refuses to honour the token`() = runTest {
        // Play believes the account owns it, but the server says it is expired or bound elsewhere.
        coEvery { billingManager.queryPurchases() } returns
            listOf(mockPurchase("tok-1", "hybrid_ai_pro_monthly"))
        coEvery { userRepository.verifyPurchase(any(), any()) } returns
            Result.failure(Exception("This subscription is already linked to another account."))

        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        viewModel.restorePurchases()
        advanceUntilIdle()

        assertEquals(listOf(PaywallEvent.NothingToRestore), events)

        job.cancel()
    }

    @Test
    fun `restore does not grant premium when the backend returns a non-premium entitlement`() = runTest {
        coEvery { billingManager.queryPurchases() } returns
            listOf(mockPurchase("tok-1", "hybrid_ai_pro_monthly"))
        coEvery { userRepository.verifyPurchase(any(), any()) } returns Result.success(freeTrial)

        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        viewModel.restorePurchases()
        advanceUntilIdle()

        assertEquals(listOf(PaywallEvent.NothingToRestore), events)

        job.cancel()
    }

    @Test
    fun `ITEM_ALREADY_OWNED is treated as a restore`() = runTest {
        coEvery { billingManager.queryPurchases() } returns
            listOf(mockPurchase("tok-1", "hybrid_ai_pro_monthly"))
        coEvery { userRepository.verifyPurchase("tok-1", "hybrid_ai_pro_monthly") } returns
            Result.success(premium)

        val events = mutableListOf<PaywallEvent>()
        val job = collectEvents(events)

        purchaseEvents.emit(PurchaseEvent.AlreadyOwned)
        advanceUntilIdle()

        coVerify { billingManager.queryPurchases() }
        assertEquals(listOf(PaywallEvent.PurchaseRestored), events)

        job.cancel()
    }

    // ==================== OFFER / CONNECTION ====================

    @Test
    fun `the buy button has no offer until Play resolves the product`() = runTest {
        assertNull(viewModel.uiState.value.offer)
        assertTrue(viewModel.uiState.value.isOfferLoading)
    }

    @Test
    fun `a billing connection failure surfaces as an offer error, not a silent dead button`() = runTest {
        connectionState.value = ConnectionState.Error("Product not found in Play Console.")
        advanceUntilIdle()

        assertEquals("Product not found in Play Console.", viewModel.uiState.value.offerError)
        assertFalse(viewModel.uiState.value.isOfferLoading)
    }
}
