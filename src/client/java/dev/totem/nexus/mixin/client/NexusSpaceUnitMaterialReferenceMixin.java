package dev.totem.nexus.mixin.client;

import dev.totem.nexus.client.NexusMaterialCatalogClientState;
import dev.totem.nexus.client.NexusSpaceUnitMapScreen;
import dev.totem.nexus.network.MaterialCatalogPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/** Adds a server-authoritative per-block comparison table to the existing Nexus Materials view. */
@Mixin(NexusSpaceUnitMapScreen.class)
public abstract class NexusSpaceUnitMaterialReferenceMixin extends Screen {
    @Unique private static final int deadrecall$PADDING = 12;
    @Unique private static final int deadrecall$HEADER_HEIGHT = 34;
    @Unique private static final int deadrecall$FOOTER_HEIGHT = 46;
    @Unique private static final int deadrecall$ROW_HEIGHT = 20;
    @Unique private static final int deadrecall$TABLE_NAME_WIDTH = 142;
    @Unique private static final int deadrecall$TABLE_VALUE_WIDTH = 42;

    @Shadow private boolean showMaterials;

    @Shadow private int panelX() {
        throw new AssertionError();
    }

    @Shadow private int panelY() {
        throw new AssertionError();
    }

    @Shadow private int panelWidth() {
        throw new AssertionError();
    }

    @Shadow private int panelHeight() {
        throw new AssertionError();
    }

    @Shadow private String trimToWidth(String value, int width) {
        throw new AssertionError();
    }

    @Unique private Button deadrecall$referenceButton;
    @Unique private boolean deadrecall$referenceMode;
    @Unique private int deadrecall$referenceScrollIndex;
    @Unique private String deadrecall$selectedBlockId;

    protected NexusSpaceUnitMaterialReferenceMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void deadrecall$createReferenceButton(CallbackInfo ci) {
        this.deadrecall$referenceButton = Button.builder(
                        deadrecall$referenceButtonText(),
                        button -> deadrecall$toggleReferenceMode())
                .bounds(0, 0, 92, 18)
                .build();
        addRenderableWidget(this.deadrecall$referenceButton);
        deadrecall$updateReferenceButton();
    }

    @Inject(method = "updateButtonLayout", at = @At("TAIL"))
    private void deadrecall$layoutReferenceButton(CallbackInfo ci) {
        deadrecall$updateReferenceButton();
    }

