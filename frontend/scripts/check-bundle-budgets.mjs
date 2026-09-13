import { readdir, stat } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const assetsDir = path.join(root, "dist", "assets");

const budgets = {
  entryJsKiB: 360,
  asyncJsKiB: 300,
  cssKiB: 110,
  totalJsKiB: 1250,
};

const entryJsPattern = /^index-[\w-]+\.js$/;

function toKiB(bytes) {
  return bytes / 1024;
}

function formatKiB(bytes) {
  return `${toKiB(bytes).toFixed(2)} kB`;
}

async function listAssets(dir) {
  const names = await readdir(dir);
  const files = await Promise.all(
    names.map(async (name) => {
      const filePath = path.join(dir, name);
      const info = await stat(filePath);
      return {
        name,
        bytes: info.size,
        ext: path.extname(name),
        entry: entryJsPattern.test(name),
      };
    }),
  );
  return files.sort((a, b) => b.bytes - a.bytes);
}

function overBudget(label, actualKiB, budgetKiB) {
  return actualKiB > budgetKiB
    ? `${label}: ${actualKiB.toFixed(2)} kB exceeds ${budgetKiB} kB`
    : null;
}

const assets = await listAssets(assetsDir).catch((error) => {
  console.error(`Bundle budget check could not read ${assetsDir}. Run npm run build first.`);
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});

const jsAssets = assets.filter((asset) => asset.ext === ".js");
const cssAssets = assets.filter((asset) => asset.ext === ".css");
const entryJs = jsAssets.filter((asset) => asset.entry);
const asyncJs = jsAssets.filter((asset) => !asset.entry);
const totalJsBytes = jsAssets.reduce((total, asset) => total + asset.bytes, 0);

const failures = [
  ...entryJs
    .map((asset) => overBudget(`Entry JS ${asset.name}`, toKiB(asset.bytes), budgets.entryJsKiB))
    .filter(Boolean),
  ...asyncJs
    .map((asset) => overBudget(`Async JS ${asset.name}`, toKiB(asset.bytes), budgets.asyncJsKiB))
    .filter(Boolean),
  ...cssAssets
    .map((asset) => overBudget(`CSS ${asset.name}`, toKiB(asset.bytes), budgets.cssKiB))
    .filter(Boolean),
  overBudget("Total JS", toKiB(totalJsBytes), budgets.totalJsKiB),
].filter(Boolean);

console.log("Bundle budget summary");
console.log(`Entry JS budget: ${budgets.entryJsKiB} kB`);
console.log(`Async JS chunk budget: ${budgets.asyncJsKiB} kB`);
console.log(`CSS chunk budget: ${budgets.cssKiB} kB`);
console.log(`Total JS budget: ${budgets.totalJsKiB} kB`);
console.log("");
console.log("Largest assets:");
assets.slice(0, 12).forEach((asset) => {
  const kind = asset.ext === ".js" && asset.entry ? "entry-js" : asset.ext.slice(1) || "asset";
  console.log(`${formatKiB(asset.bytes).padStart(10)}  ${kind.padEnd(8)}  ${asset.name}`);
});

if (failures.length > 0) {
  console.error("");
  console.error("Bundle budget check failed:");
  failures.forEach((failure) => console.error(`- ${failure}`));
  process.exit(1);
}

console.log("");
console.log("Bundle budget check passed.");
