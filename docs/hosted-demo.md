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
| Sandboxes at once | 100 (the live service sets 80 in `render.yaml`). When full, one unused for over 5 minutes makes way; otherwise newcomers are asked to try again shortly, so nobody active is pushed out. |
| New sandboxes | 60 a minute overall. Per visitor address, 6 a minute and 10 at once, but only when `nexusdesk.hosted.trusted-proxy-hops` says which `X-Forwarded-For` entry the host's proxy adds; it's off by default, because an address a visitor writes themselves could be used to claim someone else's quota |
| Rows per sandbox | 150 tickets, 400 comments, 40 accounts, 60 pieces of equipment, and 2,500 rows in total including history and assignments |
| Form size | 64 KB; every text field already has a maximum length |
| Idle expiry / maximum age | 60 minutes / 4 hours |

Nothing is written to disk. A restart empties everything and rebuilds the template, so a broken sandbox can't break the demo for anyone else. The app has no email, file upload or outside service for a visitor to trigger.

## Hosting

`render.yaml` and `deploy/render/Dockerfile` run it as a free [Render](https://render.com) web service. The image is built from this repository and runs as an unprivileged user with `--spring.profiles.active=hosted`, with the heap sized for the free plan's 512 MB. The build unpacks the jar and records a class-data-sharing archive in a short training run, which roughly halves a cold start on a small CPU share; Java ignores the archive and starts normally if it is ever missing. HTTPS ends at Render's proxy. Redirects are relative, and the session cookie is `Secure`.

Free hosting has limits. After 15 minutes without visitors the service is stopped, and the next visitor waits about a minute while Render starts it again (and everyone's sandboxes are gone, as after any restart). The free plan has a small share of a CPU, so the demo is slower than running it locally. Per-visitor address limits stay off (`trusted-proxy-hops=0`) until the proxy chain has been checked; the overall limits still apply.

The live demo updates itself: after the checks pass on `main`, the `deploy-demo` job in `.github/workflows/ci.yml` asks Render to deploy that commit, using a Render API key stored as the `RENDER_API_KEY` Actions secret. Pull requests never get the secret.

To deploy your own copy: sign in to Render, choose **New > Blueprint**, and point it at this repository.

## Running it locally

```powershell
.\mvnw.cmd package -DskipTests
java -jar target\nexusdesk-0.2.2.jar --spring.profiles.active=hosted --server.servlet.session.cookie.secure=false
```

Then open http://localhost:7860. (The cookie override is only for plain-HTTP testing on your own machine.) `HostedDemoTest` covers isolation, role switching, reset, expiry, limits, throttling and capacity.

## Checking a deployed demo

Run `python scripts/hosted_smoke_test.py https://your-demo.example` against a hosted demo you control. This standard-library HTTP check creates two disposable visitor sandboxes, exercises all three roles and checks isolation and reset. It signs out both visitors afterwards. It does not require an account or API key.
