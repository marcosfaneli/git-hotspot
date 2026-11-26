package com.mfaneli.discovery;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static com.mfaneli.architecture.ArchitectureGraph.*;

public final class AccessInventory {
    private static final Pattern SQL = Pattern.compile("(?i)^\\s*(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\b");
    private static final Pattern TABLE = Pattern.compile("(?i)\\b(FROM|JOIN|INSERT\\s+INTO|UPDATE)\\s+([a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)?)");
    public List<Node> scan(Path root) throws IOException {
        var base = root.toAbsolutePath().normalize();
        List<Path> files;
        try (var walk = Files.walk(base)) {
            files = walk.filter(p -> Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> { for(var part:base.relativize(p)) if(Set.of(".git","target","build","test").contains(part.toString())) return false; return true; })
                .sorted().toList();
        }
        var result = new ArrayList<Node>();
        if(files.isEmpty()) return result;
        var compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler==null) throw new IOException("JDK required");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(files));
            var positions=Trees.instance(task).getSourcePositions();
            for(var unit:task.parse()) {
                String file=base.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\','/');
                new TreeScanner<Void,Void>() {
                    int ordinal;
                    void add(String kind,String name,Tree tree) {
                        long pos=positions.getStartPosition(unit,tree);
                        result.add(new Node("evidence:"+file+":"+(++ordinal),kind,name,file,pos<0?-1:unit.getLineMap().getLineNumber(pos)));
                    }
                    String simple(AnnotationTree a) {
                        String n=a.getAnnotationType().toString(); return n.substring(n.lastIndexOf('.')+1);
                    }
                    String literal(ExpressionTree tree) {
                        return tree instanceof LiteralTree l && l.getValue() instanceof String s ? s : null;
                    }
                    String arg(AnnotationTree a,String key) {
                        for(var e:a.getArguments()) {
                            if(e instanceof AssignmentTree as && as.getVariable().toString().equals(key)) return literal(as.getExpression());
                            if(key.equals("value") && !(e instanceof AssignmentTree)) return literal(e);
                        }
                        return null;
                    }
                    void sql(String s,Tree tree) {
                        if(s==null || !SQL.matcher(s).find() || s.contains("'") || s.contains("\"") || s.contains("--") || s.contains("/*") || s.contains("(") || s.contains(";")) {
                            add("UNRESOLVED_SQL","Unsupported or dynamic SQL",tree); return;
                        }
                        var matcher=TABLE.matcher(s);
                        boolean found=false;
                        boolean first=true;
                        String operation=s.stripLeading().toUpperCase(Locale.ROOT);
                        while(matcher.find()) {
                            found=true;
                            boolean write=first && !operation.startsWith("SELECT");
                            add(write?"SQL_WRITE_CANDIDATE":"SQL_READ_CANDIDATE",matcher.group(2).toUpperCase(Locale.ROOT),tree);
                            first=false;
                        }
                        if(!found) add("UNRESOLVED_SQL","No supported table token",tree);
                    }
                    @Override public Void visitClass(ClassTree t,Void v) {
                        for(var a:t.getModifiers().getAnnotations()) {
                            if(simple(a).equals("Table")) {
                                String name=arg(a,"name"), schema=arg(a,"schema");
                                if(name!=null) add("TABLE_MAPPING",((schema==null||schema.isBlank()?"":schema+".")+name).toUpperCase(Locale.ROOT),a);
                            }
                            if(simple(a).equals("RequestMapping")) add("CLASS_MAPPING_CANDIDATE",a.toString(),a);
                        }
                        return super.visitClass(t,v);
                    }
                    @Override public Void visitMethod(MethodTree t,Void v) {
                        for(var a:t.getModifiers().getAnnotations()) {
                            if(Set.of("GetMapping","PostMapping","PutMapping","DeleteMapping","PatchMapping","RequestMapping").contains(simple(a))) add("ENDPOINT_CANDIDATE",a.toString(),a);
                            if(simple(a).equals("Transactional")) add("TRANSACTION_CANDIDATE",t.getName().toString(),a);
                            if(simple(a).equals("Query")) {
                                boolean nativeQuery=a.getArguments().stream().anyMatch(e -> e instanceof AssignmentTree as && as.getVariable().toString().equals("nativeQuery") && as.getExpression() instanceof LiteralTree l && Boolean.TRUE.equals(l.getValue()));
                                if(nativeQuery) sql(arg(a,"value"),a);
                                else add("UNRESOLVED_JPQL","Query requires entity resolution",a);
                            }
                        }
                        return super.visitMethod(t,v);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree t,Void v) {
                        if(t.getMethodSelect() instanceof MemberSelectTree m) {
                            String method=m.getIdentifier().toString();
                            if(Set.of("query","queryForObject","queryForList","update","execute","prepareStatement","createNativeQuery").contains(method)) {
                                if(!t.getArguments().isEmpty()) sql(literal(t.getArguments().getFirst()),t);
                            }
                        }
                        return super.visitMethodInvocation(t,v);
                    }
                }.scan(unit,null);
            }
        }
        if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR)) throw new IOException("Source parsing failed");
        result.addAll(new JpaInventory().scan(root));
        return List.copyOf(result);
    }
}
