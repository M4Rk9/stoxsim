import { test, expect } from "@playwright/test";
for (const width of [390, 1440]) test(`company evidence and news boundaries at ${width}`, async ({page}, info) => {
  await page.setViewportSize({width,height:1000});
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({accessToken:"fixture",user:{id:"research-fixture",displayName:"Example learner",email:"fixture@example.test",accounts:[]}})));
  let state = "AVAILABLE";
  await page.route("**/api/v1/**",async route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith("/quote") ? {lastPrice:200,currency:"USD",dataStatus:"STALE",pricingSource:"EXTERNAL",exchangeTimestamp:"2026-11-13T20:00:00Z"}
      : path.endsWith("/candles") ? {candles:[]}
      : path.endsWith("/insights") ? {provider:"SEC_EDGAR",status:"UNAVAILABLE",ratios:[],asOf:"2026-11-14T12:00:00Z"}
      : path.endsWith("/news") ? {status:state,provider:"Alpaca",checkedAt:"2026-11-14T12:00:00Z",message:state==="DISABLED"?"News is not available for this market yet.":"Selected headlines from the past seven days.",articles:state==="AVAILABLE"?[{headline:"Synthetic review headline — example only",source:"example.com",url:"https://example.com/story",publishedAt:"2026-11-13T10:00:00Z"},{headline:"Unsafe sample",source:"bad",url:"javascript:alert(1)",publishedAt:"2026-11-13T10:00:00Z"}]:[]}
      : {provider:"ALPACA",tradingSymbol:"AAPL",name:"Example company · synthetic fixture",exchange:"NASDAQ",currency:"USD",instrumentType:"EQUITY"};
    const options=route.request().method()==="OPTIONS";
    await route.fulfill({status:options?204:200,headers:{"Content-Type":"application/json","Access-Control-Allow-Origin":route.request().headers().origin??"http://localhost:3000","Access-Control-Allow-Credentials":"true","Access-Control-Allow-Headers":"authorization,content-type","Access-Control-Allow-Methods":"GET,POST,OPTIONS"},body:options?undefined:JSON.stringify(body)});
  });
  const errors:string[]=[]; page.on("pageerror",error=>errors.push(error.message));
  await page.goto("/stocks/NASDAQ/AAPL");
  const news=page.getByRole("article",{name:"Company news"});
  await expect(news.getByRole("link",{name:/Synthetic review headline/})).toHaveAttribute("href","https://example.com/story");
  await expect(news.getByText("Unsafe sample")).toHaveCount(0);
  await expect(news.locator("time")).toContainText("Published");
  await expect(page.getByRole("link",{name:/Examine this company with Finwiz/})).toHaveAttribute("href",/marketRegion=UNITED_STATES&exchange=NASDAQ&symbol=AAPL/);
  await news.getByText("Coverage and sources",{exact:true}).click();
  await expect(news).toContainText("Articles are not sent to Finwiz");
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await info.attach(`research-evidence-${width}`,{body:await page.screenshot({fullPage:true}),contentType:"image/png"});
  state="DISABLED";await page.reload();
  await expect(news).toContainText("News is not available");
  await expect(news.getByRole("link")).toHaveCount(0);
  state="EMPTY";await page.reload();
  await expect(news).toContainText("This does not mean nothing happened");
  expect(errors).toEqual([]);
});
