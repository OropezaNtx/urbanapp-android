import React from 'react';
import { CheckCircle2, CircleAlert, ShieldCheck, Sparkles } from 'lucide-react';

const val = (v) => v === null || v === undefined || v === '' ? '—' : String(v);

function stateClass(state) {
  if (state === 'VERIFIED' || state === 'LOSSLESS' || state === 'CLEANED' || state === 'RECOVERED') return 'quality-report-good';
  if (state === 'WARNING') return 'quality-report-warning';
  if (state === 'CRITICAL') return 'quality-report-critical';
  return 'quality-report-neutral';
}

export default function QualityReport({ report }) {
  if (!report) return null;

  return <section className="card quality-report-card">
    <div className="quality-report-head">
      <div>
        <h3><ShieldCheck size={18} /> Quality Report</h3>
        <p>Evaluación explicable construida sobre Cloud Completeness y Web Completeness. No reemplaza ninguna auditoría previa.</p>
      </div>
      <div className={`quality-report-score ${stateClass(report.primaryState)}`}>
        <b>{val(report.score)}</b>
        <span>/ 100</span>
      </div>
    </div>

    <div className="quality-report-states">
      {(report.states || []).map((state) => <span key={state} className={`quality-report-pill ${stateClass(state)}`}>{state}</span>)}
      <span className="quality-report-pill quality-report-neutral">Engine {val(report.engineVersion)}</span>
    </div>

    <div className="quality-report-grid">
      <div>
        <h4>Integrity Checks</h4>
        <div className="quality-check-list">
          {(report.checks || []).map((item) => <div key={item.name} className={`quality-check ${item.passed ? 'quality-check-pass' : 'quality-check-fail'}`}>
            {item.passed ? <CheckCircle2 size={16} /> : <CircleAlert size={16} />}
            <div>
              <b>{item.name}</b>
              <span>{item.message}</span>
            </div>
            <strong>{item.passed ? `+${item.weight}` : `0/${item.weight}`}</strong>
          </div>)}
        </div>
      </div>

      <div>
        <h4>Observaciones</h4>
        <div className="quality-observation-list">
          {!(report.observations || []).length && <div className="quality-observation"><Sparkles size={16} /><span>Sin observaciones adicionales.</span></div>}
          {(report.observations || []).map((item) => <div key={item.code} className="quality-observation">
            <Sparkles size={16} />
            <div><b>{item.code}</b><span>{item.message}</span></div>
          </div>)}
        </div>
      </div>
    </div>
  </section>;
}
