package dev.totem.nexus.space;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Converts server-owned Space Unit read models and server-calculated quotes to
 * the canonical {@code totem:nexus/space_unit_map} wire shape.
 *
 * <p>It deliberately has no networking dependency. Receiver activation and
 * authoritative map resend remain a later cutover step.
 */
public final class NexusMapPayloadFactory {
    private NexusMapPayloadFactory() { }

    /** Server-internal page; the cursor is an ordering hint, never authorization proof. */
    record DestinationPage(SpaceUnitMapPayload payload, UUID nextAfter) { }

    /**
     * Selects at most 128 currently authorized destinations after a stable UUID cursor.
     * Re-evaluates access on every request and only calculates quotes for the returned page.
     * Retains at most one page plus one lookahead record; no persistent viewer cache.
     * New destinations before the cursor appear on refresh from the first page.
     */
    static DestinationPage buildPage(UUID viewer, NexusSpaceUnitRecord source,
            TeleportInterfaceType interfaceType, MapId mapId,
            Iterable<NexusSpaceUnitRecord> candidates, NexusSpaceDiscoverySavedData discovery,
            java.util.function.Predicate<UUID> isFriend, Function<NexusSpaceUnitRecord, NexusMapQuote> quoteFor,
            java.util.function.Predicate<NexusSpaceUnitRecord> mapAccess, UUID after) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(interfaceType, "interfaceType");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(discovery, "discovery");
        Objects.requireNonNull(isFriend, "isFriend");
        Objects.requireNonNull(quoteFor, "quoteFor");
        Objects.requireNonNull(mapAccess, "mapAccess");
        var selected = new TreeMap<UUID, NexusSpaceUnitRecord>();
        for (NexusSpaceUnitRecord unit : candidates) {
            if (after != null && unit.id().compareTo(after) <= 0) continue;
            if (unit.status() != SpaceUnitStatus.ACTIVE
                    || !unit.canView(viewer, isFriend.test(unit.owner()))) continue;
            if (!NexusInterfacePayloadPolicy.allowsWithMapAccess(interfaceType, source.id(), unit, mapAccess)) continue;
            selected.put(unit.id(), unit);
            if (selected.size() > SpaceUnitMapPayload.MAX_ENTRIES + 1) selected.pollLastEntry();
        }
        boolean hasMore = selected.size() > SpaceUnitMapPayload.MAX_ENTRIES;
        if (hasMore) selected.pollLastEntry();
        var entries = new ArrayList<SpaceUnitMapPayload.Entry>(selected.size());
        for (var unit : selected.values()) {
            entries.add(entry(viewer, unit, discovery, isFriend.test(unit.owner()),
                    Objects.requireNonNull(quoteFor.apply(unit), "quoteFor returned null")));
        }
        var payload = new SpaceUnitMapPayload(source.id(), source.type().id(), source.name(),
                source.dimension().identifier().toString(), source.pos().getX(), source.pos().getY(),
                source.pos().getZ(), interfaceType, mapId == null ? SpaceUnitMapPayload.NO_MAP_ID : mapId.id(),
                entries, SpaceUnitMapPayload.MaterialSummary.from(source.structure()));
        return new DestinationPage(payload, hasMore ? selected.lastKey() : null);
    }

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
        return build(viewer, source, interfaceType, mapId, mapData, visibleUnits, onlineFriends, discovery, friends,
                unitQuoteFor, friendQuoteFor, unit -> FilledMapCoverage.isDrawn(mapData, unit.dimension(), unit.pos())
                        || discovery.hasDiscovered(viewer, unit.id()));
    }

    static SpaceUnitMapPayload build(UUID viewer, NexusSpaceUnitRecord source, TeleportInterfaceType interfaceType,
            MapId mapId, MapItemSavedData mapData, List<NexusSpaceUnitRecord> visibleUnits,
            List<NexusOnlineFriendTarget> onlineFriends, NexusSpaceDiscoverySavedData discovery,
            NexusFriendSavedData friends, Function<NexusSpaceUnitRecord, NexusMapQuote> unitQuoteFor,
            Function<NexusOnlineFriendTarget, NexusMapQuote> friendQuoteFor,
            java.util.function.Predicate<NexusSpaceUnitRecord> mapAccess) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(source, "source");
        Objects.requireNonNull(interfaceType, "interfaceType"); Objects.requireNonNull(visibleUnits, "visibleUnits"); Objects.requireNonNull(onlineFriends, "onlineFriends");
        Objects.requireNonNull(discovery, "discovery"); Objects.requireNonNull(friends, "friends");
        Objects.requireNonNull(unitQuoteFor, "unitQuoteFor"); Objects.requireNonNull(friendQuoteFor, "friendQuoteFor");
        List<NexusSpaceUnitRecord> payloadUnits = NexusInterfacePayloadPolicy.selectWithMapAccess(
                interfaceType, source.id(), visibleUnits, mapAccess);
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
        return entry(viewer, unit, discovery, !unit.owner().equals(viewer) && friends.areFriends(viewer, unit.owner()), quote);
    }

    private static SpaceUnitMapPayload.Entry entry(UUID viewer, NexusSpaceUnitRecord unit,
            NexusSpaceDiscoverySavedData discovery, boolean friend, NexusMapQuote quote) {
        boolean owned = unit.owner().equals(viewer);
        return new SpaceUnitMapPayload.Entry(unit.id(), unit.type().id(), unit.name(), unit.visibility().id(),
                !owned && friend, unit.dimension().identifier().toString(), unit.pos().getX(), unit.pos().getY(), unit.pos().getZ(),
                quote.resonance(), quote.tier(), quote.distanceBlocks(), quote.baseFoodCost(), quote.finalFoodCost(), quote.saturationCost(), quote.hungerCost(),
                quote.foodPointsNeeded(), quote.safeFoodPointsAvailable(), quote.amethystCost(), quote.amethystAvailable(), quote.baseAmethystCost(),
                quote.sourceCatalysts(), quote.targetCatalysts(), quote.catalystDiscount(), quote.basePrepareTicks(), quote.prepareTicks(),
                quote.baseMaxHorizontalDeviation(), quote.maxHorizontalDeviation(), quote.damageChancePercent(), quote.baseStructureWearChancePercent(),
                quote.structureWearChancePercent(), quote.interfaceBonusActive(), quote.interfaceBonusMessageKey(), discovery.isFavorite(viewer, unit.id()),
                unit.canManage(viewer), owned, unit.administrators().size(), unit.allowedPlayers().size(), quote.canTeleport(), quote.blockedReason())
                .withMaterial(SpaceUnitMapPayload.MaterialSummary.from(unit.structure()));
    }
}
