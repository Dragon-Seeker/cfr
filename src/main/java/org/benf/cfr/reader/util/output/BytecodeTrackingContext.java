package org.benf.cfr.reader.util.output;

/**
 * Side channel for the owning class of the method whose mapping is being written to a
 * {@code LINENUMBER} sink. Only valid inside {@code Sink.write}, which the patched
 * {@link BytecodeTrackingDumper} calls synchronously, so no other decompilation can interleave.
 */
public final class BytecodeTrackingContext {
    private static String currentOwner;

    private BytecodeTrackingContext() {
    }

    static void enter(String ownerRawName) {
        currentOwner = ownerRawName;
    }

    static void exit() {
        currentOwner = null;
    }

    /**
     * @return the owner of the mapping currently being written, as an internal name
     * (e.g. {@code com/example/Outer$Inner}), or null outside a LINENUMBER sink write
     */
    public static String currentOwner() {
        return currentOwner == null ? null : currentOwner.replace('.', '/');
    }
}
