# Livo AI — Pronunciation Coach

Record straight from the browser (or upload a file), get a pronunciation
score, and see exactly which words were unclear, mispronounced, or skipped
versus a target sentence.

Stack (matches the JD): **Spring Boot** (backend) + **React** (frontend) +
**free, offline speech recognition** (Vosk) — no paid API keys required
anywhere in this project.

```
livo-pronunciation-app/
├── backend/     Spring Boot API (Java 17) — audio validation, STT, scoring
├── frontend/    React + Vite UI — record/upload, consent, results, highlights
└── ARCHITECTURE.md   full system architecture + design rationale
```

## What's new in v2

Two gaps from review feedback are addressed directly (details in
`ARCHITECTURE.md` §6):

- **Latency**: one ffmpeg process instead of ffmpeg+ffprobe (duration is
  parsed straight from ffmpeg's own log), a larger STT read buffer, and a
  raised upload cap so recordings aren't silently rejected. Per-request
  timing is now logged.
- **"Records nothing the first time"**: the microphone is requested once,
  fully awaited, before the record button is ever enabled, and the
  recorder/stream/chunks live in refs (not stale React state) — the classic
  cause of that bug.
- **Which specific words were mispronounced**: an optional "read aloud"
  mode shows a target sentence and aligns what was actually said against it
  (word-level edit distance), so it can say a word was substituted for a
  different one or skipped entirely — not just "confidence was low."

## Quick start (local)

```bash
# 1. Backend
cd backend
mkdir -p models && cd models
curl -LO https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip && cd ..
export VOSK_MODEL_PATH=./models/vosk-model-small-en-us-0.15
mvn spring-boot:run
# -> http://localhost:8080

# 2. Frontend (new terminal)
cd frontend
npm install
npm run dev
# -> http://localhost:5173
```

Requires `ffmpeg`/`ffprobe` on PATH for the backend (see `backend/README.md`).
Recording in the browser requires HTTPS or `localhost` (a browser
requirement for microphone access).

## Deploying (all free tiers)

- **Backend**: build the included `backend/Dockerfile` (bundles ffmpeg + the
  free Vosk model) and deploy the container to Render, Railway, or Fly.io
  free tier.
- **Frontend**: `npm run build` in `frontend/`, deploy the `dist/` folder to
  Vercel, Netlify, or Cloudflare Pages. Set `VITE_API_BASE_URL` to your
  deployed backend URL, and set `ALLOWED_ORIGINS` on the backend to your
  deployed frontend URL.

## Why this stack (short version)

Vosk replaces SpeechSuper/Azure/Google here because it's free, open-source,
and runs entirely on our own server — which also happens to make the DPDP
story simple (no third-party processor ever touches the audio). Full
reasoning, scoring methodology, and DPDP compliance details are in
[`ARCHITECTURE.md`](./ARCHITECTURE.md).
