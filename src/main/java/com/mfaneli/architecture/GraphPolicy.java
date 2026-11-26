package com.mfaneli.architecture;
import java.util.*;
import static com.mfaneli.architecture.PolicyEngine.*;

public final class GraphPolicy implements Rule {
    public String id(){return "ARCH-002";}
    public String category(){return "ARCHITECTURE";}
    public Severity severity(){return Severity.WARNING;}
    public List<Finding> evaluate(Context context){
        var graph=context.graph();
        var owners=new TreeMap<String,String>();
        var types=new TreeMap<String,ArchitectureGraph.Node>();
        for(var n:graph.nodes()) if(n.kind().equals("JAVA_TYPE")) types.put(n.id(),n);
        for(var e:graph.edges()) if(types.containsKey(e.from())&&e.kind().equals("CONTAINS")) owners.put(e.to(),e.from());
        var adjacency=new TreeMap<String,Set<String>>();
        for(var e:graph.edges()) if(e.kind().equals("POSSIBLE_CALL")){
            var from=owners.get(e.from());var to=owners.get(e.to());
            if(from!=null&&to!=null&&!from.equals(to)) adjacency.computeIfAbsent(from,k->new TreeSet<>()).add(to);
        }
        var remaining=new TreeSet<>(types.keySet());
        var findings=new ArrayList<Finding>();
        while(!remaining.isEmpty()){
            var first=remaining.first();
            var forward=reachable(first,adjacency);
            var component=new TreeSet<String>();
            for(var candidate:forward) if(reachable(candidate,adjacency).contains(first)) component.add(candidate);
            remaining.removeAll(component);
            if(component.size()<2) continue;
            var node=types.get(first);
            findings.add(new Finding(id(),category(),severity(),"CANDIDATE",node.file(),node.line(),"Mutually reachable types: "+String.join(", ",component)));
        }
        return findings;
    }
    private Set<String> reachable(String start,Map<String,Set<String>> adjacency){
        var seen=new TreeSet<String>();var queue=new ArrayDeque<String>();queue.add(start);
        while(!queue.isEmpty()){
            var next=queue.remove();
            if(seen.add(next)) queue.addAll(adjacency.getOrDefault(next,Set.of()));
        }
        return seen;
    }
}
