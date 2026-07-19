# System Architecture — Livo AI Pronunciation Coach

> **v2 update:** this revision directly targets the two gaps called out in
> review feedback: **response latency** and **correctly identifying which
> specific words were mispronounced**. See §6 for what changed and why.

## 1. Components

```
 Browser (React + Vite)
   │  1. Mic pre-armed on page load (permission requested once, up front)
   │  2. Record in-browser (MediaRecorder) or upload a file
   │  3. Optional: read a supplied practice sentence (reference-text mode)
   │  POST /api/pronunciation/analyze  (multipart/form-data: audio, referenceText?)
   ▼
 Spring Boot API (Java 17)
   │  AudioProcessingService → ffmpeg: normalize to 16kHz mono WAV,
   │                           duration parsed from ffmpeg's own log (one
   │                           process instead of two)
   │  SpeechRecognitionService → Vosk offline STT: transcript + per-word
   │                             timestamps + confidence
   │  PronunciationScoringService → confidence heuristic (free speech) OR
   │                                word-alignment against the reference
   │                                sentence (read-aloud mode, see §3)
   │  temp files deleted in a `finally` block, always
   ▼
 JSON response → React renders score gauges + word-by-word highlights
```

Frontend and backend are two independently deployable apps talking over
HTTPS/CORS — no shared session state, no server-rendered pages.

## 2. Models & APIs used, and why

| Need | Choice | Why over the alternative |
|---|---|---|
| Speech-to-text w/ word timing & confidence | **Vosk** (small English model, `vosk-model-small-en-us-0.15`) | Free, Apache-2.0, runs fully offline/on our own server — no API key, no per-request billing, no audio leaving our infrastructure to a third party. The brief explicitly ruled out paid keys, which rules out SpeechSuper/Azure Pronunciation Assessment/Google Cloud Speech for this deliverable. |
| Audio normalization | **ffmpeg** | Free, industry-standard, handles arbitrary input codecs/containers (including in-browser webm/opus recordings) and, as of v2, gives us duration for free from its own log — see §6. |
| Backend framework | **Spring Boot 3** | Matches the JD directly; mature multipart handling, easy to containerize and deploy to any free host. |
| Frontend | **React + Vite** | Matches the JD; Vite gives fast dev/build with zero-config static output deployable anywhere. |

## 3. How pronunciation is scored, and what gets highlighted

There are now two scoring modes, both free of any paid phoneme-level API:

**Free-speech mode** (no reference text) — unchanged from v1: recognizer
confidence is used as a proxy for clarity.
- `confidence ≥ 0.80` → **good**
- `0.55 ≤ confidence < 0.80` → **unclear**
- `confidence < 0.55` → **mispronounced**

**Read-aloud mode** (a practice sentence is supplied) — new in v2, and the
direct answer to "correctly identify which specific words were
mispronounced": confidence alone can tell you a word was *unclear*, but not
that it was *wrong*. Instead:
1. The reference sentence and the recognized transcript are both tokenized
   to plain lowercase words.
2. A word-level Wagner–Fischer edit-distance alignment (the same algorithm
   family behind `diff`) is run between the two sequences.
3. The backtrace classifies every expected word as:
   - **match** — said, and status further refined by confidence (good/unclear)
   - **substitution** — a *different* word was recognized here — this is a
     real, comparative mispronunciation signal, not just "recognizer wasn't
     sure"
   - **omission** — the expected word was never said at all (skipped)
   - and any leftover recognized words the speaker added are flagged as
     **insertions** ("extra word")
4. `clarityScore` is the average per-reference-word correctness (match+confident
   = 100, match+unclear = 70/40, substitution = 20, omission = 0).

**Fluency** (both modes) combines speaking rate (ideal band: 110–170 wpm)
and a penalty for unnaturally long gaps between words (> 0.8s).

**Overall score** = `0.7 × clarity + 0.3 × fluency`, 0–100, in both modes.

The frontend renders the target sentence (read-aloud mode) or transcript
(free mode) inline, color-coded per word, with a tap-to-expand note per word
— including, for substitutions, exactly what was expected vs. what was heard.

**Honest limitation:** alignment tells us a *different word* than expected
was heard, which is a strong mispronunciation signal, but it is still
word-level, not phoneme-level — it can't yet say "you swapped a 'v' for a
'w'". A forced-aligner/phoneme comparison (CMUdict or a G2P model) is the
natural next step, noted in §5.

## 4. DPDP Compliance (Digital Personal Data Protection Act, 2023)

Voice recordings are personal data under DPDP. This build treats them as
sensitive and minimizes retention rather than trying to bolt on compliance
after the fact:

- **Consent**: the frontend shows an explicit consent notice before either
  the microphone or the file picker is enabled; checking it is required
  before recording or uploading. Submitting is the consent action (Section
  6, DPDP).
