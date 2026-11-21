import { expect, test, type Page, type Route } from "@playwright/test";

const expiry = "2026-12-29";
const contract = { providerKey: "NSE_FO|fixture", underlyingKey: "NSE_INDEX|Nifty 50", id: "11111111-2222-4333-8444-555555555555", kind: "CE", symbol: "NIFTY 27000 CE", expiry, lotSize: 65, tickSize: .05, strike: 27000 };
const option = { contract, last: 80, bid: 79, ask: 81, volume: 450, lastTradeAt: "2026-11-06T06:00:00Z", fresh: true };
const future = { ...option, contract: { ...contract, id: "22222222-2222-4333-8444-555555555555", kind: "FUT", symbol: "NIFTY FUT", strike: null }, fresh: false };

async function reply(route: Route, body: unknown, status = 200) {
  const request = route.request();
  const options = request.method() === "OPTIONS";
  await route.fulfill({
    status: options ? 204 : status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": request.headers().origin ?? "http://localhost:3000",
      "Access-Control-Allow-Credentials": "true",
      "Access-Control-Allow-Headers": "authorization,content-type",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    },
    body: options ? undefined : JSON.stringify(body),
  });
}

async function signedIn(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "test", displayName: "Test" } }));
  });
}

test("disabled derivatives expose no order controls", async ({ page }) => {
  await signedIn(page);
  await page.route("**/api/v1/derivatives/capability", route => reply(route, { enabled: false }));
  await page.goto("/markets/derivatives");
  await expect(page.getByText("Virtual derivatives are currently unavailable.")).toBeVisible();
  await expect(page.getByRole("button", { name: /Place virtual order/ })).toHaveCount(0);
});

