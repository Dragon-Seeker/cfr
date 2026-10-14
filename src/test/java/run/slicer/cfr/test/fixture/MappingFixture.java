package run.slicer.cfr.test.fixture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// Compiled by the test source set and decompiled by MappingTest. Shapes that broke upstream tracking.
public class MappingFixture {
    static final List<String> REGISTRY = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    public int guarded(int v) {
        try {
            if (v == 0) throw new IllegalStateException("zero");
            return 100 / v;
        } finally {
            log.add("done " + v);
        }
    }

    public Supplier<Integer> lazy(int base) {
        return () -> base * 2 + log.size();
    }

    public class Inner {
        public int twice(int v) {
            return v * 2;
        }
    }

    public Runnable anon() {
        return new Runnable() {
            @Override
            public void run() {
                REGISTRY.add("ran");
            }
        };
    }
}
