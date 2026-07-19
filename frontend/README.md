# Frontend - Pronunciation Coach (React + Vite)

## Run locally

```bash
npm install
npm run dev
```

Opens on `http://localhost:5173`. In dev, `/api` calls are proxied to
`http://localhost:8080` (the Spring Boot backend) via `vite.config.js`.

## Environment variables

- `VITE_API_BASE_URL` - base URL of the deployed backend, e.g.
  `https://livo-pronunciation-api.onrender.com`. Leave unset for local dev
  (uses the Vite proxy instead).

## Build

```bash
npm run build
```

Outputs static files to `dist/` - deployable as-is to Vercel, Netlify,
Cloudflare Pages, etc. (all free tiers).
