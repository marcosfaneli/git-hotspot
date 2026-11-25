package com.mfaneli.discovery;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.mfaneli.architecture.ArchitectureGraph;
import static com.mfaneli.architecture.ArchitectureGraph.*;

public final class CallInventory {
    private record Method(Node node, String owner, String simple, int arity, boolean varargs) {}
    private record Call(String caller, String owner, String receiver, String name, int arity, Node evidence) {}

    public ArchitectureGraph scan(Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        final Path base = root;
        List<Path> files;
        try (var walk = Files.walk(base)) {
            files = walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !excluded(base.relativize(p))).sorted().toList();
        }
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        var methods = new ArrayList<Method>();
        var calls = new ArrayList<Call>();
        if (files.isEmpty()) return new ArchitectureGraph(nodes, edges);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("A JDK is required");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(files));
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : task.parse()) {
                String file = base.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\','/');
                String pkg = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
                new TreeScanner<Void,Void>() {
                    String owner;
                    String caller;
                    Map<String,String> fields = Map.of();
                    Map<String,String> parameters = Map.of();
                    int ordinal;
                    long line(Tree tree) {
                        long pos = positions.getStartPosition(unit,tree);
                        return pos < 0 ? -1 : unit.getLineMap().getLineNumber(pos);
                    }
                    @Override public Void visitClass(ClassTree tree, Void unused) {
                        if (tree.getSimpleName().length() == 0 || caller != null) return null;
                        String old = owner;
                        var oldFields = fields;
                        owner = old == null ? pkg + tree.getSimpleName() : old + "$" + tree.getSimpleName();
                        fields = new HashMap<>();
                        for (var member : tree.getMembers()) if (member instanceof VariableTree v)
                            fields.put(v.getName().toString(),v.getType().toString());
                        String id = "type:" + file + ":" + owner;
                        nodes.add(new Node(id,"JAVA_TYPE",owner,file,line(tree)));
                        if (old != null) edges.add(new Edge("type:"+file+":"+old,id,"CONTAINS"));
                        super.visitClass(tree,unused);
                        owner = old;
                        fields = oldFields;
                        return null;
                    }
                    @Override public Void visitMethod(MethodTree tree, Void unused) {
                        if (owner == null) return null;
                        String name = tree.getName().toString();
                        String signature = name + "(" + String.join(",",tree.getParameters().stream().map(p -> p.getType().toString()).toList()) + ")";
                        caller = "method:"+file+":"+owner+"#"+signature;
                        ordinal = 0;
                        parameters = new HashMap<>();
                        for (var p : tree.getParameters()) parameters.put(p.getName().toString(),p.getType().toString());
                        var node = new Node(caller,"JAVA_METHOD",owner+"#"+signature,file,line(tree));
                        nodes.add(node);
                        methods.add(new Method(node,owner,name,tree.getParameters().size(),tree.getParameters().stream().anyMatch(p -> p.toString().contains("..."))));
                        edges.add(new Edge("type:"+file+":"+owner,caller,"CONTAINS"));
                        if (tree.getBody() != null) scan(tree.getBody(),unused);
                        caller = null;
                        parameters = Map.of();
                        return null;
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                        if (caller != null) {
                            String name;
                            String receiver = "";
                            switch (tree.getMethodSelect()) {
                                case IdentifierTree i -> { name = i.getName().toString(); receiver = owner; }
                                case MemberSelectTree m -> {
                                    name = m.getIdentifier().toString();
                                    String expr = m.getExpression().toString();
                                    if (expr.equals("this")) receiver = owner;
                                    else if (expr.startsWith("this.")) receiver = fields.getOrDefault(expr.substring(5),"");
                                    else receiver = parameters.getOrDefault(expr,"");
                                }
                                default -> name = tree.getMethodSelect().toString();
                            }
                            var evidence = new Node(caller+":call:"+(++ordinal),"CALL_SITE",tree.getMethodSelect().toString(),file,line(tree));
                            nodes.add(evidence);
                            edges.add(new Edge(caller,evidence.id(),"CONTAINS"));
                            calls.add(new Call(caller,owner,receiver,name,tree.getArguments().size(),evidence));
                        }
                        return super.visitMethodInvocation(tree,unused);
                    }
                }.scan(unit,null);
            }
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind()==Diagnostic.Kind.ERROR))
            throw new IOException("Source parsing failed; call inventory not complete");
        var byName = new HashMap<String,List<Method>>();
        for (var method : methods) byName.computeIfAbsent(method.simple(),k -> new ArrayList<>()).add(method);
        for (var call : calls) {
            var candidates = byName.getOrDefault(call.name(),List.of()).stream()
                .filter(m -> !call.receiver().isEmpty() && (m.owner().equals(call.receiver()) || m.owner().endsWith("."+call.receiver())))
                .filter(m -> m.arity()==call.arity() || (m.varargs() && call.arity()>=m.arity()-1)).toList();
            if (candidates.size()==1) {
                edges.add(new Edge(call.evidence().id(),candidates.getFirst().node().id(),"POSSIBLE_TARGET"));
                edges.add(new Edge(call.caller(),candidates.getFirst().node().id(),"POSSIBLE_CALL"));
            } else {
                var unresolved = new Node(call.evidence().id()+":unresolved","UNRESOLVED_CALL",call.evidence().name(),call.evidence().file(),call.evidence().line());
                nodes.add(unresolved);
                edges.add(new Edge(call.evidence().id(),unresolved.id(),"UNRESOLVED"));
            }
        }
        return new ArchitectureGraph(nodes,edges);
    }
    private static boolean excluded(Path path) {
        for (var part : path) if (Set.of(".git","target","build","test").contains(part.toString())) return true;
        return false;
    }
}
