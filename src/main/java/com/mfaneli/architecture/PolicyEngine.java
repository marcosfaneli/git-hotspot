package com.mfaneli.architecture;
import com.mfaneli.discovery.RestInventory.Endpoint;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;

public final class PolicyEngine {
    public enum Severity { INFO, WARNING, ERROR, OFF }
    public record Finding(String rule,String category,Severity severity,String status,String file,long line,String message) {}
    public record Context(List<Endpoint> endpoints) { public Context { endpoints=List.copyOf(endpoints); } }
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
    public static List<Rule> defaults() {
        return List.of(
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
            if(parts.length!=2||!known.contains(parts[0].strip())) throw new IOException("Unknown rule configuration: "+line);
            try {
                if(result.putIfAbsent(parts[0].strip(),Severity.valueOf(parts[1].strip()))!=null) throw new IOException("Duplicate rule: "+parts[0]);
            } catch(IllegalArgumentException e) {throw new IOException("Invalid severity: "+line,e);}
        }
        return Map.copyOf(result);
    }
    public List<Finding> evaluate(Context context,List<Rule> rules,Map<String,Severity> overrides) {
        var result=new ArrayList<Finding>();
        for(var rule:rules) {
            var severity=overrides.getOrDefault(rule.id(),rule.severity());
            if(severity==Severity.OFF) continue;
            for(var f:rule.evaluate(context)) result.add(new Finding(f.rule(),f.category(),severity,f.status(),f.file(),f.line(),f.message()));
        }
        return result.stream().distinct().sorted(Comparator.comparing(Finding::rule).thenComparing(Finding::file).thenComparingLong(Finding::line).thenComparing(Finding::message)).toList();
    }
}
