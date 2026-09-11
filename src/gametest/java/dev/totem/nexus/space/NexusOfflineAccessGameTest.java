package dev.totem.nexus.space;

import dev.totem.nexus.network.RequestAccessPlayersPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class NexusOfflineAccessGameTest {
    @GameTest(maxTicks = 60)
    public void offlineRolesUseUuidAndPreserveLiveAuthority(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer();
        var owner = h.makeMockServerPlayerInLevel(); var stranger = h.makeMockServerPlayerInLevel();
        UUID offlineId = UUID.randomUUID();
        String offlineName = "Offline" + offlineId.toString().substring(0, 8);
        dev.totem.core.player.TotemPlayerDirectoryData.get(server).remember(offlineId,offlineName);
        BlockPos pos = h.absolutePos(new BlockPos(3,2,3));
        level.setBlockAndUpdate(pos,Blocks.LODESTONE.defaultBlockState());
        owner.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        stranger.setPos(owner.position());
        UUID unitId = UUID.randomUUID(); var units = server.overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        units.put(new NexusSpaceUnitRecord(unitId,SpaceUnitType.LODESTONE,level.dimension(),pos,owner.getUUID(),"Access Test",
                SpaceUnitVisibility.PRIVATE,SpaceUnitStatus.ACTIVE,Set.of(),Set.of(),SpaceStructureSnapshot.EMPTY,
                level.getGameTime(),level.getGameTime()));
        NexusSpaceDiscoverySavedData.loadCanonical(server.overworld().getDataStorage()).markDiscovered(owner.getUUID(), unitId);
        var book = new ItemStack(Items.BOOK); NexusInterfaceBinding.writeIdentity(book,unitId);
        owner.setItemInHand(InteractionHand.MAIN_HAND,book);
        NexusSpaceUnitAuthority.establishInterfaceContext(owner,InteractionHand.MAIN_HAND,"lodestone",unitId).orElseThrow();
        try {
            var query = new RequestAccessPlayersPayload("lodestone",unitId,unitId,"administrator",offlineName.toLowerCase(Locale.ROOT),0,1);
            var page = NexusSpaceUnitAuthority.accessPlayers(owner,query).orElseThrow();
            require(h,page.players().size()==1 && page.players().getFirst().id().equals(offlineId)
                    && !page.players().getFirst().online(),"Offline player missing from authorized page: " + page.players());
            for (String role : List.of("administrator","allowed")) {
                NexusSpaceUnitAuthority.setLodestoneAccess(owner,"lodestone",unitId,unitId,role,offlineId.toString(),true);
                var record = units.get(unitId).orElseThrow();
                require(h,(role.equals("administrator")?record.administrators():record.allowedPlayers()).contains(offlineId),"Offline grant failed");
                NexusSpaceUnitAuthority.setLodestoneAccess(owner,"lodestone",unitId,unitId,role,offlineName.toLowerCase(Locale.ROOT),false);
                record = units.get(unitId).orElseThrow();
                require(h,!(role.equals("administrator")?record.administrators():record.allowedPlayers()).contains(offlineId),"Offline revoke failed");
            }
            stranger.setItemInHand(InteractionHand.MAIN_HAND,book.copy());
            require(h,NexusSpaceUnitAuthority.accessPlayers(stranger,query).isEmpty(),"Copied book leaked player directory");
            NexusSpaceUnitAuthority.setLodestoneAccess(stranger,"lodestone",unitId,unitId,"administrator",offlineId.toString(),true);
            require(h,units.get(unitId).orElseThrow().administrators().isEmpty(),"Unauthorized player granted a role");
            String pagePrefix = "Page" + unitId.toString().substring(0, 8);
            for (int i=0;i<14;i++) dev.totem.core.player.TotemPlayerDirectoryData.get(server).remember(UUID.randomUUID(),pagePrefix+i);
            var paged = NexusSpaceUnitAuthority.accessPlayers(owner,new RequestAccessPlayersPayload("lodestone",unitId,unitId,"allowed",pagePrefix,1,2)).orElseThrow();
            require(h,paged.players().size()==6 && paged.pages()==3 && paged.page()==1,"Directory pagination is not bounded");
            owner.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            require(h,NexusSpaceUnitAuthority.accessPlayers(owner,query).isEmpty(),"Missing book retained access");
        } finally {
            NexusSpaceUnitAuthority.clearInterfaceContext(owner.getUUID()); owner.discard(); stranger.discard();
        }
        h.succeed();
    }
    private static void require(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }
}
