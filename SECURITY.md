# Security notes

This is a local learning project, not a production helpdesk for real staff information.

The app uses Spring Security session login, CSRF-protected POST forms, HTTP-only SameSite cookies, salted PBKDF2 password hashes, escaped templates and bound SQL parameters. Accounts are checked again on authenticated requests, so disabling an account or changing its role invalidates the old session on the next request.

**Password hashing is weaker than these notes first claimed.** They said PBKDF2-HMAC-SHA256 with 600,000 iterations. A review on 2 October found that `SecurityConfig` uses Spring's deprecated `Pbkdf2PasswordEncoder(String, int, int, int)` constructor, which defaults to HMAC-SHA1. OWASP recommends more iterations for HMAC-SHA1 than for SHA-256, so 600,000 is below its guidance. The fix is to pass the SHA-256 algorithm explicitly (or switch to Spring's delegating encoder) and handle hashes already stored in local databases. I haven't made that change yet.

Employee queries are scoped by creator or current equipment owner. Direct URLs get the same checks. The H2 console is disabled. The app binds to loopback by default.

Before putting it on the internet I need HTTPS, secure cookies (`NEXUSDESK_SECURE_COOKIE=true`), login rate limiting, password recovery, a backup/restore process and a dependency/security review. Changing the bind address alone is not a deployment plan. I would use a separate production database and test that migration.

The demo profile has public example credentials and its own database file. Don't use it for real employee data. The normal profile needs a supplied administrator password on first startup.

I don’t accept credentials or real ticket data in public issues. For a vulnerability, I ask for a private GitHub security report if that feature is enabled, rather than publishing exploit details in the issue tracker.
