package dev.totem.nexus.space;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Proves filled maps no longer participate in lodestone registration or binding. */
public final class NexusMapActivationRemovalGameTest {
    @GameTest(maxTicks = 20)
    public void ordinaryFilledMapPassesLodestoneUseWithoutMutation(GameTestHelper helper) {
        verifyRejectedInput(helper, false, 1);
    }

    @GameTest(maxTicks = 20)
    public void legacyClaimedFilledMapStackPassesWithoutConsumption(GameTestHelper helper) {
        verifyRejectedInput(helper, true, 3);
    }

    private static void verifyRejectedInput(GameTestHelper helper, boolean legacyClaim, int count) {
        ServerLevel level = helper.getLevel();
        BlockPos lodestone = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(lodestone, Blocks.LODESTONE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(lodestone.above()));

        MapId mapId = level.getFreeMapId();
        MapItemSavedData nativeData = MapItemSavedData.createFresh(
                lodestone.getX() + 320, lodestone.getZ() - 192,
                (byte) 2, false, false, level.dimension());
        nativeData.colors[17] = 42;
        level.setMapData(mapId, nativeData);
        ItemStack held = new ItemStack(Items.FILLED_MAP, count);
        held.set(DataComponents.MAP_ID, mapId);
        if (legacyClaim) {
            NexusInterfaceBinding.writeIdentity(held, UUID.randomUUID());
        }
        ItemStack before = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        try {
            var units = level.getServer().overworld().getDataStorage()
                    .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
            InteractionResult result = UseBlockCallback.EVENT.invoker().interact(
                    player, level, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(lodestone), Direction.UP, lodestone, false));
            if (TeleportInterfaceItemResolver.resolveRegistrationInput(
                    player, InteractionHand.MAIN_HAND).isPresent()
                    || result != InteractionResult.PASS
                    || units.getLodestone(level.dimension(), lodestone).isPresent()
                    || !ItemStack.isSameItemSameComponents(before, player.getMainHandItem())
                    || player.getMainHandItem().getCount() != count
                    || level.getMapData(mapId) != nativeData
                    || nativeData.colors[17] != 42) {
                helper.fail("Filled map entered lodestone activation or was consumed/mutated");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
