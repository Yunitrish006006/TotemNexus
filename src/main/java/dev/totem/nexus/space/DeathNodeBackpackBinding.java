package dev.totem.nexus.space;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Stable backpack binding field shared with legacy death-node recovery data. */
public final class DeathNodeBackpackBinding {
    public static final String TAG_DEATH_NODE_ID = "deadrecall_space_death_node_id";
    private DeathNodeBackpackBinding() { }
    public static void write(ItemStack backpack, UUID nodeId) {
        if (backpack.isEmpty() || nodeId == null) return;
        CompoundTag tag = backpack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.store(TAG_DEATH_NODE_ID, UUIDUtil.CODEC, nodeId);
        backpack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static UUID read(ItemStack backpack) {
        if (backpack.isEmpty()) return null;
        CompoundTag tag = backpack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.read(TAG_DEATH_NODE_ID, UUIDUtil.CODEC).orElse(null);
    }
}
