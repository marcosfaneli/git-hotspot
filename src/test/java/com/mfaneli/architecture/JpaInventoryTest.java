package com.mfaneli.architecture;
import com.mfaneli.discovery.JpaInventory;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class JpaInventoryTest {
    @TempDir Path root;
    @Test void mapsExplicitTable() throws Exception {
        Files.writeString(root.resolve("Demo.java"),"""
            @Entity @Table(name="orders",schema="public") class Order {}
            interface Orders extends JpaRepository<Order,Long> {}
            class Service {
                Orders repo;
                void read(){repo.findById(1L);}
                void write(){this.repo.save(null);}
                void shadow(Object repo){repo.save(null);}
            }
            """);
        var nodes=new JpaInventory().scan(root);
        assertEquals(2,nodes.size());
        assertEquals("JPA_READ_CANDIDATE",nodes.getFirst().kind());
        assertEquals("JPA_WRITE_CANDIDATE",nodes.getLast().kind());
        assertTrue(nodes.stream().allMatch(n->n.name().equals("PUBLIC.ORDERS")));
    }
}
