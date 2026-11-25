package com.mfaneli.architecture;
import java.util.*;

public record ArchitectureGraph(List<Node> nodes, List<Edge> edges) {
    public record Node(String id, String kind, String name, String file, long line) {}
    public record Edge(String from, String to, String kind) {}
    public ArchitectureGraph {
        nodes = nodes.stream().distinct().sorted(Comparator.comparing(Node::id)).toList();
        edges = edges.stream().distinct().sorted(Comparator.comparing(Edge::from).thenComparing(Edge::to).thenComparing(Edge::kind)).toList();
        var ids = new HashSet<String>();
        for (var node : nodes) if (!ids.add(node.id())) throw new IllegalArgumentException("Duplicate node: " + node.id());
        for (var edge : edges) if (!ids.contains(edge.from()) || !ids.contains(edge.to())) throw new IllegalArgumentException("Dangling edge: " + edge);
    }
}
