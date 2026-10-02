# Commit history

I opened the repository and added my practice brief on 1 October 2026. The implementation commits cover project setup, accounts, tickets, the H2 generated-ID fix, equipment, browser pages, history retention and delivery checks.

I kept these changes as separate commits and merged the feature branch into `main`. The commit dates record when each change was committed; they are not estimates of the time spent on it. Some intermediate commits introduce controllers before their templates, so use the completed branch when running the full application.

The generated ticket-ID fix addresses H2 returning multiple generated values. The ticket insert requests only the `id` column, and the integration tests cover that regression.

[GitHub history](https://github.com/JamieP-205/NexusDesk/commits/main/)
