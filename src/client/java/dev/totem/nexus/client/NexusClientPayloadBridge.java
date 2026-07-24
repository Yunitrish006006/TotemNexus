package dev.totem.nexus.client;

import net.minecraft.client.Minecraft;

/** Supplies the state consumers required by the opt-in client payload boundaries. */
public final class NexusClientPayloadBridge {
    private NexusClientPayloadBridge() { }
    public static void register(NexusClientState state) {
        state.onMap(payload -> Minecraft.getInstance().execute(() -> {
            if (NexusMapScreen.CURRENT != null) NexusMapScreen.CURRENT.apply(payload);
            else Minecraft.getInstance().setScreenAndShow(new NexusMapScreen(payload));
        }));
        state.onRegistrationPreview(payload -> Minecraft.getInstance().execute(
                () -> Minecraft.getInstance().setScreenAndShow(new NexusRegistrationPreviewScreen(payload))));
        state.onFriends(payload -> Minecraft.getInstance().execute(() -> {
            if (NexusFriendsScreen.CURRENT != null) NexusFriendsScreen.CURRENT.apply(payload);
            else Minecraft.getInstance().setScreenAndShow(new NexusFriendsScreen(payload));
        }));
        NexusClientPayloadRegistration.registerSpaceUnitMap(state::acceptMap);
        NexusClientPayloadRegistration.registerSpaceUnitFriends(state::acceptFriends);
        NexusClientPayloadRegistration.registerAdditionalReceivers(
                state::acceptDeathNodeAdmin,
                state::acceptRegistrationPreview
        );
    }
}
