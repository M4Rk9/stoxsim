import { expect, test, type Page, type Route } from "@playwright/test";

const account = { id: "11111111-2222-4333-8444-555555555555", marketRegion: "INDIA", accountKind: "STANDARD", sandboxSlot: 0, accountLabel: "Standard India", currency: "INR", startingCapital: 500000, availableCash: 500000, blockedCash: 0, realizedProfitLoss: 0, active: true, leaderboardEligible: true };
const usAccount = { ...account, id: "22222222-2222-4333-8444-555555555555", marketRegion: "UNITED_STATES", currency: "USD", accountLabel: "Standard US" };
const stock = (symbol: string, exchange = "NSE", instrumentType = "EQUITY") => ({ id: `${exchange}-${symbol}`, tradingSymbol: symbol, name: `${symbol} company or exchange traded fund`, marketRegion: "INDIA", exchange, instrumentType, currency: "INR", tickSize: 0.05 });
async function reply(route: Route, body: unknown, status = 200) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options ? undefined : JSON.stringify(body) });
}
async function setup(page: Page, search: (route: Route, query: string) => Promise<void>, dark = false, accountsReady?: Promise<void>) {
  const user = { id: "search-learner", email: "search@example.test", displayName: "Search Learner", emailVerified: true, platformAdmin: false, accounts: [account, usAccount] };
  await page.addInitScript(({ user, dark }) => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user }));
    localStorage.setItem(`stoxsim-theme:${user.id}`, dark ? "dark" : "light");
  }, { user, dark });
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url()), path = url.pathname;
    if (path.endsWith("/accounts") && route.request().method() !== "OPTIONS") await accountsReady;
    if (route.request().method() !== "OPTIONS" && path.endsWith("/instruments/search")) return search(route, url.searchParams.get("q")!);
    const body = path.endsWith("/accounts") ? [account, usAccount]
      : path.endsWith("/portfolio") ? { ...account, investedValue: 0, marketValue: 0, unrealizedProfitLoss: 0, totalProfitLoss: 0, totalAccountValue: 500000, totalReturnPercent: 0, dataStatus: "STALE", holdings: [] }
      : path.endsWith("/market/status") ? { exchange: "NSE", phase: "CLOSED", timezone: "Asia/Kolkata" }
      : path.endsWith("/market/movers") ? { universe: "NIFTY", dataStatus: "STALE", gainers: [], losers: [] }
      : path.endsWith("/watchlists/default") ? { id: "watchlist", name: "Watchlist", items: [] }
      : path.endsWith("/candles") ? { candles: [] }
      : path.endsWith("/insights") ? { provider: "FIXTURE", status: "UNAVAILABLE", ratios: [], financials: { metrics: [] } }
      : path.endsWith("/onboarding") ? { completed: true, dismissed: true }
      : [];
    return reply(route, body);
  });
  await page.goto("/");
  return page.getByRole("textbox", { name: "Search stocks", exact: true });
}

test("initial account loading preserves a search already typed in the same market", async ({ page }) => {
  let releaseAccounts!: () => void;
  const accountsReady = new Promise<void>(resolve => { releaseAccounts = resolve; });
  const input = await setup(page, route => reply(route, [stock("RELIANCE")]), false, accountsReady);
  await input.fill("reli");
  await expect(page.locator("#stock-search-results")).toContainText("RELIANCE");
  releaseAccounts();
  await expect(page.locator(".metric").filter({ hasText: "Account value" })).toContainText("₹5,00,000.00");
  await expect(input).toHaveValue("reli");
  await expect(page.locator("#stock-search-results")).toContainText("RELIANCE");
});

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
    if ((width === 390 && dark) || (width === 1280 && !dark)) {
      await input.fill("");
      await page.evaluate(() => { const banner = document.createElement("p"); banner.textContent = "REVIEW FIXTURE · Synthetic account data · Not production"; banner.style.cssText = "padding:12px;background:#263c31;color:#fff;font:14px system-ui;margin:0"; document.body.prepend(banner); });
      await testInfo.attach(`research-home-${width}-${dark ? "dark" : "light"}`, { body: await page.screenshot({ fullPage: true }), contentType: "image/png" });
    }
  });
}

