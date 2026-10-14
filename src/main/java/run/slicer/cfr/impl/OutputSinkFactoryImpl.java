package run.slicer.cfr.impl;

import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.util.output.BytecodeTrackingContext;

import java.util.*;

public final class OutputSinkFactoryImpl implements OutputSinkFactory {
    private final Map<String, String> output = new HashMap<>();
    private final Map<String, List<MethodMapping>> mappings = new HashMap<>();
    private final List<Throwable> exceptions = new ArrayList<>();
    private final boolean trackMappings;

    // mappings for the class currently being dumped; CFR flushes them right before that class's JAVA output
    private List<MethodMapping> pending = new ArrayList<>();

    public OutputSinkFactoryImpl() {
        this(false);
    }

    public OutputSinkFactoryImpl(boolean trackMappings) {
        this.trackMappings = trackMappings;
    }

    /**
     * One method's mapping.
     *
     * @param owner      internal name of the class declaring the method (inner classes included)
     * @param name       method name
     * @param descriptor method descriptor
     * @param lines      bytecode offset -> 1-based line in the decompiled output; sparse, look up with floor semantics
     * @param original   bytecode offset -> original source line from the LineNumberTable, or null without debug info
     */
    public record MethodMapping(
            String owner, String name, String descriptor,
            NavigableMap<Integer, Integer> lines, NavigableMap<Integer, Integer> original
    ) {
    }

    @Override
    public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
        if (sinkType == SinkType.LINENUMBER) {
            return trackMappings ? List.of(SinkClass.LINE_NUMBER_MAPPING) : List.of();
        }
        return List.of(SinkClass.EXCEPTION_MESSAGE, SinkClass.DECOMPILED);
    }

    @Override
    public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
        return switch (sinkType) {
            case JAVA -> (s -> {
                if (!(s instanceof SinkReturns.Decompiled dec)) {
                    throw new RuntimeException("Unexpected sink input");
                }

                var className = dec.getClassName();
                if (!dec.getPackageName().isEmpty()) {
                    className = dec.getPackageName().replace('.', '/') + "/" + className;
                }

                output.put(className, dec.getJava());
                if (trackMappings) {
                    mappings.put(className, pending);
                    pending = new ArrayList<>();
                }
            });
            case LINENUMBER -> (s -> {
                if (!(s instanceof SinkReturns.LineNumberMapping m)) {
                    return;
                }
                pending.add(new MethodMapping(
                        BytecodeTrackingContext.currentOwner(),
                        m.methodName(),
                        m.methodDescriptor(),
                        m.getMappings(),
                        m.getClassFileMappings()
                ));
            });
            case EXCEPTION -> (s -> exceptions.add(
                    s instanceof SinkReturns.ExceptionMessage ex
                            ? ex.getThrownException()
                            : new RuntimeException(s.toString())
            ));
            default -> (s -> {
            });
        };
    }

    public Map<String, String> output() {
        return this.output;
    }

    public Map<String, List<MethodMapping>> mappings() {
        return this.mappings;
    }

    public List<Throwable> exceptions() {
        return this.exceptions;
    }
}
