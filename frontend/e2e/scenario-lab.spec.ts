import { expect, test, type Page, type Route } from "@playwright/test";
const account = { id: "11111111-2222-4333-8444-555555555555", marketRegion: "INDIA", accountKind: "STANDARD", sandboxSlot: 0, accountLabel: "Standard India", currency: "INR", startingCapital: 500000, availableCash: 500000, blockedCash: 0, realizedProfitLoss: 0, active: true, leaderboardEligible: true };
const history = { currency: "INR", tier: "PRO", days: 30, benchmark: "NIFTY 50 price index", status: "COLLECTING", missingSessions: 0, cashFlowBreaks: 0, returnPercent: null, benchmarkReturnPercent: null, maximumDrawdownPercent: null, risk: { volatilityPercent: null, beta: null, correlation: null }, points: [] };
async function respond(route: Route, body: unknown, status = 200) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options ? undefined : JSON.stringify(body) });
}
async function setup(page: Page) {
  await page.route("**/api/v1/accounts/*/portfolio/history?*", route => respond(route, history));
  await page.route("**/api/v1/scenarios", route => respond(route, catalog));
  await page.addInitScript(account => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", expiresInSeconds: 900, user: { id: "history-user", email: "history@test.local", displayName: "History User", emailVerified: true, platformAdmin: false, accounts: [account] } })), account);
  await page.route("**/api/v1/accounts", route => respond(route, [account]));
  await page.route("**/api/v1/accounts/*/portfolio", route => respond(route, { ...account, investedValue: 0, marketValue: 0, unrealizedProfitLoss: 0, totalProfitLoss: 0, totalAccountValue: 500000, totalReturnPercent: 0, dataStatus: "CLOSED", valuedAt: "2026-09-24T23:40:00Z", holdings: [] }));
  await page.route("**/api/v1/accounts/*/portfolio/insights", route => respond(route, null));
}

const catalog = [
  { id: "selloff", title: "Market sell-off", version: 1, inputType: "SYNTHETIC", shocks: [-10, -20, -30], lesson: "Cash cushions a uniform fall in equity prices." },
  { id: "custom", title: "Custom price shock", version: 1, inputType: "SYNTHETIC", shocks: [-10], lesson: "Explore a single uniform price move." },
];
const result = { currency: "INR", valuedAt: "2026-09-24T10:00:00Z", scenario: catalog[0], cash: 200, startingInvestedValue: 800, startingEquity: 1000,
  projection: { returnPercent: -24, maximumDrawdownPercent: 24, steps: [{ step: 0, shockPercent: 0, equity: 1000, change: 0 }, { step: 1, shockPercent: -30, equity: 760, change: -240 }] },
  positions: [{ symbol: "TEST", exchange: "NSE", quantity: 8, priceTimestamp: "2026-09-24T10:00:00Z", startingValue: 800, endingValue: 560, change: -240 }],
  assumptions: ["Synthetic educational inputs; not historical events or forecasts.", "Cash and quantities stay fixed."] };

test("scenario result shows synthetic assumptions and exact step and holding values", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/accounts/*/scenarios", route => respond(route, result));
  await page.goto("/portfolio");
  const lab = page.getByRole("region", { name: "Scenario Lab", exact: true });
  await lab.getByRole("button", { name: "Run scenario", exact: true }).click();
  await expect(lab.getByRole("img", { name: "Hypothetical portfolio value by scenario step" })).toBeVisible();
  await expect(lab.getByText("760.00", { exact: true }).first()).toBeVisible();
  await expect(lab.getByText(/Synthetic educational inputs/)).toBeVisible();
  await lab.getByText("Impact on each holding", { exact: true }).click();
  await expect(lab.getByRole("cell", { name: "NSE:TEST", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Your portfolio", exact: true })).toBeVisible();
});
test("custom inputs are bounded and submitted with the selected version", async ({ page }) => {
  await setup(page);
  let submitted: unknown;
  await page.route("**/api/v1/accounts/*/scenarios", route => { if (route.request().method() === "POST") submitted = route.request().postDataJSON(); return respond(route, { ...result, scenario: { ...catalog[1], shocks: [-100] } }); });
  await page.goto("/portfolio");
  const lab = page.getByRole("region", { name: "Scenario Lab", exact: true });
  await lab.getByLabel("Scenario", { exact: true }).selectOption("custom");
  await lab.getByLabel("Price change (%)", { exact: true }).fill("-101");
  await expect(lab.getByRole("button", { name: "Run scenario", exact: true })).toBeDisabled();
  await lab.getByLabel("Price change (%)", { exact: true }).fill("-100");
  await lab.getByRole("button", { name: "Run scenario", exact: true }).click();
  await expect(lab.getByRole("heading", { name: "Custom price shock · Synthetic result" })).toBeVisible();
  expect(submitted).toEqual({ scenarioId: "custom", version: 1, customShockPercent: -100 });
});
test("Pro rejection and unavailable prices are visible without hiding the portfolio", async ({ page }) => {
  await setup(page);
  let denied = true;
  await page.route("**/api/v1/accounts/*/scenarios", route => respond(route, { message: denied ? "Scenario Lab requires active Pro benefits" : "Reliable prices are unavailable for one or more holdings." }, denied ? 403 : 409));
  await page.goto("/portfolio");
  const lab = page.getByRole("region", { name: "Scenario Lab", exact: true });
  await lab.getByRole("button", { name: "Run scenario", exact: true }).click();
  await expect(lab.getByRole("alert")).toContainText("active Pro benefits");
  await expect(lab.getByRole("link", { name: "View Pro plan" })).toBeVisible();
  denied = false;
  await lab.getByRole("button", { name: "Run scenario", exact: true }).click();
  await expect(lab.getByRole("alert")).toContainText("Reliable prices are unavailable");
  await expect(lab.getByRole("img")).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Your portfolio", exact: true })).toBeVisible();
});
