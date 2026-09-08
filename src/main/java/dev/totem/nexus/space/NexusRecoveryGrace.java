package dev.totem.nexus.space;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only rescue lifecycle. It never adds, removes or replaces a potion effect. */
public final class NexusRecoveryGrace {
    private static final Map<UUID, RecoveryGraceState> sessions = new HashMap<>();
    private static final Map<UUID, ServerPlayer> players = new HashMap<>();
    private NexusRecoveryGrace() { }

    public static void register() {
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
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) cancel(player);
            sessions.clear();
            players.clear();
        });
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
        if (type != TeleportInterfaceType.RECOVERY_COMPASS || node == null || node.type() != SpaceUnitType.DEATH
                || node.status() != SpaceUnitStatus.ACTIVE || !node.owner().equals(player.getUUID())
                || !player.isAlive() || player.isSpectator()) return false;
        if (!storage.computeIfAbsent(NexusRecoveryGraceSavedData.TYPE).consume(player.getUUID(), node.id())) return false;
        cancel(player);
        sessions.put(player.getUUID(), RecoveryGraceState.arrive(player.getUUID(), node.id(), node.backpackId().orElse(null), now(player)));
        players.put(player.getUUID(), player);
        player.setInvisible(true);
        return true;
    }

    static void recovered(ServerPlayer player, NexusSpaceUnitRecord before) {
        if (before == null || before.type() != SpaceUnitType.DEATH || before.status() != SpaceUnitStatus.ACTIVE) return;
        sessions.computeIfPresent(player.getUUID(), (id, state) -> state.recover(
                player.getUUID(), before.id(), before.backpackId().orElse(null), now(player)));
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
        if (!player.isAlive() || player.isRemoved() || state.expired(now(player)) || !state.accepts(node)) cancel(player);
        else player.setInvisible(true);
    }
    public static void cancel(ServerPlayer player) {
        players.remove(player.getUUID());
        if (sessions.remove(player.getUUID()) != null)
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
