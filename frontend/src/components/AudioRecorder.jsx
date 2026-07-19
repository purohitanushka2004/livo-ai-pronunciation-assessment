import React, { useEffect, useRef, useState } from 'react';

const CANDIDATE_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/ogg;codecs=opus',
  'audio/mp4',
];

function pickMimeType() {
  if (typeof MediaRecorder === 'undefined') return null;
  for (const type of CANDIDATE_MIME_TYPES) {
    if (MediaRecorder.isTypeSupported(type)) return type;
  }
  return '';
}

/**
 * In-browser recorder.
 *
 * The "records nothing the first time, works the second time" bug in the
 * original app is a classic MediaRecorder race: if you call getUserMedia()
 * and create+start() the MediaRecorder inside the same click handler, the
 * mic permission prompt (first use only) suspends that handler while the
 * browser waits on the user, and depending on the browser the recorder can
 * end up started against a not-yet-ready stream, or React state used inside
 * the handler is stale by the time the promise resolves. The second click
 * works because permission is already granted, so everything happens
 * synchronously.
 *
 * The fix: acquire the microphone stream up front (on mount / on demand,
 * fully awaited) and only enable "Start recording" once the stream is
 * actually live. Start/stop then only ever touch refs, never state that
 * could be stale inside an async callback.
 */
export default function AudioRecorder({ minSeconds, maxSeconds, disabled, onRecordingChange }) {
  const [micState, setMicState] = useState('idle'); // idle | requesting | ready | denied
  const [micError, setMicError] = useState(null);
  const [isRecording, setIsRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [recordedUrl, setRecordedUrl] = useState(null);
  const [level, setLevel] = useState(0);

  const streamRef = useRef(null);
  const recorderRef = useRef(null);
  const chunksRef = useRef([]);
  const timerRef = useRef(null);
  const startedAtRef = useRef(0);
  const audioCtxRef = useRef(null);
  const analyserRef = useRef(null);
  const rafRef = useRef(null);
  const mimeTypeRef = useRef('');

  useEffect(() => {
    requestMic();
    return () => teardown();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function requestMic() {
    setMicError(null);
    setMicState('requesting');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 },
      });
      streamRef.current = stream;
      mimeTypeRef.current = pickMimeType();

      try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        const ctx = new AudioCtx();
        const source = ctx.createMediaStreamSource(stream);
        const analyser = ctx.createAnalyser();
        analyser.fftSize = 256;
        source.connect(analyser);
        audioCtxRef.current = ctx;
        analyserRef.current = analyser;
      } catch {
        // Level meter is cosmetic only - recording still works without it.
      }

      setMicState('ready');
    } catch (err) {
      setMicState('denied');
      setMicError(
        err?.name === 'NotAllowedError'
          ? 'Microphone access was denied. Please allow microphone access in your browser and try again.'
          : 'Could not access your microphone. You can still upload an audio file instead.'
      );
    }
  }

  function teardown() {
    stopTimer();
    cancelAnimationFrame(rafRef.current);
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      try { recorderRef.current.stop(); } catch { /* ignore */ }
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    if (audioCtxRef.current) {
      audioCtxRef.current.close().catch(() => {});
      audioCtxRef.current = null;
    }
  }

  function startTimer() {
    startedAtRef.current = performance.now();
    timerRef.current = setInterval(() => {
      const secs = (performance.now() - startedAtRef.current) / 1000;
      setElapsed(secs);
      if (secs >= maxSeconds) {
        stopRecording();
      }
    }, 150);
  }

  function stopTimer() {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }

  function tickLevel() {
    const analyser = analyserRef.current;
    if (!analyser) return;
    const data = new Uint8Array(analyser.frequencyBinCount);
    analyser.getByteFrequencyData(data);
    const avg = data.reduce((a, b) => a + b, 0) / data.length;
    setLevel(Math.min(1, avg / 90));
    rafRef.current = requestAnimationFrame(tickLevel);
  }

  function startRecording() {
    if (!streamRef.current || micState !== 'ready') return;
    if (recordedUrl) {
      URL.revokeObjectURL(recordedUrl);
      setRecordedUrl(null);
      onRecordingChange(null, 0);
    }

    chunksRef.current = [];
    const options = mimeTypeRef.current ? { mimeType: mimeTypeRef.current } : undefined;
    const recorder = new MediaRecorder(streamRef.current, options);

    recorder.ondataavailable = (e) => {
      if (e.data && e.data.size > 0) chunksRef.current.push(e.data);
    };
    recorder.onstop = handleStop;

    recorderRef.current = recorder;
    // A short timeslice guarantees we get data chunks even if stop() is
    // called almost immediately, instead of relying on a single blob that
    // only materializes when the recorder fully finalizes.
    recorder.start(250);
    setIsRecording(true);
    setElapsed(0);
    startTimer();
    tickLevel();
  }

  function stopRecording() {
    stopTimer();
    cancelAnimationFrame(rafRef.current);
    setLevel(0);
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop();
    }
    setIsRecording(false);
  }

  function handleStop() {
    const blob = new Blob(chunksRef.current, { type: recorderRef.current.mimeType || 'audio/webm' });
    const durationSecs = (performance.now() - startedAtRef.current) / 1000;
    const url = URL.createObjectURL(blob);
    setRecordedUrl(url);
    setElapsed(durationSecs);
    onRecordingChange(blob, durationSecs);
  }

  function reRecord() {
    if (recordedUrl) URL.revokeObjectURL(recordedUrl);
    setRecordedUrl(null);
    setElapsed(0);
    onRecordingChange(null, 0);
  }

  const withinWindow = elapsed >= minSeconds && elapsed <= maxSeconds;
  const progressPct = Math.min(100, (elapsed / maxSeconds) * 100);

  return (
    <div className="recorder">
      {micState === 'requesting' && (
        <p className="hint">Requesting microphone access…</p>
      )}

      {micState === 'denied' && <p className="error-text">{micError}</p>}

      {micState === 'ready' && !recordedUrl && (
        <div className="recorder-panel">
          <button
            type="button"
            className={`record-btn ${isRecording ? 'record-btn--active' : ''}`}
            onClick={isRecording ? stopRecording : startRecording}
            disabled={disabled}
            aria-label={isRecording ? 'Stop recording' : 'Start recording'}
          >
            <span className="record-dot" />
          </button>

          <div className="record-status">
            <span className="record-timer">{elapsed.toFixed(1)}s</span>
            <span className="record-window">/ {minSeconds}-{maxSeconds}s target</span>
          </div>

          <div className="record-progress">
            <div
              className={`record-progress-fill ${withinWindow ? 'record-progress-fill--good' : ''}`}
              style={{ width: `${progressPct}%` }}
            />
          </div>

          {isRecording && (
            <div className="level-meter">
              <div className="level-meter-fill" style={{ width: `${level * 100}%` }} />
            </div>
          )}

          <p className="hint">
            {isRecording
              ? 'Recording… tap again to stop.'
              : 'Tap to start recording. Recording auto-stops at ' + maxSeconds + 's.'}
          </p>
        </div>
      )}

      {recordedUrl && (
        <div className="recorder-panel">
          <audio className="record-playback" controls src={recordedUrl} />
          <p className="hint">{elapsed.toFixed(1)}s recorded.</p>
          <button type="button" className="secondary-btn" onClick={reRecord} disabled={disabled}>
            Re-record
          </button>
        </div>
      )}
    </div>
  );
}
