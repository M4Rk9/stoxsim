import { test, expect, type Route } from "@playwright/test";

const account = { id: "11111111-2222-4333-8444-555555555555", marketRegion: "INDIA", accountKind: "STANDARD", sandboxSlot: 0, accountLabel: "Standard India", currency: "INR", startingCapital: 500000, availableCash: 500000, blockedCash: 0, realizedProfitLoss: 0, active: true, leaderboardEligible: true };
async function respond(route: Route, body: unknown) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: options ? undefined : JSON.stringify(body) });
}
for (const width of [390, 1280]) test(`stock search and header stay aligned at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.addInitScript(account => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "layout-user", email: "layout@test.local", displayName: "Layout User", accounts: [account] } })), account);
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith("/accounts") ? [account]
      : path.endsWith("/portfolio") ? { ...account, investedValue: 0, marketValue: 0, unrealizedProfitLoss: 0, totalProfitLoss: 0, totalAccountValue: 500000, totalReturnPercent: 0, dataStatus: "STALE", holdings: [] }
      : path.endsWith("/market/status") ? { exchange: "NSE", phase: "REGULAR", timezone: "Asia/Kolkata", currentTime: "2026-10-07T10:00:00Z", nextTransition: "2026-10-07T10:00:00Z", orderDate: "2026-10-07" }
      : path.endsWith("/market/movers") ? { universe: "NIFTY", dataStatus: "STALE", gainers: [], losers: [] }
      : path.endsWith("/watchlists/default") ? { id: "watchlist", name: "Watchlist", items: [] }
      : path.endsWith("/onboarding") ? { completed: true, dismissed: true, introductionCompleted: true, firstOrderCompleted: true, nextStep: "COMPLETE" }
      : path.endsWith("/instruments/search") ? [{ id: "stock", tradingSymbol: "RELIANCE", name: "Reliance Industries with a long company name for wrapping", marketRegion: "INDIA", exchange: "NSE", instrumentType: "EQUITY", currency: "INR", tickSize: 0.05 }]
      : [];
    return respond(route, body);
  });
  await page.goto("/");
  const input = page.getByRole("textbox", { name: "Search stocks" });
  await input.fill("Reliance");
  const search = page.getByRole("search");
  const button = search.getByRole("button", { name: "Search", exact: true });
  const inputRect = await input.boundingBox(), buttonRect = await button.boundingBox();
  expect(inputRect!.x + inputRect!.width).toBeLessThanOrEqual(buttonRect!.x);
  await expect(input).toHaveCSS("outline-style", "none");
  await button.click();
  await expect(page.getByRole("button", { name: /RELIANCE.*Reliance Industries/ })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.getByText("STALE", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "StoxScore", exact: true })).toHaveCount(0);
  await expect(page.getByRole("combobox", { name: "Portfolio", exact: true })).toHaveCount(0);
  const lab = page.getByRole("link", { name: /Scenario Lab/ });
  await expect(lab).toHaveAttribute("target", "_blank");
  const labRect = await lab.boundingBox(), menuRect = await page.getByRole("button", { name: "Open account menu for Layout User" }).boundingBox();
  const overlaps = labRect!.x < menuRect!.x + menuRect!.width && labRect!.x + labRect!.width > menuRect!.x && labRect!.y < menuRect!.y + menuRect!.height && labRect!.y + labRect!.height > menuRect!.y;
  expect(overlaps).toBe(false);
});
