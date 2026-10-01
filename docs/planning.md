# My plan for NexusDesk

I want one place to track support requests and equipment. I’m using the supplied Northstar brief as a fictional client scenario, not claiming this is software I delivered during a placement.

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

Email, uploads, AI suggestions, SLAs and cloud deployment are stretch work. I don’t want buttons for features that don’t exist. Sites runs JavaScript/Workers rather than this Java server, so it is not the runtime for this version.

## Research used for this build

I checked these sources on 1 October 2026:

- [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html): prepared parameters, row mapping and connection handling.
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): keep protection enabled on state-changing forms.
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): salted adaptive hashes. I chose PBKDF2-HMAC-SHA256 with 600,000 iterations because Spring supports it without another crypto dependency. Argon2id is OWASP’s first choice; this is a documented trade-off, not a claim that PBKDF2 is better.
- [H2 features](https://h2database.com/html/features.html): embedded file storage and transactions. A PostgreSQL move needs separate migration and concurrency testing.

## Build provenance

I asked Codex to implement this initial version from my brief, including substantial code, tests and documentation. These notes describe decisions made during that assisted build. I still need to work through the code myself before describing it as something I can build independently. I’m not spreading commits over invented dates or adding made-up debugging stories.
