"""Exercise the packaged app over HTTP against a temporary file database.

Covers account creation, sign-in, privacy, the ticket assignment rule, resolving and closing,
sign-in throttling, pagination, persistence across a restart, and a backup/restore cycle with
scripts/db_backup.py. Standard library only (Python 3.10+). Run after ./mvnw package.
"""
from html.parser import HTMLParser
from http.cookiejar import CookieJar
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import HTTPCookieProcessor, build_opener
import os
import secrets
import socket
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
import db_backup  # noqa: E402


class FormFields(HTMLParser):
    def __init__(self, page):
        super().__init__()
        self.values = {}
        self.feed(page)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "input" and "name" in attrs:
            self.values[attrs["name"]] = attrs.get("value", "")


class Browser:
    def __init__(self, base):
        self.base = base
        self.client = build_opener(HTTPCookieProcessor(CookieJar()))

    def get(self, path):
        return self.request(path)

    def request(self, path, fields=None):
        data = None if fields is None else urlencode(fields).encode()
        try:
            response = self.client.open(self.base + path, data=data, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            return response.status, response.url, response.read().decode()

    def post(self, source, target, **fields):
        status, _, page = self.get(source)
        assert status == 200, (source, status)
        fields["_csrf"] = FormFields(page).values["_csrf"]
        return self.request(target, fields)

    def try_login(self, email, password):
        return self.post("/login", "/login", username=email, password=password)[1]

    def login(self, email, password):
        assert "/login" not in self.try_login(email, password), "Login failed"


def main():
    jars = sorted((Path(__file__).resolve().parents[1] / "target").glob("nexusdesk-*.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        raise SystemExit("Build the app first with ./mvnw package (or .\\mvnw.cmd package).")
    jar = jars[-1]
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    base = f"http://127.0.0.1:{port}"
    password = secrets.token_urlsafe(24)
    with tempfile.TemporaryDirectory(prefix="nexusdesk-smoke-") as directory:
        database = Path(directory, "helpdesk")
        env = os.environ.copy()
        env.update({"NEXUSDESK_ADMIN_PASSWORD": password, "NEXUSDESK_ADMIN_EMAIL": "admin@example.test",
                    "NEXUSDESK_DB_URL": "jdbc:h2:file:" + database.as_posix() + ";WRITE_DELAY=0",
                    "NEXUSDESK_HOST": "127.0.0.1", "PORT": str(port)})
        # I clear a developer's demo profile so this check can't touch their demo database.
        env.pop("SPRING_PROFILES_ACTIVE", None)
        process = None
        with open(Path(directory, "server.log"), "w+") as log:
            def start():
                nonlocal process
                process = subprocess.Popen(["java", "-jar", str(jar)], env=env, stdout=log, stderr=log)
                for _ in range(150):
                    try:
                        if Browser(base).get("/login")[0] == 200:
                            # The web server starts just before the bootstrap runner finishes.
                            for _ in range(30):
                                try:
                                    Browser(base).login("admin@example.test", password)
                                    return
                                except AssertionError:
                                    time.sleep(0.2)
                    except (URLError, ConnectionError):
                        pass
                    if process.poll() is not None:
                        log.seek(0)
                        raise RuntimeError("Server failed to start:\n" + log.read()[-4000:])
                    time.sleep(0.2)
                raise RuntimeError("Server did not become ready")

            def stop():
                # SIGTERM on Linux/macOS gives a graceful shutdown. On Windows this is a hard stop,
                # which is a harsher test: committed work must still be on disk.
                if process and process.poll() is None:
                    process.terminate()
                    process.wait(timeout=20)

            try:
                start()
                admin = Browser(base)
                admin.login("admin@example.test", password)
                for name, email, role in [("Alex", "employee@example.test", "EMPLOYEE"),
                                           ("Morgan", "tech@example.test", "TECHNICIAN")]:
                    assert admin.post("/users", "/users", name=name, email=email, role=role, password=password)[0] == 200
                private = admin.post("/tickets/new", "/tickets", title="Private admin ticket", description="Not shared",
                                     category="Account", priority="Low")[1].removeprefix(base)
                employee = Browser(base)
                employee.login("employee@example.test", password)
                result = employee.post("/tickets/new", "/tickets", title="Persistence check", description="Created through HTTP",
                                       category="Network", priority="High")
                assert result[0] == 200 and "Persistence check" in result[2]
                path = result[1].removeprefix(base)
                assert employee.post(path, path + "/comments", body="Keep this comment after restarting.")[0] == 200
                assert employee.get(private)[0] == 404
                assert employee.get("/users")[0] == 403

                # The assignment rule: a technician on an Open ticket is refused with a reason, not dropped.
                tech = Browser(base)
                tech.login("tech@example.test", password)
                # IDs are deterministic only inside this isolated fresh database: 3 is Morgan.
                version = FormFields(tech.get(path)[2]).values["version"]
                refused = tech.post(path, path, version=version, status="Open", priority="High", assignee="3", resolution="")
                assert refused[0] == 400 and "change the status to Assigned" in refused[2], "Open + technician must be refused"
                for status in ["Assigned", "Resolved", "Closed"]:
                    version = FormFields(tech.get(path)[2]).values["version"]
                    notes = "Confirmed working with the requester." if status != "Assigned" else ""
                    response = tech.post(path, path, version=version, status=status, priority="High", assignee="3", resolution=notes)
                    assert response[0] == 200 and "Saved:" in response[2], (status, response[0])

                # Sign-in throttling: five wrong passwords lock that account from this address.
                guesser = Browser(base)
                for attempt in range(5):
                    assert "/login?error" in guesser.try_login("employee@example.test", f"wrong-{attempt}")
                assert "/login?locked" in guesser.try_login("employee@example.test", password), "Throttle didn't engage"

                # Pagination keeps the filters in its links.
                for n in range(21):
                    admin.post("/tickets/new", "/tickets", title=f"Paging check {n}", description="Pagination",
                               category="Other", priority="Low")
                status, _, page = admin.get("/tickets?q=paging&category=Other")
                assert status == 200 and "21 tickets" in page and "q=paging&amp;category=Other&amp;page=2" in page, "Pagination links"
                status, _, page = admin.get("/tickets?q=paging&category=Other&page=2")
                assert "Paging check 0" in page and "Paging check 20" not in page, "Page 2 content"

                stop()
                start()
                fresh = Browser(base)
                fresh.login("tech@example.test", password)
                status, _, page = fresh.get(path)
                assert status == 200
                for expected in ["Persistence check", "Keep this comment after restarting.",
                                 "Confirmed working with the requester.", "This ticket is closed."]:
                    assert expected in page, expected

                # Back up the stopped database, make a change, restore, and check the change is gone.
                stop()
                assert not db_backup.in_use(db_backup.db_file(str(database)))
                db_backup.main(["backup", str(database), "--to", str(Path(directory, "backups"))])
                saved = next(Path(directory, "backups").glob("helpdesk-*.mv.db"))
                start()
                later = Browser(base)
                later.login("admin@example.test", password)
                assert db_backup.in_use(db_backup.db_file(str(database))), "A running database should be locked"
                later.post("/tickets/new", "/tickets", title="Made after the backup", description="Should vanish",
                           category="Other", priority="Low")
                assert "Made after the backup" in later.get("/tickets?q=after+the+backup")[2]
                stop()
                db_backup.main(["restore", str(saved), str(database)])
                start()
                restored = Browser(base)
                restored.login("admin@example.test", password)
                assert "Persistence check" in restored.get("/tickets?q=persistence")[2]
                assert "Made after the backup" not in restored.get("/tickets?q=after+the+backup")[2]
                print("PASS: accounts, sign-in, privacy, assignment rule, resolution and closing, throttling, "
                      "pagination, persistence across restart, and backup/restore")
            finally:
                stop()


if __name__ == "__main__":
    main()
