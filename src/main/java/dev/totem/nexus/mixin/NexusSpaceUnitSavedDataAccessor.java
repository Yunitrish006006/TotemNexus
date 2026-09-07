package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusSpaceUnitSavedData;
import dev.totem.nexus.space.SpaceStructureSnapshot;
import dev.totem.nexus.space.NexusSpaceUnitRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.UUID;

@Mixin(NexusSpaceUnitSavedData.class)
public interface NexusSpaceUnitSavedDataAccessor {
    @Accessor("unitsById")
    Map<UUID, NexusSpaceUnitRecord> totem$getUnitsById();

    @Invoker("scanStructure")
    static SpaceStructureSnapshot totem$invokeScanStructure(ServerLevel level, BlockPos lodestonePos) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