test("virtual options show lots, stale futures, and reuse a request key on retry", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 850 });
  await page.clock.install();
  await signedIn(page);
  let filled = false;
  const orderKeys: string[] = [];
  await page.route("**/api/v1/derivatives/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() === "OPTIONS") return reply(route, {});
    if (path.endsWith("/capability")) return reply(route, { enabled: true });
    if (path.endsWith("/expiries")) return reply(route, [expiry]);
    if (path.endsWith("/chain")) return reply(route, [future, option]);
    if (path.endsWith("/portfolio")) return reply(route, {
      cash: filled ? 489470 : 500000, reservedMargin: 0, realizedPnl: 0, totalEquity: 500000,
      positions: filled ? [{ market: option, lots: 2, direction: "LONG", averageEntry: 81, reservedMargin: 0, unrealizedPnl: -130, awaitingSettlement: false }] : [],
      fills: [],
    });
    if (path.endsWith("/preview")) {
      const { lots } = route.request().postDataJSON();
      return reply(route, { contract, action: "OPEN_LONG", lots, quantity: 65 * lots, price: 81,
        requiredFunds: 5265 * lots, cashChange: -5265 * lots, cashAfter: 500000 - 5265 * lots,
        realizedPnl: 0, breakeven: 27081, maximumLoss: 5265 * lots, unlimitedLoss: false,
        quoteAt: option.lastTradeAt, generatedAt: new Date().toISOString() });
    }
    const order = route.request().postDataJSON();
    orderKeys.push(order.requestKey);
    expect(order).toMatchObject({ contractId: contract.id, action: "OPEN_LONG", lots: 2 });
    if (orderKeys.length === 1) return reply(route, { message: "The quote changed. Try again." }, 503);
    filled = true;
    return reply(route, { id: "fill", symbol: contract.symbol, action: "OPEN_LONG", lots: 2, price: 81, realizedPnl: 0 });
  });
  await page.goto("/markets/derivatives");
  await expect(page.getByRole("navigation", { name: "Main navigation" }).getByRole("link", { name: "Markets" })).toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("button", { name: "Open short" })).toBeDisabled();
  await page.getByRole("searchbox", { name: "Find in loaded contracts" }).fill("99999");
  await expect(page.getByText("No loaded contracts match your filters.")).toBeVisible();
  await page.getByRole("searchbox", { name: "Find in loaded contracts" }).fill("");
  await page.getByRole("button", { name: "Buy 27000 call" }).click();
  await page.getByRole("spinbutton", { name: "Lots" }).fill("2");
  const submit = page.getByRole("button", { name: "Place virtual order" });
  await expect(submit).toBeDisabled();
  await page.getByRole("button", { name: "Review risk and funds" }).click();
  await expect(submit).toBeDisabled();
  await expect(page.getByRole("region", { name: "Order risk review" })).toContainText("₹10,530.00");
  await page.getByRole("checkbox", { name: /I understand the risk/ }).check();
  await expect(submit).toBeEnabled();
  await page.clock.fastForward(31000);
  await expect(submit).toBeDisabled();
  await expect(page.getByText("This review has expired. Review risk and funds again before submitting.")).toBeVisible();
  // Editing quantity discards the reviewed snapshot and its acknowledgement.
  await page.getByRole("spinbutton", { name: "Lots" }).fill("1");
  await expect(submit).toBeDisabled();
  await expect(page.getByRole("region", { name: "Order risk review" })).toHaveCount(0);
  await page.getByRole("spinbutton", { name: "Lots" }).fill("2");
  await page.getByRole("button", { name: "Review risk and funds" }).click();
  await page.getByRole("checkbox", { name: /I understand the risk/ }).check();
  expect(orderKeys).toHaveLength(0);
  await testInfo.attach("fno-risk-review-mobile-synthetic", { body: await page.screenshot({ fullPage: true }), contentType: "image/png" });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole("button", { name: "Advanced view" }).click();
  await testInfo.attach("fno-risk-review-desktop-synthetic", { body: await page.screenshot({ fullPage: true }), contentType: "image/png" });
  await page.setViewportSize({ width: 390, height: 850 });
  await submit.click();
  await expect(page.locator("#derivative-order").getByRole("alert")).toHaveText("The quote changed. Try again.");
  await submit.click();
  await expect(page.getByText("Buy to open filled for 2 lot(s) of NIFTY 27000 CE", { exact: false })).toBeVisible();
  expect(orderKeys).toHaveLength(2);
  expect(orderKeys[0]).toBe(orderKeys[1]);
  await expect(page.getByRole("button", { name: "Close lots" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await testInfo.attach("virtual-derivatives-markets", { body: await page.screenshot({ fullPage: true }), contentType: "image/png" });
});

test("basket review blocks execution until acknowledged and submits all legs in one request", async ({ page }, testInfo) => {
  await signedIn(page);
  await page.clock.install();
  await page.setViewportSize({ width: 390, height: 850 });
  const put = { ...option, last: 60, ask: 61, contract: { ...contract,
    id: "33333333-2222-4333-8444-555555555555", providerKey: "NSE_FO|fixture-put", kind: "PE", symbol: "NIFTY 27000 PE" } };
  const keys: string[] = [];
  let reviews = 0;
  let singleOrders = 0;
  await page.route("**/api/v1/derivatives/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() === "OPTIONS") return reply(route, {});
    if (path.endsWith("/capability")) return reply(route, { enabled: true });
    if (path.endsWith("/expiries")) return reply(route, [expiry]);
    if (path.endsWith("/chain")) return reply(route, [option, put]);
    if (path.endsWith("/portfolio")) return reply(route, { cash: 500000, reservedMargin: 0, realizedPnl: 0, totalEquity: 500000, positions: [], fills: [] });
    if (path.endsWith("/baskets/preview")) {
      reviews++;
      const body = route.request().postDataJSON();
      expect(body.legs).toHaveLength(2);
      return reply(route, {
        legs: [option, put].map(m => ({contract: m.contract, action: "OPEN_LONG", lots: 1, quantity: 65,
          price: m.ask, requiredFunds: m.ask * 65, quoteAt: m.lastTradeAt})),
        requiredFunds: 9230, cashAfter: 490770,
        payoff: { maximumLoss: 9230, unlimitedLoss: false, maximumProfit: null, unlimitedProfit: true,
          breakevens: [26858, 27142], zeroRanges: [],
          points: [{underlying: 0, pnl: 1745770}, {underlying: 26858, pnl: 0},
            {underlying: 27000, pnl: -9230}, {underlying: 27142, pnl: 0}, {underlying: 33750, pnl: 429520}] },
      });
    }
    if (path.endsWith("/baskets/orders")) {
      const body = route.request().postDataJSON();
      expect(body.legs).toHaveLength(2);
      keys.push(body.requestKey);
      if (keys.length === 1) return reply(route, {message: "Basket result could not be confirmed. Retry unchanged."}, 503);
      return reply(route, {basketId: "basket-fixture", fills: [{id: "one"}, {id: "two"}]});
    }
    singleOrders++;
    return reply(route, {}, 500);
  });
  await page.goto("/markets/derivatives");
  await page.getByRole("button", {name: "Buy 27000 call"}).click();
  await page.getByRole("button", {name: "Add to strategy basket"}).click();
  await expect(page.getByRole("button", {name: "Review basket risk"})).toBeDisabled();
  await page.getByRole("button", {name: "Buy 27000 put"}).click();
  await page.getByRole("button", {name: "Add to strategy basket"}).click();
  const review = page.getByRole("button", {name: "Review basket risk"});
  await review.click();
  const submit = page.getByRole("button", {name: "Place virtual basket"});
  await expect(submit).toBeDisabled();
  await expect(page.getByRole("region", {name: "Combined basket risk review"})).toContainText("₹9,230.00");
  await expect(page.getByRole("img", {name: "Calculated basket expiry profit and loss across index levels"})).toBeVisible();
  await page.getByRole("checkbox", {name: /I understand the combined risk/}).check();
  await expect(submit).toBeEnabled();
  await page.clock.fastForward(31000);
  await expect(submit).toBeDisabled();
  await expect(page.getByText("Basket review expired. Review the basket again.")).toBeVisible();
  await page.getByRole("spinbutton", {name: "Leg 1 lots"}).fill("2");
  await expect(page.getByRole("region", {name: "Combined basket risk review"})).toHaveCount(0);
  await page.getByRole("spinbutton", {name: "Leg 1 lots"}).fill("1");
  await review.click();
  await page.getByRole("checkbox", {name: /I understand the combined risk/}).check();
  await page.getByText("Payoff values", {exact: true}).click();
  expect(keys).toHaveLength(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await testInfo.attach("basket-payoff-mobile-synthetic", {body: await page.screenshot({fullPage: true}), contentType: "image/png"});
  await page.setViewportSize({width: 1440, height: 1000});
  await testInfo.attach("basket-payoff-desktop-synthetic", {body: await page.screenshot({fullPage: true}), contentType: "image/png"});
  await submit.click();
  await expect(page.getByRole("alert").filter({hasText: "Basket result could not be confirmed"})).toBeVisible();
  await submit.click();
  await expect(page.getByText("Virtual basket filled: 2 legs.", {exact: false})).toBeVisible();
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
  expect(reviews).toBe(2);
  expect(singleOrders).toBe(0);
  await expect(page.getByRole("spinbutton", {name: "Leg 1 lots"})).toHaveCount(0);
});
