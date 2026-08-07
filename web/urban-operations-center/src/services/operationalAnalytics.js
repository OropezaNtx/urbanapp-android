import { buildTrackMetrics } from "../components/TrackSummary";
import { webIntegrity } from "./webIntegrity";

export const OPERATIONAL_ANALYTICS_VERSION = "3.2.2";

const NOT_AVAILABLE = "NOT_AVAILABLE";
const MOVING_THRESHOLD_KMH = 2;
const MIN_GAP_THRESHOLD_MS = 10_000;
const MAX_PLAUSIBLE_SEGMENT_SPEED_KMH = 160;
const NEUTRAL_OPERATION_CODES = new Set(["AD", "INICIO", "FINAL"]);

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

function validCoordinate(point) {
  const lat = Number(point?.lat);
  const lon = Number(point?.lon);
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && !(lat === 0 && lon === 0);
}

function distanceM(a, b) {
  if (!validCoordinate(a) || !validCoordinate(b)) return 0;
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

function pointStatus(point, field, fallback = "UNKNOWN") {
  return String(point?.[field] ?? fallback).trim().toUpperCase() || fallback;
}

function countBy(values) {
  return values.reduce((acc, value) => {
    const key = String(value || "UNKNOWN");
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});
}

function compactHistogram(histogram) {
  const entries = Object.entries(histogram || {}).sort((a, b) => b[1] - a[1]);
  return entries.length ? entries.map(([key, value]) => `${key}:${value}`).join(",") : "NONE";
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
    const noFix = pointStatus(prev, "sampleStatus") === "NO_FIX" || pointStatus(curr, "sampleStatus") === "NO_FIX";
    const impossibleSpeed = Number.isFinite(kmh) && kmh > MAX_PLAUSIBLE_SEGMENT_SPEED_KMH;
    const engineSuspect = [prev, curr].some((point) => {
      const status = pointStatus(point, "geometryStatus");
      return status === "SUSPECT_SPEED" || status === "SUSPECT_JUMP";
    });

    segments.push({
      startMs,
      endMs,
      dtMs,
      meters,
      kmh,
      trusted: !noFix && !impossibleSpeed,
      noFix,
      impossibleSpeed,
      engineSuspect,
    });
  }
  return segments;
}

function normalizeType(event) {
  return String(event?.eventType ?? event?.stopType ?? "").trim().toUpperCase();
}

function delayTokens(event) {
  return String(event?.delayCodes ?? "")
    .split(/[\/,|;]+/)
    .map((value) => value.trim().toUpperCase())
    .filter(Boolean);
}

function meaningfulDelayTokens(event) {
  return delayTokens(event).filter((token) => !NEUTRAL_OPERATION_CODES.has(token));
}

