# Recourse frontend

React + TypeScript, built with Vite. Talks to the Spring Boot backend through `/api` (proxied in development).

```
npm install
npm run dev      # http://localhost:5173 (backend must be running on :8080)
npm run build    # type-check and produce dist/
```

Theme tokens live in `src/styles/tokens.css`. The signature component is `src/components/RangeMeter.tsx`:
every belief is drawn as an estimate with an uncertainty band around it.
