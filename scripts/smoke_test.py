"""Exercise the packaged app against a temporary file database, then restart it.

I use only Python's standard library here. Run after ./mvnw package.
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
import tempfile
import time


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

    def login(self, email, password):
        status, url, _ = self.post("/login", "/login", username=email, password=password)
        assert status == 200 and "/login" not in url, "Login failed"


def main():
    jar = Path(__file__).resolve().parents[1] / "target/nexusdesk-0.1.0.jar"
    if not jar.is_file():
        raise SystemExit("Build the app first with ./mvnw package (or .\\mvnw.cmd package).")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    base = f"http://127.0.0.1:{port}"
    password = secrets.token_urlsafe(24)
    with tempfile.TemporaryDirectory(prefix="nexusdesk-smoke-") as directory:
        env = os.environ.copy()
        env.update({"NEXUSDESK_ADMIN_PASSWORD": password, "NEXUSDESK_ADMIN_EMAIL": "admin@example.test",
                    "NEXUSDESK_DB_URL": "jdbc:h2:file:" + Path(directory, "helpdesk").as_posix(),
                    "NEXUSDESK_HOST": "127.0.0.1", "PORT": str(port)})
        # I clear a developer's demo profile so this check can't touch their demo database.
        env.pop("SPRING_PROFILES_ACTIVE", None)
        process = None
        with open(Path(directory, "server.log"), "w+") as log:
            def start():
                nonlocal process
                process = subprocess.Popen(["java", "-jar", str(jar)], env=env, stdout=log, stderr=log)
                for _ in range(100):
                    try:
                        if Browser(base).get("/login")[0] == 200:
                            # The web server starts just before the bootstrap runner finishes.
                            for _ in range(30):
                                try:
                                    browser = Browser(base)
                                    browser.login("admin@example.test", password)
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
                tech = Browser(base)
                tech.login("tech@example.test", password)
                # IDs are deterministic only inside this isolated fresh database.
                for status in ["Assigned", "Resolved", "Closed"]:
                    version = FormFields(tech.get(path)[2]).values["version"]
                    response = tech.post(path, path, version=version, status=status, priority="High", assignee="3",
                                         resolution="Confirmed working with the requester.")
                    assert response[0] == 200, (status, response[0])
                stop()
                start()
                fresh = Browser(base)
                fresh.login("employee@example.test", password)
                status, _, page = fresh.get(path)
                assert status == 200
                for expected in ["Persistence check", "Keep this comment after restarting.", "Confirmed working with the requester.", "This ticket is closed."]:
                    assert expected in page, expected
                print("PASS: account creation, login, ticket/comment creation, privacy, assignment, resolution, closing and persistence across restart")
            finally:
                stop()


if __name__ == "__main__":
    main()
