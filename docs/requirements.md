# Requirements I’m working against

I’m using the supplied Software Development Project Brief as the source. I’ve kept the required helpdesk, equipment and access rules in scope and separated the optional extensions.

| ID | Requirement | Implementation |
| --- | --- | --- |
| F01 | I can log in using a stored account. | Spring Security with salted password hashes |
| F02 | I can create and retrieve persistent tickets. | TicketService, H2 and Flyway |
| F03 | I can see only my own tickets as an employee. | Ownership checks on list, detail, comments and history |
| F04 | I can assign an active technician and change priority/status as IT staff. | TicketService and TicketFlow |
| F05 | I can add comments and see important changes. | Comments and ticket_history tables |
| F06 | I can resolve, close and reopen a ticket without losing old fixes. | Resolution validation and history details |
| F07 | I can find tickets by title and the brief's filters. | Parameterised queries and GET filter form |
| F08 | I can manage users as an administrator. | UserService and account pages |
| F09 | I can manage equipment and ownership as an administrator. | AssetService and assignment records |
| F10 | I can see assigned equipment as an employee. | Owner-scoped asset queries |
| F11 | I can see the five requested dashboard totals and two statistics. | Aggregate SQL and labelled bar charts |

## Non-functional requirements

I need data to survive restarts, meaningful foreign keys and constraints, parameterised SQL, no plaintext passwords, role enforcement beyond the interface, and errors that don't expose raw database details. I need to be able to run the app from a clean clone and repeat the important tests.

I’m aiming for readable Java, keyboard-accessible forms, a consistent responsive interface and useful empty states. I’m not claiming a formal accessibility audit or production security certification.

## Constraints and assumptions

I’m keeping Java and relational SQL because the brief asks for them. The initial audience is a fictional company of around 60 people, but I haven't load-tested that workload. I assume one local app process, one role per user, and one current owner per asset. Email is the unique login name.

All technician comments in this version are visible to the ticket creator. There is no private-note option. Any enabled staff member can work a ticket; assignment records responsibility, not exclusive editing rights.

I count open workload as every ticket outside Resolved/Closed. Critical counts only active critical tickets. Recently resolved means the rolling previous seven days and excludes tickets that were reopened. Dates shown in the interface use the server's local timezone.

I allow a return from Resolved/Closed to Open; reopening clears the assignee and current resolution timestamp. I keep previous resolution text in history. An unchanged closed status is accepted without adding a duplicate status event.
