package com.example.hybrid_ai_app.home.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.BillingManager
import com.example.hybrid_ai_app.core.data.PurchaseState
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val userRepository: UserRepository
) : ViewModel() {

    val purchaseState = billingManager.purchaseState

    private val _upgradeStatus = MutableStateFlow<UpgradeStatus>(UpgradeStatus.Idle)
    val upgradeStatus: StateFlow<UpgradeStatus> = _upgradeStatus.asStateFlow()

    init {
        // Observe Google Play Billing events
        viewModelScope.launch {
            purchaseState.collect { state ->
                when (state) {
                    is PurchaseState.Success -> verifyAndUpgradeUserInBackend(state.purchaseToken)
                    // 🟢 Si hay error en Google Play, paramos el Loading y mostramos el error
                    is PurchaseState.Error -> _upgradeStatus.value = UpgradeStatus.Error(state.message)
                    else -> {}
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        _upgradeStatus.value = UpgradeStatus.Loading

        //MODO PRODUCCIÓN (Comentado hasta que configures Google Play Console)
        // billingManager.launchBillingFlow(activity)

        // MODO DESARROLLO: Simulamos que Google Play fue un éxito tras 2 segundos
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // Ruedita gira 2 segundos
            verifyAndUpgradeUserInBackend("dummy_test_token_12345")
        }
    }

    private fun verifyAndUpgradeUserInBackend(purchaseToken: String) {
        viewModelScope.launch {
            try {
                // ⚠️ ARCHITECTURE NOTE: In a real production app, you send the `purchaseToken`
                // to Node.js. Node.js asks Google Developer API if the token is valid,
                // and if true, Node.js sets isPremium = true in MongoDB.
                // For now, assuming you have an endpoint like PATCH /api/users/upgrade:

                val response = userRepository.upgradeToPremium(purchaseToken)
                if (response.isSuccess) {
                    _upgradeStatus.value = UpgradeStatus.Success
                } else {
                    _upgradeStatus.value = UpgradeStatus.Error("Failed to update server profile.")
                }
            } catch (e: Exception) {
                _upgradeStatus.value = UpgradeStatus.Error(e.message ?: "Network error")
            }
        }
    }
}

sealed interface UpgradeStatus {
    object Idle : UpgradeStatus
    object Loading : UpgradeStatus
    object Success : UpgradeStatus
    data class Error(val message: String) : UpgradeStatus
}