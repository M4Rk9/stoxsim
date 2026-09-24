import { expect, test, type Page, type Route } from "@playwright/test";
const account = { id: "11111111-2222-4333-8444-555555555555", marketRegion: "INDIA", accountKind: "STANDARD", sandboxSlot: 0, accountLabel: "Standard India", currency: "INR", startingCapital: 500000, availableCash: 500000, blockedCash: 0, realizedProfitLoss: 0, active: true, leaderboardEligible: true };
const history = { currency: "INR", tier: "PRO", days: 30, benchmark: "NIFTY 50 price index", status: "COLLECTING", missingSessions: 0, cashFlowBreaks: 0, returnPercent: null, benchmarkReturnPercent: null, maximumDrawdownPercent: null, risk: { volatilityPercent: null, beta: null, correlation: null }, points: [] };
async function respond(route: Route, body: unknown, status = 200) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options ? undefined : JSON.stringify(body) });
}
async function setup(page: Page) {
  await page.addInitScript(account => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", expiresInSeconds: 900, user: { id: "history-user", email: "history@test.local", displayName: "History User", emailVerified: true, platformAdmin: false, accounts: [account] } })), account);
  await page.route("**/api/v1/accounts", route => respond(route, [account]));
  await page.route("**/api/v1/accounts/*/portfolio", route => respond(route, { ...account, investedValue: 0, marketValue: 0, unrealizedProfitLoss: 0, totalProfitLoss: 0, totalAccountValue: 500000, totalReturnPercent: 0, dataStatus: "CLOSED", valuedAt: "2026-09-24T23:40:00Z", holdings: [] }));
  await page.route("**/api/v1/accounts/*/portfolio/insights", route => respond(route, null));
}
test("new history collects without fabricating past performance", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/accounts/*/portfolio/history?*", route => respond(route, history));
  await page.goto("/portfolio");
  await expect(page.getByRole("heading", { name: "Portfolio history", exact: true })).toBeVisible();
  await expect(page.getByText(/Your history is collecting/)).toBeVisible();
  await expect(page.getByRole("img", { name: /Portfolio and benchmark growth/ })).toHaveCount(0);
});
test("aligned comparison exposes dates and an accessible data table", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/accounts/*/portfolio/history?*", route => respond(route, { ...history, status: "LIMITED_HISTORY", returnPercent: 10, benchmarkReturnPercent: 5, maximumDrawdownPercent: 0, points: [
    { day: "2026-09-23", observedAt: "2026-09-23T23:40:00Z", equity: 500000, quality: "OBSERVED", portfolioIndex: 100, benchmarkIndex: 100 },
    { day: "2026-09-24", observedAt: "2026-09-24T23:40:00Z", equity: 550000, quality: "OBSERVED", portfolioIndex: 110, benchmarkIndex: 105 },
  ] }));
  await page.goto("/portfolio");
  await expect(page.getByRole("img", { name: /Portfolio and benchmark growth/ })).toBeVisible();
  await expect(page.getByText("10.00%", { exact: true })).toBeVisible();
  await page.getByText("View daily observations", { exact: true }).click();
  await expect(page.getByRole("cell", { name: "550000.00", exact: true })).toBeVisible();
});
test("premium rejection does not hide current portfolio and cash gaps are explicit", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/accounts/*/portfolio/history?*", route => route.request().url().includes("days=90") ? respond(route, { message: "Longer history requires active Plus or Pro benefits" }, 403) : respond(route, { ...history, status: "CASH_FLOW_BREAK", missingSessions: 1, cashFlowBreaks: 1 }));
  await page.goto("/portfolio");
  await expect(page.getByText(/Performance metrics are withheld/)).toBeVisible();
  await page.getByLabel("History period").selectOption("90");
  await expect(page.getByRole("alert")).toContainText("Longer history requires");
  await expect(page.getByRole("heading", { name: "Your portfolio", exact: true })).toBeVisible();
});
