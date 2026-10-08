import { expect, test } from "@playwright/test";

test("Finwiz questions, URL inputs and generated HTML remain inert text", async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem("stoxsim-session", JSON.stringify({ accessToken: "fixture", user: { displayName: "Security", email: "security@example.test" } })));
  const markup = '<img src=x onerror="window.__xss=1"><script>window.__xss=1</script>';
  await page.route("**/api/v1/finwiz/ask", async route => {
    const options = route.request().method() === "OPTIONS";
    await route.fulfill({ status: options ? 204 : 200, headers: {
      "Content-Type": "application/json", "Access-Control-Allow-Origin": route.request().headers().origin ?? "http://localhost:3000",
      "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "authorization,content-type", "Access-Control-Allow-Methods": "POST,OPTIONS",
    }, body: options ? undefined : JSON.stringify({ answer: `${markup}\n\n[unsafe](javascript:alert(1))`, provider: "fixture", model: "fixture",
      groundedInStoxSimData: false, generatedAt: "2026-10-08T00:00:00Z", suggestedQuestions: [], disclaimer: "" }) });
  });
  await page.goto(`/finwiz?marketRegion=INDIA&symbol=${encodeURIComponent(markup)}`);
  await page.getByLabel("Your question").fill(markup);
  await page.getByRole("button", { name: "Ask Finwiz", exact: true }).click();
  await expect(page.getByText(markup, { exact: true }).last()).toBeVisible();
  await expect(page.getByText("unsafe", { exact: true })).toBeVisible();
  await expect(page.locator('main img[src="x"], main script, a[href^="javascript:"]')).toHaveCount(0);
  expect(await page.evaluate(() => (window as Window & { __xss?: number }).__xss)).toBeUndefined();
});
