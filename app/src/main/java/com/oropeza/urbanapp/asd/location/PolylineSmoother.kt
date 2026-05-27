package com.oropeza.urbanapp.asd.location

import kotlin.math.*

data class LatLng(val lat: Double, val lon: Double)

object PolylineSmoother {

    fun movingAverage(points: List<LatLng>, window: Int = 3): List<LatLng> {
        if (points.size <= 2 || window <= 1) return points
        val w = window.coerceAtLeast(3)
        val half = w / 2
        return points.mapIndexed { i, _ ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(points.lastIndex)
            val slice = points.subList(from, to + 1)
            val lat = slice.sumOf { it.lat } / slice.size
            val lon = slice.sumOf { it.lon } / slice.size
            LatLng(lat, lon)
        }
    }

    fun douglasPeucker(points: List<LatLng>, epsilonMeters: Double): List<LatLng> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        dp(points, 0, points.lastIndex, epsilonMeters, keep)
        return points.filterIndexed { idx, _ -> keep[idx] }
    }

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
        // Aproximación equirectangular local (suficiente para distancias pequeñas)
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
