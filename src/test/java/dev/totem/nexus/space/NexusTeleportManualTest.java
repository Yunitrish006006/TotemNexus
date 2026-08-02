package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NexusTeleportManualTest {
    @Test
    void guidePageDefinitionCoversSetupMaterialsAndCatalysts() {
        assertEquals(7, NexusTeleportManual.pageKeys().size());
        assertEquals("book.deadrecall.nexus_teleport_manual.page.1", NexusTeleportManual.pageKeys().getFirst());
        assertEquals("book.deadrecall.nexus_teleport_manual.page.7", NexusTeleportManual.pageKeys().getLast());
    }
}
