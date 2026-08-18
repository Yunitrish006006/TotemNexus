package dev.totem.nexus.client.manual;

import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.function.Consumer;

/** Adds compact vanilla-style diagrams to the seven Nexus manual pages. */
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
            Map.entry(PAGE_PREFIX + "9", NexusManualPageOverlay::renderAttributes),
            Map.entry(PAGE_PREFIX + "10", NexusManualPageOverlay::renderCopper),
            Map.entry(PAGE_PREFIX + "11", NexusManualPageOverlay::renderCatalyst),
            Map.entry(PAGE_PREFIX + "12", NexusManualPageOverlay::renderMaintenance)
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
        int y = context.pageTop() + 68;
        item(context, Items.BOOK, 45, y);
        arrow(context, 67, y + 8, 13);
        item(context, Items.LODESTONE, 83, y);
        arrow(context, 105, y + 8, 13);
        item(context, Items.WRITTEN_BOOK, 121, y);
        centered(context, "book.deadrecall.nexus_diagram.record", y + 24, MUTED);

        y += 55;
        item(context, Items.COMPASS, 58, y);
        plus(context, 79, y + 8);
        item(context, Items.LODESTONE, 91, y);
        arrow(context, 113, y + 8, 10);
        badge(context, 126, y + 1, "SU", GOOD);
        centered(context, "book.deadrecall.nexus_diagram.anchor_core", y + 24, GOOD);
    }

    private static void renderRegistration(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 59;
        step(context, 1, Items.LODESTONE, "book.deadrecall.nexus_diagram.place", y);
        step(context, 2, Items.COMPASS, "book.deadrecall.nexus_diagram.right_click", y + 34);
        step(context, 3, Items.COMPASS, "book.deadrecall.nexus_diagram.confirm_30s", y + 68);
        connection(context, 51, y + 18, y + 68);
        check(context, 132, y + 72);
        centered(context, "book.deadrecall.nexus_diagram.registered_bound", y + 94, GOOD);
    }

    private static void renderDiscovery(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 58;
        item(context, Items.COMPASS, 42, y);
        arrow(context, 64, y + 8, 10);
        item(context, Items.LODESTONE, 78, y);
        check(context, 103, y + 12);
        text(context, "book.deadrecall.nexus_diagram.attack_discover", 43, y + 23, MUTED);

        y += 48;
        item(context, Items.COMPASS, 42, y);
        badge(context, 67, y, "≤8", MUTED);
        arrow(context, 87, y + 8, 9);
        item(context, Items.FILLED_MAP, 100, y);
        text(context, "book.deadrecall.nexus_diagram.open_map", 43, y + 23, GOOD);
        text(context, "!", 43, y + 44, WARN);
        text(context, "book.deadrecall.nexus_diagram.hidden_until_explored", 56, y + 44, WARN);
    }

    private static void renderDestination(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 58;
        item(context, Items.FILLED_MAP, 42, y);
        arrow(context, 64, y + 8, 12);
        item(context, Items.LODESTONE, 80, y);
        arrow(context, 102, y + 8, 12);
        item(context, Items.ENDER_PEARL, 118, y);
        centered(context, "book.deadrecall.nexus_diagram.choose_target", y + 24, MUTED);

        y += 45;
        status(context, Items.SPYGLASS, "book.deadrecall.nexus_diagram.explored", y, true);
        status(context, Items.NAME_TAG, "book.deadrecall.nexus_diagram.permission", y + 26, true);
        status(context, Items.COOKED_BEEF, "book.deadrecall.nexus_diagram.resources", y + 52, true);
    }

    private static void renderPreparation(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 50;
        item(context, Items.CLOCK, 43, y);
        text(context, "book.deadrecall.nexus_diagram.preparing", 66, y + 5, MUTED);
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
        text(context, "book.deadrecall.nexus_diagram.no_full_shell", 56, y + 80, WARN);
    }

    private static void renderExpansion(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 58;
        item(context, Items.LODESTONE, 40, y);
        arrow(context, 61, y + 8, 7);
        item(context, Items.IRON_BLOCK, 71, y);
        arrow(context, 92, y + 8, 7);
        item(context, Items.DIAMOND_BLOCK, 102, y);
        arrow(context, 123, y + 8, 7);
        item(context, Items.NETHERITE_BLOCK, 133, y);
        centered(context, "book.deadrecall.nexus_diagram.extender_path", y + 23, MUTED);
        centered(context, "book.deadrecall.nexus_diagram.max_distance", y + 40, WARN);

        y += 66;
        capacityBar(context, y, 8, 0.34F, "I");
        capacityBar(context, y + 13, 24, 1.0F, "II");
        centered(context, "book.deadrecall.nexus_diagram.tier_capacity", y + 31, GOOD);
    }

    private static void renderMaterials(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 57;
        Item[] materials = {
                Items.STONE_BRICKS,
                Items.DEEPSLATE,
                Items.NETHER_BRICKS,
                Items.BLACKSTONE
        };
        for (int index = 0; index < materials.length; index++) {
            item(context, materials[index], 43 + index * 27, y);
        }
        centered(context, "book.deadrecall.nexus_diagram.valid_families", y + 23, MUTED);

        y += 43;
        item(context, Items.IRON_BLOCK, 44, y);
        plus(context, 65, y + 8);
        item(context, Items.DIAMOND_BLOCK, 77, y);
        centered(context, "book.deadrecall.nexus_diagram.refined_safe", y + 21, GOOD);

        y += 41;
        item(context, Items.CRACKED_STONE_BRICKS, 44, y);
        plus(context, 65, y + 8);
        item(context, Items.RAW_IRON_BLOCK, 77, y);
        centered(context, "book.deadrecall.nexus_diagram.worn_tradeoff", y + 21, WARN);
    }

    private static void renderAttributes(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 54;
        metric(context, Items.CHEST, "book.deadrecall.nexus_diagram.capacity", 42, y);
        metric(context, Items.SHIELD, "book.deadrecall.nexus_diagram.stability", 98, y);
        metric(context, Items.COMPASS, "book.deadrecall.nexus_diagram.lock", 42, y + 29);
        metric(context, Items.CLOCK, "book.deadrecall.nexus_diagram.phase", 98, y + 29);
        metric(context, Items.COOKED_BEEF, "book.deadrecall.nexus_diagram.food", 42, y + 58);
        metric(context, Items.AMETHYST_SHARD, "book.deadrecall.nexus_diagram.catalyst", 98, y + 58);

        y += 94;
        item(context, Items.FILLED_MAP, 48, y);
        arrow(context, 70, y + 8, 10);
        item(context, Items.SPYGLASS, 84, y);
        text(context, "book.deadrecall.nexus_diagram.live_values", 106, y + 5, WARN);
    }

    private static void renderCopper(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 58;
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

        y += 48;
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
        int y = context.pageTop() + 47;
        catalystEndpoint(context, Items.LODESTONE, 2, "book.deadrecall.nexus_diagram.source", y);
        catalystEndpoint(context, Items.LODESTONE, 2, "book.deadrecall.nexus_diagram.target", y + 31);

        y += 65;
        for (int index = 0; index < 4; index++) {
            miniItem(context, Items.AMETHYST_SHARD, context.pageLeft() + 39 + index * 15, y);
        }
        arrow(context, 102, y + 8, 10);
        text(context, "−1", 117, y + 3, GOOD);
        centered(context, "book.deadrecall.nexus_diagram.four_units", y + 21, MUTED);
        centered(context, "book.deadrecall.nexus_diagram.minimum_one", y + 40, WARN);
    }

    private static void renderMaintenance(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 47;
        item(context, Items.FILLED_MAP, 39, y);
        arrow(context, 61, y + 8, 8);
        item(context, Items.SPYGLASS, 72, y);
        arrow(context, 94, y + 8, 8);
        item(context, Items.CRACKED_STONE_BRICKS, 105, y);
        centered(context, "book.deadrecall.nexus_diagram.inspect", y + 23, MUTED);

        y += 40;
        item(context, Items.CRACKED_STONE_BRICKS, 42, y);
        plus(context, 63, y + 8);
        item(context, Items.STONE_BRICKS, 75, y);
        arrow(context, 97, y + 8, 10);
        item(context, Items.LODESTONE, 110, y);
        check(context, 129, y + 12);
        centered(context, "book.deadrecall.nexus_diagram.repair_rescan", y + 23, GOOD);

        y += 40;
        metric(context, Items.CHEST, "book.deadrecall.nexus_diagram.load_slots", 42, y);
        metric(context, Items.CLOCK, "book.deadrecall.nexus_diagram.recovery", 98, y);
        centered(context, "book.deadrecall.nexus_diagram.owner_admin", y + 23, WARN);
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
        text(context, label, 78, y + 4, accepted ? MUTED : WARN);
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
        text(context, label, 82, y + 4, INK);
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
        context.graphics().centeredText(context.font(), Component.translatable(key),
                context.pageLeft() + 93, y, color);
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
