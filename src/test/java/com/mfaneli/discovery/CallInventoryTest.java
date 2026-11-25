package com.mfaneli.discovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CallInventoryTest {
    @TempDir Path root;
    @Test void distinguishesOverloadsAndUnresolvedCalls() throws Exception {
        var source = """
            package demo;
            class Controller {
                Service service;
                void run() { this.service.save(); missing(); }
            }
            class Service {
                void save() {}
                void save(String x) {}
            }
            """;
        var file = root.resolve("Demo.java");
        Files.writeString(file,source);
        var scanner = new CallInventory();
        var graph = scanner.scan(root);
        assertTrue(graph.edges().stream().anyMatch(e -> e.kind().equals("POSSIBLE_CALL") && e.to().endsWith("#save()")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("UNRESOLVED_CALL") && n.name().equals("missing")));
        assertEquals(graph,scanner.scan(root));
        Files.writeString(file,"\n"+source);
        assertEquals(graph.nodes().stream().map(n -> n.id()).toList(),scanner.scan(root).nodes().stream().map(n -> n.id()).toList());
    }
    @Test void doesNotChooseBetweenSameArityOverloads() throws Exception {
        Files.writeString(root.resolve("Demo.java"),"class Demo { void run(){ save(1); } void save(int x){} void save(String x){} }");
        var graph = new CallInventory().scan(root);
        assertFalse(graph.edges().stream().anyMatch(e -> e.kind().equals("POSSIBLE_CALL")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("UNRESOLVED_CALL")));
    }
}
