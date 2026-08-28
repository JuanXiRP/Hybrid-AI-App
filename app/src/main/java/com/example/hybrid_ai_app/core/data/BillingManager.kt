package com.example.hybrid_ai_app.core.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Owns the Play Billing connection and exposes it as flows.
 *
 * Two deliberate design points:
 *
 * 1. Purchases are emitted as a **one-shot [SharedFlow]**, not a StateFlow. A replayed
 *    `Success` would re-trigger backend verification every time a collector re-subscribes
 *    (e.g. on ViewModel recreation after a rotation).
 *
 * 2. This class does **not** acknowledge purchases. The backend does, after it has validated
 *    the token against the Play Developer API and persisted the entitlement. Acknowledging here
 *    would tell Google "granted" before we know that the grant survived. Google auto-refunds
 *    anything unacknowledged after 3 days, which is the safety net if the backend never sees it.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // The no-arg overload was removed in Billing 8. One-time products are enabled to match
        // the previous behaviour, even though we currently only sell a subscription.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** One-shot purchase results. `replay = 0` so a stale Success never re-fires verification. */
    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents.asSharedFlow()

    /** Null until Play resolves the product. Drives the paywall's price and period. */
    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var reconnectAttempts = 0

    init {
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectAttempts = 0
                    _connectionState.value = ConnectionState.Connected
                    // Query as soon as we connect, not lazily inside launchBillingFlow — the
                    // paywall needs the real price before the user taps anything.
                    scope.launch { refreshProductDetails() }
                } else {
                    // Previously swallowed: a setup failure left the paywall silently dead.
                    _connectionState.value =
                        ConnectionState.Error(billingResult.debugMessage.ifBlank { "Billing setup failed" })
                    scheduleReconnect()
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = ConnectionState.Connecting
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.Error("Could not reach Google Play.")
            return
        }
        val backoffMs = INITIAL_BACKOFF_MS shl reconnectAttempts
        reconnectAttempts++
        scope.launch {
            delay(backoffMs)
            connect()
        }
    }

    /** Fetches the subscription's ProductDetails. Safe to call repeatedly. */
    suspend fun refreshProductDetails(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val details = suspendCancellableCoroutine { continuation ->
            // Billing 8: the callback receives QueryProductDetailsResult, which splits the
            // response into fetched details and per-product failure codes.
            billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "queryProductDetails failed: ${billingResult.debugMessage}")
                    continuation.resume(null)
                    return@queryProductDetailsAsync
                }
                result.unfetchedProductList.forEach {
                    Log.w(TAG, "Unfetched product ${it.productId}: status ${it.statusCode}")
                }
                continuation.resume(result.productDetailsList.firstOrNull())
            }
        }

        _productDetails.value = details
        if (details == null) {
            _connectionState.value =
                ConnectionState.Error("Product $SUBSCRIPTION_ID not found in Play Console.")
        }
        return details
    }

    fun launchBillingFlow(activity: Activity) {
        scope.launch {
            val details = _productDetails.value ?: refreshProductDetails()
            if (details == null) {
                _purchaseEvents.emit(PurchaseEvent.Failed("Subscription is unavailable right now."))
                return@launch
            }

            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _purchaseEvents.emit(PurchaseEvent.Failed("No valid offer found for this subscription."))
                return@launch
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()

            // launchBillingFlow must be called on the main thread.
            CoroutineScope(Dispatchers.Main).launch {
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }

    /**
     * Every subscription Play believes this user owns. Backs "Restore Purchases": the caller
     * replays each token through the backend, which is idempotent.
     */
    suspend fun queryPurchases(): List<Purchase> = suspendCancellableCoroutine { continuation ->
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                continuation.resume(purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED })
            } else {
                Log.w(TAG, "queryPurchases failed: ${billingResult.debugMessage}")
                continuation.resume(emptyList())
            }
        }
    }

    /**
     * Marks a purchase acknowledged locally. Only used as a fallback if the backend reports it
     * verified the token but could not reach Play to acknowledge; the backend is the primary
     * acknowledger. Never call this before the server has confirmed the grant.
     */
    suspend fun acknowledgeLocally(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.acknowledgePurchase(params) { result ->
                continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            when {
                billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                    purchases
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .forEach { _purchaseEvents.emit(PurchaseEvent.Purchased(it.purchaseToken, it.products)) }

                    if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                        _purchaseEvents.emit(PurchaseEvent.Pending)
                    }
                }

                billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                    _purchaseEvents.emit(PurchaseEvent.Cancelled)

                billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                    // The user owns it but this install does not know: treat as a restore.
                    _purchaseEvents.emit(PurchaseEvent.AlreadyOwned)

                else -> _purchaseEvents.emit(
                    PurchaseEvent.Failed(billingResult.debugMessage.ifBlank { "Purchase failed." })
                )
            }
        }
    }

    private companion object {
        const val TAG = "BillingManager"
        const val SUBSCRIPTION_ID = "hybrid_ai_pro_monthly"
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val INITIAL_BACKOFF_MS = 1_000L
    }
}

/** One-shot outcomes of a purchase attempt. */
sealed interface PurchaseEvent {
    data class Purchased(val purchaseToken: String, val productIds: List<String>) : PurchaseEvent
    data object Pending : PurchaseEvent
    data object Cancelled : PurchaseEvent
    data object AlreadyOwned : PurchaseEvent
    data class Failed(val message: String) : PurchaseEvent
}

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}
