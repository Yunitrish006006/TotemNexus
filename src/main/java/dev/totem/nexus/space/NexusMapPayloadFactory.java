package dev.totem.nexus.space;

import dev.totem.nexus.network.SpaceUnitMapPayload;

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
                                             List<NexusSpaceUnitRecord> visibleUnits, NexusSpaceDiscoverySavedData discovery,
                                             NexusFriendSavedData friends, Function<NexusSpaceUnitRecord, NexusMapQuote> quoteFor) {
        return build(viewer, source, interfaceType, visibleUnits, List.of(), discovery, friends, quoteFor, ignored -> NexusMapQuote.unavailable(interfaceType, "pending_authority"));
    }

    /** Includes online friends only when the same server-side relationship store authorizes them. */
    public static SpaceUnitMapPayload build(UUID viewer, NexusSpaceUnitRecord source, TeleportInterfaceType interfaceType,
                                             List<NexusSpaceUnitRecord> visibleUnits, List<NexusOnlineFriendTarget> onlineFriends,
                                             NexusSpaceDiscoverySavedData discovery, NexusFriendSavedData friends,
                                             Function<NexusSpaceUnitRecord, NexusMapQuote> unitQuoteFor,
                                             Function<NexusOnlineFriendTarget, NexusMapQuote> friendQuoteFor) {
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(source, "source");
        Objects.requireNonNull(interfaceType, "interfaceType"); Objects.requireNonNull(visibleUnits, "visibleUnits"); Objects.requireNonNull(onlineFriends, "onlineFriends");
        Objects.requireNonNull(discovery, "discovery"); Objects.requireNonNull(friends, "friends");
        Objects.requireNonNull(unitQuoteFor, "unitQuoteFor"); Objects.requireNonNull(friendQuoteFor, "friendQuoteFor");
        List<SpaceUnitMapPayload.Entry> entries = new ArrayList<>(Math.min(visibleUnits.size(), SpaceUnitMapPayload.MAX_ENTRIES));
        for (NexusSpaceUnitRecord unit : visibleUnits) {
            if (entries.size() == SpaceUnitMapPayload.MAX_ENTRIES) break;
            if (unit.status() != SpaceUnitStatus.ACTIVE || !unit.canView(viewer, friends.areFriends(viewer, unit.owner()))) continue;
            NexusMapQuote quote = Objects.requireNonNull(unitQuoteFor.apply(unit), "unitQuoteFor returned null");
            entries.add(entry(viewer, unit, discovery, friends, quote));
        }
        for (NexusOnlineFriendTarget friend : onlineFriends) {
            if (entries.size() == SpaceUnitMapPayload.MAX_ENTRIES) break;
            if (friend.playerId().equals(viewer) || !friends.areFriends(viewer, friend.playerId())) continue;
            NexusMapQuote quote = Objects.requireNonNull(friendQuoteFor.apply(friend), "friendQuoteFor returned null");
            entries.add(friendEntry(friend, quote));
        }
        return new SpaceUnitMapPayload(source.id(), source.type().id(), source.name(), source.dimension().identifier().toString(),
                source.pos().getX(), source.pos().getY(), source.pos().getZ(), interfaceType, entries,
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
    private static SpaceUnitMapPayload.Entry friendEntry(NexusOnlineFriendTarget friend, NexusMapQuote quote) {
        return new SpaceUnitMapPayload.Entry(friend.playerId(), SpaceUnitType.PLAYER.id(), friend.name(), SpaceUnitVisibility.FRIENDS.id(), true,
                friend.dimension().identifier().toString(), friend.displayPos().getX(), friend.displayPos().getY(), friend.displayPos().getZ(),
                quote.resonance(), quote.tier(), quote.distanceBlocks(), quote.baseFoodCost(), quote.finalFoodCost(), quote.saturationCost(), quote.hungerCost(),
                quote.foodPointsNeeded(), quote.safeFoodPointsAvailable(), quote.amethystCost(), quote.amethystAvailable(), quote.baseAmethystCost(),
                quote.sourceCatalysts(), quote.targetCatalysts(), quote.catalystDiscount(), quote.basePrepareTicks(), quote.prepareTicks(),
                quote.baseMaxHorizontalDeviation(), quote.maxHorizontalDeviation(), quote.damageChancePercent(), quote.baseStructureWearChancePercent(),
                quote.structureWearChancePercent(), quote.interfaceBonusActive(), quote.interfaceBonusMessageKey(), false, false, false, 0, 0,
                quote.canTeleport(), quote.blockedReason());
    }
}
