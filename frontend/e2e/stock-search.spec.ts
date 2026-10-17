import { expect, test, type Page, type Route } from "@playwright/test";

const account = { id: "11111111-2222-4333-8444-555555555555", marketRegion: "INDIA", accountKind: "STANDARD", sandboxSlot: 0, accountLabel: "Standard India", currency: "INR", startingCapital: 500000, availableCash: 500000, blockedCash: 0, realizedProfitLoss: 0, active: true, leaderboardEligible: true };
const stock = (symbol: string, exchange = "NSE", instrumentType = "EQUITY") => ({ id: symbol, tradingSymbol: symbol, name: `${symbol} company or exchange traded fund`, marketRegion: "INDIA", exchange, instrumentType, currency: "INR", tickSize: 0.05 });
async function reply(route: Route, body: unknown, status = 200) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options ? undefined : JSON.stringify(body) });
}
async function setup(page: Page, search: (route: Route, query: string) => Promise<void>, dark = false) {
  const user = { id: "search-learner", email: "search@example.test", displayName: "Search Learner", emailVerified: true, platformAdmin: false, accounts: [account] };
  await page.addInitScript(({ user, dark }) => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user }));
    localStorage.setItem(`stoxsim-theme:${user.id}`, dark ? "dark" : "light");
  }, { user, dark });
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url()), path = url.pathname;
    if (route.request().method() !== "OPTIONS" && path.endsWith("/instruments/search")) return search(route, url.searchParams.get("q")!);
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
  return page.getByRole("textbox", { name: "Search stocks", exact: true });
}

test("typing automatically debounces suggestions and clearing removes them", async ({ page }) => {
  const calls: string[] = [];
  const input = await setup(page, async (route, q) => { calls.push(q); await reply(route, [stock("RELIANCE")]); });
  await input.fill("r");
  await page.waitForTimeout(400);
  expect(calls).toEqual([]);
  await input.pressSequentially("eli", { delay: 40 });
  await expect(page.locator("#stock-search-results")).toContainText("RELIANCE");
  expect(calls).toEqual(["reli"]);
  await input.fill("");
  await expect(page.locator("#stock-search-results")).toHaveCount(0);
});

test("late responses cannot replace a newer query and failures can be retried", async ({ page }) => {
  let releaseOld: (() => void) | undefined;
  let fail = true;
  const input = await setup(page, async (route, q) => {
    if (q === "old") {
      await new Promise<void>(resolve => { releaseOld = resolve; });
      await reply(route, [stock("OLD")]).catch(() => undefined);
    } else if (q === "fail" && fail) await reply(route, { message: "Search temporarily unavailable" }, 503);
    else await reply(route, q === "empty" ? [] : [stock("NEW")]);
  });
  await input.fill("old");
  await expect.poll(() => !!releaseOld).toBe(true);
  await input.fill("new");
  await expect(page.locator("#stock-search-results")).toContainText("NEW");
  releaseOld!();
  await expect(page.locator("#stock-search-results")).not.toContainText("OLD");
  await input.fill("fail");
  await expect(page.locator("#stock-search-status")).toHaveText("Search temporarily unavailable");
  fail = false;
  await page.getByRole("search").getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.locator("#stock-search-results")).toContainText("NEW");
  await input.fill("empty");
  await expect(page.locator("#stock-search-status")).toHaveText("No matching stocks or ETFs found.");
});

for (const width of [320, 390, 1280]) for (const dark of [false, true]) {
  test(`NSE stock and ETF suggestions fit ${width}px ${dark ? "dark" : "light"}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    const input = await setup(page, route => reply(route, [stock("BSE"), stock("NIFTYBEES", "NSE", "ETF"), stock("BSEINDEX", "BSE", "INDEX"), stock("BSESTOCK", "BSE"), stock("NIFTY", "NSE", "INDEX")]), dark);
    await input.fill("bse");
    const results = page.locator("#stock-search-results");
    await expect(results.getByRole("button")).toHaveCount(2);
    await expect(results).toContainText("NIFTYBEES");
    await expect(results).not.toContainText("BSESTOCK");
    await expect(page.locator("html")).toHaveAttribute("data-theme", dark ? "dark" : "light");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const formBox = await page.getByRole("search").boundingBox(), resultBox = await results.boundingBox();
    expect(resultBox!.y).toBeGreaterThanOrEqual(formBox!.y + formBox!.height);
    for (const button of await results.getByRole("button").all()) expect(await button.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    await testInfo.attach("stock-search-suggestions", { body: await results.locator("..").screenshot(), contentType: "image/png" });
  });
}
