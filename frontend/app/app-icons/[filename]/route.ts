import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { cwd } from "node:process";
import sharp from "sharp";

export const runtime = "nodejs";
export const dynamic = "force-static";
export const dynamicParams = false;

const icons: Record<string, { size: number; maskable: boolean }> = {
  "icon-192.png": { size: 192, maskable: false },
  "icon-512.png": { size: 512, maskable: false },
  "maskable-512.png": { size: 512, maskable: true },
};

export function generateStaticParams() {
  return Object.keys(icons).map((filename) => ({ filename }));
}

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ filename: string }> },
) {
  const { filename } = await params;
  const spec = Object.hasOwn(icons, filename) ? icons[filename] : undefined;
  if (!spec) return new Response(null, { status: 404 });
  const source = await readFile(join(cwd(), "public", "stoxsim-logo.png"));
  // A maskable icon's entire mark fits inside the central safe circle.
  const markSize = spec.maskable ? Math.floor(spec.size * 0.56) : spec.size;
  const mark = await sharp(source).resize(markSize, markSize, { fit: "contain" }).png().toBuffer();
  const png = spec.maskable
    ? await sharp({
      create: { width: spec.size, height: spec.size, channels: 4, background: "#f4f6f1" },
    }).composite([{ input: mark, gravity: "centre" }]).png().toBuffer()
    : mark;
  return new Response(new Uint8Array(png), {
    headers: { "Content-Type": "image/png", "X-Content-Type-Options": "nosniff" },
  });
}
