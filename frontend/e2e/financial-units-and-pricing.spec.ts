import { test, expect, type Route } from "@playwright/test";

async function respond(route: Route, body: unknown) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : 200,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
      "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" },
    body: options ? undefined : JSON.stringify(body) });
}

for (const fixture of [
  { exchange: "NASDAQ", symbol: "AAPL", currency: "USD", units: "USD million", value: "$123.45M", provider: "ALPACA", statement: "SEC XBRL financial statements" },
  { exchange: "NSE", symbol: "TCS", currency: "INR", units: "crore", value: "₹123.45 Cr", provider: "UPSTOX", statement: "Consolidated" },
  { exchange: "NASDAQ", symbol: "AAPL", currency: "USD", units: "USD thousand", value: "123.45 USD thousand", provider: "ALPACA", statement: "Declared units" },
  { exchange: "NASDAQ", symbol: "AAPL", currency: "USD", units: undefined, value: "123.45", provider: "ALPACA", statement: "Unknown units" },
]) test(`stock financial table preserves ${fixture.units ?? "missing units"} without rescaling`, async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "test-token", user: { id: "learner", platformAdmin: false } })));
  let source = "SIMULATED";
  await page.route("**/api/v1/**", route => {
    const url = route.request().url();
    return respond(route, url.includes("/quote") ? { lastPrice: 200, dataStatus: "LIVE", pricingSource: source }
      : url.includes("/candles") ? { candles: [] }
      : url.includes("/insights") ? { provider: "TEST", asOf: "2026-10-20T12:00:00Z", status: "AVAILABLE", ratios: [], financials: {
        unitsIn: fixture.units, statementType: fixture.statement, metrics: [{ category: "revenue", history: [{ period: "2025", value: 123.45 }] }] } }
      : { ...fixture, tradingSymbol: fixture.symbol, name: "Example", instrumentType: "EQUITY" });
  });
  await page.goto(`/stocks/${fixture.exchange}/${fixture.symbol}`);
  await expect(page.getByRole("heading", { name: fixture.symbol, exact: true })).toBeVisible();
  await expect(page.getByRole("cell", { name: fixture.value, exact: true })).toBeVisible();
  await expect(page.getByText("Financial history is not available for this market yet")).toHaveCount(0);
  await expect(page.getByText("Simulated fallback price.", { exact: true })).toBeVisible();
  await expect(page.getByText(/excluded from observed performance history/)).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  source = "EXTERNAL";
  await page.reload();
  await expect(page.getByRole("heading", { name: fixture.symbol, exact: true })).toBeVisible();
  await expect(page.getByText("Simulated fallback price.", { exact: true })).toHaveCount(0);
});
