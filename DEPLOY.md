# Deploying TrimDesk to Railway

One Railway project with two services: **trimdesk** (this repo's `Dockerfile`: Spring Boot serving
the API and the built React app on one HTTPS address) and **Postgres**. The database is reachable
only over Railway's private network.

## 1. Code on GitHub

Create an empty **private** repository and push this repo to it (no README or .gitignore in the new
repo).

## 2. Railway project

1. railway.com → **New Project** → **Deploy from GitHub repo** → pick the repository.
   Railway finds `railway.toml` and builds the `Dockerfile`.
2. In the same project: **Create** → **Database** → **PostgreSQL**. Keep its default name `Postgres`
   (the variables below refer to it by that name).
3. Do **not** turn on a public TCP proxy for Postgres. The app reaches it privately.

## 3. App variables

Open the app service → **Variables** → **Raw Editor**, paste, then fill in `JWT_SECRET`:

```
SPRING_PROFILES_ACTIVE=prod,seed
GROOMDESK_SEED_END_DATE=2026-11-21
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}?reWriteBatchedInserts=true
DB_USER=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=
```

Generate the secret on your machine and paste it in (never commit it):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { '{0:x2}' -f $_ })
```

The app refuses to start in `prod` with the built-in development secret.

- `seed` fills the empty database once with 90 days of demo data ending on
  `GROOMDESK_SEED_END_DATE`. Set it to the **day you record**: the chart then covers the 90 full days
  before it, and the live "Today" card shows a busy day instead of zero (days after the deploy stay
  hidden until they arrive). The seed always gives demo customer 99105 51967 (Ajith Raj) history at
  three other outlets. It skips itself when data already
  exists, so leaving it on is harmless. To re-seed later, delete and re-create the Postgres service.
- `CORS_ORIGINS` is not needed: the frontend and API share one origin.

## 4. Public address

App service → **Settings** → **Networking** → **Generate Domain**. Only the app gets a domain.

## 5. Spending cap (do this before sharing the link)

Workspace → **Usage** → set an **email alert at $5** and a **hard limit at $10** (the minimum).
At the hard limit Railway takes the services offline instead of billing more, so a traffic attack
can cost at most $10.

## 6. Smoke test

Open the domain in a private window:

- [ ] Login page shows **TrimDesk**; each of the four demo roles signs in
- [ ] Cashier: bill demo customer 99105 51967 with 1, 2, F3; receipt prints
- [ ] HQ dashboard: chart runs up to the seed end date; royalty page loads and exports CSV
- [ ] A deep link such as `/royalty` opens directly (not a 404) after signing in
- [ ] Phone: dashboard and royalty pages fit

## Notes

- Demo users and the `demo123` password are printed on the login screen. Use this deployment for
  demo data only, never real customers.
- Logs: app service → **Deployments** → **View logs**. The first boot runs Flyway and the seed
  (about a minute), then serves on Railway's `PORT`.
