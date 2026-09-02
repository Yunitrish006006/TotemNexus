package dev.totem.nexus.space;

import dev.totem.core.api.v1.event.DeathBackpackRecoveredEvent;
import dev.totem.core.api.v1.event.SpaceUnitPublicUpdateEvent;
import dev.totem.core.api.v1.event.TotemEventBus;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class NexusSpaceUnitAuthority {
    private static final Logger LOGGER = LoggerFactory.getLogger("totem-nexus");
    public static final String SOURCE_TYPE_LODESTONE = "lodestone";
    public static final String SOURCE_TYPE_PLAYER = "player";

    private static final String TAG_DEATH_NODE_ID = "deadrecall_space_death_node_id";
    private static final String ACCESS_ROLE_ADMINISTRATOR = "administrator";
    private static final String ACCESS_ROLE_ALLOWED = "allowed";
    private static final double SOURCE_OPEN_RADIUS = 8.0D;
    private static final int MIN_REMAINING_FOOD_LEVEL = 1;
    private static final int SAME_DIMENSION_FOOD_BLOCKS_PER_POINT = 384;
    private static final int MAX_BASE_FOOD_COST = 20;
    private static final int MIN_CROSS_DIMENSION_AMETHYST_COST = 2;
    private static final int LODESTONE_VALIDATION_INTERVAL_TICKS = 40;
    private static final int LODESTONE_REGISTRATION_CONFIRM_TICKS = 20 * 30;
    private static final int TELEPORT_INTERFACE_CONTEXT_TICKS = 20 * 60 * 10;
    private static final int MAX_LODESTONE_NAME_LENGTH = 48;
    private static final double SESSION_MOVE_CANCEL_DISTANCE = 4.0D;

    private static final Map<UUID, TeleportSession> teleportSessions = new HashMap<>();
    private static final Map<UUID, LandingSearchSession> landingSearchSessions = new HashMap<>();
    private static final TeleportRouteReservationStore routeReservations = new TeleportRouteReservationStore();
    private static final Map<UUID, TeleportInterfaceContext> teleportInterfaceContexts = new HashMap<>();
    private static final Map<UUID, PendingLodestoneRegistration> pendingLodestoneRegistrations = new HashMap<>();
    private static int lodestoneValidationTicker = 0;

    private NexusSpaceUnitAuthority() {
    }

    public static void register() {
        NexusSpaceUnitDegradationRules.registerReloadListener();
        TeleportArrayMaterialProfiles.registerReloadListener();

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (state.is(Blocks.LODESTONE) && world instanceof ServerLevel level) {
                disableLodestoneAt(level, pos);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            ItemStack stack = player.getItemInHand(hand);
            if (world.getBlockState(pos).is(Blocks.LODESTONE)
                    && NexusInterfaceGesturePolicy.grantsManual(stack, player.isShiftKeyDown())) {
                if (world.isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                NexusTeleportManual.grant((ServerPlayer) player, hand);
                return InteractionResult.SUCCESS;
            }

            Optional<TeleportInterfaceItemResolver.RegistrationInput> registrationInput = world.isClientSide()
                    ? TeleportInterfaceItemResolver.resolveRegistrationInput(stack)
                    : TeleportInterfaceItemResolver.resolveRegistrationInput((ServerPlayer) player, hand);
            if (registrationInput.isEmpty() || !world.getBlockState(pos).is(Blocks.LODESTONE)) {
                return InteractionResult.PASS;
            }
            if (!NexusInterfaceGesturePolicy.routesNexus(registrationInput.get(), player.isShiftKeyDown())) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            return handleLodestoneUse(
                    (ServerPlayer) player,
                    (ServerLevel) world,
                    hand,
                    stack,
                    registrationInput.get(),
                    pos
            );
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved = world.isClientSide()
                    ? TeleportInterfaceItemResolver.resolve(player.getItemInHand(hand))
                    : TeleportInterfaceItemResolver.resolve((ServerPlayer) player, hand);
            if (resolved.isEmpty() || resolved.get().boundUnitId() == null) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            openBoundInterface((ServerPlayer) player, hand, resolved.get());
            return InteractionResult.SUCCESS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved = world.isClientSide()
                    ? TeleportInterfaceItemResolver.resolve(player.getItemInHand(hand))
                    : TeleportInterfaceItemResolver.resolve((ServerPlayer) player, hand);
            if (resolved.isEmpty() || resolved.get().boundUnitId() == null || !(entity instanceof Player)) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (!(entity instanceof ServerPlayer target)) return InteractionResult.SUCCESS;
            return handlePlayerInterfaceActivation((ServerPlayer) player, target, hand, resolved.get());
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved = world.isClientSide()
                    ? TeleportInterfaceItemResolver.resolve(stack)
                    : TeleportInterfaceItemResolver.resolve((ServerPlayer) player, hand);
            if (resolved.isEmpty() || resolved.get().boundUnitId() == null || !resolved.get().type().canDiscover()
                    || !world.getBlockState(pos).is(Blocks.LODESTONE)) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            return handleLodestoneActivation(
                    (ServerPlayer) player,
                    (ServerLevel) world,
                    hand,
                    pos,
                    resolved.get());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            UUID playerId = listener.getPlayer().getUUID();
            teleportSessions.remove(playerId);
            routeReservations.release(playerId);
            closeLandingSearch(playerId);
            teleportInterfaceContexts.remove(playerId);
            pendingLodestoneRegistrations.remove(playerId);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearTeleportSessions());
    }

    public static UUID createDeathNode(ServerPlayer player, ServerLevel level, BlockPos deathPos) {
        NexusSpaceUnitSavedData units = units(level.getServer());
        NexusSpaceDiscoverySavedData discovery = discovery(level.getServer());
        NexusSpaceUnitRecord unit = units.createDeathUnit(level, deathPos, player);
        discovery.markDiscovered(player.getUUID(), unit.id());
        return unit.id();
    }

    public static void writeDeathNodeBinding(ItemStack deathBackpack, UUID unitId) {
        if (deathBackpack.isEmpty() || unitId == null) {
            return;
        }

        CompoundTag tag = deathBackpack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.store(TAG_DEATH_NODE_ID, UUIDUtil.CODEC, unitId);
        deathBackpack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void disableDeathNodeFromBackpack(ServerPlayer player, ItemStack deathBackpack) {
        UUID unitId = readDeathNodeId(deathBackpack);
        if (unitId == null) {
            return;
        }

        boolean disabled = units(player.level().getServer())
                .disableDeathUnit(player.getUUID(), unitId, player.level().getGameTime());
        if (disabled) {
            notify(player, Component.translatable("message.deadrecall.space_unit.death_node_recovered"));
            publishDeathBackpackRecovered(player.getName().getString());
        }
    }

    public static boolean disableDeathNode(ServerPlayer player, ServerLevel level, UUID unitId) {
        if (unitId == null) {
            return false;
        }
        return units(level.getServer()).disableDeathUnit(player.getUUID(), unitId, level.getGameTime());
    }

    /**
     * Reverts a death-node creation that failed before its backpack transaction
     * committed. Unlike ordinary recovery, this must also remove the discovery
     * reference created with the node so a failed capture leaves no visible
     * tombstone in the owner's map.
     */
    public static boolean rollbackDeathNode(ServerPlayer player, ServerLevel level, UUID unitId) {
        if (player == null || level == null || unitId == null) {
            return false;
        }
        boolean disabled = disableDeathNode(player, level, unitId);
        discovery(level.getServer()).removeDiscovered(player.getUUID(), unitId);
        return disabled;
    }

    /**
     * Finalizes a backpack-bound death node. Unlike the owner-only disable
     * action, recovery may be performed by any player and succeeds when an
     * administrator has already removed the record.
     */
    public static boolean recoverDeathNode(ServerPlayer player, UUID unitId) {
        if (player == null || unitId == null) {
            return false;
        }
        return units(player.level().getServer()).recoverDeathUnit(unitId, player.level().getGameTime());
    }

    public static void sendSpaceUnitMap(ServerPlayer player) {
        Optional<InteractionHand> hand = findBoundInterfaceHand(player);
        if (hand.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_bound_interface"));
            return;
        }

        TeleportInterfaceItemResolver.resolve(player, hand.get())
                .ifPresent(resolved -> openBoundInterface(player, hand.get(), resolved));
    }

    public static void sendSpaceUnitMap(ServerPlayer player, String sourceType, UUID sourceUnitId) {
        if (requireInterfaceContext(player, sourceType, sourceUnitId, true).isEmpty()) {
            return;
        }
        if (SOURCE_TYPE_LODESTONE.equals(sourceType)) {
            sendSpaceUnitMap(player, sourceUnitId);
            return;
        }

        notify(player, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
    }

    public static void startTeleport(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId) {
        teleportSessions.remove(player.getUUID());
        routeReservations.release(player.getUUID());
        closeLandingSearch(player.getUUID());

        Optional<TeleportInterfaceContext> interfaceContext =
                requireInterfaceContext(player, sourceType, sourceUnitId, true);
        if (interfaceContext.isEmpty()) {
            return;
        }
        if (!interfaceContext.orElseThrow().interfaceType().canSelectTeleportDestination()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.management_only"));
            return;
        }

        Optional<MapSource> source = resolveMapSource(player, sourceType, sourceUnitId, true, true);
        if (source.isEmpty()) {
            return;
        }

        Optional<TeleportTarget> target = resolveTeleportTarget(player, targetUnitId, true, true);
        if (target.isEmpty()) {
            return;
        }

        TeleportQuote quote = calculateTeleportQuote(
                player,
                source.get(),
                target.get(),
                interfaceContext.get().interfaceType(),
                interfaceContext.get().mapId()
        );
        if (!quote.canTeleport()) {
            notify(player, Component.translatable(quote.blockedReason()));
            return;
        }

        ServerLevel targetLevel = player.level().getServer().getLevel(target.get().dimension());
        if (targetLevel == null) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.no_landing"));
            return;
        }

        List<TeleportRouteReservationStore.Endpoint> routeEndpoints = routeReservationEndpoints(
                player,
                source.get(),
                target.get()
        );
        if (!routeEndpoints.isEmpty()
                && !routeReservations.reserve(player.getUUID(), routeEndpoints, player.level().getGameTime())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.route_busy"));
            return;
        }

        int prepareTicks = Math.max(1, quote.prepareTicks());
        teleportSessions.put(player.getUUID(), new TeleportSession(
                player.getUUID(),
                source.get().type(),
                source.get().id(),
                target.get().id(),
                target.get().type(),
                player.level().dimension(),
                player.blockPosition().immutable(),
                interfaceContext.get().interfaceType(),
                interfaceContext.get().interactionHand(),
                interfaceContext.get().mapId(),
                interfaceContext.get().boundUnitId(),
                quote.filledMapDataValid(),
                quote.interfaceBonusActive(),
                prepareTicks,
                prepareTicks
        ));
        notify(player, Component.translatable(
                "message.deadrecall.space_unit.teleport_started",
                target.get().name(),
                seconds(prepareTicks)
        ));
    }

    public static void setFavorite(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId, boolean favorite) {
        if (requireInterfaceContext(player, sourceType, sourceUnitId, true).isEmpty()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData units = units(server);
        NexusSpaceDiscoverySavedData discovery = discovery(server);

        Optional<NexusSpaceUnitRecord> target = units.get(targetUnitId);
        if (target.isEmpty()
                || target.get().status() != SpaceUnitStatus.ACTIVE
                || !canView(player, target.get())
                || !discovery.hasDiscovered(player.getUUID(), target.get().id())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }

        boolean changed = discovery.setFavorite(player.getUUID(), target.get().id(), favorite);
        if (changed) {
            notify(player, Component.translatable(favorite
                    ? "message.deadrecall.space_unit.favorite_added"
                    : "message.deadrecall.space_unit.favorite_removed",
                    target.get().name()));
        }

        resolveMapSource(player, sourceType, sourceUnitId, false)
                .ifPresent(source -> ServerPlayNetworking.send(
                        player,
                        buildMapPayload(player, source, visibleDiscoveredUnits(player))
                ));
    }

    public static void calibrateLodestone(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId) {
        if (!requireManagementCapability(player, sourceType, sourceUnitId)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Optional<MapSource> source = resolveMapSource(player, sourceType, sourceUnitId, true);
        if (source.isEmpty()) {
            return;
        }

        NexusSpaceUnitSavedData units = units(server);
        NexusSpaceDiscoverySavedData discovery = discovery(server);
        Optional<ManageableLodestone> target = resolveManageableLodestone(
                player,
                targetUnitId,
                Component.translatable("message.deadrecall.space_unit.calibrate_missing"),
                Component.translatable("message.deadrecall.space_unit.calibrate_too_far"),
                Component.translatable("message.deadrecall.space_unit.calibrate_unloaded")
        );
        if (target.isEmpty()) {
            return;
        }

        Optional<NexusSpaceUnitRecord> calibrated = units.rescanLodestone(target.get().level(), target.get().unit().id());
        if (calibrated.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.calibrate_missing"));
            return;
        }

        SpaceStructureSnapshot structure = calibrated.get().structure();
        notify(player, Component.translatable(
                "message.deadrecall.space_unit.calibrated",
                calibrated.get().name(),
                structure.tier(),
                Math.round(structure.resonance() * 100.0D)
        ));

        resolveMapSource(player, source.get().type(), source.get().id(), false)
                .ifPresent(refreshedSource -> ServerPlayNetworking.send(
                        player,
                        buildMapPayload(player, refreshedSource, visibleDiscoveredUnits(player))
                ));
    }

    /** Repairs exactly one server-advertised worn position using matching intact material from the player's inventory. */
    public static void repairLodestoneStructure(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            BlockPos wornPos) {
        if (wornPos == null || !requireManagementCapability(player, sourceType, sourceUnitId)) {
            return;
        }
        Optional<MapSource> source = resolveMapSource(player, sourceType, sourceUnitId, true);
        if (source.isEmpty()) {
            return;
        }
        Optional<ManageableLodestone> target = resolveManageableLodestone(
                player,
                targetUnitId,
                Component.translatable("message.deadrecall.space_unit.maintenance_missing"),
                Component.translatable("message.deadrecall.space_unit.maintenance_too_far"),
                Component.translatable("message.deadrecall.space_unit.maintenance_unloaded")
        );
        if (target.isEmpty()) {
            return;
        }

        NexusSpaceUnitSavedData units = units(player.level().getServer());
        if (!units.wornLodestoneStructureBlocks(target.get().level(), target.get().unit().id()).contains(wornPos.immutable())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.maintenance_target_invalid"));
            return;
        }
        BlockState wornState = target.get().level().getBlockState(wornPos);
        TeleportArrayMaterialProfile wornProfile = TeleportArrayMaterialProfiles.profileFor(wornState);
        int cost = TeleportRouteLoadPolicy.maintenanceItemCost(target.get().unit().structure().maintenanceEfficiency());
        Optional<RepairMaterial> repairMaterial = findRepairMaterial(player, wornProfile.family(), cost);
        if (repairMaterial.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.maintenance_material_missing", cost, wornProfile.family()));
            return;
        }

        BlockState replacement = repairMaterial.get().block().withPropertiesOf(wornState);
        if (!units.repairLodestoneStructureBlock(target.get().level(), target.get().unit().id(), wornPos, replacement)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.maintenance_target_invalid"));
            return;
        }
        repairMaterial.get().stack().shrink(cost);
        LOGGER.info("Space Unit maintenance: player={} unit={} position={},{},{} family={} cost={}",
                player.getGameProfile().name(), target.get().unit().id(), wornPos.getX(), wornPos.getY(), wornPos.getZ(),
                wornProfile.family(), cost);
        notify(player, Component.translatable(
                "message.deadrecall.space_unit.maintenance_completed",
                target.get().unit().name(), wornPos.getX(), wornPos.getY(), wornPos.getZ(), cost
        ));

        resolveMapSource(player, source.get().type(), source.get().id(), false)
                .ifPresent(refreshedSource -> ServerPlayNetworking.send(
                        player,
                        buildMapPayload(player, refreshedSource, visibleDiscoveredUnits(player))
                ));
    }

    public static void setLodestoneVisibility(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            String visibilityId) {
        if (!requireManagementCapability(player, sourceType, sourceUnitId)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Optional<MapSource> source = resolveMapSource(player, sourceType, sourceUnitId, true);
        if (source.isEmpty()) {
            return;
        }

        Optional<SpaceUnitVisibility> visibility = managedVisibility(visibilityId);
        if (visibility.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.visibility_invalid"));
            return;
        }

        NexusSpaceUnitSavedData units = units(server);
        NexusSpaceDiscoverySavedData discovery = discovery(server);
        Optional<ManageableLodestone> target = resolveManageableLodestone(
                player,
                targetUnitId,
                Component.translatable("message.deadrecall.space_unit.manage_missing"),
                Component.translatable("message.deadrecall.space_unit.manage_too_far"),
                Component.translatable("message.deadrecall.space_unit.manage_unloaded")
        );
        if (target.isEmpty()) {
            return;
        }

        NexusSpaceUnitRecord previous = target.get().unit();
        Optional<NexusSpaceUnitRecord> updated = units.setLodestoneVisibility(
                previous.id(),
                player.getUUID(),
                visibility.get(),
                target.get().level().getGameTime()
        );
        if (updated.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }

        notify(player, Component.translatable(
                "message.deadrecall.space_unit.visibility_updated",
                updated.get().name(),
                Component.translatable("message.deadrecall.space_unit.visibility." + updated.get().visibility().id())
        ));

        sendPublicLodestoneVisibilityUpdate(player, previous, updated.get());

        resolveMapSource(player, source.get().type(), source.get().id(), false)
                .ifPresent(refreshedSource -> ServerPlayNetworking.send(
                        player,
                        buildMapPayload(player, refreshedSource, visibleDiscoveredUnits(player))
                ));
    }

    public static void setLodestoneName(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            String name) {
        if (!requireManagementCapability(player, sourceType, sourceUnitId)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Optional<MapSource> source = resolveMapSource(player, sourceType, sourceUnitId, true);
        if (source.isEmpty()) {
            return;
        }

        String normalizedName = normalizeLodestoneName(name);
        if (normalizedName.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.name_invalid"));
            return;
        }

        NexusSpaceUnitSavedData units = units(server);
        NexusSpaceDiscoverySavedData discovery = discovery(server);
        Optional<ManageableLodestone> target = resolveManageableLodestone(
                player,
                targetUnitId,
                Component.translatable("message.deadrecall.space_unit.manage_missing"),
                Component.translatable("message.deadrecall.space_unit.manage_too_far"),
                Component.translatable("message.deadrecall.space_unit.manage_unloaded")
        );
        if (target.isEmpty()) {
            return;
        }

        NexusSpaceUnitRecord previous = target.get().unit();
        Optional<NexusSpaceUnitRecord> updated = units.setLodestoneName(
                previous.id(),
                player.getUUID(),
                normalizedName,
                target.get().level().getGameTime()
        );
        if (updated.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }

        notify(player, Component.translatable("message.deadrecall.space_unit.name_updated", updated.get().name()));

        if (isPublicLodestone(updated.get()) && !previous.name().equals(updated.get().name())) {
            publishPublicSpaceUnitUpdate(
                    player.getName().getString(),
                    player.getName().getString() + " 將公開磁石重新命名為 " + updated.get().name()
            );
        }

        resolveMapSource(player, source.get().type(), source.get().id(), false)
                .ifPresent(refreshedSource -> ServerPlayNetworking.send(
                        player,
                        buildMapPayload(player, refreshedSource, visibleDiscoveredUnits(player))
                ));
    }

    public static void setLodestoneAccess(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            String roleId,
            String targetPlayerName,
            boolean enabled) {
        if (!requireManagementCapability(player, sourceType, sourceUnitId)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Optional<MapSource> source = resolveMapSource(player, sourceType, sourceUnitId, true);
        if (source.isEmpty()) {
            return;
        }

        String normalizedRole = roleId == null ? "" : roleId.trim().toLowerCase(Locale.ROOT);
        if (!ACCESS_ROLE_ADMINISTRATOR.equals(normalizedRole) && !ACCESS_ROLE_ALLOWED.equals(normalizedRole)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.access_invalid"));
            return;
        }

        String normalizedPlayerName = targetPlayerName == null ? "" : targetPlayerName.trim();
        if (normalizedPlayerName.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.access_name_invalid"));
            return;
        }

        ServerPlayer targetPlayer = findOnlinePlayer(server, normalizedPlayerName);
        if (targetPlayer == null) {
            notify(player, Component.translatable("message.deadrecall.space_unit.access_player_missing", normalizedPlayerName));
            return;
        }

        NexusSpaceUnitSavedData units = units(server);
        Optional<ManageableLodestone> target = resolveManageableLodestone(
                player,
                targetUnitId,
                Component.translatable("message.deadrecall.space_unit.manage_missing"),
                Component.translatable("message.deadrecall.space_unit.manage_too_far"),
                Component.translatable("message.deadrecall.space_unit.manage_unloaded")
        );
        if (target.isEmpty()) {
            return;
        }

        NexusSpaceUnitRecord previous = target.get().unit();
        if (previous.owner().equals(targetPlayer.getUUID())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.access_owner_target"));
            return;
        }

        Optional<NexusSpaceUnitRecord> updated;
        if (ACCESS_ROLE_ADMINISTRATOR.equals(normalizedRole)) {
            if (!previous.owner().equals(player.getUUID())) {
                notify(player, Component.translatable("message.deadrecall.space_unit.access_owner_only"));
                return;
            }
            updated = units.setLodestoneAdministrator(
                    previous.id(),
                    player.getUUID(),
                    targetPlayer.getUUID(),
                    enabled,
                    target.get().level().getGameTime()
            );
        } else {
            updated = units.setLodestoneAllowedPlayer(
                    previous.id(),
                    player.getUUID(),
                    targetPlayer.getUUID(),
                    enabled,
                    target.get().level().getGameTime()
            );
        }

        if (updated.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }

        notify(player, Component.translatable(
                "message.deadrecall.space_unit.access_updated",
                targetPlayer.getName(),
                Component.translatable("message.deadrecall.space_unit.access_role." + normalizedRole),
                Component.translatable(enabled
                        ? "message.deadrecall.space_unit.access_granted"
                        : "message.deadrecall.space_unit.access_revoked")
        ));

        resolveMapSource(player, source.get().type(), source.get().id(), false)
                .ifPresent(refreshedSource -> ServerPlayNetworking.send(
                        player,
                        buildMapPayload(player, refreshedSource, visibleDiscoveredUnits(player))
                ));
    }

    public static void sendFriendList(ServerPlayer player) {
        if (!requireManagementCapability(player)) {
            return;
        }
        sendFriendListUnchecked(player);
    }

    private static void sendFriendListUnchecked(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        NexusFriendSavedData friendData = friends(server);
        UUID playerId = player.getUUID();
        List<SpaceUnitFriendsPayload.Entry> entries = new ArrayList<>();

        for (UUID friendId : friendData.friendsOf(playerId)) {
            entries.add(friendEntry(server, friendId, "friend"));
        }
        for (UUID targetId : friendData.outgoingInviteTargets(playerId)) {
            entries.add(friendEntry(server, targetId, "outgoing"));
        }
        for (UUID sourceId : friendData.incomingInviteSources(playerId)) {
            entries.add(friendEntry(server, sourceId, "incoming"));
        }

        entries.sort(Comparator
                .comparingInt((SpaceUnitFriendsPayload.Entry entry) -> friendStatusSort(entry.status()))
                .thenComparing((SpaceUnitFriendsPayload.Entry entry) -> !entry.online())
                .thenComparing(SpaceUnitFriendsPayload.Entry::name, String.CASE_INSENSITIVE_ORDER));
        ServerPlayNetworking.send(player, new SpaceUnitFriendsPayload(entries));
    }

    public static void removeFriend(ServerPlayer player, UUID friendId) {
        if (!requireManagementCapability(player)) {
            return;
        }
        if (friendId == null || player.getUUID().equals(friendId)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.friend_invalid"));
            sendFriendList(player);
            return;
        }

        MinecraftServer server = player.level().getServer();
        NexusFriendSavedData friendData = friends(server);
        UUID playerId = player.getUUID();
        boolean removed = friendData.removeRelationship(playerId, friendId);
        if (!removed) {
            notify(player, Component.translatable("message.deadrecall.space_unit.friend_remove_missing"));
            sendFriendList(player);
            return;
        }

        String otherName = playerDisplayName(server, friendId);
        notify(player, Component.translatable("message.deadrecall.space_unit.friend_removed", otherName));
        sendFriendList(player);

        ServerPlayer other = server.getPlayerList().getPlayer(friendId);
        if (other != null) {
            notify(other, Component.translatable("message.deadrecall.space_unit.friend_removed_by", player.getName()));
            currentInterfaceContext(other)
                    .filter(context -> context.interfaceType().canManageFriends())
                    .ifPresent(context -> sendFriendListUnchecked(other));
        }
    }

    public static void tickTeleportSessions(MinecraftServer server) {
        routeReservations.expire(server.overworld().getGameTime());
        if (teleportSessions.isEmpty() && landingSearchSessions.isEmpty() && routeReservations.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, TeleportSession>> iterator = teleportSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TeleportSession> entry = iterator.next();
            TeleportSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (player == null) {
                iterator.remove();
                routeReservations.release(session.playerId());
                continue;
            }

            Component cancelReason = teleportCancelReason(player, session);
            if (cancelReason != null) {
                iterator.remove();
                routeReservations.release(session.playerId());
                notify(player, cancelReason);
                continue;
            }

            Optional<MapSource> source = resolveMapSource(player, session.sourceType(), session.sourceUnitId(), false);
            if (source.isEmpty()) {
                iterator.remove();
                routeReservations.release(session.playerId());
                notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.source"));
                continue;
            }

            Optional<TeleportTarget> target = resolveTeleportTarget(player, session.targetUnitId(), false);
            if (target.isEmpty()) {
                iterator.remove();
                routeReservations.release(session.playerId());
                notify(player, targetCancelReason(player, session.targetType(), session.targetUnitId()));
                continue;
            }

            TeleportQuote quote = calculateTeleportQuote(
                    player,
                    source.get(),
                    target.get(),
                    session.interfaceType(),
                    session.mapId()
            );
            if (filledMapSessionQuoteInvalid(session, quote)) {
                iterator.remove();
                routeReservations.release(session.playerId());
                notify(player, Component.translatable(
                        "message.deadrecall.space_unit.teleport_cancelled.interface_quote_changed"));
                continue;
            }
            if (!quote.canTeleport()) {
                iterator.remove();
                routeReservations.release(session.playerId());
                notify(player, Component.translatable(quote.blockedReason()));
                continue;
            }

            TeleportSession next = session.tick();
            if (next.remainingTicks() > 0) {
                entry.setValue(next);
                continue;
            }

            iterator.remove();
            completeTeleport(player, source.get(), target.get(), quote, session);
        }
        tickLandingSearchSessions(server);
    }

    public static void tickLodestoneIntegrity(MinecraftServer server) {
        lodestoneValidationTicker++;
        if (lodestoneValidationTicker < LODESTONE_VALIDATION_INTERVAL_TICKS) {
            return;
        }
        lodestoneValidationTicker = 0;

        long gameTime = server.overworld().getGameTime();
        pendingLodestoneRegistrations.entrySet().removeIf(entry -> entry.getValue().isExpired(gameTime));
        teleportInterfaceContexts.entrySet().removeIf(entry -> entry.getValue().isExpired(gameTime));

        NexusSpaceUnitSavedData units = units(server);
        for (NexusSpaceUnitRecord unit : units.activeLodestones()) {
            ServerLevel level = server.getLevel(unit.dimension());
            if (level == null || !level.isLoaded(unit.pos())) {
                continue;
            }
            if (!level.getBlockState(unit.pos()).is(Blocks.LODESTONE)) {
                units.disableLodestone(unit.dimension(), unit.pos(), level.getGameTime());
            }
        }
    }

    public static void cancelTeleport(ServerPlayer player, Component reason) {
        boolean cancelled = teleportSessions.remove(player.getUUID()) != null;
        cancelled = closeLandingSearch(player.getUUID()) || cancelled;
        cancelled = routeReservations.release(player.getUUID()) || cancelled;
        if (cancelled) {
            notify(player, reason);
        }
    }

    static boolean hasActiveTeleportSession(UUID playerId) {
        return teleportSessions.containsKey(playerId) || landingSearchSessions.containsKey(playerId);
    }

    private static boolean closeLandingSearch(UUID playerId) {
        LandingSearchSession pending = landingSearchSessions.remove(playerId);
        if (pending == null) {
            return false;
        }
        pending.search().close();
        return true;
    }

    private static void clearTeleportSessions() {
        teleportSessions.clear();
        landingSearchSessions.values().forEach(pending -> pending.search().close());
        landingSearchSessions.clear();
        routeReservations.clear();
    }

    private static Optional<ManageableLodestone> resolveManageableLodestone(
            ServerPlayer player,
            UUID targetUnitId,
            Component missingMessage,
            Component tooFarMessage,
            Component unloadedMessage) {
        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData units = units(server);
        NexusSpaceDiscoverySavedData discovery = discovery(server);
        Optional<NexusSpaceUnitRecord> targetUnit = units.get(targetUnitId);
        if (targetUnit.isEmpty()
                || targetUnit.get().status() != SpaceUnitStatus.ACTIVE
                || !targetUnit.get().isLodestoneAnchor()) {
            notify(player, missingMessage);
            return Optional.empty();
        }

        NexusSpaceUnitRecord target = targetUnit.get();
        if (!target.canManage(player.getUUID())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return Optional.empty();
        }
        if (!discovery.hasDiscovered(player.getUUID(), target.id())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target_unexplored"));
            return Optional.empty();
        }
        if (!isNearSource(player, target)) {
            notify(player, tooFarMessage);
            return Optional.empty();
        }

        ServerLevel targetLevel = server.getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(target.pos())) {
            notify(player, unloadedMessage);
            return Optional.empty();
        }
        if (!targetLevel.getBlockState(target.pos()).is(Blocks.LODESTONE)) {
            units.disableLodestone(target.dimension(), target.pos(), targetLevel.getGameTime());
            notify(player, missingMessage);
            return Optional.empty();
        }
        return Optional.of(new ManageableLodestone(target, targetLevel));
    }

    private static Optional<SpaceUnitVisibility> managedVisibility(String visibilityId) {
        if (SpaceUnitVisibility.PRIVATE.id().equalsIgnoreCase(visibilityId)) {
            return Optional.of(SpaceUnitVisibility.PRIVATE);
        }
        if (SpaceUnitVisibility.FRIENDS.id().equalsIgnoreCase(visibilityId)) {
            return Optional.of(SpaceUnitVisibility.FRIENDS);
        }
        if (SpaceUnitVisibility.PUBLIC.id().equalsIgnoreCase(visibilityId)) {
            return Optional.of(SpaceUnitVisibility.PUBLIC);
        }
        return Optional.empty();
    }

    private static String normalizeLodestoneName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_LODESTONE_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_LODESTONE_NAME_LENGTH).trim();
        }
        return normalized;
    }

    private static boolean canView(ServerPlayer player, NexusSpaceUnitRecord unit) {
        return unit.canView(player.getUUID(), friends(player.level().getServer()).areFriends(player.getUUID(), unit.owner()));
    }

    private static SpaceUnitFriendsPayload.Entry friendEntry(MinecraftServer server, UUID playerId, String status) {
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
        return new SpaceUnitFriendsPayload.Entry(
                playerId,
                onlinePlayer == null ? shortPlayerId(playerId) : onlinePlayer.getName().getString(),
                onlinePlayer != null,
                status
        );
    }

    private static int friendStatusSort(String status) {
        return switch (status) {
            case "friend" -> 0;
            case "incoming" -> 1;
            case "outgoing" -> 2;
            default -> 3;
        };
    }

    public static String playerDisplayName(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }
        return server.services().nameToIdCache().get(playerId)
                .map(net.minecraft.server.players.NameAndId::name)
                .filter(name -> !name.isBlank())
                .orElseGet(() -> shortPlayerId(playerId));
    }

    private static String shortPlayerId(UUID playerId) {
        String id = playerId.toString();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static List<NexusSpaceUnitRecord> visibleDiscoveredUnits(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return units(server).getVisibleDiscoveredUnits(player.getUUID(), discovery(server), friends(server));
    }

    private static void disableLodestoneAt(ServerLevel level, BlockPos pos) {
        NexusSpaceUnitSavedData units = units(level.getServer());
        Optional<NexusSpaceUnitRecord> previous = units.getLodestone(level.dimension(), pos);
        boolean disabled = units.disableLodestone(level.dimension(), pos, level.getGameTime());
        if (disabled) {
            previous.filter(NexusSpaceUnitAuthority::isPublicLodestone)
                    .ifPresent(unit -> publishPublicSpaceUnitUpdate(
                            "server",
                            "公開磁石已被移除：" + unit.name()
                    ));
        }
    }

    private static void disableMissingLodestone(MinecraftServer server, NexusSpaceUnitRecord unit) {
        if (!unit.isLodestoneAnchor()) {
            return;
        }

        ServerLevel level = server.getLevel(unit.dimension());
        if (level != null && !level.getBlockState(unit.pos()).is(Blocks.LODESTONE)) {
            boolean disabled = units(server).disableLodestone(unit.dimension(), unit.pos(), level.getGameTime());
            if (disabled && isPublicLodestone(unit)) {
                publishPublicSpaceUnitUpdate("server", "公開磁石已失效：" + unit.name());
            }
        }
    }

    private static void sendPublicLodestoneVisibilityUpdate(ServerPlayer player, NexusSpaceUnitRecord previous, NexusSpaceUnitRecord updated) {
        if (!previous.isLodestoneAnchor() || !updated.isLodestoneAnchor() || previous.visibility() == updated.visibility()) {
            return;
        }
        boolean wasPublic = isPublicLodestone(previous);
        boolean isPublic = isPublicLodestone(updated);
        if (!wasPublic && isPublic) {
            publishPublicSpaceUnitUpdate(
                    player.getName().getString(),
                    player.getName().getString() + " 公開了磁石：" + updated.name()
            );
        } else if (wasPublic && !isPublic) {
            publishPublicSpaceUnitUpdate(
                    player.getName().getString(),
                    player.getName().getString() + " 取消公開磁石：" + updated.name()
            );
        }
    }

    private static boolean isPublicLodestone(NexusSpaceUnitRecord unit) {
        return unit.isLodestoneAnchor()
                && unit.status() == SpaceUnitStatus.ACTIVE
                && unit.visibility() == SpaceUnitVisibility.PUBLIC;
    }

    private static void publishDeathBackpackRecovered(String playerName) {
        TotemEventBus.publish(new DeathBackpackRecoveredEvent(playerName));
    }

    private static void publishPublicSpaceUnitUpdate(String actor, String message) {
        TotemEventBus.publish(new SpaceUnitPublicUpdateEvent(actor, message));
    }

    private static boolean confirmPendingLodestoneRegistration(
            ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            TeleportInterfaceItemResolver.RegistrationInput input,
            BlockPos pos,
            SpaceStructureSnapshot preview) {
        UUID playerId = player.getUUID();
        long gameTime = level.getServer().overworld().getGameTime();
        PendingLodestoneRegistration pending = pendingLodestoneRegistrations.get(playerId);
        if (pending != null && pending.matches(level.dimension(), pos, hand, input, gameTime)
                && pending.matchesHeld(player)) {
            pendingLodestoneRegistrations.remove(playerId);
            return true;
        }
        if (pending != null) pendingLodestoneRegistrations.remove(playerId);

        pendingLodestoneRegistrations.put(playerId, new PendingLodestoneRegistration(
                playerId,
                level.dimension(),
                pos.immutable(),
                hand,
                input.type(),
                input.mapId(),
                input.boundUnitId(),
                gameTime + LODESTONE_REGISTRATION_CONFIRM_TICKS
        ));
        sendRegistrationPreview(player, level, pos, preview);
        notify(player, Component.translatable(
                "message.deadrecall.space_unit.registration_preview",
                preview.tier(),
                Math.round(preview.resonance() * 100.0D),
                Math.round(preview.completeness() * 100.0D),
                Math.round(preview.wear() * 100.0D)
        ));
        notify(player, Component.translatable(
                "message.deadrecall.space_unit.registration_confirm",
                seconds(LODESTONE_REGISTRATION_CONFIRM_TICKS)
        ));
        return false;
    }

    private static void sendRegistrationPreview(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            SpaceStructureSnapshot preview) {
        ServerPlayNetworking.send(player, new SpaceUnitRegistrationPreviewPayload(
                level.dimension().identifier().toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                preview.tier(),
                (int) Math.round(preview.resonance() * 100.0D),
                (int) Math.round(preview.completeness() * 100.0D),
                (int) Math.round(preview.wear() * 100.0D),
                seconds(LODESTONE_REGISTRATION_CONFIRM_TICKS)
        ));
    }

    public static void confirmLodestoneRegistration(
            ServerPlayer player,
            String dimensionId,
            int x,
            int y,
            int z) {
        MinecraftServer server = player.level().getServer();
        UUID playerId = player.getUUID();
        if (player.isSpectator()) {
            pendingLodestoneRegistrations.remove(playerId);
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }
        long gameTime = server.overworld().getGameTime();
        BlockPos pos = new BlockPos(x, y, z);
        PendingLodestoneRegistration pending = pendingLodestoneRegistrations.get(playerId);
        if (pending == null
                || pending.isExpired(gameTime)
                || !pending.matchesDimensionId(dimensionId)
                || !pending.pos().equals(pos)) {
            pendingLodestoneRegistrations.remove(playerId);
            notify(player, Component.translatable("message.deadrecall.space_unit.registration_expired"));
            return;
        }

        ServerLevel level = server.getLevel(pending.dimension());
        if (level == null || !level.isLoaded(pos)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.manage_unloaded"));
            return;
        }
        if (!player.level().dimension().equals(pending.dimension()) || !isValidBlockInteraction(player, pos)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(Blocks.LODESTONE)) {
            pendingLodestoneRegistrations.remove(playerId);
            notify(player, Component.translatable("message.deadrecall.space_unit.manage_missing"));
            return;
        }

        if (!pending.matchesHeld(player)) {
            pendingLodestoneRegistrations.remove(playerId);
            notify(player, Component.translatable("message.deadrecall.space_unit.registration_item_changed"));
            return;
        }

        NexusSpaceUnitSavedData units = units(server);
        Optional<NexusSpaceUnitRecord> existing = units.getLodestone(level.dimension(), pos);
        if (existing.isPresent() && !canView(player, existing.get())) {
            pendingLodestoneRegistrations.remove(playerId);
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }

        TeleportInterfaceItemResolver.RegistrationInput pendingInput = pending.input();
        ItemStack heldStack = player.getItemInHand(pending.hand());
        if (!isExactBindingInput(player, pending.hand(), heldStack, pendingInput)) {
            pendingLodestoneRegistrations.remove(playerId);
            notify(player, Component.translatable("message.deadrecall.space_unit.registration_item_changed"));
            return;
        }
        NexusSpaceUnitRecord unit = existing.orElseGet(() -> units.getOrCreateLodestone(level, pos, player));
        pendingLodestoneRegistrations.remove(playerId);
        if (!bindInterface(player, pending.hand(), heldStack, pendingInput, level, pos, unit.id())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.registration_item_changed"));
            return;
        }
        level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        notify(player, Component.translatable(
                existing.isPresent()
                        ? "message.deadrecall.space_unit.bound"
                        : "message.deadrecall.space_unit.registered",
                unit.name()
        ));
    }

    private static InteractionResult handlePlayerInterfaceUse(ServerPlayer player, ServerPlayer target) {
        if (player.getUUID().equals(target.getUUID())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.friend_self"));
            return InteractionResult.SUCCESS;
        }

        NexusFriendSavedData.FriendActionResult result = friends(player.level().getServer())
                .inviteOrAccept(player.getUUID(), target.getUUID());
        switch (result) {
            case ACCEPTED -> {
                notify(player, Component.translatable("message.deadrecall.space_unit.friend_added", target.getName()));
                notify(target, Component.translatable("message.deadrecall.space_unit.friend_added", player.getName()));
            }
            case INVITED -> {
                notify(player, Component.translatable("message.deadrecall.space_unit.friend_invite_sent", target.getName()));
                notify(target, Component.translatable("message.deadrecall.space_unit.friend_invite_received", player.getName()));
            }
            case PENDING -> notify(player, Component.translatable("message.deadrecall.space_unit.friend_invite_pending", target.getName()));
            case ALREADY_FRIENDS -> notify(player, Component.translatable("message.deadrecall.space_unit.friend_already", target.getName()));
            case INVALID -> notify(player, Component.translatable("message.deadrecall.space_unit.friend_invalid"));
        }
        return InteractionResult.SUCCESS;
    }

    static InteractionResult handlePlayerInterfaceActivation(
            ServerPlayer player,
            ServerPlayer target,
            InteractionHand hand,
            TeleportInterfaceItemResolver.ResolvedInterface expectedInterface) {
        if (player == null || target == null || hand == null || expectedInterface == null
                || player.isSpectator() || !expectedInterface.type().canManageFriends()) {
            return InteractionResult.SUCCESS;
        }
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> held =
                TeleportInterfaceItemResolver.resolve(player, hand);
        if (held.isEmpty() || !held.get().equals(expectedInterface)
                || expectedInterface.boundUnitId() == null
                || !validateBoundInterfaceSource(player, expectedInterface.boundUnitId(), true)) {
            return InteractionResult.SUCCESS;
        }
        return handlePlayerInterfaceUse(player, target);
    }

    private static InteractionResult handleLodestoneUse(
            ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            ItemStack stack,
            TeleportInterfaceItemResolver.RegistrationInput input,
            BlockPos pos) {
        if (!isValidBlockInteraction(player, pos)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.too_far"));
            return InteractionResult.SUCCESS;
        }

        NexusSpaceUnitSavedData units = units(level.getServer());
        Optional<NexusSpaceUnitRecord> existing = units.getLodestone(level.dimension(), pos);
        NexusSpaceUnitRecord unit;
        boolean created = existing.isEmpty();
        if (existing.isPresent()) {
            unit = existing.get();
            if (!canView(player, unit)) {
                notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
                return InteractionResult.SUCCESS;
            }
        } else {
            if (input.type() == TeleportInterfaceItemResolver.RegistrationInputType.NEXUS_MAP) {
                notify(player, Component.translatable("message.deadrecall.space_unit.interface.map_source_mismatch"));
                return InteractionResult.SUCCESS;
            }
            SpaceStructureSnapshot preview = units.previewLodestoneStructure(level, pos);
            if (!confirmPendingLodestoneRegistration(player, level, hand, input, pos, preview)) {
                return InteractionResult.SUCCESS;
            }
            if (!isExactBindingInput(player, hand, stack, input)) {
                notify(player, Component.translatable("message.deadrecall.space_unit.registration_item_changed"));
                return InteractionResult.SUCCESS;
            }
            unit = units.getOrCreateLodestone(level, pos, player);
        }

        if (input.type() == TeleportInterfaceItemResolver.RegistrationInputType.NEXUS_MAP
                && !unit.id().equals(input.boundUnitId())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.interface.map_source_mismatch"));
            return InteractionResult.SUCCESS;
        }
        if (!bindInterface(player, hand, stack, input, level, pos, unit.id())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.registration_item_changed"));
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (created) {
            notify(player, Component.translatable("message.deadrecall.space_unit.registered", unit.name()));
            return InteractionResult.SUCCESS;
        }

        if (!discovery(level.getServer()).hasDiscovered(player.getUUID(), unit.id())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.bound_explore_to_open", unit.name()));
            return InteractionResult.SUCCESS;
        }

        openLodestoneMap(player, hand, unit.id());
        return InteractionResult.SUCCESS;
    }

    static InteractionResult handleLodestoneActivation(
            ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            BlockPos pos,
            TeleportInterfaceItemResolver.ResolvedInterface expectedInterface) {
        if (player == null || player.isSpectator() || level == null || hand == null || pos == null || expectedInterface == null) {
            return InteractionResult.SUCCESS;
        }
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> held =
                TeleportInterfaceItemResolver.resolve(player, hand);
        if (held.isEmpty() || !held.get().equals(expectedInterface)
                || expectedInterface.boundUnitId() == null
                || !expectedInterface.type().canDiscover()
                || !validateBoundInterfaceSource(player, expectedInterface.boundUnitId(), true)) {
            return InteractionResult.SUCCESS;
        }
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.LODESTONE)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.manage_missing"));
            return InteractionResult.SUCCESS;
        }
        if (!isValidBlockInteraction(player, pos)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.too_far"));
            return InteractionResult.SUCCESS;
        }

        NexusSpaceUnitSavedData units = units(level.getServer());
        Optional<NexusSpaceUnitRecord> unit = units.getLodestone(level.dimension(), pos);
        if (unit.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.register_first"));
            return InteractionResult.SUCCESS;
        }

        NexusSpaceUnitRecord record = unit.get();
        if (!canView(player, record)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return InteractionResult.SUCCESS;
        }

        boolean changed = discovery(level.getServer()).markDiscovered(player.getUUID(), record.id());
        notify(player, Component.translatable(changed
                ? "message.deadrecall.space_unit.discovered"
                : "message.deadrecall.space_unit.already_discovered",
                record.name()));
        return InteractionResult.SUCCESS;
    }

    private static void openBoundInterface(
            ServerPlayer player,
            InteractionHand hand,
            TeleportInterfaceItemResolver.ResolvedInterface resolved) {
        UUID sourceUnitId = resolved.boundUnitId();
        if (sourceUnitId == null || !validateBoundInterfaceSource(player, sourceUnitId, true)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_bound_interface"));
            return;
        }

        openLodestoneMap(player, hand, sourceUnitId);
    }

    private static void openLodestoneMap(
            ServerPlayer player,
            InteractionHand hand,
            UUID sourceUnitId) {
        if (establishInterfaceContext(
                player,
                hand,
                SOURCE_TYPE_LODESTONE,
                sourceUnitId
        ).isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_interface"));
            return;
        }
        sendSpaceUnitMap(player, sourceUnitId);
    }

    public static void sendSpaceUnitMap(ServerPlayer player, UUID sourceUnitId) {
        Optional<TeleportInterfaceContext> interfaceContext = requireInterfaceContext(
                player,
                SOURCE_TYPE_LODESTONE,
                sourceUnitId,
                true
        );
        if (interfaceContext.isEmpty()) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData units = units(server);
        Optional<NexusSpaceUnitRecord> sourceUnit = units.get(sourceUnitId);
        if (sourceUnit.isEmpty() || sourceUnit.get().status() != SpaceUnitStatus.ACTIVE) {
            clearInterfaceContext(player.getUUID());
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return;
        }

        NexusSpaceUnitRecord source = sourceUnit.get();
        if (!source.isLodestoneAnchor()) {
            clearInterfaceContext(player.getUUID());
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_lodestone_source"));
            return;
        }
        if (!canView(player, source)) {
            clearInterfaceContext(player.getUUID());
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }

        NexusSpaceDiscoverySavedData discovery = discovery(server);
        if (!discovery.hasDiscovered(player.getUUID(), source.id())) {
            clearInterfaceContext(player.getUUID());
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_unexplored"));
            return;
        }
        if (!isNearSource(player, source)) {
            clearInterfaceContext(player.getUUID());
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_too_far"));
            return;
        }
        if (!player.level().getBlockState(source.pos()).is(Blocks.LODESTONE)) {
            disableMissingLodestone(server, source);
            clearInterfaceContext(player.getUUID());
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return;
        }

        synchronizeHeldVanillaMap(player, interfaceContext.orElseThrow());
        ServerPlayNetworking.send(player, buildMapPayload(player, mapSource(source), visibleDiscoveredUnits(player)));
    }

    /** Sends the held Nexus map through Mojang's normal map-data packet before opening its Screen. */
    private static void synchronizeHeldVanillaMap(
            ServerPlayer player,
            TeleportInterfaceContext interfaceContext) {
        if (interfaceContext.interfaceType() != TeleportInterfaceType.FILLED_MAP
                || interfaceContext.mapId() == null) {
            return;
        }
        MapItemSavedData mapData = MapItem.getSavedData(interfaceContext.mapId(), player.level());
        if (mapData == null) {
            return;
        }
        var updatePacket = mapData.getUpdatePacket(interfaceContext.mapId(), player);
        if (updatePacket != null) {
            player.connection.send(updatePacket);
        }
    }

    private static void completeTeleport(
            ServerPlayer player,
            MapSource source,
            TeleportTarget target,
            TeleportQuote quote,
            TeleportSession session) {
        Component interfaceCancelReason = teleportInterfaceCancelReason(player, session);
        if (interfaceCancelReason != null) {
            routeReservations.release(session.playerId());
            notify(player, interfaceCancelReason);
            return;
        }
        Optional<MapSource> finalSource = resolveMapSource(player, source.type(), source.id(), false, true);
        if (finalSource.isEmpty()) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.source"));
            return;
        }

        Optional<TeleportTarget> finalTarget = resolveTeleportTarget(player, target.id(), false, true);
        if (finalTarget.isEmpty()) {
            routeReservations.release(session.playerId());
            notify(player, targetCancelReason(player, target.type(), target.id()));
            return;
        }

        TeleportQuote finalQuote = calculateTeleportQuote(
                player,
                finalSource.get(),
                finalTarget.get(),
                session.interfaceType(),
                session.mapId()
        );
        if (filledMapSessionQuoteInvalid(session, finalQuote)) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable(
                    "message.deadrecall.space_unit.teleport_cancelled.interface_quote_changed"));
            return;
        }
        if (!finalQuote.canTeleport()) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable(finalQuote.blockedReason()));
            return;
        }
        if (!routeReservations.isStillValid(
                session.playerId(),
                routeReservationEndpoints(player, finalSource.get(), finalTarget.get()),
                player.level().getGameTime())) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.route_busy"));
            return;
        }

        ServerLevel targetLevel = player.level().getServer().getLevel(finalTarget.get().dimension());
        if (targetLevel == null) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
            return;
        }

        NexusSafeLanding.Search search = NexusSafeLanding.begin(
                targetLevel,
                finalTarget.get().pos(),
                finalTarget.get().lodestoneAnchor(),
                finalQuote.maxHorizontalDeviation(),
                targetLevel.getRandom(),
                finalTarget.get().type() == SpaceUnitType.PLAYER
        );
        LandingSearchSession previous = landingSearchSessions.put(
                player.getUUID(),
                new LandingSearchSession(session, search)
        );
        if (previous != null) {
            previous.search().close();
        }
    }

    private static void tickLandingSearchSessions(MinecraftServer server) {
        Iterator<Map.Entry<UUID, LandingSearchSession>> iterator =
                landingSearchSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LandingSearchSession> entry = iterator.next();
            LandingSearchSession pending = entry.getValue();
            TeleportSession session = pending.teleport();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (player == null) {
                iterator.remove();
                pending.search().close();
                routeReservations.release(session.playerId());
                continue;
            }

            Component cancelReason = teleportCancelReason(player, session);
            if (cancelReason != null) {
                iterator.remove();
                pending.search().close();
                routeReservations.release(session.playerId());
                notify(player, cancelReason);
                continue;
            }

            Optional<MapSource> source =
                    resolveMapSource(player, session.sourceType(), session.sourceUnitId(), false);
            if (source.isEmpty()) {
                iterator.remove();
                pending.search().close();
                routeReservations.release(session.playerId());
                notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.source"));
                continue;
            }

            Optional<TeleportTarget> target =
                    resolveTeleportTarget(player, session.targetUnitId(), false);
            if (target.isEmpty()) {
                iterator.remove();
                pending.search().close();
                routeReservations.release(session.playerId());
                notify(player, targetCancelReason(player, session.targetType(), session.targetUnitId()));
                continue;
            }

            TeleportQuote quote = calculateTeleportQuote(
                    player,
                    source.get(),
                    target.get(),
                    session.interfaceType(),
                    session.mapId()
            );
            if (filledMapSessionQuoteInvalid(session, quote)) {
                iterator.remove();
                pending.search().close();
                routeReservations.release(session.playerId());
                notify(player, Component.translatable(
                        "message.deadrecall.space_unit.teleport_cancelled.interface_quote_changed"));
                continue;
            }
            if (!quote.canTeleport()) {
                iterator.remove();
                pending.search().close();
                routeReservations.release(session.playerId());
                notify(player, Component.translatable(quote.blockedReason()));
                continue;
            }

            ServerLevel targetLevel = server.getLevel(target.get().dimension());
            if (targetLevel == null) {
                iterator.remove();
                pending.search().close();
                routeReservations.release(session.playerId());
                notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
                continue;
            }

            BlockPos currentAnchor = landingAnchor(target.get());
            int currentRadius = clamp(
                    quote.maxHorizontalDeviation(),
                    0,
                    NexusSafeLanding.MAX_HORIZONTAL_RADIUS
            );
            if (pending.search().level() != targetLevel
                    || !pending.search().anchor().equals(currentAnchor)
                    || pending.search().radius() != currentRadius) {
                pending.search().close();
                entry.setValue(new LandingSearchSession(
                        session,
                        NexusSafeLanding.begin(
                                targetLevel,
                                target.get().pos(),
                                target.get().lodestoneAnchor(),
                                currentRadius,
                                targetLevel.getRandom(),
                                target.get().type() == SpaceUnitType.PLAYER
                        )
                ));
                continue;
            }

            NexusSafeLanding.Progress progress = pending.search().advance();
            if (progress.state() == NexusSafeLanding.State.SEARCHING) {
                continue;
            }

            iterator.remove();
            pending.search().close();
            if (progress.state() == NexusSafeLanding.State.EXHAUSTED) {
                routeReservations.release(session.playerId());
                notify(player, Component.translatable(
                        "message.deadrecall.space_unit.teleport_cancelled.no_landing"));
                continue;
            }
            finishTeleport(player, session, progress.landing().orElseThrow());
        }
    }

    private static void finishTeleport(
            ServerPlayer player,
            TeleportSession session,
            BlockPos landingPos) {
        Component interfaceCancelReason = teleportInterfaceCancelReason(player, session);
        if (interfaceCancelReason != null) {
            routeReservations.release(session.playerId());
            notify(player, interfaceCancelReason);
            return;
        }

        Optional<MapSource> finalSource =
                resolveMapSource(player, session.sourceType(), session.sourceUnitId(), false, true);
        if (finalSource.isEmpty()) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.source"));
            return;
        }
        Optional<TeleportTarget> finalTarget =
                resolveTeleportTarget(player, session.targetUnitId(), false, true);
        if (finalTarget.isEmpty()) {
            routeReservations.release(session.playerId());
            notify(player, targetCancelReason(player, session.targetType(), session.targetUnitId()));
            return;
        }

        TeleportQuote finalQuote = calculateTeleportQuote(
                player,
                finalSource.get(),
                finalTarget.get(),
                session.interfaceType(),
                session.mapId()
        );
        if (filledMapSessionQuoteInvalid(session, finalQuote)) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable(
                    "message.deadrecall.space_unit.teleport_cancelled.interface_quote_changed"));
            return;
        }
        if (!finalQuote.canTeleport()) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable(finalQuote.blockedReason()));
            return;
        }
        if (!routeReservations.isStillValid(
                session.playerId(),
                routeReservationEndpoints(player, finalSource.get(), finalTarget.get()),
                player.level().getGameTime())) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.route_busy"));
            return;
        }

        ServerLevel targetLevel = player.level().getServer().getLevel(finalTarget.get().dimension());
        BlockPos finalAnchor = landingAnchor(finalTarget.get());
        int radius = clamp(
                finalQuote.maxHorizontalDeviation(),
                0,
                NexusSafeLanding.MAX_HORIZONTAL_RADIUS
        );
        if (targetLevel == null
                || Math.max(
                        Math.abs(landingPos.getX() - finalAnchor.getX()),
                        Math.abs(landingPos.getZ() - finalAnchor.getZ())
                ) > radius
                || !NexusSafeLanding.isSafeLoaded(targetLevel, landingPos)) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.no_landing"));
            return;
        }

        if (!deductTeleportCost(player, finalQuote)) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.cost"));
            return;
        }

        boolean teleported = player.teleportTo(
                targetLevel,
                landingPos.getX() + 0.5D,
                landingPos.getY(),
                landingPos.getZ() + 0.5D,
                Relative.DELTA,
                randomizedYaw(player, finalQuote, targetLevel.getRandom()),
                player.getXRot(),
                false
        );
        if (!teleported) {
            routeReservations.release(session.playerId());
            notify(player, Component.translatable(
                    "message.deadrecall.space_unit.teleport_cancelled.generic"));
            return;
        }
        ItemStack completedInterface = player.getItemInHand(session.interactionHand());
        if (NexusSoulboundTeleportItem.bindAfterSuccessfulTeleport(player, completedInterface)) {
            notify(player, Component.translatable(
                    "message.deadrecall.space_unit.soulbound_updated",
                    completedInterface.getHoverName()
            ));
        }
        targetLevel.playSound(null, landingPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.85F, 1.1F);
        applyArrivalDamage(player, targetLevel, finalQuote, targetLevel.getRandom());
        applyStructureWear(player, finalSource.get(), finalTarget.get(), finalQuote, targetLevel.getRandom());
        routeReservations.scheduleRecovery(session.playerId(), targetLevel.getGameTime());
        notify(player, Component.translatable("message.deadrecall.space_unit.teleport_completed", finalTarget.get().name()));
    }

    private static Optional<MapSource> resolveMapSource(ServerPlayer player, String sourceType, UUID sourceUnitId, boolean notifyFailure) {
        return resolveMapSource(player, sourceType, sourceUnitId, notifyFailure, false);
    }

    private static Optional<MapSource> resolveMapSource(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            boolean notifyFailure,
            boolean rescanStructure) {
        if (!SOURCE_TYPE_LODESTONE.equals(sourceType) || sourceUnitId == null) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return Optional.empty();
        }

        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData units = units(server);
        Optional<NexusSpaceUnitRecord> sourceUnit = units.get(sourceUnitId);
        if (sourceUnit.isEmpty() || sourceUnit.get().status() != SpaceUnitStatus.ACTIVE) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return Optional.empty();
        }

        NexusSpaceUnitRecord source = sourceUnit.get();
        if (!source.isLodestoneAnchor()) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_need_lodestone_source"));
            return Optional.empty();
        }
        if (!canView(player, source)) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return Optional.empty();
        }
        if (!discovery(server).hasDiscovered(player.getUUID(), source.id())) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_unexplored"));
            return Optional.empty();
        }
        if (!isNearSource(player, source)) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_too_far"));
            return Optional.empty();
        }
        if (!player.level().getBlockState(source.pos()).is(Blocks.LODESTONE)) {
            disableMissingLodestone(server, source);
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return Optional.empty();
        }

        if (rescanStructure) {
            ServerLevel sourceLevel = server.getLevel(source.dimension());
            if (sourceLevel == null) {
                notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
                return Optional.empty();
            }
            source = units.rescanLodestone(sourceLevel, source.id()).orElse(source);
        }

        return Optional.of(mapSource(source));
    }

    private static Optional<TeleportTarget> resolveTeleportTarget(ServerPlayer player, UUID targetUnitId, boolean notifyFailure) {
        return resolveTeleportTarget(player, targetUnitId, notifyFailure, false);
    }

    private static Optional<TeleportTarget> resolveTeleportTarget(
            ServerPlayer player,
            UUID targetUnitId,
            boolean notifyFailure,
            boolean rescanStructure) {
        if (targetUnitId == null) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
            return Optional.empty();
        }

        MinecraftServer server = player.level().getServer();
        Optional<NexusSpaceUnitRecord> targetUnit = units(server).get(targetUnitId);
        if (targetUnit.isEmpty()) {
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUnitId);
            if (targetPlayer == null) {
                notifyIfRequested(player, notifyFailure, Component.translatable(
                        PlayerTeleportTargetPolicy.cancellationMessageKey(PlayerTeleportTargetPolicy.State.OFFLINE)));
                return Optional.empty();
            }
            if (targetPlayer.getUUID().equals(player.getUUID())) {
                notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
                return Optional.empty();
            }

            PlayerTeleportTargetPolicy.State targetState = PlayerTeleportTargetPolicy.classify(
                    true,
                    targetPlayer.isAlive(),
                    targetPlayer.isRemoved(),
                    friends(server).areFriends(player.getUUID(), targetPlayer.getUUID())
            );
            if (targetState != PlayerTeleportTargetPolicy.State.AVAILABLE) {
                notifyIfRequested(player, notifyFailure, Component.translatable(
                        PlayerTeleportTargetPolicy.cancellationMessageKey(targetState)));
                return Optional.empty();
            }
            return Optional.of(TeleportTarget.player(targetPlayer));
        }

        if (targetUnit.get().status() != SpaceUnitStatus.ACTIVE) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
            return Optional.empty();
        }

        NexusSpaceUnitRecord target = targetUnit.get();
        if (!canView(player, target)) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return Optional.empty();
        }
        if (!discovery(server).hasDiscovered(player.getUUID(), target.id())) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target_unexplored"));
            return Optional.empty();
        }
        if (target.isLodestoneAnchor()) {
            ServerLevel targetLevel = server.getLevel(target.dimension());
            if (targetLevel == null) {
                notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
                return Optional.empty();
            }
            if (!targetLevel.getBlockState(target.pos()).is(Blocks.LODESTONE)) {
                units(server).disableLodestone(target.dimension(), target.pos(), targetLevel.getGameTime());
                notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
                return Optional.empty();
            }
            if (rescanStructure) {
                target = units(server).rescanLodestone(targetLevel, target.id()).orElse(target);
            }
        }
        return Optional.of(TeleportTarget.unit(target));
    }

    private static Component targetCancelReason(
            ServerPlayer player,
            SpaceUnitType targetType,
            UUID targetId) {
        if (targetType != SpaceUnitType.PLAYER) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target");
        }

        MinecraftServer server = player.level().getServer();
        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetId);
        PlayerTeleportTargetPolicy.State targetState = PlayerTeleportTargetPolicy.classify(
                targetPlayer != null,
                targetPlayer != null && targetPlayer.isAlive(),
                targetPlayer != null && targetPlayer.isRemoved(),
                targetPlayer != null && friends(server).areFriends(player.getUUID(), targetId)
        );
        return Component.translatable(PlayerTeleportTargetPolicy.cancellationMessageKey(targetState));
    }

    private static Component teleportCancelReason(ServerPlayer player, TeleportSession session) {
        if (!player.isAlive() || player.isRemoved()) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.generic");
        }
        Component interfaceReason = teleportInterfaceCancelReason(player, session);
        if (interfaceReason != null) {
            return interfaceReason;
        }
        if (!player.level().dimension().equals(session.startDimension())) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.dimension");
        }
        if (distanceSquared(player.blockPosition(), session.startPos()) > SESSION_MOVE_CANCEL_DISTANCE * SESSION_MOVE_CANCEL_DISTANCE) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.moved");
        }
        return null;
    }

    private static Component teleportInterfaceCancelReason(
            ServerPlayer player,
            TeleportSession session) {
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved =
                TeleportInterfaceItemResolver.resolve(player, session.interactionHand());
        if (resolved.isEmpty()
                || resolved.get().type() != session.interfaceType()
                || !java.util.Objects.equals(resolved.get().mapId(), session.mapId())
                || !java.util.Objects.equals(resolved.get().boundUnitId(), session.boundUnitId())
                || session.boundUnitId() == null
                || !validateBoundInterfaceSource(player, session.boundUnitId(), false)) {
            return Component.translatable(
                    "message.deadrecall.space_unit.teleport_cancelled.interface_item");
        }
        return null;
    }

    private static boolean filledMapSessionQuoteInvalid(
            TeleportSession session,
            TeleportQuote quote) {
        return session.interfaceType() == TeleportInterfaceType.FILLED_MAP
                && ((session.filledMapDataValidAtStart() && !quote.filledMapDataValid())
                || (session.filledMapBonusActiveAtStart() && !quote.interfaceBonusActive()));
    }

    private static boolean deductTeleportCost(ServerPlayer player, TeleportQuote quote) {
        if (player.getAbilities().instabuild) {
            return true;
        }

        if (quote.foodPointsNeeded() > safeFoodPointsAvailable(player) || quote.amethystCost() > countAmethyst(player)) {
            return false;
        }

        FoodData foodData = player.getFoodData();
        foodData.setSaturation(Math.max(0.0F, foodData.getSaturationLevel() - quote.saturationCost()));
        if (quote.hungerCost() > 0) {
            foodData.setFoodLevel(Math.max(MIN_REMAINING_FOOD_LEVEL, foodData.getFoodLevel() - quote.hungerCost()));
        }
        return consumeSafeFood(player, quote.foodPointsNeeded()) && consumeItems(player, Items.AMETHYST_SHARD, quote.amethystCost());
    }

    private static boolean consumeSafeFood(ServerPlayer player, int foodPointsNeeded) {
        int remaining = foodPointsNeeded;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            FoodProperties food = safeFood(stack);
            if (food == null) {
                continue;
            }

            int consumeCount = Math.min(stack.getCount(), (remaining + food.nutrition() - 1) / food.nutrition());
            stack.shrink(consumeCount);
            remaining -= consumeCount * food.nutrition();
        }
        return remaining <= 0;
    }

    private static boolean consumeItems(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        int remaining = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) {
                continue;
            }

            int consumeCount = Math.min(stack.getCount(), remaining);
            stack.shrink(consumeCount);
            remaining -= consumeCount;
        }
        return remaining <= 0;
    }

    /**
     * Finds the nearest safe feet position around an explicit server-owned
     * anchor. Administrative tools use this rather than teleporting directly
     * into an unvalidated death-node block position.
     */
    public static Optional<BlockPos> findSafeLandingNear(ServerLevel level, BlockPos anchor, int maxHorizontalDeviation) {
        if (level == null || anchor == null) {
            return Optional.empty();
        }
        return NexusSafeLanding.findLoaded(level, anchor, false, maxHorizontalDeviation);
    }

    private static float randomizedYaw(ServerPlayer player, TeleportQuote quote, RandomSource random) {
        double spread = Math.min(90.0D,
                quote.maxHorizontalDeviation() * 3.0D + ((1.0D - quote.routeStability()) * 45.0D));
        return player.getYRot() + (float) ((random.nextDouble() * 2.0D - 1.0D) * spread);
    }

    private static void applyArrivalDamage(ServerPlayer player, ServerLevel level, TeleportQuote quote, RandomSource random) {
        if (player.getAbilities().instabuild || quote.damageChancePercent() <= 0) {
            return;
        }

        double chance = Math.min(0.45D, quote.damageChancePercent() / 150.0D);
        if (random.nextDouble() >= chance) {
            return;
        }

        float damage = (float) clamp(
                2.0D + ((1.0D - quote.routeStability()) * 6.0D) + (quote.maxHorizontalDeviation() / 24.0D),
                2.0D,
                8.0D
        );
        if (player.hurtServer(level, level.damageSources().magic(), damage)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_arrival_damage"));
        }
    }

    private static void applyStructureWear(
            ServerPlayer player,
            MapSource source,
            TeleportTarget target,
            TeleportQuote quote,
            RandomSource random) {
        if (quote.structureWearChancePercent() <= 0) {
            return;
        }

        double chance = quote.structureWearChancePercent() / 100.0D;
        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData units = units(server);
        boolean worn = false;
        if (SOURCE_TYPE_LODESTONE.equals(source.type())) {
            ServerLevel sourceLevel = server.getLevel(source.dimension());
            if (sourceLevel != null) {
                worn = units.applyLodestoneWear(sourceLevel, source.id(), chance, random);
            }
        }

        if (target.lodestoneAnchor()) {
            ServerLevel targetLevel = server.getLevel(target.dimension());
            if (targetLevel != null) {
                worn = units.applyLodestoneWear(targetLevel, target.id(), chance, random) || worn;
            }
        }

        if (worn) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_structure_worn"));
        }
    }

    private static BlockPos landingAnchor(TeleportTarget target) {
        return target.lodestoneAnchor() ? target.pos().above() : target.pos();
    }

    private static double distanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int seconds(int ticks) {
        return Math.max(0, (int) Math.ceil(ticks / 20.0D));
    }

    private static void notifyIfRequested(ServerPlayer player, boolean requested, Component message) {
        if (requested) {
            notify(player, message);
        }
    }

    static boolean bindInterface(
            ServerPlayer player,
            InteractionHand hand,
            ItemStack stack,
            TeleportInterfaceItemResolver.RegistrationInput expectedInput,
            ServerLevel level,
            BlockPos pos,
            UUID unitId) {
        if (player == null || hand == null || stack == null || stack.isEmpty()
                || expectedInput == null || level == null || pos == null || unitId == null
                || !isExactBindingInput(player, hand, stack, expectedInput)) return false;

        if (expectedInput.type() == TeleportInterfaceItemResolver.RegistrationInputType.EMPTY_MAP) {
            if (!stack.is(Items.MAP)) return false;
            ItemStack nexusMap = NexusMapLifecycleAuthority.createBoundMap(level, pos, unitId, stack).orElse(null);
            if (nexusMap == null) return false;
            MapId createdMapId = nexusMap.get(DataComponents.MAP_ID);
            if (createdMapId == null) return false;
            MapItemSavedData createdMapData = MapItem.getSavedData(createdMapId, level);
            if (createdMapData == null) return false;

            if (!player.hasInfiniteMaterials()) {
                if (stack.getCount() == 1) player.setItemInHand(hand, nexusMap);
                else {
                    stack.shrink(1);
                    giveOrDrop(player, nexusMap);
                }
            } else giveOrDrop(player, nexusMap);
            return true;
        }

        if (unitId.equals(expectedInput.boundUnitId())) return true;

        if (player.hasInfiniteMaterials()) {
            ItemStack boundCopy = stack.copyWithCount(1);
            if (!NexusInterfaceBinding.write(boundCopy, level, pos, unitId)) return false;
            giveOrDrop(player, boundCopy);
            return true;
        }

        ItemStack targetStack = stack.getCount() > 1 ? stack.copyWithCount(1) : stack;
        if (!NexusInterfaceBinding.write(targetStack, level, pos, unitId)) return false;
        if (targetStack != stack) {
            stack.shrink(1);
            giveOrDrop(player, targetStack);
        }
        return true;
    }

    private static boolean isExactBindingInput(
            ServerPlayer player,
            InteractionHand hand,
            ItemStack stack,
            TeleportInterfaceItemResolver.RegistrationInput expectedInput) {
        if (player == null || hand == null || stack == null || expectedInput == null
                || player.getItemInHand(hand) != stack) return false;
        return TeleportInterfaceItemResolver.resolveRegistrationInput(player, hand)
                .filter(expectedInput::equals)
                .isPresent();
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static SpaceUnitMapPayload buildMapPayload(ServerPlayer player, MapSource source, List<NexusSpaceUnitRecord> visibleUnits) {
        MinecraftServer server = player.level().getServer();
        UUID playerId = player.getUUID();
        TeleportInterfaceContext interfaceContext = currentInterfaceContext(player)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot build a teleport map payload without a valid interface context"));
        TeleportInterfaceType interfaceType = interfaceContext.interfaceType();
        MapId mapId = interfaceContext.mapId();
        MapItemSavedData mapData = interfaceType.hasMapVisualization() && mapId != null
                ? MapItem.getSavedData(mapId, player.level())
                : null;
        List<NexusSpaceUnitRecord> payloadUnits = NexusInterfacePayloadPolicy.selectAuthorizedUnits(
                interfaceType, source.id(), visibleUnits, mapData);
        List<SpaceUnitMapPayload.Entry> entries = new ArrayList<>(
                Math.min(payloadUnits.size(), SpaceUnitMapPayload.MAX_ENTRIES));
        NexusSpaceDiscoverySavedData discoveryData = discovery(server);
        NexusFriendSavedData friendData = friends(server);
        for (NexusSpaceUnitRecord unit : payloadUnits) {
            if (entries.size() >= SpaceUnitMapPayload.MAX_ENTRIES) {
                break;
            }
            TeleportTarget target = TeleportTarget.unit(unit);
            TeleportQuote quote = calculateTeleportQuote(player, source, target, interfaceType, mapId);
            boolean friendShared = !unit.owner().equals(playerId) && friendData.areFriends(playerId, unit.owner());
            entries.add(new SpaceUnitMapPayload.Entry(
                    target.id(),
                    target.type().id(),
                    target.name(),
                    unit.visibility().id(),
                    friendShared,
                    dimensionId(target),
                    target.pos().getX(),
                    target.pos().getY(),
                    target.pos().getZ(),
                    quote.routeStability(),
                    target.tier(),
                    quote.distanceBlocks(),
                    quote.baseFoodCost(),
                    quote.finalFoodCost(),
                    quote.saturationCost(),
                    quote.hungerCost(),
                    quote.foodPointsNeeded(),
                    quote.safeFoodPointsAvailable(),
                    quote.amethystCost(),
                    quote.amethystAvailable(),
                    quote.basePrepareTicks(),
                    quote.prepareTicks(),
                    quote.baseMaxHorizontalDeviation(),
                    quote.maxHorizontalDeviation(),
                    quote.damageChancePercent(),
                    quote.baseStructureWearChancePercent(),
                    quote.structureWearChancePercent(),
                    quote.interfaceBonusActive(),
                    quote.interfaceBonusMessageKey(),
                    discoveryData.isFavorite(playerId, unit.id()),
                    unit.canManage(playerId),
                    unit.owner().equals(playerId),
                    unit.administrators().size(),
                    unit.allowedPlayers().size(),
                    quote.canTeleport(),
                    quote.blockedReason()
            ).withMaterial(materialSummaryFor(player, unit.id())));
        }
        return new SpaceUnitMapPayload(
                source.id(),
                source.type(),
                source.name(),
                dimensionId(source),
                source.pos().getX(),
                source.pos().getY(),
                source.pos().getZ(),
                interfaceType,
                mapId == null ? SpaceUnitMapPayload.NO_MAP_ID : mapId.id(),
                entries,
                materialSummaryFor(player, source.id())
        );
    }

    private static TeleportQuote calculateTeleportQuote(
            ServerPlayer player,
            MapSource source,
            TeleportTarget target,
            TeleportInterfaceType interfaceType,
            MapId mapId) {
        boolean sameDimension = source.dimension().equals(target.dimension());
        boolean sameUnit = SOURCE_TYPE_LODESTONE.equals(source.type()) && source.id().equals(target.id());
        SpaceStructureSnapshot sourceMaterial = materialStructure(player, source.id());
        SpaceStructureSnapshot targetMaterial = materialStructure(player, target.id());
        int materialFoodEfficiency = sourceMaterial.foodEfficiency() + targetMaterial.foodEfficiency();
        int materialPhaseSpeed = sourceMaterial.phaseSpeed() + targetMaterial.phaseSpeed();
        int materialAccuracy = sourceMaterial.arrivalAccuracy() + targetMaterial.arrivalAccuracy();
        int materialTargetLock = sourceMaterial.targetLock() + targetMaterial.targetLock();
        int materialArrivalSafety = sourceMaterial.arrivalSafety() + targetMaterial.arrivalSafety();
        int materialWearResistance = sourceMaterial.wearResistance() + targetMaterial.wearResistance();
        int matchingAffinity = matchingAffinity(source, target, sourceMaterial, targetMaterial, sameDimension);
        int distanceBlocks = sameDimension ? distanceBlocks(source.pos(), target.pos()) : -1;
        double routeStability = sameUnit ? 1.0D : routeStability(source, target, sameDimension, distanceBlocks);
        routeStability = clamp(routeStability + (matchingAffinity / 100.0D), 0.0D, 1.0D);
        int baseFoodCost = sameUnit ? 0 : materialFoodCost(baseFoodCost(target, sameDimension, distanceBlocks), materialFoodEfficiency);
        int amethystAvailable = countAmethyst(player);
        int baseAmethystCost = sameUnit || sameDimension ? 0 : Math.max(MIN_CROSS_DIMENSION_AMETHYST_COST,
                MIN_CROSS_DIMENSION_AMETHYST_COST + (int) Math.ceil((1.0D - routeStability) * 4.0D));
        int sourceCatalystUnits = sourceMaterial.crossDimensionCatalystUnits();
        int targetCatalystUnits = targetMaterial.crossDimensionCatalystUnits();
        int catalystShardChange = AmethystCatalystDiscount.catalystChange(sourceCatalystUnits, targetCatalystUnits);
        int amethystCost = sameUnit || sameDimension ? 0 : materialCatalystCost(
                baseAmethystCost, sourceCatalystUnits, targetCatalystUnits);
        int basePrepareTicks = sameUnit ? 0 : TeleportArrayMaterialAttributes.clamp(
                prepareTicks(target, sameDimension, distanceBlocks, routeStability) - materialPhaseSpeed, 40, 300);
        int baseMaxHorizontalDeviation = sameUnit ? 0 : materialDeviation(
                maxHorizontalDeviation(routeStability), materialAccuracy, materialTargetLock, !sameDimension || distanceBlocks > 0);
        int damageChancePercent = sameUnit ? 0 : TeleportArrayMaterialAttributes.clamp(
                damageChancePercent(source, target, sameDimension, distanceBlocks, routeStability)
                        - materialArrivalSafety - matchingAffinity,
                0,
                60
        );
        FilledMapCoverageStatus mapCoverage = resolveFilledMapCoverage(player, interfaceType, mapId, target);
        TeleportInterfaceQuotePolicy.Quote interfaceQuote = TeleportInterfaceQuotePolicy.specialize(
                interfaceType,
                target.type(),
                player.getUUID().equals(target.ownerId()),
                mapCoverage.targetCovered(),
                baseFoodCost,
                basePrepareTicks,
                baseMaxHorizontalDeviation,
                damageChancePercent
        );
        CostBreakdown cost = calculateCostBreakdown(player, interfaceQuote.foodCost());
        int payableBaseFoodCost = player.getAbilities().instabuild ? 0 : baseFoodCost;
        int payableFinalFoodCost = player.getAbilities().instabuild ? 0 : interfaceQuote.foodCost();
        String blockedReason = blockedReason(source, target, routeStability, cost, amethystCost, amethystAvailable, sameDimension, sameUnit);
        return new TeleportQuote(
                routeStability,
                distanceBlocks,
                payableBaseFoodCost,
                payableFinalFoodCost,
                cost.saturationCost(),
                cost.hungerCost(),
                cost.foodPointsNeeded(),
                cost.safeFoodPointsAvailable(),
                amethystCost,
                amethystAvailable,
                baseAmethystCost,
                sourceCatalystUnits,
                targetCatalystUnits,
                catalystShardChange,
                basePrepareTicks,
                interfaceQuote.prepareTicks(),
                baseMaxHorizontalDeviation,
                interfaceQuote.maxHorizontalDeviation(),
                damageChancePercent,
                TeleportArrayMaterialAttributes.clamp(damageChancePercent - materialWearResistance, 0, 100),
                TeleportArrayMaterialAttributes.clamp(interfaceQuote.structureWearChancePercent() - materialWearResistance, 0, 100),
                interfaceQuote.bonusActive(),
                interfaceQuote.bonusMessageKey(),
                mapCoverage.dataValid(),
                blockedReason.isEmpty(),
                blockedReason
        );
    }

    private static FilledMapCoverageStatus resolveFilledMapCoverage(
            ServerPlayer player,
            TeleportInterfaceType interfaceType,
            MapId mapId,
            TeleportTarget target) {
        if (interfaceType != TeleportInterfaceType.FILLED_MAP || mapId == null) {
            return FilledMapCoverageStatus.NOT_APPLICABLE;
        }
        MapItemSavedData mapData = MapItem.getSavedData(mapId, player.level());
        if (mapData == null) {
            return FilledMapCoverageStatus.UNAVAILABLE;
        }
        boolean covered = FilledMapCoverage.covers(
                mapData.dimension,
                mapData.centerX,
                mapData.centerZ,
                mapData.scale,
                target.dimension(),
                target.pos()
        );
        return new FilledMapCoverageStatus(true, covered);
    }

    private static CostBreakdown calculateCostBreakdown(ServerPlayer player, int baseFoodCost) {
        if (baseFoodCost <= 0 || player.getAbilities().instabuild) {
            return new CostBreakdown(0, 0, 0, safeFoodPointsAvailable(player));
        }

        FoodData foodData = player.getFoodData();
        int saturationCost = Math.min(baseFoodCost, Math.max(0, (int) Math.floor(foodData.getSaturationLevel())));
        int remaining = baseFoodCost - saturationCost;
        int hungerCost = Math.min(remaining, Math.max(0, foodData.getFoodLevel() - MIN_REMAINING_FOOD_LEVEL));
        int foodPointsNeeded = Math.max(0, remaining - hungerCost);
        return new CostBreakdown(saturationCost, hungerCost, foodPointsNeeded, safeFoodPointsAvailable(player));
    }

    private static String blockedReason(
            MapSource source,
            TeleportTarget target,
            double routeStability,
            CostBreakdown cost,
            int amethystCost,
            int amethystAvailable,
            boolean sameDimension,
            boolean sameUnit) {
        if (sameUnit) {
            return "message.deadrecall.space_unit.teleport_blocked.same_source";
        }
        if (routeStability < 0.2D) {
            return "message.deadrecall.space_unit.teleport_blocked.unstable";
        }
        if (!sameDimension && SOURCE_TYPE_LODESTONE.equals(source.type()) && source.tier() < 1) {
            return "message.deadrecall.space_unit.teleport_blocked.source_tier";
        }
        if (!sameDimension && target.lodestoneAnchor() && target.tier() < 1) {
            return "message.deadrecall.space_unit.teleport_blocked.target_tier";
        }
        if (cost.foodPointsNeeded() > cost.safeFoodPointsAvailable()) {
            return "message.deadrecall.space_unit.teleport_blocked.food";
        }
        if (amethystCost > amethystAvailable) {
            return "message.deadrecall.space_unit.teleport_blocked.amethyst";
        }
        return "";
    }

    private static double routeStability(MapSource source, TeleportTarget target, boolean sameDimension, int distanceBlocks) {
        double stability = Math.min(unitStability(source), unitStability(target));
        if (!sameDimension) {
            stability *= 0.65D;
        } else {
            stability *= Math.max(0.55D, 1.0D - (distanceBlocks / 12000.0D));
        }

        stability *= switch (target.type()) {
            case DEATH -> 0.72D;
            case PLAYER -> 0.65D;
            case TEMPORARY -> 0.85D;
            default -> 1.0D;
        };
        if (SOURCE_TYPE_PLAYER.equals(source.type())) {
            stability *= 0.85D;
        }
        return clamp(stability, 0.0D, 1.0D);
    }

    private static double unitStability(MapSource source) {
        return switch (source.unitType()) {
            case PLAYER -> clamp(source.stability(), 0.0D, 1.0D);
            case LODESTONE -> clamp(source.stability(), 0.0D, 1.0D);
            case DEATH -> 0.55D;
            case TEMPORARY -> 0.5D;
            case SYSTEM -> 0.8D;
        };
    }

    private static double unitStability(TeleportTarget target) {
        return switch (target.type()) {
            case LODESTONE -> clamp(target.stability(), 0.0D, 1.0D);
            case DEATH -> 0.55D;
            case PLAYER -> 0.6D;
            case TEMPORARY -> 0.5D;
            case SYSTEM -> 0.8D;
        };
    }

    private static int baseFoodCost(TeleportTarget target, boolean sameDimension, int distanceBlocks) {
        int cost = sameDimension
                ? Math.max(1, (distanceBlocks + SAME_DIMENSION_FOOD_BLOCKS_PER_POINT - 1) / SAME_DIMENSION_FOOD_BLOCKS_PER_POINT)
                : 6;
        cost += switch (target.type()) {
            case DEATH -> 4;
            case PLAYER -> 4;
            case TEMPORARY -> 2;
            default -> 0;
        };
        if (!sameDimension) {
            cost += 4;
        }
        return clamp(cost, 1, MAX_BASE_FOOD_COST);
    }

    private static int materialFoodCost(int baseCost, int foodEfficiency) {
        if (baseCost <= 0) {
            return 0;
        }
        int multiplier = TeleportArrayMaterialAttributes.clamp(100 - foodEfficiency, 50, 200);
        return Math.max(1, (int) Math.ceil(baseCost * multiplier / 100.0D));
    }

    private static int materialDeviation(int baseline, int accuracy, int targetLock, boolean lockApplies) {
        return TeleportArrayMaterialAttributes.clamp(baseline - accuracy - (lockApplies ? targetLock : 0), 1, 96);
    }

    private static int materialCatalystCost(int baseCost, int sourceUnits, int targetUnits) {
        return AmethystCatalystDiscount.finalCost(baseCost, sourceUnits, targetUnits);
    }

    private static Optional<RepairMaterial> findRepairMaterial(
            ServerPlayer player,
            String requiredFamily,
            int requiredCount) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getCount() < requiredCount || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            BlockState state = blockItem.getBlock().defaultBlockState();
            TeleportArrayMaterialProfile profile = TeleportArrayMaterialProfiles.profileFor(state);
            if (profile.validStructureMaterial()
                    && !NexusSpaceUnitSavedData.isWornStructureBlock(state)
                    && profile.family().equals(requiredFamily)) {
                return Optional.of(new RepairMaterial(stack, blockItem.getBlock()));
            }
        }
        return Optional.empty();
    }

    private static SpaceStructureSnapshot materialStructure(ServerPlayer player, UUID unitId) {
        if (unitId == null) {
            return SpaceStructureSnapshot.EMPTY;
        }
        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData data = units(server);
        Optional<NexusSpaceUnitRecord> unit = data.get(unitId);
        if (unit.isEmpty()) {
            return SpaceStructureSnapshot.EMPTY;
        }
        NexusSpaceUnitRecord record = unit.get();
        ServerLevel level = server.getLevel(record.dimension());
        if (record.isLodestoneAnchor()
                && (record.structure().materialSnapshotStale()
                || record.structure().materialProfileRevision() != TeleportArrayMaterialProfiles.revision()
                || !record.structure().teleportArrayExpansionModeKnown()
                || (level != null && record.structure().teleportArrayExpansionModeCode()
                != NexusTeleportArrayExpansionRules.mode(level).snapshotCode()))) {
            if (level == null || !level.isLoaded(record.pos())) {
                return SpaceStructureSnapshot.EMPTY;
            }
            return data.rescanLodestone(level, record.id())
                    .map(NexusSpaceUnitRecord::structure)
                    .orElse(SpaceStructureSnapshot.EMPTY);
        }
        return record.structure();
    }

    private static SpaceUnitMapPayload.MaterialSummary materialSummaryFor(ServerPlayer player, UUID unitId) {
        SpaceStructureSnapshot structure = materialStructure(player, unitId);
        SpaceUnitMapPayload.MaterialSummary summary = SpaceUnitMapPayload.MaterialSummary.from(structure);
        if (unitId == null) {
            return summary;
        }
        MinecraftServer server = player.level().getServer();
        Optional<NexusSpaceUnitRecord> unit = units(server).get(unitId);
        if (unit.isEmpty() || !unit.get().isLodestoneAnchor() || !unit.get().canManage(player.getUUID())) {
            return summary;
        }
        ServerLevel level = server.getLevel(unit.get().dimension());
        if (level == null) {
            return summary;
        }
        List<SpaceUnitMapPayload.MaintenanceTarget> targets = new ArrayList<>();
        for (BlockPos position : units(server).wornLodestoneStructureBlocks(level, unitId)) {
            String family = TeleportArrayMaterialProfiles.profileFor(level.getBlockState(position)).family();
            targets.add(new SpaceUnitMapPayload.MaintenanceTarget(
                    position.getX(), position.getY(), position.getZ(), family));
        }
        return summary.withMaintenance(
                TeleportRouteLoadPolicy.maintenanceItemCost(structure.maintenanceEfficiency()),
                targets
        );
    }

    private static List<TeleportRouteReservationStore.Endpoint> routeReservationEndpoints(
            ServerPlayer player,
            MapSource source,
            TeleportTarget target) {
        List<TeleportRouteReservationStore.Endpoint> endpoints = new ArrayList<>(2);
        if (SOURCE_TYPE_LODESTONE.equals(source.type())) {
            endpoints.add(routeReservationEndpoint(player, source.id()));
        }
        if (target.lodestoneAnchor()
                && endpoints.stream().noneMatch(endpoint -> endpoint.unitId().equals(target.id()))) {
            endpoints.add(routeReservationEndpoint(player, target.id()));
        }
        return endpoints;
    }

    private static TeleportRouteReservationStore.Endpoint routeReservationEndpoint(
            ServerPlayer player,
            UUID unitId) {
        SpaceStructureSnapshot material = materialStructure(player, unitId);
        return new TeleportRouteReservationStore.Endpoint(
                unitId,
                TeleportRouteLoadPolicy.slotCapacity(material.routeLoadCapacity()),
                TeleportRouteLoadPolicy.recoveryTicks(material.cooldownRecovery())
        );
    }

    private static int matchingAffinity(
            MapSource source,
            TeleportTarget target,
            SpaceStructureSnapshot sourceMaterial,
            SpaceStructureSnapshot targetMaterial,
            boolean sameDimension) {
        if (sameDimension) {
            return 0;
        }
        return sourceMaterial.dimensionAffinity().getOrDefault(target.dimension().identifier().toString(), 0)
                + targetMaterial.dimensionAffinity().getOrDefault(source.dimension().identifier().toString(), 0);
    }

    private static int prepareTicks(TeleportTarget target, boolean sameDimension, int distanceBlocks, double routeStability) {
        int ticks = 60 + (int) Math.round((1.0D - routeStability) * 80.0D);
        ticks += sameDimension ? Math.min(120, Math.max(0, distanceBlocks / 32)) : 100;
        ticks += switch (target.type()) {
            case DEATH -> 40;
            case PLAYER -> 50;
            case TEMPORARY -> 20;
            default -> 0;
        };
        return clamp(ticks, 40, 300);
    }

    private static int maxHorizontalDeviation(double routeStability) {
        if (routeStability >= 0.95D) {
            return 1;
        }
        if (routeStability >= 0.8D) {
            return 3;
        }
        if (routeStability >= 0.6D) {
            return 8;
        }
        if (routeStability >= 0.4D) {
            return 20;
        }
        if (routeStability >= 0.2D) {
            return 48;
        }
        return 96;
    }

    private static int damageChancePercent(
            MapSource source,
            TeleportTarget target,
            boolean sameDimension,
            int distanceBlocks,
            double routeStability) {
        double chance = (1.0D - routeStability) * 20.0D;
        chance += sameDimension ? Math.min(8.0D, distanceBlocks / 1500.0D) : 10.0D;
        chance += Math.max(0.0D, 1.0D - source.stability()) * 6.0D;
        chance += target.wear() * 18.0D;
        chance += switch (target.type()) {
            case DEATH -> 8.0D;
            case PLAYER -> 10.0D;
            case TEMPORARY -> 4.0D;
            default -> 0.0D;
        };
        if (SOURCE_TYPE_LODESTONE.equals(source.type()) && source.tier() >= 2) {
            chance -= 3.0D;
        }
        if (target.lodestoneAnchor() && target.tier() >= 2) {
            chance -= 3.0D;
        }
        return clamp((int) Math.round(chance), 0, 60);
    }

    private static int safeFoodPointsAvailable(ServerPlayer player) {
        int points = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            FoodProperties food = safeFood(stack);
            if (food != null) {
                points += food.nutrition() * stack.getCount();
            }
        }
        return points;
    }

    private static FoodProperties safeFood(ItemStack stack) {
        if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_DATA)) {
            return null;
        }

        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || food.nutrition() <= 0) {
            return null;
        }

        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable != null && !consumable.onConsumeEffects().isEmpty()) {
            return null;
        }
        return food;
    }

    private static int countAmethyst(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.AMETHYST_SHARD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int distanceBlocks(BlockPos from, BlockPos to) {
        long dx = (long) from.getX() - to.getX();
        long dy = (long) from.getY() - to.getY();
        long dz = (long) from.getZ() - to.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static Optional<TeleportInterfaceContext> establishInterfaceContext(
            ServerPlayer player,
            InteractionHand hand,
            String sourceType,
            UUID sourceId) {
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved =
                TeleportInterfaceItemResolver.resolve(player, hand);
        if (resolved.isEmpty() || !SOURCE_TYPE_LODESTONE.equals(sourceType) || sourceId == null) {
            return Optional.empty();
        }
        if (!sourceId.equals(resolved.get().boundUnitId())
                || !validateBoundInterfaceSource(player, sourceId, false)) return Optional.empty();

        long gameTime = player.level().getServer().overworld().getGameTime();
        TeleportInterfaceContext context = new TeleportInterfaceContext(
                player.getUUID(),
                resolved.get().type(),
                sourceType,
                sourceId,
                hand,
                resolved.get().mapId(),
                resolved.get().boundUnitId(),
                gameTime,
                gameTime + TELEPORT_INTERFACE_CONTEXT_TICKS
        );
        teleportInterfaceContexts.put(player.getUUID(), context);
        return Optional.of(context);
    }

    static Optional<TeleportInterfaceContext> currentInterfaceContext(ServerPlayer player) {
        TeleportInterfaceContext context = teleportInterfaceContexts.get(player.getUUID());
        if (context == null) {
            return Optional.empty();
        }
        return requireInterfaceContext(
                player,
                context.sourceType(),
                context.sourceId(),
                false
        );
    }

    static void clearInterfaceContext(UUID playerId) {
        teleportInterfaceContexts.remove(playerId);
    }

    private static Optional<TeleportInterfaceContext> requireInterfaceContext(
            ServerPlayer player,
            String sourceType,
            UUID sourceId,
            boolean notifyFailure) {
        TeleportInterfaceContext context = teleportInterfaceContexts.get(player.getUUID());
        long gameTime = player.level().getServer().overworld().getGameTime();
        if (context == null
                || !context.matchesSource(sourceType, sourceId)
                || context.isExpired(gameTime)
                || !context.isStillHeldBy(player)
                || (SOURCE_TYPE_LODESTONE.equals(context.sourceType())
                && !validateBoundInterfaceSource(player, context.boundUnitId(), false))) {
            teleportInterfaceContexts.remove(player.getUUID());
            notifyIfRequested(player, notifyFailure, Component.translatable(
                    "message.deadrecall.space_unit.interface.context_invalid"));
            return Optional.empty();
        }
        return Optional.of(context);
    }

    static boolean requireManagementCapability(
            ServerPlayer player,
            String sourceType,
            UUID sourceId) {
        Optional<TeleportInterfaceContext> context =
                requireInterfaceContext(player, sourceType, sourceId, true);
        if (context.isEmpty()) {
            return false;
        }
        if (!context.get().interfaceType().canManage()) {
            notify(player, Component.translatable(
                    "message.deadrecall.space_unit.interface.management_unavailable"));
            return false;
        }
        UUID boundUnitId = context.get().boundUnitId();
        boolean canManage = boundUnitId != null && units(player.level().getServer()).get(boundUnitId)
                .filter(unit -> unit.isLodestoneAnchor() && unit.status() == SpaceUnitStatus.ACTIVE)
                .filter(unit -> unit.canManage(player.getUUID()))
                .isPresent();
        if (!canManage) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
        }
        return canManage;
    }

    static boolean requireManagementCapability(ServerPlayer player) {
        Optional<TeleportInterfaceContext> context = currentInterfaceContext(player);
        if (context.isEmpty()) {
            notify(player, Component.translatable(
                    "message.deadrecall.space_unit.interface.context_invalid"));
            return false;
        }
        if (!context.get().interfaceType().canManageFriends()) {
            notify(player, Component.translatable(
                    "message.deadrecall.space_unit.interface.management_unavailable"));
            return false;
        }
        return validateBoundInterfaceSource(player, context.get().boundUnitId(), true);
    }

    private static Optional<InteractionHand> findBoundInterfaceHand(ServerPlayer player) {
        if (TeleportInterfaceItemResolver.resolve(player, InteractionHand.MAIN_HAND)
                .filter(resolved -> resolved.boundUnitId() != null).isPresent()) {
            return Optional.of(InteractionHand.MAIN_HAND);
        }
        if (TeleportInterfaceItemResolver.resolve(player, InteractionHand.OFF_HAND)
                .filter(resolved -> resolved.boundUnitId() != null).isPresent()) {
            return Optional.of(InteractionHand.OFF_HAND);
        }
        return Optional.empty();
    }

    private static UUID readDeathNodeId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.read(TAG_DEATH_NODE_ID, UUIDUtil.CODEC).orElse(null);
    }

    private static boolean isNearSource(ServerPlayer player, NexusSpaceUnitRecord source) {
        if (!player.level().dimension().equals(source.dimension())) {
            return false;
        }

        BlockPos sourcePos = source.pos();
        double dx = player.getX() - (sourcePos.getX() + 0.5D);
        double dy = player.getY() - (sourcePos.getY() + 0.5D);
        double dz = player.getZ() - (sourcePos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= SOURCE_OPEN_RADIUS * SOURCE_OPEN_RADIUS;
    }

    private static String dimensionId(NexusSpaceUnitRecord unit) {
        return unit.dimension().identifier().toString();
    }

    private static String dimensionId(MapSource source) {
        return source.dimension().identifier().toString();
    }

    private static String dimensionId(TeleportTarget target) {
        return target.dimension().identifier().toString();
    }

    private static BlockPos approximatePlayerDisplayPos(BlockPos pos) {
        return new BlockPos(
                approximateCoordinate(pos.getX(), 64),
                approximateCoordinate(pos.getY(), 16),
                approximateCoordinate(pos.getZ(), 64)
        );
    }

    private static int approximateCoordinate(int value, int grid) {
        return Math.floorDiv(value, grid) * grid + grid / 2;
    }

    private static int roundedPlayerDistance(int distanceBlocks) {
        if (distanceBlocks < 0) {
            return distanceBlocks;
        }
        return Math.max(64, Math.round(distanceBlocks / 64.0F) * 64);
    }

    private static MapSource mapSource(NexusSpaceUnitRecord source) {
        return new MapSource(
                source.id(),
                SOURCE_TYPE_LODESTONE,
                source.name(),
                source.dimension(),
                source.pos(),
                source.structure().resonance(),
                source.structure().tier(),
                source.type()
        );
    }

    private static boolean isValidBlockInteraction(Player player, BlockPos pos) {
        return player.isWithinBlockInteractionRange(pos, 0.0D);
    }

    private static NexusSpaceUnitSavedData units(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
    }

    private static NexusSpaceDiscoverySavedData discovery(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
    }

    private static NexusFriendSavedData friends(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NexusFriendSavedData.TYPE);
    }

    private static NexusMapBindingSavedData mapBindings(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NexusMapBindingSavedData.TYPE);
    }

    static boolean validateBoundInterfaceSource(
            ServerPlayer player,
            UUID sourceUnitId,
            boolean notifyFailure) {
        if (player == null || sourceUnitId == null) return false;
        Optional<NexusSpaceUnitRecord> source = units(player.level().getServer()).get(sourceUnitId);
        boolean valid = source.filter(unit -> unit.isLodestoneAnchor()
                && unit.status() == SpaceUnitStatus.ACTIVE
                && canView(player, unit)).isPresent();
        if (!valid && notifyFailure) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
        }
        return valid;
    }

    private static ServerPlayer findOnlinePlayer(MinecraftServer server, String playerName) {
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate.getName().getString().equalsIgnoreCase(playerName)) {
                return candidate;
            }
        }
        return null;
    }

    private static void notify(Player player, Component message) {
        player.sendSystemMessage(message);
    }

    private record TeleportQuote(
            double routeStability,
            int distanceBlocks,
            int baseFoodCost,
            int finalFoodCost,
            int saturationCost,
            int hungerCost,
            int foodPointsNeeded,
            int safeFoodPointsAvailable,
            int amethystCost,
            int amethystAvailable,
            int baseAmethystCost,
            int sourceCatalystUnits,
            int targetCatalystUnits,
            int catalystShardChange,
            int basePrepareTicks,
            int prepareTicks,
            int baseMaxHorizontalDeviation,
            int maxHorizontalDeviation,
            int damageChancePercent,
            int baseStructureWearChancePercent,
            int structureWearChancePercent,
            boolean interfaceBonusActive,
            String interfaceBonusMessageKey,
            boolean filledMapDataValid,
            boolean canTeleport,
            String blockedReason) {
    }

    private record CostBreakdown(
            int saturationCost,
            int hungerCost,
            int foodPointsNeeded,
            int safeFoodPointsAvailable) {
    }

    private record RepairMaterial(ItemStack stack, net.minecraft.world.level.block.Block block) {
    }

    private record FilledMapCoverageStatus(boolean dataValid, boolean targetCovered) {
        private static final FilledMapCoverageStatus NOT_APPLICABLE =
                new FilledMapCoverageStatus(false, false);
        private static final FilledMapCoverageStatus UNAVAILABLE =
                new FilledMapCoverageStatus(false, false);
    }

    private record MapSource(
            UUID id,
            String type,
            String name,
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            double stability,
            int tier,
            SpaceUnitType unitType) {
    }

    private record TeleportTarget(
            UUID id,
            SpaceUnitType type,
            String name,
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            double stability,
            int tier,
            double wear,
            boolean lodestoneAnchor,
            UUID ownerId) {

        private static TeleportTarget unit(NexusSpaceUnitRecord unit) {
            return new TeleportTarget(
                    unit.id(),
                    unit.type(),
                    unit.name(),
                    unit.dimension(),
                    unit.pos(),
                    unit.structure().resonance(),
                    unit.structure().tier(),
                    unit.structure().wear(),
                    unit.isLodestoneAnchor(),
                    unit.owner()
            );
        }

        private static TeleportTarget player(ServerPlayer player) {
            return new TeleportTarget(
                    player.getUUID(),
                    SpaceUnitType.PLAYER,
                    player.getName().getString(),
                    player.level().dimension(),
                    player.blockPosition().immutable(),
                    0.6D,
                    0,
                    0.0D,
                    false,
                    player.getUUID()
            );
        }
    }

    private record LandingSearchSession(
            TeleportSession teleport,
            NexusSafeLanding.Search search) {
    }

    private record ManageableLodestone(
            NexusSpaceUnitRecord unit,
            ServerLevel level) {
    }

    static record PendingLodestoneRegistration(
            UUID playerId,
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            InteractionHand hand,
            TeleportInterfaceItemResolver.RegistrationInputType inputType,
            MapId mapId,
            UUID boundUnitId,
            long expiresGameTime) {

        boolean matches(
                net.minecraft.resources.ResourceKey<Level> dimension,
                BlockPos pos,
                InteractionHand hand,
                TeleportInterfaceItemResolver.RegistrationInput input,
                long gameTime) {
            return !isExpired(gameTime)
                    && this.dimension.equals(dimension)
                    && this.pos.equals(pos)
                    && this.hand == hand
                    && input != null
                    && this.inputType == input.type()
                    && java.util.Objects.equals(this.mapId, input.mapId())
                    && java.util.Objects.equals(this.boundUnitId, input.boundUnitId());
        }

        boolean matchesDimensionId(String dimensionId) {
            return this.dimension.identifier().toString().equals(dimensionId);
        }

        boolean isExpired(long gameTime) {
            return gameTime > this.expiresGameTime;
        }

        boolean matchesHeld(ServerPlayer player) {
            if (player == null || !playerId.equals(player.getUUID())) return false;
            Optional<TeleportInterfaceItemResolver.RegistrationInput> current =
                    TeleportInterfaceItemResolver.resolveRegistrationInput(player, hand);
            return current.isPresent()
                    && current.get().type() == inputType
                    && java.util.Objects.equals(current.get().mapId(), mapId)
                    && java.util.Objects.equals(current.get().boundUnitId(), boundUnitId);
        }

        TeleportInterfaceItemResolver.RegistrationInput input() {
            return new TeleportInterfaceItemResolver.RegistrationInput(inputType, mapId, boundUnitId);
        }
    }

    private record TeleportSession(
            UUID playerId,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            SpaceUnitType targetType,
            net.minecraft.resources.ResourceKey<Level> startDimension,
            BlockPos startPos,
            TeleportInterfaceType interfaceType,
            InteractionHand interactionHand,
            MapId mapId,
            UUID boundUnitId,
            boolean filledMapDataValidAtStart,
            boolean filledMapBonusActiveAtStart,
            int totalTicks,
            int remainingTicks) {

        private TeleportSession tick() {
            return new TeleportSession(
                    this.playerId,
                    this.sourceType,
                    this.sourceUnitId,
                    this.targetUnitId,
                    this.targetType,
                    this.startDimension,
                    this.startPos,
                    this.interfaceType,
                    this.interactionHand,
                    this.mapId,
                    this.boundUnitId,
                    this.filledMapDataValidAtStart,
                    this.filledMapBonusActiveAtStart,
                    this.totalTicks,
                    this.remainingTicks - 1
            );
        }
    }
}
