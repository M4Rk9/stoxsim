import { test, expect, type Page } from "@playwright/test";
async function setup(page: Page) {
  await page.addInitScript(() => {
    if (!sessionStorage.getItem("stoxsim-session")) sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken:"fixture",user:{id:"learner",displayName:"Learner",email:"learner@example.test"} }));
  });
  await page.route("**/api/v1/finwiz/ask", route => {
    const options = route.request().method() === "OPTIONS";
    return route.fulfill({status:options ? 204 : 200,headers:{"Content-Type":"application/json","Access-Control-Allow-Origin":route.request().headers().origin ?? "http://localhost:3000","Access-Control-Allow-Credentials":"true","Access-Control-Allow-Headers":"authorization,content-type","Access-Control-Allow-Methods":"POST,OPTIONS"},body:options ? undefined : JSON.stringify({ answer:"A share represents ownership.",suggestedQuestions:["What is diversification?"],disclaimer:"Educational only.",groundedInStoxSimData:false })});
  });
}
test("Finwiz retains account-separated device history and Learn owns its map", async ({ page }) => {
  await setup(page); await page.goto("/finwiz");
  await expect(page.getByRole("navigation",{name:"Main navigation"})).toHaveCount(0);
  const history = page.getByRole("complementary",{name:"Chat history"});
  await expect(history).toBeVisible();
  await page.getByRole("button",{name:"New chat"}).click();
  await expect(page.getByLabel("Your question")).toHaveValue("");
  await page.getByLabel("Your question").fill("What is a share?");
  await page.getByRole("button",{name:"Ask Finwiz",exact:true}).click();
  await expect(page.getByText("A share represents ownership.")).toBeVisible();
  await page.reload();
  await history.getByRole("button",{name:"What is a share?",exact:true}).click();
  await expect(page.getByText("A share represents ownership.")).toBeVisible();
  await page.evaluate(() => sessionStorage.setItem("stoxsim-session",JSON.stringify({accessToken:"fixture",user:{id:"another",displayName:"Another",email:"another@example.test"}})));
  await page.reload();
  await expect(history.getByRole("button",{name:"What is a share?",exact:true})).toHaveCount(0);
  await page.evaluate(() => sessionStorage.setItem("stoxsim-session",JSON.stringify({accessToken:"fixture",user:{id:"learner",displayName:"Learner",email:"learner@example.test"}})));
  await page.reload();
  await history.getByRole("button",{name:"Clear history",exact:true}).click();
  await history.getByRole("button",{name:"Confirm clear history",exact:true}).click();
  await page.reload();
  await expect(history.getByRole("button",{name:"What is a share?",exact:true})).toHaveCount(0);
  await page.goto("/learn#learning-map");
  await expect(page.locator("#learning-map a")).toHaveCount(8);
  await page.locator('#learning-map a[href="/finwiz?topic=VALUATION"]').click();
  await expect(page.getByLabel("Your question")).toHaveValue(/Explain valuation/);
});
for (const width of [320,768,1440]) test(`Finwiz theme and keyboard layout at ${width}px`, async ({page}, info) => {
  await setup(page); await page.setViewportSize({width,height:900}); await page.goto("/finwiz");
  const input = page.getByLabel("Your question"); await expect(input).toBeVisible();
  if (width === 320) {
    await page.getByRole("button",{name:"Chat history",exact:true}).click();
    await expect(page.getByRole("link",{name:"StoxSim home",exact:true})).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("button",{name:"Chat history",exact:true})).toBeFocused();
  }
  await input.fill("How do I begin learning about stocks?"); await input.focus(); await page.keyboard.press("Tab");
  await expect(page.getByRole("button",{name:"Ask Finwiz",exact:true})).toBeFocused(); await page.keyboard.press("Enter");
  await expect(page.getByText("A share represents ownership.")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await info.attach(`finwiz-${width}`,{body:await page.screenshot({path:info.outputPath(`finwiz-${width}.png`)}),contentType:"image/png"});
  await page.evaluate(() => document.documentElement.style.fontSize = "200%"); await page.setViewportSize({width,height:500});
  await expect(page.getByRole("button",{name:"Ask Finwiz",exact:true})).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await info.attach(`finwiz-enlarged-${width}`,{body:await page.screenshot({path:info.outputPath(`finwiz-enlarged-${width}.png`)}),contentType:"image/png"});
});

test("Finwiz preserves a follow-up draft while an answer is loading", async ({ page }) => {
  await setup(page);
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/api/v1/finwiz/ask", async route => {
    await pending;
    await route.fallback();
  });
  await page.goto("/finwiz");
  const input = page.getByLabel("Your question");
  await input.fill("What is a share?");
  await page.getByRole("button", { name: "Ask Finwiz", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Finwiz is thinking…");
  await input.fill("How does diversification work?");
  release();
  await expect(page.getByText("A share represents ownership.")).toBeVisible();
  await expect(input).toHaveValue("How does diversification work?");
  await expect(page.getByRole("button", { name: "Ask Finwiz", exact: true })).toBeEnabled();
});
