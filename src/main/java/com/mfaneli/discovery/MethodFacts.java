package com.mfaneli.discovery;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class MethodFacts {
    public record Fact(String file,long line,String owner,String method,String kind,String detail) {}
    public List<Fact> scan(Path root) throws IOException {
        var base=root.toAbsolutePath().normalize();
        List<Path> files;
        try(var walk=Files.walk(base)) {
            files=walk.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p->p.toString().endsWith(".java"))
                .filter(p->{for(var part:base.relativize(p)) if(Set.of(".git","target","build","test").contains(part.toString())) return false; return true;})
                .sorted().toList();
        }
        var facts=new ArrayList<Fact>();
        if(files.isEmpty()) return List.of();
        var compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null) throw new IOException("JDK required");
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(files));
            var positions=Trees.instance(task).getSourcePositions();
            for(var unit:task.parse()) {
                var file=base.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\','/');
                new TreeScanner<Void,Void>() {
                    String owner="",method="";
                    boolean controller,tx,readOnly,get;
                    Map<String,String> fields=Map.of();
                    Set<String> transactional=Set.of();
                    String simple(AnnotationTree a) {var n=a.getAnnotationType().toString();return n.substring(n.lastIndexOf('.')+1);}
                    boolean has(ModifiersTree m,String name){return m.getAnnotations().stream().anyMatch(a->simple(a).equals(name));}
                    boolean readOnly(ModifiersTree m){return m.getAnnotations().stream().filter(a->simple(a).equals("Transactional")).flatMap(a->a.getArguments().stream()).anyMatch(a->a instanceof AssignmentTree x&&x.getVariable().toString().equals("readOnly")&&x.getExpression() instanceof LiteralTree l&&Boolean.TRUE.equals(l.getValue()));}
                    void add(String kind,String detail,Tree t){
                        long pos=positions.getStartPosition(unit,t);
                        facts.add(new Fact(file,pos<0?-1:unit.getLineMap().getLineNumber(pos),owner,method,kind,detail));
                    }
                    @Override public Void visitClass(ClassTree t,Void v){
                        if(!method.isEmpty()||t.getSimpleName().length()==0) return null;
                        var oldOwner=owner;var oldFields=fields;var oldTransactional=transactional;
                        boolean oldController=controller,oldTx=tx,oldRead=readOnly;
                        owner=oldOwner.isEmpty()?(unit.getPackageName()==null?"":unit.getPackageName()+".")+t.getSimpleName():oldOwner+"$"+t.getSimpleName();
                        controller=has(t.getModifiers(),"RestController")||has(t.getModifiers(),"Controller");
                        tx=has(t.getModifiers(),"Transactional");readOnly=readOnly(t.getModifiers());
                        fields=new HashMap<>();transactional=new HashSet<>();
                        for(var member:t.getMembers()){
                            if(member instanceof VariableTree f) fields.put(f.getName().toString(),f.getType().toString());
                            if(member instanceof MethodTree m&&has(m.getModifiers(),"Transactional")) transactional.add(m.getName().toString());
                        }
                        if(has(t.getModifiers(),"FeignClient")) add("HTTP_CLIENT_DECLARATION","FeignClient",t);
                        super.visitClass(t,v);
                        owner=oldOwner;fields=oldFields;transactional=oldTransactional;controller=oldController;tx=oldTx;readOnly=oldRead;return null;
                    }
                    @Override public Void visitMethod(MethodTree t,Void v){
                        boolean oldTx=tx,oldRead=readOnly;
                        method=t.getName()+"("+String.join(",",t.getParameters().stream().map(p->p.getType().toString()).toList())+")";
                        if(has(t.getModifiers(),"Transactional")) {tx=true;readOnly=readOnly(t.getModifiers());}
                        get=has(t.getModifiers(),"GetMapping");
                        if(tx) add("TRANSACTION","declared",t);
                        if(t.getBody()!=null) scan(t.getBody(),v);
                        method="";tx=oldTx;readOnly=oldRead;get=false;return null;
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree t,Void v){
                        if(method.isEmpty()) return super.visitMethodInvocation(t,v);
                        String name="",receiver="";
                        if(t.getMethodSelect() instanceof IdentifierTree i) name=i.getName().toString();
                        if(t.getMethodSelect() instanceof MemberSelectTree m){name=m.getIdentifier().toString();receiver=m.getExpression().toString();}
                        String field=receiver.startsWith("this.")?receiver.substring(5):receiver;
                        String type=fields.getOrDefault(field,"");
                        boolean repo=type.endsWith("Repository");
                        boolean jdbc=type.endsWith("JdbcTemplate")||type.endsWith("NamedParameterJdbcTemplate");
                        boolean write=(repo&&Set.of("save","saveAll","saveAndFlush","delete","deleteById","deleteAll").contains(name))||(jdbc&&name.equals("update"));
                        boolean http=(type.endsWith("RestTemplate")&&Set.of("exchange","getForObject","getForEntity","postForObject","postForEntity","put","delete","execute").contains(name))||(type.endsWith("RestClient")&&Set.of("get","post","put","delete","patch").contains(name));
                        if(controller&&repo) add("CONTROLLER_REPOSITORY",type+"."+name,t);
                        if(get&&write) add("GET_WRITE",type+"."+name,t);
                        if(readOnly&&write) add("READ_ONLY_WRITE",type+"."+name,t);
                        if(tx&&http) add("TX_HTTP",type+"."+name,t);
                        if(http) add("HTTP_CALL",type+"."+name,t);
                        if((receiver.isEmpty()||receiver.equals("this"))&&transactional.contains(name)) add("SELF_TRANSACTION",name,t);
                        if(repo&&name.equals("findAll")&&t.getArguments().isEmpty()) add("UNPAGED_FIND_ALL",type,t);
                        if(jdbc&&Set.of("query","queryForObject","update","execute").contains(name)&&!t.getArguments().isEmpty()&&t.getArguments().getFirst() instanceof BinaryTree b&&b.getKind()==Tree.Kind.PLUS) add("CONCAT_SQL",type+"."+name,t);
                        return super.visitMethodInvocation(t,v);
                    }
                    @Override public Void visitCatch(CatchTree t,Void v){
                        if(!method.isEmpty()&&t.getBlock().getStatements().isEmpty()) add("EMPTY_CATCH","empty catch block",t);
                        return super.visitCatch(t,v);
                    }
                }.scan(unit,null);
            }
        }
        if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR)) throw new IOException("Source parsing failed");
        return List.copyOf(facts);
    }
}
