# Deployment requirements

NexusDesk is a practice project and hasn't been deployed anywhere. This page records what a small, single-server deployment would need and what the file-backed database can't do. The backup, restore and export steps below were tested on disposable demo databases on 5 November 2026; nothing here has been tried on a public server.

## Shape of a deployment

```
browser ──HTTPS──> reverse proxy (Caddy or nginx, port 443) ──HTTP──> NexusDesk on 127.0.0.1:8080 ──> data/nexusdesk.mv.db
```

- **One app instance only.** H2 in file mode allows one process to open the database, and sign-in throttling counts are kept in memory. Two instances behind a load balancer won't work.
- **The proxy terminates HTTPS.** The app itself speaks plain HTTP and must stay on `127.0.0.1` (the default `NEXUSDESK_HOST`) so only the proxy can reach it.
- **Start the app with the `prod` profile:** `java -jar nexusdesk-0.2.0.jar --spring.profiles.active=prod`. It:
  - marks the session cookie `Secure`;
  - reads `X-Forwarded-For` / `X-Forwarded-Proto`, but only from a proxy on the same machine (`server.tomcat.remoteip.internal-proxies`), so a visitor can't fake their address to dodge throttling;
  - lets Spring Security send HSTS, because requests are now recognised as HTTPS.
- **The proxy must replace, not append to, `X-Forwarded-For`.** Caddy's `reverse_proxy` does this by default. In nginx use `proxy_set_header X-Forwarded-For $remote_addr;` and `proxy_set_header X-Forwarded-Proto $scheme;`.
- **Java 17 or newer**, run as an unprivileged service account that owns the `data/` folder. Nothing else should be able to read that folder: anyone who can read the `.mv.db` file can read every ticket.

## Secrets and configuration

| Setting | Purpose | Notes |
| --- | --- | --- |
| `NEXUSDESK_ADMIN_EMAIL` / `NEXUSDESK_ADMIN_PASSWORD` | First administrator, created only when the database has no accounts | Remove the password variable after the first start. The public demo password is refused. |
| `NEXUSDESK_DB_URL` | Database location | Default `jdbc:h2:file:./data/nexusdesk;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`. Keep `WRITE_DELAY=0` if you override it. |
| `NEXUSDESK_DB_PASSWORD` | H2 user password | Empty by default. It only matters if someone can already reach the file, so file permissions are the real protection. |
| `NEXUSDESK_HOST`, `PORT` | Bind address and port | Keep the host on loopback behind a proxy. |

Set these in the service manager (systemd unit, Windows service wrapper) or a file readable only by the service account. `.env.example` is a reference list; the app doesn't load it.

Never point the normal profile at a demo database, or the demo profile at a real one. The app refuses both: demo databases are marked in `app_metadata`, and the demo seeder only runs on an empty or already-demo database.

## Backups and restores

H2 keeps everything in one file. Copying it while the app runs could catch a half-written file, so `scripts/db_backup.py` refuses to work while H2 holds its lock.

```powershell
# 1. Stop NexusDesk (Ctrl+C, or stop the service).
python scripts\db_backup.py backup data\nexusdesk                 # -> backups\nexusdesk-YYYYMMDD-HHMMSS.mv.db + .sha256
python scripts\db_backup.py verify backups\nexusdesk-20261105-091500.mv.db
# 2. Start NexusDesk again.

# Restoring (app stopped). The current file is kept as data\nexusdesk.before-restore-<time>.mv.db.
python scripts\db_backup.py restore backups\nexusdesk-20261105-091500.mv.db data\nexusdesk
```

Copy backups off the machine and keep them as private as the database itself. A restore is only proven by starting the app on it and signing in, so try one on a spare machine now and then.

**What was tested:** the smoke test (`scripts/smoke_test.py`) backs up a stopped database, adds a ticket, restores the backup and checks the new ticket has gone while the earlier records remain. A manual run also confirmed the script refuses to copy a database that a running app has open.

### Portable SQL export (for H2 upgrades)

H2's file format can change between major versions, so before upgrading H2 (or moving to another database) export to SQL with the H2 tools inside the app's jar:

```powershell
java -cp target\nexusdesk-0.2.0.jar "-Dloader.main=org.h2.tools.Script" org.springframework.boot.loader.launch.PropertiesLauncher -url jdbc:h2:file:./data/nexusdesk -user sa -script nexusdesk-export.sql
java -cp target\nexusdesk-0.2.0.jar "-Dloader.main=org.h2.tools.RunScript" org.springframework.boot.loader.launch.PropertiesLauncher -url jdbc:h2:file:./data/nexusdesk-new -user sa -script nexusdesk-export.sql
```

Both commands were run against a copy of a 0.1.0 demo database; the imported copy had all its tickets. The export is plain text containing password hashes and every ticket, so treat it like the database.

## Limits of the file-backed database

- One process, one machine. No replication, no hot standby, and backups need a short stop.
- No point-in-time recovery: you can lose everything since the last backup.
- Throttling counts and sessions live in memory, so a restart signs everyone out and resets the counts.
- It suits a handful of users. Before real use I'd move to PostgreSQL (Flyway migrations would need checking for H2-specific SQL such as `MERGE ... KEY`, `DATEADD` and the generated `active_asset_id` column), add proper backups, and review logging and monitoring.

## Not covered

Password reset, email, multi-factor sign-in, audit-log export and centralised logging aren't built. A real deployment would also need a dependency and vulnerability review, a privacy assessment for staff data, and someone responsible for patching.
