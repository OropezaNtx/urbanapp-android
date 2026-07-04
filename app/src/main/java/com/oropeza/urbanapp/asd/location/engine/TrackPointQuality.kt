package com.oropeza.urbanapp.asd.location.engine

import com.oropeza.urbanapp.asd.data.local.TrackPoint

object TrackPointQuality {
    private val cleanSampleStatuses = setOf("LIVE")
    private val cleanQualityStatuses = setOf("GOOD_ACCURACY", "USABLE_ACCURACY", "LOW_ACCURACY")
    private val cleanGeometryStatuses = setOf("GEOMETRY_OK")

    fun isCoordinateUsable(point: TrackPoint): Boolean {
        return point.lat.isFinite() &&
            point.lon.isFinite() &&
            point.lat in -90.0..90.0 &&
            point.lon in -180.0..180.0 &&
            !(point.lat == 0.0 && point.lon == 0.0)
    }

    fun isCleanRoutePoint(point: TrackPoint): Boolean {
        return isCoordinateUsable(point) &&
            point.sampleStatus in cleanSampleStatuses &&
            point.qualityStatus in cleanQualityStatuses &&
            point.geometryStatus in cleanGeometryStatuses &&
            !point.isStale &&
            !point.isSynthetic
    }

    fun cleanRoutePoints(points: List<TrackPoint>): List<TrackPoint> {
        return points.filter(::isCleanRoutePoint).sortedBy { it.timeMs }
    }

    fun auditLabel(point: TrackPoint): String {
        return listOf(
            point.sampleStatus,
            point.qualityStatus,
            point.geometryStatus,
            point.filterStatus,
            point.engineMode,
            point.armStatus
        ).joinToString(" | ")
    }
}
