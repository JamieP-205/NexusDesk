# How NexusDesk works: a plain-English walkthrough

This guide explains the moving parts of version 0.2.0 so you can follow the code yourself. It was written by Claude (Anthropic's AI assistant) alongside the 0.2.0 changes. File paths are under `src/main/java/dev/jamie/nexusdesk/` unless they say otherwise.

## 1. What happens when you click "Save changes" on a ticket

1. **The browser sends a form.** `ticket-detail.html` posts `status`, `priority`, `assignee`, `resolution`, a hidden `version` number, and a hidden CSRF token to `/tickets/{id}`.
2. **Spring Security runs first.** Its filters check you're signed in, check the CSRF token, and then `config/AccountStatusFilter` re-reads your account from the database. If an administrator disabled you or changed your role a minute ago, you're signed out here, before any ticket code runs.
3. **The controller translates HTTP into a method call.** `web/TicketController.update` reads the form fields and calls `TicketService.update(...)`. Controllers do no business logic: they only decide which page to show afterwards.
4. **The service applies the rules and talks to the database.** `service/TicketService.update` checks your role, locks the ticket row, compares versions, validates every field, writes the change and writes the history rows (details in sections 3 and 4).
5. **The controller picks the response.**
   - Success: it stores a one-off "flash" message ("Saved: status Open → Assigned …") and redirects to the ticket page. Redirecting after a POST means refreshing the page can't submit the form twice.
   - A field problem (`ValidationProblem`): it re-renders the same page with status 400, the messages beside the fields, and your choices still selected.
   - Someone else saved first (`Problem` with status 409): it shows their version and keeps only the fields *you* changed (it knows what you started from because the form also sent hidden `seenStatus` / `seenPriority` / `seenAssignee` values).
6. **Thymeleaf builds the HTML.** Templates in `src/main/resources/templates/` use `th:text`, which escapes anything a user typed, so a title like `<script>` shows as text instead of running.

Every other page follows the same path: security filters → controller → service → SQL → template.

## 2. Who can do what, and where that's enforced

There are three roles: **employee**, **technician** and **administrator** (see the table in `SECURITY.md`). The important idea is that hiding a button isn't protection: anyone can type a URL or send a form by hand. So each rule is checked on the server, often twice:

- **URL level:** `config/SecurityConfig` says `/users/**` needs `ROLE_ADMIN` and everything except the sign-in page needs a signed-in user.
- **Service level:** `service/Rules.staff(actor)` and `Rules.admin(actor)` throw a 403 problem at the top of methods such as `TicketService.update` and `AssetService.assign`.
- **Data level:** queries for employees always add `creator_id = <you>` (tickets) or "current owner is you" (equipment). If you ask for someone else's ticket you get the same "not found" page as for a ticket that doesn't exist, so you can't even learn that it exists.

## 3. Database transactions and conflicting edits

A **transaction** groups several SQL statements so they either all happen or none do. Methods marked `@Transactional` run inside one.

`TicketService.update` changes the `tickets` row *and* inserts one or more `ticket_history` rows. If the history insert failed after the ticket row had changed, you'd have a change with no record of who made it. Because both are in one transaction, a failure rolls the ticket change back too. `DataIntegrityTest.aFailedHistoryWriteRollsBackTheTicketChange` proves this by making the history insert fail on purpose.

Two kinds of protection stop two people overwriting each other:

- **Row locks (`SELECT ... FOR UPDATE`).** The first thing `update`, `comment`, `assign` and `returnAsset` do is lock the row they're about to change. A second request for the same row waits until the first transaction finishes.
- **Version numbers (optimistic locking).** Every ticket and asset has a `version` column that goes up by one on every save. The form remembers the version you loaded. If it no longer matches, someone else saved in between, and nothing is written. The `UPDATE ... WHERE id=? AND version=?` statement checks again as a final guard.

For equipment there's a third layer in the schema: `asset_assignments.active_asset_id` is a generated column that holds the asset ID only while an assignment is current (no `returned_at`), and it has a `UNIQUE` constraint. So even a bug in the Java code couldn't give one laptop two current owners. Returning equipment sets `returned_at` instead of deleting the row, which is why the history survives.

H2 is told `WRITE_DELAY=0`, so each committed transaction reaches the disk straight away. Without it, H2 waits up to half a second, and a crash or forced stop could lose work that the user had been told was saved.

## 4. Ticket states and the assignment rule

```
Open → Assigned → In Progress ⇄ Waiting
          ↓            ↓           ↓
          └────────→ Resolved → Closed
                         ↓         ↓
                         └──→ Open ←┘   (reopen)
```

The allowed moves live in `service/TicketFlow`. The rule that ties status and technician together is:

> **An Open ticket never has a technician. Every other status always has one.**

Before 0.2.0, choosing a technician while leaving a ticket Open silently threw the technician away and still said "Ticket updated". Now:

- **Server:** `TicketService.update` rejects the mismatch with a message beside the Technician field ("An Open ticket has no technician. To give it to Morgan Reid, change the status to Assigned."). Nothing is saved, and your selection stays on screen.
- **Browser:** `static/app.js` keeps the form consistent as you go: picking a technician on an Open ticket switches the status to Assigned, and choosing Open clears the technician, each with a short visible note. It's only a convenience. With JavaScript off, the server rule still applies.
- **Reopening** (Resolved or Closed → Open) returns the ticket to the queue unassigned. The old resolution notes were copied into the history when they were saved (migration V2 added `ticket_history.details`), so reopening never loses them.
- **Resolution notes** are required to reach Resolved or Closed, and they're refused (not silently dropped) if you type new ones on a ticket that isn't being resolved.
- Saving with no actual changes writes nothing and says so.

## 5. How passwords are handled

Passwords are never stored. The database keeps a **hash**: the output of a deliberately slow one-way function (PBKDF2 with HMAC-SHA256, run 600,000 times) applied to the password plus a random **salt**. The salt means two people with the same password get different hashes. The slowness means each guess costs an attacker real time.

Each hash is stored with a label saying how it was made, for example `{pbkdf2-sha256}3f9a…`. Spring's `DelegatingPasswordEncoder` reads the label and uses the matching checker (`config/PasswordHashing`).

Version 0.1.0 made a mistake here: a deprecated Spring constructor quietly used HMAC-SHA1. Rather than reset everyone's password, 0.2.0:

1. labels the old hashes `{pbkdf2-sha1}` in migration `V3__tag_legacy_password_hashes.sql`;
2. checks them with `config/LegacySha1PasswordVerifier`, which repeats the old calculation exactly;
3. after a successful sign-in, lets Spring Security re-hash the password with SHA-256 and save it (`AccountDetailsService.updatePassword`). This is possible only at sign-in, because that's the one moment the app has the real password.

**Sign-in throttling** (`config/LoginThrottle` and `ThrottledAuthenticationProvider`) counts failures per email-and-address and per address over a 15-minute window. Once a limit is reached, attempts are refused before the password is even checked.

## 6. How the tests are organised

Run them with `.\mvnw.cmd verify` (Windows) or `./mvnw verify`. They use real Spring, real Spring Security and a real H2 database in memory; nothing important is mocked.

| Test class | What it shows |
| --- | --- |
| `TicketServiceTest` | Ticket rules: the assignment rule, transitions, resolution notes, reopening, no-op saves, stale versions, search and pagination |
| `AssetServiceTest` | Equipment rules: assign/return, duplicate serials, repair/retired equipment, history |
| `UserServiceTest` | Accounts: hashing, duplicate emails, last-administrator protection, admin-only actions |
| `DataIntegrityTest` | Real commits: rollback when history fails, two technicians racing, two people assigning one laptop |
| `WebFlowTest` | HTTP-level behaviour: permissions per role, CSRF, sessions, throttling, password upgrade, forms keeping input, pagination links |
| `WebSecurityTest` | The original sign-in, CSRF, privacy and escaping checks |
| `MigrationAndModeTest` | Migrating a 0.1.0-style database, the throttle's sliding window, demo-database detection |
| `DemoProfileTest` | The demo profile: seed data, demo/normal separation, and every seeded page rendering for every role |

Most test classes are `@Transactional`, so each test's changes are rolled back afterwards. `DataIntegrityTest` deliberately isn't, because it needs real commits and real concurrent transactions.

`scripts/smoke_test.py` is different: it starts the packaged app as a real server against a temporary file database and drives it over HTTP, including a restart and a backup/restore.

## 7. Exercises to try yourself

Each takes 10–30 minutes. Start the demo with `.\demo.cmd` first.

1. **Follow a request.** Sign in as `employee@example.test`, create a ticket, then find every method it passed through (controller → service → SQL insert → history insert). Write the chain down without looking at this guide.
2. **Break the transaction on purpose.** In `TicketService.update`, temporarily remove `@Transactional` and run `DataIntegrityTest`. Which test fails, and what does the failure tell you about the data that was left behind? Put the annotation back.
3. **Watch a conflict.** Open the same ticket in two browser tabs as `tech@example.test`. Change the priority in one tab and save, then change the status in the other and save. Find the lines in `TicketController.update` that decide which values the form keeps.
4. **Prove a permission.** As the employee, try `http://localhost:8080/tickets/2` (someone else's ticket) and `http://localhost:8080/users`. Find the exact line that produces each response. Then write a new test in `WebFlowTest` for one case that isn't covered yet, such as an employee trying to post a comment on a closed ticket that isn't theirs.
5. **Inspect a password hash.** Stop the demo and open its database read-only with the H2 shell (see `docs/deployment.md` for the `PropertiesLauncher` trick). Look at `SELECT email, password_hash FROM users`. What does the `{...}` prefix tell you, and why do two accounts with the same password have different hashes?
6. **Add a small rule with a test first.** For example: a ticket title can't be only punctuation. Write the failing test in `TicketServiceTest`, then make it pass in `TicketService.create`, then check the error appears beside the Title field in the browser.
