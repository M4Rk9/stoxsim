import { expect, test } from "@playwright/test";

test("registration and recovery explain where to look for email without revealing account existence", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByText(/Check Inbox and Spam\/Junk/)).toBeVisible();
  await page.route("**/api/v1/auth/password/forgot", async route => {
    const headers = { "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "content-type", "Access-Control-Allow-Methods": "POST,OPTIONS" };
    await route.fulfill({ status: 204, headers });
  });
  await page.goto("/forgot-password");
  await page.getByLabel("Email address").fill("unknown@example.test");
  await page.getByRole("button", { name: "Send reset link" }).click();
  await expect(page.getByText(/If an account exists for this email/)).toContainText("Spam/Junk");
});

test("Finwiz displays the response's authoritative depth", async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { displayName: "Learner", email: "learner@example.test" } })));
  await page.route("**/api/v1/finwiz/ask", async route => {
    const options = route.request().method() === "OPTIONS";
    await route.fulfill({ status: options ? 204 : 200, headers: {
      "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
      "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "POST,OPTIONS",
    }, body: options ? undefined : JSON.stringify({ answer: "A concise educational explanation.", provider: "fixture", model: "fixture", finwizTier: "BASIC", groundedInStoxSimData: false, generatedAt: "2026-10-16T00:00:00Z", suggestedQuestions: [], disclaimer: "Education only" }) });
  });
  await page.goto("/finwiz");
  await page.getByLabel("Your question").fill("Explain price to earnings.");
  await page.getByRole("button", { name: "Ask Finwiz", exact: true }).click();
  await expect(page.getByText(/· basic depth/)).toBeVisible();
});
