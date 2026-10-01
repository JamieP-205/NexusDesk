# Build journal

## 1 October 2026 — assisted initial build

I started with the existing README and the supplied brief. I kept the original commits and worked on `feature/nexusdesk-app`.

I put the schema and plan in place first, then added accounts and login. Tickets came next: creation, assignment, comments, status rules and history. I wrote database integration tests before completing the interface.

The first attempt to run the tests failed because Mockito tried to attach a Java agent. I changed the test mock maker because these tests don't need inline mocking.

The next run found an actual bug: H2 returned the generated timestamps along with the ticket ID. `GeneratedKeyHolder.getKey()` expected one value and failed. I changed the insert to request only the `id` column. The create-ticket tests cover that regression.

The implementation and these notes were prepared with substantial Codex assistance. This is a record of the build session, not a claim that I worked through it alone or over several weeks.
