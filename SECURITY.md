# Security notes

I treat this as a local portfolio application, not a production helpdesk for real staff information.

I use Spring Security session login, CSRF-protected POST forms, HTTP-only SameSite cookies, PBKDF2-HMAC-SHA256 password hashes with 600,000 iterations and per-password salts, escaped templates and bound SQL parameters. Accounts are checked again on authenticated requests, so disabling an account or changing its role invalidates the old session on the next request.

Employee queries are scoped by creator or current equipment owner. Direct URLs get the same checks. I keep the H2 console disabled. The app binds to loopback by default.

Before putting it on the internet I need HTTPS, secure cookies (`NEXUSDESK_SECURE_COOKIE=true`), login rate limiting, password recovery, a backup/restore process and a dependency/security review. Changing the bind address alone is not a deployment plan. I would use a separate production database and test that migration.

The demo profile has public example credentials and its own database file. I never use it for real employee data. The normal profile needs a supplied administrator password on first startup.

I don’t accept credentials or real ticket data in public issues. For a vulnerability, I ask for a private GitHub security report if that feature is enabled, rather than publishing exploit details in the issue tracker.
