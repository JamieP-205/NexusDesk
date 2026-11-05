# Starting the Windows download

## What went wrong with 0.2.0

On Jamie's laptop, double-clicking **Start demo.cmd** from the 0.2.0 ZIP failed unless it was run as administrator. Antivirus was the first suspect. The evidence, gathered on 5 November 2026, points elsewhere:

- The downloaded ZIP and every extracted file carried Windows' "downloaded from the internet" mark (`Zone.Identifier`, zone 3).
- Microsoft Defender recorded no detection for the files. The only Defender blocks in the period were an unrelated attack-surface rule on Windows services. The PC's second scanner, Cairn, logged nothing and rated the launcher "allow".
- The Code Integrity log recorded event 3033 at the moment of each double-click: Explorer was refused when starting `cmd.exe`, status `0xC0E90009` (system integrity policy violation).
- **Smart App Control** was on and enforcing.
- Reproduced with a harmless test script: without the mark it ran; with the mark, a normal (non-administrator) launch failed with *"An Application Control policy has blocked this file. Dangerous file extension from the web."*

So **Smart App Control blocks `.cmd` files downloaded from the internet**, by file type. Signing can't help a `.cmd`, because batch files can't carry an Authenticode signature. Java itself (signed by its publisher) and the JAR were never the problem.

The investigation also found smaller packaging faults in 0.2.0. The launcher mixed LF and CRLF line endings, the ZIP stored paths with backslashes (which some unzip tools mishandle), demo data was written inside the download folder (failing if that folder is read-only), and H2 wrote a trace file that grew by several megabytes a minute.

## What 0.2.1 changes

- `java -jar nexusdesk-0.2.1.jar --demo` starts the demo with no script. It runs the installed, signed Java, keeps data in `%LOCALAPPDATA%\NexusDesk\demo`, uses the next free port if 8080 is busy, and opens the browser.
- **Start demo.cmd** is still included for PCs without Smart App Control. It just runs that command. It has CRLF line endings and the ZIP uses forward-slash paths.
- The README in the ZIP explains the Smart App Control message and the `java` alternative. It asks people **not** to turn Smart App Control off. Administrator rights are never needed.
- H2 trace files are turned off, and `scripts/package_release.py` builds the ZIP the same way every time, with SHA-256 checksums.
- The simplest option is the browser demo, which needs no download at all.

**Tested on 5 November 2026** (Windows 11 Home, Smart App Control on, standard user, not elevated): the ZIP was marked as downloaded and extracted with Explorer's own extraction (which copies the mark) into a folder whose path has spaces. The `.cmd` was blocked as expected. `java -jar nexusdesk-0.2.1.jar --demo` started, signed in over HTTP in 0.3 s, wrote its database under a test app-data folder (also with spaces), left the download folder untouched, created no trace file, and moved to port 8081 because 8080 was in use.

## Options considered

| Option | Result under Smart App Control | Cost |
| --- | --- | --- |
| Keep a `.cmd` launcher | Blocked (by file type) | Free |
| Unsigned `.exe` launcher (for example from `jpackage`) | Usually blocked: unsigned and without reputation | Free |
| Bundled Java runtime | Its `java.exe` stays signed, but it still needs a script or an unsigned launcher to start the app with the right arguments | Free; adds ~45 MB |
| **Run the JAR with installed Java** (`--demo`) | Runs; Java is signed | Free |
| Signed `.exe` or `.msi` installer | Should run: Smart App Control allows code signed by a CA in Microsoft's Trusted Root Program, though new signatures can still meet reputation checks | Azure Artifact Signing is reported at about US$9.99 a month (Basic) in the USA, Canada and Europe, billed from account creation. An OV certificate from a commercial CA typically costs more a year and needs identity checks |
| Browser demo | Nothing to download | Free hosting tier |

Nothing was bought. A signed installer would remove the remaining friction for people who don't have Java. It would need a paid signing service, identity verification and an ongoing subscription, and it still wouldn't guarantee that every security product trusts the download straight away.

## Remaining limits

- With Smart App Control on, double-clicking the launcher still fails. People need the one-line `java` command (or the browser demo).
- Java 17 or newer must be installed. The download doesn't bundle a runtime.
- A clean scan or a successful local run doesn't prove the download is trustworthy to everyone. The checksums only show that it matches what was published.
