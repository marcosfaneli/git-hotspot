package com.mfaneli.architecture;
import com.mfaneli.discovery.AccessInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {
    @TempDir Path root;
    @Test void separatesMappingFromAccessAndChecksOwnership() throws Exception {
        Files.writeString(root.resolve("Demo.java"),"""
            @Table(name="OTHER")
            class Demo {
                @GetMapping("/orders")
                void run() {
                    jdbc.query("SELECT id FROM WORKORDER");
                    jdbc.update("UPDATE WORKORDER SET id = ?");
                    jdbc.query(dynamic);
                }
            }
            """);
        var evidence=new AccessInventory().scan(root);
        var findings=new RuleEngine().review("orders",evidence,Map.of("WORKORDER","legacy","OTHER","legacy"));
        assertEquals(2,findings.size());
        assertTrue(findings.stream().anyMatch(f->f.rule().equals("CROSS_OWNER_WRITE")));
        assertTrue(findings.stream().anyMatch(f->f.rule().equals("CROSS_OWNER_READ")));
        assertTrue(evidence.stream().anyMatch(n->n.kind().equals("UNRESOLVED_SQL")));
        assertEquals(0,new RuleEngine().review("legacy",evidence,Map.of("WORKORDER","legacy")).size());
    }
}
