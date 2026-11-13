import { expect, test, type Page } from "@playwright/test";

const id = "11111111-1111-4111-8111-111111111111";
const accountId = "22222222-2222-4222-8222-222222222222";
const orderId = "33333333-3333-4333-8333-333333333333";
const plan = { exchange: "NSE", symbol: "INFY", horizon: "Next quarterly results", target: "Evidence of stable margins", invalidation: "Margin contraction", reviewOn: "2026-01-01" };
async function setup(page: Page, dark = false) {
  await page.addInitScript(({ dark }) => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "strategy-fixture", email: "learner@example.test", displayName: "Example Learner", emailVerified: true } }));
    localStorage.setItem("stoxsim-theme:strategy-fixture", dark ? "dark" : "light");
  }, { dark });
  let notes: Array<Record<string, any>> = [];
  let aiCalls = 0;
  await page.route("**/api/v1/**", async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    const headers = { "Content-Type": "application/json", "Access-Control-Allow-Origin": req.headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,PUT,OPTIONS" };
    if (req.method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    let body: unknown = {};
    if (path.endsWith("/reflections")) {
      if (req.method() === "POST") { const payload = req.postDataJSON(); expect(payload.plan).toMatchObject({ exchange: "NSE", symbol: "INFY" }); notes = [{ ...payload, id, createdAt: "2026-11-13T06:00:00Z", updatedAt: "2026-11-13T06:00:00Z" }]; body = notes[0]; }
      else body = notes;
    }
    if (path.endsWith(`/reflections/${id}`)) {
      const payload = req.postDataJSON();
      notes[0] = { ...notes[0], ...payload, reviewedAt: payload.completeReview ? "2026-11-13T07:00:00Z" : null }; body = notes[0];
    }
    if (path.endsWith("/accounts")) body = [{ id: accountId, marketRegion: "INDIA", accountKind: "STANDARD" }];
    if (path.endsWith(`/accounts/${accountId}/orders`)) body = [{ id: orderId, symbol: "INFY", exchange: "NSE", side: "BUY", quantity: 2, status: "EXECUTED", createdAt: "2026-11-13T06:00:00Z" }];
    if (path.endsWith(`/reflections/${id}/order`)) {
      expect(req.postDataJSON()).toEqual({ orderId });
      notes[0] = { ...notes[0], orderId, accountId, linkedOrder: { status: "EXECUTED", side: "BUY", quantity: 2, executionPrice: 1500, currency: "INR", exchange: "NSE", symbol: "INFY", executedAt: "2026-11-13T06:00:00Z" } }; body = notes[0];
    }
    if (/finwiz|assistance/.test(path)) aiCalls++;
    return route.fulfill({ status: 200, headers, body: JSON.stringify(body) });
  });
  return { aiCalls: () => aiCalls };
}
for (const [width, dark] of [[390, true], [1440, true], [1440, false]] as const) {
  test(`private strategy to executed-order review at ${width}px ${dark ? "dark" : "light"}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    const state = await setup(page, dark);
    await page.goto("/journal?exchange=NSE&symbol=INFY");
    await page.getByLabel("My thesis and entry rationale", { exact: true }).fill("Example research: recurring revenue could support margins.");
    await page.getByLabel("Time horizon", { exact: true }).fill(plan.horizon);
    await page.getByLabel("Target or evidence").fill(plan.target);
    await page.getByLabel("Stop or invalidation").fill(plan.invalidation);
    await page.getByLabel("Review date", { exact: true }).fill(plan.reviewOn);
    await page.getByRole("button", { name: "Save strategy and review" }).click();
    await expect(page.getByRole("status")).toContainText("Reflection saved");
    const summary = page.locator("li summary").filter({ hasText: "Example research" });
    await summary.click();
    await expect(page.getByRole("link", { name: "Practise this strategy" })).toHaveAttribute("href", /strategy=11111111.*paper-order-ticket/);
    await page.getByRole("button", { name: "Link an existing practice order" }).click();
    await page.getByLabel("Practice account", { exact: true }).selectOption(accountId);
    await page.getByLabel("Matching order", { exact: true }).selectOption(orderId);
    await page.getByRole("button", { name: "Link selected order" }).click();
    await expect(page.getByText("Executed at", { exact: false })).toBeVisible();
    await page.getByRole("button", { name: "Edit reflection", exact: true }).click();
    await page.getByLabel("Complete this review.", { exact: false }).check();
    await expect(page.getByRole("button", { name: "Save strategy and review" })).toBeDisabled();
    await page.getByLabel("What I learned afterwards", { exact: false }).fill("Revenue held up, but costs rose. I should test margin assumptions before focusing on a price target.");
    await page.getByRole("button", { name: "Save strategy and review" }).click();
    await expect(page.locator("li summary")).toContainText("Reviewed");
    expect(state.aiCalls()).toBe(0);
    expect(await page.evaluate(() => Object.keys(localStorage).some(k => /journal|reflection/.test(k)))).toBe(false);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.locator("form").first().evaluate(el => el.closest("details")?.removeAttribute("open"));
    await page.evaluate(() => { const banner = document.createElement("p"); banner.textContent = "REVIEW FIXTURE · Synthetic account and order data · Not production"; banner.style.cssText = "padding:12px;background:#263c31;color:#fff;font:14px system-ui;margin:0"; document.body.prepend(banner); });
    const screenshot = await page.screenshot({ fullPage: true });
    await test.info().attach(`journal-${width}-${dark ? "dark" : "light"}.png`, { body: screenshot, contentType: "image/png" });
  });
}
