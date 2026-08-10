import { webIntegrity } from './webIntegrity';

const VERSION = '3.2C.0';

function n(value, fallback = null) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function clamp(value, min = 0, max = 100) {
  return Math.max(min, Math.min(max, value));
}

function issue(code, severity, domain, message, deduction = 0, value = null) {
  return { code, severity, domain, message, deduction, value };
}

function domainState(issues, recovered = false) {
  if (issues.some((item) => item.severity === 'CRITICAL')) return 'CRITICAL';
  if (issues.some((item) => item.severity === 'WARNING')) return 'DEGRADED';
  if (recovered) return 'RECOVERED';
  return 'HEALTHY';
}

function assessGps(analytics) {
  const gps = analytics?.gps || {};
  const issues = [];
  const coverage = n(gps.coveragePct);
  const accuracy = n(gps.averageAccuracyM);
  const outliers = n(gps.outlierRatePct, 0);
  const noGpsMs = n(gps.noGpsMs, 0);

  if (coverage != null) {
    if (coverage < 70) issues.push(issue('GPS_COVERAGE_CRITICAL', 'CRITICAL', 'GPS', `Cobertura GPS crítica (${coverage.toFixed(1)}%).`, 30, coverage));
    else if (coverage < 90) issues.push(issue('GPS_COVERAGE_LOW', 'WARNING', 'GPS', `Cobertura GPS baja (${coverage.toFixed(1)}%).`, 18, coverage));
    else if (coverage < 98) issues.push(issue('GPS_COVERAGE_REDUCED', 'WARNING', 'GPS', `Cobertura GPS reducida (${coverage.toFixed(1)}%).`, 8, coverage));
  }

  if (accuracy != null) {
    if (accuracy > 50) issues.push(issue('GPS_ACCURACY_POOR', 'WARNING', 'GPS', `Precisión GPS promedio baja (${accuracy.toFixed(1)} m).`, 10, accuracy));
    else if (accuracy > 25) issues.push(issue('GPS_ACCURACY_REDUCED', 'WARNING', 'GPS', `Precisión GPS promedio moderada (${accuracy.toFixed(1)} m).`, 5, accuracy));
  }

  if (outliers > 5) issues.push(issue('GPS_OUTLIERS_HIGH', 'WARNING', 'GPS', `Tasa de segmentos atípicos alta (${outliers.toFixed(2)}%).`, 10, outliers));
  else if (outliers > 1) issues.push(issue('GPS_OUTLIERS_PRESENT', 'WARNING', 'GPS', `Se detectaron segmentos atípicos (${outliers.toFixed(2)}%).`, 4, outliers));

  if (noGpsMs > 5 * 60_000) issues.push(issue('GPS_OUTAGE_LONG', 'CRITICAL', 'GPS', 'El recorrido acumuló más de 5 minutos sin GPS observado.', 20, noGpsMs));
  else if (noGpsMs > 60_000) issues.push(issue('GPS_OUTAGE', 'WARNING', 'GPS', 'El recorrido acumuló más de 1 minuto sin GPS observado.', 8, noGpsMs));

  return { state: domainState(issues), issues };
}

function assessDevice(analytics) {
  const device = analytics?.device || {};
  const telemetry = analytics?.telemetry;
  if (!telemetry) return { state: 'NOT_AVAILABLE', issues: [], available: false };

  const issues = [];
  const battery = device.battery || {};
  const heartbeat = device.heartbeat || {};
  const endBattery = n(battery.endPct ?? battery.value);
  const minBattery = n(battery.minPct);
  const staleSamples = n(heartbeat.staleSamples, 0);
  const maxHeartbeatAge = n(heartbeat.maxAgeMs, 0);

  const batteryReference = endBattery ?? minBattery;
  if (batteryReference != null) {
    if (batteryReference <= 5) issues.push(issue('BATTERY_CRITICAL', 'CRITICAL', 'DEVICE', `Batería crítica (${batteryReference.toFixed(0)}%).`, 20, batteryReference));
    else if (batteryReference < 15) issues.push(issue('BATTERY_LOW', 'WARNING', 'DEVICE', `Batería baja al final del recorrido (${batteryReference.toFixed(0)}%).`, 10, batteryReference));
  }

  if (staleSamples > 0 || maxHeartbeatAge >= 30_000) {
    issues.push(issue('HEARTBEAT_STALE', 'WARNING', 'DEVICE', `Se detectaron ${staleSamples || 1} muestras de heartbeat vencido.`, 12, staleSamples));
  }

  return { state: domainState(issues), issues, available: true };
}

function assessNetwork(analytics) {
  const device = analytics?.device || {};
  if (!analytics?.telemetry) return { state: 'NOT_AVAILABLE', issues: [], available: false };

  const issues = [];
  const coverage = n(device.networkCoveragePct);
  const offlineMs = n(device.networkOfflineDurationMs, 0);
  const reconnections = n(device.reconnections?.value, 0);

  if (coverage != null) {
    if (coverage < 70) issues.push(issue('NETWORK_COVERAGE_CRITICAL', 'CRITICAL', 'NETWORK', `Cobertura de red crítica (${coverage.toFixed(1)}%).`, 18, coverage));
    else if (coverage < 90) issues.push(issue('NETWORK_COVERAGE_LOW', 'WARNING', 'NETWORK', `Cobertura de red baja (${coverage.toFixed(1)}%).`, 10, coverage));
    else if (coverage < 98) issues.push(issue('NETWORK_COVERAGE_REDUCED', 'WARNING', 'NETWORK', `Cobertura de red reducida (${coverage.toFixed(1)}%).`, 4, coverage));
  }

  if (offlineMs > 5 * 60_000) issues.push(issue('NETWORK_OFFLINE_LONG', 'WARNING', 'NETWORK', 'El dispositivo acumuló más de 5 minutos sin red.', 8, offlineMs));
  if (reconnections > 5) issues.push(issue('NETWORK_RECONNECTIONS_HIGH', 'WARNING', 'NETWORK', `Se registraron ${reconnections} reconexiones de red.`, 6, reconnections));

  return { state: domainState(issues), issues, available: true };
}

