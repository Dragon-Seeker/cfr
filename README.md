# cfr

A JavaScript port of the [CFR decompiler](https://github.com/leibnitz27/cfr).

## Example

```js
const fs = require("fs");
const { decompile } = require("./cfr.js"); // get it from the dist/ directory or jsDelivr

const data = fs.readFileSync("./your/package/HelloWorld.class"); // read a class file
console.log(await decompile("your/package/HelloWorld", {
    source: async (name) => {
        /* provide classes for analysis here, including the one you want to decompile */
        
        console.log(name); /* internal name, e.g. java/lang/Object */
        return name === "your/package/HelloWorld" ? data : null /* class not available */;
    },
    options: {
        /* see https://github.com/leibnitz27/cfr/blob/master/src/org/benf/cfr/reader/util/getopt/OptionsImpl.java#L274 */
        "hidelangimports": "false", /* testing option - don't hide java.lang imports */
    },
})["your/package/HelloWorld"]);
```

## Bytecode offset mapping

`decompileMapped` takes the same arguments and also returns, for every method CFR printed, which output line
each bytecode offset was written to. That lets you link a bytecode location (class, method, offset) to the
decompiled source.

```js
const { decompileMapped } = require("./cfr.js");

const result = await decompileMapped("your/package/HelloWorld", { source });
const { source: code, methods } = result["your/package/HelloWorld"];
// methods: [{ owner: "your/package/HelloWorld$Inner", name: "run", descriptor: "()V",
//             lines: [[0, 12], [7, 13]], original: [[0, 40]] }, ...]
```

- `lines` are `[offset, 1-based line]` pairs, sorted by offset: every offset CFR tracked. Offsets it didn't
  track are absent; resolve them via the nearest tracked offset in the same basic block.
- `owner` is the class that declares the method, which differs from the output key for inner, anonymous and
  local classes (they're printed inside their top-level class). Field initializers and enum constants are
  mapped to `<clinit>` / the constructors they come from.
- `original` is the class file's own LineNumberTable as `[offset, line]` pairs, or `null` without debug info.
- `trackbytecodeloc` is forced on. CFR's `BytecodeTrackingDumper` is replaced by a patched copy
  (`src/main/java/org/benf/cfr/reader/util/output/`) that keeps tracking inside inner classes, records each
  method's owner and doesn't compress the mapping; the build strips upstream's copy from the CFR jar
  (`cfrStripped`), since TeaVM would otherwise pick it over the project's.

## Licensing

The supporting code for this project and the CFR decompiler are licensed under the MIT License
([supporting code](./LICENSE), [CFR](./LICENSE-CFR)).

_This project is not affiliated with, maintained or endorsed by the CFR project in any way. Do NOT report issues with this project to the CFR issue tracker._
