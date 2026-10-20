package dev.totem.nexus.space;
import dev.totem.nexus.network.*;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.component.DataComponents;
public final class NexusMapOverviewGameTest {
 @GameTest public void heldOverviewUsesRecordedPagesAndRejectsChangedItem(GameTestHelper h){
  var level=h.getLevel();var player=h.makeMockServerPlayerInLevel();
  try{
   var anchor=h.absolutePos(new BlockPos(1,2,1));var unit=UUID.randomUUID();
   level.setBlockAndUpdate(anchor,net.minecraft.world.level.block.Blocks.LODESTONE.defaultBlockState());
   NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage()).put(
    new NexusSpaceUnitRecord(unit,SpaceUnitType.LODESTONE,level.dimension(),anchor,player.getUUID(),
     "Overview",SpaceUnitVisibility.PRIVATE,SpaceUnitStatus.ACTIVE,java.util.Set.of(),java.util.Set.of(),
     SpaceStructureSnapshot.EMPTY,level.getGameTime(),level.getGameTime()));
   var map=NexusMapLifecycleAuthority.createBoundMap(level,anchor,unit,new ItemStack(Items.MAP)).orElseThrow();
   player.setItemInHand(InteractionHand.MAIN_HAND,map);var id=map.get(DataComponents.MAP_ID);
   var base=MapItem.getSavedData(map,level);java.util.Arrays.fill(base.colors,(byte)4);
   var request=new RequestMapOverviewPayload(UUID.randomUUID(),id.id(),-1);
   var job=NexusMapOverviewAuthority.create(player,request);if(job==null)h.fail("Held bound map was rejected");
   while(!job.complete())if(job.advance(37)>37)h.fail("Overview exceeded source-pixel budget");
   if(!job.payload().matches(request)||job.payload().colors()[64*128+64]!=4)h.fail("Incorrect overview result");
   player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
   if(job.valid(player)||NexusMapOverviewAuthority.create(player,request)!=null)h.fail("Unheld map remained authorized");
   var framed=new net.minecraft.world.entity.decoration.ItemFrame(level,anchor.south(),net.minecraft.core.Direction.SOUTH);
   framed.setItem(map.copy());level.addFreshEntity(framed);player.setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+3);
   try{
    if(!player.hasLineOfSight(framed))h.fail("Frame fixture line of sight is obstructed");
    var frameRequest=new RequestMapOverviewPayload(UUID.randomUUID(),id.id(),framed.getId());
    var frameJob=NexusMapOverviewAuthority.create(player,frameRequest);if(frameJob==null)h.fail("Nearby visible frame was rejected");
    framed.setItem(ItemStack.EMPTY);
    if(frameJob.valid(player))h.fail("Empty frame remained authorized");
    var forged=map.copy();NexusInterfaceBinding.writeIdentity(forged,UUID.randomUUID());framed.setItem(forged);
    if(frameJob.valid(player))h.fail("Changed frame binding remained authorized");
    framed.setItem(map.copy());
    player.setPos(anchor.getX()+100,anchor.getY(),anchor.getZ());
    if(frameJob.valid(player)||NexusMapOverviewAuthority.create(player,frameRequest)!=null)h.fail("Remote frame authorized terrain");
   }finally{framed.discard();}
  }finally{player.discard();}h.succeed();
 }
}
