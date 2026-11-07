import { expect, test } from "@playwright/test";

test("optional preferences persist and can be cleared without changing authentication", async ({ page }, testInfo) => {
  const user = { id: "profile-user", displayName: "Profile Learner", email: "profile@example.test", emailVerified: true, passwordLoginEnabled: true, accounts: [] };
  await page.addInitScript(user => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user })), user);
  let profile = { experienceLevel: null as string | null, learningObjective: null as string | null, preferredMarket: null as string | null, updatedAt: null as string | null };
  await page.route("**/api/v1/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers: { "Access-Control-Allow-Origin": request.headers().origin, "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,PUT,OPTIONS" } });
    if (path.endsWith("learner/profile") && request.method() === "PUT") profile = { ...request.postDataJSON(), updatedAt: new Date().toISOString() };
    const body = path.endsWith("learner/profile") ? profile : path.endsWith("auth/me") ? user
      : path.endsWith("subscription") ? { plan: "FREE", status: "ACTIVE", plans: [], sandboxAccounts: [], sandboxProvisioning: {} }
        : path.endsWith("preferences") ? { enabled: false, emailEnabled: false, zoneId: "Asia/Kolkata" } : [];
    return route.fulfill({ status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": request.headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true" }, body: JSON.stringify(body) });
  });
  await page.goto("/settings");
  const preferences = page.locator("#learning-preferences");
  await expect(preferences.getByRole("button", { name: "Save learning preferences" })).toHaveCSS("margin-top", "12px");
  await testInfo.attach("learning-preferences", { body: await preferences.screenshot(), contentType: "image/png" });
  await page.getByLabel("Experience level", { exact: true }).selectOption("BEGINNER");
  await page.getByLabel("Primary learning objective", { exact: true }).selectOption("DIVERSIFICATION");
  await page.getByLabel("Preferred market", { exact: true }).selectOption("UNITED_STATES");
  await page.getByRole("button", { name: "Save learning preferences" }).click();
  await expect(page.getByText("Learning preferences saved across your devices.")).toBeVisible();
  await page.reload();
  await expect(page.getByLabel("Preferred market", { exact: true })).toHaveValue("UNITED_STATES");
  await page.getByLabel("Experience level", { exact: true }).selectOption("");
  await page.getByRole("button", { name: "Save learning preferences" }).click();
  await expect.poll(() => profile.experienceLevel).toBeNull();
  expect(JSON.parse(await page.evaluate(() => sessionStorage.getItem("stoxsim-session")) ?? "{}").user.id).toBe(user.id);
});
