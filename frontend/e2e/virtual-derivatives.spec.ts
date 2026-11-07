import { expect, test, type Page, type Route } from "@playwright/test";

const expiry = "2026-12-29";
const contract = { id: "11111111-2222-4333-8444-555555555555", kind: "CE", symbol: "NIFTY 27000 CE", expiry, lotSize: 65, tickSize: .05, strike: 27000 };
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
  await page.getByRole("button", { name: "Buy 27000 call" }).click();
  await page.getByRole("spinbutton", { name: "Lots" }).fill("2");
  const submit = page.getByRole("button", { name: "Place virtual order" });
  await submit.click();
  await expect(page.getByRole("alert")).toHaveText("The quote changed. Try again.");
  await submit.click();
  await expect(page.getByText("Buy to open filled for 2 lot(s) of NIFTY 27000 CE", { exact: false })).toBeVisible();
  expect(orderKeys).toHaveLength(2);
  expect(orderKeys[0]).toBe(orderKeys[1]);
  await expect(page.getByRole("button", { name: "Close lots" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await testInfo.attach("virtual-derivatives-markets", { body: await page.screenshot({ fullPage: true }), contentType: "image/png" });
});
