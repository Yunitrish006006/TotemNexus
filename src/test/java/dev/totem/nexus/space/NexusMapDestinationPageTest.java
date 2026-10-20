package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapDestinationPageTest {
    private static final UUID VIEWER = new UUID(1, 1);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void walksEveryDestinationWithoutDuplicatesAndQuotesOnlyTheCurrentPage() {
        var units = new ArrayList<>(units(385));
        var seen = new HashSet<UUID>();
        UUID cursor = null;
        for (int expected : new int[]{128, 128, 128, 1}) {
            Collections.shuffle(units, new Random(expected + seen.size()));
            var quotes = new AtomicInteger();
            var page = page(VIEWER, units, cursor, ignored -> true, quotes);
            assertEquals(expected, page.payload().entries().size());
            assertEquals(expected, quotes.get());
            page.payload().entries().forEach(entry -> assertTrue(seen.add(entry.id())));
            cursor = page.nextAfter();
        }
        assertNull(cursor);
        assertEquals(385, seen.size());
    }

    @Test void exactBoundaryEmptyAndDuplicateCandidatesDoNotInventContinuation() {
        var units = new ArrayList<>(units(128));
        units.addAll(units(128));
        var page = page(VIEWER, units, null, ignored -> true, new AtomicInteger());
        assertEquals(128, page.payload().entries().size());
        assertNull(page.nextAfter());
        var empty = page(VIEWER, List.of(), null, ignored -> true, new AtomicInteger());
        assertTrue(empty.payload().entries().isEmpty());
        assertNull(empty.nextAfter());
    }

    @Test void hiddenOrUncoveredLookaheadDoesNotLeakThroughCursorOrReceiveAQuote() {
        var units = new ArrayList<>(units(128));
        units.add(unit(129, new UUID(2, 2), SpaceUnitStatus.ACTIVE));
        units.add(unit(130, VIEWER, SpaceUnitStatus.DISABLED));
        units.add(unit(131, VIEWER, SpaceUnitStatus.ACTIVE));
        var quotes = new AtomicInteger();
        var page = page(VIEWER, units, null, unit -> !unit.id().equals(id(131)), quotes);
        assertEquals(128, page.payload().entries().size());
        assertNull(page.nextAfter());
        assertEquals(128, quotes.get());
    }

    @Test void deletedCursorAndRevokedAccessDoNotSkipRemainingAuthorizedTargets() {
        var units = new ArrayList<>(units(260));
        var first = page(VIEWER, units, null, ignored -> true, new AtomicInteger());
        assertEquals(id(128), first.nextAfter());
        units.removeIf(unit -> unit.id().equals(id(128)));
        var second = page(VIEWER, units, first.nextAfter(), unit -> !unit.id().equals(id(129)), new AtomicInteger());
        assertEquals(id(130), second.payload().entries().getFirst().id());
        assertEquals(id(257), second.nextAfter());
        // A cursor copied to a different holder grants no access to private destinations.
        var transferred = page(new UUID(3, 3), units, first.nextAfter(), ignored -> true, new AtomicInteger());
        assertTrue(transferred.payload().entries().isEmpty());
        assertNull(transferred.nextAfter());
    }

    @Test void arbitraryCursorCannotAuthorizeAnyDestination() {
        var quotes = new AtomicInteger();
        var page = page(VIEWER, units(300), id(150), ignored -> false, quotes);
        assertTrue(page.payload().entries().isEmpty());
        assertNull(page.nextAfter());
        assertEquals(0, quotes.get());
    }

    @Test void uuidCursorUsesTheSameOrderingAcrossBothSignedHalves() {
        var units = new ArrayList<NexusSpaceUnitRecord>();
        for (int i = 1; i <= 130; i++) {
            units.add(unit(new UUID(Long.MIN_VALUE, i), VIEWER, SpaceUnitStatus.ACTIVE));
            units.add(unit(new UUID(Long.MAX_VALUE, i), VIEWER, SpaceUnitStatus.ACTIVE));
        }
        Collections.shuffle(units, new Random(1));
        var first = page(VIEWER, units, null, ignored -> true, new AtomicInteger());
        assertEquals(new UUID(Long.MIN_VALUE, 128), first.nextAfter());
        var second = page(VIEWER, units, first.nextAfter(), ignored -> true, new AtomicInteger());
        assertEquals(new UUID(Long.MIN_VALUE, 129), second.payload().entries().getFirst().id());
        assertEquals(new UUID(Long.MAX_VALUE, 126), second.nextAfter());
        var last = page(VIEWER, units, second.nextAfter(), ignored -> true, new AtomicInteger());
        assertEquals(4, last.payload().entries().size());
        assertNull(last.nextAfter());
    }

    private static NexusMapPayloadFactory.DestinationPage page(UUID viewer, List<NexusSpaceUnitRecord> units,
            UUID after, Predicate<NexusSpaceUnitRecord> access, AtomicInteger quotes) {
        return NexusMapPayloadFactory.buildPage(viewer, unit(0, VIEWER, SpaceUnitStatus.ACTIVE),
                TeleportInterfaceType.FILLED_MAP, new net.minecraft.world.level.saveddata.maps.MapId(1), units, new NexusSpaceDiscoverySavedData(),
                ignored -> false, ignored -> {
                    quotes.incrementAndGet();
                    return NexusMapQuote.unavailable(TeleportInterfaceType.FILLED_MAP, "pending_authority");
                }, access, after);
    }

    private static List<NexusSpaceUnitRecord> units(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> unit(i, VIEWER, SpaceUnitStatus.ACTIVE)).toList();
    }

    private static UUID id(int index) { return new UUID(0, index); }

    private static NexusSpaceUnitRecord unit(int index, UUID owner, SpaceUnitStatus status) {
        return unit(id(index), owner, status);
    }

    private static NexusSpaceUnitRecord unit(UUID id, UUID owner, SpaceUnitStatus status) {
        return new NexusSpaceUnitRecord(id, SpaceUnitType.LODESTONE, Level.OVERWORLD,
                BlockPos.ZERO, owner, "Unit", SpaceUnitVisibility.PRIVATE, status,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1L, 1L);
    }
}
