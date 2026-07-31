package dev.totem.nexus.client;

import dev.totem.nexus.network.DeathNodeAdminPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.concurrent.atomic.AtomicBoolean;

/** Registers the preserved death-node administration receiver exactly once. */
public final class NexusDeathNodeAdminClientInitializer {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private NexusDeathNodeAdminClientInitializer() {
    }

    public static void registerReceiver() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientPlayNetworking.registerGlobalReceiver(DeathNodeAdminPayload.TYPE, (payload, context) -> {
            net.minecraft.client.Minecraft minecraft = context.client();
            minecraft.execute(() -> {
                NexusDeathNodeAdminScreen screen = NexusDeathNodeAdminScreen.CURRENT;
                if (screen != null) {
                    screen.applyPayload(payload);
                } else {
                    minecraft.setScreenAndShow(new NexusDeathNodeAdminScreen(payload));
                }
            });
        });
    }
}
