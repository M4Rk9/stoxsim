import { expect, test } from "@playwright/test";
const rate = { eligible: 4, returned: 2, immature: 1, excluded: 1, percent: 50 };
const row = { cohort: "2026-11-02", registrations: 6, journeyEligible: 4, journeyImmature: 1, journeyExcluded: 1,
  researched: 3, watchlisted: 2, strategyCreated: 2, traded: 2, reviewed: 1, linkedJourneyCompleted: 1,
  d1: rate, d7: { ...rate, returned: 0, percent: 0 }, days1To7: rate,
  followingWeek: { eligible: 0, returned: 0, immature: 5, excluded: 1, percent: null } };
for (const [width,dark] of [[390,true],[1440,false],[1440,true]] as const) test(`research cohorts are bounded and readable at ${width} ${dark ? "dark" : "light"}`, async ({ page }, info) => {
  await page.setViewportSize({ width, height: 1000 });
  await page.addInitScript(dark => {
    sessionStorage.setItem("stoxsim-session",JSON.stringify({accessToken:"fixture",user:{id:"cohort-owner",displayName:"Example owner",email:"owner@example.test",platformAdmin:true}}));
    localStorage.setItem("stoxsim-theme:cohort-owner", dark ? "dark" : "light");
  },dark);
  let fail=false; const groups:string[]=[];
  await page.route("**/api/v1/**", async route => {
    const req=route.request(), url=new URL(req.url()); let body:unknown={}; let status=200;
    if(url.pathname.endsWith("analytics/overview")) body={version:"owner-analytics-v1",from:"2026-11-01",to:"2026-11-14",timeZone:"UTC",generatedAt:"2026-11-15T00:00:00Z",users:{registered:6,emailVerified:4},cohort:{registered:6,firstTradeCompleted:2,firstTradePercent:33.3},signups:[],orders:[]};
    if(url.pathname.endsWith("analytics/activity")) status=503;
    if(url.pathname.endsWith("analytics/cohorts")) {
      const group=url.searchParams.get("groupBy")??"WEEK"; groups.push(group);status=fail?403:200;
      body=fail?{message:"Forbidden"}:{version:"research-cohorts-v1",activityAvailableFrom:"2026-09-01T00:00:00Z",journeyAvailableFrom:"2026-11-01T00:00:00Z",signupDimensionsStartedAt:"2026-11-01T00:00:00Z",rows:[{...row,cohort:group==="SOURCE"?"SOCIAL":group==="DEVICE"?"ANDROID_APP":row.cohort}]};
    }
    await route.fulfill({status:req.method()==="OPTIONS"?204:status,headers:{"Content-Type":"application/json","Access-Control-Allow-Origin":req.headers().origin??"http://localhost:3000","Access-Control-Allow-Credentials":"true","Access-Control-Allow-Headers":"authorization,content-type","Access-Control-Allow-Methods":"GET,POST,OPTIONS"},body:req.method()==="OPTIONS"?undefined:JSON.stringify(body)});
  });
  await page.goto("/admin/analytics");
  const panel=page.getByRole("region",{name:"Research and return cohorts",exact:true});
  await expect(panel.getByText("2026-11-02",{exact:true})).toHaveCount(2);
  const returns=panel.getByRole("region",{name:"Return cohort table",exact:true});
  await expect(returns).toContainText("0.0%"); await expect(returns).toContainText("—");
  await page.getByLabel("Group registrations by").selectOption("SOURCE");
  await expect(panel.getByRole("rowheader",{name:"Social",exact:true})).toHaveCount(2);
  await page.getByLabel("Group registrations by").selectOption("DEVICE");
  await expect(panel.getByRole("rowheader",{name:"Android app",exact:true})).toHaveCount(2);
  expect(groups).toContain("SOURCE");expect(groups).toContain("DEVICE");
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await panel.evaluate(el=>{const banner=document.createElement("p");banner.textContent="REVIEW FIXTURE · Synthetic cohort counts · Not production";el.prepend(banner);});
  await info.attach(`research-cohorts-${width}-${dark?"dark":"light"}`,{body:await panel.screenshot(),contentType:"image/png"});
  fail=true;await page.getByLabel("Group registrations by").selectOption("WEEK");
  await expect(panel.getByRole("alert")).toContainText("Cohorts could not be loaded");
  await expect(panel.getByRole("table")).toHaveCount(0);
});
