import { expect, test, type Route } from "@playwright/test";

const api = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080";
const code = "ABCDEFGHIJKLMNOP";
const headers = (route: Route) => ({
  "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
  "Access-Control-Allow-Credentials": "true",
  "Access-Control-Allow-Headers": "authorization,content-type",
  "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
});

test("the invite survives navigation to signup and is forwarded with email registration", async ({ page }) => {
  await page.route(api + "/api/v1/auth/refresh", route => route.fulfill({ status: 401, headers: headers(route) }));
  let submitted = false;
  await page.route(api + "/api/v1/auth/register", async route => {
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers: headers(route) });
    expect(route.request().postDataJSON()).toMatchObject({ referralCode: code, termsAccepted: true });
    submitted = true;
    await route.fulfill({ status: 409, headers: headers(route), json: { message: "Fixture response." } });
  });
  await page.goto("/invite?ref=" + code.toLowerCase());
  await expect(page.getByText("Invite code applied.")).toBeVisible();
  await expect(page).toHaveURL(/\/$/);
  await page.reload();
  await page.getByLabel("Display name").fill("New learner");
  await page.getByLabel("Email", { exact: true }).fill("learner@example.test");
  await page.getByLabel("Password", { exact: true }).fill("Sample-password-123!");
  await page.locator('#get-started input[type="checkbox"]').check();
  await page.getByRole("button", { name: "Start Now!" }).click();
  await expect(page.getByText("Fixture response.")).toBeVisible();
  expect(submitted).toBe(true);
  await page.getByRole("button", { name: "Remove invite" }).click();
  await expect(page.getByText("Invite code applied.")).toHaveCount(0);
});

test("Rewards generates a shareable invite and displays both virtual currencies and history", async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "owner", displayName: "Referral learner", email: "referrals@example.test", accounts: [] } }));
    Object.defineProperty(navigator, "clipboard", { value: { writeText: async (link: string) => {
      sessionStorage.setItem("copied-invite", link);
    } } });
  });
  const invite = "https://stoxsim.com/invite?ref=" + code;
  const overview = {
    enabled: true, canInvite: true, canReview: false, minimumAgeHours: 24, qualificationDays: 90,
    rewardInr: 100000, rewardUsd: 1000, pending: 2, successful: 1, earned: { inr: 100000, usd: 1000 },
    referrals: [{ id: "r1", status: "QUALIFIED", createdAt: "2026-11-27T00:00:00Z" }],
    history: [{ id: "tx1", source: "REFERRAL", inr: 100000, usd: 1000, createdAt: "2026-11-27T00:00:00Z" }],
  };
  await page.route(url => [api + "/api/v1/rewards", api + "/api/v1/rewards/code"].includes(url.href), route => {
    const options = route.request().method() === "OPTIONS";
    const issued = new URL(route.request().url()).pathname.endsWith("/code");
    return route.fulfill({ status: options ? 204 : 200, headers: headers(route),
      json: options ? undefined : { ...overview, ...(issued ? { code, link: invite } : {}) } });
  });
  await page.goto("/rewards");
  await page.getByRole("button", { name: "Create invite link" }).click();
  await expect(page.getByLabel("Shareable link")).toHaveValue(invite);
  await page.getByRole("button", { name: "Copy link" }).click();
  await expect(page.getByRole("status")).toContainText("Invite link copied.");
  expect(await page.evaluate(() => sessionStorage.getItem("copied-invite"))).toBe(invite);
  await expect(page.getByRole("heading", { name: "Reward history" })).toBeVisible();
  await expect(page.getByText("₹1,00,000.00 · $1,000.00")).toBeVisible();
  await expect(page.getByText("Qualified", { exact: true })).toBeVisible();
});
