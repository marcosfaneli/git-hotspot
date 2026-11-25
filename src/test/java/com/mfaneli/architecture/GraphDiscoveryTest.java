package com.mfaneli.architecture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphDiscoveryTest {
    @TempDir Path root;
    @Test void discoversDeclaredMetadataAndStableGraph() throws Exception {
        Files.writeString(root.resolve("pom.xml"),"<project><artifactId>demo</artifactId><dependencies><dependency><groupId>x</groupId><artifactId>y</artifactId></dependency></dependencies></project>");
        Files.writeString(root.resolve("Demo.java"),"class Demo { void run() {} }");
        var discovery = new GraphDiscovery();
        var graph = discovery.scan(root);
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("MAVEN_PROJECT")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.kind().equals("DECLARES_DEPENDENCY")));
        assertEquals(GraphReport.json(graph),GraphReport.json(discovery.scan(root)));
    }
    @Test void rejectsDoctype() throws Exception {
        Files.writeString(root.resolve("pom.xml"),"<!DOCTYPE project [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><project><artifactId>&x;</artifactId></project>");
        assertThrows(java.io.IOException.class, () -> new GraphDiscovery().scan(root));
    }
}
