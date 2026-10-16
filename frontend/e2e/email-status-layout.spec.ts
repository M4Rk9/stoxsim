import { expect, test, type Route } from "@playwright/test";

async function reply(route: Route, body: unknown, status = 200) {
  const options = route.request().method() === "OPTIONS";
  await route.fulfill({ status: options ? 204 : status, headers: {
    "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
    "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
  }, body: options || status === 204 ? undefined : JSON.stringify(body) });
}

for (const width of [320, 390, 1280]) for (const dark of [false, true]) for (const verified of [false, true]) {
  test(`email status and resend confirmation fit ${width}px ${dark ? "dark" : "light"} verified=${verified}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    const user = { id: "layout-learner", email: "learner@example.test", displayName: "Learner", emailVerified: verified, platformAdmin: false, accounts: [] };
    await page.addInitScript(({ user, dark }) => {
      sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "layout-fixture", expiresInSeconds: 900, user }));
      localStorage.setItem(`stoxsim-theme:${user.id}`, dark ? "dark" : "light");
    }, { user, dark });
    await page.route("**/api/v1/**", async route => {
      const path = new URL(route.request().url()).pathname;
      if (path === "/api/v1/auth/me") return reply(route, user);
      if (path === "/api/v1/reports/weekly/preferences") return reply(route, { enabled: false, zoneId: "Asia/Kolkata", schedule: "Monday mornings" });
      if (path === "/api/v1/subscription") return reply(route, { plan: "FREE", status: "ACTIVE", plans: [], sandboxAccounts: [] });
      if (path === "/api/v1/auth/email-verification/resend") return reply(route, null, 204);
      if (path === "/api/v1/auth/sessions" || path === "/api/v1/auth/events" || path === "/api/v1/reports/weekly") return reply(route, []);
      return reply(route, null, 204);
    });
    await page.goto("/settings");
    await expect(page.locator("html")).toHaveAttribute("data-theme", dark ? "dark" : "light");
    const profile = page.locator("#profile");
    const badge = profile.getByText(verified ? "Email verified" : "Email verification pending", { exact: true });
    await expect(badge).toBeVisible();
    await expect(profile.getByText(/Check Inbox and Spam\/Junk/)).toHaveCount(0);
    const box = await badge.boundingBox();
    const card = await profile.boundingBox();
    expect(box!.height).toBeLessThan(45);
    expect(box!.x).toBeGreaterThan(card!.x);
    expect(box!.x + box!.width).toBeLessThan(card!.x + card!.width);
    if (!verified) {
      await profile.getByRole("button", { name: "Resend verification email" }).click();
      const notice = profile.getByRole("status").filter({ hasText: "A new verification link was sent." });
      await expect(notice).toContainText("Spam/Junk");
      const noticeBox = await notice.boundingBox();
      const saveBox = await profile.getByRole("button", { name: "Save profile" }).boundingBox();
      expect(noticeBox!.y).toBeGreaterThanOrEqual(box!.y + box!.height);
      expect(saveBox!.y).toBeGreaterThanOrEqual(noticeBox!.y + noticeBox!.height);
      expect(await notice.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    }
    await testInfo.attach("profile-email-layout", { body: await profile.screenshot(), contentType: "image/png" });
  });
}
