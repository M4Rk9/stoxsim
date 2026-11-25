import { expect, test } from "@playwright/test";
for (const width of [320, 1280]) test(`approved authentication positioning at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Paper Trade Like Never Before.", exact: true })).toBeVisible();
  await expect(page.getByText("Research smarter, Practice Better.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("link", { name: "Forgot password?" })).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("autocomplete", "current-password");
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page.getByLabel("Display name")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
