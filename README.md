# NexusDesk

I wanted a project where Java and SQL do something useful. NexusDesk is a small IT helpdesk: employees can report a problem, IT staff can work through it, and an administrator can keep track of company equipment and accounts.

I’m building it around the supplied Northstar Digital Solutions brief. Northstar is the fictional client in that brief. This is a personal learning project, not a system I’ve delivered for an employer.

**Build note:** I developed this version from my practice brief and kept the implementation, tests and documentation together.

![NexusDesk dashboard with ticket totals, two charts and recent activity](docs/screenshots/dashboard.png)

## What works

- I can create, assign, prioritise, resolve, close and reopen support tickets.
- I can add comments and follow a ticket's history, including old resolution notes after reopening.
- I can search ticket titles and filter by status, priority, category, employee, technician or date.
- I can register equipment, assign it, return it and see previous assignments.
- I can create, edit and disable accounts as an administrator.
- I can see workload totals, available equipment and category/priority charts as IT staff.
- My data stays in a relational database file when I restart the app.

| Account | Access |
| --- | --- |
| Employee | I can create tickets, see and comment on my own tickets, and see my assigned equipment. |
| Technician | I can work on all tickets, view people’s ticket/equipment history and use the dashboard. |
| Administrator | I have technician access plus account and equipment management. |

## Run the demo

I use **JDK 17 or 21**. I include the Maven wrapper, so a separate Maven installation isn't needed. The first build needs internet access to download dependencies.

```bash
git clone https://github.com/JamieP-205/NexusDesk.git
cd NexusDesk
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

In Windows PowerShell:

```powershell
git clone https://github.com/JamieP-205/NexusDesk.git
cd NexusDesk
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

I then open **http://localhost:8080**. The demo uses fictional records and a separate file at `data/nexusdesk-demo.mv.db`.

| Role | Email | Demo password |
| --- | --- | --- |
| Administrator | `admin@example.test` | `Demo-password-123` |
| Technician | `tech@example.test` | `Demo-password-123` |
| Employee | `employee@example.test` | `Demo-password-123` |

I only use these public credentials for the local demo. I use the normal profile for my own data.

## Start with an empty database

In PowerShell:

```powershell
$env:NEXUSDESK_ADMIN_EMAIL = "jamie@example.test"
$env:NEXUSDESK_ADMIN_PASSWORD = Read-Host "Choose an admin password (12-128 characters)" -MaskInput
.\mvnw.cmd spring-boot:run
```

`-MaskInput` needs PowerShell 7. In Windows PowerShell 5.1 I can set the variable in my IDE run configuration, or use this secure prompt conversion:

```powershell
$secret = Read-Host "Choose an admin password" -AsSecureString
$env:NEXUSDESK_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $secret).Password
.\mvnw.cmd spring-boot:run
```

On macOS/Linux (Bash):

```bash
export NEXUSDESK_ADMIN_EMAIL='jamie@example.test'
read -rsp 'Choose an admin password: ' NEXUSDESK_ADMIN_PASSWORD
export NEXUSDESK_ADMIN_PASSWORD
./mvnw spring-boot:run
```

The first startup creates one administrator if there are no users. Setting these variables later does **not** reset an existing account. I can remove the password variable after the first successful start.

The normal database is `data/nexusdesk.mv.db`. Flyway applies SQL migrations automatically. I don't need to create tables by hand. `.env.example` describes the optional settings; it is a reference file, not automatically loaded configuration.

I stop the server before backing up the database file. I keep backups private. Deleting the database resets the app and loses its data, so I don't use that as a normal upgrade step.

## Build and test

```bash
./mvnw verify
# Optional full HTTP flow and restart check (Python 3.10+):
python scripts/smoke_test.py
java -jar target/nexusdesk-0.1.0.jar --spring.profiles.active=demo
```

On Windows I replace `./mvnw` with `.\mvnw.cmd`. CI checks Java 17 and 21. The tests use separate in-memory databases and never touch the normal/demo files.

## Stack and structure

I use Java 17, Spring Boot 3.5.16, Spring MVC, Spring Security, Thymeleaf, Spring JDBC, H2, Flyway, JUnit 5 and plain CSS. There is no frontend build or paid service needed to run the app.

- `src/main/java/dev/jamie/nexusdesk/model/` holds the data records.
- `service/` holds business rules, parameterised SQL and transaction boundaries.
- `web/` handles routes and page models; `config/` handles login and startup.
- `src/main/resources/db/migration/` holds the versioned schema.
- `templates/` and `static/` hold the interface.
- `src/test/` holds integration and security tests.
- `docs/` holds the plan, backlog, diagrams, testing notes and screenshots.

I kept JDBC in the services for this size of app. If those classes become difficult to follow, extracting repositories is a sensible next refactor. I haven't added interfaces or inheritance just to make the folder tree bigger; the startup and security classes use framework interfaces where they are actually needed.

[Full file tree](docs/file-tree.md) · [Design and ER diagram](docs/design.md) · [Requirements](docs/requirements.md) · [Backlog](docs/backlog.md) · [Build journal](docs/dev-journal.md) · [Testing](docs/testing.md) · [Commit history](docs/commit-history.md)

## A closer look

![Ticket detail with conversation, controls and a permanent history](docs/screenshots/ticket-detail.png)

I can see the problem and discussion beside the ticket controls. The server checks status changes and detects stale edits.

![Equipment register showing ownership and availability](docs/screenshots/equipment.png)

I keep current ownership separate from the assignment history, so returning an asset doesn't remove who used it before.

[Sign-in screenshot](docs/screenshots/login.png) · [Mobile screenshot](docs/screenshots/mobile.png) · [Initial wireframes](docs/wireframes.svg)

## What I learned

I’m leaving my personal reflection open until I’ve run and changed the app myself. I don't want to claim understanding just because the generated tests pass. The concrete things I need to explain are:

- Why a ticket update and its history belong in one transaction.
- Why hiding an admin button doesn't protect its URL.
- How a unique constraint prevents two current owners of an asset.
- Why H2's generated timestamps broke the original ID lookup, and how requesting only the ID fixed it.

I’ve included a [code walkthrough](docs/learning-notes.md) so I can work through those points and make my own follow-up changes.

## Limits and next steps

I haven't implemented attachments, email, password reset, CSV/PDF export, SLA timers or similar-ticket suggestions. These are tracked as future work, not working features. Tickets are not paginated yet. Error pages keep database details private, but form errors could be more helpful beside the relevant field.

The file-backed H2 setup is for one local app instance. It isn't a production deployment. I would need HTTPS, login throttling, backups and a deployment/database review before using real staff data. [Security notes](SECURITY.md) explain what is already in place and what is still missing.

The Java server is not deployed to Sites, which uses a different server runtime. I’ve kept one working Java application rather than a separate online mockup.

## Licence

I’ve released the project under the [MIT licence](LICENSE).
