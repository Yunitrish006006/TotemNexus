package dev.totem.nexus.space;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/** Preserves legacy teleport tags and validates current death-retention candidates. */
public final class NexusSoulboundTeleportItem {
    private static final String OWNER_KEY = "totem_nexus_soulbound_owner";
    private static final String TOKEN_KEY = "totem_nexus_soulbound_token";

    private NexusSoulboundTeleportItem() {
    }

    public static boolean bindAfterSuccessfulTeleport(ServerPlayer player, ItemStack stack) {
        if (player == null || TeleportInterfaceItemResolver.resolve(stack).isEmpty()) {
            return false;
        }

        UUID token = UUID.randomUUID();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.store(OWNER_KEY, UUIDUtil.CODEC, player.getUUID());
        tag.store(TOKEN_KEY, UUIDUtil.CODEC, token);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        discovery(player).setSoulboundTeleportToken(player.getUUID(), token);
        return true;
    }

    public static boolean isEligibleForDeathRetention(ServerPlayer player, ItemStack stack) {
        return player != null && TeleportInterfaceItemResolver.resolve(stack).isPresent();
    }

    /** Binary-compatible alias; eligibility no longer depends on a successful teleport token. */
    public static boolean isActiveFor(ServerPlayer player, ItemStack stack) {
        return isEligibleForDeathRetention(player, stack);
    }

    private static NexusSpaceDiscoverySavedData discovery(ServerPlayer player) {
        return player.level().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
    }
}
