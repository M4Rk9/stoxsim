export function createAssetLinks(packageId, fingerprints) {
  const pattern = /^(?:[A-Fa-f0-9]{2}:){31}[A-Fa-f0-9]{2}$/;
  if (!fingerprints.length || fingerprints.some((value) => !pattern.test(value))) {
    throw new Error("Provide one or more SHA-256 certificate fingerprints: 32 colon-separated hex pairs.");
  }
  if (!/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/.test(packageId)) {
    throw new Error("Invalid Android package identifier.");
  }
  return [{
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: packageId,
      sha256_cert_fingerprints: [...new Set(fingerprints.map((value) => value.toUpperCase()))],
    },
  }];
}
