package com.example.hybrid_ai_app.core.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    // Replace with your actual product ID from Google Play Console
    private val SUBSCRIPTION_ID = "hybrid_ai_pro_monthly"

    init {
        connectToBilling()
    }

    private fun connectToBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Ready to query products
                }
            }
            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request
            }
        })
    }

    fun launchBillingFlow(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(SUBSCRIPTION_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                ).build()

            billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                    val productDetails = productDetailsList.first()
                    val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                    if (offerToken != null) {
                        val productDetailsParamsList = listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .setOfferToken(offerToken)
                                .build()
                        )

                        val billingFlowParams = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(productDetailsParamsList)
                            .build()

                        // 🟢 FIX 1: Google Play exige abrir su panel desde el Hilo Principal (Main)
                        CoroutineScope(Dispatchers.Main).launch {
                            billingClient.launchBillingFlow(activity, billingFlowParams)
                        }
                    } else {
                        _purchaseState.value = PurchaseState.Error("No se encontró una oferta válida.")
                    }
                } else {
                    // 🟢 FIX 2: Si el producto no existe (Play Console sin configurar), rompemos el bucle infinito
                    _purchaseState.value = PurchaseState.Error("Producto no encontrado en Google Play Console.")
                }
            }
        }
    }

    // Callback from Google Play when a transaction occurs
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseState.value = PurchaseState.Error("User cancelled the purchase.")
        } else {
            _purchaseState.value = PurchaseState.Error("Billing Error: ${billingResult.debugMessage}")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Purchase successful and verified by Google
                    _purchaseState.value = PurchaseState.Success(purchase.purchaseToken)
                }
            }
        } else {
            _purchaseState.value = PurchaseState.Success(purchase.purchaseToken)
        }
    }
}

// State wrapper
sealed interface PurchaseState {
    object Idle : PurchaseState
    object Loading : PurchaseState
    data class Success(val purchaseToken: String) : PurchaseState
    data class Error(val message: String) : PurchaseState
}