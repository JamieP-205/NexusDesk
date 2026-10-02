# Full file tree

I keep the runnable source, tests, project notes and screenshots together. Generated build output and private database files are ignored.

```text
NexusDesk/
├── .editorconfig
├── .env.example
├── .gitattributes
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.yml
│   │   └── feature_request.yml
│   ├── pull_request_template.md
│   └── workflows/
│       └── ci.yml
├── .gitignore
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── CHANGELOG.md
├── CONTRIBUTING.md
├── LICENSE
├── README.md
├── SECURITY.md
├── docs/
│   ├── backlog.md
│   ├── commit-history.md
│   ├── design.md
│   ├── dev-journal.md
│   ├── file-tree.md
│   ├── learning-notes.md
│   ├── planning.md
│   ├── requirements.md
│   ├── screenshots/
│   │   ├── README.md
│   │   ├── dashboard.png
│   │   ├── equipment.png
│   │   ├── login.png
│   │   ├── mobile.png
│   │   └── ticket-detail.png
│   ├── testing.md
│   └── wireframes.svg
├── mvnw
├── mvnw.cmd
├── pom.xml
├── scripts/
│   └── smoke_test.py
└── src/
    ├── main/
    │   ├── java/
    │   │   └── dev/
    │   │       └── jamie/
    │   │           └── nexusdesk/
    │   │               ├── NexusDeskApplication.java
    │   │               ├── config/
    │   │               │   ├── AccountStatusFilter.java
    │   │               │   ├── Bootstrap.java
    │   │               │   ├── DemoData.java
    │   │               │   └── SecurityConfig.java
    │   │               ├── model/
    │   │               │   ├── Account.java
    │   │               │   ├── Activity.java
    │   │               │   ├── Asset.java
    │   │               │   └── Ticket.java
    │   │               ├── service/
    │   │               │   ├── AssetService.java
    │   │               │   ├── DashboardService.java
    │   │               │   ├── Problem.java
    │   │               │   ├── Rules.java
    │   │               │   ├── TicketFlow.java
    │   │               │   ├── TicketService.java
    │   │               │   └── UserService.java
    │   │               └── web/
    │   │                   ├── AssetController.java
    │   │                   ├── HomeController.java
    │   │                   ├── PageAdvice.java
    │   │                   ├── TicketController.java
    │   │                   └── UserController.java
    │   └── resources/
    │       ├── application-demo.properties
    │       ├── application.properties
    │       ├── db/
    │       │   └── migration/
    │       │       ├── V1__create_helpdesk.sql
    │       │       └── V2__keep_resolution_notes.sql
    │       ├── static/
    │       │   └── style.css
    │       └── templates/
    │           ├── asset-detail.html
    │           ├── asset-new.html
    │           ├── assets.html
    │           ├── dashboard.html
    │           ├── error.html
    │           ├── fragments.html
    │           ├── login.html
    │           ├── ticket-detail.html
    │           ├── ticket-new.html
    │           ├── tickets.html
    │           ├── user-edit.html
    │           └── users.html
    └── test/
        ├── java/
        │   └── dev/
        │       └── jamie/
        │           └── nexusdesk/
        │               ├── AssetServiceTest.java
        │               ├── TicketServiceTest.java
        │               ├── UserServiceTest.java
        │               └── WebSecurityTest.java
        └── resources/
            └── mockito-extensions/
                └── org.mockito.plugins.MockMaker
```

77 tracked files, including this index.
