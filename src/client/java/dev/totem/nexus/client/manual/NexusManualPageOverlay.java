package dev.totem.nexus.client.manual;

import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Adds compact vanilla-style diagrams to the focused Nexus manual pages. */
public final class NexusManualPageOverlay {
    private static final String PAGE_PREFIX = "book.deadrecall.nexus_teleport_manual.page.";
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int GOOD = 0xFF287A45;
    private static final int WARN = 0xFFA33A2B;
    private static final int TRACK = 0xFFBCA987;

    private static final Map<String, Consumer<TotemManualPageRenderContext>> PAGES = Map.ofEntries(
            Map.entry(PAGE_PREFIX + "1", NexusManualPageOverlay::renderIntroduction),
            Map.entry(PAGE_PREFIX + "2", NexusManualPageOverlay::renderRegistration),
            Map.entry(PAGE_PREFIX + "3", NexusManualPageOverlay::renderDiscovery),
            Map.entry(PAGE_PREFIX + "4", NexusManualPageOverlay::renderDestination),
            Map.entry(PAGE_PREFIX + "5", NexusManualPageOverlay::renderPreparation),
            Map.entry(PAGE_PREFIX + "6", NexusManualPageOverlay::renderStructure),
            Map.entry(PAGE_PREFIX + "7", NexusManualPageOverlay::renderExpansion),
            Map.entry(PAGE_PREFIX + "8", NexusManualPageOverlay::renderMaterials),
            Map.entry(PAGE_PREFIX + "9", NexusManualPageOverlay::renderScanProviders),
            Map.entry(PAGE_PREFIX + "10", NexusManualPageOverlay::renderAccuracyProviders),
            Map.entry(PAGE_PREFIX + "11", NexusManualPageOverlay::renderLockProviders),
            Map.entry(PAGE_PREFIX + "12", NexusManualPageOverlay::renderStabilityAndSafety),
            Map.entry(PAGE_PREFIX + "13", NexusManualPageOverlay::renderWearAndMaintenance),
            Map.entry(PAGE_PREFIX + "14", NexusManualPageOverlay::renderSpeedAndCooldown),
            Map.entry(PAGE_PREFIX + "15", NexusManualPageOverlay::renderFoodAndLoad),
            Map.entry(PAGE_PREFIX + "16", NexusManualPageOverlay::renderCopper),
            Map.entry(PAGE_PREFIX + "17", NexusManualPageOverlay::renderCatalyst),
            Map.entry(PAGE_PREFIX + "18", NexusManualPageOverlay::renderMaintenance)
    );

