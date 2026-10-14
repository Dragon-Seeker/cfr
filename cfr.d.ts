export type Options = Record<string, string>;

export interface Config {
    source?: (name: string) => Promise<Uint8Array | null>;
    options?: Options;
}

export declare const decompile: (names: string | string[], config?: Config) => Promise<Record<string, string>>;

/** [bytecode offset, 1-based line] pairs, sorted by offset. */
export type OffsetLinePairs = [number, number][];

export interface MethodMapping {
    /** Internal name of the declaring class; differs from the output key for inner, anonymous and local classes. */
    owner: string;
    name: string;
    descriptor: string;
    /**
     * Offset -> line in `source`. One entry per offset CFR tracked (uncompressed); untracked offsets
     * are absent, so resolve them via the nearest tracked offset in the same basic block.
     */
    lines: OffsetLinePairs;
    /** Offset -> original source line from the LineNumberTable, or null without debug info. */
    original: OffsetLinePairs | null;
}

export interface MappedOutput {
    source: string;
    methods: MethodMapping[];
}

/** Decompiles with `trackbytecodeloc` forced on, returning output keyed by top-level internal name. */
export declare const decompileMapped: (names: string | string[], config?: Config) => Promise<Record<string, MappedOutput>>;
