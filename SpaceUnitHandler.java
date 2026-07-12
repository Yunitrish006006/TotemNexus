package com.adaptor.deadrecall.space;

import com.adaptor.deadrecall.network.SpaceUnitMapPayload;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SpaceUnitHandler {
    public static final String SOURCE_TYPE_LODESTONE = "lodestone";
    public static final String SOURCE_TYPE_PLAYER = "player";

    private static final String TAG_SPACE_UNIT_ID = "deadrecall_space_unit_id";
    private static final String TAG_SPACE_UNIT_DATA_VERSION = "deadrecall_space_unit_data_version";
    private static final double SOURCE_OPEN_RADIUS = 8.0D;
    private static final int MIN_REMAINING_FOOD_LEVEL = 1;
    private static final int SAME_DIMENSION_FOOD_BLOCKS_PER_POINT = 384;
    private static final int MAX_BASE_FOOD_COST = 20;
    private static final int MIN_CROSS_DIMENSION_AMETHYST_COST = 2;
    private static final double SESSION_MOVE_CANCEL_DISTANCE = 4.0D;
    private static final int SAFE_LANDING_VERTICAL_SEARCH = 4;

    private static final Map<UUID, TeleportSession> teleportSessions = new HashMap<>();

    private SpaceUnitHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.COMPASS) || !world.getBlockState(pos).is(Blocks.LODESTONE)) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            return handleLodestoneUse((ServerPlayer) player, (ServerLevel) world, stack, pos);
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.COMPASS)) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            sendPlayerAnchorMap((ServerPlayer) player);
            return InteractionResult.SUCCESS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.COMPASS) || !world.getBlockState(pos).is(Blocks.LODESTONE)) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            return handleLodestoneActivation((ServerPlayer) player, (ServerLevel) world, pos);
        });
    }

    public static void createDeathNode(ServerPlayer player, ServerLevel level, BlockPos deathPos) {
        DeadRecallSpaceUnitSavedData units = units(level.getServer());
        DeadRecallSpaceDiscoverySavedData discovery = discovery(level.getServer());
        SpaceUnitRecord unit = units.createDeathUnit(level, deathPos, player);
        discovery.markDiscovered(player.getUUID(), unit.id());
    }

    public static void sendSpaceUnitMap(ServerPlayer player) {
        ItemStack stack = findBoundCompass(player);
        if (stack.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_bound_compass"));
            return;
        }

        sendSpaceUnitMap(player, stack);
    }

    public static void sendSpaceUnitMap(ServerPlayer player, String sourceType, UUID sourceUnitId) {
        if (SOURCE_TYPE_PLAYER.equals(sourceType)) {
            if (!player.getUUID().equals(sourceUnitId)) {
                notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
                return;
            }
            sendPlayerAnchorMap(player);
            return;
        }

        if (SOURCE_TYPE_LODESTONE.equals(sourceType)) {
            sendSpaceUnitMap(player, sourceUnitId);
            return;
        }

        notify(player, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
    }

    public static void startTeleport(ServerPlayer player, String sourceType, UUID sourceUnitId, UUID targetUnitId) {
        teleportSessions.remove(player.getUUID());

        Optional<MapSource> source = resolveMapSource(player, sourceType, sourceUnitId, true);
        if (source.isEmpty()) {
            return;
        }

        Optional<SpaceUnitRecord> target = resolveTeleportTarget(player, targetUnitId, true);
        if (target.isEmpty()) {
            return;
        }

        TeleportQuote quote = calculateTeleportQuote(player, source.get(), target.get());
        if (!quote.canTeleport()) {
            notify(player, Component.translatable(quote.blockedReason()));
            return;
        }

        ServerLevel targetLevel = player.level().getServer().getLevel(target.get().dimension());
        if (targetLevel == null || findSafeLanding(targetLevel, target.get(), quote.maxHorizontalDeviation()).isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.no_landing"));
            return;
        }

        int prepareTicks = Math.max(1, quote.prepareTicks());
        teleportSessions.put(player.getUUID(), new TeleportSession(
                player.getUUID(),
                source.get().type(),
                source.get().id(),
                target.get().id(),
                player.level().dimension(),
                player.blockPosition().immutable(),
                prepareTicks,
                prepareTicks
        ));
        notify(player, Component.translatable(
                "message.deadrecall.space_unit.teleport_started",
                target.get().name(),
                seconds(prepareTicks)
        ));
    }

    public static void tickTeleportSessions(MinecraftServer server) {
        if (teleportSessions.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, TeleportSession>> iterator = teleportSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TeleportSession> entry = iterator.next();
            TeleportSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (player == null) {
                iterator.remove();
                continue;
            }

            Component cancelReason = teleportCancelReason(player, session);
            if (cancelReason != null) {
                iterator.remove();
                notify(player, cancelReason);
                continue;
            }

            Optional<MapSource> source = resolveMapSource(player, session.sourceType(), session.sourceUnitId(), false);
            if (source.isEmpty()) {
                iterator.remove();
                notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.source"));
                continue;
            }

            Optional<SpaceUnitRecord> target = resolveTeleportTarget(player, session.targetUnitId(), false);
            if (target.isEmpty()) {
                iterator.remove();
                notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
                continue;
            }

            TeleportQuote quote = calculateTeleportQuote(player, source.get(), target.get());
            if (!quote.canTeleport()) {
                iterator.remove();
                notify(player, Component.translatable(quote.blockedReason()));
                continue;
            }

            TeleportSession next = session.tick();
            if (next.remainingTicks() > 0) {
                entry.setValue(next);
                continue;
            }

            iterator.remove();
            completeTeleport(player, source.get(), target.get(), quote);
        }
    }

    public static void cancelTeleport(ServerPlayer player, Component reason) {
        if (teleportSessions.remove(player.getUUID()) != null) {
            notify(player, reason);
        }
    }

    private static InteractionResult handleLodestoneUse(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos pos) {
        if (!isValidBlockInteraction(player, pos)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.too_far"));
            return InteractionResult.SUCCESS;
        }

        DeadRecallSpaceUnitSavedData units = units(level.getServer());
        Optional<SpaceUnitRecord> existing = units.getLodestone(level.dimension(), pos);
        SpaceUnitRecord unit;
        boolean created = existing.isEmpty();
        if (existing.isPresent()) {
            unit = existing.get();
            if (!unit.canView(player.getUUID())) {
                notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
                return InteractionResult.SUCCESS;
            }
        } else {
            unit = units.getOrCreateLodestone(level, pos, player);
        }

        bindCompass(player, stack, level, pos, unit.id());
        level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (created) {
            notify(player, Component.translatable("message.deadrecall.space_unit.registered", unit.name()));
            return InteractionResult.SUCCESS;
        }

        if (!discovery(level.getServer()).hasDiscovered(player.getUUID(), unit.id())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.bound_explore_to_open", unit.name()));
            return InteractionResult.SUCCESS;
        }

        sendSpaceUnitMap(player, unit.id());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult handleLodestoneActivation(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!isValidBlockInteraction(player, pos)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.too_far"));
            return InteractionResult.SUCCESS;
        }

        DeadRecallSpaceUnitSavedData units = units(level.getServer());
        Optional<SpaceUnitRecord> unit = units.getLodestone(level.dimension(), pos);
        if (unit.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.register_first"));
            return InteractionResult.SUCCESS;
        }

        SpaceUnitRecord record = unit.get();
        if (!record.canView(player.getUUID())) {
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

    private static void sendSpaceUnitMap(ServerPlayer player, ItemStack stack) {
        UUID sourceUnitId = readBoundSpaceUnitId(stack);
        if (sourceUnitId == null) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_bound_compass"));
            return;
        }

        sendSpaceUnitMap(player, sourceUnitId);
    }

    private static void sendPlayerAnchorMap(ServerPlayer player) {
        if (!hasCompassInHand(player)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_compass"));
            return;
        }

        MinecraftServer server = player.level().getServer();
        DeadRecallSpaceUnitSavedData units = units(server);
        DeadRecallSpaceDiscoverySavedData discovery = discovery(server);
        MapSource source = new MapSource(
                player.getUUID(),
                SOURCE_TYPE_PLAYER,
                player.getName().getString(),
                player.level().dimension(),
                player.blockPosition(),
                0.6D,
                0,
                SpaceUnitType.PLAYER
        );
        ServerPlayNetworking.send(player, buildMapPayload(player, source, units.getVisibleDiscoveredUnits(player.getUUID(), discovery)));
    }

    public static void sendSpaceUnitMap(ServerPlayer player, UUID sourceUnitId) {
        if (!hasCompassInHand(player)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_compass"));
            return;
        }

        MinecraftServer server = player.level().getServer();
        DeadRecallSpaceUnitSavedData units = units(server);
        Optional<SpaceUnitRecord> sourceUnit = units.get(sourceUnitId);
        if (sourceUnit.isEmpty() || sourceUnit.get().status() != SpaceUnitStatus.ACTIVE) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return;
        }

        SpaceUnitRecord source = sourceUnit.get();
        if (!source.isLodestoneAnchor()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_need_lodestone_source"));
            return;
        }
        if (!source.canView(player.getUUID())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return;
        }

        DeadRecallSpaceDiscoverySavedData discovery = discovery(server);
        if (!discovery.hasDiscovered(player.getUUID(), source.id())) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_unexplored"));
            return;
        }
        if (!isNearSource(player, source)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_too_far"));
            return;
        }
        if (!player.level().getBlockState(source.pos()).is(Blocks.LODESTONE)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return;
        }

        ServerPlayNetworking.send(player, buildMapPayload(player, mapSource(source), units.getVisibleDiscoveredUnits(player.getUUID(), discovery)));
    }

    private static void completeTeleport(ServerPlayer player, MapSource source, SpaceUnitRecord target, TeleportQuote quote) {
        TeleportQuote finalQuote = calculateTeleportQuote(player, source, target);
        if (!finalQuote.canTeleport()) {
            notify(player, Component.translatable(finalQuote.blockedReason()));
            return;
        }

        ServerLevel targetLevel = player.level().getServer().getLevel(target.dimension());
        if (targetLevel == null) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
            return;
        }

        Optional<BlockPos> landing = findSafeLanding(targetLevel, target, finalQuote.maxHorizontalDeviation());
        if (landing.isEmpty()) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.no_landing"));
            return;
        }

        if (!deductTeleportCost(player, finalQuote)) {
            notify(player, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.cost"));
            return;
        }

        BlockPos landingPos = landing.get();
        player.teleportTo(
                targetLevel,
                landingPos.getX() + 0.5D,
                landingPos.getY(),
                landingPos.getZ() + 0.5D,
                Relative.DELTA,
                player.getYRot(),
                player.getXRot(),
                false
        );
        targetLevel.playSound(null, landingPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.85F, 1.1F);
        notify(player, Component.translatable("message.deadrecall.space_unit.teleport_completed", target.name()));
    }

    private static Optional<MapSource> resolveMapSource(ServerPlayer player, String sourceType, UUID sourceUnitId, boolean notifyFailure) {
        if (!hasCompassInHand(player)) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_need_compass"));
            return Optional.empty();
        }

        if (SOURCE_TYPE_PLAYER.equals(sourceType)) {
            if (!player.getUUID().equals(sourceUnitId)) {
                notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.no_permission"));
                return Optional.empty();
            }
            return Optional.of(playerMapSource(player));
        }

        if (!SOURCE_TYPE_LODESTONE.equals(sourceType) || sourceUnitId == null) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return Optional.empty();
        }

        MinecraftServer server = player.level().getServer();
        DeadRecallSpaceUnitSavedData units = units(server);
        Optional<SpaceUnitRecord> sourceUnit = units.get(sourceUnitId);
        if (sourceUnit.isEmpty() || sourceUnit.get().status() != SpaceUnitStatus.ACTIVE) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return Optional.empty();
        }

        SpaceUnitRecord source = sourceUnit.get();
        if (!source.isLodestoneAnchor()) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_need_lodestone_source"));
            return Optional.empty();
        }
        if (!source.canView(player.getUUID())) {
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
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.map_source_missing"));
            return Optional.empty();
        }

        return Optional.of(mapSource(source));
    }

    private static Optional<SpaceUnitRecord> resolveTeleportTarget(ServerPlayer player, UUID targetUnitId, boolean notifyFailure) {
        if (targetUnitId == null) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
            return Optional.empty();
        }

        MinecraftServer server = player.level().getServer();
        Optional<SpaceUnitRecord> targetUnit = units(server).get(targetUnitId);
        if (targetUnit.isEmpty() || targetUnit.get().status() != SpaceUnitStatus.ACTIVE) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
            return Optional.empty();
        }

        SpaceUnitRecord target = targetUnit.get();
        if (!target.canView(player.getUUID())) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.no_permission"));
            return Optional.empty();
        }
        if (!discovery(server).hasDiscovered(player.getUUID(), target.id())) {
            notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target_unexplored"));
            return Optional.empty();
        }
        if (target.isLodestoneAnchor()) {
            ServerLevel targetLevel = server.getLevel(target.dimension());
            if (targetLevel == null || !targetLevel.getBlockState(target.pos()).is(Blocks.LODESTONE)) {
                notifyIfRequested(player, notifyFailure, Component.translatable("message.deadrecall.space_unit.teleport_cancelled.target"));
                return Optional.empty();
            }
        }
        return Optional.of(target);
    }

    private static MapSource playerMapSource(ServerPlayer player) {
        return new MapSource(
                player.getUUID(),
                SOURCE_TYPE_PLAYER,
                player.getName().getString(),
                player.level().dimension(),
                player.blockPosition(),
                0.6D,
                0,
                SpaceUnitType.PLAYER
        );
    }

    private static Component teleportCancelReason(ServerPlayer player, TeleportSession session) {
        if (!player.isAlive() || player.isRemoved()) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.generic");
        }
        if (!hasCompassInHand(player)) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.no_compass");
        }
        if (!player.level().dimension().equals(session.startDimension())) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.dimension");
        }
        if (distanceSquared(player.blockPosition(), session.startPos()) > SESSION_MOVE_CANCEL_DISTANCE * SESSION_MOVE_CANCEL_DISTANCE) {
            return Component.translatable("message.deadrecall.space_unit.teleport_cancelled.moved");
        }
        return null;
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
        foodData.setFoodLevel(Math.max(MIN_REMAINING_FOOD_LEVEL, foodData.getFoodLevel() - quote.hungerCost()));
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

    private static Optional<BlockPos> findSafeLanding(ServerLevel level, SpaceUnitRecord target, int maxHorizontalDeviation) {
        BlockPos anchor = landingAnchor(target);
        int radius = clamp(maxHorizontalDeviation, 0, 96);
        for (int horizontal = 0; horizontal <= radius; horizontal++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != horizontal) {
                        continue;
                    }

                    Optional<BlockPos> landing = findSafeLandingInColumn(level, anchor.offset(dx, 0, dz));
                    if (landing.isPresent()) {
                        return landing;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findSafeLandingInColumn(ServerLevel level, BlockPos anchor) {
        if (isSafeLanding(level, anchor)) {
            return Optional.of(anchor.immutable());
        }

        for (int offset = 1; offset <= SAFE_LANDING_VERTICAL_SEARCH; offset++) {
            BlockPos above = anchor.above(offset);
            if (isSafeLanding(level, above)) {
                return Optional.of(above.immutable());
            }

            BlockPos below = anchor.below(offset);
            if (isSafeLanding(level, below)) {
                return Optional.of(below.immutable());
            }
        }
        return Optional.empty();
    }

    private static boolean isSafeLanding(ServerLevel level, BlockPos feetPos) {
        if (feetPos.getY() <= level.getMinY() || feetPos.getY() >= level.getMaxY()) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(feetPos.getX() + 0.5D, feetPos.getZ() + 0.5D)) {
            return false;
        }

        BlockPos headPos = feetPos.above();
        BlockPos floorPos = feetPos.below();
        BlockState feet = level.getBlockState(feetPos);
        BlockState head = level.getBlockState(headPos);
        BlockState floor = level.getBlockState(floorPos);
        if (!isOpenForPlayer(feet) || !isOpenForPlayer(head)) {
            return false;
        }
        if (floor.isAir() || !floor.getFluidState().isEmpty() || !floor.blocksMotion()) {
            return false;
        }
        return !isUnsafeBlock(feet) && !isUnsafeBlock(head) && !isUnsafeBlock(floor);
    }

    private static boolean isOpenForPlayer(BlockState state) {
        return state.isAir() && state.getFluidState().isEmpty();
    }

    private static boolean isUnsafeBlock(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.POWDER_SNOW);
    }

    private static BlockPos landingAnchor(SpaceUnitRecord target) {
        return target.isLodestoneAnchor() ? target.pos().above() : target.pos();
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

    private static void bindCompass(ServerPlayer player, ItemStack stack, ServerLevel level, BlockPos pos, UUID unitId) {
        ItemStack targetStack = stack;
        if (!player.hasInfiniteMaterials() && stack.getCount() > 1) {
            targetStack = stack.transmuteCopy(Items.COMPASS, 1);
            stack.consume(1, player);
        }

        writeCompassBinding(targetStack, level, pos, unitId);
        if (targetStack != stack) {
            if (!player.getInventory().add(targetStack)) {
                player.drop(targetStack, false);
            }
        }
    }

    private static void writeCompassBinding(ItemStack stack, ServerLevel level, BlockPos pos, UUID unitId) {
        stack.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos.immutable())), true));

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.store(TAG_SPACE_UNIT_ID, UUIDUtil.CODEC, unitId);
        tag.putInt(TAG_SPACE_UNIT_DATA_VERSION, DeadRecallSpaceUnitSavedData.DATA_VERSION);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static SpaceUnitMapPayload buildMapPayload(ServerPlayer player, MapSource source, List<SpaceUnitRecord> visibleUnits) {
        List<SpaceUnitMapPayload.Entry> entries = new ArrayList<>(Math.min(visibleUnits.size(), SpaceUnitMapPayload.MAX_ENTRIES));
        for (SpaceUnitRecord unit : visibleUnits) {
            if (entries.size() >= SpaceUnitMapPayload.MAX_ENTRIES) {
                break;
            }
            TeleportQuote quote = calculateTeleportQuote(player, source, unit);
            entries.add(new SpaceUnitMapPayload.Entry(
                    unit.id(),
                    unit.type().id(),
                    unit.name(),
                    dimensionId(unit),
                    unit.pos().getX(),
                    unit.pos().getY(),
                    unit.pos().getZ(),
                    quote.routeStability(),
                    unit.structure().tier(),
                    quote.distanceBlocks(),
                    quote.saturationCost(),
                    quote.hungerCost(),
                    quote.foodPointsNeeded(),
                    quote.safeFoodPointsAvailable(),
                    quote.amethystCost(),
                    quote.amethystAvailable(),
                    quote.prepareTicks(),
                    quote.maxHorizontalDeviation(),
                    quote.damageChancePercent(),
                    quote.canTeleport(),
                    quote.blockedReason()
            ));
        }

        return new SpaceUnitMapPayload(
                source.id(),
                source.type(),
                source.name(),
                dimensionId(source),
                source.pos().getX(),
                source.pos().getY(),
                source.pos().getZ(),
                entries
        );
    }

    private static TeleportQuote calculateTeleportQuote(ServerPlayer player, MapSource source, SpaceUnitRecord target) {
        boolean sameDimension = source.dimension().equals(target.dimension());
        boolean sameUnit = SOURCE_TYPE_LODESTONE.equals(source.type()) && source.id().equals(target.id());
        int distanceBlocks = sameDimension ? distanceBlocks(source.pos(), target.pos()) : -1;
        double routeStability = sameUnit ? 1.0D : routeStability(source, target, sameDimension, distanceBlocks);
        int baseFoodCost = sameUnit ? 0 : baseFoodCost(target, sameDimension, distanceBlocks);
        CostBreakdown cost = calculateCostBreakdown(player, baseFoodCost);
        int amethystAvailable = countAmethyst(player);
        int amethystCost = sameUnit || sameDimension ? 0 : Math.max(MIN_CROSS_DIMENSION_AMETHYST_COST,
                MIN_CROSS_DIMENSION_AMETHYST_COST + (int) Math.ceil((1.0D - routeStability) * 4.0D));
        int prepareTicks = sameUnit ? 0 : prepareTicks(target, sameDimension, distanceBlocks, routeStability);
        int maxHorizontalDeviation = sameUnit ? 0 : maxHorizontalDeviation(routeStability);
        int damageChancePercent = sameUnit ? 0 : damageChancePercent(target, sameDimension, distanceBlocks, routeStability);
        String blockedReason = blockedReason(source, target, routeStability, cost, amethystCost, amethystAvailable, sameDimension, sameUnit);
        return new TeleportQuote(
                routeStability,
                distanceBlocks,
                cost.saturationCost(),
                cost.hungerCost(),
                cost.foodPointsNeeded(),
                cost.safeFoodPointsAvailable(),
                amethystCost,
                amethystAvailable,
                prepareTicks,
                maxHorizontalDeviation,
                damageChancePercent,
                blockedReason.isEmpty(),
                blockedReason
        );
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
            SpaceUnitRecord target,
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
        if (!sameDimension && target.isLodestoneAnchor() && target.structure().tier() < 1) {
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

    private static double routeStability(MapSource source, SpaceUnitRecord target, boolean sameDimension, int distanceBlocks) {
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

    private static double unitStability(SpaceUnitRecord unit) {
        return switch (unit.type()) {
            case LODESTONE -> clamp(unit.structure().resonance(), 0.0D, 1.0D);
            case DEATH -> 0.55D;
            case PLAYER -> 0.6D;
            case TEMPORARY -> 0.5D;
            case SYSTEM -> 0.8D;
        };
    }

    private static int baseFoodCost(SpaceUnitRecord target, boolean sameDimension, int distanceBlocks) {
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

    private static int prepareTicks(SpaceUnitRecord target, boolean sameDimension, int distanceBlocks, double routeStability) {
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

    private static int damageChancePercent(SpaceUnitRecord target, boolean sameDimension, int distanceBlocks, double routeStability) {
        double chance = (1.0D - routeStability) * 18.0D;
        chance += sameDimension ? Math.min(8.0D, distanceBlocks / 1500.0D) : 10.0D;
        chance += switch (target.type()) {
            case DEATH -> 8.0D;
            case PLAYER -> 10.0D;
            case TEMPORARY -> 4.0D;
            default -> 0.0D;
        };
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

    private static ItemStack findBoundCompass(ServerPlayer player) {
        ItemStack mainHandStack = player.getMainHandItem();
        if (readBoundSpaceUnitId(mainHandStack) != null) {
            return mainHandStack;
        }

        ItemStack offHandStack = player.getOffhandItem();
        if (readBoundSpaceUnitId(offHandStack) != null) {
            return offHandStack;
        }

        return ItemStack.EMPTY;
    }

    private static boolean hasCompassInHand(ServerPlayer player) {
        return player.getMainHandItem().is(Items.COMPASS) || player.getOffhandItem().is(Items.COMPASS);
    }

    private static UUID readBoundSpaceUnitId(ItemStack stack) {
        if (!stack.is(Items.COMPASS)) {
            return null;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.read(TAG_SPACE_UNIT_ID, UUIDUtil.CODEC).orElse(null);
    }

    private static boolean isNearSource(ServerPlayer player, SpaceUnitRecord source) {
        if (!player.level().dimension().equals(source.dimension())) {
            return false;
        }

        BlockPos sourcePos = source.pos();
        double dx = player.getX() - (sourcePos.getX() + 0.5D);
        double dy = player.getY() - (sourcePos.getY() + 0.5D);
        double dz = player.getZ() - (sourcePos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= SOURCE_OPEN_RADIUS * SOURCE_OPEN_RADIUS;
    }

    private static String dimensionId(SpaceUnitRecord unit) {
        return unit.dimension().identifier().toString();
    }

    private static String dimensionId(MapSource source) {
        return source.dimension().identifier().toString();
    }

    private static MapSource mapSource(SpaceUnitRecord source) {
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

    private static DeadRecallSpaceUnitSavedData units(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DeadRecallSpaceUnitSavedData.TYPE);
    }

    private static DeadRecallSpaceDiscoverySavedData discovery(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DeadRecallSpaceDiscoverySavedData.TYPE);
    }

    private static void notify(Player player, Component message) {
        player.sendSystemMessage(message);
    }

    private record TeleportQuote(
            double routeStability,
            int distanceBlocks,
            int saturationCost,
            int hungerCost,
            int foodPointsNeeded,
            int safeFoodPointsAvailable,
            int amethystCost,
            int amethystAvailable,
            int prepareTicks,
            int maxHorizontalDeviation,
            int damageChancePercent,
            boolean canTeleport,
            String blockedReason) {
    }

    private record CostBreakdown(
            int saturationCost,
            int hungerCost,
            int foodPointsNeeded,
            int safeFoodPointsAvailable) {
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

    private record TeleportSession(
            UUID playerId,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            net.minecraft.resources.ResourceKey<Level> startDimension,
            BlockPos startPos,
            int totalTicks,
            int remainingTicks) {

        private TeleportSession tick() {
            return new TeleportSession(
                    this.playerId,
                    this.sourceType,
                    this.sourceUnitId,
                    this.targetUnitId,
                    this.startDimension,
                    this.startPos,
                    this.totalTicks,
                    this.remainingTicks - 1
            );
        }
    }
}
