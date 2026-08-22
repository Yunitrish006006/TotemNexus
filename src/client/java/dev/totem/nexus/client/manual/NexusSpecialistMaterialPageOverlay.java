package dev.totem.nexus.client.manual;

import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import dev.totem.nexus.client.NexusMaterialCatalogClientState;
import dev.totem.nexus.network.MaterialCatalogPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders server-authoritative material recommendations for the Nexus specialist manual pages. */
public final class NexusSpecialistMaterialPageOverlay {
    private static final String PAGE_PREFIX = "book.deadrecall.nexus_teleport_manual.page.";
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int GOOD = 0xFF287A45;
    private static final int WARN = 0xFFA33A2B;
    private static final long REFRESH_INTERVAL_NANOS = 2_000_000_000L;
    private static volatile long lastRefreshNanos;

    private NexusSpecialistMaterialPageOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-nexus", "specialist_material_pages"),
                context -> {
                    Specialist specialist = specialist(context.pageKey());
                    if (specialist != null) {
                        render(context, specialist);
                    }
                }
        );
    }

    private static Specialist specialist(String pageKey) {
        return switch (pageKey) {
            case PAGE_PREFIX + "19" -> Specialist.BACKBONE;
            case PAGE_PREFIX + "20" -> Specialist.EXPANSION;
            case PAGE_PREFIX + "21" -> Specialist.PRECISION;
            case PAGE_PREFIX + "22" -> Specialist.SPEED;
            case PAGE_PREFIX + "23" -> Specialist.EFFICIENCY;
            case PAGE_PREFIX + "24" -> Specialist.CROSS_DIMENSION;
            default -> null;
        };
    }

    private static void render(TotemManualPageRenderContext context, Specialist specialist) {
        requestRefresh();
        MaterialCatalogPayload catalog = NexusMaterialCatalogClientState.snapshot();
        if (catalog.entries().isEmpty()) {
            centered(context, Component.translatable("book.deadrecall.nexus_specialist.loading"),
                    context.pageTop() + 72, WARN);
            return;
        }

        centered(context,
                Component.translatable("book.deadrecall.nexus_specialist.server_revision", catalog.revision()),
                context.pageTop() + 39, MUTED);

        List<MaterialCatalogPayload.Entry> ranked = ranked(catalog.entries(), specialist);
        if (ranked.isEmpty()) {
            centered(context, Component.translatable("book.deadrecall.nexus_specialist.none"),
                    context.pageTop() + 72, WARN);
            return;
        }

        int y = context.pageTop() + 52;
        for (int index = 0; index < ranked.size(); index++) {
            drawRow(context, specialist, ranked.get(index), index + 1, y + index * 24);
        }
    }

    private static void requestRefresh() {
        long now = System.nanoTime();
        if (now - lastRefreshNanos < REFRESH_INTERVAL_NANOS) {
            return;
        }
        lastRefreshNanos = now;
        NexusMaterialCatalogClientState.requestRefresh();
    }

    private static List<MaterialCatalogPayload.Entry> ranked(
            List<MaterialCatalogPayload.Entry> source,
            Specialist specialist
    ) {
        Map<String, MaterialCatalogPayload.Entry> uniqueProfiles = new LinkedHashMap<>();
        for (MaterialCatalogPayload.Entry entry : source) {
            if (entry.validStructureMaterial()) {
                uniqueProfiles.putIfAbsent(entry.profileId(), entry);
            }
        }

        List<MaterialCatalogPayload.Entry> ranked = new ArrayList<>();
        for (MaterialCatalogPayload.Entry entry : uniqueProfiles.values()) {
            if (specialist.eligible(entry)) {
                ranked.add(entry);
            }
        }
        ranked.sort(Comparator
                .comparingInt((MaterialCatalogPayload.Entry entry) -> specialist.score(entry))
                .reversed()
                .thenComparing(MaterialCatalogPayload.Entry::blockId));
        return ranked.size() <= 5 ? List.copyOf(ranked) : List.copyOf(ranked.subList(0, 5));
    }

    private static void drawRow(
            TotemManualPageRenderContext context,
            Specialist specialist,
            MaterialCatalogPayload.Entry entry,
            int rank,
            int y
    ) {
        ItemStack stack = stackFor(entry.blockId());
        Component rankLabel = Component.literal(rank + ".");
        context.graphics().text(context.font(), rankLabel,
                context.pageLeft() + 34, y + 4, MUTED, false);

        if (!stack.isEmpty()) {
            int itemX = context.pageLeft() + 48;
            context.graphics().item(stack, itemX, y);
            if (inside(context, itemX, y, 16, 16)) {
                context.graphics().setTooltipForNextFrame(
                        context.font(), stack, context.mouseX(), context.mouseY());
            }
        }

        Component name = stack.isEmpty() ? Component.literal(entry.blockId()) : stack.getHoverName();
        List<FormattedCharSequence> nameLines = context.font().split(name, 82);
        if (!nameLines.isEmpty()) {
            context.graphics().text(context.font(), nameLines.getFirst(),
                    context.pageLeft() + 68, y + 1, INK, false);
        }

        Component metrics = specialist.metrics(entry);
        context.graphics().text(context.font(), metrics,
                context.pageLeft() + 68, y + 11, GOOD, false);

        int worst = entry.attributes().values().stream().min(Integer::compareTo).orElse(0);
        if (worst < 0) {
            Component penalty = Component.literal("▼" + worst);
            context.graphics().text(context.font(), penalty,
                    context.pageLeft() + 139, y + 1, WARN, false);
        }
    }

    private static ItemStack stackFor(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null || block.asItem() == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(block.asItem());
    }

    private static int maxAffinity(MaterialCatalogPayload.Entry entry) {
        return entry.dimensionAffinity().values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static void centered(
            TotemManualPageRenderContext context,
            Component component,
            int y,
            int color
    ) {
        context.graphics().centeredText(context.font(), component,
                context.pageLeft() + 93, y, color);
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

    private enum Specialist {
        BACKBONE("book.deadrecall.nexus_specialist.metrics.backbone") {
            @Override
            boolean eligible(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("structure_capacity") > 1
                        || entry.attribute("stability") > 1
                        || entry.attribute("wear_resistance") > 1;
            }

            @Override
            int score(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("structure_capacity") * 3
                        + entry.attribute("stability") * 2
                        + entry.attribute("wear_resistance") * 2
                        + entry.attribute("arrival_safety")
                        + entry.attribute("route_load_capacity");
            }

            @Override
            Component metrics(MaterialCatalogPayload.Entry entry) {
                return metricComponent(entry,
                        "structure_capacity", "stability", "wear_resistance");
            }
        },
        EXPANSION("book.deadrecall.nexus_specialist.metrics.expansion") {
            @Override
            boolean eligible(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("scan_expansion_radius") > 0;
            }

            @Override
            int score(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("scan_expansion_radius") * 4
                        + entry.attribute("structure_capacity")
                        + entry.attribute("stability");
            }

            @Override
            Component metrics(MaterialCatalogPayload.Entry entry) {
                return metricComponent(entry,
                        "scan_expansion_radius", "structure_capacity");
            }
        },
        PRECISION("book.deadrecall.nexus_specialist.metrics.precision") {
            @Override
            boolean eligible(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("arrival_accuracy") > 0 || entry.attribute("target_lock") > 0;
            }

            @Override
            int score(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("arrival_accuracy") * 3
                        + entry.attribute("target_lock") * 3
                        + entry.attribute("arrival_safety");
            }

            @Override
            Component metrics(MaterialCatalogPayload.Entry entry) {
                return metricComponent(entry, "arrival_accuracy", "target_lock");
            }
        },
        SPEED("book.deadrecall.nexus_specialist.metrics.speed") {
            @Override
            boolean eligible(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("phase_speed") > 0 || entry.attribute("cooldown_recovery") > 0;
            }

            @Override
            int score(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("phase_speed") * 3
                        + entry.attribute("cooldown_recovery") * 2
                        + entry.attribute("route_load_capacity");
            }

            @Override
            Component metrics(MaterialCatalogPayload.Entry entry) {
                return metricComponent(entry, "phase_speed", "cooldown_recovery");
            }
        },
        EFFICIENCY("book.deadrecall.nexus_specialist.metrics.efficiency") {
            @Override
            boolean eligible(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("food_efficiency") > 0
                        || entry.attribute("maintenance_efficiency") > 0;
            }

            @Override
            int score(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("food_efficiency") * 3
                        + entry.attribute("maintenance_efficiency") * 2
                        + entry.attribute("stability");
            }

            @Override
            Component metrics(MaterialCatalogPayload.Entry entry) {
                return metricComponent(entry, "food_efficiency", "maintenance_efficiency");
            }
        },
        CROSS_DIMENSION("book.deadrecall.nexus_specialist.metrics.cross_dimension") {
            @Override
            boolean eligible(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("cross_dimension_catalyst_units") > 0 || maxAffinity(entry) > 0;
            }

            @Override
            int score(MaterialCatalogPayload.Entry entry) {
                return entry.attribute("cross_dimension_catalyst_units") * 6
                        + maxAffinity(entry) * 2
                        + entry.attribute("stability")
                        + entry.attribute("arrival_safety");
            }

            @Override
            Component metrics(MaterialCatalogPayload.Entry entry) {
                return Component.translatable(metricKey,
                        signed(entry.attribute("cross_dimension_catalyst_units")),
                        signed(maxAffinity(entry)));
            }
        };

        private final String metricKey;

        Specialist(String metricKey) {
            this.metricKey = metricKey;
        }

        abstract boolean eligible(MaterialCatalogPayload.Entry entry);

        abstract int score(MaterialCatalogPayload.Entry entry);

        abstract Component metrics(MaterialCatalogPayload.Entry entry);

        Component metricComponent(MaterialCatalogPayload.Entry entry, String... attributes) {
            Object[] values = new Object[attributes.length];
            for (int index = 0; index < attributes.length; index++) {
                values[index] = signed(entry.attribute(attributes[index]));
            }
            return Component.translatable(metricKey, values);
        }
    }
}