    private NexusManualPageOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-nexus", "manual_diagrams"),
                context -> {
                    Consumer<TotemManualPageRenderContext> renderer = PAGES.get(context.pageKey());
                    if (renderer != null) {
                        renderer.accept(context);
                    }
                }
        );
    }

    private static void renderIntroduction(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 64;
        item(context, Items.BOOK, 45, y);
        arrow(context, 67, y + 8, 13);
        item(context, Items.LODESTONE, 83, y);
        arrow(context, 105, y + 8, 13);
        item(context, Items.WRITTEN_BOOK, 121, y);
        centered(context, "book.deadrecall.nexus_diagram.record", y + 24, MUTED);

        y += 49;
        item(context, Items.COMPASS, 39, y);
        item(context, Items.RECOVERY_COMPASS, 65, y);
        item(context, Items.BOOK, 91, y);
        item(context, Items.FILLED_MAP, 117, y);
        centered(context, "book.deadrecall.nexus_diagram.anchor_core", y + 23, GOOD);
    }

    private static void renderRegistration(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 45;
        item(context, Items.COMPASS, 38, y);
        item(context, Items.RECOVERY_COMPASS, 64, y);
        item(context, Items.BOOK, 90, y);
        item(context, Items.FILLED_MAP, 116, y);
        check(context, 138, y + 12);
        centered(context, "book.deadrecall.nexus_diagram.interfaces_bind_manage", y + 22, GOOD);

        y += 54;
        item(context, Items.BOOK, 40, y);
        badge(context, 62, y, "R", MUTED);
        arrow(context, 83, y + 8, 9);
        item(context, Items.LODESTONE, 96, y);
        wrapped(context, "book.deadrecall.nexus_diagram.book_normal_manual", 40, y + 21, 112, MUTED);

        y += 43;
        item(context, Items.BOOK, 40, y);
        badge(context, 62, y, "⇩R", GOOD);
        arrow(context, 83, y + 8, 9);
        item(context, Items.LODESTONE, 96, y);
        wrapped(context, "book.deadrecall.nexus_diagram.book_sneak_nexus", 40, y + 21, 112, GOOD);
    }

    private static void renderDiscovery(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 43;
        item(context, Items.MAP, 36, y);
        arrow(context, 58, y + 8, 10);
        item(context, Items.LODESTONE, 72, y);
        arrow(context, 94, y + 8, 10);
        item(context, Items.FILLED_MAP, 108, y);
        badge(context, 132, y, "ID", GOOD);
        centered(context, "book.deadrecall.nexus_diagram.empty_map_create", y + 23, GOOD);

        y += 51;
        item(context, Items.FILLED_MAP, 40, y);
        plus(context, 61, y + 8);
        item(context, Items.PAPER, 73, y);
        arrow(context, 95, y + 8, 9);
        item(context, Items.FILLED_MAP, 108, y);
        badge(context, 132, y, "◎", GOOD);
        centered(context, "book.deadrecall.nexus_diagram.exact_center", y + 23, MUTED);

        y += 49;
        text(context, "!", 40, y + 2, WARN);
        wrapped(context, "book.deadrecall.nexus_diagram.scale_anchor", 54, y, 98, WARN);
    }

    private static void renderDestination(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 43;
        item(context, Items.FILLED_MAP, 38, y);
        arrow(context, 60, y + 8, 8);
        item(context, Items.LODESTONE, 72, y);
        plus(context, 93, y + 8);
        item(context, Items.NAME_TAG, 105, y);
        check(context, 128, y + 12);
        wrapped(context, "book.deadrecall.nexus_diagram.named_markers", 40, y + 22, 112, GOOD);

        y += 51;
        item(context, Items.PLAYER_HEAD, 42, y);
        plus(context, 63, y + 8);
        badge(context, 76, y, "→", WARN);
        arrow(context, 98, y + 8, 8);
        item(context, Items.BARRIER, 110, y);
        wrapped(context, "book.deadrecall.nexus_diagram.no_player_edges", 40, y + 22, 112, WARN);

        y += 49;
        centered(context, "book.deadrecall.nexus_diagram.choose_target", y, MUTED);
    }

    private static void renderPreparation(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 50;
        item(context, Items.CLOCK, 43, y);
        wrapped(context, "book.deadrecall.nexus_diagram.preparing", 66, y + 5, 86, MUTED);
        status(context, Items.COMPASS, "book.deadrecall.nexus_diagram.hold_item", y + 25, true);
        status(context, Items.LEATHER_BOOTS, "book.deadrecall.nexus_diagram.move_limit", y + 49, true);
        status(context, Items.SHIELD, "book.deadrecall.nexus_diagram.damage_cancels", y + 73, false);
        status(context, Items.ENDER_EYE, "book.deadrecall.nexus_diagram.dimension_cancels", y + 97, false);
    }

    private static void renderStructure(TotemManualPageRenderContext context) {
        int startX = context.pageLeft() + 48;
        int y = context.pageTop() + 57;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                Item item = row == 1 && column == 1 ? Items.LODESTONE : Items.STONE_BRICKS;
                miniItem(context, item, startX + column * 19, y + row * 19);
            }
        }
        text(context, "3×3×3", 109, y + 20, MUTED);

        centered(context, "book.deadrecall.nexus_diagram.initial_26", y + 59, MUTED);
        text(context, "!", 43, y + 80, WARN);
        wrapped(context, "book.deadrecall.nexus_diagram.no_full_shell", 56, y + 80, 96, WARN);
    }

    private static void renderExpansion(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 38;
        item(context, Items.LODESTONE, 40, y);
        arrow(context, 61, y + 8, 7);
        item(context, Items.IRON_BLOCK, 71, y);
        arrow(context, 92, y + 8, 7);
        item(context, Items.DIAMOND_BLOCK, 102, y);
        arrow(context, 123, y + 8, 7);
        item(context, Items.NETHERITE_BLOCK, 133, y);
        centered(context, "book.deadrecall.nexus_diagram.extender_path", y + 21, MUTED);
        centered(context, "book.deadrecall.nexus_diagram.max_distance", y + 32, WARN);

        y += 43;
        capacityBar(context, y, 8, 0.34F, "I");
        capacityBar(context, y + 11, 24, 1.0F, "II");
        wrapped(context, "book.deadrecall.nexus_diagram.capacity_defaults", 40, y + 24, 112, GOOD);
    }

    private static void renderMaterials(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 38;
        Item[] materials = {
                Items.STONE_BRICKS,
                Items.TUFF,
                Items.DEEPSLATE_BRICKS,
                Items.NETHER_BRICKS,
                Items.POLISHED_BLACKSTONE,
                Items.OBSIDIAN,
                Items.CRYING_OBSIDIAN
        };
        for (int index = 0; index < materials.length; index++) {
            item(context, materials[index], 40 + index * 18, y);
        }
        wrapped(context, "book.deadrecall.nexus_diagram.valid_families", 40, y + 19, 112, MUTED);

        y += 38;
        item(context, Items.IRON_BLOCK, 44, y);
        plus(context, 65, y + 8);
        item(context, Items.DIAMOND_BLOCK, 77, y);
        plus(context, 98, y + 8);
        item(context, Items.NETHERITE_BLOCK, 110, y);
        wrapped(context, "book.deadrecall.nexus_diagram.refined_safe", 40, y + 19, 112, GOOD);

        y += 40;
        item(context, Items.CRACKED_STONE_BRICKS, 44, y);
        plus(context, 65, y + 8);
        item(context, Items.RAW_IRON_BLOCK, 77, y);
        wrapped(context, "book.deadrecall.nexus_diagram.worn_tradeoff", 40, y + 19, 112, WARN);
    }

    private static void renderScanProviders(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 40;
        Item[] plusOne = {
                vanillaItem("copper_bulb"), Items.IRON_BLOCK,
                Items.AMETHYST_BLOCK, Items.REDSTONE_BLOCK
        };
        for (int index = 0; index < plusOne.length; index++) {
            itemValue(context, plusOne[index], 48 + index * 27, y, "+1", GOOD);
        }
        wrapped(context, "book.deadrecall.nexus_diagram.scan_plus_one", 40, y + 26, 112, GOOD);

        y += 34;
        itemValue(context, Items.DIAMOND_BLOCK, 68, y, "+2", GOOD);
        itemValue(context, Items.NETHERITE_BLOCK, 105, y, "+2", GOOD);
        wrapped(context, "book.deadrecall.nexus_diagram.scan_plus_two", 40, y + 26, 112, GOOD);

        y += 34;
        itemValue(context, Items.IRON_ORE, 47, y, "−1", WARN);
        itemValue(context, Items.DEEPSLATE_DIAMOND_ORE, 75, y, "−1", WARN);
        itemValue(context, Items.NETHER_QUARTZ_ORE, 103, y, "−1", WARN);
        wrapped(context, "book.deadrecall.nexus_diagram.ore_reach_penalty", 40, y + 26, 112, WARN);
    }

    private static void renderAccuracyProviders(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 40;
        Item[] plusTwo = {
                Items.CHISELED_STONE_BRICKS, Items.CHISELED_DEEPSLATE,
                Items.CHISELED_NETHER_BRICKS, vanillaItem("cut_copper"),
                Items.AMETHYST_BLOCK, Items.DIAMOND_BLOCK, Items.LAPIS_BLOCK
        };
        for (int index = 0; index < plusTwo.length; index++) {
            itemValue(context, plusTwo[index], 29 + index * 18, y, "+2", GOOD);
        }
        wrapped(context, "book.deadrecall.nexus_diagram.accuracy_plus_two", 40, y + 26, 112, GOOD);

        y += 34;
        Item[] plusOne = {
                Items.DEEPSLATE_TILES, Items.RED_NETHER_BRICKS,
                Items.CHISELED_POLISHED_BLACKSTONE, vanillaItem("copper_block"),
                vanillaItem("chiseled_copper"), Items.QUARTZ_BLOCK
        };
        for (int index = 0; index < plusOne.length; index++) {
            itemValue(context, plusOne[index], 34 + index * 20, y, "+1", GOOD);
        }
        wrapped(context, "book.deadrecall.nexus_diagram.accuracy_plus_one", 40, y + 26, 112, GOOD);

        y += 34;
        Item[] ores = {
                Items.LAPIS_ORE, Items.REDSTONE_ORE, Items.DIAMOND_ORE,
                Items.EMERALD_ORE, Items.NETHER_QUARTZ_ORE
        };
        for (int index = 0; index < ores.length; index++) {
            itemValue(context, ores[index], 43 + index * 23, y, "+1", GOOD);
        }
        wrapped(context, "book.deadrecall.nexus_diagram.accuracy_ores", 40, y + 26, 112, MUTED);
    }

    private static void renderLockProviders(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 40;
        itemValue(context, vanillaItem("chiseled_copper"), 67, y, "+3", GOOD);
        itemValue(context, Items.CHISELED_POLISHED_BLACKSTONE, 104, y, "+3", GOOD);
        wrapped(context, "book.deadrecall.nexus_diagram.lock_plus_three", 40, y + 26, 112, GOOD);

        y += 34;
        Item[] plusTwo = {
                Items.CHISELED_STONE_BRICKS, Items.CHISELED_DEEPSLATE,
                Items.CHISELED_NETHER_BRICKS, Items.LAPIS_BLOCK
        };
        for (int index = 0; index < plusTwo.length; index++) {
            itemValue(context, plusTwo[index], 48 + index * 27, y, "+2", GOOD);
        }
        wrapped(context, "book.deadrecall.nexus_diagram.lock_plus_two", 40, y + 26, 112, GOOD);

        y += 34;
        Item[] plusOne = {
                vanillaItem("cut_copper"), Items.DEEPSLATE_TILES, Items.AMETHYST_BLOCK,
                Items.LAPIS_ORE, Items.REDSTONE_ORE, Items.DIAMOND_ORE, Items.EMERALD_ORE
        };
        for (int index = 0; index < plusOne.length; index++) {
            itemValue(context, plusOne[index], 29 + index * 18, y, "+1", GOOD);
        }
        wrapped(context, "book.deadrecall.nexus_diagram.lock_plus_one", 40, y + 26, 112, MUTED);
    }

    private static void renderStabilityAndSafety(TotemManualPageRenderContext context) {
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.stability_heading",
                new Item[]{
                        Items.NETHERITE_BLOCK, Items.DIAMOND_BLOCK, Items.DEEPSLATE_BRICKS,
                        Items.POLISHED_BLACKSTONE_BRICKS, Items.IRON_BLOCK
                },
                new int[]{3, 2, 2, 2, 1},
                context.pageTop() + 42, GOOD);
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.safety_heading",
                new Item[]{
                        Items.NETHERITE_BLOCK, Items.DIAMOND_BLOCK,
                        Items.POLISHED_BLACKSTONE_BRICKS, Items.ANCIENT_DEBRIS,
                        Items.IRON_BLOCK, Items.AMETHYST_BLOCK
                },
                new int[]{3, 2, 2, 2, 1, 1},
                context.pageTop() + 99, GOOD);
    }

    private static void renderWearAndMaintenance(TotemManualPageRenderContext context) {
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.wear_heading",
                new Item[]{
                        Items.NETHERITE_BLOCK, Items.DIAMOND_BLOCK, Items.DEEPSLATE_BRICKS,
                        Items.ANCIENT_DEBRIS, Items.IRON_BLOCK, Items.POLISHED_BLACKSTONE_BRICKS
                },
                new int[]{3, 2, 2, 2, 1, 1},
                context.pageTop() + 40, GOOD);
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.maintenance_heading",
                new Item[]{
                        Items.POLISHED_DEEPSLATE, Items.POLISHED_BLACKSTONE, Items.EMERALD_BLOCK,
                        Items.STONE_BRICKS, vanillaItem("copper_block"), Items.IRON_BLOCK,
                        Items.DIAMOND_BLOCK
                },
                new int[]{2, 2, 2, 1, 1, 1, 1},
                context.pageTop() + 75, GOOD);
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.interference_heading",
                new Item[]{
                        Items.MOSSY_STONE_BRICKS, Items.POLISHED_BLACKSTONE,
                        Items.CHISELED_POLISHED_BLACKSTONE, Items.NETHERITE_BLOCK,
                        Items.ANCIENT_DEBRIS
                },
                new int[]{2, 2, 1, 1, 1},
                context.pageTop() + 110, GOOD);
    }

    private static void renderSpeedAndCooldown(TotemManualPageRenderContext context) {
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.speed_heading",
                new Item[]{
                        Items.REDSTONE_BLOCK, Items.GOLD_BLOCK, Items.NETHER_BRICKS,
                        vanillaItem("copper_grate"), vanillaItem("copper_block"),
                        Items.QUARTZ_BLOCK, Items.COAL_BLOCK
                },
                new int[]{3, 2, 2, 2, 1, 1, 1},
                context.pageTop() + 42, GOOD);
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.cooldown_heading",
                new Item[]{
                        vanillaItem("copper_bulb"), Items.GOLD_BLOCK, Items.REDSTONE_BLOCK,
                        vanillaItem("copper_block"), vanillaItem("copper_grate"),
                        Items.EMERALD_BLOCK, Items.NETHER_BRICKS
                },
                new int[]{2, 2, 2, 1, 1, 1, 1},
                context.pageTop() + 99, GOOD);
    }

    private static void renderFoodAndLoad(TotemManualPageRenderContext context) {
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.food_heading",
                new Item[]{
                        Items.GOLD_BLOCK, Items.EMERALD_BLOCK, Items.POLISHED_DEEPSLATE,
                        Items.QUARTZ_BLOCK, Items.RAW_GOLD_BLOCK
                },
                new int[]{2, 2, 1, 1, 1},
                context.pageTop() + 42, GOOD);
        materialAttribute(context,
                "book.deadrecall.nexus_diagram.load_heading",
                new Item[]{
                        vanillaItem("copper_bulb"), Items.IRON_BLOCK, Items.NETHERITE_BLOCK,
                        vanillaItem("copper_block"), Items.DIAMOND_BLOCK,
                        Items.POLISHED_BLACKSTONE_BRICKS, Items.REDSTONE_BLOCK
                },
                new int[]{2, 2, 2, 1, 1, 1, 1},
                context.pageTop() + 99, GOOD);
    }

    private static void renderCopper(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 50;
        Item[] copper = {
                vanillaItem("copper_block"),
                vanillaItem("exposed_copper"),
                vanillaItem("weathered_copper"),
                vanillaItem("oxidized_copper")
        };
        for (int index = 0; index < copper.length; index++) {
            item(context, copper[index], 39 + index * 29, y);
            if (index < copper.length - 1) {
                arrow(context, 57 + index * 29, y + 8, 8);
            }
        }
        text(context, "−", 129, y + 4, WARN);
        centered(context, "book.deadrecall.nexus_diagram.oxidation_weakens", y + 23, WARN);

        y += 44;
        item(context, Items.HONEYCOMB, 54, y);
        plus(context, 75, y + 8);
        item(context, vanillaItem("copper_block"), 87, y);
        arrow(context, 109, y + 8, 10);
        item(context, vanillaItem("waxed_copper_block"), 122, y);
        check(context, 137, y + 12);
        centered(context, "book.deadrecall.nexus_diagram.wax_protects", y + 23, GOOD);
        centered(context, "book.deadrecall.nexus_diagram.copper_order", y + 44, MUTED);
    }

    private static void renderCatalyst(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 58;
        catalystEndpoint(context, Items.LODESTONE, 2, "book.deadrecall.nexus_diagram.source", y);
        catalystEndpoint(context, Items.LODESTONE, 2, "book.deadrecall.nexus_diagram.target", y + 26);

        y += 55;
        for (int index = 0; index < 4; index++) {
            miniItem(context, Items.AMETHYST_SHARD, context.pageLeft() + 39 + index * 15, y);
        }
        arrow(context, 102, y + 8, 10);
        text(context, "−1", 117, y + 3, GOOD);
        centered(context, "book.deadrecall.nexus_diagram.four_units", y + 21, MUTED);
        centered(context, "book.deadrecall.nexus_diagram.minimum_one", y + 36, WARN);
    }

    private static void renderMaintenance(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 35;
        item(context, Items.FILLED_MAP, 39, y);
        arrow(context, 61, y + 8, 8);
        item(context, Items.SPYGLASS, 72, y);
        arrow(context, 94, y + 8, 8);
        item(context, Items.CRACKED_STONE_BRICKS, 105, y);
        centered(context, "book.deadrecall.nexus_diagram.inspect", y + 19, MUTED);

        y += 35;
        item(context, Items.CRACKED_STONE_BRICKS, 42, y);
        plus(context, 63, y + 8);
        item(context, Items.STONE_BRICKS, 75, y);
        arrow(context, 97, y + 8, 10);
        item(context, Items.LODESTONE, 110, y);
        check(context, 129, y + 12);
        centered(context, "book.deadrecall.nexus_diagram.repair_rescan", y + 19, GOOD);

        y += 36;
        metric(context, Items.CHEST, "book.deadrecall.nexus_diagram.load_slots_short", 38, y);
        metric(context, Items.CLOCK, "book.deadrecall.nexus_diagram.recovery_short", 101, y);
        centered(context, "book.deadrecall.nexus_diagram.owner_admin", y + 19, WARN);
        wrapped(context, "book.deadrecall.nexus_diagram.live_truth", 40, y + 30, 116, WARN);
    }

    private static void materialAttribute(
            TotemManualPageRenderContext context,
            String heading,
            Item[] materials,
            int[] values,
            int y,
            int color
    ) {
        if (materials.length != values.length) {
            throw new IllegalArgumentException("Each material needs one score");
        }
        text(context, heading, 40, y, color);
        int spacing = materials.length >= 7 ? 18 : 20;
        int totalWidth = 16 + (materials.length - 1) * spacing;
        int start = 93 - totalWidth / 2;
        for (int index = 0; index < materials.length; index++) {
            itemValue(context, materials[index], start + index * spacing, y + 10,
                    "+" + values[index], GOOD);
        }
    }

    private static void itemValue(
            TotemManualPageRenderContext context,
            Item item,
            int localX,
            int y,
            String value,
            int color
    ) {
        item(context, item, localX, y);
        Component label = Component.literal(value);
        context.graphics().text(context.font(), label,
                context.pageLeft() + localX + (16 - context.font().width(label)) / 2,
                y + 16, color, false);
    }

    private static void wrapped(
            TotemManualPageRenderContext context,
            String key,
            int localX,
            int y,
            int width,
            int color
    ) {
        List<FormattedCharSequence> lines = context.font().split(Component.translatable(key), width);
        for (int index = 0; index < lines.size(); index++) {
            context.graphics().text(context.font(), lines.get(index),
                    context.pageLeft() + localX,
                    y + index * context.font().lineHeight,
                    color,
                    false);
        }
    }

    private static void status(
            TotemManualPageRenderContext context,
            Item item,
            String label,
            int y,
            boolean accepted
    ) {
        item(context, item, 43, y);
        text(context, accepted ? "✓" : "!", 65, y + 4, accepted ? GOOD : WARN);
        wrapped(context, label, 78, y + 4, 74, accepted ? MUTED : WARN);
    }

    private static void catalystEndpoint(
            TotemManualPageRenderContext context,
            Item endpoint,
            int count,
            String label,
            int y
    ) {
        item(context, endpoint, 43, y);
        plus(context, 64, y + 8);
        item(context, Items.AMETHYST_SHARD, 76, y);
        text(context, "×" + count, 94, y + 5, MUTED);
        text(context, label, 115, y + 5, INK);
    }

    private static void step(
            TotemManualPageRenderContext context,
            int number,
            Item item,
            String label,
            int y
    ) {
        badge(context, 40, y + 2, Integer.toString(number), MUTED);
        item(context, item, 59, y);
        wrapped(context, label, 82, y + 4, 70, INK);
    }

    private static void metric(
            TotemManualPageRenderContext context,
            Item item,
            String label,
            int x,
            int y
    ) {
        item(context, item, x, y);
        text(context, label, x + 19, y + 5, MUTED);
    }

    private static void capacityBar(
            TotemManualPageRenderContext context,
            int y,
            int value,
            float fill,
            String tier
    ) {
        int x = context.pageLeft() + 44;
        context.graphics().fill(x, y, x + 72, y + 7, TRACK);
        context.graphics().fill(x + 1, y + 1, x + 1 + Math.round(70 * fill), y + 6, GOOD);
        text(context, Integer.toString(value), 119, y - 1, MUTED);
        text(context, tier, 139, y - 1, INK);
    }

    private static void item(TotemManualPageRenderContext context, Item item, int localX, int y) {
        renderItem(context, new ItemStack(item), context.pageLeft() + localX, y, 16);
    }

    private static void miniItem(TotemManualPageRenderContext context, Item item, int x, int y) {
        renderItem(context, new ItemStack(item), x, y, 14);
    }

    private static void renderItem(
            TotemManualPageRenderContext context,
            ItemStack stack,
            int x,
            int y,
            int hitSize
    ) {
        context.graphics().item(stack, x, y);
        if (inside(context, x, y, hitSize, hitSize)) {
            context.graphics().setTooltipForNextFrame(
                    context.font(), stack, context.mouseX(), context.mouseY());
        }
    }

    private static void badge(
            TotemManualPageRenderContext context,
            int localX,
            int y,
            String label,
            int color
    ) {
        int x = context.pageLeft() + localX;
        context.graphics().fill(x, y, x + 16, y + 16, 0xFFE9DFC8);
        context.graphics().outline(x, y, 16, 16, color);
        Component component = Component.literal(label);
        context.graphics().text(context.font(), component,
                x + (16 - context.font().width(component)) / 2, y + 4, color, false);
    }

    private static void arrow(TotemManualPageRenderContext context, int localX, int y, int length) {
        int x = context.pageLeft() + localX;
        line(context, x, y, x + length, y, MUTED);
        arrowHead(context, x + length, y, MUTED);
    }

    private static void arrowHead(TotemManualPageRenderContext context, int x, int y, int color) {
        context.graphics().fill(x - 3, y - 2, x + 1, y + 3, color);
        context.graphics().fill(x - 1, y - 4, x + 1, y + 5, color);
    }

    private static void connection(
            TotemManualPageRenderContext context,
            int localX,
            int y1,
            int y2
    ) {
        int x = context.pageLeft() + localX;
        context.graphics().fill(x, y1, x + 1, y2, TRACK);
    }

    private static void line(
            TotemManualPageRenderContext context,
            int x1,
            int y1,
            int x2,
            int y2,
            int color
    ) {
        if (y1 == y2) {
            context.graphics().fill(x1, y1, x2 + 1, y1 + 1, color);
        }
    }

    private static void plus(TotemManualPageRenderContext context, int localX, int y) {
        text(context, "+", localX, y - 4, MUTED);
    }

    private static void check(TotemManualPageRenderContext context, int localX, int y) {
        text(context, "✓", localX, y - 4, GOOD);
    }

    private static void centered(
            TotemManualPageRenderContext context,
            String key,
            int y,
            int color
    ) {
        List<FormattedCharSequence> lines = context.font().split(Component.translatable(key), 112);
        for (int index = 0; index < lines.size(); index++) {
            context.graphics().centeredText(context.font(), lines.get(index),
                    context.pageLeft() + 93,
                    y + index * context.font().lineHeight,
                    color);
        }
    }

    private static void text(
            TotemManualPageRenderContext context,
            String keyOrLiteral,
            int localX,
            int y,
            int color
    ) {
        Component component = keyOrLiteral.contains(".")
                ? Component.translatable(keyOrLiteral)
                : Component.literal(keyOrLiteral);
        context.graphics().text(context.font(), component,
                context.pageLeft() + localX, y, color, false);
    }

    private static boolean inside(
            TotemManualPageRenderContext context,
            int x,
            int y,
            int width,
            int height
    ) {
        return context.mouseX() >= x && context.mouseX() < x + width
                && context.mouseY() >= y && context.mouseY() < y + height;
    }

    private static Item vanillaItem(String path) {
        return BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("minecraft", path));
    }
}
