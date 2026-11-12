// SPDX-License-Identifier: GPL-3.0-only
import java.nio.file.*;
import java.net.*;
import java.util.Comparator;
import javax.tools.ToolProvider;

/** Source launcher keeps these real Java tests runnable even without a javac executable. */
class RunToolbarTests {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK with jdk.compiler is required");
        Path out = Files.createTempDirectory("teclado-toolbar-tests-");
        try {
            int code = compiler.run(null, System.out, System.err, "-d", out.toString(),
                "heliboard/overlay/ToolbarState.java", "heliboard/tests/ToolbarStateTest.java");
            if (code != 0) throw new IllegalStateException("Java compilation failed: " + code);
            try (var loader = new URLClassLoader(new URL[]{out.toUri().toURL()})) {
                loader.loadClass("helium314.keyboard.latin.suggestions.ToolbarStateTest")
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            try (var paths = Files.walk(out)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