function classifyEvents(events = []) {
  const safe = Array.isArray(events) ? events : [];
  const types = safe.map(normalizeType);
  const allTokens = safe.flatMap(delayTokens);
  const flags = safe.filter((event) => normalizeType(event).includes("BANDERA"));
  const explicitDelays = safe.filter((event) => {
    const type = normalizeType(event);
    return type.includes("DEMORA") || type.includes("DELAY");
  });
  const codedDelayEvents = safe.filter((event) => meaningfulDelayTokens(event).length > 0);
  const delays = safe.filter((event) => explicitDelays.includes(event) || meaningfulDelayTokens(event).length > 0);
  const observations = safe.filter((event) => String(event?.notes ?? event?.otherDelayDesc ?? "").trim().length > 0);
  const incidentIds = new Set(
    safe
      .filter((event) => flags.includes(event) || delays.includes(event))
      .map((event, index) => event?.id ?? event?.cloudEventId ?? event?.eventId ?? index)
  );

  return {
    total: safe.length,
    delays: delays.length,
    explicitDelays: explicitDelays.length,
    flags: flags.length,
    codedDelayEvents: codedDelayEvents.length,
    incidents: incidentIds.size,
    observations: observations.length,
    typeHistogram: countBy(types),
    delayCodeHistogram: countBy(allTokens),
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
  const trustedSegments = segments.filter((segment) => segment.trusted);
  const rejectedSegments = segments.filter((segment) => !segment.trusted);

  const tripStartMs = timestamp(trip?.startTime) || track.startTime || 0;
  const tripEndMs = timestamp(trip?.endTime) || track.endTime || 0;
  const durationMs = tripStartMs && tripEndMs && tripEndMs >= tripStartMs
    ? tripEndMs - tripStartMs
    : (track.durationMs || 0);

  const rawDistanceKm = track.distance / 1000;
  const analyticDistanceM = trustedSegments.reduce((sum, segment) => sum + segment.meters, 0);
  const distanceKm = analyticDistanceM / 1000;
  const distanceAdjustmentKm = rawDistanceKm - distanceKm;
  const distanceCorrectionPct = rawDistanceKm > 0 ? (distanceAdjustmentKm / rawDistanceKm) * 100 : 0;

  const intervals = segments.map((segment) => segment.dtMs).filter((value) => value > 0);
  const medianIntervalMs = median(intervals);
  const expectedIntervalMs = medianIntervalMs > 0 ? medianIntervalMs : 0;
  const gapThresholdMs = Math.max(MIN_GAP_THRESHOLD_MS, expectedIntervalMs > 0 ? expectedIntervalMs * 3 : MIN_GAP_THRESHOLD_MS);
  const gpsGaps = segments.filter((segment) => segment.dtMs > gapThresholdMs);
  const noGpsMs = gpsGaps.reduce((sum, gap) => sum + Math.max(0, gap.dtMs - expectedIntervalMs), 0);
  const gpsCoveragePct = durationMs > 0 ? Math.max(0, Math.min(100, ((durationMs - noGpsMs) / durationMs) * 100)) : null;
  const accuracy = accuracyStats(points);

  const speeds = trustedSegments.map((segment) => segment.kmh).filter((value) => Number.isFinite(value) && value >= 0);
  const movingSegments = trustedSegments.filter((segment) => segment.kmh > MOVING_THRESHOLD_KMH);
  const movingMs = movingSegments.reduce((sum, segment) => sum + segment.dtMs, 0);
  const observedOperationalMs = Math.max(0, durationMs - noGpsMs);
  const stoppedMs = Math.max(0, observedOperationalMs - movingMs);
  const averageSpeedKmh = durationMs > 0 ? distanceKm / (durationMs / 3_600_000) : null;
  const maxSpeedKmh = speeds.length ? Math.max(...speeds) : null;
  const minSpeedKmh = speeds.length ? Math.min(...speeds) : null;
  const movementRatioPct = observedOperationalMs > 0
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
      rejectedNoFixSegments: rejectedSegments.filter((segment) => segment.noFix).length,
      rejectedImpossibleSpeedSegments: rejectedSegments.filter((segment) => segment.impossibleSpeed).length,
      engineSuspectSegments: segments.filter((segment) => segment.engineSuspect).length,
      geometryStatusHistogram: countBy(points.map((point) => pointStatus(point, "geometryStatus"))),
      sampleStatusHistogram: countBy(points.map((point) => pointStatus(point, "sampleStatus"))),
      qualityStatusHistogram: countBy(points.map((point) => pointStatus(point, "qualityStatus"))),
    },
    trip: {
      durationMs,
      distanceKm,
      rawDistanceKm,
      distanceAdjustmentKm,
      distanceCorrectionPct,
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
      movementRatioPct,
      efficiencyPct: movementRatioPct,
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
  const id = tripDocId ?? trip?.id ?? null;

  webIntegrity("OPERATIONAL_ANALYTICS", {
    tripDocId: id,
    version: result.version,
    gpsLossEvents: result.gps.lossEvents,
    gpsNoSignalMs: result.gps.noGpsMs,
    gpsCoveragePct: result.gps.coveragePct == null ? null : result.gps.coveragePct.toFixed(2),
    avgAccuracyM: result.gps.averageAccuracyM == null ? null : result.gps.averageAccuracyM.toFixed(2),
    rejectedSegments: result.gps.rejectedSegments,
    engineSuspectSegments: result.gps.engineSuspectSegments,
    durationMs: result.trip.durationMs,
    distanceKm: result.trip.distanceKm.toFixed(3),
    rawDistanceKm: result.trip.rawDistanceKm.toFixed(3),
    distanceCorrectionPct: result.trip.distanceCorrectionPct.toFixed(2),
    avgSpeedKmh: result.trip.averageSpeedKmh == null ? null : result.trip.averageSpeedKmh.toFixed(2),
    maxSpeedKmh: result.trip.maxSpeedKmh == null ? null : result.trip.maxSpeedKmh.toFixed(2),
    events: result.events.total,
    delays: result.events.delays,
    explicitDelays: result.events.explicitDelays,
    flags: result.events.flags,
    codedDelayEvents: result.events.codedDelayEvents,
    incidents: result.events.incidents,
    observations: result.events.observations,
    movingMs: result.operator.movingMs,
    stoppedMs: result.operator.stoppedMs,
    unobservedMs: result.operator.unobservedMs,
    movementRatioPct: result.operator.movementRatioPct == null ? null : result.operator.movementRatioPct.toFixed(2),
    payloadBytesEstimated: result.sync.payloadBytesEstimated,
    webAnalysisMs: result.sync.webAnalysisMs.toFixed(3),
  });

  webIntegrity("OPERATIONAL_GPS_SEMANTICS", {
    tripDocId: id,
    geometryStatus: compactHistogram(result.gps.geometryStatusHistogram),
    sampleStatus: compactHistogram(result.gps.sampleStatusHistogram),
    qualityStatus: compactHistogram(result.gps.qualityStatusHistogram),
    engineSuspectSegments: result.gps.engineSuspectSegments,
    rejectedNoFixSegments: result.gps.rejectedNoFixSegments,
    rejectedImpossibleSpeedSegments: result.gps.rejectedImpossibleSpeedSegments,
  });

  webIntegrity("OPERATIONAL_EVENT_SEMANTICS", {
    tripDocId: id,
    stopTypes: compactHistogram(result.events.typeHistogram),
    delayCodes: compactHistogram(result.events.delayCodeHistogram),
    neutralCodes: "AD,INICIO,FINAL",
  });

  webIntegrity("OPERATIONAL_ANALYTICS_AVAILABILITY", {
    tripDocId: id,
    batteryHistory: result.availability.batteryHistory,
    heartbeatHistory: result.availability.heartbeatHistory,
    reconnectionHistory: result.availability.reconnectionHistory,
    uploadTiming: result.availability.uploadTiming,
    cloudTiming: result.availability.cloudTiming,
    webAnalysisTiming: result.availability.webAnalysisTiming,
  });
  return result;
}
