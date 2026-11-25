package com.mfaneli.discovery;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class RestInventory {
    public record Endpoint(String file,long line,String method,String verb,String path,boolean body,boolean noContent,String returnType) {}
    public List<Endpoint> scan(Path root) throws IOException {
        var base=root.toAbsolutePath().normalize();
        List<Path> files;
        try(var walk=Files.walk(base)) {
            files=walk.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p->p.toString().endsWith(".java"))
                .filter(p->{for(var part:base.relativize(p)) if(Set.of(".git","target","build","test").contains(part.toString())) return false; return true;})
                .sorted().toList();
        }
        var endpoints=new ArrayList<Endpoint>();
        if(files.isEmpty()) return List.of();
        var compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null) throw new IOException("JDK required");
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(files));
            var positions=Trees.instance(task).getSourcePositions();
            for(var unit:task.parse()) {
                String file=base.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\','/');
                new TreeScanner<Void,Void>() {
                    String prefix="";
                    boolean controller;
                    String simple(AnnotationTree a) {
                        var name=a.getAnnotationType().toString(); return name.substring(name.lastIndexOf('.')+1);
                    }
                    boolean has(ModifiersTree m,String name) {return m.getAnnotations().stream().anyMatch(a->simple(a).equals(name));}
                    List<String> paths(AnnotationTree a) {
                        for(var arg:a.getArguments()) {
                            ExpressionTree value=arg;
                            if(arg instanceof AssignmentTree as) {
                                if(!Set.of("value","path").contains(as.getVariable().toString())) continue;
                                value=as.getExpression();
                            }
                            if(value instanceof LiteralTree l && l.getValue() instanceof String s) return List.of(s);
                            if(value instanceof NewArrayTree array && array.getInitializers()!=null) {
                                var values=new ArrayList<String>();
                                for(var item:array.getInitializers()) {
                                    if(!(item instanceof LiteralTree l) || !(l.getValue() instanceof String s)) return List.of();
                                    values.add(s);
                                }
                                return values;
                            }
                            return List.of();
                        }
                        return List.of("");
                    }
                    @Override public Void visitClass(ClassTree t,Void v) {
                        var old=prefix; boolean was=controller;
                        controller=has(t.getModifiers(),"RestController")||has(t.getModifiers(),"Controller");
                        prefix="";
                        for(var a:t.getModifiers().getAnnotations()) if(simple(a).equals("RequestMapping")) {
                            var p=paths(a); prefix=p.size()==1?p.getFirst():null;
                        }
                        super.visitClass(t,v); prefix=old; controller=was; return null;
                    }
                    @Override public Void visitMethod(MethodTree t,Void v) {
                        if(!controller) return null;
                        boolean body=t.getParameters().stream().anyMatch(p->has(p.getModifiers(),"RequestBody"));
                        boolean noContent=t.getModifiers().getAnnotations().stream().filter(a->simple(a).equals("ResponseStatus"))
                            .flatMap(a->a.getArguments().stream()).anyMatch(a->a.toString().matches("(?:value\\s*=\\s*|code\\s*=\\s*)?(?:HttpStatus\\.)?NO_CONTENT"));
                        for(var a:t.getModifiers().getAnnotations()) {
                            String verb=switch(simple(a)) {
                                case "GetMapping"->"GET"; case "PostMapping"->"POST"; case "PutMapping"->"PUT";
                                case "DeleteMapping"->"DELETE"; case "PatchMapping"->"PATCH"; case "RequestMapping"->"UNKNOWN"; default->"";
                            };
                            if(verb.isEmpty()) continue;
                            var paths=paths(a);
                            if(paths.isEmpty()) paths=List.of("<unresolved>");
                            for(var p:paths) {
                                String path=prefix==null||p.equals("<unresolved>")?"<unresolved>":("/"+prefix+"/"+p).replaceAll("/+","/");
                                if(path.length()>1&&path.endsWith("/")) path=path.substring(0,path.length()-1);
                                long pos=positions.getStartPosition(unit,a);
                                endpoints.add(new Endpoint(file,pos<0?-1:unit.getLineMap().getLineNumber(pos),t.getName().toString(),verb,path,body,noContent,t.getReturnType()==null?"":t.getReturnType().toString()));
                            }
                        }
                        return null;
                    }
                }.scan(unit,null);
            }
        }
        if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR)) throw new IOException("Source parsing failed");
        return List.copyOf(endpoints);
    }
}
