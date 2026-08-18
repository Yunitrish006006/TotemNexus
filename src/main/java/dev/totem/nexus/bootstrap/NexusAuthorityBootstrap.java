package dev.totem.nexus.bootstrap;

import dev.totem.core.api.v1.death.DeathBackpackNodeLifecycle;
import dev.totem.core.api.v1.death.DeathRetainedItemPolicy;
import dev.totem.nexus.network.NexusAuthorityPayloadHandler;
import dev.totem.nexus.network.NexusPayloadRegistration;
import dev.totem.nexus.space.NexusDeathBackpackNodeAdapter;
import dev.totem.nexus.space.NexusDeathNodeAdminAuthority;
import dev.totem.nexus.space.NexusDeathNodeAuthority;
import dev.totem.nexus.space.NexusDeathNodeAdminService;
import dev.totem.nexus.space.NexusDistributedSpawnAuthority;
import dev.totem.nexus.space.NexusGameplayAuthority;
import dev.totem.nexus.space.NexusSpaceUnitAuthority;
import dev.totem.nexus.space.NexusSpaceUnitRefreshNetworking;
import dev.totem.nexus.space.NexusSoulboundTeleportItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The only live server composition for a cut-over Nexus artifact. Registration
 * remains idempotent so a duplicate initializer cannot create a second event
 * callback or payload receiver.
 */
public final class NexusAuthorityBootstrap {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private NexusAuthorityBootstrap() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        NexusGameplayAuthority authority = new NexusGameplayAuthority();
        DeathBackpackNodeLifecycle.register(new NexusDeathBackpackNodeAdapter(new NexusDeathNodeAuthority()));
        DeathRetainedItemPolicy.register(NexusSoulboundTeleportItem::isEligibleForDeathRetention);
        NexusDistributedSpawnAuthority.register();
        NexusSpaceUnitAuthority.register();
        NexusPayloadRegistration.registerServerboundTypes();
        NexusPayloadRegistration.registerClientboundTypes();
        NexusPayloadRegistration.registerReceivers(new NexusAuthorityPayloadHandler(authority));
        NexusPayloadRegistration.registerDeathNodeAdminReceivers(new NexusDeathNodeAdminAuthority());
        NexusSpaceUnitRefreshNetworking.register();

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamageTaken, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
                NexusSpaceUnitAuthority.cancelTeleport(
                        player,
                        Component.translatable("message.deadrecall.space_unit.teleport_cancelled.damage")
                );
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(authority::tickTeleportSessions);
        ServerTickEvents.END_SERVER_TICK.register(authority::tickLodestoneIntegrity);
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                NexusDeathNodeAdminService.clearSession(listener.getPlayer().getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("deadrecall")
                        .then(Commands.literal("deathnodes")
                                .executes(context -> openDeathNodes(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("deathpoints")
                                .executes(context -> openDeathNodes(context.getSource().getPlayerOrException())))));
    }

    private static int openDeathNodes(ServerPlayer player) {
        NexusDeathNodeAdminService.sendSnapshot(player);
        return 1;
    }
}
