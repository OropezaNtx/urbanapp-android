package com.oropeza.urbanapp.asd.domain.trip

import com.oropeza.urbanapp.asd.location.LocationFix
import com.oropeza.urbanapp.asd.operations.TripOperationManager

class CloseTripUseCase(
    private val operationManager: TripOperationManager = TripOperationManager
) {
    suspend operator fun invoke(tripId: Long, fix: LocationFix): TripOperationResult {
        return when (val result = operationManager.closeTrip(tripId, fix)) {
            TripOperationManager.OperationResult.Success -> TripOperationResult.Success
            TripOperationManager.OperationResult.AlreadyRunning -> TripOperationResult.AlreadyRunning
            TripOperationManager.OperationResult.AlreadyClosed -> TripOperationResult.AlreadyClosed
            is TripOperationManager.OperationResult.Failed -> TripOperationResult.Failed(result.throwable)
        }
    }
}
