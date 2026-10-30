import { test, expect, type Page, type Route } from "@playwright/test";

const API = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080";
const user = {
  id: "00000000-0000-0000-0000-000000000001", displayName: "Google Test Learner",
  email: "google-test@example.test", emailVerified: true, accounts: [],
};
const browserErrors = new WeakMap<Page, string[]>();
test.beforeEach(async ({ page }) => {
  const errors: string[] = [];
  browserErrors.set(page, errors);
  page.on("pageerror", error => errors.push(error.message));
  // Keep dashboard/telemetry requests independent of the mocked sign-in session.
  // Specific Google mocks registered in each test take precedence.
  await mock(page, `${API}/api/v1/**`, route => {
    const path = new URL(route.request().url()).pathname;
    return path === "/api/v1/auth/me" ? respond(route, { json: user }) : path === "/api/v1/accounts" ? respond(route, { json: [] })
      : path.startsWith("/api/v1/analytics/") ? respond(route, { status: 204 })
        : respond(route, { status: 404, json: {} });
  });
});
test.afterEach(async ({ page }) => {
  expect(browserErrors.get(page), "The Google flow must not crash the page").toEqual([]);
});
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
  await expect(page.getByRole("main").getByRole("alert")).toContainText("unavailable");
  await expect(page.getByRole("link", { name: "Return to StoxSim" })).toBeVisible();
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
    await respond(route, { json: { accessToken: "stoxsim-access-token", tokenType: "Bearer", expiresInSeconds: 900, user } });
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
  await page.addInitScript(user => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "existing-token", tokenType: "Bearer", expiresInSeconds: 900, user })), user);
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
  await page.goto("/google-sign-in/manage");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(page.getByRole("button", { name: "Test Google account" })).toHaveCount(0);
  await page.getByLabel("Current StoxSim password").fill("local-password");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("button", { name: "Test Google account" }).click();
  await expect(page.getByRole("status")).toContainText("Google is linked");
  expect(linked).toBe(true);
});

async function enabledGoogle(page: Page) {
  await mock(page, "https://accounts.google.com/gsi/client", route => respond(route, { contentType: "application/javascript", body: googleScript }));
  await mock(page, `${API}/api/v1/auth/google/config`, route => respond(route, { json: { clientId: "test.apps.googleusercontent.com" } }));
  await mock(page, `${API}/api/v1/auth/google/challenge`, route => respond(route, { json: { nonce: "test-nonce" } }));
}

test("Google signup requires explicit consent without requesting a password", async ({ page }) => {
  await enabledGoogle(page);
  let registered = false;
  await mock(page, `${API}/api/v1/auth/google/register`, async route => {
    expect(route.request().postDataJSON()).toEqual({ credential: "test-credential", displayName: "New Learner", termsAccepted: true });
    registered = true;
    await respond(route, { status: 201, json: { accessToken: "new-access", expiresInSeconds: 900, user: { ...user, passwordLoginEnabled: false } } });
  });
  const response = await page.goto("/google-sign-in/register");
  expect(response!.headers()["content-security-policy"]).toContain("https://accounts.google.com/gsi/");
  await page.getByLabel("Display name").fill("New Learner");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(page.getByRole("button", { name: "Test Google account" })).toHaveCount(0);
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("button", { name: "Test Google account" }).click();
  await page.waitForURL("/");
  expect(registered).toBe(true);
});

async function passwordlessManagement(page: Page) {
  await page.addInitScript(user => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "existing-token", expiresInSeconds: 900, user })), { ...user, passwordLoginEnabled: false });
  await enabledGoogle(page);
  await mock(page, `${API}/api/v1/auth/me`, route => respond(route, { json: { ...user, passwordLoginEnabled: false } }));
  await mock(page, `${API}/api/v1/auth/google/link`, route => respond(route, { json: { linked: true } }));
  await page.goto("/google-sign-in/manage");
}

test("Google-only account adds a password after Google confirmation and clears its session", async ({ page }) => {
  await passwordlessManagement(page);
  await mock(page, `${API}/api/v1/auth/google/password`, async route => {
    expect(route.request().headers().authorization).toBe("Bearer existing-token");
    expect(route.request().postDataJSON()).toEqual({ credential: "test-credential", newPassword: "new-local-password" });
    await respond(route, { status: 204 });
  });
  await page.getByLabel("New password", { exact: true }).fill("new-local-password");
  await page.getByLabel("Confirm new password").fill("new-local-password");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("button", { name: "Test Google account" }).click();
  await expect(page.getByRole("status")).toContainText("Password added");
  expect(await page.evaluate(() => sessionStorage.getItem("stoxsim-session"))).toBeNull();
});

test("Google-only deletion needs explicit destructive confirmation and a fresh Google credential", async ({ page }) => {
  await passwordlessManagement(page);
  await mock(page, `${API}/api/v1/auth/google/delete`, async route => {
    expect(route.request().postDataJSON()).toEqual({ credential: "test-credential", confirmed: true });
    await respond(route, { status: 204 });
  });
  await page.getByLabel("Permanently delete my account instead").check();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(page.getByRole("button", { name: "Test Google account" })).toHaveCount(0);
  await page.getByLabel("I understand that my account and simulation data will be permanently deleted.").check();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("button", { name: "Test Google account" }).click();
  await expect(page.getByRole("status")).toContainText("Your account has been deleted");
  expect(await page.evaluate(() => sessionStorage.getItem("stoxsim-session"))).toBeNull();
});

test("failed Google reauthentication does not replay a consumed challenge", async ({ page }) => {
  await passwordlessManagement(page);
  let attempts = 0;
  await mock(page, `${API}/api/v1/auth/google/password`, async route => {
    attempts++;
    await respond(route, { status: 401, json: { message: "Choose the Google account linked to this StoxSim account." } });
  });
  await mock(page, `${API}/api/v1/auth/refresh`, route => respond(route, { json: { accessToken: "refreshed", expiresInSeconds: 900, user } }));
  await page.getByLabel("New password", { exact: true }).fill("new-local-password");
  await page.getByLabel("Confirm new password").fill("new-local-password");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("button", { name: "Test Google account" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Choose the Google account linked");
  expect(attempts).toBe(1);
});

test("home registration offers Google signup directly", async ({ page }) => {
  await enabledGoogle(page);
  await page.goto("/");
  const link = page.getByRole("link", { name: "Sign up with Google" });
  await expect(link).toBeVisible();
  await expect(link).toHaveAttribute("href", "/google-sign-in/register");
});
