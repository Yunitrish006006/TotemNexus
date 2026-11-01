package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

public final class NexusMapExactCreationGameTest {
    @GameTest
    public void lodestoneUseCreatesExactCenteredMapIdRecordWithoutItemIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        UUID unitId = UUID.randomUUID();
        NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage()).put(
                new NexusSpaceUnitRecord(
                        unitId, SpaceUnitType.LODESTONE, level.dimension(), anchor, player.getUUID(),
                        "Exact Center", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                        Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, level.getGameTime(), level.getGameTime()));

        ItemStack emptyMap = new ItemStack(Items.MAP);
        emptyMap.set(DataComponents.CUSTOM_NAME, Component.literal("Survey Components"));
        CompoundTag custom = new CompoundTag();
        custom.putString("unrelated", "preserved");
        custom.putString(NexusInterfaceBinding.UNIT_ID_KEY, UUID.randomUUID().toString());
        custom.putInt(NexusInterfaceBinding.DATA_VERSION_KEY, NexusSpaceUnitSavedData.DATA_VERSION);
        emptyMap.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        player.setItemInHand(InteractionHand.MAIN_HAND, emptyMap);
        var input = TeleportInterfaceItemResolver.resolveRegistrationInput(
                player, InteractionHand.MAIN_HAND).orElseThrow();

        if (!NexusSpaceUnitAuthority.bindInterface(
                player, InteractionHand.MAIN_HAND, emptyMap, input, level, anchor, unitId)) {
            helper.fail("Lodestone use did not create the exact-centered MapId map");
        }
        ItemStack result = player.getMainHandItem();
        var mapId = result.get(DataComponents.MAP_ID);
        var nativeData = mapId == null ? null : MapItem.getSavedData(mapId, level);
        var records = level.getServer().overworld().getDataStorage().get(NexusMapRecordSavedData.TYPE);
        String preserved = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getStringOr("unrelated", "");
        if (!result.is(Items.FILLED_MAP) || mapId == null || nativeData == null)
            helper.fail("Creation did not publish a native filled-map MapId");
        if (nativeData.centerX != anchor.getX() || nativeData.centerZ != anchor.getZ()
                || nativeData.scale != 0 || nativeData.locked
                || !nativeData.dimension.equals(level.dimension()))
            helper.fail("Created map did not preserve the lodestone's exact native geometry");
        if (records == null || records.resolve(mapId, nativeData).isEmpty())
            helper.fail("Created map did not publish a compatible MapId baseline record");
        if (NexusInterfaceBinding.hasIdentityMetadata(result)
                || NexusInterfaceBinding.read(result) != null)
            helper.fail("Created map retained item-level Nexus/Space Unit identity");
        if (NexusMapBindingSavedData.loadCanonical(
                level.getServer().overworld().getDataStorage()).get(mapId).isPresent())
            helper.fail("Created map retained a legacy MapId-to-Space-Unit binding");
        if (!"Survey Components".equals(result.getHoverName().getString()))
            helper.fail("Created map lost its input custom name");
        if (!"preserved".equals(preserved))
            helper.fail("Identity cleanup removed unrelated custom item data");
        player.discard();
        helper.succeed();
    }

    @GameTest
    public void missingLodestonePublishesNoMap(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
        ItemStack emptyMap = new ItemStack(Items.MAP);
        player.setItemInHand(InteractionHand.MAIN_HAND, emptyMap);
        var input = TeleportInterfaceItemResolver.resolveRegistrationInput(
                player, InteractionHand.MAIN_HAND).orElseThrow();

        if (NexusSpaceUnitAuthority.bindInterface(
                player, InteractionHand.MAIN_HAND, emptyMap, input, level, anchor, UUID.randomUUID())
                || player.getMainHandItem() != emptyMap || !emptyMap.is(Items.MAP)) {
            helper.fail("Invalid lodestone creation published or consumed a map");
        }
        player.discard();
        helper.succeed();
    }
}
