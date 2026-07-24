package dev.totem.nexus.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Periodic server authority that disables persisted Lodestones after their block disappears. */
public final class NexusLodestoneIntegrityAuthority {
    private int ticks;
    public void tick(MinecraftServer server) {
        if (++ticks < 40) return;
        ticks = 0;
        NexusSpaceUnitSavedData units = server.overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        for (NexusSpaceUnitRecord unit : units.activeLodestones()) {
            ServerLevel level = server.getLevel(unit.dimension());
            if (level != null && level.isLoaded(unit.pos()) && !level.getBlockState(unit.pos()).is(Blocks.LODESTONE)) units.disableLodestone(unit.id(), level.getGameTime());
        }
    }
}
