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
  await page.getByRole("checkbox", { name: /I agree to the Terms of Use/ }).check();
  const registrationResponsePromise = page.waitForResponse((response) =>
    response.request().method() === "POST"
    && response.url().endsWith("/api/v1/auth/register")
  );
  await page.getByRole("button", { name: "Start Now!", exact: true }).click();
  const registrationResponse = await registrationResponsePromise;
  if (!registrationResponse.ok()) {
    throw new Error(
      `Registration failed with ${registrationResponse.status()}: ${await registrationResponse.text()}`,
    );
  }
  await expect(page.getByRole("heading", { name: "Good day, Browser." }))
    .toBeVisible({ timeout: PORTFOLIO_TIMEOUT });
  const guide = page.getByRole("dialog", { name: "Two markets. Zero real-money risk." });
  await expect(guide).toBeVisible();
  await guide.getByRole("button", { name: "Next" }).click();
  await expect(page.getByRole("dialog", { name: "Know how fresh every price is." })).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByRole("dialog", { name: "Find a stock and place one paper trade." })).toBeVisible();
  await page.getByRole("button", { name: "Start first trade" }).click();
  await expect(page.getByLabel("First trade walkthrough, step 1 of 2")).toBeVisible();
  await expect(page.getByRole("heading", { name: "StoxScore" })).toBeVisible();
  await expect(page.getByText("Not scored yet")).toBeVisible();
  await expect(page.getByText("stoxscore-portfolio-v1")).toBeVisible();
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
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "light");

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await expect(page.getByRole("group", { name: "Appearance" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Use system appearance" })).toHaveCount(0);
  await page.getByRole("button", { name: "Use dark appearance" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "dark");
  await expect(page.locator(".marketBanner")).not.toHaveCSS("background-color", "rgb(240, 242, 239)");
  await expect(page.locator(".estimateBox")).not.toHaveCSS("background-color", "rgb(246, 248, 245)");

  await page.reload();
  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "My Watchlist" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "dark");

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  const portfolioLink = page.getByRole("menuitem", { name: /Portfolio/ });
  await expect(portfolioLink).toHaveAttribute("target", "_blank");
  const portfolioPopup = page.waitForEvent("popup");
  await portfolioLink.click();
  const portfolioPage = await portfolioPopup;
  await expect(portfolioPage.getByRole("heading", { name: "Your portfolio" }))
    .toBeVisible({ timeout: PORTFOLIO_TIMEOUT });
  await expect(portfolioPage.getByRole("heading", { name: "Allocation and performance" }))
    .toBeVisible();
  await expect(portfolioPage.getByText("portfolio-insights-v1")).toBeVisible();
  await expect(portfolioPage.getByRole("heading", { name: "India holdings" })).toBeVisible();
  await portfolioPage.close();

  await page.getByRole("menuitem", { name: /Sign out/ }).click();
  await expect(page.getByRole("heading", { name: "Your first virtual portfolio" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");

  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Open dashboard", exact: true })).toBeVisible();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Open dashboard", exact: true }).click();

  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();
  await expectIndiaAccount(page);
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(page.locator("html")).toHaveAttribute("data-theme-preference", "dark");
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

test("guided onboarding progress and dismissal persist across sessions", async ({ page }) => {
  test.setTimeout(150_000);
  await registerLearner(page, "onboarding");

  await page.reload();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const coach = page.getByLabel("First trade walkthrough, step 1 of 2");
  await expect(coach).toBeVisible({ timeout: PORTFOLIO_TIMEOUT });

  const dismissResponse = page.waitForResponse((response) =>
    response.request().method() === "POST"
    && response.url().endsWith("/api/v1/onboarding/dismiss")
  );
  await coach.getByRole("button", { name: "Dismiss guide" }).click();
  await expect((await dismissResponse).ok()).toBe(true);
  await expect(coach).toHaveCount(0);

  await page.reload();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByLabel(/First trade walkthrough/)).toHaveCount(0);
});

test("learning progression awards authoritative missions and daily check-ins once", async ({ page }) => {
  test.setTimeout(150_000);
  await registerLearner(page, "progression");

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await page.getByRole("menuitem", { name: /Learning path/ }).click();

  await expect(page.getByRole("heading", { name: "Learning path" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Level progress" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Learning foundations" })).toBeVisible();
  await expect(page.getByText("learning-progression-v1", { exact: true })).toBeVisible();
  await expect(page.getByText("50 XP", { exact: false }).first()).toBeVisible();

  const checkInResponse = page.waitForResponse((response) =>
    response.request().method() === "POST"
    && response.url().endsWith("/api/v1/progression/check-in")
  );
  await page.getByRole("button", { name: "Record today’s check-in" }).click();
  await expect((await checkInResponse).ok()).toBe(true);
  await expect(page.getByRole("button", { name: "Checked in today" })).toBeDisabled();
  await expect(page.getByText("1 day", { exact: true })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("button", { name: "Checked in today" })).toBeDisabled();
  await expect(page.getByText("1 day", { exact: true })).toBeVisible();
});

test("a learner can opt into the standard season and create a private league", async ({ page }) => {
  test.setTimeout(150_000);
  await registerLearner(page, "competitions");

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await page.getByRole("menuitem", { name: /Competitions/ }).click();

  await expect(page.getByRole("heading", { name: "Learning competitions" })).toBeVisible();
  await expect(page.getByText("standard-india-entry-return-v1", { exact: true })).toBeVisible();
  await expect(page.getByText(/global display-name disclosure an explicit, separate choice/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Create", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Join", exact: true })).toBeDisabled();
  const enrollmentResponse = page.waitForResponse((response) =>
    response.request().method() === "POST"
    && response.url().endsWith("/api/v1/competitions/current/enroll")
  );
  await page.getByRole("button", { name: "Join standard leaderboard" }).click();
  await expect((await enrollmentResponse).ok()).toBe(true);
  await expect(page.getByRole("region", { name: "Your competition position" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Create", exact: true })).toBeEnabled();
  await expect(page.getByText("Browser Learner", { exact: true }).first()).toBeVisible();

  await page.getByLabel("Create a league").fill("Browser Study Circle");
  const creationResponse = page.waitForResponse((response) =>
    response.request().method() === "POST"
    && response.url().endsWith("/api/v1/leagues")
  );
  await page.getByRole("button", { name: "Create", exact: true }).click();
  await expect((await creationResponse).ok()).toBe(true);
  await expect(page.getByRole("status")).toContainText("SHOWN ONCE");
  await expect(page.getByRole("status").locator("code")).toContainText(/^STX-/);

  let refreshRequests = 0;
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().endsWith("/api/v1/auth/refresh")) {
      refreshRequests += 1;
    }
  });
  await page.evaluate(() => window.sessionStorage.removeItem("stoxsim-session"));
  await page.reload();
  await expect(page.getByRole("button", { name: /Browser Study Circle/ })).toBeVisible();
  expect(refreshRequests).toBe(1);
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


test("account settings expose recovery, sessions and portable data", async ({ page }) => {
  test.setTimeout(150_000);
  await registerLearner(page, "lifecycle");

  await page.getByRole("button", { name: "Open account menu for Browser Learner" }).click();
  await page.getByRole("menuitem", { name: /Account settings/ }).click();

  await expect(page.getByRole("heading", { name: "Profile & security" })).toBeVisible();
  await expect(page.getByText("Email verification pending")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Your free plan" })).toBeVisible();
  await expect(page.getByText("Leaderboard integrity protected")).toBeVisible();
  await expect(page.getByRole("button", { name: "Current plan" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Billing not available yet" })).toHaveCount(2);
  await expect(page.getByRole("heading", { name: "Portfolio reports" })).toBeVisible();
  await expect(page.getByText("Verify your email address before enabling delivery.")).toBeVisible();
  await page.getByRole("button", { name: "Preview current report" }).click();
  await expect(page.getByText(/PREVIEW · weekly-portfolio-report-v1/)).toBeVisible();
  await expect(page.getByText("This browser")).toBeVisible({ timeout: 15_000 });

  const exportRequest = page.waitForRequest("**/api/v1/auth/me/export");
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download account data" }).click();
  await expect((await exportRequest).method()).toBe("POST");
  await expect((await download).suggestedFilename()).toBe("stoxsim-account-export.json");

  await page.getByRole("button", { name: "Log out all devices" }).click();
  await expect(page.getByRole("heading", { name: "Your first virtual portfolio" })).toBeVisible();
});
