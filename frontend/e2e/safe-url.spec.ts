import { expect, test } from "@playwright/test";
import { safeWebsiteUrl } from "../app/lib/safe-url";

test("campus website links reject executable and credential-bearing URLs", () => {
  for (const input of ["javascript:alert(1)", "data:text/html,<script>alert(1)</script>", "//evil.test", "https://user:pass@example.test", "https://", "http://example.test", "https://example.test@evil.test"])
    expect(safeWebsiteUrl(input)).toBeNull();
  expect(safeWebsiteUrl("https://university.example.test/admissions")).toBe("https://university.example.test/admissions");
});
