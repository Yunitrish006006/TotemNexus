package dev.totem.nexus.space;

import dev.totem.core.api.v1.death.DeathRetainedItemPolicy;
import dev.totem.core.social.TotemFriendSavedData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Exercises the stable distributed-spawn schema with a live server registry. */
public final class NexusDistributedSpawnGameTest {
    @SuppressWarnings("removal")
    @GameTest(maxTicks = 20)
    public void validInterfacesAreEligibleBeforeAndAfterSuccessfulTeleport(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack compass = new ItemStack(Items.COMPASS);
        ItemStack book = new ItemStack(Items.BOOK);
        ItemStack recoveryCompass = new ItemStack(Items.RECOVERY_COMPASS);
        ItemStack invalidFilledMap = new ItemStack(Items.FILLED_MAP);

        try {
            DeathRetainedItemPolicy policy = DeathRetainedItemPolicy.current()
                    .orElseThrow(() -> helper.assertionException(
                            "Nexus did not register its Core death-retained-item policy"));
            if (!policy.shouldRetain(player, compass)) {
                helper.fail("A valid compass required a prior successful teleport");
                return;
            }
            if (!policy.shouldRetain(player, recoveryCompass)
                    || policy.shouldRetain(player, invalidFilledMap)) {
                helper.fail("Death policy disagreed with the current interface resolver");
                return;
            }

            if (!NexusSoulboundTeleportItem.bindAfterSuccessfulTeleport(player, book)) {
                helper.fail("Nexus did not bind a valid book interface");
                return;
            }
            if (!policy.shouldRetain(player, book) || !policy.shouldRetain(player, compass)) {
                helper.fail("Successful teleport tags incorrectly changed current interface eligibility");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void safeLandingSearchAdvancesIncrementallyOnLoadedChunks(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 3, 2);
        helper.setBlock(feet.below(), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(feet, net.minecraft.world.level.block.Blocks.AIR);
        helper.setBlock(feet.above(), net.minecraft.world.level.block.Blocks.AIR);

        BlockPos expected = helper.absolutePos(feet);
        NexusSafeLanding.Search search =
                NexusSafeLanding.begin(
                        helper.getLevel(),
                        expected,
                        false,
                        48,
                        net.minecraft.util.RandomSource.create(42L),
                        true
                );
        java.util.concurrent.atomic.AtomicBoolean finished =
                new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            if (finished.get()) {
                return;
            }
            NexusSafeLanding.Progress progress = search.advance();
            if (progress.state() == NexusSafeLanding.State.SEARCHING) {
                return;
            }
            finished.set(true);
            search.close();
            if (progress.state() != NexusSafeLanding.State.FOUND
                    || !progress.landing().orElseThrow().equals(expected)) {
                helper.fail("Incremental safe-landing search did not return the loaded exact column");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void savedDataKeysPreserveLegacyWorldDataUnderCoreOwnership(GameTestHelper helper) {
        if (!NexusSpaceUnitSavedData.TYPE.id().toString().equals("deadrecall:space_units")
                || !NexusSpaceDiscoverySavedData.TYPE.id().toString().equals("deadrecall:space_discovery")
                || !TotemFriendSavedData.TYPE.id().toString().equals("deadrecall:space_friends")
                || !NexusFriendSavedData.TYPE.id().toString().equals("totem-nexus:friendship_view")
                || !NexusDistributedSpawnSavedData.TYPE.id().toString().equals("deadrecall:distributed_spawns")) {
            helper.fail("Core/Nexus friendship ownership changed an expected SavedData key");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void spawnRoundTripRetainsDimensionPositionAndTime(GameTestHelper helper) {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000013");
        NexusDistributedSpawnSavedData data = new NexusDistributedSpawnSavedData();
        BlockPos expected = helper.absolutePos(new BlockPos(2, 2, 2));
        data.put(player, helper.getLevel().dimension(), expected, 90.0F, 42L);
        if (data.get(player).filter(spawn -> spawn.dimension().equals(helper.getLevel().dimension())
                && spawn.pos().equals(expected) && spawn.createdGameTime() == 42L).isEmpty()) {
            helper.fail("Distributed spawn schema did not retain its stable fields");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void spaceUnitVisibilityRequiresDiscoveryAndHonorsFriendSharing(GameTestHelper helper) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000023");
        NexusSpaceUnitRecord unit = new NexusSpaceUnitRecord(
                unitId, SpaceUnitType.LODESTONE, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 2, 2)),
                owner, "Friend Anchor", SpaceUnitVisibility.FRIENDS, SpaceUnitStatus.ACTIVE,
                java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 1L, 1L);
        NexusSpaceUnitSavedData units = new NexusSpaceUnitSavedData();
        NexusSpaceDiscoverySavedData discovery = new NexusSpaceDiscoverySavedData();
        NexusFriendSavedData friends = helper.getLevel().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        // Friendship is TotemCore-owned and GameTest worlds retain saved data between runs.
        // Reset only this test pair so the pre-friendship visibility assertion is meaningful.
        friends.removeRelationship(owner, viewer);
        units.put(unit);
        if (!units.visibleDiscoveredUnits(viewer, discovery, friends).isEmpty()) {
            helper.fail("Undiscovered Space Unit was visible");
            return;
        }
        discovery.markDiscovered(viewer, unitId);
        if (!units.visibleDiscoveredUnits(viewer, discovery, friends).isEmpty()) {
            helper.fail("Friend-only Space Unit was visible without friendship");
            return;
        }
        try {
            friends.inviteOrAccept(owner, viewer);
            friends.inviteOrAccept(viewer, owner);
            if (!units.visibleDiscoveredUnits(viewer, discovery, friends).equals(java.util.List.of(unit))) {
                helper.fail("Friend-only discovered Space Unit was not visible after acceptance");
                return;
            }
            helper.succeed();
        } finally {
            friends.removeRelationship(owner, viewer);
        }
    }

    @GameTest(maxTicks = 20)
    public void deathNodeRollbackIsOwnerBoundButRecoveryIsIdempotent(GameTestHelper helper) {
        var owner = helper.makeMockServerPlayerInLevel();
        var other = helper.makeMockServerPlayerInLevel();
        NexusDeathBackpackNodeAdapter adapter = new NexusDeathBackpackNodeAdapter(new NexusDeathNodeAuthority());
        UUID nodeId = adapter.create(owner, helper.getLevel(), helper.absolutePos(new BlockPos(6, 2, 2)));
        NexusSpaceUnitSavedData units = helper.getLevel().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = helper.getLevel().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        if (units.get(nodeId).isEmpty() || !discovery.hasDiscovered(owner.getUUID(), nodeId)) {
            helper.fail("Created Death Node was not persisted and discovered for its owner");
            return;
        }
        adapter.rollback(other, helper.getLevel(), nodeId);
        if (units.get(nodeId).filter(unit -> unit.status() == SpaceUnitStatus.ACTIVE).isEmpty()) {
            helper.fail("Non-owner rolled back a Death Node");
            return;
        }
        if (!adapter.recover(other, nodeId)
                || units.get(nodeId).filter(unit -> unit.status() == SpaceUnitStatus.DISABLED).isEmpty()) {
            helper.fail("Recovery did not disable the active Death Node");
            return;
        }
        if (!adapter.recover(owner, nodeId) || !adapter.recover(other, UUID.randomUUID())) {
            helper.fail("Death Node recovery was not idempotent");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void completeAuthorityFacadeUsesThePreservedDeathNodeSchema(GameTestHelper helper) {
        var owner = helper.makeMockServerPlayerInLevel();
        NexusGameplayAuthority authority = new NexusGameplayAuthority();
        UUID nodeId = authority.createDeathNode(owner, helper.getLevel(), helper.absolutePos(new BlockPos(7, 2, 2)));
        net.minecraft.world.item.ItemStack backpack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST);
        authority.bindDeathNode(backpack, nodeId);
        if (!nodeId.equals(DeathNodeBackpackBinding.read(backpack))
                || !authority.disableDeathNode(owner, helper.getLevel(), nodeId)) {
            helper.fail("Complete Nexus authority did not preserve the Death Node lifecycle");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void deathNodeBackpackBindingRoundTripsWithoutReplacingCustomData(GameTestHelper helper) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE);
        net.minecraft.nbt.CompoundTag existing = new net.minecraft.nbt.CompoundTag();
        existing.putString("existing", "kept");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(existing));
        UUID nodeId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        DeathNodeBackpackBinding.write(stack, nodeId);
        if (!nodeId.equals(DeathNodeBackpackBinding.read(stack))
                || !"kept".equals(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getStringOr("existing", ""))) {
            helper.fail("Death Node binding did not preserve backpack custom data");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void friendRemovalOnlyChangesTheRequestersRelationship(GameTestHelper helper) {
        var first = helper.makeMockServerPlayerInLevel();
        var second = helper.makeMockServerPlayerInLevel();
        NexusFriendSavedData friends = helper.getLevel().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        friends.inviteOrAccept(first.getUUID(), second.getUUID());
        friends.inviteOrAccept(second.getUUID(), first.getUUID());
        if (!new NexusFriendAuthority().removeFriend(first, second.getUUID())
                || friends.areFriends(first.getUUID(), second.getUUID())) {
            helper.fail("Friend removal did not remove the requester's relationship");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void friendPayloadAuthorityRefreshesTheServerOwnedListAfterRemoval(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var friend = helper.makeMockServerPlayerInLevel();
        NexusFriendSavedData data = helper.getLevel().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        data.inviteOrAccept(player.getUUID(), friend.getUUID());
        data.inviteOrAccept(friend.getUUID(), player.getUUID());
        java.util.List<dev.totem.nexus.network.SpaceUnitFriendsPayload> sent = new java.util.ArrayList<>();
        NexusFriendPayloadAuthority authority = new NexusFriendPayloadAuthority(new NexusFriendAuthority(),
                (recipient, payload) -> { if (recipient == player) sent.add(payload); });
        if (!authority.removeFriend(player, friend.getUUID()) || sent.size() != 1 || !sent.getFirst().entries().isEmpty()
                || data.areFriends(player.getUUID(), friend.getUUID())) {
            helper.fail("Friend payload authority did not refresh the requester's server-owned list");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void mapPayloadAuthorityIncludesOnlyServerAuthorizedOnlineFriends(GameTestHelper helper) {
        var viewer = helper.makeMockServerPlayerInLevel();
        var friend = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        var storage = helper.getLevel().getServer().overworld().getDataStorage();
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        friends.inviteOrAccept(viewer.getUUID(), friend.getUUID());
        friends.inviteOrAccept(friend.getUUID(), viewer.getUUID());
        java.util.List<dev.totem.nexus.network.SpaceUnitMapPayload> sent = new java.util.ArrayList<>();
        NexusMapPayloadAuthority authority = new NexusMapPayloadAuthority(
                (recipient, payload) -> { if (recipient == viewer) sent.add(payload); },
                (recipient, target) -> NexusMapQuote.unavailable(TeleportInterfaceType.COMPASS, "pending_authority"));
        UUID sourceId = UUID.randomUUID();
        NexusSpaceUnitRecord source = new NexusSpaceUnitRecord(sourceId, SpaceUnitType.LODESTONE,
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 2, 4)), viewer.getUUID(), "Source",
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, java.util.Set.of(), java.util.Set.of(),
                SpaceStructureSnapshot.EMPTY, 0, 0);
        TeleportInterfaceContext context = new TeleportInterfaceContext(viewer.getUUID(), TeleportInterfaceType.COMPASS,
                "lodestone", sourceId, net.minecraft.world.InteractionHand.MAIN_HAND, null, 0, 1000);
        authority.sendCalculated(viewer, context, source, new NexusMapQuoteAuthority());
        if (sent.size() != 1 || sent.getFirst().entries().stream().noneMatch(entry -> entry.id().equals(friend.getUUID()))
                || sent.getFirst().entries().stream().anyMatch(entry -> entry.id().equals(stranger.getUUID()))) {
            helper.fail("Map payload did not project only server-authorized online friends");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void favoriteMutationResendsTheAuthoritativeMap(GameTestHelper helper) {
        var viewer = helper.makeMockServerPlayerInLevel();
        viewer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS));
        var storage = helper.getLevel().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = storage.computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = storage.computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        NexusSpaceUnitRecord source = new NexusSpaceUnitRecord(sourceId, SpaceUnitType.LODESTONE,
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 2, 4)), viewer.getUUID(), "Source",
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, java.util.Set.of(), java.util.Set.of(),
                SpaceStructureSnapshot.EMPTY, 0, 0);
        NexusSpaceUnitRecord target = new NexusSpaceUnitRecord(targetId, SpaceUnitType.LODESTONE,
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(5, 2, 4)), viewer.getUUID(), "Target",
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, java.util.Set.of(), java.util.Set.of(),
                SpaceStructureSnapshot.EMPTY, 0, 0);
        units.put(source); units.put(target); discovery.markDiscovered(viewer.getUUID(), sourceId); discovery.markDiscovered(viewer.getUUID(), targetId);
        java.util.List<dev.totem.nexus.network.SpaceUnitMapPayload> sent = new java.util.ArrayList<>();
        NexusMapPayloadAuthority payloads = new NexusMapPayloadAuthority((recipient, payload) -> sent.add(payload),
                (recipient, unit) -> NexusMapQuote.unavailable(TeleportInterfaceType.COMPASS, "pending_authority"));
        TeleportInterfaceSessionStore sessions = new TeleportInterfaceSessionStore();
        sessions.put(new TeleportInterfaceContext(viewer.getUUID(), TeleportInterfaceType.COMPASS, "lodestone", sourceId,
                net.minecraft.world.InteractionHand.MAIN_HAND, null, 0, 1000));
        NexusMapOpenAuthority authority = new NexusMapOpenAuthority(sessions, payloads, new NexusMapQuoteAuthority());
        if (!authority.setFavorite(viewer, "lodestone", sourceId, targetId, true) || sent.size() != 1
                || sent.getFirst().entries().stream().noneMatch(entry -> entry.id().equals(targetId) && entry.favorite())) {
            helper.fail("Favorite mutation did not resend the authoritative map");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void favoriteRequiresMatchingMapSessionAndVisibleDiscovery(GameTestHelper helper) {
        var owner = helper.makeMockServerPlayerInLevel();
        var viewer = helper.makeMockServerPlayerInLevel();
        var storage = helper.getLevel().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = storage.computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = storage.computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        UUID source = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        NexusSpaceUnitRecord target = new NexusSpaceUnitRecord(targetId, SpaceUnitType.LODESTONE,
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(8, 2, 2)), owner.getUUID(), "Public", SpaceUnitVisibility.PUBLIC,
                SpaceUnitStatus.ACTIVE, java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0);
        units.put(target); discovery.markDiscovered(viewer.getUUID(), targetId);
        TeleportInterfaceSessionStore sessions = new TeleportInterfaceSessionStore();
        viewer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS));
        sessions.put(new TeleportInterfaceContext(viewer.getUUID(), TeleportInterfaceType.COMPASS, "compass", source,
                net.minecraft.world.InteractionHand.MAIN_HAND, null, 0, 1000));
        NexusMapAuthority authority = new NexusMapAuthority(sessions);
        if (authority.setFavorite(viewer, "compass", UUID.randomUUID(), targetId, true)
                || !authority.setFavorite(viewer, "compass", source, targetId, true)
                || !discovery.isFavorite(viewer.getUUID(), targetId)) {
            helper.fail("Favorite authority did not enforce session/source/discovery checks"); return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void teleportInterfaceResolverAndSessionRequireTheServerHeldItem(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var compass = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, compass);
        if (TeleportInterfaceItemResolver.resolve(player, net.minecraft.world.InteractionHand.MAIN_HAND)
                .filter(resolved -> resolved.type() == TeleportInterfaceType.COMPASS && resolved.mapId() == null).isEmpty()) {
            helper.fail("Teleport interface resolver did not use the held compass");
            return;
        }
        UUID source = UUID.randomUUID();
        TeleportInterfaceSessionStore sessions = new TeleportInterfaceSessionStore();
        sessions.put(new TeleportInterfaceContext(player.getUUID(), TeleportInterfaceType.COMPASS, "compass", source,
                net.minecraft.world.InteractionHand.MAIN_HAND, null, 0, 1000));
        if (sessions.require(player, "compass", source, 0).isEmpty()) {
            helper.fail("Valid held interface context was rejected");
            return;
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        if (sessions.require(player, "compass", source, 0).isPresent()) {
            helper.fail("Session accepted after its server-held interface changed");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void teleportInterfaceAuthorityEstablishesContextFromTheHeldHand(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK));
        UUID source = UUID.randomUUID();
        NexusTeleportInterfaceAuthority authority = new NexusTeleportInterfaceAuthority(new TeleportInterfaceSessionStore());
        var context = authority.establish(player, net.minecraft.world.InteractionHand.OFF_HAND, "player", source);
        if (context.filter(value -> value.interfaceType() == TeleportInterfaceType.BOOK
                        && value.interactionHand() == net.minecraft.world.InteractionHand.OFF_HAND
                        && value.matchesSource("player", source)).isEmpty()
                || authority.require(player, "player", source).isEmpty()) {
            helper.fail("Teleport interface authority did not establish the server-held context");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void playerAnchorContextAlwaysUsesTheServerPlayersIdentity(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS));
        NexusTeleportInterfaceAuthority authority = new NexusTeleportInterfaceAuthority(new TeleportInterfaceSessionStore());
        if (authority.establishPlayerAnchor(player, net.minecraft.world.InteractionHand.MAIN_HAND)
                .filter(context -> context.sourceType().equals("player") && context.sourceId().equals(player.getUUID())).isEmpty()) {
            helper.fail("Player anchor context did not use the server player's identity");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void inactiveDeathNodePurgeCleansEveryDiscoveryAndFavoriteReference(GameTestHelper helper) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000051");
        UUID firstViewer = UUID.fromString("00000000-0000-0000-0000-000000000052");
        UUID secondViewer = UUID.fromString("00000000-0000-0000-0000-000000000053");
        UUID nodeId = UUID.fromString("00000000-0000-0000-0000-000000000054");
        NexusSpaceUnitSavedData units = new NexusSpaceUnitSavedData();
        NexusSpaceDiscoverySavedData discovery = new NexusSpaceDiscoverySavedData();
        NexusSpaceUnitRecord disabledNode = new NexusSpaceUnitRecord(nodeId, SpaceUnitType.DEATH,
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(10, 2, 2)), owner, "Expired", SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.DISABLED, java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 1, 2);
        units.put(disabledNode);
        discovery.markDiscovered(firstViewer, nodeId);
        discovery.setFavorite(firstViewer, nodeId, true);
        discovery.markDiscovered(secondViewer, nodeId);
        discovery.setFavorite(secondViewer, nodeId, true);
        if (!units.purgeInactiveDeathUnit(nodeId) || units.get(nodeId).isPresent() || !discovery.removeUnitReferences(nodeId)
                || discovery.hasDiscovered(firstViewer, nodeId) || discovery.isFavorite(firstViewer, nodeId)
                || discovery.hasDiscovered(secondViewer, nodeId) || discovery.isFavorite(secondViewer, nodeId)) {
            helper.fail("Death Node purge did not remove all persisted references");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void mapPayloadFactoryUsesOnlyVisibleActiveUnitsAndPreservesViewerFlags(GameTestHelper helper) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000061");
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000062");
        NexusSpaceUnitRecord source = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(12, 2, 2)), viewer, "Source", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0);
        NexusSpaceUnitRecord visible = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(13, 2, 2)), owner, "Visible", SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE,
                java.util.Set.of(viewer), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0);
        NexusSpaceUnitRecord hidden = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(14, 2, 2)), owner, "Hidden", SpaceUnitVisibility.HIDDEN, SpaceUnitStatus.ACTIVE,
                java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0);
        NexusSpaceUnitRecord disabled = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(15, 2, 2)), owner, "Disabled", SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.DISABLED,
                java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0);
        NexusSpaceDiscoverySavedData discovery = new NexusSpaceDiscoverySavedData();
        discovery.markDiscovered(viewer, visible.id()); discovery.setFavorite(viewer, visible.id(), true);
        var payload = NexusMapPayloadFactory.build(viewer, source, TeleportInterfaceType.COMPASS,
                java.util.List.of(visible, hidden, disabled), discovery, new NexusFriendSavedData(),
                ignored -> NexusMapQuote.unavailable(TeleportInterfaceType.COMPASS, "pending_authority"));
        if (payload.entries().size() != 1 || !payload.entries().getFirst().id().equals(visible.id())
                || !payload.entries().getFirst().favorite() || !payload.entries().getFirst().manageable()
                || payload.entries().getFirst().canTeleport()) {
            helper.fail("Map factory exposed an invalid unit or lost authoritative viewer flags");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void mapPayloadFactoryIncludesOnlyAuthorizedOnlineFriendsAcrossDimensions(GameTestHelper helper) {
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000071");
        UUID friendId = UUID.fromString("00000000-0000-0000-0000-000000000072");
        UUID strangerId = UUID.fromString("00000000-0000-0000-0000-000000000073");
        NexusSpaceUnitRecord source = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(16, 2, 2)), viewer, "Source", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0);
        NexusFriendSavedData friends = new NexusFriendSavedData();
        friends.inviteOrAccept(viewer, friendId); friends.inviteOrAccept(friendId, viewer);
        var payload = NexusMapPayloadFactory.build(viewer, source, TeleportInterfaceType.COMPASS, java.util.List.of(),
                java.util.List.of(new NexusOnlineFriendTarget(friendId, "Friend", net.minecraft.world.level.Level.NETHER, new BlockPos(32, 70, -32)),
                        new NexusOnlineFriendTarget(strangerId, "Stranger", helper.getLevel().dimension(), new BlockPos(1, 70, 1))),
                new NexusSpaceDiscoverySavedData(), friends,
                ignored -> NexusMapQuote.unavailable(TeleportInterfaceType.COMPASS, "pending_authority"),
                ignored -> NexusMapQuote.unavailable(TeleportInterfaceType.COMPASS, "pending_authority"));
        if (payload.entries().size() != 1 || !payload.entries().getFirst().id().equals(friendId)
                || !payload.entries().getFirst().dimension().equals("minecraft:the_nether")
                || !payload.entries().getFirst().friendShared()) {
            helper.fail("Map factory exposed a non-friend or lost the friend's cross-dimension identity");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void lodestoneAuthorityEnforcesManagerAndOwnerOnlyAccessRules(GameTestHelper helper) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000081");
        UUID administrator = UUID.fromString("00000000-0000-0000-0000-000000000082");
        UUID visitor = UUID.fromString("00000000-0000-0000-0000-000000000083");
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000084");
        NexusSpaceUnitSavedData units = new NexusSpaceUnitSavedData();
        units.put(new NexusSpaceUnitRecord(unitId, SpaceUnitType.LODESTONE, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(18, 2, 2)),
                owner, "Original", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, java.util.Set.of(administrator), java.util.Set.of(),
                SpaceStructureSnapshot.EMPTY, 1, 1));
        NexusLodestoneAuthority authority = new NexusLodestoneAuthority();
        if (authority.rename(units, visitor, unitId, "Denied", 2).isPresent()
                || authority.setVisibility(units, administrator, unitId, "public", 3).isEmpty()
                || authority.setAccess(units, administrator, unitId, visitor, "administrator", true, 4).isPresent()
                || authority.setAccess(units, administrator, unitId, visitor, "allowed", true, 5).isEmpty()
                || authority.rename(units, owner, unitId, "  Renamed  ", 6).isEmpty()) {
            helper.fail("Lodestone authority did not enforce manager and owner boundaries");
            return;
        }
        NexusSpaceUnitRecord updated = units.get(unitId).orElseThrow();
        if (updated.visibility() != SpaceUnitVisibility.PUBLIC || !updated.allowedPlayers().contains(visitor)
                || updated.administrators().contains(visitor) || !updated.name().equals("Renamed")) {
            helper.fail("Lodestone authority did not persist its authorized mutations");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void virtualRestartRetainsLodestoneFriendAndDistributedSpawnState(GameTestHelper helper) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000091");
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000092");
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000093");
        NexusSpaceUnitSavedData units = new NexusSpaceUnitSavedData();
        units.put(new NexusSpaceUnitRecord(unitId, SpaceUnitType.LODESTONE, helper.getLevel().dimension(), new BlockPos(20, 70, -8), owner,
                "Restart Anchor", SpaceUnitVisibility.FRIENDS, SpaceUnitStatus.ACTIVE, java.util.Set.of(viewer), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 20, 25));
        NexusFriendSavedData friends = new NexusFriendSavedData();
        friends.inviteOrAccept(owner, viewer); friends.inviteOrAccept(viewer, owner);
        NexusDistributedSpawnSavedData spawns = new NexusDistributedSpawnSavedData();
        spawns.put(viewer, helper.getLevel().dimension(), new BlockPos(4, 80, 9), 135.0F, 30);
        NexusSpaceUnitSavedData restartedUnits = roundTrip(NexusSpaceUnitSavedData.CODEC, units);
        NexusFriendSavedData restartedFriends = roundTrip(NexusFriendSavedData.CODEC, friends);
        NexusDistributedSpawnSavedData restartedSpawns = roundTrip(NexusDistributedSpawnSavedData.CODEC, spawns);
        if (restartedUnits.get(unitId).filter(unit -> unit.administrators().contains(viewer) && unit.name().equals("Restart Anchor")).isEmpty()
                || !restartedFriends.areFriends(owner, viewer)
                || restartedSpawns.get(viewer).filter(spawn -> spawn.pos().equals(new BlockPos(4, 80, 9)) && spawn.createdGameTime() == 30).isEmpty()) {
            helper.fail("Nexus SavedData did not survive a codec-backed restart round trip");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void friendListAuthoritySortsRelationshipStatesWithoutExposingUnrelatedPlayers(GameTestHelper helper) {
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID friend = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID incoming = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID outgoing = UUID.fromString("00000000-0000-0000-0000-000000000104");
        NexusFriendSavedData data = new NexusFriendSavedData();
        data.inviteOrAccept(viewer, friend); data.inviteOrAccept(friend, viewer);
        data.inviteOrAccept(incoming, viewer); data.inviteOrAccept(viewer, outgoing);
        var payload = NexusFriendListAuthority.build(helper.getLevel().getServer(), viewer, data);
        if (payload.entries().size() != 3 || !payload.entries().get(0).id().equals(friend) || !payload.entries().get(0).status().equals("friend")
                || !payload.entries().get(1).id().equals(incoming) || !payload.entries().get(1).status().equals("incoming")
                || !payload.entries().get(2).id().equals(outgoing) || !payload.entries().get(2).status().equals("outgoing")) {
            helper.fail("Friend list authority did not project and sort only authorized relationship states");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void teleportQuoteCalculatorEnforcesCrossDimensionResourceAndTierGates(GameTestHelper helper) {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000111");
        var source = new NexusTeleportQuoteCalculator.Source(UUID.randomUUID(), "lodestone", helper.getLevel().dimension(), new BlockPos(0, 64, 0), .9D, 2, 0);
        var target = new NexusTeleportQuoteCalculator.Target(UUID.randomUUID(), SpaceUnitType.LODESTONE, net.minecraft.world.level.Level.NETHER,
                new BlockPos(0, 64, 0), .9D, 1, 0D, true, player, 0);
        var blocked = NexusTeleportQuoteCalculator.calculate(source, target, TeleportInterfaceType.COMPASS,
                new NexusTeleportQuoteCalculator.Resources(player, false, 5, 10, 0, 0), false);
        var payable = NexusTeleportQuoteCalculator.calculate(source, target, TeleportInterfaceType.COMPASS,
                new NexusTeleportQuoteCalculator.Resources(player, false, 20, 20, 20, 10), false);
        if (blocked.canTeleport() || !blocked.blockedReason().equals("message.deadrecall.space_unit.teleport_blocked.amethyst")
                || !payable.canTeleport() || payable.amethystCost() < 2 || payable.prepareTicks() < 40) {
            helper.fail("Teleport quote calculator did not preserve cross-dimension resource gates");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void teleportExecutionSessionCancelsMovementAndCompletesOnlyAfterPreparation(GameTestHelper helper) {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000121");
        var session = new NexusTeleportExecutionSession(player, "lodestone", UUID.randomUUID(), UUID.randomUUID(), SpaceUnitType.LODESTONE,
                helper.getLevel().dimension(), new BlockPos(0, 64, 0), TeleportInterfaceType.COMPASS, false, false, 2);
        var moved = new NexusTeleportExecutionSession.PlayerState(true, false, helper.getLevel().dimension(), new BlockPos(5, 64, 0));
        NexusTeleportSessionStore store = new NexusTeleportSessionStore(); store.start(session);
        if (!session.cancellationReason(moved).equals("message.deadrecall.space_unit.teleport_cancelled.moved")
                || store.tick(player).filter(next -> next.remainingTicks() == 1 && !next.ready()).isEmpty()
                || store.tick(player).filter(NexusTeleportExecutionSession::ready).isEmpty() || store.get(player).isPresent()) {
            helper.fail("Teleport session did not enforce movement cancellation or preparation completion");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void lodestoneIntegrityDisablesPersistedAnchorWhenItsBlockIsMissing(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        NexusSpaceUnitSavedData units = server.overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000131");
        BlockPos pos = helper.absolutePos(new BlockPos(24, 2, 2));
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
        units.put(new NexusSpaceUnitRecord(id, SpaceUnitType.LODESTONE, helper.getLevel().dimension(), pos, UUID.randomUUID(), "Missing", SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.ACTIVE, java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0));
        NexusLodestoneIntegrityAuthority integrity = new NexusLodestoneIntegrityAuthority();
        for (int tick = 0; tick < 40; tick++) integrity.tick(server);
        if (units.get(id).filter(unit -> unit.status() == SpaceUnitStatus.DISABLED).isEmpty()) {
            helper.fail("Lodestone integrity did not disable a missing persisted anchor");
            return;
        }
        helper.succeed();
    }

    private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value) {
        return codec.parse(com.mojang.serialization.JsonOps.INSTANCE, codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value)
                .getOrThrow(IllegalArgumentException::new)).getOrThrow(IllegalArgumentException::new);
    }
}
