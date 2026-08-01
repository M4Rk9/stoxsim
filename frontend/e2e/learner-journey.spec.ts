import { expect, test } from "@playwright/test";

test.setTimeout(process.env.EXPECT_US_MARKET_DATA === "true" ? 480_000 : 60_000);

test("a learner can create, restore and reopen an India portfolio", async ({ page }) => {
  const email = `browser-${Date.now()}-${Math.random().toString(16).slice(2)}@stoxsim.test`;
  const password = "Browser-acceptance-2026";

  await page.goto("/");
  await page.getByLabel("Display name").fill("Browser Learner");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Start Now!" }).click();

  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("₹5,00,000.00");
  await expect(page.locator(".metric").filter({ hasText: "Available cash" }))
    .toContainText("₹5,00,000.00");
  await expect(page.locator(".streamBadge")).toBeVisible({ timeout: 15_000 });

  await page.getByRole("button", { name: /US/ }).click();
  await expect(page.getByText("USA PORTFOLIO")).toBeVisible();
  await expect(page.getByRole("button", { name: /US/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("$10,000.00");

  if (process.env.EXPECT_US_MARKET_DATA === "true") {
    await expect(async () => {
      await page.getByPlaceholder("Search Apple, Nvidia, SPY…").fill("Apple");
      await page.getByRole("button", { name: "Search" }).click();
      await expect(page.locator(".searchResults button strong").filter({ hasText: /^AAPL$/ }))
        .toBeVisible({ timeout: 5_000 });
    }).toPass({
      timeout: 300_000,
      intervals: [5_000, 10_000],
    });

    await page.getByRole("button", { name: /India/ }).click();
    await page.getByRole("button", { name: /US/ }).click();
    await expect(page.locator(".indexCard").first())
      .not.toContainText("UNAVAILABLE", { timeout: 120_000 });
  }

  await page.getByRole("button", { name: /India/ }).click();
  await expect(page.getByText("INDIA PORTFOLIO")).toBeVisible();
  await expect(page.getByRole("button", { name: /India/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("₹5,00,000.00");

  await page.reload();
  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "My Watchlist" })).toBeVisible();

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await expect(page.getByRole("menuitem", { name: /Account settings/ })).toBeVisible();
  await page.getByRole("menuitem", { name: /Sign out/ }).click();
  await expect(page.getByRole("heading", { name: "Your first virtual portfolio" })).toBeVisible();

  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Open dashboard" }).click();

  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("₹5,00,000.00");
});
