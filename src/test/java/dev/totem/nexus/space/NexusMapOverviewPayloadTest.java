package dev.totem.nexus.space;
import dev.totem.nexus.network.*;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NexusMapOverviewPayloadTest {
 @Test void exactIdentityBoundedPaletteAndCopiesRoundTrip(){
  var r=new RequestMapOverviewPayload(UUID.randomUUID(),42,7);byte[] pixels=new byte[16384];pixels[0]=4;
  var p=new MapOverviewPayload(r.requestId(),42,7,0,0,5,"minecraft:overworld",false,pixels);pixels[0]=8;
  var b=new FriendlyByteBuf(Unpooled.buffer());try{
   RequestMapOverviewPayload.CODEC.encode(b,r);assertEquals(r,RequestMapOverviewPayload.CODEC.decode(b));
   MapOverviewPayload.CODEC.encode(b,p);assertEquals(16438,b.readableBytes());
   var q=MapOverviewPayload.CODEC.decode(b);assertTrue(q.matches(r));assertEquals(4,q.colors()[0]);
   var copy=q.colors();copy[0]=9;assertEquals(4,q.colors()[0]);
   assertFalse(q.matches(new RequestMapOverviewPayload(UUID.randomUUID(),42,7)));
   assertFalse(q.matches(new RequestMapOverviewPayload(r.requestId(),42,-1)));
   assertFalse(q.matches(new RequestMapOverviewPayload(r.requestId(),43,7)));
  }finally{b.release();}
 }
 @Test void malformedAndTruncatedPacketsReject(){
  var id=UUID.randomUUID();assertThrows(IllegalArgumentException.class,()->new RequestMapOverviewPayload(id,-1,-1));
  assertThrows(IllegalArgumentException.class,()->new MapOverviewPayload(id,1,-1,0,0,21,"minecraft:overworld",false,new byte[16384]));
  assertThrows(IllegalArgumentException.class,()->new MapOverviewPayload(id,1,-1,0,0,5,"minecraft:overworld",false,new byte[16385]));
  var b=new FriendlyByteBuf(Unpooled.buffer());try{assertThrows(IndexOutOfBoundsException.class,()->MapOverviewPayload.CODEC.decode(b));}finally{b.release();}
 }
}
