# SkillSwap

A Java skill-barter desk for the Odoo Hackathon problem statement: people list what they can teach, ask for something back, and settle the swap with a rating.

The original Swing screens are replaced by a small **zero-dependency Java HTTP server** so the app can run in a browser and in Docker.

## What you can do

**Members**
- Register / sign in (PBKDF2 password hashing, cookie sessions)
- Profile: name, location, photo URL, bio, availability, public or private
- Skills offered and skills wanted
- Browse and search public desks by name or skill (`Photoshop`, `Excel`, `Java`…)
- Send a swap, accept or reject incoming ones, withdraw a pending outgoing request
- Leave a 1–5 rating and a note after a swap is accepted

**Admin** (configured through deployment environment variables)
- Strip a spammy skill from a profile
- Ban / unban members
- Watch pending, accepted, rejected, cancelled, and completed swaps
- Broadcast a desk-wide note
- Download CSV reports (users, swaps, feedback)

## Run locally

**Docker**

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080).

Docker Compose enables `SKILLSWAP_DEMO_DATA=true` for local sample accounts only.
Never enable demo data on a public deployment.

**JDK 17+**

```bash
chmod +x compile.sh run.sh
./run.sh
```

## Deploy (Render + Vercel)

This is a Java server. **Render can host it. Vercel cannot run Java.**

Step-by-step: [DEPLOY.md](DEPLOY.md)

- **Render:** New Web Service → Docker → this repo → health check `/api/health`
- **Vercel (optional UI only):** deploy static files and set `SKILLSWAP_API` to your Render URL

## Demo seats

Available only with `SKILLSWAP_DEMO_DATA=true`.

| Who | Email | Password |
| --- | --- | --- |
| Admin | admin@skillswap.local | admin123 |
| Aisha (Photoshop) | aisha@skillswap.local | aisha123 |
| Ravi (Java) | ravi@skillswap.local | ravi1234 |
| Meera (Guitar) | meera@skillswap.local | meera123 |
| Kabir (Excel) | kabir@skillswap.local | kabir123 |
| Nora (Photography) | nora@skillswap.local | nora123 |

`Private Patil` has a private profile and does not appear on the public floor.

## Layout

```
src/main/java/com/skillswap/   domain + HTTP server
src/main/resources/static/     editorial UI
Dockerfile
docker-compose.yml
```

No Maven, no extra JARs — `javac` and the JDK `HttpServer` are enough.
