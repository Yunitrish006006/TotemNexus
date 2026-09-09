package dev.totem.nexus.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;

/** UUID lookup only. A missing loaded entity is never substituted with the original death position. */
final class NexusDeathTarget {
    private NexusDeathTarget() { }

    static ItemEntity live(MinecraftServer server, NexusSpaceUnitRecord node) {
        if (node == null || node.type() != SpaceUnitType.DEATH || node.status() != SpaceUnitStatus.ACTIVE
                || node.backpackId().isEmpty()) return null;
        var level = server.getLevel(node.dimension());
        if (level == null) return null;
        var entity = level.getEntity(node.backpackId().orElseThrow());
        return entity instanceof ItemEntity item && !item.isRemoved()
                && node.id().equals(DeathNodeBackpackBinding.read(item.getItem())) ? item : null;
    }

    static NexusSpaceUnitRecord latest(MinecraftServer server, NexusSpaceUnitRecord node) {
        var item = live(server, node);
        if (item == null) return node;
        var units = NexusSpaceUnitSavedData.loadCanonical(server.overworld().getDataStorage());
        units.moveDeathBackpack(node.id(), item.getUUID(), node.owner(), item.level().dimension(),
                item.blockPosition(), server.overworld().getGameTime());
        return units.get(node.id()).orElse(node);
    }
}
