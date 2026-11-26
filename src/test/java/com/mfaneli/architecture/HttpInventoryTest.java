package com.mfaneli.architecture;
import com.mfaneli.discovery.HttpInventory;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class HttpInventoryTest {
    @TempDir Path root;
    @Test void detectsDeclarationsWithoutLeakingValues() throws Exception {
        Files.writeString(root.resolve("application.properties"),"""
            spring.cloud.openfeign.client.config.orders.connectTimeout=0
            spring.cloud.openfeign.client.config.orders.readTimeout=5000
            spring.cloud.openfeign.client.config.billing.readTimeout=${SECRET}
            spring.cloud.openfeign.client.config.orders.retryer=example.Retryer
            password=secret
            """);
        var nodes=new HttpInventory().scan(root);
        assertEquals(4,nodes.size());
        var c=new PolicyEngine.Context(List.of(),List.of(),List.of(),new ArchitectureGraph(List.of(),List.of()),null,nodes);
        var findings=new PolicyEngine().evaluate(c,PolicyEngine.defaults(),Map.of());
        assertEquals(3,findings.size());
        assertFalse(findings.toString().contains("SECRET"));
        assertFalse(findings.toString().contains("example.Retryer"));
    }
}
