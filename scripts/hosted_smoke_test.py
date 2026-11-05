"""Check a hosted demo's real HTTP workflows in two disposable visitor sandboxes.

Usage: python scripts/hosted_smoke_test.py https://your-demo.example
Only use against the hosted demo, never a production account. Signs out to remove
the test sandboxes. Standard library only; no credentials or API key required.
"""
from html.parser import HTMLParser
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse
from urllib.request import build_opener, HTTPCookieProcessor
import re
import secrets
import sys


class Fields(HTMLParser):
    def __init__(self, page):
        super().__init__()
        self.values = {}
        self.feed(page)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'input' and attrs.get('name'):
            self.values[attrs['name']] = attrs.get('value', '')


class Visitor:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.client = build_opener(HTTPCookieProcessor(CookieJar()))
        self.started = False

    def request(self, path, fields=None, expected=200):
        data = None if fields is None else urlencode(fields).encode()
        try:
            response = self.client.open(self.base + path, data, timeout=60)
        except HTTPError as error:
            response = error
        with response:
            page = response.read().decode()
            assert response.status == expected, (path, response.status, expected)
            return urlparse(response.url).path, page

    def post(self, source, target, expected=200, **fields):
        _, page = self.request(source)
        fields['_csrf'] = Fields(page).values['_csrf']
        return self.request(target, fields, expected)

    def start(self, role):
        _, page = self.request('/login')
        assert 'action="/demo/start"' in page, 'This is not a hosted demo'
        assert 'Raise tickets, reply, and close or reopen your own' in page, 'Old deployment: v0.2.2 role guidance is absent'
        result = self.post('/login', '/demo/start', role=role)
        self.started = True
        return result

    def switch(self, role):
        return self.post('/tickets', '/demo/switch', role=role)

    def action(self, path, action, **fields):
        _, page = self.request(path)
        fields['version'] = Fields(page).values['version']
        return self.post(path, path + '/' + action, **fields)

    def close(self):
        if self.started:
            self.post('/tickets', '/logout')


def main():
    base = sys.argv[1]
    a, b = Visitor(base), Visitor(base)
    marker = 'Demo verification ' + secrets.token_hex(5)
    try:
        _, page = a.start('employee')
        assert 'Things to try as an employee' in page
        a.request('/users', expected=403)
        a.request('/assets/new', expected=403)
        # Error pages must not discard the visitor's sandbox.
        assert 'Things to try as an employee' in a.request('/tickets')[1]
        path, page = a.post('/tickets/new', '/tickets', title=marker,
                            description='Fictional verification ticket', category='Other', priority='Low')
        assert re.fullmatch(r'/tickets/\d+', path), path
        assert marker in page
        a.post(path, path + '/comments', body='Fictional employee follow-up')
        _, page = a.switch('technician')
        assert 'Things to try as an IT technician' in page
        a.request('/users', expected=403)
        a.request('/assets/new', expected=403)
        assert 'Assign to me' in a.request(path)[1]
        _, page = a.action(path, 'take')
        assert 'Resolve this ticket' in page
        a.action(path, 'resolve', resolution='Fictional repair verified')
        a.switch('employee')
        assert "Yes, it's fixed" in a.request(path)[1]
        a.action(path, 'reopen', reason='Fictional issue still occurs')
        a.switch('technician')
        a.action(path, 'take')
        a.action(path, 'resolve', resolution='Second fictional repair verified')
        a.switch('employee')
        _, page = a.action(path, 'confirm')
        assert 'The ticket is now Closed' in page
        assert '3 of 3 done' in a.request('/tickets')[1]
        print('PASS employee raise/comment/reopen/confirm; technician take/resolve; role permissions and checklist')

        b.start('technician')
        # The search input echoes the query; check ticket links rather than that text.
        assert f'href="{path}"' not in b.request('/tickets?' + urlencode({'q': marker}))[1]
        b.request(path, expected=404)
        print('PASS separate visitor cannot see the first visitor ticket')

        _, page = a.switch('admin')
        assert 'Things to try as an administrator' in page
        email = 'verification@example.test'
        _, page = a.post('/users', '/users', name=marker, email=email,
                         role='EMPLOYEE', password='Fictional-check-password-123')
        assert 'Account created for' in page
        # Find the account by reading the actual server-rendered edit pages.
        ids = set(re.findall(r'href="(/users/\d+)"', page))
        user_path = next(p for p in ids if email in a.request(p)[1])
        a.post(user_path, user_path, name=marker, email=email, role='TECHNICIAN', enabled='true')
        _, page = a.post('/assets/new', '/assets', type='Laptop', manufacturer='Demo',
                         model='Verification', serial=marker, purchaseDate='2026-01-01')
        asset_path = re.search(r'action="(/assets/\d+)/assign"', page).group(1)
        _, page = a.post(asset_path, asset_path + '/assign', owner=user_path.rsplit('/', 1)[1])
        assert 'Equipment assigned to' in page
        _, page = a.post(asset_path, asset_path + '/return')
        assert 'Equipment returned to available stock' in page and marker in page
        assert '3 of 3 done' in a.request('/')[1]
        print('PASS administrator creates account, changes role, adds/assigns/returns equipment; checklist')
        a.post('/tickets', '/demo/reset', role='employee')
        assert marker not in a.request('/tickets')[1]
        assert '0 of 3 done' in a.request('/tickets')[1]
        a.request(path, expected=404)
        print('PASS reset clears the visitor data and checklist')
    finally:
        for visitor in (a, b):
            try:
                visitor.close()
            except Exception as error:
                print('Cleanup could not be confirmed:', type(error).__name__)


if __name__ == '__main__':
    main()