    @Inject(method = "drawMaterialPanel", at = @At("HEAD"), cancellable = true)
    private void deadrecall$drawReferenceTable(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (!this.showMaterials || !this.deadrecall$referenceMode) {
            return;
        }
        deadrecall$renderReferenceTable(extractor, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void deadrecall$scrollReferenceTable(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!this.showMaterials || !this.deadrecall$referenceMode) {
            return;
        }
        List<MaterialCatalogPayload.Entry> entries = NexusMaterialCatalogClientState.snapshot().entries();
        int visibleRows = deadrecall$visibleRows();
        int maxStart = Math.max(0, entries.size() - visibleRows);
        int tableX = deadrecall$tableX();
        int tableY = deadrecall$tableY();
        int tableWidth = deadrecall$tableWidth();
        int tableHeight = visibleRows * deadrecall$ROW_HEIGHT;
        if (!deadrecall$isInside(mouseX, mouseY, tableX, tableY, tableWidth, tableHeight)) {
            return;
        }
        if (verticalAmount < 0) {
            this.deadrecall$referenceScrollIndex = Math.min(maxStart, this.deadrecall$referenceScrollIndex + 1);
        } else if (verticalAmount > 0) {
            this.deadrecall$referenceScrollIndex = Math.max(0, this.deadrecall$referenceScrollIndex - 1);
        }
        cir.setReturnValue(true);
    }

    @Unique
    private void deadrecall$toggleReferenceMode() {
        this.deadrecall$referenceMode = !this.deadrecall$referenceMode;
        if (this.deadrecall$referenceMode) {
            this.deadrecall$referenceScrollIndex = 0;
            NexusMaterialCatalogClientState.requestRefresh();
        }
        deadrecall$updateReferenceButton();
    }

    @Unique
    private void deadrecall$updateReferenceButton() {
        if (this.deadrecall$referenceButton == null) {
            return;
        }
        this.deadrecall$referenceButton.visible = this.showMaterials;
        this.deadrecall$referenceButton.setX(panelX() + panelWidth() - deadrecall$PADDING - 94);
        this.deadrecall$referenceButton.setY(panelY() + deadrecall$HEADER_HEIGHT + 10);
        this.deadrecall$referenceButton.setMessage(deadrecall$referenceButtonText());
    }

    @Unique
    private Component deadrecall$referenceButtonText() {
        return Component.translatable(this.deadrecall$referenceMode
                ? "message.deadrecall.space_unit.material_array_summary"
                : "message.deadrecall.space_unit.material_reference");
    }

    @Unique
    private void deadrecall$renderReferenceTable(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        MaterialCatalogPayload catalog = NexusMaterialCatalogClientState.snapshot();
        List<MaterialCatalogPayload.Entry> entries = catalog.entries();
        int x = panelX() + deadrecall$PADDING;
        int y = panelY() + deadrecall$HEADER_HEIGHT + 6;
        int width = panelWidth() - deadrecall$PADDING * 2;
        int height = panelHeight() - deadrecall$HEADER_HEIGHT - deadrecall$FOOTER_HEIGHT + 2;

        extractor.fill(x, y, x + width, y + height, 0xC0101419);
        extractor.outline(x, y, width, height, 0xFF3F4A56);
        extractor.text(this.font,
                Component.translatable("message.deadrecall.space_unit.material_reference_title"),
                x + 10, y + 9, 0xFFFFFFFF);
        extractor.text(this.font,
                Component.translatable("message.deadrecall.space_unit.material_reference_subtitle", catalog.revision()),
                x + 10, y + 22, 0xFF9EAFBE);

        if (entries.isEmpty()) {
            extractor.text(this.font,
                    Component.translatable("message.deadrecall.space_unit.material_reference_loading"),
                    x + 10, y + 52, 0xFFFFD166);
            return;
        }

        int tableX = deadrecall$tableX();
        int tableY = deadrecall$tableY();
        int tableWidth = deadrecall$tableWidth();
        int visibleRows = deadrecall$visibleRows();
        int maxStart = Math.max(0, entries.size() - visibleRows);
        this.deadrecall$referenceScrollIndex = Math.min(this.deadrecall$referenceScrollIndex, maxStart);

        deadrecall$drawTableHeader(extractor, tableX, tableY - 14);

        MaterialCatalogPayload.Entry hovered = null;
        for (int row = 0; row < visibleRows; row++) {
            int index = this.deadrecall$referenceScrollIndex + row;
            if (index >= entries.size()) {
                break;
            }
            MaterialCatalogPayload.Entry entry = entries.get(index);
            int rowY = tableY + row * deadrecall$ROW_HEIGHT;
            boolean isHovered = deadrecall$isInside(mouseX, mouseY, tableX, rowY, tableWidth, deadrecall$ROW_HEIGHT - 1);
            boolean isSelected = entry.blockId().equals(this.deadrecall$selectedBlockId);
            if (isHovered) {
                hovered = entry;
                this.deadrecall$selectedBlockId = entry.blockId();
            }
            if (isHovered || isSelected) {
                extractor.fill(tableX, rowY, tableX + tableWidth, rowY + deadrecall$ROW_HEIGHT - 1,
                        isHovered ? 0xFF263646 : 0xFF1C2833);
            } else if ((row & 1) == 1) {
                extractor.fill(tableX, rowY, tableX + tableWidth, rowY + deadrecall$ROW_HEIGHT - 1, 0x401F2831);
            }
            deadrecall$drawReferenceRow(extractor, entry, tableX, rowY);
        }

        MaterialCatalogPayload.Entry selected = hovered != null ? hovered : deadrecall$selectedEntry(entries);
        if (selected == null) {
            selected = entries.getFirst();
            this.deadrecall$selectedBlockId = selected.blockId();
        }
        deadrecall$drawReferenceDetails(extractor, selected, tableX + tableWidth + 12, tableY - 14,
                x + width - (tableX + tableWidth + 12), height - 48);

        if (entries.size() > visibleRows) {
            extractor.text(this.font,
                    Component.literal((this.deadrecall$referenceScrollIndex + 1) + "-"
                            + Math.min(entries.size(), this.deadrecall$referenceScrollIndex + visibleRows)
                            + " / " + entries.size()),
                    tableX, y + height - 13, 0xFF8394A5);
        }
    }

    @Unique
    private void deadrecall$drawTableHeader(GuiGraphicsExtractor extractor, int x, int y) {
        extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.material_reference.block"),
                x + 2, y, 0xFF9EAFBE);
        int metricX = x + deadrecall$TABLE_NAME_WIDTH;
        String[] keys = {
                "message.deadrecall.space_unit.material_reference.expansion_short",
                "message.deadrecall.space_unit.material_reference.accuracy_short",
                "message.deadrecall.space_unit.material_reference.food_short",
                "message.deadrecall.space_unit.material_reference.stability_short"
        };
        for (String key : keys) {
            extractor.text(this.font, Component.translatable(key), metricX + 3, y, 0xFF9EAFBE);
            metricX += deadrecall$TABLE_VALUE_WIDTH;
        }
    }

