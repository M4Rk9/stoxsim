import { expect, test, type Page } from "@playwright/test";

const PASSWORD = "Browser-acceptance-2026";
const PORTFOLIO_TIMEOUT = 15_000;

function uniqueEmail(label: string) {
  return `browser-${label}-${Date.now()}-${Math.random().toString(16).slice(2)}@stoxsim.test`;
}

async function registerLearner(page: Page, label: string, showsFirstTradeCoach = true) {
  const email = uniqueEmail(label);
  await page.goto("/");
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
  await page.getByLabel("Display name").fill("Browser Learner");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("checkbox", { name: /I agree to the Terms of Use/ }).check();
  const registrationResponsePromise = page.waitForResponse((response) =>
    response.request().method() === "POST"
    && response.url().endsWith("/api/v1/auth/register")
  );
  const onboardingResponsePromise = page.waitForResponse(response =>
    response.request().method() === "GET" && response.url().endsWith("/api/v1/onboarding"),
    { timeout: PORTFOLIO_TIMEOUT },
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
  const onboardingResponse = await onboardingResponsePromise;
  expect(onboardingResponse.status(), "A new learner must load authoritative onboarding state").toBe(200);
  expect((await onboardingResponse.json()).nextStep).toBe("INTRODUCTION");
  const guide = page.getByRole("dialog", { name: "Your home for practising and learning." });
  await expect(guide).toBeVisible();
  const nextButton = guide.getByRole("button", { name: "Next" });
  await expect(nextButton).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(guide.getByRole("button", { name: "Skip for now" })).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(nextButton).toBeFocused();
  await guide.getByRole("button", { name: "Next" }).click();
  await expect(page.getByRole("dialog", { name: "Know when the market is open." })).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByRole("dialog", { name: "Find a stock and place one paper trade." })).toBeVisible();
  await page.getByRole("button", { name: "Start first trade" }).click();
  if (showsFirstTradeCoach) await expect(page.getByLabel("First trade walkthrough, step 1 of 2")).toBeVisible();
  await expect(page.getByRole("heading", { name: "StoxScore" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Scenario Lab ↗", exact: true })).toHaveAttribute("target", "_blank");
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
  await expect(page.getByLabel("Portfolio account")).toHaveCount(0);
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

  const accountMenuLauncher = page.getByRole("button", { name: "Open account menu for Browser Learner" });
  await accountMenuLauncher.focus();
  await page.keyboard.press("ArrowDown");
  await expect(page.getByRole("menu")).toBeVisible();
  await expect(page.getByRole("menuitem", { name: /Portfolio/ })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("menu")).toHaveCount(0);
  await expect(accountMenuLauncher).toBeFocused();
  await accountMenuLauncher.click();
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

  const themedPages = [
    { path: "/progress", heading: "Learning path" },
    { path: "/competitions", heading: "Learning competitions" },
    { path: "/settings", heading: "Profile & security" },
  ];
  for (const themedPage of themedPages) {
    await page.goto(themedPage.path);
    await expect(page.getByRole("heading", { name: themedPage.heading })).toBeVisible();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page.locator("main")).toHaveCSS("background-color", "rgb(18, 18, 18)");
    await expect(page.locator("main")).toHaveCSS("color", "rgb(241, 241, 241)");
  }

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Good day, Browser." })).toBeVisible();

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
  await expect(portfolioPage.getByText("portfolio-insights-v1")).toHaveCount(0);
  await expect(portfolioPage.getByRole("heading", { name: "Stox Analysis", exact: true })).toBeVisible();
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

  await page.setViewportSize({ width: 390, height: 844 });
  const accountSwitcher = page.getByRole("link", { name: "Scenario Lab ↗", exact: true });
  const accountMenuButton = page.getByRole("button", {
    name: "Open account menu for Browser Learner",
  });
  await expect(accountSwitcher).toBeVisible();
  await expect(accountMenuButton).toBeVisible();
  const switcherBox = await accountSwitcher.boundingBox();
  const menuButtonBox = await accountMenuButton.boundingBox();
  expect(switcherBox).not.toBeNull();
  expect(menuButtonBox).not.toBeNull();
  if (switcherBox && menuButtonBox) {
    const overlaps = switcherBox.x < menuButtonBox.x + menuButtonBox.width
      && switcherBox.x + switcherBox.width > menuButtonBox.x
      && switcherBox.y < menuButtonBox.y + menuButtonBox.height
      && switcherBox.y + switcherBox.height > menuButtonBox.y;
    expect(overlaps).toBe(false);
  }

  await page.goto("/portfolio");
  await expect(page.getByRole("heading", { name: "Your portfolio" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Back to dashboard" })).toBeHidden();
  await accountMenuButton.click();
  const accountMenu = page.getByRole("menu");
  await expect(accountMenu).toBeVisible();
  const accountMenuBox = await accountMenu.boundingBox();
  expect(accountMenuBox).not.toBeNull();
  if (accountMenuBox) {
    expect(accountMenuBox.x).toBeGreaterThanOrEqual(0);
    expect(accountMenuBox.x + accountMenuBox.width).toBeLessThanOrEqual(390);
    expect(accountMenuBox.y + accountMenuBox.height).toBeLessThanOrEqual(844);
  }
  await page.keyboard.press("Escape");
  const mobileViewportFits = await page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth
  );
  expect(mobileViewportFits).toBe(true);
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
  await expect(page.getByLabel("Portfolio account")).toHaveCount(0);

  if (process.env.EXPECT_US_MARKET_DATA === "true") {
    await expect(async () => {
      await page.getByPlaceholder("Search Apple, Nvidia, SPY…").fill("Apple");
      await page.getByRole("button", { name: "Search", exact: true }).click();
      await expect(page.locator(".searchResults a strong").filter({ hasText: /^AAPL$/ }))
        .toBeVisible({ timeout: 5_000 });
    }).toPass({
      timeout: 300_000,
      intervals: [5_000, 10_000],
    });

    await page.getByRole("button", { name: /Quick quote for AAPL/ }).first().click();
    const stockDetailLink = page.getByRole("link", { name: /Study AAPL in detail/ });
    await expect(stockDetailLink)
      .toHaveAttribute("href", "/stocks/NASDAQ/AAPL", { timeout: 10_000 });
    await expect(stockDetailLink).toHaveAttribute("target", "_blank");
    const firstPopupPromise = page.waitForEvent("popup");
    await stockDetailLink.click();
    const firstStockDetailPage = await firstPopupPromise;
    const secondPopupPromise = page.waitForEvent("popup");
    await stockDetailLink.click();
    const secondStockDetailPage = await secondPopupPromise;

    await Promise.all([firstStockDetailPage, secondStockDetailPage].map(async (stockDetailPage) => {
      await expect(stockDetailPage).toHaveURL(/\/stocks\/NASDAQ\/AAPL$/);
      await expect(stockDetailPage.getByRole("heading", { name: "AAPL", exact: true }))
        .toBeVisible({ timeout: 120_000 });
      await stockDetailPage.close();
    }));

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

test("the market switch selects the active paid portfolio without a redundant selector", async ({ page }) => {
  test.setTimeout(150_000);
  const sandboxId = "11111111-2222-4333-8444-555555555555";
  let standardIndiaId = "";

  await page.route("**/api/v1/accounts", async (route) => {
    const response = await route.fetch();
    if (!response.ok()) {
      await route.fulfill({ response });
      return;
    }
    const accounts = await response.json();
    const standard = accounts.find((account: { accountKind: string; marketRegion: string }) =>
      account.accountKind === "STANDARD" && account.marketRegion === "INDIA"
    );
    standardIndiaId = standard?.id ?? "";
    await route.fulfill({
      response,
      json: [...accounts, {
        ...standard,
        id: sandboxId,
        accountKind: "SANDBOX",
        sandboxPlan: "PLUS",
        sandboxSlot: 1,
        accountLabel: "Plus sandbox 1",
        startingCapital: 2_500_000,
        availableCash: 2_500_000,
        leaderboardEligible: false,
      }],
    });
  });
  await page.route(`**/api/v1/accounts/${sandboxId}/**`, async (route) => {
    if (!standardIndiaId) {
      await route.abort();
      return;
    }
    const mappedUrl = route.request().url().replace(sandboxId, standardIndiaId);
    const response = await route.fetch({ url: mappedUrl });
    const json = await response.json();
    if (route.request().url().endsWith("/portfolio")) {
      await route.fulfill({
        response,
        json: {
          ...json,
          startingCapital: 2_500_000,
          availableCash: 2_500_000,
          totalAccountValue: 2_500_000,
        },
      });
      return;
    }
    await route.fulfill({ response, json });
  });

  await registerLearner(page, "sandbox-switcher", false);
  await expect(page.getByLabel("Portfolio account")).toHaveCount(0);

  await expect(page.getByText("SANDBOX · INDIA PORTFOLIO"))
    .toBeVisible({ timeout: PORTFOLIO_TIMEOUT });
  await expect(page.getByText("Learning sandbox · excluded from standard rankings")).toHaveCount(0);
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("₹25,00,000.00", { timeout: PORTFOLIO_TIMEOUT });
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
  await 