# Changelog

## 0.1.0 — 2 October 2026

I added the first working version of NexusDesk:

- Login and employee, technician and administrator access.
- Tickets, status transitions, assignment, comments and permanent history.
- Search by title plus status, priority, category, person, assignee and date filters.
- Equipment records, current ownership and assignment history.
- Account creation, editing and disabling.
- Dashboard totals and category/priority charts.
- A separate demo profile and example records.
- Database, permission and rendered-page tests.

During the build I fixed H2 generated-key handling and kept resolution notes in the audit trail when a ticket is reopened. I also added stale-edit checks for tickets and equipment.

I started the implementation on 1 October and finished the packaged-app checks and documentation on 2 October.
