import { expect, test, type Page, type Route } from "@playwright/test";

const fixture = {
  version: "owner-analytics-v1", from: "2020-01-01", to: "2020-01-02", timeZone: "UTC",
  generatedAt: "2026-09-21T12:00:00Z",
  users: { registered: 42, emailVerified: 30 },
  cohort: { registered: 4, firstTradeCompleted: 1, firstTradePercent: 25 },
  signups: [{ date: "2020-01-01", registered: 3 }, { date: "2020-01-02", registered: 1 }],
  orders: [{ marketRegion: "INDIA", status: "EXECUTED", count: 9 }],
};

async function respond(route: Route, status: number, body: unknown) {
  const origin = route.request().headers().origin ?? "http://localhost:3000";
  await route.fulfill({
    status: route.request().method() === "OPTIONS" ? 204 : status,
    headers: {
      "Content-Type": "application/json", "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    },
    body: route.request().method() === "OPTIONS" ? undefined : JSON.stringify(body),
  });
}

async function session(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "test-session", user: { platformAdmin: true } }));
  });
}

test("owner sees aggregate metrics, exact daily counts and updated date range", async ({ page }) => {
  await session(page);
  const dates: string[] = [];
  await page.route("**/api/v1/admin/analytics/overview?**", async route => {
    if (route.request().method() !== "OPTIONS") dates.push(new URL(route.request().url()).search);
    await respond(route, 200, fixture);
  });
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Owner analytics" })).toBeVisible();
  await expect(page.locator("article").filter({ hasText: "First-trade conversion" })).toContainText("25.0%");
  await expect(page.locator("article").filter({ hasText: "Registered learners" })).toContainText("42");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await page.getByText("View daily counts", { exact: true }).click();
  await expect(page.getByRole("table", { name: "Registrations by UTC date" })).toBeVisible();
  await page.getByLabel("From (UTC)").fill("2020-01-01");
  await page.getByLabel("To (UTC)").fill("2020-01-02");
  await page.getByRole("button", { name: "Apply dates" }).click();
  await expect.poll(() => dates.at(-1)).toBe("?from=2020-01-01&to=2020-01-02");
  await expect(page.getByRole("button", { name: "Apply dates" })).toBeEnabled();
});

test("learner cannot view metrics by forging an admin flag in browser storage", async ({ page }) => {
  await session(page);
  await page.route("**/api/v1/admin/analytics/overview?**", route => respond(route, 403, { message: "Forbidden" }));
  await page.goto("/admin/analytics");
  await expect(page.getByRole("alert")).toContainText("platform administrators only");
  await expect(page.getByRole("heading", { name: "Current learner accounts" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Back to dashboard" })).toBeVisible();
});

test("empty results show a dash for conversion, never a fabricated percentage", async ({ page }) => {
  await session(page);
  await page.route("**/api/v1/admin/analytics/overview?**", route => respond(route, 200, {
    ...fixture, users: { registered: 0, emailVerified: 0 },
    cohort: { registered: 0, firstTradeCompleted: 0, firstTradePercent: null },
    signups: fixture.signups.map(day => ({ ...day, registered: 0 })), orders: [],
  }));
  await page.goto("/admin/analytics");
  await expect(page.getByText("No learner registrations in this period.")).toBeVisible();
  await expect(page.locator("article").filter({ hasText: "First-trade conversion" })).toContainText("—");
  await expect(page.getByText("No standard-account orders in this period.")).toBeVisible();
});

test("failed reload removes old metrics and offers a retry", async ({ page }) => {
  await session(page);
  let fail = false;
  await page.route("**/api/v1/admin/analytics/overview?**", route => respond(route, fail ? 503 : 200,
    fail ? { message: "Temporarily unavailable" } : fixture));
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Current learner accounts" })).toBeVisible();
  fail = true;
  await page.getByRole("button", { name: "Apply dates" }).click();
  await expect(page.getByRole("alert")).toContainText("Temporarily unavailable");
  await expect(page.getByRole("heading", { name: "Current learner accounts" })).toHaveCount(0);
  fail = false;
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByRole("heading", { name: "Current learner accounts" })).toBeVisible();
});

test("new tab recovers the HttpOnly session and expired sessions show sign in", async ({ page }) => {
  await page.route("**/api/v1/auth/refresh", route => respond(route, 200, { accessToken: "refreshed-session", user: {} }));
  await page.route("**/api/v1/admin/analytics/overview?**", route => respond(route, 200, fixture));
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Current learner accounts" })).toBeVisible();
  await page.route("**/api/v1/admin/analytics/overview?**", route => respond(route, 401, {}));
  await page.route("**/api/v1/auth/refresh", route => respond(route, 401, {}));
  await page.getByRole("button", { name: "Apply dates" }).click();
  await expect(page.getByRole("link", { name: "Go to sign in" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Current learner accounts" })).toHaveCount(0);
});

for (const theme of ["light", "dark"]) {
  test(`analytics fits a narrow viewport in ${theme} mode`, async ({ page }) => {
    await session(page);
    await page.setViewportSize({ width: 375, height: 812 });
    await page.addInitScript(selected => localStorage.setItem("stoxsim-theme", selected), theme);
    await page.route("**/api/v1/admin/analytics/overview?**", route => respond(route, 200, fixture));
    await page.goto("/admin/analytics");
    // Set the established theme attribute explicitly to exercise this page's styles.
    await page.evaluate(selected => document.documentElement.dataset.theme = selected, theme);
    await expect(page.getByRole("heading", { name: "Current learner accounts" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByLabel("From (UTC)").focus();
    await expect(page.getByLabel("From (UTC)")).toBeFocused();
  });
}
