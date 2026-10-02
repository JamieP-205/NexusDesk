# My plan for NexusDesk

I want one place to track support requests and equipment. I wrote the Northstar brief myself as practice; Northstar is a fictional company, not a client and not a placement.

Codex wrote this plan from my brief during the first build session. I've corrected it where it turned out to be wrong.

## Starting scope — 1 October 2026

I’m keeping Java and SQL at the centre of this. The first slice is login, tickets, assignment, comments and a controlled status flow. After that I’m adding asset ownership, user administration, filtering and a small dashboard.

I chose Spring Boot with server-rendered Thymeleaf pages. I considered a separate React app, but that would add another build and an API before the basic helpdesk works. JDBC keeps the SQL visible. H2 stores data in a local file, so someone can try the project without installing a database server.

I’m keeping dependencies managed by Spring Boot 3.5.16 and targeting Java 17. I’m using Flyway for numbered schema migrations instead of recreating tables at startup.

## Rules I need to get right

- An employee can only read and comment on their own tickets.
- A technician or administrator can manage tickets, but an assignee must be active IT staff.
- Every ticket change and its history entry must succeed together.
- I need a resolution before resolving a ticket; a closed ticket can be reopened.
- One asset can have one current owner. Returning it must keep the old assignment history.
- Disabling an account must end its access even if it is already logged in.
- I must keep one enabled administrator and avoid leaving active work assigned to disabled staff.

## What I’m leaving for later

Email, uploads, AI suggestions, SLAs and cloud deployment are stretch work. I don’t want buttons for features that don’t exist.

## Research used for this build

Sources the build relied on (checked during the Codex session on 1 October 2026):

- [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html): prepared parameters, row mapping and connection handling.
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): keep protection enabled on state-changing forms.
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): salted adaptive hashes. The plan was PBKDF2-HMAC-SHA256 with 600,000 iterations because Spring supports it without another crypto dependency. The code actually ended up using HMAC-SHA1 through a deprecated constructor; see [SECURITY.md](../SECURITY.md). Argon2id is OWASP’s first choice.
- [H2 features](https://h2database.com/html/features.html): embedded file storage and transactions. A PostgreSQL move needs separate migration and concurrency testing.

## Build provenance

I asked Codex to implement this initial version from my brief, including the code, tests and documentation. I still need to work through the code myself before describing it as something I can build independently.

## First implementation checkpoint

The first test run reached H2 and applied the migration, but Mockito's default inline mock maker could not attach an agent in the build environment. These integration tests use real services and a real database, so I switched the test mock maker to the subclass implementation rather than adding instrumentation just to start the tests.
