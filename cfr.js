const isNode = typeof process !== "undefined";
const wasmPath = async () => {
    const url = new URL("./cfr.wasm", import.meta.url).href;
    return isNode ? await import("node:url").then((mod) => mod.fileURLToPath(url)) : url;
};

let exportsPromise = null;
const loadExports = () => {
    if (!exportsPromise) {
        exportsPromise = (async () => {
            try {
                const { load } = await import("./cfr.wasm-runtime.js");
                const { exports } = await load(await wasmPath());
                return exports;
            } catch (e) {
                console.warn("Failed to load WASM module (non-compliant browser?), falling back to JS implementation", e);
                return await import("./cfr.runtime.js");
            }
        })();
    }
    return exportsPromise;
};

export const decompile = async (names, options) => {
    const { decompile } = await loadExports();
    return decompile(Array.isArray(names) ? names : [names], options);
};

export const decompileMapped = async (names, options) => {
    const { decompileMapped } = await loadExports();
    return JSON.parse(await decompileMapped(Array.isArray(names) ? names : [names], options));
};
