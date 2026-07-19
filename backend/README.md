# Backend - Pronunciation Scorer (Spring Boot)

Free/offline speech recognition using [Vosk](https://alphacephei.com/vosk/) - no API key, no billing.

## Prerequisites

1. **Java 17+** and **Maven 3.9+**
2. **ffmpeg** and **ffprobe** on the PATH (used to normalize uploads to 16kHz mono WAV and to measure duration)
   ```bash
   # Debian/Ubuntu
   sudo apt-get install -y ffmpeg
   # macOS
   brew install ffmpeg
   ```
3. **A free Vosk English model** (one-time download, no signup, no key):
   ```bash
   mkdir -p models
   cd models
   curl -LO https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
   unzip vosk-model-small-en-us-0.15.zip
   cd ..
   ```
   The small model (~40MB) is fast and free-tier-friendly. For higher accuracy in
   production, swap in `vosk-model-en-us-0.22` (~1.8GB, still free) by changing
   `VOSK_MODEL_PATH`.

## Run locally

```bash
export VOSK_MODEL_PATH=./models/vosk-model-small-en-us-0.15
export ALLOWED_ORIGINS=http://localhost:5173
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Health check: `GET /api/pronunciation/health`.

## Deploying (free tier)

Any host that lets you run a Docker container works (Render, Railway, Fly.io free
tiers). A `Dockerfile` is included that bundles ffmpeg and downloads the small
Vosk model at build time, so you don't need to commit a 40MB model to git.

```bash
docker build -t pronunciation-scorer .
docker run -p 8080:8080 -e ALLOWED_ORIGINS=https://your-frontend.vercel.app pronunciation-scorer
```

## API

`POST /api/pronunciation/analyze`
- multipart/form-data, field name `audio`
- 30-45 second audio file (wav/mp3/m4a/ogg)
- Returns `AnalysisResponse` JSON: overall score, clarity/fluency sub-scores,
  transcript, and a `words[]` array with per-word status
  (`good` / `unclear` / `mispronounced`) for highlighting in the UI.
