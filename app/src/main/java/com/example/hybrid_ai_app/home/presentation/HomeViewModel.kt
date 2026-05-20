package com.example.hybrid_ai_app.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.home.domain.model.ActivePlan
import com.example.hybrid_ai_app.home.domain.usecase.GetActivePlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val plan: ActivePlan) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getActivePlanUseCase: GetActivePlanUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // TODO: En la siguiente iteración, consumiremos este token del UserRepository de sesión
        val mockToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjZhMGI0YWRiOWU3ZWU4ZTQ5NGRiYjllZSIsImlhdCI6MTc3OTEyNDk1NSwiZXhwIjoxNzgxNzE2OTU1fQ.9IlYxRJajkUJBrdq98JJzOWXFfVHduHVLrmgd7t1hy0"
        fetchActivePlan(mockToken)
    }

    fun fetchActivePlan(token: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = getActivePlanUseCase(token)
            result.onSuccess { plan ->
                _uiState.value = HomeUiState.Success(plan)
            }.onFailure { exception ->
                _uiState.value = HomeUiState.Error(exception.message ?: "Unknown Error occurred")
            }
        }
    }
}