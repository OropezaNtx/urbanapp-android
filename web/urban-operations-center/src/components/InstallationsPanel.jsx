import React, { useState } from 'react';
import { CheckCircle, Clock, Shield, Briefcase, XCircle, RefreshCw, Activity, ShieldAlert } from 'lucide-react';
import { updateInstallationStatus } from '../services/installations';

const toMillis = (v) => {
  if (!v) return 0;
  if (typeof v === 'number') return v;
  if (v.toMillis) return v.toMillis();
  if (v.seconds) return v.seconds * 1000;
  return new Date(v).getTime() || 0;
};

const fmt = (v) => {
  const ms = toMillis(v);
  if (!ms) return '—';
  return new Date(ms).toLocaleString('es-MX');
};

export default function InstallationsPanel({ installations }) {
  const [loading, setLoading] = useState(false);

  async function handleApprove(id) {
    const orgId = prompt("Ingrese ID de Organización (Workspace):", "demo_org");
    const licId = prompt("Ingrese ID de Licencia:", "lic_default_pilot");

    if (!orgId || !licId) return;

    setLoading(true);
    try {
      await updateInstallationStatus(id, 'ACTIVE', orgId, licId);
    } catch (e) {
      alert("Error: " + e.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleRevoke(id) {
    if (!confirm("¿Está seguro de REVOCAR esta instalación? El dispositivo no podrá iniciar nuevos levantamientos.")) return;

    setLoading(true);
    try {
      await updateInstallationStatus(id, 'REVOKED', null, null);
    } catch (e) {
      alert("Error: " + e.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="installations-panel">
      <div className="table">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Dispositivo / OS</th>
              <th>Versión</th>
              <th>Status</th>
              <th>Workspace / Licencia</th>
              <th>Telemetría (Check / Sync / HB)</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {installations.map(i => (
              <tr key={i.id}>
                <td><code>{i.id.substring(0, 8)}...</code></td>
                <td>
                  <div><b>{i.manufacturer} {i.model}</b></div>
                  <small>Android {i.androidVersion} (SDK {i.sdkInt})</small>
                  <small>{i.packageName}</small>
                </td>
                <td>{i.appVersionName} ({i.appVersionCode})</td>
                <td>
                  <span className={`badge status-${(i.status || 'unknown').toLowerCase()}`}>
                    {i.status === 'PENDING' && <Clock size={12} />}
                    {i.status === 'ACTIVE' && <CheckCircle size={12} />}
                    {i.status === 'REVOKED' && <XCircle size={12} />}
                    {i.status || 'UNKNOWN'}
                  </span>
                </td>
                <td>
                    <div><Briefcase size={12} /> {i.organizationId || '—'}</div>
                    <div><Shield size={12} /> {i.licenseId || '—'}</div>
                </td>
                <td>
                    <div title="Last License Check"><Shield size={12} /> {fmt(i.lastLicenseCheckAt)}</div>
                    <div title="Last Data Sync"><RefreshCw size={12} /> {fmt(i.lastSyncAt)}</div>
                    <div title="Last Heartbeat"><Activity size={12} /> {fmt(i.lastHeartbeatAt || i.lastSeenAt)}</div>
                </td>
                <td>
                  <div className="row" style={{ gap: '8px' }}>
                    {i.status === 'PENDING' && (
                        <button className="btn-success" onClick={() => handleApprove(i.id)} disabled={loading}>
                        <CheckCircle size={14} /> Aprobar
                        </button>
                    )}
                    {i.status === 'ACTIVE' && (
                        <button className="btn-revoke" onClick={() => handleRevoke(i.id)} disabled={loading} style={{ backgroundColor: '#ef4444', color: 'white', border: 'none', padding: '6px 12px', borderRadius: '8px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: '600' }}>
                        <ShieldAlert size={14} /> Revocar
                        </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
