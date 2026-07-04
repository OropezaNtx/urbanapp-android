package com.oropeza.urbanapp.asd.domain.trip

/**
 * Stable singleton entrypoint for ASD trip domain operations.
 *
 * This keeps ViewModels from constructing use cases directly and gives us a
 * single dependency boundary while the app still uses the existing manual graph.
 */
object TripDomain {
    val useCases: TripDomainUseCases = TripDomainUseCases()
}
