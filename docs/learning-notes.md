# My next pass through the code

I need to be able to change and explain this project, not just run it. I’m using this order for my own review.

1. I’ll start in `NexusDeskApplication`, then follow `TicketController.create` into `TicketService.create` and the ticket insert.
2. I’ll compare the SQL tables with the records and explain why the foreign keys are needed.
3. I’ll log in as the demo employee and try another person's ticket URL. Then I’ll find the check that blocks it.
4. I’ll follow one status change and its history insert. I’ll explain what should happen if either database operation fails.
5. I’ll open the same ticket in two tabs, save one and try saving the other. Then I’ll find the version check.
6. I’ll assign, return and reassign one device. I’ll check that all assignment rows still exist.
7. I’ll run one test class and make a small feature change with a test of my own.

A sensible first change is field-level validation feedback. It would make the app nicer while teaching me form binding and server-side validation without adding a whole new service.

## Reflection to fill in after I do that

- Which bit could I explain without reading the code?
- What did I misunderstand at first?
- What did I change myself, and how did I check it?
- What would I do differently in the next version?

I’m deliberately leaving these as questions. The build session can't supply personal learning experiences I haven't had yet.
