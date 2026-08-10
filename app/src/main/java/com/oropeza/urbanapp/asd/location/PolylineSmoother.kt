package com.oropeza.urbanapp.asd.location

import kotlin.math.*

data class LatLng(val lat: Double, val lon: Double)

object PolylineSmoother {

    private const val MAX_SMOOTHING_SHIFT_M = 4.0

    /**
     * Geometry-preserving smoothing.
     *
     * AforaGpsEngine already applies Kalman filtering to defensible fixes. This
     * second stage is only for visual micro-jitter, so it must never materially
     * relocate the route. Endpoints are preserved, the center point keeps most of
     * the weight and any introduced displacement is capped at 4 m.
     */
    fun movingAverage(points: List<LatLng>, window: Int = 3): List<LatLng> {
        if (points.size <= 2 || window <= 1) return points

        return points.mapIndexed { i, current ->
            if (i == 0 || i == points.lastIndex) return@mapIndexed current

            val prev = points[i - 1]
            val next = points[i + 1]

            // Weighted centered filter: do not let neighbours overpower the
            // canonical filtered coordinate produced by the GPS engine.
            val candidate = LatLng(
                lat = prev.lat * 0.15 + current.lat * 0.70 + next.lat * 0.15,
                lon = prev.lon * 0.15 + current.lon * 0.70 + next.lon * 0.15
            )

            boundShift(current, candidate, MAX_SMOOTHING_SHIFT_M)
        }
    }

    fun douglasPeucker(points: List<LatLng>, epsilonMeters: Double): List<LatLng> {
        if (points.size < 3) return points
        if (points.size <= 500) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        dp(points, 0, points.lastIndex, epsilonMeters, keep)
        return points.filterIndexed { idx, _ -> keep[idx] }
    }

    private fun boundShift(origin: LatLng, candidate: LatLng, maxShiftM: Double): LatLng {
        val distance = haversineMeters(origin, candidate)
        if (!distance.isFinite() || distance <= maxShiftM || distance <= 0.0) return candidate
        val ratio = (maxShiftM / distance).coerceIn(0.0, 1.0)
        return LatLng(
            lat = origin.lat + (candidate.lat - origin.lat) * ratio,
            lon = origin.lon + (candidate.lon - origin.lon) * ratio
        )
    }

    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2.0)
        return 2.0 * r * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1.0 - h).coerceAtLeast(0.0)))
    }

    fun douglasPeuckerLegacy(points: List<LatLng>, epsilonMeters: Double): List<LatLng> =
        douglasPeucker(points, epsilonMeters)

    private fun dp(points: List<LatLng>, start: Int, end: Int, epsM: Double, keep: BooleanArray) {
        if (end <= start + 1) return

        var maxDist = 0.0
        var idx = -1

        val a = points[start]
        val b = points[end]

        for (i in start + 1 until end) {
            val d = distancePointToSegmentMeters(points[i], a, b)
            if (d > maxDist) {
                maxDist = d
                idx = i
            }
        }

        if (maxDist > epsM && idx != -1) {
            keep[idx] = true
            dp(points, start, idx, epsM, keep)
            dp(points, idx, end, epsM, keep)
        }
    }

    private fun distancePointToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        val lat0 = Math.toRadians((a.lat + b.lat) / 2.0)
        fun toXY(x: LatLng): Pair<Double, Double> {
            val xM = Math.toRadians(x.lon) * cos(lat0) * 6371000.0
            val yM = Math.toRadians(x.lat) * 6371000.0
            return xM to yM
        }
        val (px, py) = toXY(p)
        val (ax, ay) = toXY(a)
        val (bx, by) = toXY(b)

        val vx = bx - ax
        val vy = by - ay
        val wx = px - ax
        val wy = py - ay

        val c1 = wx * vx + wy * vy
        if (c1 <= 0) return hypot(px - ax, py - ay)

        val c2 = vx * vx + vy * vy
        if (c2 <= c1) return hypot(px - bx, py - by)

        val t = c1 / c2
        val projX = ax + t * vx
        val projY = ay + t * vy
        return hypot(px - projX, py - projY)
    }
}
