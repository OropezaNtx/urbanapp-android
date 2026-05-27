package com.oropeza.urbanapp.asd.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Trip::class, StopEvent::class, DelayEvent::class, TrackPoint::class,
        CcSession::class, CcEvent::class,

        // ✅ FOV
        FovSession::class,
        FovRouteMaster::class,
        FovPoiCatalogItem::class,
        FovObservation::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun delayDao(): DelayDao
    abstract fun trackDao(): TrackDao

    abstract fun ccSessionDao(): CcSessionDao
    abstract fun ccEventDao(): CcEventDao

    // ✅ FOV
    abstract fun fovSessionDao(): FovSessionDao
    abstract fun fovRouteMasterDao(): FovRouteMasterDao
    abstract fun fovPoiCatalogDao(): FovPoiCatalogDao
    abstract fun fovObservationDao(): FovObservationDao
}
