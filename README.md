# SkillSwap

A Java skill-barter desk for the Odoo Hackathon problem statement: people list what they can teach, ask for something back, and settle the swap with a rating.

The original Swing screens are replaced by a small **zero-dependency Java HTTP server** so the app can run in a browser and in Docker. No Maven, no extra JARs — `javac` and the JDK `HttpServer` are enough.

## What you can do

**Members**
- Register / sign in (PBKDF2-hashed passwords, secure cookie sessions)
- Profile: name, location, photo URL, bio, availability, public or private
- Skills offered and skills wanted
- Browse and search public desks by name or skill (`Photoshop`, `Excel`, `Java`…)
- Send a swap, accept or reject incoming ones, withdraw a pending outgoing request
- Emails unlock only after a swap is accepted, so you can set a time
- Mark the skill finished, then leave a 1–5 rating and a note

**Admin** (`admin@skillswap.local` / `admin123`)
- Strip a spammy skill from a profile
- Ban / unban members (banning revokes their active sessions instantly)
- Watch pending, accepted, rejected, cancelled, and completed swaps
- Broadcast a desk-wide note
- Download CSV reports (users, swaps, feedback)

## Security

- **Passwords** — PBKDF2-HMAC-SHA256 (120,000 iterations, 16-byte random salt), constant-time comparison on login
- **Sessions** — 256-bit `SecureRandom` tokens, `HttpOnly; SameSite=Lax` cookies (+ `Secure` behind HTTPS), 7-day expiry with automatic purging
- **Brute-force protection** — login/register rate-limited to 15 attempts per 10 minutes per IP
- **CSRF hardening** — all POST/PUT requests must send `Content-Type: application/json`, on top of SameSite cookies
- **Headers** — Content-Security-Policy, `X-Content-Type-Options: nosniff`, Referrer-Policy, Permissions-Policy, and HSTS when behind an HTTPS proxy
- **Input validation** — email format, password length bounds, field length clipping, JSON depth/size limits, path traversal protection, request body and path caps

## Run locally

**Docker**

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080).

The container is hardened: latest Alpine security patches at build time, non-root user, read-only root filesystem, dropped capabilities, `no-new-privileges`, pids/memory limits, and a built-in health check on `/api/health`.

**JDK 17+**

```bash
chmod +x compile.sh run.sh
./run.sh
```

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `SKILLSWAP_STATIC` | `src/main/resources/static` | Static files directory |
| `SKILLSWAP_CORS` | — | Comma-separated extra allowed origins |
| `COOKIE_SECURE` | auto | Force the `Secure` cookie flag |
| `COOKIE_SAMESITE` | `Lax` | `Lax`, `Strict`, or `None` |

## Deploy (Render + Vercel)

This is a Java server. **Render can host it. Vercel cannot run Java.**

Step-by-step: [DEPLOY.md](DEPLOY.md)

- **Render:** New Web Service → Docker → this repo → health check `/api/health`
- **Vercel (optional UI only):** deploy static files and set `SKILLSWAP_API` to your Render URL

## Demo seats

| Who | Email | Password |
| --- | --- | --- |
| Admin | admin@skillswap.local | admin123 |
| Aisha (Photoshop) | aisha@skillswap.local | aisha123 |
| Ravi (Java) | ravi@skillswap.local | ravi123 |
| Meera (Guitar) | meera@skillswap.local | meera123 |
| Kabir (Excel) | kabir@skillswap.local | kabir123 |
| Nora (Photography) | nora@skillswap.local | nora123 |

`Private Patil` has a private profile and does not appear on the public floor.

## Layout

```
src/main/java/com/skillswap/   domain + HTTP server
src/main/resources/static/     editorial UI
Dockerfile                     multi-stage, non-root, health-checked
docker-compose.yml             hardened runtime (read-only, cap_drop, limits)
```

Data is in-memory and reseeded on restart — perfect for a demo, swap in a database for production.
