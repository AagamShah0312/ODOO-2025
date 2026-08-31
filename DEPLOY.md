# How to run SkillSwap locally, and how to put it on Render / Vercel

This is a **Java** app (JDK `HttpServer` + Docker). It is not a Node.js app.

**Use Render for the real site.** Vercel cannot run a Java server. Vercel can only host the static UI, and even then the Java API still has to live on Render.

---

## 1. Run it locally

### Option A — Docker (easiest)

You need [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```bash
git clone https://github.com/AagamShah0312/ODOO-2025.git
cd ODOO-2025
docker compose up --build
```

Open http://localhost:8080

Stop with `Ctrl+C`, or `docker compose down`.

### Option B — JDK on your machine

1. Install **JDK 17 or newer**  
   - Windows / macOS: [Temurin 21](https://adoptium.net/)  
   - Confirm: `java -version` and `javac -version`
2. From the project folder:

```bash
chmod +x compile.sh run.sh
./run.sh
```

Windows (PowerShell, JDK on `PATH`):

```powershell
mkdir out
Get-ChildItem -Recurse src\main\java\*.java | ForEach-Object { $_.FullName } | Set-Content sources.txt
javac -encoding UTF-8 -d out @sources.txt
$env:SKILLSWAP_STATIC = "$pwd\src\main\resources\static"
$env:PORT = "8080"
java -cp out com.skillswap.SkillSwapApp
```

Open http://localhost:8080

### Demo logins

| Who | Email | Password |
| --- | --- | --- |
| Aisha | aisha@skillswap.local | aisha123 |
| Ravi | ravi@skillswap.local | ravi123 |
| Admin | admin@skillswap.local | admin123 |

---

## 2. Upload to Render (recommended — this actually runs Java)

Render builds the `Dockerfile` and keeps the server alive.

1. Push this repo to GitHub (already done if you use `AagamShah0312/ODOO-2025`).
2. Go to [https://dashboard.render.com](https://dashboard.render.com) and sign in with GitHub.
3. **New + → Web Service →** select the GitHub repo.
4. Settings:
   - **Language / Runtime:** Docker
   - **Root directory:** leave empty
   - **Dockerfile path:** `Dockerfile`
   - **Instance:** Free
   - **Health check path:** `/api/health`
5. Click **Create Web Service**. First build takes a few minutes.
6. Open the `https://something.onrender.com` URL Render gives you.

`render.yaml` is in the repo, so you can also use **New → Blueprint** and point it at the repo.

### Notes

- Free Render apps **sleep after idle**. The first request after that can take ~30–60 seconds.
- Data is **in memory**. A restart wipes users/swaps and reseeds the demo accounts.
- Render sets `PORT` for you. The Java app already reads it.

---

## 3. Upload to Vercel (UI only — Java will not run here)

Vercel hosts static files and serverless Node/Python/Go. **It will not compile or run this Java server.**

Two honest options:

### Option A — Do not use Vercel

Deploy only on Render. That one URL is the full app.

### Option B — Vercel for the pretty URL, Render for the API

1. Deploy the Java app on Render first. Copy the URL, e.g. `https://skillswap-xxxx.onrender.com` (no trailing slash).
2. On Render, add environment variables:
   - `COOKIE_SAMESITE` = `None`
   - `COOKIE_SECURE` = `true`  
   Then **Manual Deploy → Deploy latest commit** so cookies work from another origin.
3. Go to [https://vercel.com](https://vercel.com) → **Add New → Project** → import the same GitHub repo.
4. Vercel project settings:
   - **Framework preset:** Other
   - **Build command:** leave the repo `vercel.json` (it copies `src/main/resources/static` into `public`)
   - **Output directory:** `public`
5. Add environment variable:
   - `SKILLSWAP_API` = `https://skillswap-xxxx.onrender.com`  
     (your real Render URL, no trailing slash)
6. Deploy. The Vercel URL loads the UI; login/API calls go to Render.

If login seems to “work” then bounce you out, the Render cookie settings in step 2 are missing.

---

## What not to do

- Do not set Vercel’s runtime to Node and expect `java` to start.
- Do not point Vercel at `run.sh` or `Dockerfile`. Vercel ignores Docker for typical web projects.
- Railway / Fly.io / Render are the right class of host for this app. Vercel is not.
