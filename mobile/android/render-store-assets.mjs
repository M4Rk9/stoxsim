// Deterministic review assets; never signs, uploads or publishes an Android build.
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { mkdir, copyFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const sharp = require('sharp');
const root = fileURLToPath(new URL('.', import.meta.url));
const output = resolve(process.argv[2] ?? join(root, 'generated/store-assets'));
await mkdir(output, { recursive: true });
await sharp(join(root, 'store/feature-graphic.svg')).flatten({ background: '#102a23' }).png()
  .toFile(join(output, 'feature-graphic.png'));
await sharp(join(root, 'project/store_icon.png')).resize(512, 512).png()
  .toFile(join(output, 'store-icon.png'));
await copyFile(join(root, 'STORE_LISTING_DRAFT.md'), join(output, 'STORE_LISTING_DRAFT.md'));
for (const [name, width, height] of [['feature-graphic.png', 1024, 500], ['store-icon.png', 512, 512]]) {
  const metadata = await sharp(join(output, name)).metadata();
  if (metadata.width !== width || metadata.height !== height) throw new Error(`Unexpected ${name} dimensions`);
  if (name === 'feature-graphic.png' && metadata.hasAlpha) throw new Error('Feature graphic must be opaque');
}
console.log('Prepared review icon, feature graphic and listing draft. No screenshots or publication are implied.');
