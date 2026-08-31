package dev.totem.nexus.mixin;

import dev.totem.nexus.network.SpaceUnitMapPayload;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Adds catalyst-specific quote details after the authoritative map payload has been built. */
@Mixin(NexusSpaceUnitAuthority.class)
public abstract class NexusSpaceUnitMapCatalystBreakdownMixin {
    @Unique
    private static final int BASE_CROSS_DIMENSION_COST = 2;

    @Inject(method = "buildMapPayload", at = @At("RETURN"), cancellable = true)
    private static void deadrecall$appendCatalystBreakdown(
            ServerPlayer player,
            @Coerce Object source,
            List<NexusSpaceUnitRecord> visibleUnits,
            CallbackInfoReturnable<SpaceUnitMapPayload> cir
    ) {
        SpaceUnitMapPayload payload = cir.getReturnValue();
        if (payload == null || payload.entries().isEmpty()) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData units = server.overworld()
                .getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        boolean sourceLodestone = NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE.equals(payload.sourceType());
        int sourceCatalysts = deadrecall$payloadCatalystBlocks(units, payload.sourceUnitId());

        List<SpaceUnitMapPayload.Entry> enriched = new ArrayList<>(payload.entries().size());
        for (SpaceUnitMapPayload.Entry entry : payload.entries()) {
            boolean crossDimension = !payload.sourceDimension().equals(entry.dimension());
            boolean targetLodestone = NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE.equals(entry.type());
            int baseCost = crossDimension
                    ? Math.max(BASE_CROSS_DIMENSION_COST,
                    BASE_CROSS_DIMENSION_COST + (int) Math.ceil((1.0D - entry.resonance()) * 4.0D))
                    : 0;
            AmethystCatalystDiscount.Quote quote = AmethystCatalystDiscount.quoteForEndpoints(
                    baseCost,
                    sourceLodestone,
                    sourceCatalysts,
                    targetLodestone,
                    deadrecall$payloadCatalystBlocks(units, entry.id())
            );

            enriched.add(new SpaceUnitMapPayload.Entry(
                    entry.id(),
                    entry.type(),
                    entry.name(),
                    entry.visibility(),
                    entry.friendShared(),
                    entry.dimension(),
                    entry.x(),
                    entry.y(),
                    entry.z(),
                    entry.resonance(),
                    entry.tier(),
                    entry.distanceBlocks(),
                    entry.baseFoodCost(),
                    entry.finalFoodCost(),
                    entry.saturationCost(),
                    entry.hungerCost(),
                    entry.foodPointsNeeded(),
                    entry.safeFoodPointsAvailable(),
                    entry.amethystCost(),
                    entry.amethystAvailable(),
                    quote.baseCost(),
                    quote.sourceCatalysts(),
                    quote.targetCatalysts(),
                    quote.appliedDiscount(),
                    entry.basePrepareTicks(),
                    entry.prepareTicks(),
                    entry.baseMaxHorizontalDeviation(),
                    entry.maxHorizontalDeviation(),
                    entry.damageChancePercent(),
                    entry.baseStructureWearChancePercent(),
                    entry.structureWearChancePercent(),
                    entry.interfaceBonusActive(),
                    entry.interfaceBonusMessageKey(),
                    entry.favorite(),
                    entry.manageable(),
                    entry.owned(),
                    entry.administratorCount(),
                    entry.allowedPlayerCount(),
                    entry.canTeleport(),
                    entry.blockedReason()
            ));
        }

        cir.setReturnValue(new SpaceUnitMapPayload(
                payload.sourceUnitId(),
                payload.sourceType(),
                payload.sourceName(),
                payload.sourceDimension(),
                payload.sourceX(),
                payload.sourceY(),
                payload.sourceZ(),
                payload.interfaceType(),
                payload.mapId(),
                List.copyOf(enriched)
        ));
    }

    @Unique
    private static int deadrecall$payloadCatalystBlocks(
            NexusSpaceUnitSavedData units,
            java.util.UUID unitId
    ) {
        return units.get(unitId)
                .map(NexusSpaceUnitRecord::structure)
                .map(snapshot -> snapshot.amethystCatalystBlocks())
                .orElse(0);
    }
}
