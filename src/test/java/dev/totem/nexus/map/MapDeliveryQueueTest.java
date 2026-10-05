package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapDeliveryQueueTest {
    record Page(int id,int revision) { }
    @Test void repeatedDirtyFirstPageDoesNotStarveLaterPages() {
        List<Page> pending=new ArrayList<>();Set<Integer> arrived=new HashSet<>();
        for(int tick=0;tick<20;tick++) {
            var request=new ArrayList<Page>();for(int id=0;id<20;id++) request.add(new Page(id,tick));
            pending=MapDeliveryQueue.merge(pending,request,Page::id);
            Page next=pending.removeFirst();assertEquals(tick,next.revision());arrived.add(next.id());
            assertEquals(19,pending.size());
        }
        assertEquals(20,arrived.size());
    }
    @Test void obsoleteViewportPagesAreRemoved() {
        assertEquals(List.of(new Page(2,2),new Page(3,2)),MapDeliveryQueue.merge(
                List.of(new Page(1,1),new Page(2,1)),List.of(new Page(3,2),new Page(2,2)),Page::id));
    }
}
