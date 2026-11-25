import { test, expect } from "@playwright/test";
test("desktop workspace search uses real instrument API contract and closes by Escape", async ({page}) => {
  await page.setViewportSize({width:1280,height:900});
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session",JSON.stringify({accessToken:"fixture",user:{id:"nav-user",displayName:"Navigation Learner",email:"nav@example.test"}})));
  await page.route("**/api/v1/**", route => route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({})}));
  await page.route("**/api/v1/instruments/search?**", route => {
    const url = new URL(route.request().url());
    expect(url.searchParams.get("marketRegion")).toBe("INDIA");
    expect(url.searchParams.get("q")).toBe("TCS");
    return route.fulfill({status:200,contentType:"application/json",body:JSON.stringify([{id:"tcs",tradingSymbol:"TCS",name:"Tata Consultancy Services",exchange:"NSE",marketRegion:"INDIA",instrumentType:"EQUITY"}])});
  });
  await page.goto("/learn");
  await page.getByRole("button",{name:"Search instruments across StoxSim"}).click();
  const dialog=page.getByRole("dialog",{name:"Search instruments"});
  await dialog.getByRole("searchbox",{name:"Stock or ETF"}).fill("TCS");
  await expect(dialog.getByRole("link",{name:/TCS/})).toHaveAttribute("href","/stocks/NSE/TCS");
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
});
