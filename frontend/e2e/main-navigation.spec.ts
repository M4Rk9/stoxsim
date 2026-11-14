import { expect, test } from "@playwright/test";

for (const width of [320, 1280]) test(`core areas and journal remain reachable at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "nav-user", displayName: "Navigation Learner", email: "nav@example.test" } })));
  await page.route("**/api/v1/**", route => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.goto("/learn");
  const nav = page.getByRole("navigation", { name: "Main navigation" });
  await expect(nav.getByRole("link")).toHaveCount(5);
  for (const label of ["Home", "Markets", "Learn", "Compete", "Account"]) {
    const link = nav.getByRole("link", { name: label, exact: true });
    await expect(link).toBeInViewport();
    expect((await link.boundingBox())!.height).toBeGreaterThanOrEqual(44);
    expect((await link.boundingBox())!.width).toBeGreaterThanOrEqual(44);
    expect(await link.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  }
  await expect(nav.getByRole("link", { name: "Learn", exact: true })).toHaveAttribute("aria-current", "page");
  await expect(nav.getByRole("link", { name: "Markets", exact: true })).toHaveAttribute("href", "/#stock-search");
  await expect(page.getByRole("link", { name: /Your learning path/ })).toHaveAttribute("href", "/progress");
  await expect(page.getByRole("link", { name: /Scenario Lab/ }).first()).toHaveAttribute("href", "/scenario-lab");
  await expect(page.getByRole("link", { name: /Strategy journal/ })).toHaveAttribute("href", "/journal");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("main navigation is absent for a signed-out visitor", async ({ page }) => {
  await page.goto("/learn");
  await expect(page.getByRole("navigation", { name: "Main navigation" })).toHaveCount(0);
});
