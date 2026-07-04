package com.oropeza.urbanapp.asd.domain.trip

import com.oropeza.urbanapp.asd.AsdGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateTripHeaderUseCase {
    suspend operator fun invoke(
        tripId: Long,
        routeName: String,
        company: String?,
        vehicleEco: String?,
        direction: String,
        routeNumber: Int?,
        esFs: String?,
        baseStart: String?,
        baseEnd: String?,
        plateNumber: String?,
        vehicleType: String?,
        seatCapacity: Int?,
        notes: String?,
        aforador: String?,
        supervisor: String?,
        deviceNumber: String?,
        observerSex: String?
    ): TripOperationResult = withContext(Dispatchers.IO) {
        try {
            val ok = AsdGraph.repo.updateTripHeader(
                tripId,
                routeName,
                company,
                vehicleEco,
                direction,
                routeNumber,
                esFs,
                baseStart,
                baseEnd,
                plateNumber,
                vehicleType,
                seatCapacity,
                notes,
                aforador,
                supervisor,
                deviceNumber,
                observerSex
            )
            if (ok) TripOperationResult.Success else TripOperationResult.Failed(IllegalStateException("Trip header update returned false"))
        } catch (t: Throwable) {
            TripOperationResult.Failed(t)
        }
    }
}
