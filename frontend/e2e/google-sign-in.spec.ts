import { test, expect, type Page, type Route } from "@playwright/test";

const API = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080";
async function respond(route: Route, options: Parameters<Route["fulfill"]>[0]) {
  return route.fulfill({ ...options, headers: {
    "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
    "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS", ...options?.headers,
  } });
}
async function mock(page: Page, url: string, handler: (route: Route) => Promise<void>) {
  await page.route(url, route => route.request().method() === "OPTIONS"
    ? respond(route, { status: 204 }) : handler(route));
}
const googleScript = `
  window.google = { accounts: { id: {
    initialize(config) { window.googleConfig = config; },
    renderButton(element) {
      const button = document.createElement('button'); button.textContent = 'Test Google account';
      button.addEventListener('click', () => window.googleConfig.callback({ credential: 'test-credential' }));
      element.appendChild(button);
    }, cancel() {}
  } } };
`;

test("disabled Google sign-in loads no provider script and preserves password login", async ({ page }) => {
  let loaded = false;
  await mock(page, "https://accounts.google.com/**", route => { loaded = true; return route.abort(); });
  await mock(page, `${API}/api/v1/auth/google/config`, route => respond(route, { status: 404, json: {} }));
  await page.goto("/google-sign-in");
  await expect(page.getByRole("alert")).toContainText("unavailable");
  await expect(page.getByRole("link", { name: "Use email and password" })).toBeVisible();
  expect(loaded).toBe(false);
});

test("Google credential exchange keeps tokens out of URLs and uses the browser nonce", async ({ page }) => {
  await mock(page, "https://accounts.google.com/gsi/client", route => respond(route, { contentType: "application/javascript", body: googleScript }));
  await mock(page, `${API}/api/v1/auth/google/config`, route => respond(route, { json: { clientId: "test.apps.googleusercontent.com" } }));
  await mock(page, `${API}/api/v1/auth/google/challenge`, route => respond(route, { json: { nonce: "test-nonce" } }));
  let exchanged = false;
  await mock(page, `${API}/api/v1/auth/google/login`, async route => {
    expect(route.request().postDataJSON()).toEqual({ credential: "test-credential" });
    exchanged = true;
    await respond(route, { json: { accessToken: "stoxsim-access-token", user: { id: "test-user", accounts: [] } } });
  });
  const response = await page.goto("/google-sign-in");
  expect(response!.headers()["content-security-policy"]).toContain("https://accounts.google.com/gsi/");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(page.getByRole("button", { name: "Test Google account" })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { googleConfig: { nonce: string } }).googleConfig.nonce)).toBe("test-nonce");
  await page.getByRole("button", { name: "Test Google account" }).click();
  await page.waitForURL("/");
  expect(exchanged).toBe(true);
  expect(page.url()).not.toContain("token");
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem("stoxsim-session")!).accessToken)).toBe("stoxsim-access-token");
});

test("linking requires a local password and sends it only to StoxSim", async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "existing-token", user: { id: "test-user" } })));
  await mock(page, "https://accounts.google.com/gsi/client", route => respond(route, { contentType: "application/javascript", body: googleScript }));
  await mock(page, `${API}/api/v1/auth/google/config`, route => respond(route, { json: { clientId: "test.apps.googleusercontent.com" } }));
  await mock(page, `${API}/api/v1/auth/google/challenge`, route => respond(route, { json: { nonce: "test-nonce" } }));
  let linked = false;
  await mock(page, `${API}/api/v1/auth/google/link`, async route => {
    if (route.request().method() === "POST") {
      expect(route.request().headers().authorization).toBe("Bearer existing-token");
      expect(route.request().postDataJSON()).toEqual({ credential: "test-credential", password: "local-password" });
      linked = true;
      await respond(route, { status: 204 });
    } else await respond(route, { json: { linked: false } });
  });
  await page.goto("/google-sign-in?manage=1");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(page.getByRole("button", { name: "Test Google account" })).toHaveCount(0);
  await page.getByLabel("Current StoxSim password").fill("local-password");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("button", { name: "Test Google account" }).click();
  await expect(page.getByRole("status")).toContainText("Google is linked");
  expect(linked).toBe(true);
});
