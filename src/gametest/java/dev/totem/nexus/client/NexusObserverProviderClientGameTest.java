package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.observer.ObserverRemoteCursor;
import dev.totem.core.api.v1.client.observer.ObserverScreenContext;
import dev.totem.core.api.v1.client.observer.ObserverScreenHandle;
import dev.totem.core.api.v1.client.observer.ObserverScreenProvider;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Owner-local runtime proof for every Nexus production Observer Screen variant. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusObserverProviderClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.getInput().resizeWindow(1280, 720);
            List<ObserverScreenProvider> providers = context.computeOnClient(client -> FabricLoader.getInstance()
                    .getEntrypoints(ObserverScreenProvider.ENTRYPOINT, ObserverScreenProvider.class));
            require(providers.stream().anyMatch(NexusObserverScreenProvider.class::isInstance),
                    "Nexus Observer provider entrypoint is missing");
            require(providers.stream().anyMatch(NexusDeathAdminObserverScreenProvider.class::isInstance),
                    "Nexus Death Admin Observer provider entrypoint is missing");
            NexusObserverScreenProvider nexus = new NexusObserverScreenProvider();
            NexusDeathAdminObserverScreenProvider death = new NexusDeathAdminObserverScreenProvider();
            require(nexus.protocolVersion() == 3,
                    "Local-only visualization controls must not change the Nexus Observer semantic protocol");

            exercise(context, nexus,
                    clientScreen(context, () -> new NexusSpaceUnitMapScreen(map("Home"))),
                    clientScreen(context, () -> new NexusSpaceUnitMapScreen(map("Remote Home"))),
                    "nexus-observer-owner-map", screen ->
                            "Remote Home".equals(((NexusSpaceUnitMapScreen) screen).observerPayload().sourceName()));
            exercise(context, nexus,
                    clientScreen(context, () -> new NexusSpaceUnitMapScreen(filledMap("Map Home", 8801))),
                    clientScreen(context, () -> new NexusSpaceUnitMapScreen(filledMap("Remote Map Home", 8801))),
                    "nexus-observer-owner-map-data-unavailable", screen -> {
                        NexusSpaceUnitMapScreen map = (NexusSpaceUnitMapScreen) screen;
                        return "Remote Map Home".equals(map.observerPayload().sourceName())
                                && map.mapDataUnavailableForVisualTest();
                    });
            exercise(context, nexus,
                    clientScreen(context, () -> new NexusMapScreen(map("Home"))),
                    clientScreen(context, () -> new NexusMapScreen(map("Remote Home"))),
                    "nexus-observer-owner-map-legacy", screen ->
                            "Remote Home".equals(((NexusMapScreen) screen).observerPayload().sourceName()));
            exercise(context, nexus,
                    clientScreen(context, () -> new NexusSpaceUnitFriendsScreen(null, friends(1))),
                    clientScreen(context, () -> new NexusSpaceUnitFriendsScreen(null, friends(2))),
                    "nexus-observer-owner-friends", screen ->
                            ((NexusSpaceUnitFriendsScreen) screen).observerPayload().entries().size() == 2);
            exercise(context, nexus,
                    clientScreen(context, () -> new NexusFriendsScreen(friends(1))),
                    clientScreen(context, () -> new NexusFriendsScreen(friends(2))),
                    "nexus-observer-owner-friends-legacy", screen ->
                            ((NexusFriendsScreen) screen).observerPayload().entries().size() == 2);
            exercise(context, nexus,
                    clientScreen(context, () -> new NexusSpaceUnitRegistrationPreviewScreen(registration(3))),
                    clientScreen(context, () -> new NexusSpaceUnitRegistrationPreviewScreen(registration(4))),
                    "nexus-observer-owner-registration", screen ->
                            ((NexusSpaceUnitRegistrationPreviewScreen) screen).observerPayload().tier() == 4);
            exercise(context, nexus,
                    clientScreen(context, () -> new NexusRegistrationPreviewScreen(registration(3))),
                    clientScreen(context, () -> new NexusRegistrationPreviewScreen(registration(4))),
                    "nexus-observer-owner-registration-legacy", screen ->
                            ((NexusRegistrationPreviewScreen) screen).observerPayload().tier() == 4);
            context.getInput().resizeWindow(854, 480);
            exercise(context, death,
                    clientScreen(context, () -> new NexusDeathNodeAdminScreen(death(1))),
                    clientScreen(context, () -> new NexusDeathNodeAdminScreen(death(2))),
                    "nexus-observer-owner-death-admin", screen ->
                            ((NexusDeathNodeAdminScreen) screen).observerPayload().entries().size() == 2);
        }
    }

    private static void exercise(ClientGameTestContext context, ObserverScreenProvider provider,
                                 Screen source, Screen updateSource, String screenshot,
                                 Predicate<Screen> updated) {
        ObserverScreenSnapshot initial = context.computeOnClient(client ->
                provider.capture(source, 1).orElseThrow());
        ObserverScreenSnapshot update = context.computeOnClient(client ->
                provider.capture(updateSource, 2).orElseThrow());
        AtomicInteger stops = new AtomicInteger();
        ObserverScreenHandle handle = context.computeOnClient(client -> provider.create(
                new ObserverScreenContext(UUID.randomUUID(), "Target", stops::incrementAndGet), initial));
        context.runOnClient(client -> client.setScreenAndShow(handle.screen()));
        context.waitFor(client -> client.gui.screen() == handle.screen(), 100);
        context.runOnClient(client -> {
            require(handle.screen() instanceof NexusOwnedScreen owned && owned.totem$isObserverReadOnly(),
                    "Nexus production Screen did not enter Observer mode: " + handle.screen().getClass().getName());
            String otherSupportedVariant = update.variant().equals("friends") ? "map" : "friends";
            handle.applySnapshot(foreign(update, update.familyId(), otherSupportedVariant,
                    update.protocolVersion(), 90));
            handle.applySnapshot(foreign(update, update.familyId(), update.variant(),
                    update.protocolVersion() + 1, 91));
            handle.applySnapshot(foreign(update, "foreign", update.variant(), update.protocolVersion(), 92));
            handle.applySnapshot(update);
            handle.applySnapshot(initial);
            require(updated.test(handle.screen()), "Exact monotonic Nexus snapshot policy failed for "
                    + handle.screen().getClass().getName());
            handle.applyCursor(new ObserverRemoteCursor(2, 100, 80,
                    Math.max(1, handle.screen().width), Math.max(1, handle.screen().height), ItemStack.EMPTY));
            handle.applyCursor(new ObserverRemoteCursor(1, 0, 0,
                    Math.max(1, handle.screen().width), Math.max(1, handle.screen().height), ItemStack.EMPTY));
            ObserverPacketProbe.reset();
            require(handle.screen().mouseClicked(new MouseButtonEvent(1, 1,
                            new MouseButtonInfo(0, 0)), false), "Observer mouse input was not consumed");
            require(handle.screen().keyPressed(new KeyEvent(65, 0, 0)),
                    "Observer keyboard input was not consumed");
            require(ObserverPacketProbe.sends() == 0, "Nexus Observer input attempted a packet");
        });
        context.waitTicks(2);
        context.takeScreenshot(screenshot);
        context.runOnClient(client -> {
            ObserverPacketProbe.reset();
            require(handle.screen().keyPressed(new KeyEvent(256, 0, 0)), "Escape was not consumed");
            require(stops.get() == 1, "Escape did not request stop-observing exactly once");
            require(ObserverPacketProbe.sends() == 0, "Closing Observer mode attempted a packet");
            client.setScreenAndShow(null);
        });
        context.waitForScreen(null);
    }

    private static Screen clientScreen(ClientGameTestContext context,
                                       java.util.function.Supplier<Screen> factory) {
        return context.computeOnClient(client -> factory.get());
    }

    private static SpaceUnitMapPayload map(String name) {
        return new SpaceUnitMapPayload(UUID.randomUUID(), "local", name, "minecraft:overworld",
                1, 64, 1, TeleportInterfaceType.COMPASS, SpaceUnitMapPayload.NO_MAP_ID, List.of());
    }

    private static SpaceUnitMapPayload filledMap(String name, int mapId) {
        return new SpaceUnitMapPayload(UUID.randomUUID(), "local", name, "minecraft:overworld",
                1, 64, 1, TeleportInterfaceType.FILLED_MAP, mapId, List.of());
    }

    private static SpaceUnitFriendsPayload friends(int count) {
        java.util.ArrayList<SpaceUnitFriendsPayload.Entry> entries = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) entries.add(new SpaceUnitFriendsPayload.Entry(
                UUID.randomUUID(), "Friend " + index, index == 0, "friend"));
        return new SpaceUnitFriendsPayload(entries);
    }

    private static SpaceUnitRegistrationPreviewPayload registration(int tier) {
        return new SpaceUnitRegistrationPreviewPayload("minecraft:overworld", 2, 70, 2,
                tier, 84, 92, 7, 20);
    }

    private static DeathNodeAdminPayload death(int count) {
        java.util.ArrayList<DeathNodeAdminPayload.Entry> entries = new java.util.ArrayList<>();
        UUID owner = UUID.randomUUID();
        for (int index = 0; index < count; index++) entries.add(new DeathNodeAdminPayload.Entry(
                UUID.randomUUID(), owner, "Owner", "Node " + index, "active",
                "minecraft:overworld", 1, 64, 1, 1, 2,
                index == 0 ? List.of("duplicate_active_location") : List.of()));
        return new DeathNodeAdminPayload(entries, true, 1, 20, 43, 20L, true,
                null, null, "", 0L);
    }

    private static ObserverScreenSnapshot foreign(ObserverScreenSnapshot source, String family,
                                                   String variant, int protocol, long sequence) {
        return new ObserverScreenSnapshot(family, variant, protocol, sequence, source.title(), source.slots(),
                source.data(), source.metadata(), source.ownerPayload());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
