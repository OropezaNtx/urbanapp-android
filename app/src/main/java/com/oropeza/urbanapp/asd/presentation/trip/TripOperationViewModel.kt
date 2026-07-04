package com.oropeza.urbanapp.asd.presentation.trip

import androidx.lifecycle.ViewModel
import com.oropeza.urbanapp.asd.domain.trip.TripDomain
import com.oropeza.urbanapp.asd.domain.trip.TripOperationResult
import com.oropeza.urbanapp.asd.location.LocationFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TripOperationUiState(
    val closingTripId: Long? = null,
    val isClosing: Boolean = false,
    val lastResult: TripOperationResult? = null,
    val lastErrorMessage: String? = null
)

class TripOperationViewModel : ViewModel() {

    private val useCases = TripDomain.useCases

    private val _uiState = MutableStateFlow(TripOperationUiState())
    val uiState: StateFlow<TripOperationUiState> = _uiState.asStateFlow()

    fun isClosing(tripId: Long): Boolean {
        val state = _uiState.value
        return state.isClosing && state.closingTripId == tripId
    }

    suspend fun closeTrip(tripId: Long, fix: LocationFix): TripOperationResult {
        if (isClosing(tripId)) {
            val ignored = TripOperationResult.AlreadyRunning
            _uiState.update { it.copy(lastResult = ignored, lastErrorMessage = null) }
            return ignored
        }

        _uiState.update {
            it.copy(
                closingTripId = tripId,
                isClosing = true,
                lastResult = null,
                lastErrorMessage = null
            )
        }

        val result = useCases.closeTrip(tripId, fix)

        _uiState.update {
            it.copy(
                closingTripId = null,
                isClosing = false,
                lastResult = result,
                lastErrorMessage = (result as? TripOperationResult.Failed)?.throwable?.message
            )
        }

        return result
    }

    fun clearResult() {
        _uiState.update { it.copy(lastResult = null, lastErrorMessage = null) }
    }
}
