# How this build was checked

Everything in the first two sections was run by Codex during the build sessions, not by me. I'll add my own results below them as I go.

Codex ran `mvn verify` with Maven 3.9.11 and JDK 21, compiling for Java 17. The suite passed 23 tests: 8 ticket, 5 asset, 4 account and 6 web/security tests. GitHub Actions also passed verification on Java 17 and 21 on 2 October 2026. The packaged-app smoke check runs after that suite in CI.

| Area | What the tests check |
| --- | --- |
| Tickets | Valid create, blank and boundary-length titles, ownership, employee write restrictions, invalid assignees/states, resolution/close/reopen, history retention, stale updates and search input |
| Equipment | Assignment/return history, service and database duplicate-owner checks, employee access, repair/retired restrictions, duplicate serials, future dates and stale edits |
| Accounts | Different salted hashes for the same password, duplicate case-insensitive email, invalid email/password, current-admin protection and active-work reassignment |
| Web | Valid/invalid login, required login, CSRF rejection, an employee blocked from the admin pages, another employee's direct ticket URL, disabled sessions, rendered pages, escaped HTML and invalid IDs. There are no tests yet for a technician trying admin actions, or for a role change ending a session. |

The tests use actual Spring services, Spring Security and H2. They don't mock the database or assert only that a method was called. Each class uses its own in-memory database and rolls back test changes.

## Packaged app and browser checks

Codex ran `python scripts/smoke_test.py` against the packaged JAR. It creates an isolated temporary file database, creates accounts, checks login and ticket privacy, submits a ticket and comment, assigns/resolves/closes the ticket, restarts the server and checks that the ticket, comment and resolution survived. It passed on 2 October 2026 using Java 17. The script uses Python’s standard library and removes its temporary database afterwards.

Codex also captured the five screenshots from the running demo in Chromium. The browser reported no page JavaScript errors and no page-level horizontal overflow at a 390-pixel viewport. Wide tables scroll inside their own container. This does not replace an accessibility audit.

## Bugs found during this session

| Problem | Fix | Evidence |
| --- | --- | --- |
| The test framework could not attach its inline agent in Codex's environment. | Switched Mockito to its subclass mock maker, since these tests don't need inline mocks. | Tests could then run against H2. |
| Ticket insert returned ID and generated timestamps; `getKey()` rejected multiple values. | Requested only the generated `id` column. | The previously failing create/lifecycle tests passed. |
| Reopening would clear the current resolution without retaining its text. | Added history details through migration V2. | The lifecycle test checks that the old fix remains in history. |

## Limits of this evidence

Nobody has load-tested the app, run a formal accessibility audit or tested PostgreSQL. Automated database tests use H2 in memory; the separate restart check covers the demo file database. The optimistic-save tests check stale versions, but they aren't a complete multi-user stress test. The concurrency review added row locks; broader concurrency testing is still worthwhile before real use.

## Manual checks I'm working through

I haven't done these myself yet. When I have, I'll note the date and anything that didn't behave as expected.

1. Start the demo, sign in as `employee@example.test`, create a ticket and add a comment.
2. Try opening another employee's ticket directly. Expect a safe not-found page.
3. Sign in as IT, assign the new ticket, resolve it with notes, close it and reopen it.
4. Check the earlier notes are still in history.
5. Open one ticket in two tabs and confirm the second stale save is rejected.
6. Assign equipment, try assigning it again, return it and inspect the earlier assignment.
7. Disable an employee while their session is open, then refresh that session.
8. Stop and restart the application and check the records still exist.
9. Check the login, queue, detail, equipment and account pages with the keyboard and a narrow viewport.
