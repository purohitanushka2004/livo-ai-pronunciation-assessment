import React, { useState } from 'react';
import UploadForm from './components/UploadForm.jsx';
import ResultsView from './components/ResultsView.jsx';

export default function App() {
  const [result, setResult] = useState(null);
  const [fileName, setFileName] = useState(null);

  return (
    <div className="page">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">
            <span className="bar b1" />
            <span className="bar b2" />
            <span className="bar b3" />
            <span className="bar b4" />
          </span>
          <div>
            <div className="brand-name">Livo AI</div>
            <div className="brand-sub">Pronunciation Coach</div>
          </div>
        </div>
      </header>

      <main className="content">
        {!result && (
          <UploadForm
            onResult={(r, name) => {
              setResult(r);
              setFileName(name);
            }}
          />
        )}

        {result && (
          <ResultsView
            result={result}
            fileName={fileName}
            onReset={() => setResult(null)}
          />
        )}
      </main>

      <footer className="footer">
        Audio is processed in memory and deleted immediately after scoring. Nothing is stored. &middot;{' '}
        <span className="footer-muted">DPDP-aligned by design</span>
      </footer>
    </div>
  );
}
