package dev.totem.nexus.space;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

/** Server integration coverage for durable interfaces and Nexus-owned vanilla maps. */
public final class NexusInterfaceLifecycleGameTest {
    @GameTest(maxTicks = 260, environment = "totem-nexus-gametest:compass_teleport")
    public void boundCompassStartsAndCompletesServerAuthoritativeTeleport(GameTestHelper helper) {
        verifyBoundInterfaceTeleport(helper, new ItemStack(Items.COMPASS));
    }

    @GameTest(maxTicks = 260, environment = "totem-nexus-gametest:map_teleport")
    public void previouslyIssuedValidNexusMapStartsAndCompletesServerAuthoritativeTeleport(GameTestHelper helper) {
        verifyBoundInterfaceTeleport(helper, new ItemStack(Items.MAP));
    }

    @GameTest(maxTicks = 30, environment = "totem-nexus-gametest:changed_interface_cancel")
    public void changingTheInitiatingCompassCancelsItsActiveTeleportSession(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 4));
        BlockPos target = helper.absolutePos(new BlockPos(13, 2, 4));
        buildFunctionalArray(level, source);
        buildFunctionalArray(level, target);
        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setNoGravity(true);
        player.setPos(source.getX() + 0.5D, source.getY() + 1.0D, source.getZ() + 0.5D);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        putLodestone(level, sourceId, player.getUUID(), source, SpaceUnitVisibility.PRIVATE, Set.of());
        putLodestone(level, targetId, player.getUUID(), target, SpaceUnitVisibility.PRIVATE, Set.of());
        var discovery = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        discovery.markDiscovered(player.getUUID(), sourceId);
        discovery.markDiscovered(player.getUUID(), targetId);
        ItemStack compass = bindSingle(helper, player, level, source, sourceId, new ItemStack(Items.COMPASS));
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        player.getAbilities().instabuild = true;
        if (NexusSpaceUnitAuthority.establishInterfaceContext(
                player, InteractionHand.MAIN_HAND, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId).isEmpty()) {
            player.discard();
            helper.fail("Bound compass did not establish a teleport interface context");
            return;
        }
        NexusSpaceUnitAuthority.startTeleport(
                player, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId, targetId);
        if (!NexusSpaceUnitAuthority.hasActiveTeleportSession(player.getUUID())) {
            player.discard();
            helper.fail("Valid compass teleport did not start before changed-item cancellation");
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.startSequence().thenExecuteAfter(2, () -> {
            if (NexusSpaceUnitAuthority.hasActiveTeleportSession(player.getUUID())) {
                player.discard();
                helper.fail("Teleport session survived replacement of its initiating compass");
                return;
            }
            if (player.blockPosition().closerThan(target, 3.0D)) {
                player.discard();
                helper.fail("Changed-item cancellation still moved the player to the target");
                return;
            }
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void allFourInterfacesBindDiscoverManageAndInviteUnderServerAuthority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos discoveredAnchor = anchor.offset(2, 0, 0);
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(discoveredAnchor, Blocks.LODESTONE.defaultBlockState());
        var owner = helper.makeMockServerPlayerInLevel();
        var targetPlayer = helper.makeMockServerPlayerInLevel();
        owner.getAbilities().instabuild = false;
        owner.setPos(anchor.getX() + 0.5D, anchor.getY() + 1.0D, anchor.getZ() + 0.5D);
        UUID sourceId = UUID.randomUUID();
        UUID discoveredId = UUID.randomUUID();
        putLodestone(level, sourceId, owner.getUUID(), anchor, SpaceUnitVisibility.PRIVATE, Set.of());
        putLodestone(level, discoveredId, owner.getUUID(), discoveredAnchor, SpaceUnitVisibility.PUBLIC, Set.of());
        var discovery = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        discovery.markDiscovered(owner.getUUID(), sourceId);

        ItemStack compass = bindSingle(helper, owner, level, anchor, sourceId, new ItemStack(Items.COMPASS));
        ItemStack recovery = new ItemStack(Items.RECOVERY_COMPASS);
        recovery.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        recovery = bindSingle(helper, owner, level, anchor, sourceId, recovery);
        ItemStack book = bindSingle(helper, owner, level, anchor, sourceId, new ItemStack(Items.BOOK));
        ItemStack nexusMap = bindSingle(helper, owner, level, anchor, sourceId, new ItemStack(Items.MAP));
        java.util.List<ItemStack> interfaces = java.util.List.of(compass.copy(), recovery.copy(), book.copy(), nexusMap.copy());

        if (compass.get(DataComponents.LODESTONE_TRACKER) == null
                || !Boolean.TRUE.equals(recovery.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
                || !book.is(Items.BOOK) || !nexusMap.is(Items.FILLED_MAP)
                || interfaces.stream().anyMatch(stack -> stack.get(DataComponents.CUSTOM_NAME) == null)) {
            helper.fail("Binding changed native interface identity or unrelated components");
            return;
        }

        var units = level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusFriendSavedData friends = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        for (ItemStack bound : interfaces) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, bound.copy());
            var resolved = TeleportInterfaceItemResolver.resolve(owner, InteractionHand.MAIN_HAND).orElse(null);
            if (resolved == null || !sourceId.equals(resolved.boundUnitId())
                    || NexusSpaceUnitAuthority.establishInterfaceContext(
                    owner, InteractionHand.MAIN_HAND, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId).isEmpty()
                    || !NexusSpaceUnitAuthority.requireManagementCapability(
                    owner, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId)
                    || !NexusSpaceUnitAuthority.requireManagementCapability(owner)) {
                helper.fail("A supported bound interface could not establish its management/friend context");
                return;
            }
            if (!resolved.type().canSelectTeleportDestination()) {
                NexusSpaceUnitAuthority.startTeleport(owner,
                        NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId, discoveredId);
                if (NexusSpaceUnitAuthority.hasActiveTeleportSession(owner.getUUID())) {
                    helper.fail("A management-only interface started a forged teleport session");
                    return;
                }
            }

            String managedName = "Managed " + resolved.type().id();
            NexusSpaceUnitAuthority.setLodestoneName(
                    owner, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId, sourceId, managedName);
            if (!units.get(sourceId).map(NexusSpaceUnitRecord::name).filter(managedName::equals).isPresent()) {
                helper.fail("A supported bound interface could not perform a management action");
                return;
            }

            friends.removeRelationship(owner.getUUID(), targetPlayer.getUUID());
            NexusSpaceUnitAuthority.handlePlayerInterfaceActivation(
                    owner, targetPlayer, InteractionHand.MAIN_HAND, resolved);
            if (!friends.outgoingInviteTargets(owner.getUUID()).contains(targetPlayer.getUUID())) {
                helper.fail("A supported bound interface could not perform the friend action");
                return;
            }

            discovery.removeDiscovered(owner.getUUID(), discoveredId);
            NexusSpaceUnitAuthority.handleLodestoneActivation(
                    owner, level, InteractionHand.MAIN_HAND, discoveredAnchor, resolved);
            if (!discovery.hasDiscovered(owner.getUUID(), discoveredId)) {
                helper.fail("A supported bound interface could not discover an accessible lodestone");
                return;
            }
            NexusSpaceUnitAuthority.clearInterfaceContext(owner.getUUID());
        }
        owner.discard();
        targetPlayer.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void bindingSplitsSurvivalBookPreservesCreativeSourceAndDoesNotDuplicateSameBinding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        UUID unitId = UUID.randomUUID();
        putLodestone(level, unitId, player.getUUID(), anchor, SpaceUnitVisibility.PRIVATE, Set.of());

        ItemStack books = new ItemStack(Items.BOOK, 3);
        books.set(DataComponents.CUSTOM_NAME, Component.literal("Component-Preserving Book"));
        player.setItemInHand(InteractionHand.MAIN_HAND, books);
        var input = TeleportInterfaceItemResolver.resolveRegistrationInput(player, InteractionHand.MAIN_HAND).orElseThrow();
        if (!NexusSpaceUnitAuthority.bindInterface(
                player, InteractionHand.MAIN_HAND, books, input, level, anchor, unitId)
                || books.getCount() != 2 || NexusInterfaceBinding.read(books) != null) {
            helper.fail("Survival book stack was not split exactly once");
            return;
        }
        ItemStack boundBook = findBound(player, Items.BOOK, unitId);
        if (boundBook == null || !Component.literal("Component-Preserving Book")
                .equals(boundBook.get(DataComponents.CUSTOM_NAME))) {
            helper.fail("Split book did not retain components and binding");
            return;
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, boundBook);
        int beforeSameBound = countBound(player, unitId);
        var sameInput = TeleportInterfaceItemResolver.resolveRegistrationInput(player, InteractionHand.MAIN_HAND).orElseThrow();
        if (!NexusSpaceUnitAuthority.bindInterface(
                player, InteractionHand.MAIN_HAND, boundBook, sameInput, level, anchor, unitId)
                || countBound(player, unitId) != beforeSameBound) {
            helper.fail("Rebinding the same item duplicated a bound copy");
            return;
        }

        player.getInventory().clearContent();
        player.getAbilities().instabuild = true;
        ItemStack creativeBooks = new ItemStack(Items.BOOK, 3);
        creativeBooks.set(DataComponents.CUSTOM_NAME, Component.literal("Creative Source"));
        player.setItemInHand(InteractionHand.MAIN_HAND, creativeBooks);
        var creativeInput = TeleportInterfaceItemResolver.resolveRegistrationInput(player, InteractionHand.MAIN_HAND).orElseThrow();
        if (!NexusSpaceUnitAuthority.bindInterface(
                player, InteractionHand.MAIN_HAND, creativeBooks, creativeInput, level, anchor, unitId)
                || creativeBooks.getCount() != 3 || NexusInterfaceBinding.read(creativeBooks) != null
                || countBound(player, unitId) != 1) {
            helper.fail("Creative binding changed its source stack or failed to give exactly one bound copy");
            return;
        }
        ItemStack creativeBound = findBound(player, Items.BOOK, unitId);
        if (creativeBound == null || !Component.literal("Creative Source")
                .equals(creativeBound.get(DataComponents.CUSTOM_NAME))) {
            helper.fail("Creative binding did not preserve the bound copy's native components");
            return;
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void plainBookUsePrioritizesManualWhileCrouchingRoutesToNexus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos manualAnchor = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos nexusAnchor = helper.absolutePos(new BlockPos(6, 2, 2));
        level.setBlockAndUpdate(manualAnchor, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(nexusAnchor, Blocks.LODESTONE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        var units = level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        try {
            player.setPos(manualAnchor.getX() + 0.5D, manualAnchor.getY() + 1.0D, manualAnchor.getZ() + 0.5D);
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
            if (activateLodestone(player, level, InteractionHand.MAIN_HAND, manualAnchor)
                    != net.minecraft.world.InteractionResult.SUCCESS
                    || !TotemManualAssembler.isCanonical(player.getMainHandItem())
                    || units.getLodestone(level.dimension(), manualAnchor).isPresent()
                    || TeleportInterfaceItemResolver.resolveRegistrationInput(
                    player, InteractionHand.MAIN_HAND).isPresent()) {
                helper.fail("Normal plain-book use did not remain the manual acquisition path");
                return;
            }

            player.getInventory().clearContent();
            player.setPos(nexusAnchor.getX() + 0.5D, nexusAnchor.getY() + 1.0D, nexusAnchor.getZ() + 0.5D);
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
            if (activateLodestone(player, level, InteractionHand.MAIN_HAND, nexusAnchor)
                    != net.minecraft.world.InteractionResult.SUCCESS
                    || !player.getMainHandItem().is(Items.BOOK)
                    || TotemManualAssembler.isCanonical(player.getMainHandItem())
                    || units.getLodestone(level.dimension(), nexusAnchor).isPresent()) {
                helper.fail("Crouching plain-book use did not enter the pending Nexus path");
                return;
            }

            NexusSpaceUnitAuthority.confirmLodestoneRegistration(
                    player, level.dimension().identifier().toString(),
                    nexusAnchor.getX(), nexusAnchor.getY(), nexusAnchor.getZ());
            UUID unitId = units.getLodestone(level.dimension(), nexusAnchor)
                    .map(NexusSpaceUnitRecord::id).orElse(null);
            if (unitId == null || !unitId.equals(NexusInterfaceBinding.read(player.getMainHandItem()))) {
                helper.fail("Crouching plain-book confirmation did not bind the Nexus interface");
                return;
            }
            helper.succeed();
        } finally {
            player.setShiftKeyDown(false);
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void pendingRegistrationRejectsChangedHeldIdentityAndReplay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(7, 2, 7));
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setPos(anchor.getX() + 0.5D, anchor.getY() + 1.0D, anchor.getZ() + 0.5D);
        ItemStack initiatingCompass = new ItemStack(Items.COMPASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, initiatingCompass);
        var units = level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        try {
            if (activateLodestone(player, level, InteractionHand.MAIN_HAND, anchor)
                    != net.minecraft.world.InteractionResult.SUCCESS
                    || units.getLodestone(level.dimension(), anchor).isPresent()
                    || NexusInterfaceBinding.read(initiatingCompass) != null) {
                helper.fail("Initial interaction did not create an unconsumed pending registration");
                return;
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RECOVERY_COMPASS));
            confirmRegistration(player, level, anchor);
            if (units.getLodestone(level.dimension(), anchor).isPresent()
                    || NexusInterfaceBinding.read(player.getMainHandItem()) != null) {
                helper.fail("A changed held identity consumed stale pending registration");
                return;
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, initiatingCompass);
            confirmRegistration(player, level, anchor);
            if (units.getLodestone(level.dimension(), anchor).isPresent()
                    || NexusInterfaceBinding.read(initiatingCompass) != null) {
                helper.fail("A replay recreated or consumed invalidated pending registration");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void copiedForgedAndRevokedBindingsNeverGrantServerAuthority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        var owner = helper.makeMockServerPlayerInLevel();
        var viewer = helper.makeMockServerPlayerInLevel();
        var target = helper.makeMockServerPlayerInLevel();
        owner.getAbilities().instabuild = false;
        viewer.setPos(anchor.getX() + 0.5D, anchor.getY() + 1.0D, anchor.getZ() + 0.5D);
        UUID sourceId = UUID.randomUUID();
        putLodestone(level, sourceId, owner.getUUID(), anchor, SpaceUnitVisibility.PRIVATE, Set.of());

        ItemStack compass = bindSingle(helper, owner, level, anchor, sourceId, new ItemStack(Items.COMPASS));
        ItemStack recovery = bindSingle(helper, owner, level, anchor, sourceId, new ItemStack(Items.RECOVERY_COMPASS));
        ItemStack book = bindSingle(helper, owner, level, anchor, sourceId, new ItemStack(Items.BOOK));
        ItemStack map = bindSingle(helper, owner, level, anchor, sourceId, new ItemStack(Items.MAP));
        NexusFriendSavedData friends = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        for (ItemStack copied : java.util.List.of(compass, recovery, book, map)) {
            viewer.setItemInHand(InteractionHand.MAIN_HAND, copied.copy());
            var resolved = TeleportInterfaceItemResolver.resolve(viewer, InteractionHand.MAIN_HAND).orElse(null);
            if (resolved == null || NexusSpaceUnitAuthority.validateBoundInterfaceSource(viewer, sourceId, false)
                    || NexusSpaceUnitAuthority.establishInterfaceContext(
                    viewer, InteractionHand.MAIN_HAND, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId).isPresent()) {
                helper.fail("Copied private binding granted a server context");
                return;
            }
            friends.removeRelationship(viewer.getUUID(), target.getUUID());
            NexusSpaceUnitAuthority.handlePlayerInterfaceActivation(
                    viewer, target, InteractionHand.MAIN_HAND, resolved);
            if (friends.outgoingInviteTargets(viewer.getUUID()).contains(target.getUUID())) {
                helper.fail("Copied private binding granted a friend action");
                return;
            }
        }

        ItemStack forged = new ItemStack(Items.COMPASS);
        UUID missingUnit = UUID.randomUUID();
        NexusInterfaceBinding.writeIdentity(forged, missingUnit);
        viewer.setItemInHand(InteractionHand.MAIN_HAND, forged);
        if (NexusSpaceUnitAuthority.establishInterfaceContext(
                viewer, InteractionHand.MAIN_HAND, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, missingUnit).isPresent()) {
            helper.fail("Forged binding to a missing unit granted a context");
            return;
        }

        var units = level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        units.put(units.get(sourceId).orElseThrow().withAllowedPlayer(viewer.getUUID(), true, level.getGameTime()));
        viewer.setItemInHand(InteractionHand.MAIN_HAND, compass.copy());
        if (NexusSpaceUnitAuthority.establishInterfaceContext(
                viewer, InteractionHand.MAIN_HAND, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId).isEmpty()) {
            helper.fail("Allowed player could not establish the expected pre-revocation context");
            return;
        }
        units.put(units.get(sourceId).orElseThrow().withAllowedPlayer(viewer.getUUID(), false, level.getGameTime()));
        if (NexusSpaceUnitAuthority.currentInterfaceContext(viewer).isPresent()
                || NexusSpaceUnitAuthority.validateBoundInterfaceSource(viewer, sourceId, false)) {
            helper.fail("Revoked permission left a durable interface context authorized");
            return;
        }
        owner.discard();
        viewer.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void nexusMapCreationScaleLockAndCloneKeepExactNonGridAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = new BlockPos(24, 70, -40);
        level.getChunkAt(anchor); // Test setup only; production validation never loads this chunk.
        var previousBlock = level.getBlockState(anchor);
        var units = level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        var previousUnit = units.getLodestone(level.dimension(), anchor);
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        UUID unitId = UUID.randomUUID();
        putLodestone(level, unitId, player.getUUID(), anchor, SpaceUnitVisibility.PRIVATE, Set.of());
        try {
            ItemStack emptyMap = new ItemStack(Items.MAP);
            emptyMap.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved Map Name"));
            ItemStack nexusMap = NexusMapLifecycleAuthority.createBoundMap(level, anchor, unitId, emptyMap)
                    .orElseThrow(() -> helper.assertionException("Could not create Nexus map"));
            MapId originalId = nexusMap.get(DataComponents.MAP_ID);
            MapItemSavedData originalData = MapItem.getSavedData(originalId, level);
            if (originalId == null || originalData == null
                    || originalData.centerX != 24 || originalData.centerZ != -40 || originalData.scale != 0
                    || !unitId.equals(NexusInterfaceBinding.read(nexusMap))
                    || !"Preserved Map Name".equals(nexusMap.getHoverName().getString())) {
                helper.fail("Created Nexus map did not preserve exact center, binding, or components");
                return;
            }

            ItemStack clone = nexusMap.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, clone);
            if (!originalId.equals(clone.get(DataComponents.MAP_ID))
                    || TeleportInterfaceItemResolver.resolve(player, InteractionHand.MAIN_HAND)
                    .filter(value -> value.mapId().equals(originalId) && value.boundUnitId().equals(unitId)).isEmpty()) {
                helper.fail("A same-MapId clone stopped being a valid Nexus map");
                return;
            }

            ItemStack scaled = nexusMap.copy();
            scaled.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
            if (NexusMapLifecycleAuthority.postProcess(scaled, level, MapPostProcessing.SCALE)
                    != NexusMapLifecycleAuthority.PostProcessResult.PROCESSED) {
                helper.fail("Nexus SCALE was not processed");
                return;
            }
            MapId scaledId = scaled.get(DataComponents.MAP_ID);
            MapItemSavedData scaledData = MapItem.getSavedData(scaledId, level);
            if (scaledId == null || scaledId.equals(originalId) || scaledData == null
                    || scaledData.centerX != 24 || scaledData.centerZ != -40 || scaledData.scale != 1
                    || !mapBindings(level).validates(scaledId, unitId, scaledData)) {
                helper.fail("Scaled Nexus map lost its exact anchor or registry proof");
                return;
            }

            originalData.colors[0] = 42;
            ItemStack locked = nexusMap.copy();
            locked.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
            if (NexusMapLifecycleAuthority.postProcess(locked, level, MapPostProcessing.LOCK)
                    != NexusMapLifecycleAuthority.PostProcessResult.PROCESSED) {
                helper.fail("Nexus LOCK was not processed");
                return;
            }
            MapId lockedId = locked.get(DataComponents.MAP_ID);
            MapItemSavedData lockedData = MapItem.getSavedData(lockedId, level);
            if (lockedId == null || lockedId.equals(originalId) || lockedData == null || !lockedData.locked
                    || lockedData.centerX != 24 || lockedData.centerZ != -40 || lockedData.colors[0] != 42
                    || !mapBindings(level).validates(lockedId, unitId, lockedData)) {
                helper.fail("Locked Nexus map lost pixels, center, or registry proof");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
            units.disableLodestone(unitId, level.getGameTime());
            previousUnit.ifPresent(units::put);
            level.setBlockAndUpdate(anchor, previousBlock);
        }
    }

    @GameTest(maxTicks = 20)
    public void nexusScaleRefusesMissingOrUnloadedAnchorWithoutChangingMapId(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        UUID unitId = UUID.randomUUID();
        putLodestone(level, unitId, player.getUUID(), anchor, SpaceUnitVisibility.PRIVATE, Set.of());
        ItemStack nexusMap = NexusMapLifecycleAuthority.createBoundMap(
                level, anchor, unitId, new ItemStack(Items.MAP)).orElseThrow();
        MapId originalId = nexusMap.get(DataComponents.MAP_ID);
        level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
        ItemStack missingResult = nexusMap.copy();
        missingResult.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        if (NexusMapLifecycleAuthority.expansionAnchorEligibility(missingResult, level)
                != NexusMapLifecycleAuthority.ScaleEligibility.DENIED
                || NexusMapLifecycleAuthority.postProcess(missingResult, level, MapPostProcessing.SCALE)
                != NexusMapLifecycleAuthority.PostProcessResult.DENIED
                || !originalId.equals(missingResult.get(DataComponents.MAP_ID))) {
            helper.fail("Missing-anchor SCALE changed the existing Nexus map");
            return;
        }

        BlockPos remote = new BlockPos(20_000_024, 70, -20_000_040);
        if (level.isLoaded(remote)) {
            helper.fail("Unloaded-anchor fixture was unexpectedly loaded");
            return;
        }
        UUID remoteUnitId = UUID.randomUUID();
        putLodestone(level, remoteUnitId, player.getUUID(), remote, SpaceUnitVisibility.PRIVATE, Set.of());
        MapItemSavedData remoteData = MapItemSavedData.createFresh(
                remote.getX(), remote.getZ(), (byte) 0, false, false, level.dimension());
        MapId remoteMapId = level.getFreeMapId();
        level.setMapData(remoteMapId, remoteData);
        ItemStack remoteMap = new ItemStack(Items.FILLED_MAP);
        remoteMap.set(DataComponents.MAP_ID, remoteMapId);
        NexusInterfaceBinding.writeIdentity(remoteMap, remoteUnitId);
        mapBindings(level).bind(remoteMapId, remoteUnitId, GlobalPos.of(level.dimension(), remote), remoteData);
        if (NexusMapLifecycleAuthority.expansionAnchorEligibility(remoteMap, level)
                != NexusMapLifecycleAuthority.ScaleEligibility.DENIED || level.isLoaded(remote)) {
            helper.fail("Expansion validation force-loaded or accepted an unloaded anchor");
            return;
        }

        var units = level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        units.put(units.get(remoteUnitId).orElseThrow().withStatus(SpaceUnitStatus.DISABLED, level.getGameTime()));
        if (NexusMapLifecycleAuthority.expansionAnchorEligibility(remoteMap, level)
                != NexusMapLifecycleAuthority.ScaleEligibility.DENIED) {
            helper.fail("Registry-owned map fell through to vanilla SCALE after its unit was disabled");
            return;
        }

        UUID missingUnitId = UUID.randomUUID();
        MapId missingUnitMapId = level.getFreeMapId();
        MapItemSavedData missingUnitData = MapItemSavedData.createFresh(24, -40, (byte) 0, false, false, level.dimension());
        level.setMapData(missingUnitMapId, missingUnitData);
        ItemStack missingUnitMap = new ItemStack(Items.FILLED_MAP);
        missingUnitMap.set(DataComponents.MAP_ID, missingUnitMapId);
        NexusInterfaceBinding.writeIdentity(missingUnitMap, missingUnitId);
        mapBindings(level).bind(missingUnitMapId, missingUnitId,
                GlobalPos.of(level.dimension(), new BlockPos(24, 70, -40)), missingUnitData);
        if (NexusMapLifecycleAuthority.expansionAnchorEligibility(missingUnitMap, level)
                != NexusMapLifecycleAuthority.ScaleEligibility.DENIED) {
            helper.fail("Registry-owned map fell through to vanilla SCALE after its unit disappeared");
            return;
        }

        ItemStack strippedBinding = nexusMap.copy();
        strippedBinding.remove(DataComponents.CUSTOM_DATA);
        if (NexusMapLifecycleAuthority.expansionAnchorEligibility(strippedBinding, level)
                != NexusMapLifecycleAuthority.ScaleEligibility.DENIED) {
            helper.fail("Registry-owned MapId with stripped binding fell through to vanilla SCALE");
            return;
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void arbitraryOrForgedFilledMapCannotResolveAsNexusMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        MapId mapId = level.getFreeMapId();
        MapItemSavedData data = MapItemSavedData.createFresh(24, -40, (byte) 0, false, false, level.dimension());
        level.setMapData(mapId, data);
        ItemStack forged = new ItemStack(Items.FILLED_MAP);
        forged.set(DataComponents.MAP_ID, mapId);
        NexusInterfaceBinding.writeIdentity(forged, UUID.randomUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, forged);
        if (TeleportInterfaceItemResolver.resolve(player, InteractionHand.MAIN_HAND).isPresent()
                || TeleportInterfaceItemResolver.resolveRegistrationInput(player, InteractionHand.MAIN_HAND).isPresent()) {
            helper.fail("Arbitrary filled map or forged item binding resolved as a Nexus map");
            return;
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void nexusMapPayloadFiltersNamedMarkersWithoutSharedMapDataLeakage(GameTestHelper helper) {
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID otherOwner = UUID.fromString("00000000-0000-0000-0000-000000000502");
        BlockPos center = new BlockPos(0, 70, 0);
        NexusSpaceUnitRecord source = unit(
                UUID.fromString("00000000-0000-0000-0000-000000000510"), viewer,
                "Home Nexus", Level.OVERWORLD, center, SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord inBounds = unit(
                UUID.fromString("00000000-0000-0000-0000-000000000511"), otherOwner,
                "East Archive", Level.OVERWORLD, center.offset(63, 0, 0),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord atExcludedEdge = unit(
                UUID.fromString("00000000-0000-0000-0000-000000000512"), otherOwner,
                "No Edge Arrow", Level.OVERWORLD, center.offset(64, 0, 0),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord otherDimension = unit(
                UUID.fromString("00000000-0000-0000-0000-000000000513"), otherOwner,
                "Nether Secret", Level.NETHER, center, SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord unauthorized = unit(
                UUID.fromString("00000000-0000-0000-0000-000000000514"), otherOwner,
                "Private Secret", Level.OVERWORLD, center.offset(8, 0, 0),
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord disabled = unit(
                UUID.fromString("00000000-0000-0000-0000-000000000515"), otherOwner,
                "Disabled Nexus", Level.OVERWORLD, center.offset(4, 0, 0),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.DISABLED);
        List<NexusSpaceUnitRecord> candidates = List.of(
                source, inBounds, atExcludedEdge, otherDimension, unauthorized, disabled);

        MapItemSavedData mapData = MapItemSavedData.createFresh(
                center.getX(), center.getZ(), (byte) 0, false, false, Level.OVERWORLD);
        mapData.addClientSideDecorations(List.of(new MapDecoration(
                MapDecorationTypes.BLUE_MARKER, (byte) 2, (byte) -2, (byte) 0,
                Optional.of(Component.literal("Vanilla Marker")))));
        List<MapDecoration> decorationsBefore = StreamSupport.stream(
                mapData.getDecorations().spliterator(), false).toList();
        NexusSpaceDiscoverySavedData discovery = new NexusSpaceDiscoverySavedData();
        candidates.forEach(unit -> discovery.markDiscovered(viewer, unit.id()));
        NexusFriendSavedData friends = new NexusFriendSavedData();
        MapId mapId = new MapId(501);

        SpaceUnitMapPayload payload = NexusMapPayloadFactory.build(
                viewer, source, TeleportInterfaceType.FILLED_MAP, mapId, mapData,
                candidates, discovery, friends,
                ignored -> NexusMapQuote.unavailable(TeleportInterfaceType.FILLED_MAP, "pending_authority"));
        List<MapDecoration> decorationsAfter = StreamSupport.stream(
                mapData.getDecorations().spliterator(), false).toList();
        if (payload.mapId() != mapId.id()
                || !payload.entries().stream().map(SpaceUnitMapPayload.Entry::id)
                .toList().equals(List.of(source.id(), inBounds.id()))
                || !payload.entries().stream().map(SpaceUnitMapPayload.Entry::name)
                .toList().equals(List.of("Home Nexus", "East Archive"))
                || payload.entries().stream().anyMatch(entry -> entry.id().equals(atExcludedEdge.id()))
                || !decorationsAfter.equals(decorationsBefore)) {
            helper.fail("Nexus map leaked, renamed, or synthesized an out-of-bounds marker");
            return;
        }

        SpaceUnitMapPayload management = NexusMapPayloadFactory.build(
                viewer, source, TeleportInterfaceType.BOOK, null, mapData,
                candidates, discovery, friends,
                ignored -> NexusMapQuote.unavailable(TeleportInterfaceType.BOOK, "pending_authority"));
        if (management.mapId() != SpaceUnitMapPayload.NO_MAP_ID
                || !management.entries().stream().map(SpaceUnitMapPayload.Entry::id)
                .toList().equals(List.of(source.id()))) {
            helper.fail("A management-only interface exposed Nexus map visualization entries");
            return;
        }
        helper.succeed();
    }

    private static net.minecraft.world.InteractionResult activateLodestone(
            net.minecraft.server.level.ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            BlockPos pos) {
        return UseBlockCallback.EVENT.invoker().interact(
                player,
                level,
                hand,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void confirmRegistration(
            net.minecraft.server.level.ServerPlayer player,
            ServerLevel level,
            BlockPos pos) {
        NexusSpaceUnitAuthority.confirmLodestoneRegistration(
                player,
                level.dimension().identifier().toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ());
    }

    private static NexusSpaceUnitRecord unit(
            UUID id,
            UUID owner,
            String name,
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            SpaceUnitVisibility visibility,
            SpaceUnitStatus status) {
        return new NexusSpaceUnitRecord(
                id, SpaceUnitType.LODESTONE, dimension, pos, owner, name, visibility, status,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1L, 1L);
    }

    private static void putLodestone(
            ServerLevel level,
            UUID unitId,
            UUID owner,
            BlockPos pos,
            SpaceUnitVisibility visibility,
            Set<UUID> allowedPlayers) {
        level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE).put(
                new NexusSpaceUnitRecord(unitId, SpaceUnitType.LODESTONE, level.dimension(), pos.immutable(), owner,
                        "Lifecycle Anchor", visibility, SpaceUnitStatus.ACTIVE, Set.of(), allowedPlayers,
                        SpaceStructureSnapshot.EMPTY, level.getGameTime(), level.getGameTime()));
    }

    private static void verifyBoundInterfaceTeleport(GameTestHelper helper, ItemStack input) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 4));
        BlockPos target = helper.absolutePos(new BlockPos(13, 2, 4));
        buildFunctionalArray(level, source);
        buildFunctionalArray(level, target);
        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setNoGravity(true);
        player.setPos(source.getX() + 0.5D, source.getY() + 1.0D, source.getZ() + 0.5D);
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        putLodestone(level, sourceId, player.getUUID(), source, SpaceUnitVisibility.PRIVATE, Set.of());
        putLodestone(level, targetId, player.getUUID(), target, SpaceUnitVisibility.PRIVATE, Set.of());
        var discovery = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        discovery.markDiscovered(player.getUUID(), sourceId);
        discovery.markDiscovered(player.getUUID(), targetId);

        ItemStack bound = bindSingle(helper, player, level, source, sourceId, input);
        player.setItemInHand(InteractionHand.MAIN_HAND, bound.copy());
        player.getAbilities().instabuild = true;
        // Re-establishing after binding models reopening an already issued interface.
        NexusSpaceUnitAuthority.clearInterfaceContext(player.getUUID());
        if (NexusSpaceUnitAuthority.establishInterfaceContext(
                player, InteractionHand.MAIN_HAND, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId).isEmpty()) {
            player.discard();
            helper.fail("Previously issued interface did not establish a teleport context");
            return;
        }
        NexusSpaceUnitAuthority.startTeleport(
                player, NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE, sourceId, targetId);
        if (!NexusSpaceUnitAuthority.hasActiveTeleportSession(player.getUUID())) {
            player.discard();
            helper.fail("Valid bound interface did not start a teleport session: " + bound.getItem());
            return;
        }

        helper.succeedWhen(() -> {
            BlockPos landed = player.blockPosition();
            int horizontalOffset = Math.max(
                    Math.abs(landed.getX() - target.getX()),
                    Math.abs(landed.getZ() - target.getZ()));
            if (NexusSpaceUnitAuthority.hasActiveTeleportSession(player.getUUID())) {
                throw helper.assertionException("Waiting for active bound interface teleport: " + bound.getItem());
            }
            if (landed.closerThan(source.above(), 2.0D)
                    || horizontalOffset > TeleportInterfaceQuotePolicy.MAX_DEVIATION + 1
                    || Math.abs(landed.getY() - target.above().getY()) > 8) {
                throw helper.assertionException("Bound interface teleport did not reach its permitted target area: "
                        + bound.getItem() + " at " + landed.toShortString());
            }
            player.discard();
        });
    }

    private static void buildFunctionalArray(ServerLevel level, BlockPos anchor) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos position = anchor.offset(x, 0, z);
                level.setBlockAndUpdate(position, (x == 0 && z == 0
                        ? Blocks.LODESTONE
                        : Blocks.GOLD_BLOCK).defaultBlockState());
            }
        }
    }

    private static NexusMapBindingSavedData mapBindings(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(NexusMapBindingSavedData.TYPE);
    }

    private static ItemStack bindSingle(
            GameTestHelper helper,
            net.minecraft.server.level.ServerPlayer player,
            ServerLevel level,
            BlockPos anchor,
            UUID unitId,
            ItemStack stack) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved " + stack.getItem()));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var input = TeleportInterfaceItemResolver.resolveRegistrationInput(player, InteractionHand.MAIN_HAND)
                .orElseThrow(() -> helper.assertionException("Interface did not resolve for binding"));
        if (!NexusSpaceUnitAuthority.bindInterface(
                player, InteractionHand.MAIN_HAND, stack, input, level, anchor, unitId)) {
            throw helper.assertionException("Interface binding failed");
        }
        return player.getItemInHand(InteractionHand.MAIN_HAND).copy();
    }

    private static ItemStack findBound(
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.world.item.Item item,
            UUID unitId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(item) && unitId.equals(NexusInterfaceBinding.read(candidate))) {
                ItemStack taken = candidate.copy();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                return taken;
            }
        }
        return null;
    }

    private static int countBound(net.minecraft.server.level.ServerPlayer player, UUID unitId) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (unitId.equals(NexusInterfaceBinding.read(candidate))) count += candidate.getCount();
        }
        return count;
    }
}
