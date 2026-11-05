# The public browser demo

The `hosted` profile runs NexusDesk for anyone with a browser, using fictional data only. It's the same application, with the same permissions, validation and security checks. The only difference is where each visitor's data lives.

## How visitors are kept apart

- **A sandbox per visitor.** Starting the demo creates a private in-memory H2 database for that browser session. `SandboxFilter` points every query in that visitor's requests at their own database (`SandboxRoutingDataSource`), before Spring Security runs. Services and SQL don't know they're in a sandbox.
- **Fast, cheap copies.** At startup (and hourly, so the sample dates stay recent) the fictional data is seeded once into a template, exported as SQL and replayed into each new sandbox. A sandbox takes tens of milliseconds to create and doesn't repeat the password hashing.
- **No shared password.** The sign-in page has one button per role. Pressing one creates the sandbox and signs the visitor in as that fictional account, so there's no public credential for anyone to change or lock. Signing in with a password still works, but only inside the visitor's own sandbox. Failed attempts count against that sandbox, never against the shared proxy address.
- **Switch role, reset, leave.** The sidebar switches between employee, technician and administrator in the same sandbox, or resets it to the original data. Signing out or an hour without requests deletes the sandbox. A sandbox never lives longer than four hours.

## Bounds

| Limit | Default (`application-hosted.properties`) |
| --- | --- |
| Sandboxes at once | 100. When full, one unused for over 5 minutes makes way; otherwise newcomers are asked to try again shortly, so nobody active is pushed out. |
| New sandboxes | 60 a minute overall; per visitor address, 6 a minute and 10 at once (best effort: the address comes from a header a visitor could forge, which only lets them dodge their own limit) |
| Rows per sandbox | 150 tickets, 400 comments, 40 accounts, 60 pieces of equipment, and 2,500 rows in total including history and assignments |
| Form size | 64 KB; every text field already has a maximum length |
| Idle expiry / maximum age | 60 minutes / 4 hours |

Nothing is written to disk. A restart empties everything and rebuilds the template, so a broken sandbox can't break the demo for anyone else. The app has no email, file upload or outside service for a visitor to trigger.

## Hosting

`deploy/huggingface/` holds a Docker Space definition. It builds the app from a pinned commit of this repository and runs it as an unprivileged user with `--spring.profiles.active=hosted`. HTTPS ends at the host's proxy. Redirects are relative, and the session cookie is `Secure`.

Free hosting has limits. A Space that gets no visits for about two days goes to sleep, and the first visit afterwards waits while it starts. Its address and restarts are controlled by the host.

## Running it locally

```powershell
.\mvnw.cmd package -DskipTests
java -jar target\nexusdesk-0.2.1.jar --spring.profiles.active=hosted --server.servlet.session.cookie.secure=false
```

Then open http://localhost:7860. (The cookie override is only for plain-HTTP testing on your own machine.) `HostedDemoTest` covers isolation, role switching, reset, expiry, limits, throttling and capacity.
