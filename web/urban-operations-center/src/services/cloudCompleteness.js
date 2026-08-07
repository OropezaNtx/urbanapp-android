import { webIntegrity } from "./webIntegrity";

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function isValidGpsPoint(point) {
  const lat = Number(point?.lat);
  const lon = Number(point?.lon);
  return Number.isFinite(lat) && Number.isFinite(lon) && !(lat === 0 && lon === 0);
}

function isClosedTrip(trip) {
  const status = String(trip?.status || "").toUpperCase();
  if (status === "CLOSED") return true;
  if (status === "ACTIVE") return false;
  return Boolean(trip?.endTime);
}

function chunkIndexOf(chunk) {
  const direct = num(chunk?.chunkIndex);
  if (direct !== null) return direct;

  const id = String(chunk?.chunkId || chunk?.id || "");
  const match = id.match(/(?:chunk[_-]?)(\d+)$/i);
  return match ? Number(match[1]) : null;
}

function findMissingChunkIndexes(chunks) {
  const indexes = chunks
    .map(chunkIndexOf)
    .filter((v) => Number.isInteger(v))
    .sort((a, b) => a - b);

  if (indexes.length < 2) return [];

  const unique = [...new Set(indexes)];
  const missing = [];
  for (let i = unique[0]; i <= unique[unique.length - 1]; i += 1) {
    if (!unique.includes(i)) missing.push(i);
  }
  return missing;
}

export function evaluateCloudCompleteness({ trip, events = [], trackChunks = [] } = {}) {
  if (!trip) {
    return {
      state: "MISSING_TRIP",
      complete: false,
      tripClosed: false,
      eventCount: events.length,
      chunkCount: trackChunks.length,
      rawPointCount: 0,
      validGpsPointCount: 0,
      invalidGpsPointCount: 0,
      declaredPointCount: 0,
      pointPayloadDelta: 0,
      missingChunkIndexes: [],
      qualityState: "UNKNOWN",
    };
  }

  const tripClosed = isClosedTrip(trip);
  const points = trackChunks.flatMap((chunk) => Array.isArray(chunk?.points) ? chunk.points : []);
  const rawPointCount = points.length;
  const validGpsPointCount = points.filter(isValidGpsPoint).length;
  const invalidGpsPointCount = rawPointCount - validGpsPointCount;
  const declaredPointCount = trackChunks.reduce((sum, chunk) => {
    const declared = num(chunk?.pointCount);
    return sum + (declared ?? (Array.isArray(chunk?.points) ? chunk.points.length : 0));
  }, 0);
  const pointPayloadDelta = rawPointCount - declaredPointCount;
  const missingChunkIndexes = findMissingChunkIndexes(trackChunks);

  let state = "COMPLETE";
  if (!tripClosed) {
    state = "SYNC_PENDING";
  } else if (events.length === 0) {
    state = "MISSING_EVENTS";
  } else if (trackChunks.length === 0) {
    state = "MISSING_CHUNKS";
  } else if (missingChunkIndexes.length > 0) {
    state = "MISSING_CHUNKS";
  } else if (pointPayloadDelta !== 0) {
    state = "POINT_COUNT_MISMATCH";
  }

  return {
    state,
    complete: state === "COMPLETE",
    tripClosed,
    eventCount: events.length,
    chunkCount: trackChunks.length,
    rawPointCount,
    validGpsPointCount,
    invalidGpsPointCount,
    declaredPointCount,
    pointPayloadDelta,
    missingChunkIndexes,
    qualityState: invalidGpsPointCount > 0 ? "GPS_POINTS_FILTERED" : "GPS_OK",
  };
}

export function logCloudCompleteness({ tripDocId, trip, events, trackChunks }) {
  const result = evaluateCloudCompleteness({ trip, events, trackChunks });
  webIntegrity("CLOUD_COMPLETENESS", {
    tripDocId: tripDocId ?? trip?.id ?? null,
    state: result.state,
    complete: result.complete,
    tripClosed: result.tripClosed,
    events: result.eventCount,
    chunks: result.chunkCount,
    rawPoints: result.rawPointCount,
    validGpsPoints: result.validGpsPointCount,
    invalidGpsPoints: result.invalidGpsPointCount,
    declaredPoints: result.declaredPointCount,
    pointPayloadDelta: result.pointPayloadDelta,
    missingChunkIndexes: result.missingChunkIndexes.length
      ? result.missingChunkIndexes.join(",")
      : "NONE",
    qualityState: result.qualityState,
  });
  return result;
}
