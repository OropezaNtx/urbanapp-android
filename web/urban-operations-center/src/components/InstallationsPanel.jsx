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

  async function handleApprove(i) {
    const workspaceId = prompt("Ingrese ID de Cliente / Empresa:", "cliente_nuevo");
    const licenseId = prompt("Ingrese Plan de Licencia:", "plan_piloto_7dias");

    if (!workspaceId || !licenseId) return;

    setLoading(true);
    try {
      await updateInstallationStatus(i.id, 'ACTIVE', { workspaceId, licenseId }, i.ownerUid);
    } catch (e) {
      alert("Error: " + e.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleRevoke(i) {
    if (!confirm("¿Está seguro de REVOCAR el acceso a este equipo? No podrá iniciar nuevos levantamientos.")) return;

    setLoading(true);
    try {
      await updateInstallationStatus(i.id, 'REVOKED', { workspaceId: null, licenseId: null }, i.ownerUid);
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
              <th>ID Equipo</th>
              <th>Modelo / OS</th>
              <th>Versión App</th>
              <th>Estado</th>
              <th>Cliente</th>
              <th>Licencia</th>
              <th>Último Reporte</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {installations.map(i => (
              <tr key={i.id}>
                <td title={i.id}><code>{i.id.substring(0, 8)}</code></td>
                <td>
                  <div><b>{i.manufacturer} {i.model}</b></div>
                  <small>Android {i.androidVersion}</small>
                </td>
                <td>{i.appVersionName}</td>
                <td>
                  <span className={`badge status-${(i.status || 'unknown').toLowerCase()}`}>
                    {i.status === 'PENDING' && <Clock size={12} />}
                    {i.status === 'ACTIVE' && <CheckCircle size={12} />}
                    {i.status === 'REVOKED' && <XCircle size={12} />}
                    {i.status === 'PENDING' ? 'ESPERANDO ACTIVACIÓN' : (i.status === 'ACTIVE' ? 'OPERATIVO' : 'ACCESO REVOCADO')}
                  </span>
                </td>
                <td>
                    <div><Briefcase size={12} /> {i.workspaceId || '—'}</div>
                </td>
                <td>
                    <div><Shield size={12} /> {i.licenseId || '—'}</div>
                </td>
                <td>
                    <div title="Último respaldo de datos"><RefreshCw size={12} /> {fmt(i.lastSyncAt)}</div>
                    <div title="Última actividad"><Activity size={12} /> {fmt(i.lastHeartbeatAt || i.lastSeenAt)}</div>
                </td>
                <td>
                  <div className="row" style={{ gap: '8px' }}>
                    {i.status === 'PENDING' && (
                        <button className="btn-success" onClick={() => handleApprove(i)} disabled={loading}>
                        <CheckCircle size={14} /> Aprobar
                        </button>
                    )}
                    {i.status === 'ACTIVE' && (
                        <button className="btn-revoke" onClick={() => handleRevoke(i)} disabled={loading} style={{ backgroundColor: '#ef4444', color: 'white', border: 'none', padding: '6px 12px', borderRadius: '8px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: '600' }}>
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
