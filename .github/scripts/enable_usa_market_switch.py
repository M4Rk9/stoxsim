from pathlib import Path

PAGE_PATH = Path("frontend/app/page.tsx")
TEST_PATH = Path("frontend/e2e/learner-journey.spec.ts")

OLD_SELECTOR = '''        <div className="marketSwitch" aria-label="Market selection">
          <button className="active"><span>🇮🇳</span> India</button>
          <button disabled title="United States market is the next product phase"><span>🇺🇸</span> US <small>NEXT</small></button>
        </div>'''

NEW_SELECTOR = '''        <div className="marketSwitch" aria-label="Market selection">
          <button
            type="button"
            className={marketRegion === "INDIA" ? "active" : ""}
            aria-pressed={marketRegion === "INDIA"}
            onClick={() => switchMarket("INDIA")}
          ><span>🇮🇳</span> India</button>
          <button
            type="button"
            className={marketRegion === "UNITED_STATES" ? "active" : ""}
            aria-pressed={marketRegion === "UNITED_STATES"}
            onClick={() => switchMarket("UNITED_STATES")}
          ><span>🇺🇸</span> US</button>
        </div>'''

TEST_ANCHOR = '''  await expect(page.locator(".streamBadge")).toBeVisible({ timeout: 15_000 });

  await page.reload();'''

TEST_REPLACEMENT = '''  await expect(page.locator(".streamBadge")).toBeVisible({ timeout: 15_000 });

  await page.getByRole("button", { name: /US/ }).click();
  await expect(page.getByText("USA PORTFOLIO")).toBeVisible();
  await expect(page.getByRole("button", { name: /US/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("$10,000.00");

  await page.getByRole("button", { name: /India/ }).click();
  await expect(page.getByText("INDIA PORTFOLIO")).toBeVisible();
  await expect(page.getByRole("button", { name: /India/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".metric").filter({ hasText: "Account value" }))
    .toContainText("₹5,00,000.00");

  await page.reload();'''


def replace_once(path: Path, old: str, new: str, already_present: str) -> None:
    content = path.read_text(encoding="utf-8")
    if already_present in content:
        return
    if old not in content:
        raise SystemExit(f"Expected patch target was not found in {path}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")


replace_once(
    PAGE_PATH,
    OLD_SELECTOR,
    NEW_SELECTOR,
    'onClick={() => switchMarket("UNITED_STATES")}',
)
replace_once(
    TEST_PATH,
    TEST_ANCHOR,
    TEST_REPLACEMENT,
    'getByText("USA PORTFOLIO")',
)