test("home research counts a loaded quote, never a failed quote or a suggestion", async ({ page }) => {
  const pageErrors: string[] = [];
  page.on("pageerror", error => pageErrors.push(error.message));
  const input = await setup(page, route => reply(route, [stock("RELIANCE")]));
  const events: string[] = [];
  await page.route("**/api/v1/analytics/events", async route => {
    if (route.request().method() === "POST") events.push(route.request().postDataJSON().event);
    await reply(route, {});
  });
  let fail = true;
  await page.route("**/api/v1/instruments/INDIA/NSE/RELIANCE/quote", route => reply(route,
    fail ? { message: "Quote unavailable" } : { lastPrice: 200, previousClose: 199, dataStatus: "CLOSED", pricingSource: "EXTERNAL" }, fail ? 503 : 200));
  await input.fill("reli");
  await expect(page.locator("#stock-search-results")).toContainText("RELIANCE");
  expect(events).not.toContain("STOCK_OPENED");
  await page.locator("#stock-search-results button").click();
  await expect(page.getByRole("alert").filter({ hasText: "Quote unavailable" })).toBeVisible();
  expect(events).not.toContain("STOCK_OPENED");
  fail = false;
  await input.fill("reli");
  await page.locator("#stock-search-results button").click();
  await expect.poll(() => events.filter(e => e === "STOCK_OPENED").length).toBe(1);
  await input.fill("reli");
  await page.locator("#stock-search-results button").click();
  await expect(page.getByRole("alert").filter({ hasText: "Quote unavailable" })).toHaveCount(0);
  expect(events.filter(e => e === "STOCK_OPENED")).toHaveLength(1);
  await expect(page.locator(".quoteCard")).toBeVisible();
  expect(pageErrors).toEqual([]);
});


