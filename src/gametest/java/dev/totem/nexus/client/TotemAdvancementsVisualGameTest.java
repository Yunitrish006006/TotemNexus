package dev.totem.nexus.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import java.util.List;

/** Captures the single shared TOTEM advancement page supplied by the visual bundle. */
@SuppressWarnings("UnstableApiUsage")
public final class TotemAdvancementsVisualGameTest implements FabricClientGameTest {
    private static final List<Branch> BRANCHES = List.of(
            new Branch("remnant_root", "totem-remnant"),
            new Branch("automata_root", "totem-automata"),
            new Branch("alchemy_root", "totem-alchemy"),
            new Branch("nexus_root", "totem-nexus"),
            new Branch("enchanting_root", "totem-enchanting"),
            new Branch("vanilla_tweaks_root", "totem-vanilla-tweaks"));
    private static Object creativeScreen;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runCommand("gamemode creative @a");
            context.waitFor(TotemAdvancementsVisualGameTest::hasCreativeAbilities);
            context.takeScreenshot("totem-standalone-overview-before");
            context.runOnClient(TotemAdvancementsVisualGameTest::openAdvancementsScreen);
            context.waitTicks(20);
            context.runOnClient(TotemAdvancementsVisualGameTest::selectSharedRootAndValidateBranches);
            context.waitTicks(2);
            context.takeScreenshot("totem-standalone-advancements-shared-root-after");

            if (hasStandaloneCreativeTabOwner()) {
                context.runOnClient(TotemAdvancementsVisualGameTest::openCreativeScreen);
                context.waitTicks(20);
                context.runOnClient(TotemAdvancementsVisualGameTest::selectTotemCreativeTab);
                context.waitTicks(2);
                context.takeScreenshot("totem-standalone-creative-main-after");
            }
            context.runOnClient(TotemAdvancementsVisualGameTest::closeScreen);
        }
    }

    private static boolean hasStandaloneCreativeTabOwner() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("totem-remnant")
                || loader.isModLoaded("totem-automata")
                || loader.isModLoaded("totem-alchemy");
    }

    private static void openAdvancementsScreen(Object client) {
        try {
            Object handler = advancementHandler(client);
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.advancements.AdvancementsScreen");
            Object screen = null;
            for (var constructor : screenClass.getConstructors()) {
                if (constructor.getParameterCount() == 1) {
                    screen = constructor.newInstance(handler);
                    break;
                }
            }
            if (screen == null) {
                throw new NoSuchMethodException("AdvancementsScreen(ClientAdvancementManager)");
            }
            invoke(client, "setScreenAndShow", screen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open the advancements screen", exception);
        }
    }

    private static void selectSharedRootAndValidateBranches(Object client) {
        try {
            Object handler = advancementHandler(client);
            Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
            Object rootId = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, "deadrecall", "root");
            Object root = invoke(handler, "get", rootId);
            if (root == null) {
                throw new IllegalStateException("Missing shared advancement root deadrecall:root");
            }
            invoke(handler, "setSelectedTab", root, true);

            for (Branch branch : BRANCHES) {
                if (!FabricLoader.getInstance().isModLoaded(branch.modId())) {
                    continue;
                }
                Object branchId = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class)
                        .invoke(null, "deadrecall", branch.id());
                Object advancement = invoke(handler, "get", branchId);
                if (advancement == null) {
                    throw new IllegalStateException("Missing TOTEM advancement branch deadrecall:" + branch.id());
                }
                Object value = invoke(advancement, "value");
                Object parent = ((java.util.Optional<?>) invoke(value, "parent")).orElse(null);
                if (!rootId.equals(parent)) {
                    throw new IllegalStateException("Advancement deadrecall:" + branch.id()
                            + " is not attached to deadrecall:root");
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not validate the shared TOTEM advancement page", exception);
        }
    }

    private static Object advancementHandler(Object client) throws ReflectiveOperationException {
        Object connection = invoke(client, "getConnection");
        if (connection == null) {
            throw new IllegalStateException("Client GameTest did not provide an advancement handler");
        }
        return invoke(connection, "getAdvancements");
    }

    private static void openCreativeScreen(Object client) {
        try {
            Object player = client.getClass().getField("player").get(client);
            Object level = client.getClass().getField("level").get(client);
            Object enabledFeatures = invoke(level, "enabledFeatures");
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen");
            Class<?> playerClass = Class.forName("net.minecraft.client.player.LocalPlayer");
            Class<?> featureFlagsClass = Class.forName("net.minecraft.world.flag.FeatureFlagSet");
            creativeScreen = screenClass
                    .getConstructor(playerClass, featureFlagsClass, boolean.class)
                    .newInstance(player, enabledFeatures, true);
            invoke(client, "setScreenAndShow", creativeScreen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open the Creative inventory", exception);
        }
    }

    private static void selectTotemCreativeTab(Object client) {
        try {
            if (creativeScreen == null) {
                throw new IllegalStateException("Creative inventory was not opened");
            }
            Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
            Object id = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, "deadrecall", "main");
            Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries")
                    .getField("CREATIVE_MODE_TAB")
                    .get(null);
            Object result = registry.getClass().getMethod("get", identifierClass).invoke(registry, id);
            Object tabHolder = ((java.util.Optional<?>) result)
                    .orElseThrow(() -> new IllegalStateException("Missing standalone Creative tab deadrecall:main"));
            Object tab = invoke(tabHolder, "value");
            if (tab == null) {
                throw new IllegalStateException("Missing standalone Creative tab deadrecall:main");
            }
            Class<?> tabClass = Class.forName("net.minecraft.world.item.CreativeModeTab");
            Class<?> screenExtension = Class.forName(
                    "net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen");
            boolean selected = (Boolean) screenExtension
                    .getMethod("setSelectedTab", tabClass)
                    .invoke(creativeScreen, tab);
            if (!selected) {
                throw new IllegalStateException("Could not switch to standalone Creative tab deadrecall:main");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not select the Totem Creative tab", exception);
        }
    }

    private static boolean hasCreativeAbilities(Object client) {
        try {
            Object player = client.getClass().getField("player").get(client);
            if (player == null) {
                return false;
            }
            Object abilities = invoke(player, "getAbilities");
            return abilities.getClass().getField("instabuild").getBoolean(abilities);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not verify the Creative-mode transition", exception);
        }
    }

    private static void closeScreen(Object client) {
        try {
            invoke(client, "setScreenAndShow", new Object[]{null});
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not close the advancements screen", exception);
        }
    }

    private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (var method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    private record Branch(String id, String modId) {
    }
}
