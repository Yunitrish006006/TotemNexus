package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Owns client registration for the future TotemNexus module.
 */
public final class NexusClientBootstrap {
    private NexusClientBootstrap() {
    }

    public static void registerNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(SpaceUnitMapPayload.TYPE,
                (payload, context) -> {
                    Minecraft mc = context.client();
                    mc.execute(() -> {
                        NexusSpaceUnitMapScreen screen = NexusSpaceUnitMapScreen.CURRENT;
                        if (screen != null && screen.isFor(payload.sourceType(), payload.sourceUnitId())) {
                            screen.applyPayload(payload);
                        } else {
                            mc.setScreenAndShow(new NexusSpaceUnitMapScreen(payload));
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(SpaceUnitFriendsPayload.TYPE,
                (payload, context) -> {
                    Minecraft mc = context.client();
                    mc.execute(() -> {
                        NexusSpaceUnitFriendsScreen screen = NexusSpaceUnitFriendsScreen.CURRENT;
                        if (screen != null) {
                            screen.applyPayload(payload);
                        } else {
                            mc.setScreenAndShow(new NexusSpaceUnitFriendsScreen(null, payload));
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(SpaceUnitRegistrationPreviewPayload.TYPE,
                (payload, context) -> {
                    Minecraft mc = context.client();
                    mc.execute(() -> mc.setScreenAndShow(new NexusSpaceUnitRegistrationPreviewScreen(payload)));
                });
    }
}
