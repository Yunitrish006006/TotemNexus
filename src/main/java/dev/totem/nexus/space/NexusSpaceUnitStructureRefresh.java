package dev.totem.nexus.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.util.UUID;

public final class NexusSpaceUnitStructureRefresh {
    private NexusSpaceUnitStructureRefresh() {
    }

    public static Optional<NexusSpaceUnitRecord> refresh(MinecraftServer server, UUID unitId) {
        if (server == null || unitId == null) {
            return Optional.empty();
        }

        NexusSpaceUnitSavedData data = server.overworld()
                .getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        Optional<NexusSpaceUnitRecord> existing = data.get(unitId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        NexusSpaceUnitRecord record = existing.get();
        if (!record.isLodestoneAnchor() || record.status() != SpaceUnitStatus.ACTIVE) {
            return existing;
        }

        ServerLevel level = server.getLevel(record.dimension());
        if (level == null || !level.getBlockState(record.pos()).is(Blocks.LODESTONE)) {
            return existing;
        }

        return data.rescanLodestone(level, unitId);
    }
}
