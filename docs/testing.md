# How I checked this build

I ran `mvn verify` with the pinned wrapper-equivalent Maven 3.9.11 toolchain and JDK 21, compiling for Java 17. The suite passed 23 tests: 8 ticket, 5 asset, 4 account and 6 web/security tests. GitHub Actions also passed verification on Java 17 and 21 on 2 October 2026. I added the packaged-app smoke check to run after that suite in CI.

| Area | What I checked |
| --- | --- |
| Tickets | Valid create, blank and boundary-length titles, ownership, employee write restrictions, invalid assignees/states, resolution/close/reopen, history retention, stale updates and search input |
| Equipment | Assignment/return history, service and database duplicate-owner checks, employee access, repair/retired restrictions, duplicate serials, future dates and stale edits |
| Accounts | Different salted hashes for the same password, duplicate case-insensitive email, invalid email/password, current-admin protection and active-work reassignment |
| Web | Valid/invalid login, required login, CSRF rejection, admin URL restrictions, another employee's direct ticket URL, disabled sessions, rendered pages, escaped HTML and invalid IDs |

The tests use actual Spring services, Spring Security and H2. They don't mock the database or assert only that a method was called. Each class uses its own in-memory database and rolls back test changes.

## Packaged app and browser checks

I ran `python scripts/smoke_test.py` against the packaged JAR. It creates an isolated temporary file database, creates accounts, checks login and ticket privacy, submits a ticket and comment, assigns/resolves/closes the ticket, restarts the server and checks that the ticket, comment and resolution survived. It passed on 2 October 2026 using Java 17. The script uses Python’s standard library and removes its temporary database afterwards.

I also captured five screenshots from the running demo in Chromium. The browser reported no page JavaScript errors and no page-level horizontal overflow at a 390-pixel viewport. I visually checked the desktop overview, ticket detail and mobile overview. Wide tables scroll inside their own container. This does not replace an accessibility audit.

## Bugs found during this session

| Problem | Fix | Evidence |
| --- | --- | --- |
| The test framework could not attach its inline agent in the execution environment. | I selected Mockito's subclass mock maker for tests that don't need inline mocks. | Tests could then run against H2. |
| Ticket insert returned ID and generated timestamps; `getKey()` rejected multiple values. | I requested only the generated `id` column. | The previously failing create/lifecycle tests passed. |
| Reopening would clear the current resolution without retaining its text. | I added history details through migration V2. | The lifecycle test checks that the old fix remains in history. |

## Limits of this evidence

I haven't load-tested the app, run a formal accessibility audit or tested PostgreSQL. Automated database tests use H2 in memory; the separate restart check covers the demo file database. The optimistic-save tests check stale versions, but they aren't a complete multi-user stress test. The concurrency review added row locks; broader concurrency testing is still worthwhile before real use.

## Repeatable manual checks

1. I start the demo, sign in as `employee@example.test`, create a ticket and add a comment.
2. I try opening another employee's ticket directly; I expect a safe not-found page.
3. I sign in as IT, assign the new ticket, resolve it with notes, close it and reopen it.
4. I check the earlier notes are still in history.
5. I open one ticket in two tabs and confirm the second stale save is rejected.
6. I assign equipment, try assigning it again, return it and inspect the earlier assignment.
7. I disable an employee while their session is open, then refresh that session.
8. I stop and restart the application and check the records still exist.
9. I check the login, queue, detail, equipment and account pages with the keyboard and a narrow viewport.
