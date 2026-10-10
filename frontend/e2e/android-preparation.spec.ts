import { expect, test } from "@playwright/test";
import sharp from "sharp";

test("install manifest serves actual square icons with a safe maskable canvas", async ({ request }) => {
  const response = await request.get("/manifest.webmanifest");
  expect(response.ok()).toBeTruthy();
  const manifest = await response.json();
  expect(manifest.id).toBe("/");
  expect(manifest.scope).toBe("/");
  expect(manifest.display).toBe("standalone");
  for (const icon of manifest.icons) {
    const image = await request.get(icon.src);
    expect(image.ok()).toBeTruthy();
    expect(image.headers()["content-type"]).toContain("image/png");
    const bytes = await image.body();
    const metadata = await sharp(bytes).metadata();
    expect(`${metadata.width}x${metadata.height}`).toBe(icon.sizes);
    if (icon.purpose === "maskable") {
      const { data } = await sharp(bytes).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
      expect([...data.subarray(0, 4)]).toEqual([244, 246, 241, 255]);
    }
  }
  expect((await request.get("/app-icons/unknown.png")).status()).toBe(404);
});

test("deletion instructions and website association are public without a login", async ({ request }) => {
  const response = await request.get("/delete-account");
  expect(response.ok()).toBeTruthy();
  const html = await response.text();
  expect(html).toContain("Delete your StoxSim account");
  expect(html).toContain('href="/settings"');
  expect(html).toContain("support.stoxsim@gmail.com");
  expect(html).toContain("Never send your password");
  const association = await request.get("/.well-known/assetlinks.json");
  expect(association.ok()).toBeTruthy();
  const links = await association.json();
  expect(Array.isArray(links)).toBeTruthy();
  for (const link of links) {
    expect(link.target.namespace).toBe("android_app");
    expect(link.target.package_name).toBe("com.stoxsim.app");
    expect(link.target.sha256_cert_fingerprints.length).toBeGreaterThan(0);
    for (const fingerprint of link.target.sha256_cert_fingerprints) {
      expect(fingerprint).toMatch(/^(?:[A-F0-9]{2}:){31}[A-F0-9]{2}$/);
    }
  }
});
