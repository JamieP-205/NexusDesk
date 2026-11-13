# Changelog

## Unreleased

- Motion: buttons dip slightly when pressed, messages and ticket rows ease in (rows 30 ms apart), success messages give one soft pulse, and opened sections ease their contents in. All of it is off when the device asks for reduced motion.

- Hosted demo: the Render image now unpacks the jar and records a class-data-sharing archive at build time, so a cold start loads Spring's classes from the archive instead of parsing them again. Measured on a simulated 0.1 CPU, the time to the first sign-in page fell from about 90 to 105 seconds to about 48 to 50 seconds. Behaviour is unchanged.

## 0.2.2 — 5 November 2026

Made by Claude (Anthropic's AI assistant) in Claude Code at Jamie's request, after Jamie found the roles hard to tell apart: the demo gave no hint of what each account could do, and the main actions were in a form at the bottom of each ticket.

**Added**
- Quick actions on a ticket. Technicians get **Assign to me** and **Resolve this ticket**; the person who raised a Resolved ticket gets **Yes, it's fixed** (closes it) or **No, it's still not working** (reopens it with their reason as a comment). They follow the same rules, history and conflict check as the full Update ticket form.
- A **Things to try** checklist for each role in the local and browser demos, ticking off as you go, plus a suggested journey across roles. The normal app doesn't show it.
- The sign-in page now says what each role can do.

**Browser demo**
- The browser demo now runs on Render's free plan (`render.yaml`); Hugging Face Docker Spaces need a paid plan.
- Fixed: in the browser demo, opening a page you weren't allowed to see, or one that didn't exist, signed you out and deleted your copy of the data. Error pages are rendered in a second pass that didn't see the visitor's sandbox.
- The browser demo hashes the published demo password once when preparing its data, instead of six times an hour.

## 0.2.1 — 5 November 2026

Made by Claude (Anthropic's AI assistant) in Claude Code at Jamie's request.

**Fixed**
- The 0.2.0 Windows launcher failed unless run as administrator. The cause was Smart App Control blocking `.cmd` files downloaded from the internet, not antivirus (see [Windows download](docs/windows-download.md)). The demo now starts with `java -jar nexusdesk-0.2.1.jar --demo`, with no script needed; the `.cmd` remains for PCs without Smart App Control.
- Demo data moved from the download folder to `%LOCALAPPDATA%\NexusDesk\demo`, so the download can live anywhere.
- H2 no longer writes a trace file that grew by megabytes a minute.
- The release ZIP uses forward-slash paths and CRLF line endings, and is built the same way every time (`scripts/package_release.py`).

**Added**
- A `hosted` profile for a public browser demo: a private in-memory sandbox per visitor, one-click role buttons, role switching, reset, expiry, and limits on sandboxes, rows and sign-up rate (see [Browser demo](docs/hosted-demo.md)).
- `--demo` picks the next free port if 8080 is busy and opens the browser.
- 11 more tests (83 in total).

## 0.2.0 — 5 November 2026

Made by Claude (Anthropic's AI assistant) in Claude Code at Jamie's request. Commits carry a `Co-Authored-By: Claude` trailer.

**Fixed**
- Choosing a technician while leaving a ticket Open silently dropped the technician and still said "Ticket updated". The rule is now explicit (Open has no technician; every other status has one), the server explains a mismatch beside the field and keeps the selection, and the form keeps the two in step.
- Passwords were hashed with PBKDF2-HMAC-SHA1 through a deprecated constructor. New hashes use PBKDF2-HMAC-SHA256; migration V3 tags old hashes, which still work and are upgraded at the next sign-in.
- Resolution notes typed on an unfinished ticket were silently discarded; they're now refused with an explanation.
- "Ticket updated" / "Asset updated" appeared even when nothing changed. Saves now say what changed, or that nothing did.
- After an edit conflict, re-saving could undo the other person's change. Only the fields you changed are carried over.
- On Windows, a forced stop could lose the last half-second of saved work (H2 write delay). Commits now reach disk immediately.
- `spring-boot:run` limited the JIT compiler, making each sign-in take about two seconds.

**Added**
- Sign-in throttling per email+address and per address.
- Ticket list pagination (20 per page) that keeps search and filters.
- Field-level validation messages, an error summary, and kept input on every form (never the password).
- Clear empty states, a skip link, and better labels for roles and statuses.
- A separated demo profile with 26 fictional tickets, `demo.cmd` for Windows, and guards so demo and real databases can't be mixed.
- A `prod` profile for running behind an HTTPS proxy, `scripts/db_backup.py`, and [deployment notes](docs/deployment.md).
- 49 more tests (72 in total), a smoke test covering throttling, paging, restart and backup/restore, and a [walkthrough](docs/walkthrough.md).

## 0.1.0 — 2 October 2026

First working version, built from my brief with substantial help from Codex:

- Login and employee, technician and administrator access.
- Tickets, status transitions, assignment, comments and permanent history.
- Search by title plus status, priority, category, person, assignee and date filters.
- Equipment records, current ownership and assignment history.
- Account creation, editing and disabling.
- Dashboard totals and category/priority charts.
- A separate demo profile and example records.
- Database, permission and rendered-page tests.

During the build, Codex fixed H2 generated-key handling, kept resolution notes in the audit trail when a ticket is reopened, and added stale-edit checks for tickets and equipment. The implementation started on 1 October and the packaged-app checks and documentation were finished on 2 October.
