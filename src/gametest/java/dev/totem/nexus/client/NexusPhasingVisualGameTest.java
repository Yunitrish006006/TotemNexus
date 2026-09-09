package dev.totem.nexus.client;

import dev.totem.nexus.effect.NexusEffects;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Server-synchronized Phasing uses the native inventory effect panel and lighting path. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusPhasingVisualGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String oldLanguage = context.computeOnClient(client -> client.options.languageCode);
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.getInput().resizeWindow(1280, 720);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(NexusEffects.PHASING,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            });
            context.waitFor(client -> client.player.hasEffect(NexusEffects.PHASING) && client.player.isInvisible());
            for (String language : new String[]{"en_us", "zh_tw"}) {
                language(context, language);
                for (int scale : new int[]{2, 3}) {
                    context.runOnClient(client -> {
                        client.options.guiScale().set(scale);
                        client.setScreenAndShow(new InventoryScreen(client.player));
                    });
                    context.waitForScreen(InventoryScreen.class);
                    context.waitTicks(5);
                    context.runOnClient(client -> {
                        require(client.player.getActiveEffects().size() == 1
                                && client.player.getEffect(NexusEffects.PHASING).getAmplifier() == 0,
                                "Phasing created extra visible potion effects");
                        require(client.player.isInvisible(), "Phasing invisibility was not synchronized");
                        require(GameRenderer.nightVisionScale(client.player, 0) == 1.0F,
                                "Phasing did not use full native night-vision intensity");
                    });
                    context.getInput().setCursorPos(10, 10);
                    context.takeScreenshot("nexus-phasing-" + language + "-scale-" + scale);
                }
            }
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 1));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 1));
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200));
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 1200));
                player.removeEffect(NexusEffects.PHASING);
                require(player.getActiveEffects().size() == 4, "Server removed independent effects");
            });
            context.waitFor(client -> !client.player.hasEffect(NexusEffects.PHASING)
                    && client.player.getActiveEffects().size() == 4);
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(client.player.hasEffect(MobEffects.WEAKNESS)
                        && client.player.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1
                        && client.player.hasEffect(MobEffects.RESISTANCE)
                        && client.player.getEffect(MobEffects.RESISTANCE).getAmplifier() == 1
                        && client.player.hasEffect(MobEffects.NIGHT_VISION)
                        && client.player.hasEffect(MobEffects.INVISIBILITY) && client.player.isInvisible(),
                        "Removing Phasing changed independent potion effects");
                require(GameRenderer.nightVisionScale(client.player, 0) == 1.0F,
                        "Independent night vision stopped after Phasing removal");
            });
        } finally {
            context.runOnClient(client -> client.options.guiScale().set(oldScale));
            language(context, oldLanguage);
        }
    }

    private static void language(ClientGameTestContext context, String language) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> {
            client.options.languageCode = language;
            client.getLanguageManager().setSelected(language);
            reload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> reload.get() != null && reload.get().isDone());
        context.waitFor(client -> client.gui.overlay() == null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
