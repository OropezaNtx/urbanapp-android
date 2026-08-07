import { evaluateCloudCompleteness } from "./cloudCompleteness";
import { evaluateWebCompleteness } from "./webCompleteness";
import { webIntegrity } from "./webIntegrity";

export const QUALITY_ENGINE_VERSION = "3.1.0";
export const QUALITY_SCHEMA_VERSION = 1;

const CHECK_WEIGHTS = Object.freeze({
  TRIP_EXISTS: 15,
  TRIP_CLOSED: 10,
  CLOUD_COMPLETE: 20,
  EVENTS_PRESENT: 10,
  CHUNKS_COMPLETE: 10,
  LOSSLESS_TRACK: 10,
  WEB_COMPLETE: 15,
  MAP_COMPLETE: 3,
  PLAYBACK_COMPLETE: 3,
  TIMELINE_COMPLETE: 2,
  EXPORT_COMPLETE: 2,
});

function check(name, passed, message, details = {}) {
  return {
    name,
    passed: Boolean(passed),
    weight: CHECK_WEIGHTS[name] || 0,
    message,
    details,
  };
}

function hasRecoverySignal(trip) {
  if (!trip) return false;
  const numericSignals = [
    trip.recoveryCount,
    trip.watchdogRestarts,
    trip.recoveredChunks,
    trip.recoveryAttempts,
  ].map((v) => Number(v ?? 0));
  return trip.recovered === true || numericSignals.some((v) => Number.isFinite(v) && v > 0);
}

function buildObservations({ trip, cloud, web }) {
  const observations = [];

  if (cloud.invalidGpsPointCount > 0) {
    observations.push({
      code: "AUTO_CLEANED",
      level: "INFO",
      message: `${cloud.invalidGpsPointCount} punto(s) GPS inválido(s) conservados en cloud y excluidos de la geometría.`,
      value: cloud.invalidGpsPointCount,
    });
    observations.push({
      code: "GPS_NORMALIZED",
      level: "INFO",
      message: `${cloud.validGpsPointCount} de ${cloud.rawPointCount} punto(s) son utilizables por consumidores geoespaciales.`,
      value: cloud.validGpsPointCount,
    });
  }

  if (cloud.pointPayloadDelta === 0 && cloud.rawPointCount === cloud.declaredPointCount) {
    observations.push({
      code: "LOSSLESS",
      level: "SUCCESS",
      message: "El payload cloud conserva todos los puntos declarados; no existe pérdida estructural.",
      value: cloud.rawPointCount,
    });
  }

  if (hasRecoverySignal(trip)) {
    observations.push({
      code: "RECOVERED",
      level: "SUCCESS",
      message: "El recorrido registra recuperación asistida o automática y terminó consistente.",
      value: true,
    });
  }

  if (web.mismatches.length > 0) {
    observations.push({
      code: "WEB_MISMATCH",
      level: "WARNING",
      message: web.mismatches.join(", "),
      value: web.mismatches.length,
    });
  }

  return observations;
}

