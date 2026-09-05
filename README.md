# SkillSwap

SkillSwap is a full-stack Java marketplace where people exchange skills instead of money. Members create a profile, discover people who teach what they want to learn, propose a swap, and leave feedback when it is complete.

Built as an Odoo Hackathon project and designed as a polished, self-contained portfolio demo.

## Highlights

- Browse public skill profiles and search by name or skill
- Register, sign in, and manage a public or private profile
- Send, accept, reject, withdraw, and finish skill-swap requests
- Unlock contact details only after a swap is accepted
- Leave one rating and feedback note per participant
- Admin dashboard for moderation, broadcasts, and CSV reports
- Scheduled maintenance checkpoint with a readiness endpoint at `/api/health`

## Demo accounts

The local demo seeds **3 admins and 6 members**. All demo passwords are intentionally public and must only be used with `SKILLSWAP_DEMO_DATA=true`.

| Role | Name | Email | Password |
| --- | --- | --- | --- |
| Admin | Platform Admin | admin@skillswap.local | admin123 |
| Admin | Anika Bose | anika.admin@skillswap.local | anikaadmin123 |
| Admin | Dev Malhotra | dev.admin@skillswap.local | devadmin123 |
| Member | Aisha Rahman | aisha@skillswap.local | aisha123 |
| Member | Ravi Mehta | ravi@skillswap.local | ravi1234 |
| Member | Meera Iyer | meera@skillswap.local | meera123 |
| Member | Kabir Singh | kabir@skillswap.local | kabir123 |
| Member | Nora D'Souza | nora@skillswap.local | nora123 |
| Member | Private Patil | patil@skillswap.local | patil123 |

The seed data includes pending, accepted, rejected, finished, and completed swaps, plus feedback and ratings. Sign in as any admin to explore moderation and reports.

## Tech

- Java 21, using the JDK `HttpServer`
- Vanilla HTML, CSS, and JavaScript
- Docker and Docker Compose
- PBKDF2 password hashing, server-side sessions, CORS controls, and security headers

## Project structure

```text
src/main/java/com/skillswap/       domain logic and HTTP API
src/main/resources/static/         browser UI
Dockerfile                         production container image
docker-compose.yml                 local demo environment
render.yaml                        Render Blueprint
vercel.json                        Vercel static-UI configuration
```

## Documentation

For local setup, the cron/health checkpoint, and Vercel + Render deployment, see [readme1.md](readme1.md).
