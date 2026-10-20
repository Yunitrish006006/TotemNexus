package dev.totem.nexus.space;

import dev.totem.nexus.network.NexusMapDetailPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NexusExtendedGeometryPayloadTest {
    @Test void logicalExtentRoundTripsSeparatelyFromNativeRasterScale() {
        var root=new NexusMapDetailPayload.Layer(1,64,64,4,"minecraft:overworld",false);
        var payload=new NexusMapDetailPayload(1,List.of(),List.of(root),20);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { NexusMapDetailPayload.CODEC.encode(buffer,payload);assertEquals(payload,NexusMapDetailPayload.CODEC.decode(buffer)); }
        finally { buffer.release(); }
        assertEquals(4,payload.geometries().getFirst().scale());
    }
    @Test void illegalExtentOrMismatchedNativeRootCannotEnterClientState() {
        var root=new NexusMapDetailPayload.Layer(1,0,0,3,"minecraft:overworld",false);
        assertThrows(IllegalArgumentException.class,()->new NexusMapDetailPayload(1,List.of(),List.of(root),5));
        assertThrows(IllegalArgumentException.class,()->new NexusMapDetailPayload(1,List.of(),List.of(),21));
        assertThrows(IllegalArgumentException.class,()->new NexusMapDetailPayload(1,List.of(),List.of(),5));
    }
}
