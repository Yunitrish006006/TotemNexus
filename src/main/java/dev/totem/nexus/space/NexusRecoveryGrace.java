package dev.totem.nexus.space;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import dev.totem.nexus.effect.NexusEffects;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only rescue lifecycle. Owns only the Nexus Phasing effect; independent potions remain untouched. */
public final class NexusRecoveryGrace {
    private static final Map<UUID, RecoveryGraceState> sessions = new HashMap<>();
    private static final Map<UUID, ServerPlayer> players = new HashMap<>();
    private NexusRecoveryGrace() { }

    private static final Map<net.minecraft.world.entity.item.ItemEntity, UUID> backpackNodes = new java.util.WeakHashMap<>();

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
                var nodeId = DeathNodeBackpackBinding.read(item.getItem());
                if (nodeId != null) backpackNodes.put(item, nodeId);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item)) return;
            var knownNode = backpackNodes.remove(item);
            var reason = entity.getRemovalReason();
            if (reason == null || !reason.shouldDestroy()) return;
            var nodeId = knownNode != null ? knownNode : DeathNodeBackpackBinding.read(item.getItem());
            if (nodeId == null) return;
            var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
            units.get(nodeId).filter(node -> node.status() == SpaceUnitStatus.ACTIVE
                    && node.backpackId().filter(entity.getUUID()::equals).isPresent())
                    .ifPresent(node -> units.disableDeathUnit(node.owner(), node.id(), level.getGameTime()));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : java.util.List.copyOf(players.values())) tick(player);
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) cancel(serverPlayer);
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.getEntity() instanceof ServerPlayer attacker) {
                cancel(attacker);
                if (entity instanceof ServerPlayer victim) cancel(victim);
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) cancel(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> cancel(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) cancel(player);
            sessions.clear();
            players.clear();
            backpackNodes.clear();
        });
    }

    static void trackBackpack(net.minecraft.server.level.ServerLevel level, UUID nodeId, UUID entityId) {
        if (level.getEntity(entityId) instanceof net.minecraft.world.entity.item.ItemEntity item)
            backpackNodes.put(item, nodeId);
    }

    public static void projectileLaunched(Projectile projectile) {
        if (!(projectile instanceof ThrownEnderpearl) && !(projectile instanceof ThrownExperienceBottle)
                && !(projectile instanceof FishingHook) && projectile.getOwner() instanceof ServerPlayer player)
            cancel(player);
    }

    /** Called only by the successful server teleport completion path, never a client receiver. */
    static boolean completed(ServerPlayer player, TeleportInterfaceType type, UUID targetId) {
        var storage = player.level().getServer().overworld().getDataStorage();
        var node = NexusSpaceUnitSavedData.loadCanonical(storage).get(targetId).orElse(null);
        if (!type.canSelectTeleportDestination() || node == null || node.type() != SpaceUnitType.DEATH
                || node.status() != SpaceUnitStatus.ACTIVE || node.backpackId().isEmpty() || !node.owner().equals(player.getUUID())
                || !player.isAlive() || player.isSpectator()) return false;
        cancel(player);
        sessions.put(player.getUUID(), RecoveryGraceState.arrive(player.getUUID(), node.id(), node.backpackId().orElse(null), now(player)));
        players.put(player.getUUID(), player);
        player.addEffect(new MobEffectInstance(NexusEffects.PHASING, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        player.setInvisible(true);
        return true;
    }

    static void recovered(ServerPlayer player, NexusSpaceUnitRecord before) {
        if (before == null || before.type() != SpaceUnitType.DEATH || before.status() != SpaceUnitStatus.ACTIVE) return;
        RecoveryGraceState old = sessions.get(player.getUUID());
        sessions.computeIfPresent(player.getUUID(), (id, state) -> state.recover(
                player.getUUID(), before.id(), before.backpackId().orElse(null), now(player)));
        RecoveryGraceState current = sessions.get(player.getUUID());
        if (current != null && current != old && player.hasEffect(NexusEffects.PHASING)) {
            player.removeEffect(NexusEffects.PHASING);
            player.addEffect(new MobEffectInstance(NexusEffects.PHASING,
                    (int) RecoveryGraceState.RECOVERED_TICKS, 0, false, false, true));
        }
        for (ServerPlayer protectedPlayer : java.util.List.copyOf(players.values())) {
            RecoveryGraceState state = sessions.get(protectedPlayer.getUUID());
            if (state != null && state.nodeId().equals(before.id()) && !state.recovered()) cancel(protectedPlayer);
        }
    }

    public static boolean active(ServerPlayer player) { return sessions.containsKey(player.getUUID()); }
    static RecoveryGraceState state(ServerPlayer player) { return sessions.get(player.getUUID()); }
    static void tick(ServerPlayer player) {
        RecoveryGraceState state = sessions.get(player.getUUID());
        if (state == null) return;
        var node = NexusSpaceUnitSavedData.loadCanonical(player.level().getServer().overworld().getDataStorage())
                .get(state.nodeId()).orElse(null);
        if (!player.hasEffect(NexusEffects.PHASING) || !player.isAlive() || player.isRemoved() || state.expired(now(player)) || !state.accepts(node)) cancel(player);
        else player.setInvisible(true);
    }
    public static void cancel(ServerPlayer player) {
        players.remove(player.getUUID());
        sessions.remove(player.getUUID());
        player.removeEffect(NexusEffects.PHASING);
        player.setInvisible(player.isSpectator() || player.hasEffect(MobEffects.INVISIBILITY));
    }
    /** Persistence mutation hook cancels immediately; recovery uses the explicit exact-node suffix path. */
    static void invalidated(net.minecraft.server.MinecraftServer server, UUID nodeId) {
        invalidated(nodeId);
    }
    static void invalidated(UUID nodeId) {
        for (ServerPlayer player : java.util.List.copyOf(players.values())) {
            RecoveryGraceState state = sessions.get(player.getUUID());
            if (state != null && state.nodeId().equals(nodeId)) cancel(player);
        }
    }
    private static long now(ServerPlayer player) { return player.level().getServer().overworld().getGameTime(); }
}
