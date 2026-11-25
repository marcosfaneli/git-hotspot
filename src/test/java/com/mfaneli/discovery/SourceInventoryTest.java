package com.mfaneli.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceInventoryTest {
    @TempDir Path root;

    @Test void discoversWithoutResolvingSpringDependencies() throws Exception {
        Files.writeString(root.resolve("Controller.java"), """
            class Controller {
                @GetMapping("/orders")
                void list() {}
            }
            """);
        var inventory = new SourceInventory();
        var entries = inventory.scan(root);
        assertTrue(entries.stream().anyMatch(e -> e.kind().equals("TYPE") && e.name().equals("Controller")));
        assertTrue(entries.stream().anyMatch(e -> e.kind().equals("MAPPING_CANDIDATE") && e.line() == 2));
        assertEquals(entries, inventory.scan(root));
    }

    @Test void rejectsInvalidSource() throws Exception {
        Files.writeString(root.resolve("Broken.java"), "class {");
        assertThrows(java.io.IOException.class, () -> new SourceInventory().scan(root));
    }
}
