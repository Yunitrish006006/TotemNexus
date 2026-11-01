package dev.totem.nexus.space;

import net.minecraft.world.InteractionHand;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Publishes a map session only after its MapId record is durably acceptable. */
final class NexusMapOpenPreflight {
    private NexusMapOpenPreflight() { }

    static Optional<TeleportInterfaceContext> establish(
            UUID playerId,
            InteractionHand hand,
            long gameTime,
            NexusHeldMapResolver.ResolvedMap heldMap,
            Supplier<NexusMapRecordSavedData> recordStorage,
            TeleportInterfaceSessionStore sessions,
            long lifetimeTicks) {
        if (playerId == null || hand == null || heldMap == null || recordStorage == null || sessions == null
                || gameTime < 0 || lifetimeTicks < 0 || gameTime > Long.MAX_VALUE - lifetimeTicks) {
            return Optional.empty();
        }

        NexusMapRecordSavedData records;
        try {
            records = recordStorage.get();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (records == null) return Optional.empty();

        NexusMapRecordSavedData.BootstrapResult bootstrap =
                records.bootstrap(heldMap.mapId(), heldMap.nativeData());
        if (bootstrap.result() == NexusMapRecordSavedData.RegisterResult.CONFLICT) {
            return Optional.empty();
        }

        TeleportInterfaceContext context = new TeleportInterfaceContext(
                playerId,
                TeleportInterfaceType.FILLED_MAP,
                "player",
                playerId,
                hand,
                heldMap.mapId(),
                null,
                gameTime,
                gameTime + lifetimeTicks);
        sessions.put(context);
        return Optional.of(context);
    }
}
