package run.slicer.cfr.test;

import org.benf.cfr.reader.api.CfrDriver;
import org.junit.jupiter.api.Test;
import run.slicer.cfr.impl.ClassFileSourceImpl;
import run.slicer.cfr.impl.MappingJson;
import run.slicer.cfr.impl.OutputSinkFactoryImpl;
import run.slicer.cfr.impl.OutputSinkFactoryImpl.MethodMapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the mapping sink on the JVM (no TeaVM), with the patched BytecodeTrackingDumper from
 * src/main shadowing CFR's copy on the classpath.
 */
public class MappingTest {
    private static final String FIXTURE = "run/slicer/cfr/test/fixture/MappingFixture";

    private static OutputSinkFactoryImpl decompile(String name) {
        final var sinks = new OutputSinkFactoryImpl(true);
        new CfrDriver.Builder()
                .withClassFileSource(new ClassFileSourceImpl(MappingTest::classBytes))
                .withOutputSink(sinks)
                .withOptions(Map.of("trackbytecodeloc", "true"))
                .build()
                .analyse(List.of(name));
        return sinks;
    }

    private static byte[] classBytes(String name) {
        // test classes and JDK classes (jrt) are both reachable as resources
        try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static MethodMapping find(List<MethodMapping> methods, String owner, String name) {
        return methods.stream()
                .filter(m -> m.owner().equals(owner) && m.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no mapping for " + owner + "." + name + " in " + methods));
    }

    @Test
    public void mapsAllClassShapes() {
        final var sinks = decompile(FIXTURE);
        final String source = sinks.output().get(FIXTURE);
        assertNotNull(source, "decompiled output");
        final List<MethodMapping> methods = sinks.mappings().get(FIXTURE);
        final String[] lines = source.split("\n", -1);

        // every mapped line points into the output
        for (final MethodMapping m : methods) {
            assertFalse(m.lines().isEmpty(), m.name() + " has an empty mapping");
            for (final int line : m.lines().values()) {
                assertTrue(line >= 1 && line <= lines.length, m.name() + " maps outside the source: " + line);
            }
        }

        // parameter names depend on debug info, so match structure only
        // upstream drops these entirely
        assertTrue(lines[find(methods, FIXTURE + "$Inner", "twice").lines().firstEntry().getValue() - 1].contains(" * 2;"));
        assertTrue(lines[find(methods, FIXTURE + "$1", "run").lines().firstEntry().getValue() - 1].contains("REGISTRY.add"));
        // field initializers lifted out of <init>/<clinit>
        assertTrue(lines[find(methods, FIXTURE, "<init>").lines().firstEntry().getValue() - 1].contains("log = new ArrayList"));
        assertTrue(lines[find(methods, FIXTURE, "<clinit>").lines().firstEntry().getValue() - 1].contains("REGISTRY = new ArrayList"));
        // lambda body maps to the line it is inlined on
        final var lambda = methods.stream().filter(m -> m.name().startsWith("lambda$lazy$")).findFirst().orElseThrow();
        assertTrue(lines[lambda.lines().firstEntry().getValue() - 1].contains(" * 2 + this.log.size()"));
    }

    @Test
    public void mappingIsOffByDefault() {
        final var sinks = new OutputSinkFactoryImpl();
        new CfrDriver.Builder()
                .withClassFileSource(new ClassFileSourceImpl(MappingTest::classBytes))
                .withOutputSink(sinks)
                .build()
                .analyse(List.of(FIXTURE));
        assertNotNull(sinks.output().get(FIXTURE));
        assertTrue(sinks.mappings().isEmpty());
    }

    @Test
    public void jsonEscapesAndShapes() {
        final var sinks = decompile(FIXTURE);
        final String json = MappingJson.write(sinks.output(), sinks.mappings());
        assertTrue(json.startsWith("{\"" + FIXTURE + "\":{\"source\":\""));
        assertTrue(json.contains("\"owner\":\"" + FIXTURE + "$Inner\""));
        assertFalse(json.contains("\n"), "raw newlines must be escaped");
    }
}
