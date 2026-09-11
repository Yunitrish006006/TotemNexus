package dev.totem.nexus.space;
import dev.totem.nexus.network.AccessPlayersPayload;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NexusAccessPlayersPayloadTest {
    @Test void pageBoundsAndDuplicateIdentitiesAreRejected() {
        UUID id = UUID.randomUUID(); var entry = new AccessPlayersPayload.Entry(id,"Alice",false,false);
        assertThrows(IllegalArgumentException.class,() -> new AccessPlayersPayload("player",id,id,"administrator",0,1,1,Collections.nCopies(7,entry)));
        assertThrows(IllegalArgumentException.class,() -> new AccessPlayersPayload("player",id,id,"allowed",0,1,1,List.of(entry,entry)));
        assertThrows(IllegalArgumentException.class,() -> new AccessPlayersPayload("player",id,id,"owner",0,1,1,List.of()));
    }
}
