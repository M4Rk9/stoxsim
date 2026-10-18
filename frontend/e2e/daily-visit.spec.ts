import { expect, test, type Page } from "@playwright/test";
const learner = { accessToken: "token-a", expiresInSeconds: 900, user: { id: "learner-a", email: "learner@example.test", displayName: "Learner", emailVerified: true, platformAdmin: false, accounts: [] } };
async function setup(page: Page, failFirst = false) {
  const calls: string[] = [];
  await page.clock.install({ time: new Date("2026-10-15T18:29:00Z") });
  await page.addInitScript(value => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify(value));
    window.addEventListener("stoxsim-daily-visit", () => { document.documentElement.dataset.visitRecorded = "yes"; });
  }, learner);
  await page.route("**/api/v1/analytics/events", route => route.fulfill({ status: 204 }));
  await page.route("**/api/v1/progression/check-in", async route => {
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers: { "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Headers": "authorization", "Access-Control-Allow-Methods": "POST", "Access-Control-Allow-Credentials": "true" } });
    calls.push(route.request().headers().authorization);
    await route.fulfill({ status: failFirst && calls.length === 1 ? 503 : 200, headers: { "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Content-Type": "application/json" }, body: JSON.stringify({ checkedInToday: true, lastCheckInDate: calls.length === 1 ? "2026-10-15" : "2026-10-16", checkInZoneId: "Asia/Kolkata" }) });
  });
  return calls;
}
test("opening a signed-in page records a visit, respects server midnight and isolates accounts", async ({ page }) => {
  const calls = await setup(page);
  await page.goto("/learn-stock-trading");
  await expect.poll(() => calls.length).toBe(1);
  await page.waitForFunction(() => document.documentElement.dataset.visitRecorded === "yes");
  await page.clock.runFor(45_000);
  expect(calls).toHaveLength(1);
  await page.clock.runFor(15_000);
  await expect.poll(() => calls.length).toBe(2); // Local midnight, though UTC day is unchanged.
  await page.clock.runFor(60_000);
  expect(calls).toHaveLength(2);
  await page.evaluate(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "token-b", expiresInSeconds: 900, user: { id: "learner-b", email: "other@example.test", displayName: "Other learner", emailVerified: true, platformAdmin: false, accounts: [] } })));
  await page.clock.runFor(15_000);
  await expect.poll(() => calls.length).toBe(3);
  expect(calls[2]).toBe("Bearer token-b");
  await page.evaluate(() => sessionStorage.removeItem("stoxsim-session"));
  await page.clock.runFor(120_000);
  expect(calls).toHaveLength(3);
});
test("failed recording retries after a minute and hidden pages do not record", async ({ page }) => {
  const calls = await setup(page, true);
  await page.goto("/learn-stock-trading");
  await expect.poll(() => calls.length).toBe(1);
  await page.evaluate(() => Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "hidden" }));
  await page.clock.runFor(120_000);
  expect(calls).toHaveLength(1);
  await page.evaluate(() => { Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "visible" }); document.dispatchEvent(new Event("visibilitychange")); });
  await expect.poll(() => calls.length).toBe(2);
});

test("a cached visit cannot overwrite more recent mission XP", async ({ page }) => {
  await page.clock.install({ time: new Date("2026-10-15T19:00:00Z") });
  const progression = { version: "learning-progression-v1", totalXp: 100, level: 1, levelName: "Starter", levelFloorXp: 0, nextLevelXp: 200,
    currentStreak: 1, longestStreak: 1, checkedInToday: true, lastCheckInDate: "2026-10-16", checkInZoneId: "Asia/Kolkata", challenges: [], achievements: [], disclaimer: "Education" };
  await page.addInitScript(value => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify(value));
    window.addEventListener("stoxsim-daily-visit", () => { document.documentElement.dataset.visitRecorded = "yes"; });
  }, learner);
  await page.route("**/api/v1/**", async route => {
    const headers = { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" };
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    if (route.request().url().endsWith("/progression/check-in")) return route.fulfill({ status: 200, headers, body: JSON.stringify({ ...progression, totalXp: 50 }) });
    if (route.request().url().endsWith("/progression/visit")) {
      expect(route.request().method()).toBe("POST");
      await page.waitForFunction(() => document.documentElement.dataset.visitRecorded === "yes");
      return route.fulfill({ status: 200, headers, body: JSON.stringify(progression) });
    }
    return route.fulfill({ status: 204, headers });
  });
  await page.goto("/progress");
  await expect(page.getByText("100 XP", { exact: true })).toBeVisible();
  await expect(page.getByText("Today’s visit recorded", { exact: true })).toBeVisible();
});
