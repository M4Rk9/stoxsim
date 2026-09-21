import { expect, test, type Page, type Route } from "@playwright/test";

const owner = { accessToken: "test-token", expiresInSeconds: 900,
  user: { id: "owner", email: "owner@stoxsim.test", displayName: "Owner", emailVerified: true, platformAdmin: true, accounts: [] } };
const overview = { version: "owner-analytics-v1", from: "2026-08-01", to: "2026-08-31", timeZone: "UTC",
  generatedAt: "2026-09-21T12:00:00Z", users: { registered: 42, emailVerified: 30 },
  cohort: { registered: 10, firstTradeCompleted: 5, firstTradePercent: 50 }, signups: [], orders: [] };
const activity = { version: "product-activity-v1", trackingStartedAt: "2026-08-01T12:00:00Z",
  availableFrom: "2026-08-01T12:00:00Z", generatedAt: "2026-09-21T12:00:00Z",
  activeUsers: { dau: 4, wau: 8, mau: null }, daily: [{ date: "2026-08-01", learners: null }, { date: "2026-08-02", learners: 4 }],
  activation: { eligible: 10, immature: 2, excluded: 3, researched: 8, watchlisted: 6, activated: 5, executed: 4, percent: 50, medianHours: 6.5 },
  retention: [{ day: 1, eligible: 10, returned: 5, immature: 2, excluded: 3, percent: 50 },
    { day: 7, eligible: 8, returned: 2, immature: 4, excluded: 3, percent: 25 },
    { day: 30, eligible: 0, returned: 0, immature: 12, excluded: 3, percent: null }] };
async function respond(route: Route, status: number, body: unknown) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
      "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS" },
    body: options || status === 204 ? undefined : JSON.stringify(body) });
}
async function setup(page: Page) {
  await page.addInitScript(value => sessionStorage.setItem("stoxsim-session", JSON.stringify(value)), owner);
  await page.route("**/api/v1/admin/analytics/overview?**", route => respond(route, 200, overview));
  await page.route("**/api/v1/admin/analytics/activity?**", route => respond(route, 200, activity));
}

test("activity shows distinct windows, mature funnel and individual retention denominators", async ({ page }) => {
  await setup(page);
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Activity and retention", exact: true })).toBeVisible();
  await expect(page.locator("article").filter({ hasText: "Daily active learners" })).toContainText("4");
  await expect(page.locator("article").filter({ hasText: "Monthly active learners" })).toContainText("—");
  await expect(page.getByText("Full window not observed.", { exact: false })).toBeVisible();
  const retention = page.getByRole("table", { name: "Retention for registrations in the selected dates" });
  await expect(retention.getByRole("row").filter({ hasText: "D7" })).toContainText("25.0%");
  await expect(retention.getByRole("row").filter({ hasText: "D30" })).toContainText("—");
  await expect(page.getByText("6.5 hours", { exact: true })).toBeVisible();
  await page.getByText("View daily active learners", { exact: true }).click();
  await expect(page.getByRole("table", { name: "Distinct active learners by UTC date; — means unobserved" })).toBeVisible();
});

test("new deployment explains immature cohorts instead of showing zero retention", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/admin/analytics/activity?**", route => respond(route, 200, { ...activity,
    activeUsers: { dau: null, wau: null, mau: null }, activation: { ...activity.activation, eligible: 0, activated: 0, percent: null, medianHours: null } }));
  await page.goto("/admin/analytics");
  await expect(page.getByText("No fully observed, mature activation cohort yet.")).toBeVisible();
});

test("activity reload failure clears stale metrics and retries without losing M1", async ({ page }) => {
  await setup(page);
  let fail = false;
  await page.route("**/api/v1/admin/analytics/activity?**", route => respond(route, fail ? 503 : 200, activity));
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Activity and retention", exact: true })).toBeVisible();
  fail = true;
  await page.getByRole("button", { name: "Apply dates" }).click();
  await expect(page.getByRole("button", { name: "Retry activity analytics" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Activity and retention", exact: true })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Current learner accounts" })).toBeVisible();
  fail = false;
  await page.getByRole("button", { name: "Retry activity analytics" }).click();
  await expect(page.getByRole("heading", { name: "Activity and retention", exact: true })).toBeVisible();
});

for (const theme of ["light", "dark"]) test(`activity tables fit mobile in ${theme} mode`, async ({ page }) => {
  await setup(page);
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(value => localStorage.setItem("stoxsim-theme:owner", value), theme);
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Return-day retention" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("learner capture is minimal, deduplicates interactions and records loaded research", async ({ page }) => {
  const learner = { ...owner, user: { ...owner.user, id: "learner", platformAdmin: false } };
  await page.addInitScript(value => sessionStorage.setItem("stoxsim-session", JSON.stringify(value)), learner);
  const events: unknown[] = [];
  await page.route("**/api/v1/analytics/events", async route => {
    if (route.request().method() === "POST") events.push(route.request().postDataJSON());
    await respond(route, 204, null);
  });
  await page.route("**/api/v1/instruments/**", route => {
    const url = route.request().url();
    return respond(route, 200, url.includes("/quote") ? { lastPrice: 200, previousClose: 199, dataStatus: "LIVE" }
      : url.includes("/candles") ? { candles: [] }
      : url.includes("/insights") ? { provider: "TEST", asOf: "2026-09-21T12:00:00Z", status: "UNAVAILABLE", ratios: [], financials: { metrics: [] } }
      : { provider: "ALPACA", currency: "USD", tradingSymbol: "AAPL", name: "Apple", exchange: "NASDAQ", instrumentType: "EQUITY" });
  });
  await page.goto("/stocks/NASDAQ/AAPL");
  await expect(page.getByRole("heading", { name: "AAPL", exact: true })).toBeVisible();
  await expect.poll(() => events.length).toBe(2);
  expect(events).toEqual(expect.arrayContaining([{ version: 1, event: "ACTIVE" }, { version: 1, event: "STOCK_OPENED" }]));
  await page.getByRole("heading", { name: "AAPL", exact: true }).click();
  await page.keyboard.press("Tab");
  expect(events).toHaveLength(2);
});

test("anonymous visitors and owners emit no product activity", async ({ page }) => {
  const events: unknown[] = [];
  await page.route("**/api/v1/analytics/events", async route => {
    if (route.request().method() === "POST") events.push(route.request().postDataJSON());
    await respond(route, 204, null);
  });
  await page.goto("/privacy");
  await page.getByRole("heading", { name: "Privacy Notice", exact: true }).click();
  expect(events).toHaveLength(0);
  await setup(page);
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Activity and retention", exact: true })).toBeVisible();
  await page.keyboard.press("Tab");
  expect(events).toHaveLength(0);
});
