package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusSpaceUnitSavedData;
import dev.totem.nexus.space.SpaceStructureSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NexusSpaceUnitSavedData.class)
public abstract class NexusSpaceUnitCatalystMixin {
    private static final TagKey<Block> DEADRECALL_AMETHYST_CATALYSTS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath("deadrecall", "space_unit_amethyst_catalysts")
    );

    @Inject(method = "scanStructure", at = @At("RETURN"), cancellable = true)
    private static void deadrecall$countAmethystCatalysts(
            ServerLevel level,
            BlockPos lodestonePos,
            CallbackInfoReturnable<SpaceStructureSnapshot> cir
    ) {
        SpaceStructureSnapshot snapshot = cir.getReturnValue();
        int catalystBlocks = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (level.getBlockState(lodestonePos.offset(dx, dy, dz)).is(DEADRECALL_AMETHYST_CATALYSTS)) {
                        catalystBlocks++;
                    }
                }
            }
        }

        cir.setReturnValue(new SpaceStructureSnapshot(
                snapshot.completeness(),
                snapshot.symmetry(),
                snapshot.resonance(),
                snapshot.interference(),
                snapshot.environmentStability(),
                snapshot.wear(),
                snapshot.tier(),
                catalystBlocks
        ));
    }
}
