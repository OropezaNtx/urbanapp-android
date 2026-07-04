package com.oropeza.urbanapp.asd.domain.trip

/**
 * Domain entrypoint for ASD trip operations.
 *
 * ViewModels should depend on this object instead of calling Repository directly
 * for mutations. Repository remains the persistence/sync boundary, while use cases
 * own operation flow and guards.
 */
data class TripDomainUseCases(
    val closeTrip: CloseTripUseCase = CloseTripUseCase(),
    val addStop: AddStopUseCase = AddStopUseCase(),
    val updateHeader: UpdateTripHeaderUseCase = UpdateTripHeaderUseCase()
)
