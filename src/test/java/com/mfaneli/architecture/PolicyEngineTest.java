package com.mfaneli.architecture;
import com.mfaneli.discovery.RestInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineTest {
    @TempDir Path root;
    @Test void detectsRestCandidatesAndSupportsOverrides() throws Exception {
        Files.writeString(root.resolve("Demo.java"),"""
            @RestController @RequestMapping("/orders")
            class Demo {
                @GetMapping("/get") String get(@RequestBody String body) {return body;}
                @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) String delete(){return "x";}
            }
            """);
        var endpoints=new RestInventory().scan(root);
        assertEquals("/orders/get",endpoints.getFirst().path());
        var engine=new PolicyEngine();
        var context=new PolicyEngine.Context(endpoints);
        assertEquals(3,engine.evaluate(context,PolicyEngine.defaults(),Map.of()).size());
        assertEquals(2,engine.evaluate(context,PolicyEngine.defaults(),Map.of("REST-007",PolicyEngine.Severity.OFF)).size());
    }
}
