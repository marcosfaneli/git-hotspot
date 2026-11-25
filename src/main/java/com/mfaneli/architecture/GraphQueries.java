package com.mfaneli.architecture;
import java.util.*;

public final class GraphQueries {
    public List<String> path(ArchitectureGraph graph,String from,String to) {
        var ids=new HashSet<String>();
        for(var node:graph.nodes()) ids.add(node.id());
        if(!ids.contains(from)||!ids.contains(to)) throw new IllegalArgumentException("Unknown node ID");
        var next=new HashMap<String,List<String>>();
        for(var edge:graph.edges()) if(edge.kind().equals("POSSIBLE_CALL"))
            next.computeIfAbsent(edge.from(),k->new ArrayList<>()).add(edge.to());
        var queue=new ArrayDeque<String>();
        var parent=new HashMap<String,String>();
        queue.add(from); parent.put(from,from);
        while(!queue.isEmpty()) {
            var current=queue.remove();
            if(current.equals(to)) {
                var path=new ArrayList<String>();
                while(!current.equals(from)) { path.add(current); current=parent.get(current); }
                path.add(from); return List.copyOf(path.reversed());
            }
            for(var target:next.getOrDefault(current,List.of())) if(!parent.containsKey(target)) {
                parent.put(target,current); queue.add(target);
            }
        }
        return List.of();
    }
}
