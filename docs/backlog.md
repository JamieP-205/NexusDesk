# My backlog

The first version was built in two Codex sessions. Form errors, paging and sign-in throttling were completed by Claude in 0.2.0 (see the [changelog](../CHANGELOG.md)). The "To do" rows are what's next.

| Slice | User story | Acceptance check | Status |
| --- | --- | --- | --- |
| Accounts | As an employee, I want to sign in so that my requests stay private. | Correct password works; wrong password fails. | Complete |
| Tickets | As an employee, I want to raise and follow a ticket so that I know what is happening. | Saved ticket survives restart; other employees cannot read it. | Complete |
| Queue | As a technician, I want to filter and assign work so that I can prioritise the queue. | Filters combine; only active IT staff can be assigned. | Complete |
| Conversation | As an employee, I want to add updates so that IT has the information it needs. | My comments are visible; I cannot comment on someone else's ticket. | Complete |
| Resolution | As a technician, I want to record a fix so that there is a useful support history. | A blank resolution is rejected; reopening keeps the old notes. | Complete |
| Inventory | As an administrator, I want to assign equipment so that I know who has it. | Two current owners are blocked; returns keep history. | Complete |
| People | As an administrator, I want to disable accounts so that former users lose access. | Existing sessions stop on the next request; records remain. | Complete |
| Overview | As a technician, I want workload totals so that I know where to start. | Totals come from SQL; chart values are labelled. | Complete |
| Delivery | As a contributor, I want setup notes and tests so that I can continue the project. | Clean package, screenshots, wrapper and CI configuration. | Complete |
| Form errors | As a user, I want errors beside fields so that I can correct them without losing context. | Keep entered non-secret values and focus the first error. | Complete (0.2.0) |
| Paging | As a technician, I want pages of results so that a large queue stays usable. | Stable ordering and filters preserved between pages. | Complete (0.2.0) |
| Sign-in safety | As an administrator, I want repeated failed sign-ins slowed down so that passwords can't be guessed quickly. | Blocked attempts never reach the password check. | Complete (0.2.0) |
| Recovery | As an account holder, I want a safe password reset so that I can regain access. | Expiring single-use tokens, rate limiting, no account disclosure. | To do |
| Admin password reset | As an administrator, I want to set a temporary password for someone who is locked out. | Forces a change at next sign-in; recorded in the history. | To do |
| PostgreSQL | As an operator, I want a server database so that backups don't need downtime. | Migrations and concurrency tests pass on PostgreSQL. | To do |
| Similar tickets | As a technician, I want to find previous fixes so that I can avoid repeating investigation. | Start with a measured keyword prototype using fictional data. | To do |

Issues [#1 (form errors)](https://github.com/JamieP-205/NexusDesk/issues/1) and [#2 (pagination)](https://github.com/JamieP-205/NexusDesk/issues/2) are addressed on the `claude/finish-nexusdesk` branch and can be closed once it's reviewed and merged.
