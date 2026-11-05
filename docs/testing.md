# How this build was checked

The checks below were run by the AI tools that built each version, not by me. I'll add my own results at the end as I go.

## Version 0.2.1 (Claude, 5 November 2026)

Same machine and tools as 0.2.0.

| Check | Result |
| --- | --- |
| `.\mvnw.cmd verify` | 83 tests, 0 failures (11 new in `HostedDemoTest`) |
| `python scripts\smoke_test.py` | PASS (as for 0.2.0) |
| Hosted profile run locally | Two visitors got separate data; sandboxes created in 30–60 ms; ticket IDs continued correctly after copying the template |
| Downloaded ZIP, Smart App Control on, standard user | Details in [Windows download](windows-download.md): `.cmd` blocked as expected; `java -jar … --demo` started, signed in and kept data outside the download folder |
| `scripts/package_release.py` run twice | Identical ZIP checksum both times |

An automated security review of the hosted-demo commit pointed out that history and assignment rows weren't capped. A total-row limit per sandbox now covers them, tested by `historyGrowthIsCappedToo`.

## Version 0.2.0 (Claude, 4–5 November 2026)

Run on Windows 11 with OpenJDK 27, Maven 3.9.11 (wrapper) and Python 3.14. The CI workflow runs the same checks on Java 17, 21 and 25.

| Check | Result |
| --- | --- |
| `.\mvnw.cmd verify` | 72 tests, 0 failures |
| `python scripts\smoke_test.py` | PASS: accounts, sign-in, privacy, assignment rule, resolving and closing, throttling, pagination, persistence across a restart, backup and restore |
| Upgrade of a copy of a real 0.1.0 demo database | Migrations V3–V4 applied; an old SHA-1 password signed in and was re-hashed as SHA-256; all 10 tickets kept; the normal profile refused to open it; the original file was unchanged (checksum compared) |
| `scripts\db_backup.py` against a running app | Refused to copy the locked file |
| H2 SQL export/import through the app jar | Imported copy had all tickets |
| A clean clone of the pushed branch | `.\mvnw.cmd verify` and the smoke test passed; `.\demo.cmd` started and signed in |

| Test class | Tests | What it covers |
| --- | --- | --- |
| `TicketServiceTest` | 17 | Assignment rule (Open + technician refused, forward states need one, reopening), invalid/disabled assignees, transitions, resolution notes, no-op saves, stale saves, comments making a form stale, search escaping, pagination and filter errors |
| `AssetServiceTest` | 8 | Assign/return history, duplicate owners (service and database), employee access, repair/retired, duplicate serials, dates, no-op edits |
| `UserServiceTest` | 6 | SHA-256 hashes, duplicate emails, field errors, current-admin protection, admin-only actions, reassigning active work |
| `DataIntegrityTest` | 4 | Real commits: a failed history write rolls back the ticket change; two technicians saving the same version (one wins, one history set); two admins assigning one laptop (one owner); history kept after return |
| `WebFlowTest` | 22 | Over HTTP: employee/technician permission matrix, privacy filters, disabled and demoted sessions, CSRF, session ID change at sign-in, safe error pages, throttling, SHA-1 hash upgrade, untagged hashes failing closed, forms keeping input, conflict merging, pagination links, empty states |
| `WebSecurityTest` | 6 | The original sign-in, CSRF, privacy, disabled-session, escaping and invalid-ID checks |
| `MigrationAndModeTest` | 5 | Migrating a 0.1.0-style database, SHA-256 vs the old configuration, demo detection, the throttle's sliding window and in-flight counting |
| `DemoProfileTest` | 4 | Demo seed data, every seeded page rendering for each role, normal startup refusing a demo database, the demo seeder refusing a real one |

### In the browser

The demo was driven in Chromium with Playwright as all three roles: raising a ticket with a validation error, the assignment rule (with and without the form script), a two-tab edit conflict, reopening a closed ticket, disabling a signed-in employee, equipment assign/return, duplicate-email and self-disable errors, and pagination. Every page for every role was checked at 1366, 768 and 375 pixels wide: no horizontal page scroll and no clipped text. The first Tab key press reaches a "Skip to content" link, and the tab order runs through filters, results and page links. After a failed submission, focus moves to the error summary.

### Bugs found while checking 0.2.0

| Problem | Fix | Evidence |
| --- | --- | --- |
| Smoke test failed on Windows after restarting: the last saves were missing. | `WRITE_DELAY=0` in the database URLs. | Smoke test passes, including a hard stop on Windows. |
| Spring's non-deprecated PBKDF2 constructor couldn't verify 0.1.0 hashes. | `LegacySha1PasswordVerifier`. | Migration test and a real 0.1.0 database copy. |
| The first throttle could be bypassed by URL shape or simultaneous guesses (automated security review). | Throttle inside the authentication provider, counting in-flight attempts. | `guessesInFlightCountTowardsTheLimit`. |
| Inline field messages never appeared. | `errors.get(name)` instead of `errors[name]`. | `WebFlowTest` checks the inline element. |
| Equipment with history showed an error page. | Typed `Assignment` record. | `equipmentPageShowsCurrentAndPastOwners`, `everyDemoPageRendersForEveryRole`. |
| Re-saving after a conflict undid the other person's change. | Carry over only fields the user changed. | `aConflictKeepsOnlyMyChangesNotTheStaleValues`. |
| Sign-in took ~2 s under `spring-boot:run`. | `optimizedLaunch=false`. | Sign-in measured at ~0.4 s afterwards. |

### Limits of this evidence

No load test, formal accessibility audit (screen reader, contrast tooling) or external security review. Database tests use H2; nothing has run on PostgreSQL. The concurrency tests cover two simultaneous requests, not sustained load.

## Version 0.1.0 (Codex, 1–2 October 2026)

Codex ran `mvn verify` with Maven 3.9.11 and JDK 21, compiling for Java 17: 23 tests passed. GitHub Actions passed on Java 17 and 21. Codex ran the original smoke test against the packaged JAR (accounts, ticket privacy, assign/resolve/close, restart) on Java 17, and captured screenshots in Chromium with no page-level horizontal overflow at 390 pixels.

| Problem found then | Fix |
| --- | --- |
| Mockito couldn't attach its inline agent in Codex's environment. | Subclass mock maker. |
| Ticket insert returned ID and timestamps; `getKey()` rejected multiple values. | Request only the `id` column. |
| Reopening cleared the resolution without keeping its text. | History details (migration V2). |

## Manual checks I'm working through

I haven't done these myself yet. When I have, I'll note the date and anything unexpected.

1. Start the demo, sign in as `employee@example.test`, create a ticket and add a comment.
2. Try opening another employee's ticket directly. Expect a safe not-found page.
3. Sign in as IT, choose a technician on an Open ticket and watch the status change; then resolve, close and reopen it.
4. Check the earlier notes are still in history.
5. Open one ticket in two tabs, save one, then save the other and read the conflict message.
6. Assign equipment, return it and inspect the earlier assignment.
7. Disable an employee while their session is open, then refresh that session.
8. Get five passwords wrong in a row, then try the right one.
9. Stop and restart the application and check the records still exist; then try a backup and restore on the demo database.
