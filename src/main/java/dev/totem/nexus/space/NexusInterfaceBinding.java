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

/**
 * Item identity for a Nexus interface. A binding names server-owned Space Unit
 * data; it never stores or grants a player role.
 */
public final class NexusInterfaceBinding {
    public static final String UNIT_ID_KEY = "deadrecall_space_unit_id";
    public static final String DATA_VERSION_KEY = "deadrecall_space_unit_data_version";
    private static final String LEGACY_UNIT_ID_KEY = "space_unit_id";
    private static final String LEGACY_DATA_VERSION_KEY = "space_unit_data_version";

    private NexusInterfaceBinding() { }

    public static UUID read(ItemStack stack) {
        Binding binding = readBinding(stack);
        return binding == null ? null : binding.unitId();
    }

    public static int readDataVersion(ItemStack stack) {
        Binding binding = readBinding(stack);
        return binding == null ? -1 : binding.dataVersion();
    }

    public static boolean write(ItemStack stack, ServerLevel level, net.minecraft.core.BlockPos pos, UUID unitId) {
        if (!isSupportedInterface(stack) || level == null || pos == null || unitId == null) return false;
        if (stack.is(Items.COMPASS)) {
            stack.set(DataComponents.LODESTONE_TRACKER,
                    new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos.immutable())), true));
        }
        return writeIdentity(stack, unitId);
    }

    static boolean writeIdentity(ItemStack stack, UUID unitId) {
        if (!isSupportedInterface(stack) || unitId == null) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.store(UNIT_ID_KEY, UUIDUtil.CODEC, unitId);
        tag.putInt(DATA_VERSION_KEY, NexusSpaceUnitSavedData.DATA_VERSION);
        // Keep the extracted pre-cutover reader compatible with existing items.
        if (stack.is(Items.COMPASS)) {
            tag.store(LEGACY_UNIT_ID_KEY, UUIDUtil.CODEC, unitId);
            tag.putInt(LEGACY_DATA_VERSION_KEY, NexusSpaceUnitSavedData.DATA_VERSION);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }

    public static boolean isSupportedInterface(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS) || stack.is(Items.BOOK)) return true;
        return stack.is(Items.FILLED_MAP) && stack.get(DataComponents.MAP_ID) != null;
    }

    private static Binding readBinding(ItemStack stack) {
        if (!isSupportedInterface(stack)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Binding canonical = readPair(tag, UNIT_ID_KEY, DATA_VERSION_KEY);
        if (canonical != null) return canonical;
        return stack.is(Items.COMPASS) ? readPair(tag, LEGACY_UNIT_ID_KEY, LEGACY_DATA_VERSION_KEY) : null;
    }

    private static Binding readPair(CompoundTag tag, String unitIdKey, String versionKey) {
        UUID unitId = tag.read(unitIdKey, UUIDUtil.CODEC).orElse(null);
        int dataVersion = tag.contains(versionKey) ? tag.getIntOr(versionKey, -1) : -1;
        if (unitId == null || dataVersion <= 0 || dataVersion > NexusSpaceUnitSavedData.DATA_VERSION) return null;
        return new Binding(unitId, dataVersion);
    }

    private record Binding(UUID unitId, int dataVersion) { }
}
