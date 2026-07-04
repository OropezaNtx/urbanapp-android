package com.oropeza.urbanapp.asd.domain.trip

sealed class TripOperationResult {
    data object Success : TripOperationResult()
    data object AlreadyRunning : TripOperationResult()
    data object AlreadyClosed : TripOperationResult()
    data class Failed(val throwable: Throwable) : TripOperationResult()

    val isSuccess: Boolean
        get() = this is Success || this is AlreadyClosed
}
