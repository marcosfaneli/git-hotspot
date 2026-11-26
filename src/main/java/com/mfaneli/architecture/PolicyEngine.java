package com.mfaneli.architecture;
import com.mfaneli.discovery.RestInventory.Endpoint;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;

public final class PolicyEngine {
    public enum Severity { INFO, WARNING, ERROR, OFF }
    public record Finding(String rule,String category,Severity severity,String status,String file,long line,String message) {}
    public record Context(List<Endpoint> endpoints,List<com.mfaneli.discovery.MethodFacts.Fact> facts,List<ArchitectureGraph.Node> persistence,ArchitectureGraph graph) {
        public Context(List<Endpoint> endpoints,List<com.mfaneli.discovery.MethodFacts.Fact> facts,List<ArchitectureGraph.Node> persistence) { this(endpoints,facts,persistence,new ArchitectureGraph(List.of(),List.of())); }
        public Context { endpoints=List.copyOf(endpoints); facts=List.copyOf(facts); persistence=List.copyOf(persistence); }
        public Context(List<Endpoint> endpoints) { this(endpoints,List.of(),List.of()); }
    }
    public interface Rule {
        String id();
        String category();
        Severity severity();
        List<Finding> evaluate(Context context);
    }
    private record RestRule(String id,Severity severity,java.util.function.Predicate<Endpoint> test,String message) implements Rule {
        public String category(){return "REST";}
        public List<Finding> evaluate(Context context) {
            return context.endpoints().stream().filter(test).map(e->new Finding(id,category(),severity,"CANDIDATE",e.file(),e.line(),message+" ["+e.verb()+" "+e.path()+"]")).toList();
        }
    }
    private record FactRule(String id,String category,Severity severity,String kind,String message) implements Rule {
        public List<Finding> evaluate(Context context) {
            return context.facts().stream().filter(f->f.kind().equals(kind)).map(f->new Finding(id,category,severity,"CANDIDATE",f.file(),f.line(),message+" ["+f.owner()+"#"+f.method()+": "+f.detail()+"]")).toList();
        }
    }
    private record UnresolvedPersistenceRule() implements Rule {
        public String id(){return "PERSIST-003";}
        public String category(){return "PERSISTENCE";}
        public Severity severity(){return Severity.INFO;}
        public List<Finding> evaluate(Context context){
            return context.persistence().stream().filter(n->n.kind().startsWith("UNRESOLVED_")).map(n->new Finding(id(),category(),severity(),"INCONCLUSIVE",n.file(),n.line(),n.name())).toList();
        }
    }
    public static List<Rule> defaults() {
        return List.of(
            new GraphPolicy(),
            new FactRule("REST-001","REST",Severity.ERROR,"GET_WRITE","Possible write from GET"),
            new FactRule("ARCH-001","ARCHITECTURE",Severity.WARNING,"CONTROLLER_REPOSITORY","Controller directly calls repository"),
            new FactRule("TX-001","TRANSACTIONS",Severity.WARNING,"SELF_TRANSACTION","Possible transactional self-invocation"),
            new FactRule("TX-002","TRANSACTIONS",Severity.WARNING,"TX_HTTP","Possible HTTP operation inside transaction"),
            new FactRule("TX-003","TRANSACTIONS",Severity.WARNING,"READ_ONLY_WRITE","Possible write inside read-only transaction"),
            new FactRule("PERSIST-001","PERSISTENCE",Severity.INFO,"UNPAGED_FIND_ALL","Unbounded repository findAll candidate"),
            new FactRule("PERSIST-002","PERSISTENCE",Severity.WARNING,"CONCAT_SQL","SQL expression uses concatenation; review parameterization"),
            new UnresolvedPersistenceRule(),
            new FactRule("MICRO-001","MICROSERVICES",Severity.INFO,"HTTP_CALL","Outbound HTTP candidate; review timeout and retry policy"),
            new FactRule("CODE-001","CODE_QUALITY",Severity.WARNING,"EMPTY_CATCH","Empty catch block"),
            new RestRule("REST-002",Severity.WARNING,e->e.verb().equals("GET")&&e.body(),"GET declares a request body"),
            new RestRule("REST-003",Severity.ERROR,e->e.noContent()&&!Set.of("void","Void","ResponseEntity<Void>","ResponseEntity<java.lang.Void>").contains(e.returnType()),"204 endpoint declares a potentially non-empty return type"),
            new RestRule("REST-007",Severity.INFO,e->!e.path().equals("<unresolved>")&&Arrays.stream(e.path().split("/")).anyMatch(p->Set.of("create","delete","update","get","save","remove").contains(p.toLowerCase(Locale.ROOT))),"Action-like path segment; review resource naming")
        );
    }
    public Map<String,Severity> config(Path file) throws IOException {
        var result=new TreeMap<String,Severity>();
        var known=defaults().stream().map(Rule::id).collect(java.util.stream.Collectors.toSet());
        for(var line:Files.readAllLines(file)) {
            if(line.isBlank()||line.stripLeading().startsWith("#")) continue;
            var parts=line.split("=",2);
            if(parts.length!=2||!(known.contains(parts[0].strip())||defaults().stream().anyMatch(r->("category."+r.category()).equals(parts[0].strip())))) throw new IOException("Unknown rule configuration: "+line);
            try {
                if(result.putIfAbsent(parts[0].strip(),Severity.valueOf(parts[1].strip()))!=null) throw new IOException("Duplicate rule: "+parts[0]);
            } catch(IllegalArgumentException e) {throw new IOException("Invalid severity: "+line,e);}
        }
        return Map.copyOf(result);
    }
    public List<Finding> evaluate(Context context,List<Rule> rules,Map<String,Severity> overrides) {
        var result=new ArrayList<Finding>();
        for(var rule:rules) {
            var severity=overrides.getOrDefault(rule.id(),overrides.getOrDefault("category."+rule.category(),rule.severity()));
            if(severity==Severity.OFF) continue;
            for(var f:rule.evaluate(context)) result.add(new Finding(f.rule(),f.category(),severity,f.status(),f.file(),f.line(),f.message()));
        }
        return result.stream().distinct().sorted(Comparator.comparing(Finding::rule).thenComparing(Finding::file).thenComparingLong(Finding::line).thenComparing(Finding::message)).toList();
    }
}
