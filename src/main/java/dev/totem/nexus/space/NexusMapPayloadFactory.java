package dev.totem.nexus.space;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Converts server-owned Space Unit read models and server-calculated quotes to
 * the legacy {@code deadrecall:space_unit_map} wire shape.
 *
 * <p>It deliberately has no networking dependency. Receiver activation and
 * authoritative map resend remain a later cutover step.
 */
public final class NexusMapPayloadFactory {
    private NexusMapPayloadFactory() { }

    public static SpaceUnitMapPayload build(UUID viewer, NexusSpaceUnitRecord source, TeleportInterfaceType interfaceType,
                                             MapId mapId,
                                             List<NexusSpaceUnitRecord> visibleUnits, NexusSpaceDiscoverySavedData discovery,
                                             NexusFriendSavedData friends, Function<NexusSpaceUnitRecord, NexusMapQuote> quoteFor) {
        return build(viewer, source, interfaceType, mapId, null, visibleUnits, List.of(), discovery, friends, quoteFor,
                ignored -> NexusMapQuote.unavailable(interfaceType, "pending_authority"));
    }

    public static SpaceUnitMapPayload build(UUID viewer, NexusSpaceUnitRecord source, TeleportInterfaceType interfaceType,
                                             MapId mapId, MapItemSavedData mapData,
                                             List<NexusSpaceUnitRecord> visibleUnits, NexusSpaceDiscoverySavedData discovery,
                                             NexusFriendSavedData friends, Function<NexusSpaceUnitRecord, NexusMapQuote> quoteFor) {
        return build(viewer, source, interfaceType, mapId, mapData, visibleUnits, List.of(), discovery, friends, quoteFor,
                ignored -> NexusMapQuote.unavailable(interfaceType, "pending_authority"));
    }

    /**
     * Compatibility overload. Synthetic online-player coordinates are never
     * projected by an interface; only persisted Space Units may be entries.
     */
    public static SpaceUnitMapPayload build(UUID viewer, NexusSpaceUnitRecord source, TeleportInterfaceType interfaceType,
                                             MapId mapId,
                                             List<NexusSpaceUnitRecord> visibleUnits, List<NexusOnlineFriendTarget> onlineFriends,
                                             NexusSpaceDiscoverySavedData discovery, NexusFriendSavedData friends,
                                             Function<NexusSpaceUnitRecord, NexusMapQuote> unitQuoteFor,
                                             Function<NexusOnlineFriendTarget, NexusMapQuote> friendQuoteFor) {
        return build(viewer, source, interfaceType, mapId, null, visibleUnits, onlineFriends, discovery, friends,
                unitQuoteFor, friendQuoteFor);
    }

    public static SpaceUnitMapPayload build(UUID viewer, NexusSpaceUnitRecord source, TeleportInterfaceType interfaceType,
                                             MapId mapId, MapItemSavedData mapData,
                                             List<NexusSpaceUnitRecord> visibleUnits, List<NexusOnlineFriendTarget> onlineFriends,
                                             NexusSpaceDiscoverySavedData discovery, NexusFriendSavedData friends,
                                             Function<NexusSpaceUnitRecord, NexusMapQuote> unitQuoteFor,
                                             Function<NexusOnlineFriendTarget, NexusMapQuote> friendQuoteFor) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(source, "source");
        Objects.requireNonNull(interfaceType, "interfaceType"); Objects.requireNonNull(visibleUnits, "visibleUnits"); Objects.requireNonNull(onlineFriends, "onlineFriends");
        Objects.requireNonNull(discovery, "discovery"); Objects.requireNonNull(friends, "friends");
        Objects.requireNonNull(unitQuoteFor, "unitQuoteFor"); Objects.requireNonNull(friendQuoteFor, "friendQuoteFor");
        List<NexusSpaceUnitRecord> payloadUnits = NexusInterfacePayloadPolicy.selectAuthorizedUnits(
                interfaceType, source.id(), visibleUnits, mapData);
        List<SpaceUnitMapPayload.Entry> entries = new ArrayList<>(Math.min(payloadUnits.size(), SpaceUnitMapPayload.MAX_ENTRIES));
        for (NexusSpaceUnitRecord unit : payloadUnits) {
            if (entries.size() == SpaceUnitMapPayload.MAX_ENTRIES) break;
            if (unit.status() != SpaceUnitStatus.ACTIVE || !unit.canView(viewer, friends.areFriends(viewer, unit.owner()))) continue;
            NexusMapQuote quote = Objects.requireNonNull(unitQuoteFor.apply(unit), "unitQuoteFor returned null");
            entries.add(entry(viewer, unit, discovery, friends, quote));
        }
        return new SpaceUnitMapPayload(source.id(), source.type().id(), source.name(), source.dimension().identifier().toString(),
                source.pos().getX(), source.pos().getY(), source.pos().getZ(), interfaceType,
                mapId == null ? SpaceUnitMapPayload.NO_MAP_ID : mapId.id(), entries,
                SpaceUnitMapPayload.MaterialSummary.from(source.structure()));
    }

    private static SpaceUnitMapPayload.Entry entry(UUID viewer, NexusSpaceUnitRecord unit, NexusSpaceDiscoverySavedData discovery,
                                                    NexusFriendSavedData friends, NexusMapQuote quote) {
        boolean owned = unit.owner().equals(viewer);
        return new SpaceUnitMapPayload.Entry(unit.id(), unit.type().id(), unit.name(), unit.visibility().id(),
                !owned && friends.areFriends(viewer, unit.owner()), unit.dimension().identifier().toString(), unit.pos().getX(), unit.pos().getY(), unit.pos().getZ(),
                quote.resonance(), quote.tier(), quote.distanceBlocks(), quote.baseFoodCost(), quote.finalFoodCost(), quote.saturationCost(), quote.hungerCost(),
                quote.foodPointsNeeded(), quote.safeFoodPointsAvailable(), quote.amethystCost(), quote.amethystAvailable(), quote.baseAmethystCost(),
                quote.sourceCatalysts(), quote.targetCatalysts(), quote.catalystDiscount(), quote.basePrepareTicks(), quote.prepareTicks(),
                quote.baseMaxHorizontalDeviation(), quote.maxHorizontalDeviation(), quote.damageChancePercent(), quote.baseStructureWearChancePercent(),
                quote.structureWearChancePercent(), quote.interfaceBonusActive(), quote.interfaceBonusMessageKey(), discovery.isFavorite(viewer, unit.id()),
                unit.canManage(viewer), owned, unit.administrators().size(), unit.allowedPlayers().size(), quote.canTeleport(), quote.blockedReason())
                .withMaterial(SpaceUnitMapPayload.MaterialSummary.from(unit.structure()));
    }
}
