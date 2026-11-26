import {test,expect} from "@playwright/test";
for(const width of [320,768,1440]) test(`M2 public system status renders without page overflow at ${width}px`,async ({page},info)=>{
  await page.setViewportSize({width,height:900});
  await page.goto("/status");
  await expect(page.getByRole("heading",{level:1})).toBeVisible();
  await page.evaluate(()=>document.fonts.ready);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await info.attach(`m2-status-${width}`,{body:await page.screenshot({fullPage:true}),contentType:"image/png"});
});
