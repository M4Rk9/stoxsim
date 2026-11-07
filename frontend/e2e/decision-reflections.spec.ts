import { expect, test } from "@playwright/test";

test("saving a private reflection never contacts Finwiz without explicit consent", async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { id: "reflection-user", displayName: "Reflection Learner", email: "reflection@example.test" } })));
  const items: Array<Record<string, unknown>> = []; let assistanceCalls = 0;
  await page.route("**/api/v1/**", async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    const headers = { "Content-Type": "application/json", "Access-Control-Allow-Origin": req.headers().origin ?? "http://localhost:3000", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "GET,POST,PUT,DELETE,OPTIONS" };
    if (req.method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    let body: unknown = {};
    if (path.endsWith("/reflections") && req.method() === "GET") body = items;
    if (path.endsWith("/reflections") && req.method() === "POST") { body = { ...req.postDataJSON(), id: "11111111-1111-4111-8111-111111111111", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }; items.push(body as Record<string, unknown>); }
    if (path.endsWith("/assistance")) { expect(req.postDataJSON()).toEqual({ consent: true }); assistanceCalls++; body = { answer: "Review the assumptions and what you learned.", provider: "FALLBACK", disclaimer: "Educational information only.", groundedInStoxSimData: false, generatedAt: new Date().toISOString() }; }
    return route.fulfill({ status: 200, headers, body: JSON.stringify(body) });
  });
  await page.goto("/learn");
  await page.getByText("Write a reflection", { exact: true }).click();
  await page.getByLabel("Why I made this practice decision", { exact: true }).fill("I wanted to understand concentration risk.");
  await page.getByRole("button", { name: "Save reflection", exact: true }).click();
  await expect(page.getByText("Reflection saved. Nothing was sent to Finwiz.")).toBeVisible();
  expect(assistanceCalls).toBe(0);
  await page.getByText("I wanted to understand concentration risk.", { exact: true }).first().click();
  const ask = page.getByRole("button", { name: "Ask Finwiz about this reflection" });
  await expect(ask).toBeDisabled();
  await page.getByLabel("I choose to share this saved reflection", { exact: false }).check();
  await ask.click();
  await expect(page.getByText("Review the assumptions and what you learned.")).toBeVisible();
  expect(assistanceCalls).toBe(1);
  await expect(ask).toBeDisabled();
  expect(await page.evaluate(() => Object.keys(localStorage).some(key => /reflection|journal/i.test(key)))).toBe(false);
});
