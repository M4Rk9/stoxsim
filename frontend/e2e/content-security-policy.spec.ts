import { test, expect } from "@playwright/test";

function nonce(policy: string) {
  const value = policy.match(/script-src[^;]*'nonce-([^']+)'/)?.[1];
  expect(value).toBeTruthy();
  expect(policy.match(/script-src[^;]*/)?.[0]).not.toMatch(/unsafe-inline|unsafe-eval/);
  expect(policy).toContain("script-src-attr 'none'");
  return value!;
}

test("fresh trusted nonces match hydration scripts and cannot be supplied by a caller", async ({ page, request }) => {
  const first = await request.get("/", { headers: { "x-nonce": "attacker", "Content-Security-Policy": "script-src 'unsafe-inline'" } });
  const firstNonce = nonce(first.headers()["content-security-policy"]);
  expect(firstNonce).not.toBe("attacker");
  expect(first.headers()["cache-control"]).toContain("no-store");
  const response = await page.goto("/");
  const secondNonce = nonce(response!.headers()["content-security-policy"]);
  expect(secondNonce).not.toBe(firstNonce);
  const scripts = await page.locator("script").evaluateAll(elements => elements
    .filter(element => (!element.getAttribute("type") || element.getAttribute("type") === "text/javascript") && element.textContent)
    .map(element => (element as HTMLScriptElement).nonce));
  expect(scripts.length).toBeGreaterThan(1);
  expect(scripts.every(value => value === secondNonce)).toBe(true);
  // The theme bootstrap and React hydration must still execute under the policy.
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await page.getByRole("button", { name: "Sign in", exact: true }).first().click();
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await expect(page.getByPlaceholder("At least 8 characters")).toBeVisible();
});

test("the browser blocks injected inline scripts and event handlers", async ({ page }) => {
  await page.goto("/");
  await page.evaluate(() => {
    const script = document.createElement("script");
    script.textContent = "window.__injectedScriptRan = true";
    document.body.appendChild(script);
    const button = document.createElement("button");
    button.setAttribute("onclick", "window.__injectedHandlerRan = true");
    document.body.appendChild(button);
    button.click();
  });
  expect(await page.evaluate(() => ({
    script: (window as unknown as Record<string, unknown>).__injectedScriptRan,
    handler: (window as unknown as Record<string, unknown>).__injectedHandlerRan,
  }))).toEqual({ script: undefined, handler: undefined });
});

test("checkout origins are permitted only on the billing route", async ({ request }) => {
  for (const path of ["/", "/settings", "/admin/billing"]) {
    const response = await request.get(path);
    const policy = response.headers()["content-security-policy"];
    nonce(policy);
    const scripts = policy.match(/script-src[^;]*/)?.[0] ?? "";
    const scriptSources = scripts.trim().split(/\s+/);
    expect(scriptSources.some(source => source === "https://checkout.razorpay.com")).toBe(path === "/admin/billing");
  }
});