for (const replacement of ["market", "stock"] as const) test(`superseded quote cannot record research or replace a ${replacement} selection`, async ({ page }) => {
  const pageErrors: string[] = [];
  page.on("pageerror", error => pageErrors.push(error.message));
  const input = await setup(page, (route, q) => reply(route, [stock(q === "old" ? "OLD" : "NEW")]));
  const events: string[] = [];
  await page.route("**/api/v1/analytics/events", async route => {
    if (route.request().method() === "POST") events.push(route.request().postDataJSON().event);
    await reply(route, {});
  });
  let releaseOld: (() => void) | undefined;
  let oldFinished = false;
  await page.route("**/api/v1/instruments/INDIA/NSE/*/quote", async route => {
    if (route.request().method() === "OPTIONS") return reply(route, {});
    if (route.request().url().includes("/OLD/")) {
      await new Promise<void>(resolve => { releaseOld = resolve; });
      await reply(route, { lastPrice: 999, previousClose: 998, dataStatus: "CLOSED", pricingSource: "EXTERNAL" }).catch(() => undefined);
      oldFinished = true;
    } else await reply(route, { lastPrice: 200, previousClose: 199, dataStatus: "CLOSED", pricingSource: "EXTERNAL" });
  });
  await input.fill("old");
  await page.locator("#stock-search-results button").click();
  await expect.poll(() => !!releaseOld).toBe(true);
  if (replacement === "market") {
    await page.getByRole("button", { name: "🇺🇸 US", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Find a US stock or ETF" })).toBeVisible();
  } else {
    await input.fill("new");
    await page.locator("#stock-search-results button").click();
    await expect(page.locator(".quoteCard h3")).toHaveText("NEW");
  }
  releaseOld!();
  await expect.poll(() => oldFinished).toBe(true);
  if (replacement === "market") {
    await expect(page.locator(".quoteCard")).toHaveCount(0);
    expect(events).not.toContain("STOCK_OPENED");
    await input.fill("ap");
    await expect(page.getByRole("search").getByRole("button", { name: "Search", exact: true })).toBeEnabled();
  } else {
    await expect(page.locator(".quoteCard h3")).toHaveText("NEW");
    await expect(page.locator(".quotePrice")).toContainText("₹200.00");
    expect(events.filter(event => event === "STOCK_OPENED")).toHaveLength(1);
  }
  expect(pageErrors).toEqual([]);
});

test("movers and watchlists open the exact listing, and details prefill the existing order ticket", async ({ page }, testInfo) => {
  await setup(page, route => reply(route, []));
  const quote = { lastPrice: 200, previousClose: 199, dataStatus: "CLOSED", pricingSource: "EXTERNAL" };
  await page.route("**/api/v1/market/movers?*", route => reply(route, {
    universe: "NIFTY", dataStatus: "CLOSED",
    gainers: [{ ...quote, instrumentKey: "NSE_EQ|FIXTURE", symbol: "M&M", exchange: "NSE", name: "Mahindra fixture", change: 1, changePercent: .5 }],
    losers: [],
  }));
  await page.route("**/api/v1/instruments/cash-exchanges", route => reply(route, ["NSE", "BSE"]));
  await page.route("**/api/v1/watchlists/default", route => reply(route, {
    id: "watchlist", name: "Watchlist",
    items: [{ ...quote, ...stock("M&M", "BSE"), itemId: "bse-watch", instrumentId: "bse-instrument", symbol: "M&M", lastPrice: 210 }],
  }));
  await page.route(/\/api\/v1\/instruments\/INDIA\/(NSE|BSE)\/M(?:%26|&)M(?:\/quote)?$/, route => {
    const exchange = new URL(route.request().url()).pathname.includes("/BSE/") ? "BSE" : "NSE";
    return reply(route, route.request().url().endsWith("/quote") ? quote : stock("M&M", exchange));
  });
  await page.reload();
  const mover = page.getByRole("link", { name: /Mahindra fixture/ });
  await expect(mover).toHaveAttribute("href", "/stocks/NSE/M%26M");
  await mover.focus();
  await mover.press("Enter");
  await expect(page.getByRole("heading", { name: "M&M", exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "M&M", exact: true })).toBeVisible();
  await testInfo.attach("stock-detail-actions", { body: await page.locator('[aria-label="Virtual trading"]').screenshot(), contentType: "image/png" });
  await page.getByRole("link", { name: "Virtual Buy", exact: true }).click();
  const ticket = page.locator("#paper-order-ticket");
  await expect(ticket.getByLabel("Stock", { exact: true })).toHaveValue("M&M");
  await expect(ticket.getByRole("button", { name: "Buy", exact: true })).toHaveAttribute("aria-pressed", "true");
  await expect(ticket.getByRole("button", { name: "Place buy order" })).toBeEnabled();
  await page.getByRole("link", { name: /M&M BSE/ }).click();
  await expect(page).toHaveURL(/\/stocks\/BSE\/M%26M$/);
  await page.getByRole("link", { name: "Virtual Sell", exact: true }).click();
  await expect(ticket).toContainText("BSE");
  await expect(ticket.getByRole("button", { name: "Sell", exact: true })).toHaveAttribute("aria-pressed", "true");
  await expect(ticket.getByRole("button", { name: "Place sell order" })).toBeEnabled();
});

test("the quick quote chart switches to OHLC candles and supports keyboard inspection", async ({ page }, testInfo) => {
  const input = await setup(page, route => reply(route, [stock("RELIANCE")]));
  await page.route("**/api/v1/instruments/INDIA/NSE/RELIANCE/quote", route => reply(route,
    { lastPrice: 111, dataStatus: "CLOSED", pricingSource: "EXTERNAL" }));
  await page.route("**/api/v1/instruments/INDIA/NSE/RELIANCE/candles?*", route => reply(route, { candles: [
    { timestamp: "2025-05-05T03:45:00Z", open: 110, high: 111, low: 99, close: 100 },
    { timestamp: "2025-05-06T03:45:00Z", open: 98, high: 112, low: 97, close: 111, volume: 450 },
  ] }));
  await input.fill("reli");
  await page.getByRole("button", { name: "Quick quote for RELIANCE on NSE" }).click();
  await expect(page.getByRole("img", { name: "Historical closing-price chart" })).toBeVisible();
  await page.getByRole("button", { name: "Candlestick" }).click();
  const candles = page.getByRole("img", { name: "Daily candlestick chart" });
  await expect(candles).toBeVisible();
  await expect(page.getByRole("checkbox", { name: "Patterns" })).toHaveCount(0);
  const readout = page.locator('[aria-label="Selected candle"]');
  await expect(readout).toContainText("Volume 450");
  await candles.press("ArrowLeft");
  await expect(readout).toContainText("Close ₹100.00");
  await testInfo.attach("candlestick-chart", { body: await page.locator(".chartBlock").screenshot(), contentType: "image/png" });
});

test("the BSE exchange filter keeps a dual listing distinct and opens a BSE-only equity", async ({ page }) => {
  const input = await setup(page, route => reply(route, [stock("ACME"), stock("ACME", "BSE"), stock("ACMEONLY", "BSE")]));
  await page.route("**/api/v1/instruments/cash-exchanges", route => reply(route, ["NSE", "BSE"]));
  await page.route("**/api/v1/instruments/INDIA/BSE/ACMEONLY", route => reply(route, stock("ACMEONLY", "BSE")));
  await page.route("**/api/v1/instruments/INDIA/BSE/ACMEONLY/quote", route => reply(route, {
    lastPrice: 100, dataStatus: "CLOSED", pricingSource: "EXTERNAL",
  }));
  await page.reload();
  await expect(page.getByRole("combobox", { name: "Exchange", exact: true })).toBeVisible();
  await input.fill("acme");
  const results = page.locator("#stock-search-results");
  await expect(results.locator('a[href="/stocks/NSE/ACME"]')).toBeVisible();
  await expect(results.locator('a[href="/stocks/BSE/ACME"]')).toBeVisible();
  await page.getByRole("combobox", { name: "Exchange", exact: true }).selectOption("BSE");
  await expect(results.getByRole("link")).toHaveCount(2);
  await expect(results.locator('a[href="/stocks/NSE/ACME"]')).toHaveCount(0);
  await results.getByRole("link", { name: /ACMEONLY/ }).click();
  await expect(page).toHaveURL(/\/stocks\/BSE\/ACMEONLY$/);
  await expect(page.getByRole("heading", { name: "ACMEONLY", exact: true })).toBeVisible();
});

for (const width of [320,768,1440]) test(`stock detail ranges and order card fit ${width}px`,async({page},info)=>{
  await page.setViewportSize({width,height:1000}); await setup(page,route=>reply(route,[]));
  await page.route("**/api/v1/instruments/INDIA/BSE/ACME",route=>reply(route,{...stock("ACME","BSE"),provider:"UPSTOX"}));
  await page.route("**/api/v1/instruments/INDIA/BSE/ACME/quote",route=>reply(route,{lastPrice:111,open:100,high:112,low:99,previousClose:100,dataStatus:"CLOSED",pricingSource:"EXTERNAL"}));
  const requested:string[]=[];
  await page.route("**/api/v1/instruments/INDIA/BSE/ACME/candles?*",route=>{
    requested.push(new URL(route.request().url()).searchParams.get("interval")!);
    return reply(route,{candles:[
      {timestamp:"2026-11-05T03:45:00Z",open:100,high:110,low:98,close:108,volume:100},
      {timestamp:"2026-11-06T03:45:00Z",open:108,high:112,low:106,close:111,volume:400},
      {timestamp:"2026-11-06T04:00:00Z",open:111,high:112,low:103,close:105,volume:200},
      {timestamp:"2026-11-06T04:15:00Z",open:105,high:111,low:101,close:109,volume:250}]});
  });
  await page.goto("/stocks/BSE/ACME"); await expect(page.getByRole("img",{name:"Historical closing-price chart"})).toBeVisible();
  await page.getByRole("button",{name:"Candlestick",exact:true}).click();
  for(const [label,interval] of [["1D","ONE_MINUTE"],["1W","FIFTEEN_MINUTES"],["1M","ONE_DAY"],["6M","ONE_DAY"],["1Y","ONE_DAY"],["3Y","ONE_WEEK"],["5Y","ONE_WEEK"]]){
    const count=requested.length; await page.getByRole("button",{name:label,exact:true}).click();
    await expect.poll(()=>requested.length).toBeGreaterThan(count); expect(requested.at(-1)).toBe(interval);
    await expect(page.locator('[aria-label="Selected candle"]')).toBeVisible();
  }
  await page.getByRole("button",{name:"1M",exact:true}).click();
  const svg=page.getByRole("img",{name:"Daily candlestick chart"}); await expect(svg).toBeVisible();
  const positions=await svg.locator("rect").evaluateAll(rects=>rects.map(rect=>Number(rect.getAttribute("x"))));
  expect(positions).toHaveLength(4); expect(positions[2]-positions[1]).toBeCloseTo(positions[1]-positions[0]);
  const actions=page.locator('[aria-label="Virtual trading"]');
  await expect(actions.getByRole("link",{name:"Buy",exact:true})).toHaveAttribute("href",/exchange=BSE.*side=BUY/);
  expect(await actions.evaluate(el=>Boolean(el.closest("aside")))).toBe(true);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await info.attach(`stock-detail-${width}`,{body:await page.screenshot({path:info.outputPath(`stock-detail-${width}.png`),fullPage:true}),contentType:"image/png"});
});
test("F&O opens a separate tab and restores its session",async({page,context},info)=>{
  await setup(page,route=>reply(route,[]));
  await page.route("**/api/v1/derivatives/capability",route=>reply(route,{enabled:true}));
  let refreshed=0;
  await context.route("**/api/v1/**",route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith("/auth/refresh")){if(route.request().method()!=="OPTIONS")refreshed++;return reply(route,{accessToken:"popup-fixture",user:{id:"test",displayName:"Learner",email:"test@example.test"}});}
    if(path.endsWith("/capability"))return reply(route,{enabled:true});
    if(path.endsWith("/portfolio"))return reply(route,{cash:500000,reservedMargin:0,realizedPnl:0,totalEquity:500000,positions:[],fills:[]});
    return reply(route,[]);
  });
  await page.reload(); const link=page.getByRole("link",{name:/Open F&O workspace/});
  await expect(link).toHaveAttribute("target","_blank");
  await info.attach("markets-fno",{body:await page.locator(".derivativesDiscovery").screenshot({path:info.outputPath("markets-fno.png")}),contentType:"image/png"});
  const popupPromise=page.waitForEvent("popup"); await link.click(); const popup=await popupPromise;
  await expect(popup.getByRole("heading",{name:"NSE index contracts"})).toBeVisible();
  expect(refreshed).toBeGreaterThan(0); expect(await popup.evaluate(()=>window.opener)).toBeNull();
});
