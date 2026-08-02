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

  await expect(page.getByRole("button", { name: /Switch to (dark|light) mode/ })).toHaveCount(0);
  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await expect(page.getByRole("group", { name: "Appearance" })).toBeVisible();
  await page.getByRole("button", { name: "Use dark appearance" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "dark");
  await expect(page.locator(".marketBanner")).not.toHaveCSS("background-color", "rgb(240, 242, 239)");
  await expect(page.locator(".estimateBox")).not.toHaveCSS("background-color", "rgb(246, 248, 245)");

  await page.getByRole("button", { name: "Use light appearance" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "light");
  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();

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
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "light");

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await expect(page.getByRole("menuitem", { name: /Finwiz AI/ })).toHaveCount(0);
  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();

  await page.route("**/api/v1/finwiz/ask", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        answer: "### Evaluate valuation\n\nA **high-quality company** can still be overpriced.\n\n$$\\text{P/E Ratio} = \\frac{\\text{Share Price}}{\\text{Earnings Per Share}}$$\n\n* Compare the ratio with direct peers.\n* Check whether growth supports the premium.\n\n---\n\n### Risks and limitations\n\nDo not treat one ratio as a buy or sell signal.",
        provider: "GEMINI",
        model: "gemini-3.6-flash",
        groundedInStoxSimData: false,
        generatedAt: new Date().toISOString(),
        suggestedQuestions: ["When can a low P/E be misleading?"],
        disclaimer: "Educational information only.",
      }),
    });
  });

  await page.getByRole("link", { name: "Ask Finwiz AI" }).click();
  await expect(page.getByRole("heading", { name: "FINWIZ AI" })).toBeVisible();
  await expect(page.getByRole("img", { name: "Interactive Finwiz topic selector" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Select Technical analysis" })).toBeVisible();
  await page.getByLabel("Your question").fill("How should a beginner evaluate valuation?");
  await page.getByRole("button", { name: /Transmit question/ }).click();

  const report = page.locator("#finwiz-response");
  await expect(report).toBeVisible();
  await expect(report.getByRole("heading", { name: "Evaluate valuation" })).toBeVisible();
  await expect(report).toContainText("P/E Ratio = (Share Price ÷ Earnings Per Share)");
  await expect(report).not.toContainText("###");
  await expect(report).not.toContainText("**");
  await expect(report).not.toContainText("$$");
  await page.getByRole("link", { name: "Back to dashboard" }).click();
  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();

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
