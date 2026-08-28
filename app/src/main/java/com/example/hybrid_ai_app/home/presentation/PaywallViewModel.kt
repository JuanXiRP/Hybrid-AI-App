package com.example.hybrid_ai_app.home.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.example.hybrid_ai_app.core.data.BillingManager
import com.example.hybrid_ai_app.core.data.ConnectionState
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PurchaseEvent
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The recurring price, as Play reports it — already localized and in the user's currency. */
data class SubscriptionOffer(
    val formattedPrice: String,
    /** ISO-8601 duration, e.g. "P1M". */
    val billingPeriodIso: String,
    /** ISO-8601 duration of the introductory free phase, if the Play product defines one. */
    val freeTrialPeriodIso: String? = null,
)

data class PaywallUiState(
    val offer: SubscriptionOffer? = null,
    val offerError: String? = null,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
) {
    val isOfferLoading: Boolean get() = offer == null && offerError == null
    val isBusy: Boolean get() = isPurchasing || isRestoring
}

/** One-shot outcomes. Not state — replaying these would re-navigate or re-notify. */
sealed interface PaywallEvent {
    data object PurchaseSucceeded : PaywallEvent
    data object PurchaseRestored : PaywallEvent
    data object NothingToRestore : PaywallEvent
    data object PurchaseCancelled : PaywallEvent
    data object PurchasePending : PaywallEvent
    data class Error(val message: String) : PaywallEvent
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val userRepository: UserRepository,
    private val entitlementManager: EntitlementManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
    val events: Flow<PaywallEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            billingManager.productDetails.collect { details ->
                _uiState.update { it.copy(offer = details?.toOffer()) }
            }
        }

        viewModelScope.launch {
            billingManager.connectionState.collect { state ->
                if (state is ConnectionState.Error) {
                    _uiState.update { it.copy(offerError = state.message) }
                }
            }
        }

        // purchaseEvents is a SharedFlow with replay = 0, so a Success from a previous instance
        // of this ViewModel cannot re-trigger verification here.
        viewModelScope.launch {
            billingManager.purchaseEvents.collect(::handlePurchaseEvent)
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        _uiState.update { it.copy(isPurchasing = true) }
        billingManager.launchBillingFlow(activity)
    }

    /**
     * Required by Google Play review. Asks Play what this account already owns and replays each
     * token through the backend, which is idempotent.
     */
    fun restorePurchases() {
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true) }

            val purchases = billingManager.queryPurchases()
            if (purchases.isEmpty()) {
                _uiState.update { it.copy(isRestoring = false) }
                _events.send(PaywallEvent.NothingToRestore)
                return@launch
            }

            val restored = purchases.any { purchase ->
                userRepository
                    .verifyPurchase(purchase.purchaseToken, purchase.products.firstOrNull())
                    .getOrNull()
                    ?.isPremium == true
            }

            _uiState.update { it.copy(isRestoring = false) }

            if (restored) {
                entitlementManager.refresh()
                _events.send(PaywallEvent.PurchaseRestored)
            } else {
                // Play knows about a purchase the backend refuses to honour: expired, or already
                // bound to a different account.
                _events.send(PaywallEvent.NothingToRestore)
            }
        }
    }

    private suspend fun handlePurchaseEvent(event: PurchaseEvent) {
        when (event) {
            is PurchaseEvent.Purchased -> verifyWithBackend(event.purchaseToken, event.productIds.firstOrNull())

            // The user already owns it but this install did not know — same path as a restore.
            PurchaseEvent.AlreadyOwned -> restorePurchases()

            PurchaseEvent.Pending -> {
                _uiState.update { it.copy(isPurchasing = false) }
                _events.send(PaywallEvent.PurchasePending)
            }

            PurchaseEvent.Cancelled -> {
                _uiState.update { it.copy(isPurchasing = false) }
                _events.send(PaywallEvent.PurchaseCancelled)
            }

            is PurchaseEvent.Failed -> {
                _uiState.update { it.copy(isPurchasing = false) }
                _events.send(PaywallEvent.Error(event.message))
            }
        }
    }

    /**
     * The purchase is not real until the server says so. The backend validates the token against
     * the Play Developer API, persists the entitlement, and only then acknowledges it with Google.
     */
    private suspend fun verifyWithBackend(purchaseToken: String, productId: String?) {
        userRepository.verifyPurchase(purchaseToken, productId)
            .onSuccess {
                entitlementManager.refresh()
                _uiState.update { state -> state.copy(isPurchasing = false) }
                _events.send(PaywallEvent.PurchaseSucceeded)
            }
            .onFailure { error ->
                _uiState.update { state -> state.copy(isPurchasing = false) }
                _events.send(PaywallEvent.Error(error.message ?: "Could not verify the purchase."))
            }
    }
}

private fun ProductDetails.toOffer(): SubscriptionOffer? {
    val phases = subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList
        ?: return null

    // The recurring phase is always last; anything before it is an intro or free-trial phase.
    val recurring = phases.lastOrNull() ?: return null
    val freeTrial = phases.firstOrNull { it.priceAmountMicros == 0L }

    return SubscriptionOffer(
        formattedPrice = recurring.formattedPrice,
        billingPeriodIso = recurring.billingPeriod,
        freeTrialPeriodIso = freeTrial?.billingPeriod,
    )
}
