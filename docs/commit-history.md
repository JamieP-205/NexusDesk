# Commit history

I kept the original repository commits and added work in the order it was implemented. The dates reflect the actual sessions on 1 and 2 October 2026. I haven't backdated them or added pretend debugging work.

| Order | Commit | What changed |
| --- | --- | --- |
| 1 | Initial commit | Original repository setup. |
| 2 | README updated with relevant information | Original brief and objectives. |
| 3 | Set up Java project and plan the database | Build configuration, schema and initial plan. |
| 4 | Add login and role checks with stored user accounts | Authentication and account rules. |
| 5 | Build ticket workflow with comments and an audit trail | Tickets, filtering, status flow, comments and history. |
| 6 | Fix generated ticket IDs after running database tests | A real H2 generated-key failure found by the integration tests. |
| 7 | Add equipment assignments and account administration | Inventory, ownership and account pages. |
| 8 | Add the helpdesk screens, dashboard and demo data | Working interface and fictional demo records. |
| 9 | Keep resolution notes when a ticket is reopened | History preservation and concurrency review. |
| 10 | Check account rules and make the build repeatable | Extra tests, Maven wrapper, Java 17/21 CI and template formatting. |
| 11 | Verify the packaged app survives a restart | Repeatable HTTP and file-database smoke check. |
| 12 | Document the build and add real app screenshots | Setup guide, backlog, design, wireframes, screenshot assets and repository guidance. |

I used `feature/nexusdesk-app` for the implementation. The merge into `main` preserves these commits instead of squashing them into one.

[Live GitHub history](https://github.com/JamieP-205/NexusDesk/commits/main/)
