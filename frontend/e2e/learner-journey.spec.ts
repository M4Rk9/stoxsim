import { expect, test, type Page } from "@playwright/test";

const PASSWORD = "Browser-acceptance-2026";
const PORTFOLIO_TIMEOUT = 15_000;

function uniqueEmail(label: string) {
  return `browser-${label}-${Date.now()}-${Math.random().toString(16).slice(2)}@stoxsim.test`;
}

async function registerLearner(page: Page, label: string) {
  const email = uniqueEmail(label);
  await page.goto("/");
  await page.getByLabel("Display name").fill("Browser Learner");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Start Now!", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  return email;
}

async function expectIndiaAccount(page: Page) {
  await expect(page.getByText("INDIA PORTFOLIO")).toBeVisible({ timeout: PORTFOLIO_TIMEOUT });
  await expect(page.getByRole("button", { name: /India/ }))
    .toHaveAttribute("aria-pressed", "true", { timeout: PORTFOLIO_TIMEOUT });
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("₹5,00,000.00", { timeout: PORTFOLIO_TIMEOUT });
  await expect(page.locator(".metric").filter({ hasText: "Available cash" }))
    .toContainText("₹5,00,000.00", { timeout: PORTFOLIO_TIMEOUT });
}

test("a learner can register, persist appearance and sign in again", async ({ page }) => {
  test.setTimeout(150_000);
  const email = await registerLearner(page, "auth");

  const browserSession = await page.evaluate(() =>
    window.sessionStorage.getItem("stoxsim-session")
  );
  expect(browserSession).not.toBeNull();
  expect(JSON.parse(browserSession ?? "{}").refreshToken).toBeUndefined();
  expect(await page.evaluate(() =>
    window.localStorage.getItem("stoxsim-session")
  )).toBeNull();

  const refreshCookie = (await page.context().cookies()).find(
    (cookie) => cookie.name === "stoxsim_refresh",
  );
  expect(refreshCookie?.httpOnly).toBe(true);
  expect(refreshCookie?.sameSite).toBe("Strict");

  await expectIndiaAccount(page);
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

  await page.reload();
  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "My Watchlist" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "light");

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await expect(page.getByRole("menuitem", { name: /Account settings/ })).toBeVisible();
  await page.getByRole("menuitem", { name: /Sign out/ }).click();
  await expect(page.getByRole("heading", { name: "Your first virtual portfolio" })).toBeVisible();

  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Open dashboard", exact: true })).toBeVisible();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Open dashboard", exact: true }).click();

  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  await expectIndiaAccount(page);
});

test("a learner can switch between India and United States markets", async ({ page }) => {
  test.setTimeout(process.env.EXPECT_US_MARKET_DATA === "true" ? 480_000 : 90_000);
  await registerLearner(page, "markets");

  await expectIndiaAccount(page);

  await page.getByRole("button", { name: /US/ }).click();
  await expect(page.getByText("USA PORTFOLIO")).toBeVisible({ timeout: PORTFOLIO_TIMEOUT });
  await expect(page.getByRole("button", { name: /US/ }))
    .toHaveAttribute("aria-pressed", "true", { timeout: PORTFOLIO_TIMEOUT });
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("$10,000.00", { timeout: PORTFOLIO_TIMEOUT });

  if (process.env.EXPECT_US_MARKET_DATA === "true") {
    await expect(async () => {
      await page.getByPlaceholder("Search Apple, Nvidia, SPY…").fill("Apple");
      await page.getByRole("button", { name: "Search", exact: true }).click();
      await expect(page.locator(".searchResults button strong").filter({ hasText: /^AAPL$/ }))
        .toBeVisible({ timeout: 5_000 });
    }).toPass({
      timeout: 300_000,
      intervals: [5_000, 10_000],
    });

    await page.locator(".searchResults button").filter({ hasText: /^AAPL/ }).first().click();
    await expect(page.getByRole("link", { name: /Study AAPL in detail/ }))
      .toHaveAttribute("href", "/stocks/NASDAQ/AAPL", { timeout: 10_000 });
    const fundamentals = page.locator(".fundamentalsSection");
    await expect(fundamentals)
      .toContainText("SEC EDGAR filings", { timeout: 120_000 });
    await expect(fundamentals)
      .toContainText("USD million", { timeout: 120_000 });
    await expect(fundamentals)
      .not.toContainText("INR crore");

    await page.getByRole("button", { name: /India/ }).click();
    await page.getByRole("button", { name: /US/ }).click();
    await expect(page.locator(".indexCard").first())
      .not.toContainText("UNAVAILABLE", { timeout: 120_000 });
  }

  await page.getByRole("button", { name: /India/ }).click();
  await expectIndiaAccount(page);
});

test("Finwiz reactor renders a clean, accessible answer", async ({ page }) => {
  test.setTimeout(150_000);
  await registerLearner(page, "finwiz");

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
  await page.getByRole("button", { name: "Select Technical analysis" }).focus();
  await expect(page.getByRole("button", { name: "Select Technical analysis" })).toBeFocused();

  await page.getByLabel("Your question").fill("How should a beginner evaluate valuation?");
  await page.getByRole("button", { name: /Transmit question/ }).click();

  const report = page.locator("#finwiz-response");
  await expect(report).toBeVisible();
  await expect(report.getByRole("heading", { name: "Evaluate valuation" })).toBeVisible();
  await expect(report).toContainText("A high-quality company can still be overpriced.");
  await expect(report).toContainText("P/E Ratio = (Share Price ÷ Earnings Per Share)");
  await expect(report).not.toContainText("###");
  await expect(report).not.toContainText("**");
  await expect(report).not.toContainText("$$");

  await page.getByRole("link", { name: "Back to dashboard" }).click();
  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
});
