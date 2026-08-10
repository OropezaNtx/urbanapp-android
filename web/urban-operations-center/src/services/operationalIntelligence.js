import { webIntegrity } from './webIntegrity';

export const OPERATIONAL_INTELLIGENCE_VERSION = '3.3.0';

function n(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function finite(value) {
  return value !== null && value !== undefined && Number.isFinite(Number(value));
}

function toMillis(value) {
  if (!value) return 0;
  if (typeof value === 'number') return value;
  if (typeof value?.toMillis === 'function') return value.toMillis();
  if (finite(value?.seconds)) return Number(value.seconds) * 1000;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function isClosed(trip) {
  const status = String(trip?.status || '').toUpperCase();
  if (status === 'ACTIVE') return false;
  if (status === 'CLOSED') return true;
  return Boolean(trip?.endTime);
}

function sameLocalDay(ms, referenceMs = Date.now()) {
  if (!ms) return false;
  const a = new Date(ms);
  const b = new Date(referenceMs);
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate();
}

function label(value, fallback = 'Sin dato') {
  const text = String(value ?? '').trim();
  return text || fallback;
}

function pushGroup(map, key, sample) {
  const current = map.get(key) || {
    key,
    label: key,
    trips: 0,
    closedTrips: 0,
    healthSamples: 0,
    healthScoreSum: 0,
    gpsSamples: 0,
    gpsCoverageSum: 0,
    gpsAccuracySamples: 0,
    gpsAccuracySum: 0,
    gpsLossEvents: 0,
    networkDegradedTrips: 0,
    recoveries: 0,
    incidents: 0,
    delays: 0,
    flags: 0,
    eventCount: 0,
    distanceKm: 0,
  };

  current.trips += 1;
  current.closedTrips += sample.closed ? 1 : 0;
  if (finite(sample.healthScore)) {
    current.healthSamples += 1;
    current.healthScoreSum += Number(sample.healthScore);
  }
  if (finite(sample.gpsCoveragePct)) {
    current.gpsSamples += 1;
    current.gpsCoverageSum += Number(sample.gpsCoveragePct);
  }
  if (finite(sample.averageAccuracyM)) {
    current.gpsAccuracySamples += 1;
    current.gpsAccuracySum += Number(sample.averageAccuracyM);
  }
  current.gpsLossEvents += n(sample.gpsLossEvents);
  current.networkDegradedTrips += sample.networkState === 'DEGRADED' || sample.networkState === 'CRITICAL' ? 1 : 0;
  current.recoveries += n(sample.recoveries);
  current.incidents += n(sample.incidents);
  current.delays += n(sample.delays);
  current.flags += n(sample.flags);
  current.eventCount += n(sample.events);
  current.distanceKm += n(sample.distanceKm);
  map.set(key, current);
}

function finalizeGroup(group) {
  return {
    ...group,
    averageHealthScore: group.healthSamples ? group.healthScoreSum / group.healthSamples : null,
    averageGpsCoveragePct: group.gpsSamples ? group.gpsCoverageSum / group.gpsSamples : null,
    averageAccuracyM: group.gpsAccuracySamples ? group.gpsAccuracySum / group.gpsAccuracySamples : null,
    eventsPerKm: group.distanceKm > 0 ? group.eventCount / group.distanceKm : null,
  };
}

export function buildOperationalIntelligence(records, { now = Date.now() } = {}) {
  const samples = (records || []).map(({ trip, detail }) => {
    const analytics = detail?.operationalAnalytics || {};
    const health = detail?.operationalHealth || null;
    const quality = detail?.quality || null;
    const gps = analytics.gps || {};
    const device = analytics.device || {};
    const recovery = analytics.recovery || {};
    const events = analytics.events || {};
    const tripMetrics = analytics.trip || {};
    const closed = isClosed(trip);
    const startMs = toMillis(trip?.startTime);
    const endMs = toMillis(trip?.endTime);
    const operationalState = closed ? (health?.state || 'NOT_AVAILABLE') : 'IN_PROGRESS';
    const healthScore = closed && health?.telemetryAvailable ? health?.score : null;

    return {
      id: trip?.id,
      trip,
      detail,
      closed,
      startMs,
      endMs,
      today: sameLocalDay(startMs || endMs, now),
      operationalState,
      healthScore,
      qualityScore: quality?.score ?? null,
      gpsState: health?.domains?.gps?.state || (closed ? 'NOT_AVAILABLE' : 'IN_PROGRESS'),
      deviceState: health?.domains?.device?.state || 'NOT_AVAILABLE',
      networkState: health?.domains?.network?.state || 'NOT_AVAILABLE',
      recoveryState: health?.domains?.recovery?.state || 'NOT_AVAILABLE',
      syncState: health?.domains?.sync?.state || 'NOT_AVAILABLE',
      telemetryAvailable: Boolean(analytics.telemetry),
      gpsCoveragePct: gps.coveragePct ?? null,
      averageAccuracyM: gps.averageAccuracyM ?? null,
      gpsLossEvents: gps.lossEvents ?? 0,
      gpsNoSignalMs: gps.noGpsMs ?? 0,
      networkCoveragePct: device.networkCoveragePct ?? null,
      networkOfflineMs: device.networkOfflineDurationMs ?? 0,
      reconnections: device.reconnections?.value ?? 0,
      batteryEndPct: device.battery?.endPct ?? null,
      recoveries: recovery.count ?? 0,
      assistedRecoveries: recovery.assistedCount ?? 0,
      fgsBlocked: recovery.fgsBlockedCount ?? 0,
      events: events.total ?? 0,
      incidents: events.incidents ?? 0,
      delays: events.delays ?? 0,
      flags: events.flags ?? 0,
      distanceKm: tripMetrics.distanceKm ?? 0,
      route: label(trip?.routeName, 'Ruta sin nombre'),
      operator: label(trip?.aforador ?? trip?.observerName, 'Operador sin identificar'),
      device: label(trip?.deviceNumber ?? trip?.deviceInstallationId, 'Dispositivo sin identificar'),
      vehicle: label(trip?.vehicleEco ?? trip?.plateNumber, 'Unidad sin identificar'),
      issues: health?.issues || [],
    };
  });

  const today = samples.filter((sample) => sample.today);
  const closed = samples.filter((sample) => sample.closed);
  const evaluable = closed.filter((sample) => finite(sample.healthScore));
  const degraded = closed.filter((sample) => sample.operationalState === 'DEGRADED');
  const critical = closed.filter((sample) => sample.operationalState === 'CRITICAL');
  const recovered = closed.filter((sample) => sample.operationalState === 'RECOVERED');
  const inProgress = samples.filter((sample) => !sample.closed);
  const healthy = closed.filter((sample) => sample.operationalState === 'HEALTHY');

  const routeGroups = new Map();
  const operatorGroups = new Map();
  const deviceGroups = new Map();
  samples.forEach((sample) => {
    pushGroup(routeGroups, sample.route, sample);
    pushGroup(operatorGroups, sample.operator, sample);
    pushGroup(deviceGroups, sample.device, sample);
  });

  const routes = [...routeGroups.values()].map(finalizeGroup);
  const operators = [...operatorGroups.values()].map(finalizeGroup);
  const devices = [...deviceGroups.values()].map(finalizeGroup);

  const averageHealthScore = evaluable.length
    ? evaluable.reduce((sum, sample) => sum + Number(sample.healthScore), 0) / evaluable.length
    : null;
  const averageGpsCoveragePct = closed.filter((s) => finite(s.gpsCoveragePct));
  const gpsCoverageAverage = averageGpsCoveragePct.length
    ? averageGpsCoveragePct.reduce((sum, s) => sum + Number(s.gpsCoveragePct), 0) / averageGpsCoveragePct.length
    : null;

  const needsAttention = [...critical, ...degraded]
    .sort((a, b) => n(a.healthScore, 101) - n(b.healthScore, 101));

  const poorGpsDevices = devices
    .filter((d) => d.gpsSamples > 0)
    .sort((a, b) => {
      const aCoverage = a.averageGpsCoveragePct ?? 101;
      const bCoverage = b.averageGpsCoveragePct ?? 101;
      if (aCoverage !== bCoverage) return aCoverage - bCoverage;
      return (b.averageAccuracyM ?? 0) - (a.averageAccuracyM ?? 0);
    });

  const eventHeavyRoutes = routes
    .filter((r) => r.eventCount > 0)
    .sort((a, b) => (b.eventsPerKm ?? b.eventCount) - (a.eventsPerKm ?? a.eventCount));

  const operatorQuality = operators
    .filter((o) => o.healthSamples > 0 || o.gpsSamples > 0)
    .sort((a, b) => (b.averageHealthScore ?? -1) - (a.averageHealthScore ?? -1));

  const report = {
    version: OPERATIONAL_INTELLIGENCE_VERSION,
    generatedAt: now,
    samples,
    summary: {
      total: samples.length,
      today: today.length,
      closed: closed.length,
      inProgress: inProgress.length,
      evaluable: evaluable.length,
      healthy: healthy.length,
      degraded: degraded.length,
      critical: critical.length,
      recovered: recovered.length,
      averageHealthScore,
      averageGpsCoveragePct: gpsCoverageAverage,
      telemetryCoveragePct: closed.length ? (closed.filter((s) => s.telemetryAvailable).length / closed.length) * 100 : null,
    },
    today: {
      total: today.length,
      failed: today.filter((s) => s.closed && s.operationalState === 'CRITICAL'),
      degraded: today.filter((s) => s.closed && s.operationalState === 'DEGRADED'),
      recovered: today.filter((s) => s.closed && s.operationalState === 'RECOVERED'),
      inProgress: today.filter((s) => !s.closed),
    },
    attention: needsAttention,
    rankings: {
      poorGpsDevices: poorGpsDevices.slice(0, 10),
      eventHeavyRoutes: eventHeavyRoutes.slice(0, 10),
      operatorQuality: operatorQuality.slice(0, 10),
      networkDevices: devices
        .filter((d) => d.networkDegradedTrips > 0)
        .sort((a, b) => b.networkDegradedTrips - a.networkDegradedTrips)
        .slice(0, 10),
      recoveryDevices: devices
        .filter((d) => d.recoveries > 0)
        .sort((a, b) => b.recoveries - a.recoveries)
        .slice(0, 10),
    },
  };

  webIntegrity('OPERATIONAL_INTELLIGENCE', {
    version: report.version,
    total: report.summary.total,
    today: report.summary.today,
    inProgress: report.summary.inProgress,
    healthy: report.summary.healthy,
    degraded: report.summary.degraded,
    critical: report.summary.critical,
    recovered: report.summary.recovered,
    averageHealthScore: report.summary.averageHealthScore?.toFixed?.(1) ?? null,
  });

  return report;
}
