package com.adaptor.deadrecall.space;

import com.adaptor.deadrecall.mixin.DeadRecallSpaceUnitSavedDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.util.UUID;

public final class SpaceUnitStructureRefresh {
    private SpaceUnitStructureRefresh() {
    }

    public static Optional<SpaceUnitRecord> refresh(MinecraftServer server, UUID unitId) {
        if (server == null || unitId == null) {
            return Optional.empty();
        }

        DeadRecallSpaceUnitSavedData data = server.overworld()
                .getDataStorage()
                .computeIfAbsent(DeadRecallSpaceUnitSavedData.TYPE);
        Optional<SpaceUnitRecord> existing = data.get(unitId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        SpaceUnitRecord record = existing.get();
        if (!record.isLodestoneAnchor() || record.status() != SpaceUnitStatus.ACTIVE) {
            return existing;
        }

        ServerLevel level = server.getLevel(record.dimension());
        if (level == null || !level.getBlockState(record.pos()).is(Blocks.LODESTONE)) {
            return existing;
        }

        SpaceStructureSnapshot snapshot = DeadRecallSpaceUnitSavedDataAccessor
                .deadrecall$invokeScanStructure(level, record.pos());
        if (snapshot.equals(record.structure())) {
            return existing;
        }

        SpaceUnitRecord updated = record.withStructure(snapshot, level.getGameTime());
        DeadRecallSpaceUnitSavedDataAccessor accessor =
                (DeadRecallSpaceUnitSavedDataAccessor) (Object) data;
        accessor.deadrecall$getUnitsById().put(updated.id(), updated);
        data.setDirty();
        return Optional.of(updated);
    }
}
