import { expect, test, type Page, type Route } from "@playwright/test";

const entry = { id: "21212121-2121-4121-8121-212121212121", plan: "PLUS", providerId: "sub_fixture", status: "created", currentPeriodEnd: null, paidCount: 0 };
async function respond(route: Route, status: number, body: unknown) {
  await route.fulfill({ status: route.request().method() === "OPTIONS" ? 204 : status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
      "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" },
    body: route.request().method() === "OPTIONS" ? undefined : JSON.stringify(body) });
}
async function session(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({
    accessToken: "fixture", expiresInSeconds: 900,
    user: { id: "admin", email: "admin@billing.test", displayName: "Billing Admin", emailVerified: true, platformAdmin: true, accounts: [] },
  })));
}
test("disabled and forbidden responses never offer checkout", async ({ page }) => {
  await session(page);
  let forbidden = false;
  await page.route("**/api/v1/billing/test", route => respond(route, forbidden ? 403 : 200,
    forbidden ? { message: "Administrator access required" } : { enabled: false, mode: "TEST", keyId: null, entries: [] }));
  await page.goto("/admin/billing");
  await expect(page.getByRole("heading", { name: "Test checkout is disabled" })).toBeVisible();
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  forbidden = true;
  await page.getByRole("button", { name: "Reload billing" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Administrator access required");
  await expect(page.getByRole("button", { name: "Test Plus", exact: true })).toHaveCount(0);
});
test("consent gates checkout and browser success does not mark payment confirmed", async ({ page }) => {
  await session(page);
  let created = false;
  let requestKey = "";
  await page.route("**/api/v1/billing/test", route => respond(route, 200, { enabled: true, mode: "TEST", keyId: "rzp_test_fixture", entries: created ? [entry] : [] }));
  await page.route("**/api/v1/billing/test/subscriptions", async route => {
    if (route.request().method() === "POST") { created = true; requestKey = route.request().postDataJSON().requestKey; }
    await respond(route, 200, entry);
  });
  await page.route("https://checkout.razorpay.com/v1/checkout.js", route => route.fulfill({ contentType: "application/javascript",
    body: 'window.Razorpay = class { constructor(options) { this.options=options; } on() {} open() { this.options.handler({razorpay_payment_id:"untrusted"}); } };' }));
  await page.goto("/admin/billing");
  await expect(page.getByRole("button", { name: "Test Plus", exact: true })).toBeDisabled();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Test Plus", exact: true }).click();
  await expect(page.getByText("Checkout returned successfully. Refresh status to verify it with Razorpay.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "PLUS · created" })).toBeVisible();
  await expect(page.getByText("Confirmed payments: 0")).toBeVisible();
  expect(requestKey).toMatch(/^[0-9a-f-]{36}$/);
  await expect(page.getByRole("button", { name: "Test Pro", exact: true })).toBeDisabled();
});
test("pending creation can be reconciled and cancellation requires confirmation", async ({ page }) => {
  await session(page);
  let current = { ...entry, providerId: null as string | null, status: "CREATING" };
  await page.route("**/api/v1/billing/test", route => respond(route, 200, { enabled: true, mode: "TEST", keyId: "rzp_test_fixture", entries: [current] }));
  await page.route("**/api/v1/billing/test/subscriptions/*/reconcile", async route => {
    if (route.request().method() === "POST") { expect(route.request().postDataJSON().providerId).toBe("sub_fixture"); current = { ...entry }; }
    await respond(route, 200, current);
  });
  await page.route("**/api/v1/billing/test/subscriptions/*/cancel", async route => {
    if (route.request().method() === "POST") current = { ...entry, status: "cancelled" };
    await respond(route, 200, current);
  });
  await page.goto("/admin/billing");
  page.once("dialog", dialog => dialog.accept("sub_fixture"));
  await page.getByRole("button", { name: "Reconcile test subscription" }).click();
  await expect(page.getByRole("heading", { name: "PLUS · created" })).toBeVisible();
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Cancel test subscription", exact: true }).click();
  await expect(page.getByRole("heading", { name: "PLUS · cancelled" })).toBeVisible();
});

test("test benefits need explicit confirmation and show verified access state", async ({ page }) => {
  await session(page);
  let current = { ...entry, status: "active", paidCount: 1, benefitsEnabled: false, benefitStatus: "OFF", accessUntil: null as string | null };
  let mutations = 0;
  await page.route("**/api/v1/billing/test", route => respond(route, 200, { enabled: true, mode: "TEST", keyId: "rzp_test_fixture", entries: [current] }));
  await page.route("**/api/v1/billing/test/subscriptions/*/benefits", async route => {
    if (route.request().method() === "POST") {
      mutations++;
      const enabled = route.request().postDataJSON().enabled;
      current = { ...current, benefitsEnabled: enabled, benefitStatus: enabled ? "ACTIVE" : "OFF", accessUntil: enabled ? "2099-01-01T00:00:00Z" : null };
    }
    await respond(route, 200, current);
  });
  await page.goto("/admin/billing");
  await expect(page.getByText("Test benefits: OFF", { exact: true })).toBeVisible();
  page.once("dialog", dialog => dialog.dismiss());
  await page.getByRole("button", { name: "Enable test benefits", exact: true }).click();
  expect(mutations).toBe(0);
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Enable test benefits", exact: true }).click();
  await expect(page.getByText("Test benefits: ACTIVE", { exact: true })).toBeVisible();
  await expect(page.getByText(/Sandbox access until:/)).toBeVisible();
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Disable test benefits", exact: true }).click();
  await expect(page.getByText("Test benefits: OFF", { exact: true })).toBeVisible();
  expect(mutations).toBe(2);
});
