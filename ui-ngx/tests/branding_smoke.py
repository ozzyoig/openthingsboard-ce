# SPDX-FileCopyrightText: Copyright The Thingsboard Authors
# SPDX-License-Identifier: Apache-2.0
"""Browser smoke test of the built Angular UI with an explicitly mocked API.

Run after `ng build --configuration development`:
  CHROMIUM_PATH=/usr/bin/chromium python3 ui-ngx/tests/branding_smoke.py
Requires Python Playwright. This does not replace the Java/PostgreSQL tests.
"""
import base64
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
from threading import Thread
import time

from playwright.sync_api import sync_playwright, expect


ROOT = Path(__file__).resolve().parents[1] / "target/generated-resources/public"
PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aFOkAAAAASUVORK5CYII=")
IMAGE = "data:image/png;base64," + base64.b64encode(PNG).decode()
UID = "11111111-1111-4111-8111-111111111111"
TENANT = "22222222-2222-4222-8222-222222222222"


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if not (ROOT / self.path.split("?")[0].lstrip("/")).is_file():
            self.path = "/index.html"
        super().do_GET()

    def log_message(self, *_):
        pass


def token(authority):
    def encode(value):
        return base64.urlsafe_b64encode(json.dumps(value).encode()).decode().rstrip("=")
    claims = {"sub": "admin@example.test", "scopes": [authority], "userId": UID,
              "tenantId": TENANT, "exp": int(time.time()) + 3600}
    return encode({"alg": "HS256", "typ": "JWT"}) + "." + encode(claims) + ".test"


def main():
    assert (ROOT / "index.html").is_file(), "Build Angular before running this test"
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(Handler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{server.server_port}"
    global_brand = {"appName": "Global Test Brand", "logo": IMAGE,
                    "loginLogo": IMAGE, "favicon": IMAGE, "primaryColor": "#123456"}
    own = {}
    scope = {"authority": None}
    errors = []

    def api(route):
        request = route.request
        path = request.url.split(base)[-1].split("?")[0]
        data = {"data": [], "totalPages": 0, "totalElements": 0, "hasNext": False}
        if path == "/api/noauth/branding":
            data = global_brand
        elif path == "/api/branding/image":
            assert "multipart/form-data" in request.headers["content-type"]
            data = {"data": IMAGE}
        elif path == "/api/branding/effective":
            data = {**global_brand, **own.get(scope["authority"], {})}
        elif path == "/api/branding":
            if request.method == "POST":
                own[scope["authority"]] = {k: v for k, v in request.post_data_json.items() if v is not None}
            elif request.method == "DELETE":
                own.pop(scope["authority"], None)
            data = own.get(scope["authority"], {})
        elif path == f"/api/user/{UID}":
            data = {"id": {"id": UID, "entityType": "USER"}, "tenantId": {"id": TENANT, "entityType": "TENANT"},
                    "authority": scope["authority"], "email": "admin@example.test", "additionalInfo": {}}
        elif path == "/api/system/params":
            data = {"maxDatapointsLimit": 50000, "nullsOrderStrategy": "NULLS_FIRST", "edqsEnabled": False}
        elif "settings" in path:
            data = {}
        elif "oauth2Clients" in path:
            data = []
        route.fulfill(status=200, content_type="application/json", body=json.dumps(data))

    try:
        with sync_playwright() as playwright:
            options = {"headless": True, "args": ["--no-sandbox"]}
            if os.environ.get("CHROMIUM_PATH"):
                options["executable_path"] = os.environ["CHROMIUM_PATH"]
            browser = playwright.chromium.launch(**options)
            page = browser.new_page()
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.route("**/api/**", api)
            page.goto(base + "/login")
            expect(page.get_by_label("Username (email)")).to_be_visible()
            expect(page.locator("tb-logo")).to_contain_text("Global Test Brand")
            expect(page).to_have_title("Global Test Brand | Login")
            assert page.locator('link[rel="icon"]').get_attribute("href") == IMAGE
            assert page.locator(".tb-login-content").evaluate("el => el.classList.contains('tb-dark')")

            for authority in ["SYS_ADMIN", "TENANT_ADMIN"]:
                scope["authority"] = authority
                page.evaluate("jwt => {localStorage.setItem('jwt_token', jwt); localStorage.setItem('jwt_token_expiration', String(Date.now()+3600000));}", token(authority))
                page.goto(base + "/settings/appearance")
                expect(page.get_by_label("Application name", exact=True)).to_be_visible()
                expect(page.locator("#branding-loginLogo")).to_have_count(1 if authority == "SYS_ADMIN" else 0)
                page.get_by_label("Application name", exact=True).fill(authority + " Brand")
                color = page.get_by_label("Primary accent color", exact=True)
                color.fill("not-a-color")
                expect(page.get_by_role("button", name="Save", exact=True)).to_be_disabled()
                color.fill("#ffffff")
                page.locator("#branding-logo").set_input_files({"name": "logo.png", "mimeType": "image/png", "buffer": PNG})
                expect(page.locator('section img').first).to_be_visible()
                page.get_by_role("button", name="Save", exact=True).click()
                expect(page.get_by_role("status")).to_have_text("Branding saved.")
                expect(page.locator("tb-logo").first).to_contain_text(authority + " Brand")
                assert page.locator("html").evaluate("el => getComputedStyle(el).getPropertyValue('--tb-brand-on-primary').trim()") == "#000000"
                page.reload()
                expect(page.get_by_label("Application name", exact=True)).to_have_value(authority + " Brand")
                page.get_by_role("button", name="Reset all overrides", exact=True).click()
                expect(page.get_by_label("Application name", exact=True)).to_have_value("")
                expect(page.locator("tb-logo").first).to_contain_text("Global Test Brand")

            scope["authority"] = None
            page.evaluate("() => localStorage.clear()")
            page.goto(base + "/login")
            expect(page.locator("tb-logo")).to_contain_text("Global Test Brand")
            assert not errors, errors
            browser.close()
        print("PASS: login branding, favicon, admin/tenant editors, upload preview, validation, save, refresh and reset (mocked API)")
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    main()
