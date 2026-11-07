import { expect, test, type Page, type Route } from "@playwright/test";

const entry = { id: "21212121-2121-4121-8121-212121212121", provider: "RAZORPAY", plan: "PLUS", providerReference: "sub_fixture", status: "CREATED", currentPeriodEnd: null, accessUntil: null, cancellationStatus: "NONE", managementUrl: null };
const catalog = { plan: "FREE", plans: ["FREE", "PLUS", "PRO"].map((plan, i) => ({ plan, displayName: ["Free", "Plus", "Pro"][i], monthlyPriceInr: [0, 99, 199][i], entitlements: { finwizTier: "BASIC", analyticsTier: "BASIC", scenarioCredits: 3 } })) };
async function respond(route: Route, body: unknown, status = 200) {
  const preflight = route.request().method() === "OPTIONS";
  await route.fulfill({ status: preflight ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: preflight ? undefined : JSON.stringify(body) });
}
async function signedIn(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "billing-user", email: "billing@example.test", displayName: "Billing Learner", emailVerified: true, accounts: [] } })));
  await page.route("**/api/v1/subscription", route => respond(route, catalog));
}
test("disabled billing never opens provider checkout", async ({ page }) => {
  await signedIn(page); let checkoutCalls = 0;
  await page.route("**/api/v1/billing", route => respond(route, { enabled: false, razorpayEnabled: false, googlePlayEnabled: false, keyId: null, entries: [] }));
  page.on("request", req => { if (new URL(req.url()).hostname === "checkout.razorpay.com") checkoutCalls++; });
  await page.goto("/billing");
  await expect(page.getByRole("button", { name: "Choose Plus", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Choose Pro", exact: true })).toBeDisabled();
  await expect(page.getByText("₹99/month")).toBeVisible(); await expect(page.getByText("₹199/month")).toBeVisible();
  expect(checkoutCalls).toBe(0);
});

test("untrusted checkout success refreshes provider state without granting access", async ({ page }) => {
  await signedIn(page); let created = false, refreshes = 0, requestKey = "";
  await page.route("**/api/v1/billing", route => respond(route, { enabled: true, razorpayEnabled: true, googlePlayEnabled: false, keyId: "rzp_test_fixture", entries: created ? [entry] : [] }));
  await page.route("**/api/v1/billing/subscriptions", async route => { if (route.request().method() === "POST") { created = true; requestKey = route.request().postDataJSON().requestKey; } await respond(route, entry); });
  await page.route("**/api/v1/billing/subscriptions/*/refresh", async route => { if (route.request().method() === "POST") refreshes++; await respond(route, entry); });
  await page.route("https://checkout.razorpay.com/v1/checkout.js", route => route.fulfill({ contentType: "application/javascript", body: 'window.Razorpay = class { constructor(options) { this.options=options; } on() {} open() { this.options.handler({razorpay_payment_id:"forged"}); } };' }));
  await page.goto("/billing");
  await expect(page.getByRole("button", { name: "Choose Plus", exact: true })).toBeDisabled();
  await page.getByRole("checkbox").check(); await page.getByRole("button", { name: "Choose Plus", exact: true }).click();
  await expect(page.getByRole("status").filter({ hasText: "Provider status refreshed" })).toBeVisible();
  expect(refreshes).toBe(1); expect(requestKey).toMatch(/^[0-9a-f-]{36}$/);
  await expect(page.getByRole("heading", { name: "PLUS · created" })).toBeVisible();
  const free = page.getByRole("region", { name: "Compare plans" }).locator("article").filter({ has: page.getByRole("heading", { name: "Free", exact: true }) });
  await expect(free).toContainText("Current plan");
  expect(await page.evaluate(() => sessionStorage.getItem("stoxsim-checkout:billing-user:PLUS"))).toBe(requestKey);
});

test("pending cancellation remains visibly pending and requires explicit confirmation", async ({ page }) => {
  await signedIn(page); let current = { ...entry, status: "ACTIVE", accessUntil: "2099-01-01T00:00:00Z", cancellationStatus: "NONE" }; let cancels = 0;
  await page.route("**/api/v1/billing", route => respond(route, { enabled: true, razorpayEnabled: true, googlePlayEnabled: false, keyId: "rzp_test_fixture", entries: [current] }));
  await page.route("**/api/v1/billing/subscriptions/*/cancel", async route => { if (route.request().method() === "POST") { cancels++; current = { ...current, cancellationStatus: "REQUESTED" }; } await respond(route, current); });
  await page.goto("/billing");
  page.once("dialog", dialog => dialog.dismiss()); await page.getByRole("button", { name: "Cancel renewal", exact: true }).click(); expect(cancels).toBe(0);
  page.once("dialog", dialog => dialog.accept()); await page.getByRole("button", { name: "Cancel renewal", exact: true }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Renewal may still occur"); expect(cancels).toBe(1);
  await expect(page.getByRole("button", { name: "Retry cancellation" })).toBeVisible();
});

test("pending creation prevents duplicate checkout and unsafe management links stay hidden", async ({ page }) => {
  await signedIn(page);
  await page.route("**/api/v1/billing", route => respond(route, { enabled: true, razorpayEnabled: true, googlePlayEnabled: true, keyId: "rzp_test_fixture", entries: [{ ...entry, status: "CREATING", providerReference: null }, { ...entry, id: "other", provider: "GOOGLE_PLAY", managementUrl: "https://evil.example/" }] }));
  await page.goto("/billing"); await page.getByRole("checkbox").check();
  await expect(page.getByRole("button", { name: "Choose Plus", exact: true })).toBeDisabled();
  await expect(page.getByText("Awaiting provider verification. Your checkout attempt is preserved.")).toBeVisible();
  await expect(page.getByRole("link", { name: "Manage in Google Play" })).toHaveCount(0);
});
