import { expect, test } from "@playwright/test";

test("notifications require explicit preference changes and reject unsafe links", async ({ page }) => {
  const user = { id: "notifications-user", displayName: "Notification Learner", email: "notifications@example.test", emailVerified: true, passwordLoginEnabled: true, accounts: [] };
  await page.addInitScript(user => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user })), user);
  let preferences = { learning: true, competitions: false, portfolioEducation: false, productAnnouncements: false, promotionalMessages: false, pushEnabled: false, emailDigest: false };
  let saves = 0, read = false;
  await page.route("**/api/v1/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    const headers = { "Content-Type": "application/json", "Access-Control-Allow-Origin": request.headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,PUT,OPTIONS" };
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    if (path.endsWith("notifications/preferences") && request.method() === "PUT") { preferences = request.postDataJSON(); saves++; }
    if (path.endsWith("notifications/note/read")) read = true;
    const body = path.endsWith("notifications/preferences") ? preferences
      : path.endsWith("notifications") ? { unreadCount: read ? 0 : 1, items: [{ id: "note", category: "LEARNING", title: "Learning progress available", body: "Review your latest learning mission.", href: "javascript:alert(1)", createdAt: "2026-10-01T12:00:00Z", readAt: read ? "2026-10-02T12:00:00Z" : null }] }
        : path.endsWith("auth/me") ? user : path.endsWith("learner/profile") ? { experienceLevel: null, learningObjective: null, preferredMarket: null, updatedAt: null }
          : path.endsWith("subscription") ? { plan: "FREE", status: "ACTIVE", plans: [], sandboxAccounts: [], sandboxProvisioning: {} }
            : path.endsWith("preferences") ? { enabled: false, emailEnabled: false, zoneId: "Asia/Kolkata" } : [];
    return route.fulfill({ status: 200, headers, body: JSON.stringify(body) });
  });
  await page.goto("/settings#notifications");
  const section = page.getByRole("region", { name: "Notifications and preferences" });
  await expect(section.getByRole("link", { name: "View update" })).toHaveCount(0);
  await expect(page.getByLabel("Promotional messages and offers", { exact: true })).not.toBeChecked();
  expect(saves).toBe(0);
  await section.getByRole("button", { name: /Mark read:/ }).click();
  await expect(section.getByRole("button", { name: /Mark read:/ })).toHaveCount(0);
  await page.getByLabel("Promotional messages and offers", { exact: true }).check();
  await page.getByRole("button", { name: "Save notification preferences" }).click();
  await expect(page.getByText("Notification preferences saved.")).toBeVisible();
  expect(preferences.promotionalMessages).toBe(true); expect(saves).toBe(1);
  await page.reload();
  await expect(page.getByLabel("Promotional messages and offers", { exact: true })).toBeChecked();
});

test("email unsubscribe needs an explicit action and clears the URL token", async ({ page }) => {
  let calls = 0;
  await page.route("**/api/v1/notifications/unsubscribe?**", async route => {
    if (route.request().method() === "POST") calls++;
    return route.fulfill({ status: 204, headers: { "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Methods": "POST,OPTIONS" } });
  });
  await page.goto("/notifications/unsubscribe?token=opaque-test-token");
  await expect(page.getByRole("button", { name: "Stop optional notification emails" })).toBeVisible();
  expect(calls).toBe(0);
  await page.getByRole("button", { name: "Stop optional notification emails" }).click();
  await expect(page.getByRole("status")).toContainText("unsubscribe request has been processed");
  expect(calls).toBe(1);
  await expect(page).toHaveURL(/\/notifications\/unsubscribe$/);
  await expect(page.locator('meta[name="referrer"]')).toHaveAttribute("content", "no-referrer");
});
