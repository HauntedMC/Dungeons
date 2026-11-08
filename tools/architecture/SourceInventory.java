import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Structural review priorities for every production source file, using the JDK parser. */
public final class SourceInventory {
    private SourceInventory() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("Run from the repository root with no arguments");
        }
        List<Path> paths;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            paths = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(paths));
            var positions = Trees.instance(task).getSourcePositions();
            var report = new StringBuilder(
                    "package\tpath\tlines\tmethods\tlongest_method\tmethod_lines\treview_signals\n");
            for (var unit : task.parse()) {
                appendFile(report, unit, positions);
            }
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IllegalStateException("Source parsing failed: " + diagnostic);
                }
            }
            System.out.print(report);
        }
    }

    private static void appendFile(StringBuilder report, CompilationUnitTree unit,
            SourcePositions positions) throws Exception {
        var metrics = new Metrics(unit, positions);
        metrics.scan(unit, null);
        var path = Path.of(unit.getSourceFile().toUri());
        int lines = Files.readAllLines(path).size();
        var signals = new ArrayList<String>();
        if (lines > 500) signals.add("class over 500 lines");
        if (metrics.longestLines > 80) signals.add("method over 80 lines");
        report.append(String.format(Locale.ROOT, "%s\t%s\t%d\t%d\t%s\t%d\t%s%n",
                unit.getPackageName(), Path.of("").toAbsolutePath().relativize(path), lines,
                metrics.count, metrics.longestName, metrics.longestLines,
                String.join("; ", signals)));
    }

    private static final class Metrics extends TreeScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private int count;
        private long longestLines;
        private String longestName = "";

        Metrics(CompilationUnitTree unit, SourcePositions positions) {
            this.unit = unit;
            this.positions = positions;
        }

        @Override
        public Void visitMethod(MethodTree method, Void unused) {
            if (method.getBody() != null) {
                count++;
                long start = positions.getStartPosition(unit, method);
                long end = positions.getEndPosition(unit, method);
                long lines = unit.getLineMap().getLineNumber(end)
                        - unit.getLineMap().getLineNumber(start) + 1;
                if (lines > longestLines) {
                    longestLines = lines;
                    longestName = method.getName().toString();
                }
            }
            return super.visitMethod(method, unused);
        }
    }
}
