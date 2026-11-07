import { test, expect } from "@playwright/test";
test("Finwiz has device history, no bottom navigation, and Learn owns its map", async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken:"fixture",user:{id:"learner",displayName:"Learner",email:"learner@example.test"} })));
  await page.route("**/api/v1/finwiz/ask", route => route.fulfill({status:200,headers:{"Content-Type":"application/json","Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"*"},body:JSON.stringify({ answer:"A share represents ownership.",suggestedQuestions:[],disclaimer:"Educational only.",groundedInStoxSimData:false })}));
  await page.goto("/finwiz");
  await expect(page.getByRole("navigation",{name:"Main navigation"})).toHaveCount(0);
  await expect(page.getByRole("complementary",{name:"Chat history"})).toBeVisible();
  await page.getByRole("button",{name:"New chat"}).click();
  await expect(page.getByLabel("Your question")).toHaveValue("");
  await page.goto("/learn#learning-map");
  await expect(page.locator("#learning-map a")).toHaveCount(8);
  await page.locator("#learning-map a").filter({hasText:"Valuation"}).click();
  await expect(page.getByLabel("Your question")).toHaveValue(/Explain valuation/);
});
