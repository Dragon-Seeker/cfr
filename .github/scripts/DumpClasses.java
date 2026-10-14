import java.nio.file.Files;
import java.nio.file.Path;

// Writes JDK class files to <out>/<internal name>.class for the Node smoke test.
public class DumpClasses {
    public static void main(String[] args) throws Exception {
        final Path out = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) {
            try (var in = ClassLoader.getSystemResourceAsStream(args[i] + ".class")) {
                final Path file = out.resolve(args[i] + ".class");
                Files.createDirectories(file.getParent());
                Files.write(file, in.readAllBytes());
            }
        }
    }
}
