export function enrichOperationalAnalyticsWithTelemetry(analytics, telemetry) {
  if (!analytics || !telemetry) return analytics;

  const battery = telemetry.battery || {};
  const heartbeat = telemetry.heartbeat || {};
  const network = telemetry.network || {};
  const recovery = telemetry.recovery || {};
  const sync = telemetry.sync || {};

  const firstUpload = Number(sync.firstUploadStartedAt || 0);
  const lastUpload = Number(sync.lastUploadFinishedAt || 0);
  const activitySpanMs = firstUpload > 0 && lastUpload >= firstUpload
    ? lastUpload - firstUpload
    : null;
  const lastRunDurationMs = Number.isFinite(Number(sync.lastRunDurationMs))
    ? Number(sync.lastRunDurationMs)
    : null;

  return {
    ...analytics,
    telemetry,
    device: {
      ...(analytics.device || {}),
      battery: {
        availability: battery.samples > 0 ? "AVAILABLE" : "NOT_AVAILABLE",
        value: battery.averagePct ?? null,
        startPct: battery.startPct ?? null,
        endPct: battery.endPct ?? null,
        minPct: battery.minPct ?? null,
        maxPct: battery.maxPct ?? null,
        samples: battery.samples ?? 0,
        chargingSamples: battery.chargingSamples ?? 0,
      },
      heartbeat: {
        availability: heartbeat.samples > 0 ? "AVAILABLE" : "NOT_AVAILABLE",
        value: heartbeat.averageAgeMs ?? null,
        averageAgeMs: heartbeat.averageAgeMs ?? null,
        maxAgeMs: heartbeat.maxAgeMs ?? null,
        staleSamples: heartbeat.staleSamples ?? 0,
        samples: heartbeat.samples ?? 0,
        lastHeartbeatAt: heartbeat.lastHeartbeatAt ?? null,
      },
      reconnections: {
        availability: network.samples > 0 ? "AVAILABLE" : "NOT_AVAILABLE",
        value: network.reconnections ?? 0,
      },
      networkCoveragePct: network.coveragePct ?? null,
      networkOfflineDurationMs: network.offlineDurationMs ?? null,
      networkTransitions: network.transitions ?? 0,
      lastNetworkType: network.lastType ?? null,
      lastNetworkConnected: network.lastConnected ?? null,
    },
    recovery: {
      count: recovery.count ?? 0,
      assistedCount: recovery.assistedCount ?? 0,
      fgsBlockedCount: recovery.fgsBlockedCount ?? 0,
      watchdogHealthyCount: recovery.watchdogHealthyCount ?? 0,
      lastRecoveryAt: recovery.lastRecoveryAt ?? null,
      lastRecoveryGapMs: recovery.lastRecoveryGapMs ?? 0,
    },
    sync: {
      ...(analytics.sync || {}),
      payloadBytesActual: sync.payloadBytes ?? null,
      syncRuns: sync.runs ?? 0,
      completedSyncRuns: sync.completedRuns ?? 0,
      retries: sync.retries ?? 0,
      failedItems: sync.failedItems ?? 0,
      confirmedItems: sync.confirmedItems ?? 0,
      firstUploadStartedAt: sync.firstUploadStartedAt ?? null,
      lastUploadFinishedAt: sync.lastUploadFinishedAt ?? null,
      lastCloudConfirmedAt: sync.lastCloudConfirmedAt ?? null,
      syncActivitySpanMs: activitySpanMs,
      lastSyncRunStartedAt: sync.lastRunStartedAt ?? null,
      lastSyncRunFinishedAt: sync.lastRunFinishedAt ?? null,
      lastSyncRunDurationMs: lastRunDurationMs,
      averageSyncRunDurationMs: sync.averageRunDurationMs ?? null,
      maxSyncRunDurationMs: sync.maxRunDurationMs ?? null,
      lastResult: sync.lastResult ?? null,
      uploadDurationMs: {
        availability: lastRunDurationMs != null ? "AVAILABLE" : "NOT_AVAILABLE",
        value: lastRunDurationMs,
      },
      cloudDurationMs: {
        availability: sync.averageCloudRoundTripMs != null ? "AVAILABLE" : "NOT_AVAILABLE",
        value: sync.averageCloudRoundTripMs ?? null,
      },
      averageCloudRoundTripMs: sync.averageCloudRoundTripMs ?? null,
      maxCloudRoundTripMs: sync.maxCloudRoundTripMs ?? null,
    },
    availability: {
      ...(analytics.availability || {}),
      batteryHistory: battery.samples > 0,
      heartbeatHistory: heartbeat.samples > 0,
      reconnectionHistory: network.samples > 0,
      networkHistory: network.samples > 0,
      recoveryHistory: true,
      uploadTiming: lastRunDurationMs != null,
      syncActivitySpan: activitySpanMs != null,
      cloudTiming: sync.averageCloudRoundTripMs != null,
      webAnalysisTiming: true,
    },
  };
}
