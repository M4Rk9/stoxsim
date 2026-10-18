import { expect, test, type Page, type Route } from "@playwright/test";

const account = { id: "11111111-2222-4333-8444-555555555555", marketRegion: "INDIA", accountKind: "STANDARD", sandboxSlot: 0, accountLabel: "Standard India", currency: "INR", startingCapital: 500000, availableCash: 500000, blockedCash: 0, realizedProfitLoss: 0, active: true, leaderboardEligible: true };
const stock = (symbol: string, exchange = "NSE", instrumentType = "EQUITY") => ({ id: symbol, tradingSymbol: symbol, name: `${symbol} company or exchange traded fund`, marketRegion: "INDIA", exchange, instrumentType, currency: "INR", tickSize: 0.05 });
async function reply(route: Route, body: unknown, status = 200) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options ? undefined : JSON.stringify(body) });
}
async function setup(page: Page, journey: { nextStep: string; [key: string]: unknown }, dark = false) {
  const user = { id: "search-learner", email: "search@example.test", displayName: "Search Learner", emailVerified: true, platformAdmin: false, accounts: [account] };
  await page.addInitScript(({ user, dark }) => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user }));
    localStorage.setItem(`stoxsim-theme:${user.id}`, dark ? "dark" : "light");
  }, { user, dark });
  let dismissed = false;
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url()), path = url.pathname;
    if (path.endsWith("/onboarding/dismiss")) dismissed = true;
    if (path.endsWith("/onboarding/resume")) dismissed = false;
    if (path.includes("/onboarding")) return reply(route, path.endsWith("/journey") ? { completedSteps: 2, totalSteps: 6, ...journey } : { completed: true, introductionCompleted: true, firstOrderCompleted: true, dismissed });
    const body = path.endsWith("/accounts") ? [account]
      : path.endsWith("/portfolio") ? { ...account, investedValue: 0, marketValue: 0, unrealizedProfitLoss: 0, totalProfitLoss: 0, totalAccountValue: 500000, totalReturnPercent: 0, dataStatus: "STALE", holdings: [] }
      : path.endsWith("/market/status") ? { exchange: "NSE", phase: "CLOSED", timezone: "Asia/Kolkata" }
      : path.endsWith("/market/movers") ? { universe: "NIFTY", dataStatus: "STALE", gainers: [], losers: [] }
      : path.endsWith("/watchlists/default") ? { id: "watchlist", name: "Watchlist", items: [] }
      : path.endsWith("/onboarding") ? { completed: true, dismissed: true }
      : [];
    return reply(route, body);
  });
  await page.goto("/");
  return page.getByRole("region", { name: "Your StoxSim learning journey" });
}


test("pending execution advances to Finwiz when server observes a fill", async ({ page }) => {
  const progress = { nextStep: "WAITING", firstTradeExecuted: false };
  const journey = await setup(page, progress);
  await expect(journey).toContainText("Your order is waiting to execute");
  await expect(journey.getByRole("link", { name: "Check open orders" })).toHaveAttribute("href", "#open-orders");
  progress.nextStep = "FINWIZ"; progress.firstTradeExecuted = true;
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(journey).toContainText("First trade executed. Understand what happened.");
  await expect(journey.getByRole("link", { name: "Learn with Finwiz AI" })).toHaveAttribute("href", "/finwiz");
});
test("hide and resume retain all feature cards", async ({ page }) => {
  const journey = await setup(page, { nextStep: "SCENARIO", firstTradeExecuted: true, finwizAsked: true });
  await expect(journey).toContainText("Put your learning to the test");
  await journey.getByRole("button", { name: "Hide guide" }).click();
  await expect(journey.getByRole("heading", { name: "Put your learning to the test" })).toHaveCount(0);
  for (const name of ["Finwiz AI", "Scenario Lab", "Learning Path", "Competitions"])
    await expect(journey.getByRole("heading", { name, exact: true })).toBeVisible();
  await journey.getByRole("button", { name: "Resume guide" }).click();
  await expect(journey).toContainText("Put your learning to the test");
});
for (const width of [320, 390, 1280]) for (const dark of [false, true]) {
  test(`journey cards and guidance fit ${width}px ${dark ? "dark" : "light"}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    const journey = await setup(page, { nextStep: "FINWIZ", firstTradeExecuted: true }, dark);
    await expect(journey.getByRole("heading", { name: "First trade executed. Understand what happened." })).toBeVisible();
    await expect(page.locator("html")).toHaveAttribute("data-theme", dark ? "dark" : "light");
    const cards = journey.locator('a').filter({ has: page.locator('h3') });
    await expect(cards).toHaveCount(4);
    const boxes = await cards.evaluateAll(nodes => nodes.map(n => { const b = n.getBoundingClientRect(); return { left: b.left, right: b.right, top: b.top, bottom: b.bottom }; }));
    for (let i = 0; i < boxes.length; i++) for (let j = i+1; j < boxes.length; j++) {
      const a=boxes[i], b=boxes[j];
      expect(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top).toBe(true);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await testInfo.attach(`journey-${width}-${dark ? "dark" : "light"}`, { body: await journey.screenshot(), contentType: "image/png" });
  });
}
