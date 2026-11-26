package com.mfaneli.architecture;
import com.mfaneli.discovery.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalPolicyTest {
    @TempDir Path root;
    @Test void evaluatesMultipleFamiliesWithoutOwnership() throws Exception {
        Files.writeString(root.resolve("Demo.java"),"""
            @RestController class Demo {
                OrderRepository repository;
                RestTemplate http;
                JdbcTemplate jdbc;
                @GetMapping("/orders") @Transactional(readOnly=true)
                void run() {
                    repository.save(null);
                    repository.findAll();
                    http.getForObject("/other", String.class);
                    jdbc.query("SELECT id FROM T WHERE id="+id);
                    this.other();
                    try { other(); } catch(Exception e) {}
                }
                @Transactional void other() {}
            }
            """);
        var c=new PolicyEngine.Context(new RestInventory().scan(root),new MethodFacts().scan(root),new AccessInventory().scan(root));
        var e=new PolicyEngine();
        var findings=e.evaluate(c,PolicyEngine.defaults(),Map.of());
        var ids=findings.stream().map(PolicyEngine.Finding::rule).collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.containsAll(Set.of("REST-001","ARCH-001","TX-001","TX-002","TX-003","PERSIST-001","PERSIST-002","PERSIST-003","MICRO-001","CODE-001")));
        assertEquals(findings,e.evaluate(c,PolicyEngine.defaults(),Map.of()));
        assertFalse(e.evaluate(c,PolicyEngine.defaults(),Map.of("category.TRANSACTIONS",PolicyEngine.Severity.OFF)).stream().anyMatch(f->f.category().equals("TRANSACTIONS")));
    }
}
