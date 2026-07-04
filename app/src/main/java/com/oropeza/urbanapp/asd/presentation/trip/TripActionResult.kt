package com.oropeza.urbanapp.asd.presentation.trip

sealed class TripActionResult {
    data object Success : TripActionResult()
    data object Ignored : TripActionResult()
    data class Failed(val message: String) : TripActionResult()

    val isSuccess: Boolean
        get() = this is Success
}
