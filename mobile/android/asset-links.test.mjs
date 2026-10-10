import test from "node:test";
import assert from "node:assert/strict";
import { createAssetLinks } from "./asset-links.mjs";

test("missing or malformed signing certificates cannot be published", () => {
  for (const input of [[], ["REPLACE_ME"], ["AA:BB"], ["00".repeat(32)], ["../private/upload.jks"]]) {
    assert.throws(() => createAssetLinks("com.stoxsim.app", input), /fingerprints/);
  }
});

test("associate only the configured package and supplied certificates", () => {
  const fingerprint = Array(32).fill("ab").join(":");
  const [association] = createAssetLinks("com.stoxsim.app", [fingerprint, fingerprint.toUpperCase()]);
  assert.equal(association.target.package_name, "com.stoxsim.app");
  assert.deepEqual(association.target.sha256_cert_fingerprints, [fingerprint.toUpperCase()]);
  assert.deepEqual(association.relation, ["delegate_permission/common.handle_all_urls"]);
  assert.throws(() => createAssetLinks("https://stoxsim.com", [fingerprint]), /package/);
});
