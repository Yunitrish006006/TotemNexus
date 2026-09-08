package dev.totem.nexus.space;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusTeleportManualTest {
    @Test
    void guideExcludesObsoleteAcquisitionPageButKeepsSetupThroughSpecialists() {
        assertEquals(25, NexusTeleportManual.pageKeys().size());
        assertEquals("book.totem.nexus_teleport_manual.page.2", NexusTeleportManual.pageKeys().getFirst());
        assertEquals("book.totem.nexus_teleport_manual.page.26", NexusTeleportManual.pageKeys().getLast());
    }

    @Test
    void focusedMaterialPagesStayInsideTheVirtualManual() {
        int assembledPageCount = 3 + NexusTeleportManual.pageKeys().size();
        assertEquals(28, assembledPageCount,
                "cover, contents, section divider and twenty-five body pages must remain deterministic");
        assertTrue(assembledPageCount < TotemManualAssembler.MAX_PAGES,
                "the Nexus chapter must remain compatible with the unlimited shared Totem Manual");
    }
}
