package run.slicer.cfr;

import org.teavm.jso.*;
import org.teavm.jso.core.JSMapLike;
import run.slicer.cfr.impl.ClassFileSourceImpl;
import run.slicer.cfr.impl.MappingJson;
import run.slicer.cfr.impl.OutputSinkFactoryImpl;
import org.benf.cfr.reader.api.CfrDriver;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.Uint8Array;

import java.util.List;

public class Main {
    @JSExport
    public static JSPromise<JSMapLike<JSString>> decompile(String[] names, Options options) {
        return decompile0(names, options == null || JSObjects.isUndefined(options) ? JSObjects.create() : options);
    }

    /**
     * Like {@link #decompile}, but also collects bytecode offset -> output line mappings.
     * Resolves to a JSON string (see {@link MappingJson}); the cfr.js wrapper parses it.
     */
    @JSExport
    public static JSPromise<JSString> decompileMapped(String[] names, Options options) {
        final Options opts = options == null || JSObjects.isUndefined(options) ? JSObjects.create() : options;
        return JSPromise.callAsync(() -> {
            final var sinkFactory = run(names, opts, true);
            return JSString.valueOf(MappingJson.write(sinkFactory.output(), sinkFactory.mappings()));
        });
    }

    private static JSPromise<JSMapLike<JSString>> decompile0(String[] names, Options options) {
        return JSPromise.callAsync(() -> {
            final var sinkFactory = run(names, options, false);

            final JSMapLike<JSString> output = JSObjects.create();
            for (final var entry : sinkFactory.output().entrySet()) {
                output.set(entry.getKey(), JSString.valueOf(entry.getValue()));
            }
            return output;
        });
    }

    private static OutputSinkFactoryImpl run(String[] names, Options options, boolean trackMappings) {
        final var sinkFactory = new OutputSinkFactoryImpl(trackMappings);
        final var rawOptions = options.rawOptions();
        if (trackMappings) {
            rawOptions.put("trackbytecodeloc", "true");
        }

        new CfrDriver.Builder()
                .withClassFileSource(new ClassFileSourceImpl(name0 -> source0(options, name0)))
                .withOutputSink(sinkFactory)
                .withOptions(rawOptions)
                .build()
                .analyse(List.of(names));

        // not sure how to get these exceptions across since they can be non-fatal, log them for now
        for (final Throwable throwable : sinkFactory.exceptions()) {
            error(JSExceptions.getJSException(throwable));
        }
        return sinkFactory;
    }

    private static byte[] source0(Options options, String name) {
        final Uint8Array b = options.source(name).await();
        return b == null || JSObjects.isUndefined(b) ? null : unwrapByteArray(b);
    }

    @JSBody(params = {"data"}, script = "return data;")
    private static native @JSByRef(optional = true) byte[] unwrapByteArray(Uint8Array data);

    @JSBody(params = "message", script = "console.error(message);")
    private static native void error(JSObject message);
}
