package dev.totem.nexus.space;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.Optional;
import java.util.UUID;

/** Preserves the legacy compass-to-Space-Unit binding stored on the server item. */
public final class NexusCompassBinding {
    private static final String UNIT_ID_KEY = "space_unit_id";
    private static final String DATA_VERSION_KEY = "space_unit_data_version";
    private NexusCompassBinding() { }

    public static UUID read(ItemStack stack) {
        if (stack == null || !stack.is(Items.COMPASS)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.read(UNIT_ID_KEY, UUIDUtil.CODEC).orElse(null);
    }

    public static boolean write(ItemStack stack, ServerLevel level, net.minecraft.core.BlockPos pos, UUID unitId) {
        if (stack == null || level == null || pos == null || unitId == null || !stack.is(Items.COMPASS)) return false;
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos.immutable())), true));
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.store(UNIT_ID_KEY, UUIDUtil.CODEC, unitId);
        tag.putInt(DATA_VERSION_KEY, NexusSpaceUnitSavedData.DATA_VERSION);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }
}
