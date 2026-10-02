# Build log

This is a log of the two Codex sessions that produced the first version. Codex did the work described here and wrote most of this log; I've edited it so it says who did what.

## 1 October 2026: first build session

Codex started from my README and brief, kept the two original commits and worked on `feature/nexusdesk-app`.

It put the schema and plan in place first, then added accounts and login. Tickets came next: creation, assignment, comments, status rules and history. Database integration tests were written before the interface was finished.

The first attempt to run the tests failed because Mockito tried to attach a Java agent in Codex's environment. It switched the test mock maker, because these tests don't need inline mocking.

The next run found an actual bug: H2 returned the generated timestamps along with the ticket ID. `GeneratedKeyHolder.getKey()` expected one value and failed. The insert now requests only the `id` column, and the create-ticket tests cover that regression. You can see the failure by running the later tests against commit `18827c2`.

All eight ticket tests passed after the ID fix. Codex then added assets and accounts, followed by the browser pages and dashboard. The next test run passed 19 tests, including page rendering, CSRF, escaped ticket content, privacy and disabled sessions.

During its own review, Codex found that reopening a ticket cleared its resolution field while history only said that notes had been saved. It added a second migration to keep the actual resolution text in the history, and serialised comments with ticket updates so a comment cannot slip onto a ticket while another request closes it.

At the end of the session it split the work into the commits you see in the history. That's why seven of them were made within ten seconds of each other.


## 2 October 2026: second session

The 23-test suite had passed in Codex's environment, and the GitHub Java 17 and 21 jobs then passed too. Codex added a smoke script that checks the packaged JAR through real HTTP requests and restarts it against the same file database. That check passed, including ownership restrictions, ticket progression and retained comments/resolution.

Codex then added the demo screenshots, setup instructions, diagrams and repository files, and opened issues #1 and #2 and the pull request. The pull request was merged with a merge commit, so the individual commits are kept rather than squashed.
