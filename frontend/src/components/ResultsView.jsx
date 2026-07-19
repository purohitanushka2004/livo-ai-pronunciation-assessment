import React, { useState } from 'react';
import ScoreGauge from './ScoreGauge.jsx';

const STATUS_LABEL = {
  good: 'Clear',
  unclear: 'Unclear',
  mispronounced: 'Mispronounced',
  omitted: 'Skipped',
  extra: 'Extra word',
};

export default function ResultsView({ result, fileName, onReset }) {
  const [activeWordIdx, setActiveWordIdx] = useState(null);
  const flaggedWords = (result.words || []).filter((w) => w.status !== 'good');
  const isReferenceMode = result.mode === 'reference';

  return (
    <section className="results">
      <div className="results-header">
        <div>
          <h2>Results {isReferenceMode && <span className="mode-badge">Read-aloud</span>}</h2>
          <p className="lede-small">{fileName} · {result.durationSeconds}s · {result.speakingRateWpm} wpm</p>
        </div>
        <button className="secondary-btn" onClick={onReset}>Analyze another</button>
      </div>

      <div className="card scores-card">
        <ScoreGauge score={result.overallScore} label="Overall" />
        <ScoreGauge score={result.clarityScore} label="Clarity" />
        <ScoreGauge score={result.fluencyScore} label="Fluency" />
      </div>

      {result.summary?.length > 0 && (
        <div className="card summary-card">
          <h3>Takeaways</h3>
          <ul>
            {result.summary.map((s, i) => (
              <li key={i}>{s}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="card transcript-card">
        <h3>{isReferenceMode ? 'Target sentence' : 'Transcript'}</h3>
        <p className="transcript-hint">
          Tap a highlighted word to see what went wrong. Words in{' '}
          <span className="legend legend-unclear">amber</span> were unclear; words in{' '}
          <span className="legend legend-bad">red</span> were mispronounced
          {isReferenceMode && (
            <>
              ; words with a <span className="legend legend-skip">dashed strike</span> were skipped entirely
            </>
          )}
          .
        </p>
        <div className="transcript">
          {(result.words || []).map((w, i) => (
            <span key={i} className="word-wrap">
              <button
                className={`word word--${w.status}`}
                onClick={() => setActiveWordIdx(activeWordIdx === i ? null : i)}
              >
                {w.word}
              </button>
              {activeWordIdx === i && (
                <span className="tooltip">
                  <strong>{STATUS_LABEL[w.status]}</strong>
                  {w.status !== 'omitted' && <> · score {w.wordScore}/100</>}
                  <br />
                  {w.note}
                </span>
              )}
            </span>
          ))}
        </div>
      </div>

      {flaggedWords.length > 0 && (
        <div className="card flagged-card">
          <h3>Words to practice ({flaggedWords.length})</h3>
          <table className="flagged-table">
            <thead>
              <tr>
                <th>Word</th>
                <th>Issue</th>
                <th>Score</th>
              </tr>
            </thead>
            <tbody>
              {flaggedWords.map((w, i) => (
                <tr key={i}>
                  <td className="flagged-word">{w.word}</td>
                  <td>{STATUS_LABEL[w.status]}</td>
                  <td>{w.wordScore}/100</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