function assessRecovery(analytics) {
  if (!analytics?.telemetry) return { state: 'NOT_AVAILABLE', issues: [], available: false };
  const recovery = analytics?.recovery || {};
  const count = n(recovery.count, 0);
  const assisted = n(recovery.assistedCount, 0);
  const blocked = n(recovery.fgsBlockedCount, 0);
  const issues = [];

  if (blocked > 0) issues.push(issue('FGS_BLOCKED', 'INFO', 'RECOVERY', `Android bloqueó ${blocked} intento(s) de arranque del servicio y requirió recuperación asistida.`, 0, blocked));
  if (assisted > 0) issues.push(issue('ASSISTED_RECOVERY', 'INFO', 'RECOVERY', `Se registraron ${assisted} recuperación(es) asistida(s).`, 0, assisted));
  if (count > 0) issues.push(issue('TRACKING_RECOVERED', 'INFO', 'RECOVERY', `Tracking fue recuperado correctamente ${count} vez/veces.`, 0, count));

  return {
    state: count > 0 || assisted > 0 || blocked > 0 ? 'RECOVERED' : 'HEALTHY',
    issues,
    available: true,
    recovered: count > 0 || assisted > 0 || blocked > 0,
  };
}

function assessSync(analytics) {
  const sync = analytics?.sync || {};
  if (!analytics?.telemetry) return { state: 'NOT_AVAILABLE', issues: [], available: false };

  const issues = [];
  const failures = n(sync.failedItems, 0);
  const retries = n(sync.retries, 0);
  const lastResult = String(sync.lastResult || '').toUpperCase();
  const averageRoundTrip = n(sync.averageCloudRoundTripMs);
  const maxRun = n(sync.maxSyncRunDurationMs);

  if (lastResult && !['SUCCESS', 'OK'].includes(lastResult)) {
    issues.push(issue('SYNC_LAST_RESULT_FAILED', 'CRITICAL', 'SYNC', `Último resultado de sincronización: ${lastResult}.`, 20, lastResult));
  }
  if (failures > 0) issues.push(issue('SYNC_FAILED_ITEMS', 'WARNING', 'SYNC', `${failures} item(s) registraron fallo durante sincronización.`, 12, failures));
  if (retries > 0) issues.push(issue('SYNC_RETRIES', 'WARNING', 'SYNC', `${retries} reintento(s) de sincronización.`, Math.min(8, 2 + retries), retries));
  if (averageRoundTrip != null && averageRoundTrip > 3_000) issues.push(issue('SYNC_CLOUD_SLOW', 'WARNING', 'SYNC', `Round-trip cloud promedio alto (${averageRoundTrip.toFixed(0)} ms).`, 6, averageRoundTrip));
  if (maxRun != null && maxRun > 60_000) issues.push(issue('SYNC_RUN_SLOW', 'WARNING', 'SYNC', 'Al menos un run de sincronización tardó más de 60 segundos.', 5, maxRun));

  return { state: domainState(issues), issues, available: true };
}

export function buildOperationalHealth({ tripDocId, analytics }) {
  if (!analytics) return null;

  const gps = assessGps(analytics);
  const device = assessDevice(analytics);
  const network = assessNetwork(analytics);
  const recovery = assessRecovery(analytics);
  const sync = assessSync(analytics);
  const domains = { gps, device, network, recovery, sync };
  const issues = Object.values(domains).flatMap((domain) => domain.issues || []);
  const telemetryAvailable = Boolean(analytics.telemetry);

  const deductions = issues.reduce((sum, item) => sum + (item.deduction || 0), 0);
  const score = clamp(100 - deductions);
  const hasCritical = issues.some((item) => item.severity === 'CRITICAL');
  const hasWarning = issues.some((item) => item.severity === 'WARNING');
  const recovered = recovery.recovered === true;

  let state = 'HEALTHY';
  if (!telemetryAvailable) state = 'NOT_AVAILABLE';
  else if (hasCritical || score < 60) state = 'CRITICAL';
  else if (hasWarning || score < 85) state = 'DEGRADED';
  else if (recovered) state = 'RECOVERED';

  const report = {
    version: VERSION,
    state,
    score,
    telemetryAvailable,
    generatedAt: Date.now(),
    domains,
    issues,
    summary: {
      critical: issues.filter((item) => item.severity === 'CRITICAL').length,
      warnings: issues.filter((item) => item.severity === 'WARNING').length,
      info: issues.filter((item) => item.severity === 'INFO').length,
      recovered,
    },
  };

  webIntegrity('OPERATIONAL_HEALTH', {
    tripDocId,
    version: VERSION,
    state,
    score,
    telemetryAvailable,
    gps: gps.state,
    device: device.state,
    network: network.state,
    recovery: recovery.state,
    sync: sync.state,
    critical: report.summary.critical,
    warnings: report.summary.warnings,
    recovered,
  });

  return report;
}
