import { expect, test, type Page, type Route } from "@playwright/test";
const india = { id: "11111111-2222-4333-8444-555555555555", marketRegion: "INDIA", accountKind: "STANDARD", sandboxSlot: 0, active: true };
const us = { ...india, id: "22222222-2222-4333-8444-555555555555", marketRegion: "UNITED_STATES" };
const usPro = { ...us, id: "33333333-2222-4333-8444-555555555555", accountKind: "SANDBOX", sandboxSlot: 1 };
const catalog = [
  { id: "selloff", title: "Market sell-off", version: 1, inputType: "SYNTHETIC", shocks: [-10, -20, -30], lesson: "Cash cushions a uniform fall in equity prices." },
  { id: "recovery", title: "Fall and partial recovery", version: 1, inputType: "SYNTHETIC", shocks: [-20, -35, -15, 0, 10], lesson: "Explore a recovery after a fall." },
  { id: "whipsaw", title: "Rally then reversal", version: 1, inputType: "SYNTHETIC", shocks: [15, 30, 5, -10], lesson: "Explore a change of direction." },
  { id: "custom", title: "Custom price shock", version: 1, inputType: "SYNTHETIC", shocks: [-10], lesson: "Make it yours." },
];
const result = { currency: "INR", valuedAt: "2026-09-24T10:00:00Z", scenario: catalog[0], cash: 200, startingEquity: 1000,
  projection: { returnPercent: -24, maximumDrawdownPercent: 24, steps: [{ step: 0, shockPercent: 0, equity: 1000, change: 0 }, { step: 1, shockPercent: -30, equity: 760, change: -240 }] },
  positions: [{ symbol: "TEST", exchange: "NSE", startingValue: 800, endingValue: 560, change: -240 }], assumptions: ["Cash and quantities stay fixed."] };
async function respond(route: Route, body: unknown, status = 200) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options ? undefined : JSON.stringify(body) });
}
async function setup(page: Page, remaining = 10) {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "lab-user", email: "lab@test.local", displayName: "Lab User" } })));
  await page.route("**/api/v1/accounts", route => respond(route, [india, us, usPro]));
  await page.route("**/api/v1/scenarios", route => respond(route, catalog));
  await page.route("**/api/v1/scenarios/credits", route => respond(route, { plan: "PLUS", allowance: 10, remaining, renewsAt: null }));
}
test("standalone lab runs scenarios and keeps calculation details collapsed", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/accounts/*/scenarios", route => respond(route, result));
  await page.goto("/scenario-lab");
  await page.getByRole("button", { name: /Run scenario/ }).click();
  await expect(page.getByRole("img", { name: "Hypothetical portfolio value by scenario step" })).toBeVisible();
  await expect(page.getByText("₹760.00", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("Cash and quantities stay fixed.")).not.toBeVisible();
  await page.getByText("Impact on each holding", { exact: true }).click();
  await expect(page.getByRole("cell", { name: "NSE:TEST", exact: true })).toBeVisible();
});
test("custom path input is validated and retries reuse the request ID", async ({ page }) => {
  await setup(page);
  const submissions: Array<{ requestId: string; title: string; customShocks: number[] }> = [];
  await page.route("**/api/v1/accounts/*/scenarios", route => {
    if (route.request().method() !== "POST") return respond(route, {});
    submissions.push(route.request().postDataJSON());
    return submissions.length === 1 ? respond(route, { message: "Please retry" }, 503) : respond(route, { ...result, scenario: { ...catalog[3], title: "My rally" } });
  });
  await page.goto("/scenario-lab");
  await page.getByRole("button", { name: /Custom price shock/ }).click();
  await page.getByLabel("Scenario name").fill("My rally");
  await page.getByLabel("Step 1 price change").fill("-101");
  await expect(page.getByRole("button", { name: /Run scenario/ })).toBeDisabled();
  await page.getByLabel("Step 1 price change").fill("-100");
  await page.getByRole("button", { name: /Run scenario/ }).click();
  await expect(page.getByRole("region", { name: "Scenario Lab", exact: true }).getByRole("alert")).toContainText("Please retry");
  await page.getByRole("button", { name: /Run scenario/ }).click();
  await expect(page.getByRole("heading", { name: "My rally" })).toBeVisible();
  expect(submissions).toHaveLength(2);
  expect(submissions[0].requestId).toBe(submissions[1].requestId);
  expect(submissions[1]).toMatchObject({ title: "My rally", customShocks: [-100, 25, 50] });
});
test("market switch chooses the active paid US portfolio", async ({ page }) => {
  await setup(page);
  let path = "";
  await page.route("**/api/v1/accounts/*/scenarios", route => { path = route.request().url(); return respond(route, { ...result, currency: "USD" }); });
  await page.goto("/scenario-lab");
  await page.getByRole("button", { name: "US · USD" }).click();
  await page.getByRole("button", { name: /Run scenario/ }).click();
  await expect(page.getByText("$760.00", { exact: true }).first()).toBeVisible();
  expect(path).toContain(usPro.id);
});
test("exhausted credits disable creating a new scenario", async ({ page }) => {
  await setup(page, 0);
  await page.goto("/scenario-lab");
  await expect(page.getByRole("button", { name: /Run scenario/ })).toBeDisabled();
  await expect(page.getByRole("link", { name: "View your plan" })).toBeVisible();
});
for (const theme of ["light", "dark"]) test(`lab fits mobile in ${theme} appearance`, async ({ page }) => {
  await setup(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(theme => localStorage.setItem("stoxsim-theme:lab-user", theme), theme);
  await page.goto("/scenario-lab");
  await page.getByRole("button", { name: /Custom price shock/ }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `/tmp/stoxsim-lab-${theme}.png`, fullPage: true });
});
