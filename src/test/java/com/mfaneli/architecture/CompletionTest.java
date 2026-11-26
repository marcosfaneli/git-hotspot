package com.mfaneli.architecture;
import java.util.*;
import java.nio.file.*;
import com.mfaneli.discovery.RestInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class CompletionTest {
    @TempDir Path root;
    @Test void routes() throws Exception {
        Files.writeString(root.resolve("Demo.java"),"""
            @RestController @RequestMapping({"/a","/b"}) class Demo {
                @GetMapping("/{id}") void first(@PathVariable("wrong") String id){}
                @GetMapping("/{other}") void second(){}
                @GetMapping(path="/{id}",params="x") void third(){}
            }
            """);
        var c=new PolicyEngine.Context(new RestInventory().scan(root));
        assertEquals(6,c.endpoints().size());
        assertEquals(2,new RoutePolicy(false).evaluate(c).size());
        assertEquals(2,new RoutePolicy(true).evaluate(c).size());
    }
    @Test void isolatesDatabaseOwnership(){
        var nodes=List.of(new ArchitectureGraph.Node("n","SQL_WRITE_CANDIDATE","PUBLIC.ORDERS","A.java",2));
        var graph=new ArchitectureGraph(List.of(),List.of());
        var owners=Map.of("A:PUBLIC.ORDERS","orders","B:PUBLIC.ORDERS","billing");
        var a=new PolicyEngine.Context(List.of(),List.of(),nodes,graph,new OwnershipPolicy.Scope("orders","A",owners));
        var b=new PolicyEngine.Context(List.of(),List.of(),nodes,graph,new OwnershipPolicy.Scope("orders","B",owners));
        assertTrue(new OwnershipPolicy("WRITE").evaluate(a).isEmpty());
        assertEquals(1,new OwnershipPolicy("WRITE").evaluate(b).size());
        assertTrue(PolicyReport.json(new OwnershipPolicy("WRITE").evaluate(b),0).contains("B:PUBLIC.ORDERS"));
    }
}