export function evaluateTripQuality({ trip, events = [], trackChunks = [] } = {}) {
  const cloud = evaluateCloudCompleteness({ trip, events, trackChunks });
  const web = evaluateWebCompleteness({ trip, events, trackChunks });
  const c = web.consumers;

  const checks = [
    check("TRIP_EXISTS", Boolean(trip), "Documento principal del recorrido presente."),
    check("TRIP_CLOSED", cloud.tripClosed, cloud.tripClosed ? "Recorrido cerrado correctamente." : "El recorrido sigue activo o no tiene cierre."),
    check("CLOUD_COMPLETE", cloud.complete, `Cloud Completeness: ${cloud.state}.`),
    check("EVENTS_PRESENT", cloud.eventCount > 0, `${cloud.eventCount} evento(s) disponibles en cloud.`, { count: cloud.eventCount }),
    check(
      "CHUNKS_COMPLETE",
      cloud.chunkCount > 0 && cloud.missingChunkIndexes.length === 0,
      cloud.missingChunkIndexes.length ? `Faltan chunkIndex: ${cloud.missingChunkIndexes.join(",")}.` : `${cloud.chunkCount} chunk(s) sin huecos internos.`,
      { count: cloud.chunkCount, missing: cloud.missingChunkIndexes }
    ),
    check(
      "LOSSLESS_TRACK",
      cloud.pointPayloadDelta === 0,
      cloud.pointPayloadDelta === 0 ? "Conteo declarado y payload GPS coinciden." : `Delta de payload: ${cloud.pointPayloadDelta}.`,
      { raw: cloud.rawPointCount, declared: cloud.declaredPointCount, delta: cloud.pointPayloadDelta }
    ),
    check("WEB_COMPLETE", web.complete, `Web Completeness: ${web.state}.`),
    check("MAP_COMPLETE", c.mapPoints === cloud.validGpsPointCount && c.mapEvents === c.exportWaypoints, "Mapa consume el universo geoespacial esperado."),
    check("PLAYBACK_COMPLETE", c.playbackPoints === cloud.validGpsPointCount, "Playback consume todos los puntos GPS utilizables."),
    check("TIMELINE_COMPLETE", c.timelineEvents === cloud.eventCount, "Timeline consume todos los eventos cloud."),
    check("EXPORT_COMPLETE", c.exportPoints === cloud.validGpsPointCount && c.exportWaypoints === c.mapEvents, "Exportadores consumen el mismo universo válido que mapa/web."),
  ];

  const earned = checks.reduce((sum, item) => sum + (item.passed ? item.weight : 0), 0);
  const possible = checks.reduce((sum, item) => sum + item.weight, 0) || 100;
  const score = Math.max(0, Math.min(100, Math.round((earned / possible) * 100)));
  const observations = buildObservations({ trip, cloud, web });

  let primaryState;
  if (score === 100 && cloud.complete && web.complete) primaryState = "VERIFIED";
  else if (score < 50 || !trip || cloud.state === "MISSING_TRIP") primaryState = "CRITICAL";
  else primaryState = "WARNING";

  const states = [primaryState];
  if (observations.some((o) => o.code === "AUTO_CLEANED")) states.push("CLEANED");
  if (observations.some((o) => o.code === "LOSSLESS")) states.push("LOSSLESS");
  if (observations.some((o) => o.code === "RECOVERED")) states.push("RECOVERED");

  return {
    version: QUALITY_SCHEMA_VERSION,
    engineVersion: QUALITY_ENGINE_VERSION,
    generatedAt: Date.now(),
    score,
    primaryState,
    states: [...new Set(states)],
    checks,
    observations,
    passedChecks: checks.filter((item) => item.passed).length,
    failedChecks: checks.filter((item) => !item.passed).length,
    cloudState: cloud.state,
    webState: web.state,
    cloud,
    web,
  };
}

export function logTripQuality({ tripDocId, trip, events = [], trackChunks = [] } = {}) {
  webIntegrity("QUALITY_ENGINE_STARTED", {
    tripDocId: tripDocId ?? trip?.id ?? null,
    engineVersion: QUALITY_ENGINE_VERSION,
  });

  const report = evaluateTripQuality({ trip, events, trackChunks });

  report.checks.forEach((item) => {
    webIntegrity("QUALITY_CHECK", {
      tripDocId: tripDocId ?? trip?.id ?? null,
      check: item.name,
      passed: item.passed,
      weight: item.weight,
    });
  });

  report.observations.forEach((item) => {
    webIntegrity("QUALITY_OBSERVATION", {
      tripDocId: tripDocId ?? trip?.id ?? null,
      observation: item.code,
      level: item.level,
      value: item.value ?? null,
    });
  });

  webIntegrity("QUALITY_SCORE", {
    tripDocId: tripDocId ?? trip?.id ?? null,
    score: report.score,
    primaryState: report.primaryState,
    states: report.states.join(","),
    passedChecks: report.passedChecks,
    failedChecks: report.failedChecks,
    engineVersion: report.engineVersion,
  });

  return report;
}
