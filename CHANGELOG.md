# Changelog

## 0.1.0 — 2 October 2026

First working version, built by the development tools from my brief:

- Login and employee, technician and administrator access.
- Tickets, status transitions, assignment, comments and permanent history.
- Search by title plus status, priority, category, person, assignee and date filters.
- Equipment records, current ownership and assignment history.
- Account creation, editing and disabling.
- Dashboard totals and category/priority charts.
- A separate demo profile and example records.
- Database, permission and rendered-page tests.

During the build, I fixed H2 generated-key handling, kept resolution notes in the audit trail when a ticket is reopened, and added stale-edit checks for tickets and equipment. The implementation started on 1 October and the packaged-app checks and documentation were finished on 2 October.
