# SkillSwap: local setup and deployment guide

SkillSwap is a Java server. Render hosts the API; Vercel is optional and hosts only the static browser UI.

## Run locally

### Docker Compose (recommended)

Install Docker Desktop, clone the repository, and run:

```bash
docker compose up --build
```

Open <http://localhost:8080>. Stop it with `Ctrl+C` or:

```bash
docker compose down
```

The Compose configuration sets `SKILLSWAP_DEMO_DATA=true`, which loads the 3-admin, 6-member demo dataset. The credentials are in [README.md](README.md).

### JDK 21

Install JDK 21 and confirm both `java -version` and `javac -version` work. Then:

```bash
chmod +x compile.sh run.sh
./compile.sh
SKILLSWAP_STATIC=src/main/resources/static SKILLSWAP_DEMO_DATA=true ./run.sh
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force out
Get-ChildItem -Recurse src\main\java -Filter *.java | ForEach-Object { $_.FullName } | Set-Content sources.txt
javac -encoding UTF-8 -d out @sources.txt
$env:SKILLSWAP_STATIC = "$pwd\src\main\resources\static"
$env:SKILLSWAP_DEMO_DATA = "true"
$env:PORT = "8080"
java -cp out com.skillswap.SkillSwapApp
```

## Cron job and health checkpoint

The application includes a lightweight internal scheduled job. It runs every minute to remove expired sessions and records its last successful run. The readiness endpoint is:

```text
GET /api/health
```

It returns HTTP 200 only after the maintenance checkpoint succeeds. Docker and Render both use this endpoint for their health checks. Use it for an external cron monitor or uptime service if you want notifications:

```bash
curl https://YOUR-RENDER-SERVICE.onrender.com/api/health
```

## Deploy the Java app to Render

1. Push this repository to GitHub.
2. In [Render](https://dashboard.render.com), select **New +** → **Blueprint** and choose the repository. Render reads `render.yaml`.
3. Set the required secret environment variables:

   - `SKILLSWAP_ADMIN_EMAIL`
   - `SKILLSWAP_ADMIN_PASSWORD` — use a long, unique password
   - `SKILLSWAP_ADMIN_NAME` — optional

4. Deploy. Render builds the Docker image and checks `/api/health`.
5. Open the Render URL when the deploy is live.

Do not set `SKILLSWAP_DEMO_DATA=true` on a public service. The demo credentials are deliberately public. This project currently uses in-memory data, so a restart clears changes and recreates only the configured production admin.

## Deploy the UI to Vercel

Vercel does not run this Java server. Deploy the API to Render first.

1. In [Vercel](https://vercel.com), choose **Add New** → **Project** and import this GitHub repository.
2. Select framework preset **Other**. Keep the repository `vercel.json`; it copies the static UI into `public`.
3. Add the Vercel environment variable:

   - `SKILLSWAP_API` = `https://YOUR-RENDER-SERVICE.onrender.com`

4. Deploy and copy the Vercel production URL.
5. In Render, add these environment variables and redeploy:

   - `COOKIE_SAMESITE` = `None`
   - `COOKIE_SECURE` = `true`
   - `SKILLSWAP_CORS` = your exact Vercel URL, for example `https://skillswap.vercel.app` (no trailing slash)

The Vercel URL now serves the UI while requests and sign-in sessions go to Render.
