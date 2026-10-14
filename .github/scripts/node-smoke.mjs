// CI smoke test: decompile JDK classes with the built dist/ directly under Node (no Gradle, no JUnit)
// and report time and peak RSS. Usage: node node-smoke.mjs <classes dir> <name> [extra names...]
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { resolve } from "node:path";

const [dir, ...names] = process.argv.slice(2);
let peak = 0;
const sample = () => (peak = Math.max(peak, process.memoryUsage().rss));
const timer = setInterval(sample, 50);

const { decompile, decompileMapped } = await import(pathToFileURL(resolve("dist/cfr.js")).href);
const lookup = {};
for (const n of names) lookup[n] = await readFile(`${dir}/${n}.class`);
const source = async (n) => lookup[n] ?? null;

const t0 = Date.now();
const out = await decompile(names[0], { source });
sample();

const lines = out[names[0]]?.split("\n").length ?? 0;
console.log(`RESULT ${names[0]}: ${lines} lines, ${Date.now() - t0} ms, peak RSS ${(peak / 1048576).toFixed(0)} MB`);
if (!lines) process.exit(1);

// The patched BytecodeTrackingDumper must be the one compiled in: it is the only thing that sets owners
const mapped = (await decompileMapped(names[0], { source }))[names[0]];
sample();
clearInterval(timer);
const unowned = mapped?.methods.filter((m) => !m.owner) ?? [];
console.log(`MAPPED ${names[0]}: ${mapped?.methods.length ?? 0} methods, ${unowned.length} without owner`);
if (!mapped?.methods.length || unowned.length) {
    console.log("FAIL: decompileMapped is running CFR's unpatched BytecodeTrackingDumper");
    process.exit(1);
}
