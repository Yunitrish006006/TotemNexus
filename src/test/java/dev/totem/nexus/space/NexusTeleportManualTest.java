package dev.totem.nexus.space;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusTeleportManualTest {
    @Test
    void guidePageDefinitionCoversSetupMaterialsCatalystsAndSpecialists() {
        assertEquals(24, NexusTeleportManual.pageKeys().size());
        assertEquals("book.deadrecall.nexus_teleport_manual.page.1", NexusTeleportManual.pageKeys().getFirst());
        assertEquals("book.deadrecall.nexus_teleport_manual.page.24", NexusTeleportManual.pageKeys().getLast());
    }

    @Test
    void focusedMaterialPagesStayInsideTheVanillaBookLimit() {
        int assembledPageCount = 3 + NexusTeleportManual.pageKeys().size();
        assertEquals(27, assembledPageCount,
                "cover, contents, section divider and twenty-four body pages must remain deterministic");
        assertTrue(assembledPageCount < TotemManualAssembler.MAX_PAGES,
                "the standalone Nexus guide must leave room for other module sections");
    }
}
