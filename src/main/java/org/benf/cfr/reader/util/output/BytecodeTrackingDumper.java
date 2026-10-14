package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.loc.HasByteCodeLoc;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Patched copy of CFR 0.152's BytecodeTrackingDumper (shadows the class from the CFR jar).
 *
 * <p>Changes from upstream, both marked "PATCH":
 * <ol>
 *     <li>{@link #withTypeUsageInformation} keeps tracking. Upstream inherits
 *     {@link DelegatingDumper#withTypeUsageInformation}, which returns the wrapped dumper, so every
 *     member/static inner class (and method-scoped override dumper) escapes tracking and gets no mappings.</li>
 *     <li>{@link #close} flushes one method at a time and publishes the owning class through
 *     {@link BytecodeTrackingContext}, because {@code SinkReturns.LineNumberMapping} has no owner field.</li>
 *     <li>Mappings are emitted uncompressed: one entry per tracked offset, instead of only the offsets
 *     where the line changes. An offset missing from the map was not tracked (e.g. the exception-path
 *     copy of a finally block), which upstream's compressed form cannot express.</li>
 *     <li>Field initializers and enum constants report their location, so code CFR lifts out of
 *     {@code <clinit>}/{@code <init>} into declarations is mapped too.</li>
 * </ol>
 */
class BytecodeTrackingDumper extends DelegatingDumper {

    private final Map<Method, MethodBytecode> perMethod;
    private final BytecodeDumpConsumer consumer;
    // PATCH: only the top-level tracker flushes; child trackers share perMethod.
    private final boolean root;

    BytecodeTrackingDumper(Dumper dumper, BytecodeDumpConsumer consumer) {
        this(dumper, consumer, MapFactory.newLazyMap(MapFactory.<Method, MethodBytecode>newIdentityMap(), new UnaryFunction<Method, MethodBytecode>() {
            @Override
            public MethodBytecode invoke(Method arg) {
                return new MethodBytecode();
            }
        }), true);
    }

    private BytecodeTrackingDumper(Dumper dumper, BytecodeDumpConsumer consumer, Map<Method, MethodBytecode> perMethod, boolean root) {
        super(dumper);
        this.consumer = consumer;
        this.perMethod = perMethod;
        this.root = root;
    }

    @Override
    public void informBytecodeLoc(HasByteCodeLoc loc) {
        BytecodeLoc locInfo = loc.getCombinedLoc();
        int currentDepth = delegate.getIndentLevel();
        int currentLine = delegate.getCurrentLine();

        for (Method method : locInfo.getMethods()) {
            perMethod.get(method).add(locInfo.getOffsetsForMethod(method), currentDepth, currentLine);
        }
    }

    // PATCH: wrap the inner dumper instead of escaping to it.
    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new BytecodeTrackingDumper(delegate.withTypeUsageInformation(innerclassTypeUsageInformation), consumer, perMethod, false);
    }

    // PATCH: field initializers and enum constants. CFR lifts their code out of <clinit>/<init>
    // into declarations, which are not statements and so never call informBytecodeLoc.
    // A defining fieldName() starts a declaration; expressions dumped before the line ends are its value.
    private boolean inDeclaration;

    @Override
    public Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic, boolean defines) {
        super.fieldName(name, owner, hiddenDeclaration, isStatic, defines);
        if (defines) inDeclaration = true;
        return this;
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (inDeclaration && d instanceof HasByteCodeLoc) {
            informBytecodeLoc((HasByteCodeLoc) d);
        }
        return super.dump(d);
    }

    @Override
    public Dumper endCodeln() {
        inDeclaration = false;
        return super.endCodeln();
    }

    @Override
    public Dumper newln() {
        inDeclaration = false;
        return super.newln();
    }

    static class LocAtLine {
        int depth;
        int currentLine;

        LocAtLine(int depth, int currentLine) {
            this.depth = depth;
            this.currentLine = currentLine;
        }

        public int getDepth() {
            return depth;
        }

        public int getCurrentLine() {
            return currentLine;
        }

        public void improve(int currentDepth, int currentLine) {
            if (currentDepth > depth) {
                depth = currentDepth;
                this.currentLine = currentLine;
            }
        }
    }

    static class MethodBytecode {
        Map<Integer, LocAtLine> locAtLineMap = MapFactory.newTreeMap();

        public void add(Collection<Integer> offsetsForMethod, int currentDepth, int currentLine) {
            for (Integer offset : offsetsForMethod) {
                LocAtLine known = locAtLineMap.get(offset);
                if (known == null) {
                    locAtLineMap.put(offset, new LocAtLine(currentDepth, currentLine));
                } else {
                    known.improve(currentDepth, currentLine);
                }
            }
        }

        TreeMap<Integer, Integer> getFinal() {
            TreeMap<Integer, Integer> res = MapFactory.newTreeMap();
            Integer lastLine = -1;
            for (Map.Entry<Integer, LocAtLine> entry : locAtLineMap.entrySet()) {
                Integer line = entry.getValue().getCurrentLine();
                if (lastLine.equals(line)) continue;
                res.put(entry.getKey(), line);
                lastLine = line;
            }
            return res;
        }

        // PATCH: every tracked offset, uncompressed, so consumers can tell "tracked" from "gap".
        TreeMap<Integer, Integer> getAll() {
            TreeMap<Integer, Integer> res = MapFactory.newTreeMap();
            for (Map.Entry<Integer, LocAtLine> entry : locAtLineMap.entrySet()) {
                res.put(entry.getKey(), entry.getValue().getCurrentLine());
            }
            return res;
        }
    }

    @Override
    public void close() {
        if (!root) {
            // PATCH: child trackers never own the output; the top-level close flushes everything.
            return;
        }
        /*
         * We need to flush the mappings we have generated to an appropriate consumer.
         * PATCH: one item per accept() so the owner can be read synchronously by the sink.
         */
        for (Map.Entry<Method, MethodBytecode> entry : perMethod.entrySet()) {
            // PATCH: uncompressed (upstream: getFinal()), see getAll()
            final TreeMap<Integer, Integer> data = entry.getValue().getAll();
            final Method method = entry.getKey();
            BytecodeTrackingContext.enter(method.getClassFile().getClassType().getRawName());
            try {
                consumer.accept(Collections.<BytecodeDumpConsumer.Item>singletonList(new BytecodeDumpConsumer.Item() {
                    @Override
                    public Method getMethod() {
                        return method;
                    }

                    @Override
                    public NavigableMap<Integer, Integer> getBytecodeLocs() {
                        return data;
                    }
                }));
            } finally {
                BytecodeTrackingContext.exit();
            }
        }
        delegate.close();
    }
}
