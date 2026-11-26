package com.mfaneli.discovery;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.mfaneli.architecture.ArchitectureGraph.Node;

public final class JpaInventory {
    private record Type(String name,String table,String entity){}
    private record Use(String file,long line,String repository,String method){}
    private static String simple(String name){return name.substring(name.lastIndexOf('.')+1);}
    public List<Node> scan(Path root) throws IOException {
        var base=root.toAbsolutePath().normalize();
        List<Path> files;
        try(var walk=Files.walk(base)){
            files=walk.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).filter(p->p.toString().endsWith(".java"))
                .filter(p->{for(var part:base.relativize(p)) if(Set.of(".git","target","build","test").contains(part.toString())) return false;return true;}).sorted().toList();
        }
        if(files.isEmpty())return List.of();
        var types=new ArrayList<Type>();var uses=new ArrayList<Use>();
        var compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IOException("JDK required");
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)){
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(files));
            var positions=Trees.instance(task).getSourcePositions();
            for(var unit:task.parse()){
                var file=base.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\','/');
                new TreeScanner<Void,Void>(){
                    Map<String,String> fields=Map.of(),shadow=Map.of();
                    String literal(AnnotationTree a,String key){
                        for(var x:a.getArguments())if(x instanceof AssignmentTree as&&as.getVariable().toString().equals(key)&&as.getExpression() instanceof LiteralTree l&&l.getValue() instanceof String s)return s;
                        return null;
                    }
                    @Override public Void visitClass(ClassTree t,Void v){
                        if(t.getSimpleName().length()==0)return null;
                        var old=fields;fields=new HashMap<>();
                        String table=null,entity=null;
                        boolean isEntity=t.getModifiers().getAnnotations().stream().anyMatch(a->simple(a.getAnnotationType().toString()).equals("Entity"));
                        if(isEntity)for(var a:t.getModifiers().getAnnotations())if(simple(a.getAnnotationType().toString()).equals("Table")){
                            var name=literal(a,"name");var schema=literal(a,"schema");
                            if(name!=null&&!name.isBlank())table=((schema==null||schema.isBlank()?"":schema+".")+name).toUpperCase(Locale.ROOT);
                        }
                        for(var parent:t.getImplementsClause())if(parent instanceof ParameterizedTypeTree p&&simple(p.getType().toString()).equals("JpaRepository")&&p.getTypeArguments().size()==2)entity=p.getTypeArguments().getFirst().toString();
                        types.add(new Type(t.getSimpleName().toString(),table,entity));
                        for(var member:t.getMembers())if(member instanceof VariableTree f)fields.put(f.getName().toString(),f.getType().toString());
                        super.visitClass(t,v);fields=old;return null;
                    }
                    @Override public Void visitMethod(MethodTree t,Void v){
                        var old=shadow;shadow=new HashMap<>();
                        for(var p:t.getParameters())shadow.put(p.getName().toString(),p.getType().toString());
                        if(t.getBody()!=null){
                            new TreeScanner<Void,Void>(){
                                @Override public Void visitVariable(VariableTree x,Void ignored){shadow.put(x.getName().toString(),"");return super.visitVariable(x,ignored);}
                                @Override public Void visitClass(ClassTree x,Void ignored){return null;}
                            }.scan(t.getBody(),null);
                            scan(t.getBody(),v);
                        }
                        shadow=old;return null;
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree t,Void v){
                        if(t.getMethodSelect() instanceof MemberSelectTree m){
                            var receiver=m.getExpression().toString();
                            var type=receiver.startsWith("this.")?fields.get(receiver.substring(5)):shadow.getOrDefault(receiver,fields.get(receiver));
                            var method=m.getIdentifier().toString();
                            if(type!=null&&Set.of("save","saveAll","saveAndFlush","delete","deleteById","deleteAll","findById","findAll","count","existsById").contains(method)){
                                long pos=positions.getStartPosition(unit,t);
                                uses.add(new Use(file,pos<0?-1:unit.getLineMap().getLineNumber(pos),simple(type),method));
                            }
                        }
                        return super.visitMethodInvocation(t,v);
                    }
                }.scan(unit,null);
            }
        }
        if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))throw new IOException("Source parsing failed");
        var result=new ArrayList<Node>();int ordinal=0;
        for(var u:uses){
            var repos=types.stream().filter(t->t.name().equals(u.repository())).toList();
            if(repos.size()!=1||repos.getFirst().entity()==null)continue;
            var entities=types.stream().filter(t->t.name().equals(simple(repos.getFirst().entity()))).toList();
            String table=entities.size()==1?entities.getFirst().table():null;
            String kind=table==null?"UNRESOLVED_JPA":Set.of("save","saveAll","saveAndFlush","delete","deleteById","deleteAll").contains(u.method())?"JPA_WRITE_CANDIDATE":"JPA_READ_CANDIDATE";
            result.add(new Node("jpa:"+(++ordinal),kind,table==null?u.repository()+"."+u.method():table,u.file(),u.line()));
        }
        return List.copyOf(result);
    }
}
