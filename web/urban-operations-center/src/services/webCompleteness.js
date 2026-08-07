import { buildTrackMetrics } from "../components/TrackSummary";
import { buildTripInsights } from "../components/TripInsights";
import { buildExportStats } from "../exporters/geo";
import { evaluateCloudCompleteness } from "./cloudCompleteness";
import { webIntegrity } from "./webIntegrity";

function num(value) {
  return Number(value ?? 0) || 0;
}

function validCoord(lat, lon) {
  const a = Number(lat);
  const b = Number(lon);
  return Number.isFinite(a) && Number.isFinite(b) && !(a === 0 && b === 0);
}

function eventLat(event) {
  return event?.stopLat ?? event?.lat ?? event?.startLat;
}

function eventLon(event) {
  return event?.stopLon ?? event?.lon ?? event?.startLon;
}

function eventTime(event) {
  return num(event?.timestamp || event?.stopTime || event?.startTime || event?.createdAt);
}

export function evaluateWebCompleteness({ trip, events = [], trackChunks = [] } = {}) {
  const safeEvents = Array.isArray(events) ? events : [];
  const safeChunks = Array.isArray(trackChunks) ? trackChunks : [];
  const cloud = evaluateCloudCompleteness({ trip, events: safeEvents, trackChunks: safeChunks });
  const track = buildTrackMetrics(safeChunks);
  const insights = buildTripInsights(trip, safeEvents, safeChunks);
  const exportStats = buildExportStats(safeEvents, safeChunks);

  const eventPoints = safeEvents.filter((event) => validCoord(eventLat(event), eventLon(event)));
  const playbackEvents = eventPoints.filter((event) => eventTime(event) > 0);

  const consumers = {
    summaryPoints: track.totalPoints,
    mapPoints: track.totalPoints,
    playbackPoints: track.totalPoints,
    exportPoints: exportStats.pointCount,
    tableEvents: safeEvents.length,
    timelineEvents: insights.timeline.length,
    mapEvents: eventPoints.length,
    playbackEvents: playbackEvents.length,
    exportWaypoints: exportStats.eventPointCount,
  };

  const mismatches = [];

  if (consumers.summaryPoints !== cloud.validGpsPointCount) mismatches.push("SUMMARY_POINTS");
  if (consumers.mapPoints !== cloud.validGpsPointCount) mismatches.push("MAP_POINTS");
  if (consumers.playbackPoints !== cloud.validGpsPointCount) mismatches.push("PLAYBACK_POINTS");
  if (consumers.exportPoints !== cloud.validGpsPointCount) mismatches.push("EXPORT_POINTS");
  if (consumers.tableEvents !== cloud.eventCount) mismatches.push("TABLE_EVENTS");
  if (consumers.timelineEvents !== cloud.eventCount) mismatches.push("TIMELINE_EVENTS");
  if (consumers.exportWaypoints !== consumers.mapEvents) mismatches.push("EXPORT_EVENT_POINTS");

  let state = "WEB_COMPLETE";
  if (!cloud.complete) state = "CLOUD_INCOMPLETE";
  else if (mismatches.length > 0) state = "WEB_MISMATCH";

  return {
    state,
    complete: state === "WEB_COMPLETE",
    cloudState: cloud.state,
    cloudComplete: cloud.complete,
    rawCloudPoints: cloud.rawPointCount,
    validGpsPoints: cloud.validGpsPointCount,
    invalidGpsPoints: cloud.invalidGpsPointCount,
    eventCount: cloud.eventCount,
    chunkCount: cloud.chunkCount,
    consumers,
    mismatches,
  };
}

export function logWebCompleteness({ tripDocId, trip, events, trackChunks }) {
  const result = evaluateWebCompleteness({ trip, events, trackChunks });
  webIntegrity("WEB_COMPLETENESS", {
    tripDocId: tripDocId ?? trip?.id ?? null,
    state: result.state,
    complete: result.complete,
    cloudState: result.cloudState,
    cloudComplete: result.cloudComplete,
    chunks: result.chunkCount,
    cloudEvents: result.eventCount,
    rawCloudPoints: result.rawCloudPoints,
    validGpsPoints: result.validGpsPoints,
    invalidGpsPoints: result.invalidGpsPoints,
    summaryPoints: result.consumers.summaryPoints,
    mapPoints: result.consumers.mapPoints,
    playbackPoints: result.consumers.playbackPoints,
    exportPoints: result.consumers.exportPoints,
    tableEvents: result.consumers.tableEvents,
    timelineEvents: result.consumers.timelineEvents,
    mapEvents: result.consumers.mapEvents,
    playbackEvents: result.consumers.playbackEvents,
    exportWaypoints: result.consumers.exportWaypoints,
    mismatches: result.mismatches.length ? result.mismatches.join(",") : "NONE",
  });
  return result;
}
