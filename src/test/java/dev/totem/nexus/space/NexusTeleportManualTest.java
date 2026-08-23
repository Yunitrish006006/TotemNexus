package dev.totem.nexus.space;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusTeleportManualTest {
    @Test
    void guideExcludesObsoleteAcquisitionPageButKeepsSetupThroughSpecialists() {
        assertEquals(23, NexusTeleportManual.pageKeys().size());
        assertEquals("book.deadrecall.nexus_teleport_manual.page.2", NexusTeleportManual.pageKeys().getFirst());
        assertEquals("book.deadrecall.nexus_teleport_manual.page.24", NexusTeleportManual.pageKeys().getLast());
    }

    @Test
    void focusedMaterialPagesStayInsideTheVirtualManual() {
        int assembledPageCount = 3 + NexusTeleportManual.pageKeys().size();
        assertEquals(26, assembledPageCount,
                "cover, contents, section divider and twenty-three body pages must remain deterministic");
        assertTrue(assembledPageCount < TotemManualAssembler.MAX_PAGES,
                "the Nexus chapter must remain compatible with the unlimited shared Totem Manual");
    }
}
