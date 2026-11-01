package dev.totem.nexus.client;

import dev.totem.nexus.space.NexusAccessE2eServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.nio.file.Files;
import static dev.totem.nexus.space.NexusAccessE2eServer.*;

public final class NexusAccessE2eClient implements ClientModInitializer {
    private boolean connected, opened, selected, granted, reopened;
    private boolean visualizationStarted, visualizationScreenshotRequested, visualizationDisabled;
    private boolean visualizationReenabled, disconnectRequested;
    private int ticks;
    private int screenshotTicks;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (exists("done") || exists("failure.txt")) { client.stop(); return; }
            try {
                if (++ticks > 20*240) throw new AssertionError("Client timed out");
                if (!connected && client.gui.screen() instanceof TitleScreen) {
                    connected = true;
                    var address = ServerAddress.parseString("127.0.0.1:25571");
                    ConnectScreen.startConnecting(client.gui.screen(),client,address,
                            new ServerData("Nexus Access E2E","127.0.0.1:25571",ServerData.Type.OTHER),false,null);
                }
                if (client.player == null || client.level == null || !exists("ready")) return;
                client.gui.toastManager().clear();
                boolean target = client.getUser().getName().equals("AccessTarget");
                if (target) target(client); else observer(client);
            } catch (Throwable error) { NexusAccessE2eServer.fail(error); client.stop(); }
        });
    }
    private void open(Minecraft client) {
        client.setScreenAndShow(new NexusAccessScreen(null,"lodestone",UNIT,UNIT,"administrator"));
    }
    private void target(Minecraft client) throws Exception {
        if (!opened && client.gui.screen()==null) { opened=true; open(client); mark("target-open"); }
        if (client.gui.screen() instanceof NexusAccessScreen screen && screen.observerPayload().players().stream().anyMatch(p->p.id().equals(OFFLINE))) {
            if (!selected && exists("observer-initial")) {
                screen.select(OFFLINE);
                for (var child : screen.children()) if (child instanceof EditBox box) box.setValue("PRIVATE_SEARCH_DO_NOT_RELAY");
                selected=true; mark("target-selected");
            }
            if (selected && !granted && exists("observer-updated")) {
                Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                    try (var owned = image) { owned.writeToFile(RESULTS.resolve("nexus-access-target.png")); }
                    catch (Exception error) { NexusAccessE2eServer.fail(error); }
                });
                granted=true; screen.submit(true); mark("target-granted");
            }
        }
        if (granted && !reopened && exists("grant-ok") && exists("observer-closed")) { reopened=true; open(client); }
        if (exists("access-done")) targetVisualization(client);
    }
    private void targetVisualization(Minecraft client) {
        if (!visualizationStarted) {
            client.setScreenAndShow(null);
            client.player.setYRot(180.0F);
            client.player.setXRot(20.0F);
            NexusArrayVisualizationClient.toggleArray("lodestone", UNIT);
            visualizationStarted=true;
            mark("target-visualization-requested");
            return;
        }
        if (!visualizationScreenshotRequested
                && NexusArrayVisualizationClient.isArrayEnabledFor(UNIT)
                && NexusArrayVisualizationClient.hasSnapshotFor(UNIT)
                && NexusArrayVisualizationClient.hasRendered(UNIT)) {
            visualizationScreenshotRequested=true;
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                try (var owned = image) {
                    owned.writeToFile(RESULTS.resolve("nexus-array-visualization-target.png"));
                    mark("target-visualization-rendered");
                } catch (Exception error) { NexusAccessE2eServer.fail(error); }
            });
            return;
        }
        if (!visualizationDisabled && exists("server-visualization-enabled") && exists("observer-overlay-absent")) {
            NexusArrayVisualizationClient.toggleArray("lodestone", UNIT);
            if (NexusArrayVisualizationClient.isActiveFor(UNIT))
                throw new AssertionError("Explicit disable retained the target overlay");
            visualizationDisabled=true;
            mark("target-visualization-disabled");
            return;
        }
        if (visualizationDisabled && !visualizationReenabled && exists("server-visualization-disabled")) {
            NexusArrayVisualizationClient.toggleArray("lodestone", UNIT);
            visualizationReenabled=true;
            return;
        }
        if (visualizationReenabled && !exists("target-visualization-reenabled")
                && NexusArrayVisualizationClient.hasSnapshotFor(UNIT)
                && NexusArrayVisualizationClient.hasRendered(UNIT)) {
            mark("target-visualization-reenabled");
            return;
        }
        if (!disconnectRequested && exists("server-visualization-reenabled")) {
            disconnectRequested=true;
            mark("target-disconnecting");
            client.stop();
        }
    }
    private void observer(Minecraft client) {
        if (exists("target-visualization-rendered") && exists("server-visualization-enabled")
                && !exists("observer-overlay-absent")) {
            if (NexusArrayVisualizationClient.isActiveFor(UNIT))
                throw new AssertionError("Observer client activated the target's world overlay");
            mark("observer-overlay-absent");
        }
        if (!(client.gui.screen() instanceof NexusAccessScreen screen)) {
            if (exists("target-granted")) mark("observer-closed");
            return;
        }
        if (!screen.totem$isObserverReadOnly()) throw new AssertionError("Observer received a mutable access screen");
        var offline = screen.observerPayload().players().stream().filter(p->p.id().equals(OFFLINE)).findFirst();
        if (offline.isEmpty()) return;
        for (var child : screen.children()) if (child instanceof EditBox box && !box.getValue().isEmpty())
            throw new AssertionError("Private search input leaked");
        if (!exists("observer-initial")) { mark("observer-initial"); return; }
        if (OFFLINE.equals(screen.observerSelection()) && exists("target-selected") && !exists("observer-updated")) {
            screen.select(java.util.UUID.randomUUID()); screen.submit(true); screen.submit(false);
            if (!OFFLINE.equals(screen.observerSelection())) throw new AssertionError("Observer mutated selection");
            if (++screenshotTicks < 3) return;
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                try (var owned = image) { owned.writeToFile(RESULTS.resolve("nexus-access-observer.png")); }
                catch (Exception error) { NexusAccessE2eServer.fail(error); }
            });
            mark("observer-updated");
        }
        if (exists("observer-closed") && offline.get().member() && !exists("stop-sent")) {
            screen.keyPressed(new KeyEvent(256,0,0)); mark("stop-sent");
        }
    }
}
