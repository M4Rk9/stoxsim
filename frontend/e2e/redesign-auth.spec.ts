import { expect, test } from "@playwright/test";
for (const width of [320, 768, 1280]) test(`approved authentication positioning at ${width}px`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Paper Trade Like Never Before.", exact: true })).toBeVisible();
  await expect(page.getByText("Research smarter, Practice Better.", { exact: true })).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  const fonts = await page.evaluate(() => ({
    body: getComputedStyle(document.body).fontFamily,
    heading: getComputedStyle(document.querySelector("h1")!).fontFamily,
    loaded: Array.from(document.fonts).map(face => face.family),
  }));
  expect(fonts.body).toContain("Inter");
  expect(fonts.body).toContain("system-ui");
  expect(fonts.heading).toBe(fonts.body);
  expect(fonts.loaded).not.toContain("Plus Jakarta Sans");
  expect(fonts.loaded).not.toContain("JetBrains Mono");
  await testInfo.attach(`registration-${width}`, {body:await page.screenshot({fullPage:true}),contentType:"image/png"});
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("link", { name: "Forgot password?" })).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("autocomplete", "current-password");
  await testInfo.attach(`login-${width}`, {body:await page.screenshot({fullPage:true}),contentType:"image/png"});
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page.getByLabel("Display name")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

for (const path of ["/", "/paper-trading", "/privacy"]) test(`official social links in footer at ${path}`, async ({page}) => {
  await page.setViewportSize({width:390,height:844}); await page.goto(path);
  const nav=page.getByRole("navigation",{name:"StoxSim social media",exact:true});
  for (const [name,url] of [["Instagram","https://www.instagram.com/stoxsim/"],["X","https://x.com/stoxsim"],["Reddit","https://www.reddit.com/r/StoxSimOfficial/"],["LinkedIn","https://www.linkedin.com/company/stoxsim"]]) {
    const link=nav.getByRole("link",{name,exact:true});
    await expect(link).toHaveAttribute("href",url);
    await expect(link).toHaveAttribute("target","_blank");
    await expect(link).toHaveAttribute("rel","noopener noreferrer");
  }
  await nav.scrollIntoViewIfNeeded(); await expect(nav).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
