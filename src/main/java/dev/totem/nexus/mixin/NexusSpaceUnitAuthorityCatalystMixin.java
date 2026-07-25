package dev.totem.nexus.mixin;

import dev.totem.nexus.space.AmethystCatalystDiscount;
import dev.totem.nexus.space.NexusSpaceUnitSavedData;
import dev.totem.nexus.space.NexusSpaceUnitAuthority;
import dev.totem.nexus.space.NexusSpaceUnitRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NexusSpaceUnitAuthority.class)
public abstract class NexusSpaceUnitAuthorityCatalystMixin {
    @Redirect(
            method = "calculateTeleportQuote",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I")
    )
    private static int deadrecall$applyAmethystCatalystDiscount(
            int minimumCost,
            int calculatedCost,
            ServerPlayer player,
            @Coerce Object source,
            @Coerce Object target
    ) {
        int baseCost = Math.max(minimumCost, calculatedCost);
        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData units = server.overworld()
                .getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);

        NexusSpaceUnitMapSourceAccessor sourceAccessor = (NexusSpaceUnitMapSourceAccessor) source;
        NexusSpaceUnitTeleportTargetAccessor targetAccessor = (NexusSpaceUnitTeleportTargetAccessor) target;
        boolean sourceLodestone = NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE.equals(sourceAccessor.deadrecall$getType());
        boolean targetLodestone = targetAccessor.deadrecall$isLodestoneAnchor();

        return AmethystCatalystDiscount.quoteForEndpoints(
                baseCost,
                sourceLodestone,
                deadrecall$quoteCatalystBlocks(units, sourceAccessor.deadrecall$getId()),
                targetLodestone,
                deadrecall$quoteCatalystBlocks(units, targetAccessor.deadrecall$getId())
        ).finalCost();
    }

    @Unique
    private static int deadrecall$quoteCatalystBlocks(
            NexusSpaceUnitSavedData units,
            java.util.UUID unitId
    ) {
        return units.get(unitId)
                .map(NexusSpaceUnitRecord::structure)
                .map(snapshot -> snapshot.amethystCatalystBlocks())
                .orElse(0);
    }
}
