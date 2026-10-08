import { readFileSync, readdirSync, existsSync } from "node:fs";
import { resolve, relative } from "node:path";
import { randomBytes } from "node:crypto";
import { spawnSync } from "node:child_process";
import ts from "../frontend/node_modules/typescript/lib/typescript.js";

const root = resolve(import.meta.dirname, "..");
const frontend = resolve(root, "frontend");
const allowed = new Set(["NEXT_PUBLIC_API_URL"]);
function* files(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) yield* files(path);
    else if (entry.isFile()) yield path;
  }
}

// Reject aliases, destructuring and dynamic environment access as well as direct
// private variable access. A private value can leak through prerendered HTML even
// when Next.js does not inline it into client JavaScript.
let violations = 0;
for (const path of files(resolve(frontend, "app"))) {
  if (!/\.[cm]?[jt]sx?$/.test(path)) continue;
  const source = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
  function visit(node) {
    if (ts.isIdentifier(node) && node.text === "process") {
      const env = node.parent;
      const access = env.parent;
      const valid = ts.isPropertyAccessExpression(env) && env.expression === node
        && env.name.text === "env" && ts.isPropertyAccessExpression(access)
        && access.expression === env && allowed.has(access.name.text);
      if (!valid) {
        const { line } = source.getLineAndCharacterOfPosition(node.getStart());
        console.error(`Unapproved environment access: ${relative(root, path)}:${line + 1}`);
        violations++;
      }
    }
    ts.forEachChild(node, visit);
  }
  visit(source);
}
if (violations) process.exit(1);
if (process.argv.includes("--source-only")) {
  console.log("Frontend environment access is restricted to NEXT_PUBLIC_API_URL.");
  process.exit(0);
}

// Discover backend environment names rather than keeping a drifting key list.
const config = readFileSync(resolve(root, "backend/src/main/resources/application.yml"), "utf8")
  + readFileSync(resolve(root, "deploy/production/compose.yml"), "utf8");
const names = new Set([...config.matchAll(/\$\{([A-Z][A-Z0-9_]*)/g)].map((match) => match[1])
  .filter((name) => /SECRET|TOKEN|PASSWORD|API_KEY|KEY_ID/.test(name)));
// Also cover common future integrations that are not currently configured.
for (const name of ["OPENAI_API_KEY", "STRIPE_SECRET_KEY", "STRIPE_WEBHOOK_SECRET",
  "FIREBASE_PRIVATE_KEY", "AWS_SECRET_ACCESS_KEY", "DATABASE_URL"]) names.add(name);
const prefix = `STOXSIM_SECRET_CANARY_${randomBytes(24).toString("hex")}_`;
const canaries = Object.fromEntries([...names].map((name) => [name, `${prefix}${name}`]));
const build = spawnSync("npm", ["run", "build"], {
  cwd: frontend, env: { ...process.env, ...canaries }, stdio: "inherit",
});
if (build.status !== 0) process.exit(build.status ?? 1);

let inspected = 0;
for (const directory of [".next/static", ".next/server/app", "public"]) {
  const path = resolve(frontend, directory);
  if (!existsSync(path)) throw new Error(`Missing browser artifact directory: ${directory}`);
  for (const artifact of files(path)) {
    // Server modules are included too: this is stricter than just public assets.
    const content = readFileSync(artifact);
    for (const [name, value] of Object.entries(canaries)) {
      if (content.includes(value)) {
        console.error(`Backend secret reached a frontend artifact: ${name}, ${relative(frontend, artifact)}`);
        process.exit(1);
      }
    }
    inspected++;
  }
}
if (!inspected) throw new Error("No browser artifacts inspected");
console.log(`Secret isolation verified: ${names.size} backend canaries absent from ${inspected} frontend artifacts.`);
