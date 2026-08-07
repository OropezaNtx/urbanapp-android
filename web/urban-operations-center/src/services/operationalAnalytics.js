import { buildTrackMetrics } from "../components/TrackSummary";
import { webIntegrity } from "./webIntegrity";

export const OPERATIONAL_ANALYTICS_VERSION = "3.2.1";

const NOT_AVAILABLE = "NOT_AVAILABLE";
const MOVING_THRESHOLD_KMH = 2;
const MIN_GAP_THRESHOLD_MS = 10_000;
const FALLBACK_MAX_PLAUSIBLE_SPEED_KMH = 160;

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function timestamp(value) {
  if (!value) return 0;
  if (typeof value === "number") return value;
  if (typeof value?.toMillis === "function") return value.toMillis();
  if (Number.isFinite(Number(value?.seconds))) return Number(value.seconds) * 1000;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function distanceM(a, b) {
  const R = 6_371_000;
  const lat1 = num(a?.lat) * Math.PI / 180;
  const lat2 = num(b?.lat) * Math.PI / 180;
  const dLat = (num(b?.lat) - num(a?.lat)) * Math.PI / 180;
  const dLon = (num(b?.lon) - num(a?.lon)) * Math.PI / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
}

function median(values) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

function accuracyStats(points) {
  const values = points.map((p) => num(p.accuracy)).filter((v) => v > 0 && v < 9999);
  if (!values.length) return { averageM: null, minM: null, maxM: null };
  return {
    averageM: values.reduce((a, b) => a + b, 0) / values.length,
    minM: Math.min(...values),
    maxM: Math.max(...values),
  };
}

function geometryRejected(point) {
  const geometry = String(point?.geometryStatus || "").toUpperCase();
  const sample = String(point?.sampleStatus || "").toUpperCase();
  return geometry === "SUSPECT_SPEED" || geometry === "SUSPECT_JUMP" || geometry === "NO_FIX" || sample === "NO_FIX";
}

function buildSegments(points) {
  const segments = [];
  for (let i = 1; i < points.length; i += 1) {
    const prev = points[i - 1];
    const curr = points[i];
    const startMs = timestamp(prev.time);
    const endMs = timestamp(curr.time);
    const dtMs = endMs - startMs;
    if (dtMs <= 0) continue;
    const meters = distanceM(prev, curr);
    const kmh = (meters / dtMs) * 3600;
    const rejectedByEngine = geometryRejected(prev) || geometryRejected(curr);
    const rejectedByFallback = Number.isFinite(kmh) && kmh > FALLBACK_MAX_PLAUSIBLE_SPEED_KMH;
    segments.push({
      startMs,
      endMs,
      dtMs,
      meters,
      kmh,
      trusted: !rejectedByEngine && !rejectedByFallback,
      rejectedByEngine,
      rejectedByFallback,
    });
  }
  return segments;
}

function normalizeType(event) {
  return String(event?.eventType ?? event?.stopType ?? "").trim().toUpperCase();
}

function hasDelayCode(event) {
  return String(event?.delayCodes ?? "").trim().length > 0;
}

function classifyEvents(events = []) {
  const safe = Array.isArray(events) ? events : [];
  const flags = safe.filter((e) => normalizeType(e).includes("BANDERA"));
  const pureDelays = safe.filter((e) => {
    const type = normalizeType(e);
    return type.includes("DEMORA") || type.includes("DELAY");
  });
  const codedDelayEvents = safe.filter(hasDelayCode);
  const observations = safe.filter((e) => String(e?.notes ?? e?.otherDelayDesc ?? "").trim().length > 0);
  const incidentIds = new Set(
    safe
      .filter((e) => flags.includes(e) || pureDelays.includes(e) || hasDelayCode(e))
      .map((e, index) => e?.id ?? e?.cloudEventId ?? e?.eventId ?? index)
  );
  return {
    total: safe.length,
    delays: pureDelays.length,
    flags: flags.length,
    codedDelayEvents: codedDelayEvents.length,
    incidents: incidentIds.size,
    observations: observations.length,
  };
}

function estimatePayloadBytes(value) {
  try {
    return new TextEncoder().encode(JSON.stringify(value ?? null)).length;
  } catch {
    return null;
  }
}

export function evaluateOperationalAnalytics({ trip, events = [], trackChunks = [] } = {}) {
  const startedAt = typeof performance !== "undefined" ? performance.now() : Date.now();
  const track = buildTrackMetrics(Array.isArray(trackChunks) ? trackChunks : []);
  const points = track.points || [];
  const segments = buildSegments(points);
  const trustedSegments = segments.filter((s) => s.trusted);
  const rejectedSegments = segments.filter((s) => !s.trusted);

  const tripStartMs = timestamp(trip?.startTime) || track.startTime || 0;
  const tripEndMs = timestamp(trip?.endTime) || track.endTime || 0;
  const durationMs = tripStartMs && tripEndMs && tripEndMs >= tripStartMs
    ? tripEndMs - tripStartMs
    : (track.durationMs || 0);

  const rawDistanceKm = track.distance / 1000;
  const trustedDistanceM = trustedSegments.reduce((sum, s) => sum + s.meters, 0);
  const distanceKm = trustedDistanceM / 1000;

  const intervals = segments.map((s) => s.dtMs).filter((v) => v > 0);
  const medianIntervalMs = median(intervals);
  const expectedIntervalMs = medianIntervalMs > 0 ? medianIntervalMs : 0;
  const gapThresholdMs = Math.max(MIN_GAP_THRESHOLD_MS, expectedIntervalMs > 0 ? expectedIntervalMs * 3 : MIN_GAP_THRESHOLD_MS);
  const gpsGaps = segments.filter((s) => s.dtMs > gapThresholdMs);
  const noGpsMs = gpsGaps.reduce((sum, gap) => sum + Math.max(0, gap.dtMs - expectedIntervalMs), 0);
  const gpsCoveragePct = durationMs > 0 ? Math.max(0, Math.min(100, ((durationMs - noGpsMs) / durationMs) * 100)) : null;
  const accuracy = accuracyStats(points);

  const speeds = trustedSegments.map((s) => s.kmh).filter((v) => Number.isFinite(v) && v >= 0);
  const movingSegments = trustedSegments.filter((s) => s.kmh > MOVING_THRESHOLD_KMH);
  const movingMs = movingSegments.reduce((sum, s) => sum + s.dtMs, 0);
  const stoppedMs = Math.max(0, durationMs - movingMs - noGpsMs);
  const averageSpeedKmh = durationMs > 0 ? distanceKm / (durationMs / 3_600_000) : null;
  const maxSpeedKmh = speeds.length ? Math.max(...speeds) : null;
  const minSpeedKmh = speeds.length ? Math.min(...speeds) : null;
  const observedOperationalMs = Math.max(0, durationMs - noGpsMs);
  const operatorEfficiencyPct = observedOperationalMs > 0
    ? Math.max(0, Math.min(100, (movingMs / observedOperationalMs) * 100))
    : null;

  const eventCounts = classifyEvents(events);
  const durationMinutes = durationMs / 60_000;
  const eventsPerKm = distanceKm > 0 ? eventCounts.total / distanceKm : null;
  const eventsPerMinute = durationMinutes > 0 ? eventCounts.total / durationMinutes : null;

  const payloadBytesEstimated = estimatePayloadBytes({ trip, events, trackChunks });
  const finishedAt = typeof performance !== "undefined" ? performance.now() : Date.now();

  return {
    version: OPERATIONAL_ANALYTICS_VERSION,
    generatedAt: Date.now(),
    gps: {
      points: points.length,
      lossEvents: gpsGaps.length,
      noGpsMs,
      coveragePct: gpsCoveragePct,
      medianIntervalMs: medianIntervalMs || null,
      expectedIntervalMs: expectedIntervalMs || null,
      gapThresholdMs,
      averageAccuracyM: accuracy.averageM,
      minAccuracyM: accuracy.minM,
      maxAccuracyM: accuracy.maxM,
      rejectedSegments: rejectedSegments.length,
      engineRejectedSegments: rejectedSegments.filter((s) => s.rejectedByEngine).length,
      fallbackRejectedSegments: rejectedSegments.filter((s) => s.rejectedByFallback).length,
    },
    trip: {
      durationMs,
      distanceKm,
      rawDistanceKm,
      distanceAdjustmentKm: rawDistanceKm - distanceKm,
      averageSpeedKmh,
      maxSpeedKmh,
      minSpeedKmh,
    },
    events: {
      ...eventCounts,
      eventsPerKm,
      eventsPerMinute,
    },
    operator: {
      movingMs,
      stoppedMs,
      unobservedMs: noGpsMs,
      efficiencyPct: operatorEfficiencyPct,
      gpsCoveragePct,
    },
    device: {
      gpsCoveragePct,
      averageAccuracyM: accuracy.averageM,
      battery: { availability: NOT_AVAILABLE, value: null },
      heartbeat: { availability: NOT_AVAILABLE, value: null },
      reconnections: { availability: NOT_AVAILABLE, value: null },
    },
    sync: {
      payloadBytesEstimated,
      uploadDurationMs: { availability: NOT_AVAILABLE, value: null },
      cloudDurationMs: { availability: NOT_AVAILABLE, value: null },
      webAnalysisMs: Math.max(0, finishedAt - startedAt),
    },
    availability: {
      batteryHistory: false,
      heartbeatHistory: false,
      reconnectionHistory: false,
      uploadTiming: false,
      cloudTiming: false,
      webAnalysisTiming: true,
    },
  };
}

export function logOperationalAnalytics({ tripDocId, trip, events, trackChunks }) {
  const result = evaluateOperationalAnalytics({ trip, events, trackChunks });
  webIntegrity("OPERATIONAL_ANALYTICS", {
    tripDocId: tripDocId ?? trip?.id ?? null,
    version: result.version,
    gpsLossEvents: result.gps.lossEvents,
    gpsNoSignalMs: result.gps.noGpsMs,
    gpsCoveragePct: result.gps.coveragePct == null ? null : result.gps.coveragePct.toFixed(2),
    avgAccuracyM: result.gps.averageAccuracyM == null ? null : result.gps.averageAccuracyM.toFixed(2),
    rejectedSegments: result.gps.rejectedSegments,
    durationMs: result.trip.durationMs,
    distanceKm: result.trip.distanceKm.toFixed(3),
    rawDistanceKm: result.trip.rawDistanceKm.toFixed(3),
    avgSpeedKmh: result.trip.averageSpeedKmh == null ? null : result.trip.averageSpeedKmh.toFixed(2),
    maxSpeedKmh: result.trip.maxSpeedKmh == null ? null : result.trip.maxSpeedKmh.toFixed(2),
    events: result.events.total,
    delays: result.events.delays,
    flags: result.events.flags,
    codedDelayEvents: result.events.codedDelayEvents,
    incidents: result.events.incidents,
    observations: result.events.observations,
    movingMs: result.operator.movingMs,
    stoppedMs: result.operator.stoppedMs,
    unobservedMs: result.operator.unobservedMs,
    efficiencyPct: result.operator.efficiencyPct == null ? null : result.operator.efficiencyPct.toFixed(2),
    payloadBytesEstimated: result.sync.payloadBytesEstimated,
    webAnalysisMs: result.sync.webAnalysisMs.toFixed(3),
  });
  webIntegrity("OPERATIONAL_ANALYTICS_AVAILABILITY", {
    tripDocId: tripDocId ?? trip?.id ?? null,
    batteryHistory: result.availability.batteryHistory,
    heartbeatHistory: result.availability.heartbeatHistory,
    reconnectionHistory: result.availability.reconnectionHistory,
    uploadTiming: result.availability.uploadTiming,
    cloudTiming: result.availability.cloudTiming,
    webAnalysisTiming: result.availability.webAnalysisTiming,
  });
  return result;
}
