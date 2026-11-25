import {test,expect} from "@playwright/test";
for (const width of [320,768,1280]) test(`recovery and Google UI at ${width}px`,async ({page},testInfo)=>{
  await page.setViewportSize({width,height:900});
  await page.route("**/api/v1/auth/google/config",route=>route.fulfill({status:503,contentType:"application/json",body:"{}"}));
  for(const item of [{path:"/forgot-password",title:"Reset your password"},{path:"/reset-password",title:"Choose a new password"},{path:"/verify-email",title:"Link not accepted"},{path:"/google-sign-in",title:"Sign in with Google"}]){
    await page.goto(item.path);
    await expect(page.getByRole("heading",{name:item.title,exact:true})).toBeVisible();
    await page.evaluate(()=>document.fonts.ready);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await testInfo.attach(`${item.path.slice(1)}-${width}`,{body:await page.screenshot({fullPage:true}),contentType:"image/png"});
  }
});
