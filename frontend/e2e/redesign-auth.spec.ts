import { expect, test } from "@playwright/test";
for (const width of [320, 768, 1280]) test(`approved authentication positioning at ${width}px`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Paper Trade Like Never Before.", exact: true })).toBeVisible();
  await expect(page.getByText("Research smarter, Practice Better.", { exact: true })).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  expect(await page.evaluate(async () => {
    const faces = await Promise.all([document.fonts.load('700 24px "Plus Jakarta Sans"'), document.fonts.load('400 14px Inter'), document.fonts.load('700 16px "JetBrains Mono"')]);
    return faces.every(group => group.length > 0 && group.every(face => face.status === "loaded"));
  })).toBe(true);
  await testInfo.attach(`registration-${width}`, {body:await page.screenshot({fullPage:true}),contentType:"image/png"});
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("link", { name: "Forgot password?" })).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("autocomplete", "current-password");
  await testInfo.attach(`login-${width}`, {body:await page.screenshot({fullPage:true}),contentType:"image/png"});
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page.getByLabel("Display name")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