    @Unique
    private void deadrecall$drawReferenceRow(
            GuiGraphicsExtractor extractor,
            MaterialCatalogPayload.Entry entry,
            int x,
            int y
    ) {
        ItemStack stack = deadrecall$stackFor(entry.blockId());
        if (!stack.isEmpty()) {
            extractor.item(stack, x + 2, y + 2);
        }
        String name = stack.isEmpty() ? entry.blockId() : stack.getHoverName().getString();
        extractor.text(this.font, trimToWidth(name, deadrecall$TABLE_NAME_WIDTH - 24), x + 22, y + 6, 0xFFE0E6EC);

        int metricX = x + deadrecall$TABLE_NAME_WIDTH;
        String[] attributes = {"scan_expansion_radius", "arrival_accuracy", "food_efficiency", "stability"};
        for (String attribute : attributes) {
            int value = entry.attribute(attribute);
            extractor.text(this.font, deadrecall$signed(value), metricX + 8, y + 6, deadrecall$valueColor(value));
            metricX += deadrecall$TABLE_VALUE_WIDTH;
        }
    }

    @Unique
    private void deadrecall$drawReferenceDetails(
            GuiGraphicsExtractor extractor,
            MaterialCatalogPayload.Entry entry,
            int x,
            int y,
            int width,
            int height
    ) {
        extractor.fill(x, y, x + width, y + height, 0x701A2027);
        extractor.outline(x, y, width, height, 0xFF34414D);
        ItemStack stack = deadrecall$stackFor(entry.blockId());
        if (!stack.isEmpty()) {
            extractor.item(stack, x + 8, y + 7);
        }
        String name = stack.isEmpty() ? entry.blockId() : stack.getHoverName().getString();
        extractor.text(this.font, trimToWidth(name, width - 38), x + 29, y + 8, 0xFFFFFFFF);
        extractor.text(this.font,
                trimToWidth(Component.translatable("message.deadrecall.space_unit.material_reference.family",
                        entry.family()).getString(), width - 16),
                x + 8, y + 28, 0xFF9EAFBE);
        extractor.text(this.font,
                trimToWidth(Component.translatable("message.deadrecall.space_unit.material_reference.profile",
                        entry.profileId()).getString(), width - 16),
                x + 8, y + 40, entry.validStructureMaterial() ? 0xFF8BD9A0 : 0xFFFFD166);

        String[][] metrics = {
                {"structure_capacity", "message.deadrecall.space_unit.material_reference.capacity"},
                {"scan_expansion_radius", "message.deadrecall.space_unit.material_reference.expansion"},
                {"stability", "message.deadrecall.space_unit.material_reference.stability"},
                {"arrival_accuracy", "message.deadrecall.space_unit.material_reference.accuracy"},
                {"target_lock", "message.deadrecall.space_unit.material_reference.lock"},
                {"arrival_safety", "message.deadrecall.space_unit.material_reference.safety"},
                {"wear_resistance", "message.deadrecall.space_unit.material_reference.wear"},
                {"maintenance_efficiency", "message.deadrecall.space_unit.material_reference.maintenance"},
                {"interference_resistance", "message.deadrecall.space_unit.material_reference.interference"},
                {"food_efficiency", "message.deadrecall.space_unit.material_reference.food"},
                {"phase_speed", "message.deadrecall.space_unit.material_reference.phase"},
                {"cooldown_recovery", "message.deadrecall.space_unit.material_reference.cooldown"},
                {"route_load_capacity", "message.deadrecall.space_unit.material_reference.load"},
                {"cross_dimension_catalyst_units", "message.deadrecall.space_unit.material_reference.catalyst"}
        };
        int columnWidth = Math.max(92, (width - 20) / 2);
        int metricStartY = y + 59;
        for (int index = 0; index < metrics.length; index++) {
            int column = index / 7;
            int row = index % 7;
            int metricX = x + 8 + column * columnWidth;
            int metricY = metricStartY + row * 14;
            int value = entry.attribute(metrics[index][0]);
            String text = Component.translatable(metrics[index][1], deadrecall$signed(value)).getString();
            extractor.text(this.font, trimToWidth(text, columnWidth - 6), metricX, metricY, deadrecall$valueColor(value));
        }

        int affinityY = metricStartY + 7 * 14 + 6;
        extractor.text(this.font,
                trimToWidth(Component.translatable("message.deadrecall.space_unit.material_reference.affinity",
                        deadrecall$affinityText(entry.dimensionAffinity())).getString(), width - 16),
                x + 8, affinityY, 0xFFD9C394);
    }

