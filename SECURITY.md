# Security notes

NexusDesk is a practice project, not a production helpdesk. Don't put real staff data in it. These notes describe what version 0.2.2 does and what it doesn't.

## Sign-in and passwords

- **Hashing.** New and changed passwords use PBKDF2-HMAC-SHA256 with 600,000 iterations, a 16-byte random salt and a 256-bit output, via Spring Security's non-deprecated `Pbkdf2PasswordEncoder` constructor. Each stored hash starts with its algorithm, e.g. `{pbkdf2-sha256}…` (`config/PasswordHashing.java`).
- **What 0.1.0 got wrong.** Version 0.1.0 used the deprecated `Pbkdf2PasswordEncoder(String, int, int, int)` constructor, which silently chose HMAC-SHA1 (with a 256-bit output). The docs at the time wrongly said SHA-256.
- **Migration without resets.** Migration `V3` tags existing hashes as `{pbkdf2-sha1}`. `LegacySha1PasswordVerifier` checks them by repeating the old calculation with the JDK, and after a successful sign-in Spring Security replaces the hash with SHA-256 (`AccountDetailsService.updatePassword`). Accounts that never sign in again keep their old hash; an administrator can't reset passwords in this version. Hashes with no tag are rejected rather than guessed. This was tested on a copy of a real 0.1.0 demo database.
- **Throttling.** Five failed sign-ins for one email from one address, or twenty from one address across any emails, block further attempts from there for 15 minutes. Blocked attempts never reach the password check, and attempts still being checked count towards the limit, so firing many at once doesn't help. The check sits inside the authentication provider (`ThrottledAuthenticationProvider`), so it covers every route to a password check. Counts are in memory: they reset on restart and aren't shared between instances. A determined attacker with many addresses is slowed, not stopped.
- **Same message for every failure.** A wrong email, wrong password or disabled account all show "The email or password wasn't recognised".

## Sessions and requests

- Session cookies are `HttpOnly`, `SameSite=Lax`, sent only as cookies (never in URLs), and `Secure` under the `prod` and `hosted` profiles. Sessions time out after 30 minutes (60 minutes in the hosted demo).
- Spring Security issues a new session ID at sign-in, so a planted session ID is useless.
- Every POST form carries a CSRF token, including sign-in and sign-out.
- On every authenticated request `AccountStatusFilter` re-reads the account. A disabled account, or one whose role changed, is signed out on its next request.
- Responses carry a Content Security Policy that only allows the app's own scripts, styles and images, plus `frame-ancestors 'none'` and a same-origin referrer policy.

## Permissions

| Role | Can |
| --- | --- |
| Employee | Raise tickets, read and comment on their own tickets, see equipment assigned to them |
| Technician | Work on every ticket, see everyone's tickets and equipment, use the dashboard |
| Administrator | Technician access plus accounts and equipment management |

Rules are enforced on the server, not just by hiding buttons: `/users/**` needs the administrator role in `SecurityConfig`, and every service method that makes a staff or administrator change checks the role again (`Rules.staff`, `Rules.admin`). Employee queries are always limited to their own records, whatever filters are sent; another person's ticket or asset returns the same "not found" page as one that doesn't exist. `WebFlowTest` and `DemoProfileTest` check these rules over HTTP.

## Data handling

- All SQL uses bound parameters. The only SQL built from strings uses fixed column and table names.
- Templates escape user content (`th:text`); tests check that a `<script>` title is shown as text.
- Error pages never include stack traces, exception names or SQL. Database failures are logged on the server with their cause.
- Form errors never send a submitted password back to the browser.

## Demo profile

The demo profile uses a public password (`Demo-password-123`) shown on its sign-in page. It only runs against its own file (`data/nexusdesk-demo.mv.db`), and it refuses to add demo accounts to a database that already has real ones. The normal profile refuses to start on a demo database and rejects the demo password as its first administrator password, so the public credentials can't become a fallback.

## Public browser demo

The `hosted` profile (see [docs/hosted-demo.md](docs/hosted-demo.md)) gives each visitor a private in-memory copy of the fictional data, so no visitor can see, change or lock anything belonging to another. Demo access uses one-click role buttons rather than a shared password. Password sign-in attempts are throttled per visitor sandbox, because every visitor reaches the app through the same hosting proxy. Sandboxes are capped in number, size and creation rate, and expire after an hour idle. The app has no email or other outside service, so demo actions can't send messages.

## Known gaps

No password reset, no multi-factor sign-in, no account lockout notifications, no audit log export, and no automated dependency scanning. HTTPS depends on a reverse proxy (see [deployment](docs/deployment.md)).

## Reporting a problem

Please don't put credentials, real ticket data or exploit details in a public issue. Use a private GitHub security advisory for the repository if one is available.
