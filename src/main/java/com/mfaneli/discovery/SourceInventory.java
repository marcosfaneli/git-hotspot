package com.mfaneli.discovery;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class SourceInventory {
    public record Entry(String kind, String name, String file, long line) {}

    public List<Entry> scan(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("Invalid directory: " + root);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("A JDK is required");
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !excluded(root.relativize(p)))
                .sorted().toList();
        }
        if (files.isEmpty()) return List.of();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var entries = new ArrayList<Entry>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(files));
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : task.parse()) {
                String file = root.toAbsolutePath().normalize().relativize(
                    Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize()).toString().replace('\\', '/');
                new TreeScanner<Void, Void>() {
                    void add(String kind, String name, Tree tree) {
                        long pos = positions.getStartPosition(unit, tree);
                        entries.add(new Entry(kind, name, file, pos < 0 ? -1 : unit.getLineMap().getLineNumber(pos)));
                    }
                    @Override public Void visitClass(ClassTree tree, Void unused) {
                        add("TYPE", tree.getSimpleName().toString(), tree);
                        return super.visitClass(tree, unused);
                    }
                    @Override public Void visitMethod(MethodTree tree, Void unused) {
                        add("METHOD", tree.getName().toString(), tree);
                        for (var annotation : tree.getModifiers().getAnnotations()) {
                            String name = annotation.getAnnotationType().toString();
                            String simple = name.substring(name.lastIndexOf('.') + 1);
                            if (Set.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping").contains(simple))
                                add("MAPPING_CANDIDATE", annotation.toString().replaceAll("\\s+", " "), annotation);
                        }
                        return super.visitMethod(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR))
            throw new IOException("Source parsing failed; inventory not complete");
        entries.sort(Comparator.comparing(Entry::file).thenComparingLong(Entry::line).thenComparing(Entry::kind).thenComparing(Entry::name));
        return List.copyOf(entries);
    }

    private boolean excluded(Path path) {
        for (Path part : path) {
            if (Set.of(".git", "target", "build", "test").contains(part.toString())) return true;
        }
        return false;
    }
}
