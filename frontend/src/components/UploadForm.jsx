import React, { useRef, useState } from 'react';
import { analyzeAudio } from '../api.js';
import AudioRecorder from './AudioRecorder.jsx';
import { randomPracticeSentence } from '../data/practiceSentences.js';

const MIN_SECONDS = 30;
const MAX_SECONDS = 45;

export default function UploadForm({ onResult }) {
  const [consentGiven, setConsentGiven] = useState(false);
  const [speechMode, setSpeechMode] = useState('reference'); // 'reference' | 'free'
  const [sentence, setSentence] = useState(() => randomPracticeSentence());
  const [inputMode, setInputMode] = useState('record'); // 'record' | 'file'

  // Recorded-audio state
  const [recordedBlob, setRecordedBlob] = useState(null);
  const [recordedSeconds, setRecordedSeconds] = useState(0);

  // File-upload state
  const [selectedFile, setSelectedFile] = useState(null);
  const [clientDuration, setClientDuration] = useState(null);
  const [durationError, setDurationError] = useState(null);
  const inputRef = useRef(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  function handleFileChange(e) {
    const file = e.target.files?.[0];
    setError(null);
    setDurationError(null);
    setClientDuration(null);
    setSelectedFile(null);
    if (!file) return;

    const audio = document.createElement('audio');
    audio.preload = 'metadata';
    audio.onloadedmetadata = () => {
      const duration = audio.duration;
      setClientDuration(duration);
      if (duration < MIN_SECONDS - 1 || duration > MAX_SECONDS + 1) {
        setDurationError(
          `This clip is ${duration.toFixed(1)}s long. Please upload something between ${MIN_SECONDS} and ${MAX_SECONDS} seconds.`
        );
      }
      URL.revokeObjectURL(audio.src);
    };
    audio.src = URL.createObjectURL(file);
    setSelectedFile(file);
  }

  function shuffleSentence() {
    setSentence((prev) => randomPracticeSentence(prev.id));
  }

  const referenceText = speechMode === 'reference' ? sentence.text : null;

  const recordedReady =
    inputMode === 'record' &&
    recordedBlob &&
    recordedSeconds >= MIN_SECONDS - 1 &&
    recordedSeconds <= MAX_SECONDS + 1;

  const fileReady = inputMode === 'file' && selectedFile && !durationError;

  const canSubmit = consentGiven && (recordedReady || fileReady) && !loading;

  async function handleSubmit() {
    if (!canSubmit) return;
    setLoading(true);
    setError(null);
    try {
      let fileToSend;
      let displayName;
      if (inputMode === 'record') {
        const ext = recordedBlob.type.includes('ogg') ? 'ogg' : recordedBlob.type.includes('mp4') ? 'm4a' : 'webm';
        fileToSend = new File([recordedBlob], `recording.${ext}`, { type: recordedBlob.type });
        displayName = `Recording (${recordedSeconds.toFixed(1)}s)`;
      } else {
        fileToSend = selectedFile;
        displayName = selectedFile.name;
      }
      const data = await analyzeAudio(fileToSend, referenceText);
      onResult(data, displayName);
    } catch (err) {
      setError(err.message || 'Analysis failed. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="card upload-card">
      <h1>Check your pronunciation</h1>
      <p className="lede">
        Record (or upload) 30–45 seconds of English speech. We'll transcribe it, score your
        pronunciation, and point out exactly which words need work.
      </p>

      <div className={`consent-box ${consentGiven ? 'consent-box--checked' : ''}`}>
        <label>
          <input
            type="checkbox"
            checked={consentGiven}
            onChange={(e) => setConsentGiven(e.target.checked)}
          />
          <span>
            I consent to my audio being processed to generate a pronunciation score.
            My audio is analyzed in memory and deleted immediately after — it is not stored,
            shared, or used to train any model.
          </span>
        </label>
      </div>

      <div className="segmented">
        <button
          type="button"
          className={`segmented-btn ${speechMode === 'reference' ? 'segmented-btn--active' : ''}`}
          onClick={() => setSpeechMode('reference')}
        >
          Read a sentence
        </button>
        <button
          type="button"
          className={`segmented-btn ${speechMode === 'free' ? 'segmented-btn--active' : ''}`}
          onClick={() => setSpeechMode('free')}
        >
          Speak freely
        </button>
      </div>

      {speechMode === 'reference' && (
        <div className="sentence-box">
          <div className="sentence-box-header">
            <span className="sentence-box-label">Read this out loud</span>
            <button type="button" className="link-btn" onClick={shuffleSentence}>
              Shuffle ↻
            </button>
          </div>
          <p className="sentence-text">{sentence.text}</p>
          <p className="hint">
            Reading this at a natural pace should land in the {MIN_SECONDS}-{MAX_SECONDS}s window.
            We'll tell you exactly which words matched and which didn't.
          </p>
        </div>
      )}

      <div className="segmented segmented--secondary">
        <button
          type="button"
          className={`segmented-btn ${inputMode === 'record' ? 'segmented-btn--active' : ''}`}
          onClick={() => setInputMode('record')}
        >
          Record
        </button>
        <button
          type="button"
          className={`segmented-btn ${inputMode === 'file' ? 'segmented-btn--active' : ''}`}
          onClick={() => setInputMode('file')}
        >
          Upload a file
        </button>
      </div>

      {inputMode === 'record' && (
        <AudioRecorder
          minSeconds={MIN_SECONDS}
          maxSeconds={MAX_SECONDS}
          disabled={!consentGiven || loading}
          onRecordingChange={(blob, seconds) => {
            setRecordedBlob(blob);
            setRecordedSeconds(seconds);
          }}
        />
      )}

      {inputMode === 'file' && (
        <>
          <div className={`dropzone ${!consentGiven ? 'dropzone--disabled' : ''}`}>
            <input
              ref={inputRef}
              type="file"
              accept="audio/*"
              disabled={!consentGiven}
              onChange={handleFileChange}
              id="audio-input"
            />
            <label htmlFor="audio-input" className="dropzone-label">
              {selectedFile ? (
                <>
                  <strong>{selectedFile.name}</strong>
                  {clientDuration && <span> · {clientDuration.toFixed(1)}s</span>}
                </>
              ) : (
                <>
                  <strong>Choose an audio file</strong>
                  <span>WAV, MP3, M4A, or OGG · 30–45 seconds</span>
                </>
              )}
            </label>
          </div>
          {durationError && <p className="error-text">{durationError}</p>}
        </>
      )}

      {error && <p className="error-text">{error}</p>}

      <button className="primary-btn" disabled={!canSubmit} onClick={handleSubmit}>
        {loading ? 'Analyzing…' : 'Analyze pronunciation'}
      </button>

      {loading && (
        <p className="hint">Transcribing and scoring your recording — this takes a few seconds.</p>
      )}
    </section>
  );
}
