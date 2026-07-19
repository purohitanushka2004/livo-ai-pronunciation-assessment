import React from 'react';

export default function ScoreGauge({ score, label = 'Overall Score' }) {
  const radius = 54;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (score / 100) * circumference;

  const color = score >= 85 ? 'var(--good)' : score >= 65 ? 'var(--warn)' : 'var(--bad)';

  return (
    <div className="gauge">
      <svg width="140" height="140" viewBox="0 0 140 140">
        <circle cx="70" cy="70" r={radius} className="gauge-track" />
        <circle
          cx="70"
          cy="70"
          r={radius}
          className="gauge-fill"
          style={{ stroke: color, strokeDasharray: circumference, strokeDashoffset: offset }}
        />
        <text x="70" y="66" textAnchor="middle" className="gauge-number">{score}</text>
        <text x="70" y="88" textAnchor="middle" className="gauge-suffix">/ 100</text>
      </svg>
      <div className="gauge-label">{label}</div>
    </div>
  );
}