- **Storage**: the API is stateless. Uploaded audio is written to a
  request-scoped OS temp file, converted, scored, and the temp files are
  deleted in a `finally` block immediately after the response is built —
  in the same request, regardless of success or failure. No database, no
  object storage, no logging of audio bytes or transcripts.
- **Retention**: effectively zero. Nothing outlives the HTTP request that
  created it. On the client, the in-browser recording lives only in memory
  (a Blob URL) for the duration of the session and is revoked on re-record.
- **Data residency**: the backend is a single container with no external
  STT API call (Vosk runs in-process), so audio never crosses a border to
  a third-party processor. Deploying the container to an India-region host
  (e.g., a Mumbai/Bangalore region on Render/Railway/Fly.io) keeps
  processing entirely within India, satisfying data-residency-conscious
  deployments without any code change.
- **Deletion**: there is nothing to delete after the response returns —
  by design, rather than by a deletion request workflow.
- **Purpose limitation**: audio is used only to produce the pronunciation
  score in that request; it is never used to train or fine-tune any model.

## 5. Trade-offs, and what's next with another week

**Trade-offs made for this scope:**
- Word-level alignment instead of true phoneme alignment for read-aloud
  mode (fast to build, free, catches whole-word substitutions/omissions,
  but can't localize *which sound* within a correctly-matched word was off).
- Small Vosk model (~40MB) for fast cold-starts on free hosting tiers,
  trading some transcription accuracy for deploy simplicity.
- No user accounts/history — every analysis is a one-off, which keeps the
  DPDP story simple but means no progress tracking.

**With another week:**
1. Real phoneme-level scoring: align recognized phonemes (forced-aligner or
   `whisper` + G2P) against expected pronunciation (CMUdict), including
   inside matched words, not just at the word-substitution level.
2. Swap in the larger Vosk model (or on-prem Whisper) behind a feature flag
   for accuracy-sensitive deployments.
3. Stream partial transcription results to the client over SSE/WebSocket so
   the UI can show progress during transcription rather than a single
   "analyzing…" spinner — perceived latency, not just actual latency.
4. Optional accounts with a real deletion/export endpoint if history
   tracking is wanted, with explicit per-DPDP data-principal rights.
5. Rate limiting and true file-type sniffing (not just extension) at the
   API boundary for hardening.

## 6. What changed in v2, and why (latency + mispronunciation feedback)

**Response latency:**
- **One ffmpeg call instead of ffmpeg + ffprobe.** ffmpeg already prints the
  source duration in its own log; `AudioProcessingService` now parses that
  directly (`Duration: HH:MM:SS.xx`) instead of spawning a second `ffprobe`
  process just to ask the same question. For a 30-45s clip, process-spawn
  overhead is a meaningful fraction of total request time, so removing an
  entire subprocess is a direct, measurable win. `ffprobe` is kept only as
  a fallback if the log parse ever fails.
- **Bigger streaming read buffer** in `SpeechRecognitionService` (4KB → 16KB)
  means far fewer native `acceptWaveForm()` calls per clip, cutting
  JNI/syscall overhead in the hottest loop of the request.
- **Per-request timing logs** (`analyze() timing ms: convert=... transcribe=...
  total=...`) were added to the controller so latency regressions are
  visible in production logs instead of only being noticed by users.
- **Upload size cap raised 8MB → 20MB.** In-browser webm/opus recordings are
  small, but 8MB was uncomfortably close to a worst-case 45s 16-bit WAV and
  was a plausible source of the "sometimes silently fails" reports.

**Recording bug ("first time doesn't record, second time does"):**
This is a classic MediaRecorder race: requesting `getUserMedia()` and
creating/starting a `MediaRecorder` inside the same click handler means the
first-ever permission prompt suspends that handler mid-flight, and any React
state read inside the async continuation can be stale by the time the
promise resolves — so the first click can start recording against a
not-yet-ready stream (or with an `ondataavailable` handler that isn't wired
up yet), while the second click, with permission already granted, runs
synchronously and works. `AudioRecorder.jsx` fixes this by:
- requesting the microphone **once, up front, fully awaited**, before the
  record button is ever enabled;
- keeping the live `MediaStream`/`MediaRecorder`/chunks in **refs**, never
  in state read from inside an async callback, so there's no stale-closure
  window;
- starting with `recorder.start(250)` (a timeslice) so data is flushed
  periodically instead of depending on a single blob that only exists once
  the recorder fully finalizes.

**Identifying which specific words were mispronounced:**
Added an optional **read-aloud mode**: the user is shown a target sentence
and reads it back. The backend aligns the recognized words against that
script (word-level edit distance, §3) so it can say, per word, whether it
was *matched*, *substituted for a different word*, or *skipped* — a direct,
comparative mispronunciation signal, rather than inferring "hard to
understand" purely from recognizer confidence. Free-speech mode (no
script) is kept as a fallback for users who'd rather talk naturally.