    @Unique
    private MaterialCatalogPayload.Entry deadrecall$selectedEntry(List<MaterialCatalogPayload.Entry> entries) {
        if (this.deadrecall$selectedBlockId == null) {
            return null;
        }
        for (MaterialCatalogPayload.Entry entry : entries) {
            if (entry.blockId().equals(this.deadrecall$selectedBlockId)) {
                return entry;
            }
        }
        return null;
    }

    @Unique
    private ItemStack deadrecall$stackFor(String blockId) {
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

    @Unique
    private String deadrecall$affinityText(Map<String, Integer> affinities) {
        if (affinities.isEmpty()) {
            return "—";
        }
        return affinities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " " + deadrecall$signed(entry.getValue()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("—");
    }

    @Unique
    private int deadrecall$tableX() {
        return panelX() + deadrecall$PADDING + 10;
    }

    @Unique
    private int deadrecall$tableY() {
        return panelY() + deadrecall$HEADER_HEIGHT + 64;
    }

    @Unique
    private int deadrecall$tableWidth() {
        return deadrecall$TABLE_NAME_WIDTH + deadrecall$TABLE_VALUE_WIDTH * 4;
    }

    @Unique
    private int deadrecall$visibleRows() {
        int innerHeight = panelHeight() - deadrecall$HEADER_HEIGHT - deadrecall$FOOTER_HEIGHT + 2;
        return Math.max(1, (innerHeight - 76) / deadrecall$ROW_HEIGHT);
    }

    @Unique
    private static boolean deadrecall$isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Unique
    private static String deadrecall$signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    @Unique
    private static int deadrecall$valueColor(int value) {
        if (value > 0) {
            return 0xFF8BD9A0;
        }
        if (value < 0) {
            return 0xFFF08080;
        }
        return 0xFF8794A1;
    }
}
