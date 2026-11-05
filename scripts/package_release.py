"""Build the downloadable demo ZIP and its checksum file from target/nexusdesk-<version>.jar.

    ./mvnw package          (or .\\mvnw.cmd package)
    python scripts/package_release.py

Writes dist/NexusDesk-<version>-demo.zip, dist/nexusdesk-<version>.jar and dist/SHA256SUMS.txt. ZIP entries use forward slashes and
Windows files use CRLF line endings, so the archive extracts cleanly with Explorer, 7-Zip and unzip.
Standard library only.
"""
from pathlib import Path
import hashlib
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]

LAUNCHER = r"""@echo off
rem Starts the NexusDesk demo. It needs Java 17 or newer and opens your browser when ready.
setlocal
cd /d "%~dp0"
where java >nul 2>nul || (
  echo Java 17 or newer is needed. Install it from https://adoptium.net/ and try again.
  pause
  exit /b 1
)
java -jar "%~dp0nexusdesk-{version}.jar" --demo
if errorlevel 1 pause
"""

README = """NexusDesk {version} - local demo
===============================

A practice IT helpdesk with fictional people, tickets and equipment.
You can also try it in your browser without downloading anything:
https://jamieparr.dev/projects/nexusdesk/

Requirements
------------
Java 17 or newer (for example Eclipse Temurin from https://adoptium.net/).
Check with:  java -version

Start the demo
--------------
Windows: extract the whole ZIP, open the folder and double-click "Start demo.cmd".

Any system, or if Windows won't open the .cmd file: open a terminal in this folder
(in Windows Explorer: right-click the folder background > Open in Terminal) and run

    java -jar nexusdesk-{version}.jar --demo

Your browser opens when the app is ready (usually http://localhost:8080; the next free
port is used if 8080 is busy). Sign in as employee@example.test, tech@example.test or
admin@example.test. All three use the password Demo-password-123.
Stop it by closing the window or pressing Ctrl+C.

About "Start demo.cmd" on Windows
---------------------------------
"Start demo.cmd" is an unsigned script. Windows Smart App Control, when it is switched on,
refuses unsigned scripts downloaded from the internet and shows "An Application Control
policy has blocked this file". Please don't turn Smart App Control off or change security
settings for this demo; use the java command above instead, which runs the Java you
installed. Administrator rights are never needed.

Your demo data
--------------
Windows: %LOCALAPPDATA%\\NexusDesk\\demo
macOS/Linux: ~/.local/share/nexusdesk/demo
Delete that folder to start again with fresh fictional data. Don't enter real
information: the demo passwords are public.

Checking the download
---------------------
SHA-256 checksums are listed in SHA256SUMS.txt on the release page:
https://github.com/JamieP-205/NexusDesk/releases/tag/v{version}
In PowerShell: Get-FileHash NexusDesk-{version}-demo.zip -Algorithm SHA256

Source, documentation and how it was built: https://github.com/JamieP-205/NexusDesk
"""


def crlf(text: str) -> bytes:
    return text.replace("\r\n", "\n").replace("\n", "\r\n").encode("utf-8")


def main() -> None:
    pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
    version = re.search(r"<artifactId>nexusdesk</artifactId><version>([^<]+)</version>", pom).group(1)
    jar = ROOT / "target" / f"nexusdesk-{version}.jar"
    if not jar.is_file():
        raise SystemExit(f"{jar} is missing. Run ./mvnw package first.")
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    archive = dist / f"NexusDesk-{version}-demo.zip"
    folder = f"NexusDesk-{version}"
    entries = [
        (f"{folder}/nexusdesk-{version}.jar", jar.read_bytes()),
        (f"{folder}/Start demo.cmd", crlf(LAUNCHER.replace("{version}", version))),
        (f"{folder}/README.txt", crlf(README.replace("{version}", version))),
        (f"{folder}/LICENSE", crlf((ROOT / "LICENSE").read_text(encoding="utf-8"))),
    ]
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in entries:
            # A fixed timestamp keeps the archive identical for the same JAR.
            info = zipfile.ZipInfo(name, date_time=(2026, 11, 5, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, data)
    # The bare JAR is published too; the hosted demo's Docker image downloads and checks it.
    bare = dist / jar.name
    bare.write_bytes(jar.read_bytes())
    lines = []
    for f in (archive, bare):
        digest = hashlib.sha256(f.read_bytes()).hexdigest()
        lines.append(f"{digest}  {f.name}\n")
        print(f"{f.name} ({f.stat().st_size:,} bytes) sha256 {digest}")
    (dist / "SHA256SUMS.txt").write_text("".join(lines), encoding="ascii", newline="\n")


if __name__ == "__main__":
    main()
