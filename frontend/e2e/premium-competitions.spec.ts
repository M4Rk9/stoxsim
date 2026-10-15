import { expect, test, type Route } from "@playwright/test";
const season = { code: "2026-Q4-PRO", title: "2026 Q4 Pro Learning Season", startsAt: "2026-10-01T00:00:00Z", endsAt: "2027-01-01T00:00:00Z", open: true, scoringVersion: "standard-india-entry-return-v1" };
const board = { season, enrolled: false, participantCount: 0, standings: [], comparisonNote: "Standard India account only", disclaimer: "Education" };
async function reply(route: Route, status: number, body: unknown) {
  const preflight = route.request().method() === "OPTIONS";
  await route.fulfill({ status: preflight ? 204 : status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS" }, body: preflight || status === 204 ? undefined : JSON.stringify(body) });
}
for (const eligible of [true, false]) test(`Pro competition requires explicit opt-in and server eligibility (${eligible})`, async ({ page }) => {
  let enrolled = 0;
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "token", expiresInSeconds: 900, user: { id: "learner", email: "learner@example.test", displayName: "Learner", emailVerified: true, platformAdmin: false, accounts: [] } })));
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/competitions/premium/enroll") { if (route.request().method() !== "OPTIONS") enrolled++; return reply(route, 200, { ...board, enrolled: true, participantCount: 1 }); }
    if (path === "/api/v1/competitions/premium") return reply(route, eligible ? 200 : 403, eligible ? board : { message: "Pro competitions require an active Pro subscription" });
    if (path === "/api/v1/competitions/current") return reply(route, 200, { ...board, season: { ...season, code: "2026-Q4", title: "Standard season" } });
    if (path === "/api/v1/leagues") return reply(route, 200, []);
    if (path === "/api/v1/campus") return reply(route, 200, { platformAdmin: false, emailVerified: true });
    return reply(route, 204, null);
  });
  await page.goto("/competitions");
  await page.getByRole("button", { name: "Open Pro leaderboard" }).click();
  if (!eligible) {
    await expect(page.getByRole("alert")).toContainText("active Pro");
    await expect(page.getByRole("button", { name: "Join Pro leaderboard" })).toHaveCount(0);
  } else {
    await expect(page.getByRole("button", { name: "Join Pro leaderboard" })).toBeDisabled();
    expect(enrolled).toBe(0);
    await page.getByRole("checkbox", { name: /Share my display name/ }).check();
    await page.getByRole("button", { name: "Join Pro leaderboard" }).click();
    await expect.poll(() => enrolled).toBe(1);
    await expect(page.getByRole("button", { name: "Join standard leaderboard" })).toBeVisible();
  }
});
