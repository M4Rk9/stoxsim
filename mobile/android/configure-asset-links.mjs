import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";
import { createAssetLinks } from "./asset-links.mjs";

// SHA-256 certificate fingerprints are public identifiers, not signing keys.
const input = process.argv.slice(2);
const root = dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(readFileSync(resolve(root, "twa-manifest.json"), "utf8"));
let links;
try { links = createAssetLinks(manifest.packageId, input); }
catch (error) { console.error(error.message); process.exit(1); }
writeFileSync(resolve(root, "../../frontend/public/.well-known/assetlinks.json"),
  `${JSON.stringify(links, null, 2)}\n`);
console.log("Website association updated. Deploy it and verify the Play-installed app.");
