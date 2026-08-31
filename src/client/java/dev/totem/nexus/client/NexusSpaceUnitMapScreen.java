package dev.totem.nexus.client;

import dev.totem.nexus.network.CalibrateSpaceUnitPayload;
import dev.totem.nexus.network.RenameSpaceUnitPayload;
import dev.totem.nexus.network.RequestSpaceUnitMapPayload;
import dev.totem.nexus.network.RequestTeleportArrayVisualizationPayload;
import dev.totem.nexus.network.RepairSpaceUnitPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.StartSpaceUnitTeleportPayload;
import dev.totem.nexus.network.ToggleSpaceUnitFavoritePayload;
import dev.totem.nexus.network.UpdateSpaceUnitAccessPayload;
import dev.totem.nexus.network.UpdateSpaceUnitVisibilityPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class NexusSpaceUnitMapScreen extends NexusOwnedScreen {
    public static NexusSpaceUnitMapScreen CURRENT = null;

    private static final int PANEL_WIDTH = 640;
    private static final int PANEL_HEIGHT = 360;
    private static final int PANEL_PADDING = 12;
    private static final int HEADER_HEIGHT = 34;
    private static final int CONTROL_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 46;
    private static final int GAP = 10;
    private static final int LIST_WIDTH = 196;
    private static final int LIST_HEADER_HEIGHT = 24;
    private static final int LIST_ROW_BOTTOM_GAP = 4;
    private static final int ROW_HEIGHT = 30;
    private static final int FOOTER_BUTTON_WIDTH = 62;
    private static final int CALIBRATION_RADIUS_BLOCKS = 8;
    private static final int MAX_RENAME_LENGTH = 48;
    private static final int MAX_ACCESS_PLAYER_NAME_LENGTH = 64;
    private static final int VISIBILITY_BUTTON_WIDTH = 56;
    private static final String ACCESS_ROLE_ADMINISTRATOR = "administrator";
    private static final String ACCESS_ROLE_ALLOWED = "allowed";
    private static final int MIN_MAP_SIZE = 32;
    private static final int VANILLA_MAP_SIZE = 128;

    private SpaceUnitMapPayload payload;
    private List<String> dimensions;
    private String activeDimension;
    private UUID selectedUnitId;
    private int listScrollIndex = 0;
    private final MapRenderState mapRenderState = new MapRenderState();
    private List<MapLabelLayout> renderedMapLabels = List.of();
    private String searchQuery = "";
    private TypeFilter typeFilter = TypeFilter.ALL;
    private FriendFilter friendFilter = FriendFilter.ALL;
    private SortMode sortMode = SortMode.NAME;
    private EditBox searchField;
    private Button typeFilterButton;
    private Button friendFilterButton;
    private Button sortButton;
    private Button favoriteButton;
    private Button visibilityButton;
    private Button adminButton;
    private Button accessButton;
    private Button renameButton;
    private Button calibrateButton;
    private Button teleportButton;
    private Button friendsButton;
    private Button materialButton;
    private Button arrayPreviewButton;
    private Button repairButton;
    private Button refreshButton;
    private Button doneButton;
    private boolean showMaterials;
    private int selectedMaintenanceIndex;
    private int maintenanceScrollIndex;
    private String expandedMaterialFamily;

    public NexusSpaceUnitMapScreen(SpaceUnitMapPayload payload) {
        this(payload, false, () -> { });
    }

    NexusSpaceUnitMapScreen(SpaceUnitMapPayload payload, boolean observer, Runnable stop) {
        super(screenTitle(payload), observer, stop);
        this.payload = payload;
        this.dimensions = collectDimensions(payload);
        this.activeDimension = dimensions.contains(payload.sourceDimension())
                ? payload.sourceDimension()
                : dimensions.isEmpty() ? payload.sourceDimension() : dimensions.get(0);
        this.selectedUnitId = payload.sourceUnitId();
        if (observer) {
            NexusArrayVisualizationClient.clear();
        }
        CURRENT = this;
    }

    @Override
    public void removed() {
        super.removed();
        if (CURRENT == this) {
            CURRENT = null;
        }
    }

    public boolean isFor(String sourceType, UUID sourceUnitId) {
        return this.payload.sourceType().equals(sourceType) && this.payload.sourceUnitId().equals(sourceUnitId);
    }

    public void applyPayload(SpaceUnitMapPayload payload) {
        UUID previousSelection = this.selectedUnitId;
        String previousDimension = this.activeDimension;
        this.payload = payload;
        this.dimensions = collectDimensions(payload);
        this.activeDimension = this.dimensions.contains(previousDimension)
                ? previousDimension
                : this.dimensions.contains(payload.sourceDimension())
                ? payload.sourceDimension()
                : this.dimensions.isEmpty() ? payload.sourceDimension() : this.dimensions.get(0);
        this.selectedUnitId = containsEntry(previousSelection) || payload.sourceUnitId().equals(previousSelection)
                ? previousSelection
                : payload.sourceUnitId();
        if (!hasMapVisualization()) {
            this.activeDimension = payload.sourceDimension();
            this.selectedUnitId = payload.sourceUnitId();
        }
        this.listScrollIndex = Math.min(this.listScrollIndex, getMaxListScrollIndex());
        syncSelectionWithFilters();
    }

    SpaceUnitMapPayload observerPayload() { return payload; }

    private static Component screenTitle(SpaceUnitMapPayload payload) {
        return Component.translatable(payload.interfaceType().hasMapVisualization()
                ? "container.deadrecall.space_unit.map"
                : "container.deadrecall.space_unit.management");
    }

    private boolean hasMapVisualization() {
        return this.payload.interfaceType().hasMapVisualization();
    }

    @Override
    protected void init() {
        CURRENT = this;
        this.searchField = new EditBox(this.font, searchX(), controlsY(), searchWidth(), 18,
                Component.translatable("message.deadrecall.space_unit.map_search"));
        this.searchField.setMaxLength(64);
        this.searchField.setValue(this.searchQuery);
        this.searchField.setHint(Component.translatable("message.deadrecall.space_unit.map_search"));
        this.searchField.setResponder(value -> {
            this.searchQuery = value == null ? "" : value;
            this.listScrollIndex = 0;
            syncSelectionWithFilters();
        });
        this.addRenderableWidget(this.searchField);

        this.typeFilterButton = Button.builder(typeFilterText(), button -> cycleTypeFilter())
                .bounds(typeFilterX(), controlsY(), typeFilterWidth(), 18)
                .build();
        this.addRenderableWidget(this.typeFilterButton);

        this.friendFilterButton = Button.builder(friendFilterText(), button -> cycleFriendFilter())
                .bounds(friendFilterX(), controlsY(), friendFilterWidth(), 18)
                .build();
        this.addRenderableWidget(this.friendFilterButton);

        this.sortButton = Button.builder(sortModeText(), button -> cycleSortMode())
                .bounds(sortX(), controlsY(), sortWidth(), 18)
                .build();
        this.addRenderableWidget(this.sortButton);

        this.friendsButton = Button.builder(
                        Component.translatable("message.deadrecall.space_unit.map_friends"),
                        button -> openFriendManagement())
                .bounds(friendsButtonX(), friendsButtonY(), friendsButtonWidth(), 18)
                .build();
        this.addRenderableWidget(this.friendsButton);

        this.materialButton = Button.builder(materialButtonText(), button -> toggleMaterialView())
                .bounds(materialButtonX(), friendsButtonY(), materialButtonWidth(), 18)
                .build();
        this.addRenderableWidget(this.materialButton);

        this.repairButton = Button.builder(Component.translatable("message.deadrecall.space_unit.maintenance_repair"),
                        button -> requestSelectedMaintenance())
                .bounds(maintenanceButtonX(), maintenanceButtonY(), 78, 18)
                .build();
        this.addRenderableWidget(this.repairButton);

        this.arrayPreviewButton = Button.builder(arrayPreviewButtonText(), button -> toggleArrayPreview())
                .bounds(arrayPreviewButtonX(), maintenanceButtonY(), arrayPreviewButtonWidth(), 18)
                .tooltip(Tooltip.create(arrayPreviewTooltip()))
                .build();
        this.addRenderableWidget(this.arrayPreviewButton);

        this.favoriteButton = Button.builder(favoriteButtonText(), button -> toggleSelectedFavorite())
                .bounds(favoriteButtonX(), favoriteButtonY(), favoriteButtonWidth(), 18)
                .build();
        this.addRenderableWidget(this.favoriteButton);

        this.visibilityButton = Button.builder(visibilityButtonText(), button -> toggleSelectedVisibility())
                .bounds(visibilityButtonX(), visibilityButtonY(), visibilityButtonWidth(), 18)
                .build();
        this.addRenderableWidget(this.visibilityButton);

        this.adminButton = Button.builder(
                        Component.translatable("message.deadrecall.space_unit.map_admins"),
                        button -> requestAccessUpdate(ACCESS_ROLE_ADMINISTRATOR))
                .bounds(adminButtonX(), footerButtonY(), FOOTER_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.adminButton);

        this.accessButton = Button.builder(
                        Component.translatable("message.deadrecall.space_unit.map_allowed"),
                        button -> requestAccessUpdate(ACCESS_ROLE_ALLOWED))
                .bounds(accessButtonX(), footerButtonY(), FOOTER_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.accessButton);

        this.renameButton = Button.builder(Component.translatable("message.deadrecall.space_unit.map_rename"), button -> requestRename())
                .bounds(renameButtonX(), footerButtonY(), FOOTER_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.renameButton);

        this.calibrateButton = Button.builder(Component.translatable("message.deadrecall.space_unit.map_calibrate"), button -> requestCalibration())
                .bounds(calibrateButtonX(), footerButtonY(), FOOTER_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.calibrateButton);

        this.teleportButton = Button.builder(Component.translatable("message.deadrecall.space_unit.teleport_start"), button -> requestTeleport())
                .bounds(teleportButtonX(), footerButtonY(), FOOTER_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.teleportButton);

        this.refreshButton = Button.builder(Component.translatable("message.deadrecall.space_unit.map_refresh"), button -> requestRefresh())
                .bounds(refreshButtonX(), footerButtonY(), FOOTER_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.refreshButton);

        this.doneButton = Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(doneButtonX(), footerButtonY(), FOOTER_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.doneButton);
        updateButtonLayout();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        updateButtonLayout();
        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();

        extractor.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xEA16191D);
        extractor.outline(panelX, panelY, panelWidth, panelHeight, 0xFF657383);
        extractor.text(this.font, this.title, panelX + PANEL_PADDING, panelY + 9, 0xFFFFFFFF);
        int summaryX = panelX + PANEL_PADDING + 150;
        int summaryWidth = Math.max(0, materialButtonX() - summaryX - 8);
        if (summaryWidth > 26) {
            extractor.item(interfaceIcon(), summaryX, panelY + 1);
            if (isInside(mouseX, mouseY, summaryX, panelY + 1, 16, 16)) {
                extractor.setTooltipForNextFrame(interfaceTooltip(), mouseX, mouseY);
            }
            extractor.text(
                    this.font,
                    trimToWidth(sourceSummary(), summaryWidth - 18),
                    summaryX + 18,
                    panelY + 9,
                    0xFFB8C0C8
            );
        }

        if (this.showMaterials) {
            this.renderedMapLabels = List.of();
            drawMaterialPanel(extractor, mouseX, mouseY);
        } else if (!hasMapVisualization()) {
            this.renderedMapLabels = List.of();
            drawManagementOnly(extractor, mouseX, mouseY);
        } else {
            drawMap(extractor, mouseX, mouseY);
            drawNodeList(extractor, mouseX, mouseY);
            drawFooter(extractor, mouseX, mouseY);
        }

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (observerReadOnly()) return true;
        if (this.showMaterials) {
            if (selectMaterialFamilyAt(event.x(), event.y())) {
                return true;
            }
            if (selectMaintenanceTargetAt(event.x(), event.y())) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (!hasMapVisualization()) {
            return super.mouseClicked(event, doubleClick);
        }

        UUID mapHit = mapEntryAt(event.x(), event.y());
        if (mapHit != null) {
            this.selectedUnitId = mapHit;
            ensureSelectedVisible();
            if (event.button() == 1) {
                toggleFavorite(mapHit);
            }
            return true;
        }

        UUID rowHit = listEntryAt(event.x(), event.y());
        if (rowHit != null) {
            this.selectedUnitId = rowHit;
            if (event.button() == 1) {
                toggleFavorite(rowHit);
            }
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (observerReadOnly()) return true;
        if (this.showMaterials) {
            List<SpaceUnitMapPayload.MaintenanceTarget> targets = selectedMaterial().maintenanceTargets();
            if (isInside(mouseX, mouseY, panelX() + PANEL_PADDING + 8, maintenanceListY(),
                    panelWidth() - PANEL_PADDING * 2 - 100, 80) && targets.size() > 5) {
                int maxStart = targets.size() - 5;
                this.maintenanceScrollIndex = verticalAmount < 0
                        ? Math.min(maxStart, this.maintenanceScrollIndex + 1)
                        : Math.max(0, this.maintenanceScrollIndex - 1);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (!hasMapVisualization()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (isInside(mouseX, mouseY, listX(), listY(), listWidth(), listHeight())) {
            if (verticalAmount < 0) {
                this.listScrollIndex = Math.min(getMaxListScrollIndex(), this.listScrollIndex + 1);
                return true;
            }
            if (verticalAmount > 0) {
                this.listScrollIndex = Math.max(0, this.listScrollIndex - 1);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void requestRefresh() {
        if (ClientPlayNetworking.canSend(RequestSpaceUnitMapPayload.TYPE)) {
            ClientPlayNetworking.send(new RequestSpaceUnitMapPayload(this.payload.sourceType(), this.payload.sourceUnitId()));
        }
    }

    private void requestTeleport() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || !selected.canTeleport()) {
            return;
        }

        if (ClientPlayNetworking.canSend(StartSpaceUnitTeleportPayload.TYPE)) {
            ClientPlayNetworking.send(new StartSpaceUnitTeleportPayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    selected.id()
            ));
            this.onClose();
        }
    }

    private void requestCalibration() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || !canCalibrate(selected)) {
            return;
        }

        if (ClientPlayNetworking.canSend(CalibrateSpaceUnitPayload.TYPE)) {
            ClientPlayNetworking.send(new CalibrateSpaceUnitPayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    selected.id()
            ));
        }
    }

    private void requestRename() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || !canRename(selected) || this.minecraft == null) {
            return;
        }

        this.minecraft.setScreenAndShow(new RenameSpaceUnitScreen(selected));
    }

    private void sendRename(UUID targetUnitId, String name) {
        if (ClientPlayNetworking.canSend(RenameSpaceUnitPayload.TYPE)) {
            ClientPlayNetworking.send(new RenameSpaceUnitPayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    targetUnitId,
                    name
            ));
        }
    }

    private void requestAccessUpdate(String role) {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || !canManageAccess(selected, role) || this.minecraft == null) {
            return;
        }

        this.minecraft.setScreenAndShow(new AccessSpaceUnitScreen(selected, role));
    }

    private void openFriendManagement() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(new NexusSpaceUnitFriendsScreen(this, null));
        }
    }

    private void sendAccessUpdate(UUID targetUnitId, String role, String playerName, boolean enabled) {
        if (ClientPlayNetworking.canSend(UpdateSpaceUnitAccessPayload.TYPE)) {
            ClientPlayNetworking.send(new UpdateSpaceUnitAccessPayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    targetUnitId,
                    role,
                    playerName,
                    enabled
            ));
        }
    }

    private void toggleSelectedFavorite() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected != null && canFavorite(selected)) {
            requestFavorite(selected, !selected.favorite());
        }
    }

    private void toggleFavorite(UUID unitId) {
        SpaceUnitMapPayload.Entry entry = entryById(unitId);
        if (entry != null && canFavorite(entry)) {
            requestFavorite(entry, !entry.favorite());
        }
    }

    private void requestFavorite(SpaceUnitMapPayload.Entry entry, boolean favorite) {
        if (ClientPlayNetworking.canSend(ToggleSpaceUnitFavoritePayload.TYPE)) {
            ClientPlayNetworking.send(new ToggleSpaceUnitFavoritePayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    entry.id(),
                    favorite
            ));
        }
    }

    private void toggleSelectedVisibility() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || !canChangeVisibility(selected)) {
            return;
        }

        String nextVisibility = switch (selected.visibility()) {
            case "private" -> "friends";
            case "friends" -> "public";
            default -> "private";
        };
        if (ClientPlayNetworking.canSend(UpdateSpaceUnitVisibilityPayload.TYPE)) {
            ClientPlayNetworking.send(new UpdateSpaceUnitVisibilityPayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    selected.id(),
                    nextVisibility
            ));
        }
    }

    private void cycleTypeFilter() {
        this.typeFilter = this.typeFilter.next();
        this.listScrollIndex = 0;
        syncSelectionWithFilters();
        updateControlMessages();
    }

    private void cycleFriendFilter() {
        this.friendFilter = this.friendFilter.next();
        this.listScrollIndex = 0;
        syncSelectionWithFilters();
        updateControlMessages();
    }

    private void cycleSortMode() {
        this.sortMode = this.sortMode.next();
        this.listScrollIndex = 0;
        ensureSelectedVisible();
        updateControlMessages();
    }

    private void toggleMaterialView() {
        this.showMaterials = !this.showMaterials;
        updateButtonLayout();
    }

    private void toggleArrayPreview() {
        if (observerReadOnly() || !"lodestone".equals(this.payload.sourceType())) {
            return;
        }
        boolean enabled = NexusArrayVisualizationClient.isActiveFor(this.payload.sourceUnitId());
        if (enabled) {
            NexusArrayVisualizationClient.clear();
        } else if (!this.payload.sourceUnitId().equals(this.selectedUnitId)) {
            return;
        }
        if (ClientPlayNetworking.canSend(RequestTeleportArrayVisualizationPayload.TYPE)) {
            ClientPlayNetworking.send(new RequestTeleportArrayVisualizationPayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    !enabled
            ));
        }
    }

    private Component arrayPreviewButtonText() {
        return Component.translatable(NexusArrayVisualizationClient.isActiveFor(this.payload.sourceUnitId())
                ? "message.deadrecall.space_unit.array_preview_hide"
                : "message.deadrecall.space_unit.array_preview_show");
    }

    private Component arrayPreviewTooltip() {
        if (observerReadOnly()) {
            return Component.translatable("message.deadrecall.space_unit.array_preview_observer");
        }
        if (!"lodestone".equals(this.payload.sourceType())) {
            return Component.translatable("message.deadrecall.space_unit.array_preview_lodestone_only");
        }
        if (!this.payload.sourceUnitId().equals(this.selectedUnitId)
                && !NexusArrayVisualizationClient.isActiveFor(this.payload.sourceUnitId())) {
            return Component.translatable("message.deadrecall.space_unit.array_preview_source_only");
        }
        if (!ClientPlayNetworking.canSend(RequestTeleportArrayVisualizationPayload.TYPE)) {
            return Component.translatable("message.deadrecall.space_unit.array_preview_unavailable");
        }
        return Component.translatable("message.deadrecall.space_unit.array_preview_hint");
    }

    /** Package-visible visual-test hook; production input still uses the Material button. */
    void showMaterialDiagnosticsForVisualTest() {
        this.showMaterials = true;
    }

    /** Package-visible proof that Observer relays cannot activate the visualization request. */
    boolean arrayPreviewButtonDisabledForVisualTest() {
        return this.arrayPreviewButton != null
                && this.arrayPreviewButton.visible
                && !this.arrayPreviewButton.active;
    }

    /** Package-visible semantic proof that a non-map interface exposes only its bound source. */
    boolean managementOnlyPresentationForVisualTest() {
        return !hasMapVisualization()
                && this.selectedUnitId.equals(this.payload.sourceUnitId())
                && entriesForActiveDimension().size() <= 1
                && this.searchField != null && !this.searchField.visible
                && this.typeFilterButton != null && !this.typeFilterButton.visible
                && this.teleportButton != null && !this.teleportButton.visible;
    }

    /** Package-visible semantic proof for the explicit Observer/client cache-miss presentation. */
    boolean mapDataUnavailableForVisualTest() {
        return hasMapVisualization() && cachedMapData() == null;
    }

    /** Package-visible proof that the production draw path emitted visible, non-overlapping map labels. */
    boolean renderedMapLabelsForVisualTest(List<String> expectedNames) {
        if (!this.renderedMapLabels.stream().map(MapLabelLayout::text).toList().equals(expectedNames)) {
            return false;
        }
        for (int index = 0; index < this.renderedMapLabels.size(); index++) {
            MapLabelLayout label = this.renderedMapLabels.get(index);
            if (!label.inside(mapLabelBounds())) {
                return false;
            }
            for (int other = index + 1; other < this.renderedMapLabels.size(); other++) {
                if (label.overlaps(this.renderedMapLabels.get(other))) {
                    return false;
                }
            }
        }
        return !this.renderedMapLabels.isEmpty();
    }

    private Component materialButtonText() {
        return Component.translatable(this.showMaterials
                ? "message.deadrecall.space_unit.map_view"
                : "message.deadrecall.space_unit.map_materials");
    }

    private void drawMaterialPanel(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        SpaceUnitMapPayload.MaterialSummary material = selectedMaterial();
        int x = panelX() + PANEL_PADDING;
        int y = panelY() + HEADER_HEIGHT + 6;
        int width = panelWidth() - PANEL_PADDING * 2;
        int height = panelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT + 2;
        extractor.fill(x, y, x + width, y + height, 0xC0101419);
        extractor.outline(x, y, width, height, 0xFF3F4A56);

        String selectedName = selectedEntry() == null ? this.payload.sourceName() : selectedEntry().name();
        extractor.text(this.font, Component.translatable(
                "message.deadrecall.space_unit.material_title", selectedName), x + 10, y + 9, 0xFFFFFFFF);
        extractor.text(this.font, Component.translatable(
                "message.deadrecall.space_unit.material_subtitle", material.profileRevision()),
                x + 10, y + 22, 0xFF9EAFBE);

        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected != null) {
            extractor.text(this.font, trimToWidth(Component.translatable(
                    "message.deadrecall.space_unit.material_route_summary",
                    selected.finalFoodCost(), selected.amethystCost(), seconds(selected.prepareTicks()),
                    selected.maxHorizontalDeviation(), selected.structureWearChancePercent()).getString(), width - 20),
                    x + 10, y + 35, 0xFFB8D9F3);
        }
        List<MaterialMetric> metrics = List.of(
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_capacity",
                        material.effectiveCapacity(), material.rawStructuralBlocks()), 0),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_reach", material.maximumReachedDistance()), 0),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_stability", signed(material.stability())), material.stability()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_accuracy", signed(material.arrivalAccuracy())), material.arrivalAccuracy()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_lock", signed(material.targetLock())), material.targetLock()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_safety", signed(material.arrivalSafety())), material.arrivalSafety()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_wear", signed(material.wearResistance())), material.wearResistance()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_maintenance", signed(material.maintenanceEfficiency())), material.maintenanceEfficiency()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_interference", signed(material.interferenceResistance())), material.interferenceResistance()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_food", signed(material.foodEfficiency())), material.foodEfficiency()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_phase", signed(material.phaseSpeed())), material.phaseSpeed()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_cooldown", signed(material.cooldownRecovery())), material.cooldownRecovery()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_load", signed(material.routeLoadCapacity())), material.routeLoadCapacity()),
                new MaterialMetric(Component.translatable("message.deadrecall.space_unit.material_catalyst", signed(material.crossDimensionCatalystUnits())), material.crossDimensionCatalystUnits())
        );
        int columnWidth = Math.max(100, (width - 24) / 2);
        for (int index = 0; index < metrics.size(); index++) {
            int column = index % 2;
            int row = index / 2;
            int metricX = x + 10 + column * columnWidth;
            int metricY = y + 52 + row * 15;
            MaterialMetric metric = metrics.get(index);
            extractor.text(this.font, trimToWidth(metric.label().getString(), columnWidth - 8), metricX, metricY,
                    material.rawStructuralBlocks() == 0 ? 0xFF8D98A4 : signedColor(metric.value()));
        }

        int mapsY = y + 166;
        SpaceUnitMapPayload.FamilyContribution expandedFamily = expandedMaterialFamily(material);
        String familyLabel = expandedFamily == null
                ? materialMapText(material.familyCounts(), false)
                : expandedFamily.family() + " ×" + expandedFamily.blockCount();
        extractor.text(this.font, trimToWidth(Component.translatable(
                "message.deadrecall.space_unit.material_families", familyLabel).getString(), width - 20),
                x + 10, mapsY, expandedFamily == null ? 0xFFB8D9F3 : 0xFFFFD166);
        if (!material.familyContributions().isEmpty()
                && isInside(mouseX, mouseY, x + 8, mapsY - 2, width - 16, 14)) {
            extractor.setTooltipForNextFrame(
                    Component.translatable("message.deadrecall.space_unit.material_families_hint"), mouseX, mouseY);
        }
        int detailOffset = expandedFamily == null ? 0 : 16;
        if (expandedFamily != null) {
            extractor.text(this.font, trimToWidth(materialContributionText(expandedFamily), width - 20),
                    x + 10, mapsY + 16, contributionColor(expandedFamily));
        }
        String affinityText = selected == null
                ? Component.translatable("message.deadrecall.space_unit.material_affinity",
                materialMapText(material.dimensionAffinity(), true)).getString()
                : Component.translatable("message.deadrecall.space_unit.material_route_affinity",
                materialMapText(this.payload.sourceMaterial().dimensionAffinity(), true),
                materialMapText(material.dimensionAffinity(), true)).getString();
        extractor.text(this.font, trimToWidth(affinityText, width - 20),
                x + 10, mapsY + 16 + detailOffset, 0xFFD9C394);
        if (material.rawStructuralBlocks() == 0) {
            extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.material_empty"),
                    x + 10, mapsY + 42 + detailOffset, 0xFFFFD166);
        }
        drawMaintenanceTargets(extractor, material, x, mapsY + 42 + detailOffset, width, mouseX, mouseY);
    }

    private void drawMaintenanceTargets(
            GuiGraphicsExtractor extractor,
            SpaceUnitMapPayload.MaterialSummary material,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY) {
        List<SpaceUnitMapPayload.MaintenanceTarget> targets = material.maintenanceTargets();
        if (targets.isEmpty()) {
            return;
        }
        extractor.text(this.font, Component.translatable(
                "message.deadrecall.space_unit.maintenance_targets", material.maintenanceItemCost()),
                x + 10, y, 0xFFFFD166);
        int rowY = y + 14;
        int visible = Math.min(5, targets.size());
        int start = Math.min(this.maintenanceScrollIndex, Math.max(0, targets.size() - visible));
        for (int row = 0; row < visible; row++) {
            int index = start + row;
            SpaceUnitMapPayload.MaintenanceTarget target = targets.get(index);
            boolean selected = index == selectedMaintenanceIndex();
            boolean hovered = isInside(mouseX, mouseY, x + 8, rowY, width - 100, 14);
            extractor.fill(x + 8, rowY, x + width - 92, rowY + 14,
                    selected ? 0xFF4B3D24 : hovered ? 0xFF343029 : 0x8020252B);
            extractor.outline(x + 8, rowY, width - 100, 14, selected ? 0xFFFFD166 : 0xFF4B5663);
            String line = Component.translatable("message.deadrecall.space_unit.maintenance_target_row",
                    target.x(), target.y(), target.z(), target.family()).getString();
            extractor.text(this.font, trimToWidth(line, width - 112), x + 12, rowY + 3, 0xFFE8EDF2);
            rowY += 16;
        }
    }

    private SpaceUnitMapPayload.MaterialSummary selectedMaterial() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        return selected == null ? this.payload.sourceMaterial() : selected.material();
    }

    private SpaceUnitMapPayload.FamilyContribution expandedMaterialFamily(SpaceUnitMapPayload.MaterialSummary material) {
        if (this.expandedMaterialFamily == null) {
            return null;
        }
        return material.familyContributions().stream()
                .filter(contribution -> this.expandedMaterialFamily.equals(contribution.family()))
                .findFirst()
                .orElse(null);
    }

    private boolean selectMaterialFamilyAt(double mouseX, double mouseY) {
        SpaceUnitMapPayload.MaterialSummary material = selectedMaterial();
        if (material.familyContributions().isEmpty()) {
            return false;
        }
        int x = panelX() + PANEL_PADDING;
        int y = panelY() + HEADER_HEIGHT + 6 + 166;
        int width = panelWidth() - PANEL_PADDING * 2;
        if (!isInside(mouseX, mouseY, x + 8, y - 2, width - 16, 14)) {
            return false;
        }
        List<SpaceUnitMapPayload.FamilyContribution> families = material.familyContributions();
        int current = -1;
        for (int index = 0; index < families.size(); index++) {
            if (families.get(index).family().equals(this.expandedMaterialFamily)) {
                current = index;
                break;
            }
        }
        this.expandedMaterialFamily = families.get((current + 1) % families.size()).family();
        return true;
    }

    private String materialContributionText(SpaceUnitMapPayload.FamilyContribution contribution) {
        if (contribution.attributes().isEmpty()) {
            return Component.translatable("message.deadrecall.space_unit.material_empty").getString();
        }
        return contribution.attributes().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> shortMaterialAttribute(entry.getKey()) + " " + signed(entry.getValue()))
                .reduce((left, right) -> left + " · " + right)
                .orElse("--");
    }

    private static String shortMaterialAttribute(String attribute) {
        return switch (attribute) {
            case "structure_capacity" -> "capacity";
            case "scan_expansion_radius" -> "scan";
            case "arrival_accuracy" -> "accuracy";
            case "arrival_safety" -> "safety";
            case "wear_resistance" -> "wear";
            case "maintenance_efficiency" -> "maintenance";
            case "interference_resistance" -> "interference";
            case "food_efficiency" -> "food";
            case "phase_speed" -> "phase";
            case "cooldown_recovery" -> "recovery";
            case "route_load_capacity" -> "load";
            case "cross_dimension_catalyst_units" -> "catalyst";
            default -> attribute;
        };
    }

    private static int contributionColor(SpaceUnitMapPayload.FamilyContribution contribution) {
        int total = contribution.attributes().values().stream().mapToInt(Integer::intValue).sum();
        return signedColor(total);
    }

    private int selectedMaintenanceIndex() {
        int size = selectedMaterial().maintenanceTargets().size();
        if (size == 0) {
            return -1;
        }
        this.selectedMaintenanceIndex = Math.max(0, Math.min(this.selectedMaintenanceIndex, size - 1));
        return this.selectedMaintenanceIndex;
    }

    private boolean selectMaintenanceTargetAt(double mouseX, double mouseY) {
        SpaceUnitMapPayload.MaterialSummary material = selectedMaterial();
        if (material.maintenanceTargets().isEmpty()) {
            return false;
        }
        int x = panelX() + PANEL_PADDING;
        int y = maintenanceListY();
        int width = panelWidth() - PANEL_PADDING * 2;
        int visible = Math.min(5, material.maintenanceTargets().size());
        int start = Math.min(this.maintenanceScrollIndex, Math.max(0, material.maintenanceTargets().size() - visible));
        for (int row = 0; row < visible; row++) {
            if (isInside(mouseX, mouseY, x + 8, y + row * 16, width - 100, 14)) {
                this.selectedMaintenanceIndex = start + row;
                return true;
            }
        }
        return false;
    }

    private void requestSelectedMaintenance() {
        SpaceUnitMapPayload.MaintenanceTarget target = selectedMaintenanceTarget();
        if (target == null || !ClientPlayNetworking.canSend(RepairSpaceUnitPayload.TYPE)) {
            return;
        }
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        UUID targetUnitId = selected == null ? this.payload.sourceUnitId() : selected.id();
        ClientPlayNetworking.send(new RepairSpaceUnitPayload(
                this.payload.sourceType(), this.payload.sourceUnitId(), targetUnitId,
                target.x(), target.y(), target.z()
        ));
    }

    private SpaceUnitMapPayload.MaintenanceTarget selectedMaintenanceTarget() {
        List<SpaceUnitMapPayload.MaintenanceTarget> targets = selectedMaterial().maintenanceTargets();
        int index = selectedMaintenanceIndex();
        return index < 0 || index >= targets.size() ? null : targets.get(index);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static int signedColor(int value) {
        return value > 0 ? 0xFF8BD9A0 : value < 0 ? 0xFFE36A6A : 0xFFE0E6EC;
    }

    private record MaterialMetric(Component label, int value) {
    }

    private String materialMapText(java.util.Map<String, Integer> values, boolean showSign) {
        if (values.isEmpty()) {
            return "--";
        }
        return values.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> shortDimension(entry.getKey()) + " ×"
                        + (showSign ? signed(entry.getValue()) : entry.getValue()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("--");
    }

    private void drawDimensionTabs(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int x = panelX() + PANEL_PADDING;
        int y = panelY() + HEADER_HEIGHT;
        int maxRight = panelX() + panelWidth() - PANEL_PADDING;

        for (String dimension : this.dimensions) {
            int tabWidth = Math.min(132, Math.max(64, this.font.width(shortDimension(dimension)) + 18));
            if (x + tabWidth > maxRight) {
                extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.map_more_dimensions"), x + 4, y + 6, 0xFF9AA3AD);
                return;
            }

            boolean active = dimension.equals(this.activeDimension);
            boolean hovered = isInside(mouseX, mouseY, x, y, tabWidth, 18);
            extractor.fill(x, y, x + tabWidth, y + 18, active ? 0xFF304154 : hovered ? 0xFF27313D : 0xFF20262E);
            extractor.outline(x, y, tabWidth, 18, active ? 0xFF78A6D6 : 0xFF4B5663);
            extractor.text(this.font, trimToWidth(shortDimension(dimension), tabWidth - 10), x + 5, y + 5, active ? 0xFFFFFFFF : 0xFFC8D0D8);
            x += tabWidth + 4;
        }
    }

    private void drawMap(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int x = mapX();
        int y = mapY();
        int width = mapWidth();
        int height = mapHeight();
        extractor.fill(x, y, x + width, y + height, 0xFF0E1115);
        extractor.outline(x, y, width, height, 0xFF3F4A56);
        MapItemSavedData cached = cachedMapData();
        if (cached == null) {
            this.renderedMapLabels = List.of();
            Component unavailable = Component.translatable("message.deadrecall.space_unit.map_data_unavailable");
            Component detail = Component.translatable("message.deadrecall.space_unit.map_data_unavailable_detail");
            extractor.centeredText(this.font, unavailable, x + width / 2, y + height / 2 - 10, 0xFFFFD166);
            extractor.centeredText(this.font, trimToWidth(detail.getString(), Math.max(1, width - 16)),
                    x + width / 2, y + height / 2 + 6, 0xFFB8C0C8);
            return;
        }

        MapId mapId = new MapId(this.payload.mapId());
        MapItemSavedData transientData = transientMapData(cached);
        MapRenderArea area = mapRenderArea();
        extractor.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        extractor.pose().pushMatrix();
        extractor.pose().translate(area.x(), area.y());
        extractor.pose().scale(area.scale(), area.scale());
        this.minecraft.getMapRenderer().extractRenderState(mapId, transientData, this.mapRenderState);
        extractor.map(this.mapRenderState);
        extractor.pose().popMatrix();
        extractor.disableScissor();
        drawTransientMapLabels(extractor, cached, area);
    }

    private void drawManagementOnly(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int x = panelX() + PANEL_PADDING;
        int y = panelY() + HEADER_HEIGHT + 6;
        int width = panelWidth() - PANEL_PADDING * 2;
        int height = panelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;
        extractor.fill(x, y, x + width, y + height, 0xC0101419);
        extractor.outline(x, y, width, height, 0xFF3F4A56);
        extractor.item(interfaceIcon(), x + 12, y + 12);
        if (isInside(mouseX, mouseY, x + 12, y + 12, 16, 16)) {
            extractor.setTooltipForNextFrame(interfaceTooltip(), mouseX, mouseY);
        }
        extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.management_only"),
                x + 36, y + 10, 0xFFFFFFFF);
        extractor.text(this.font,
                trimToWidth(Component.translatable("message.deadrecall.space_unit.management_only_detail").getString(),
                        width - 48),
                x + 36, y + 23, 0xFFB8C0C8);

        int contentBottom = y + height - 8;
        int hintY = contentBottom - this.font.lineHeight;
        int bonusY = hintY - this.font.lineHeight * 2;
        int summaryY = bonusY - this.font.lineHeight * 2;
        int interfaceY = summaryY - this.font.lineHeight * 2;
        int sourceY = interfaceY - this.font.lineHeight * 2;
        SpaceUnitMapPayload.Entry source = sourceEntry();
        String sourceName = source == null ? displayNexusName(this.payload.sourceName()) : displayNexusName(source.name());
        extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.management_bound_source", sourceName),
                x + 14, sourceY, 0xFFFFD166);
        extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.management_interface",
                        Component.translatable(interfaceNameKey())),
                x + 14, interfaceY, 0xFFE0E6EC);
        if (source != null) {
            extractor.text(this.font, trimToWidth(managementSummary(source), width - 28),
                    x + 14, summaryY, 0xFFB8D9F3);
            extractor.text(this.font, Component.translatable(source.interfaceBonusMessageKey()),
                    x + 14, bonusY, source.interfaceBonusActive() ? 0xFF8BD9A0 : 0xFF93A4B5);
        } else {
            extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.management_source_unavailable"),
                    x + 14, summaryY, 0xFFFFD166);
        }
        String controlsHint = Component.translatable("message.deadrecall.space_unit.management_controls_hint").getString();
        extractor.text(this.font, trimToWidth(controlsHint, width - 28),
                x + 14, hintY, 0xFFB8C0C8);
    }

    private void drawTransientMapLabels(
            GuiGraphicsExtractor extractor, MapItemSavedData mapData, MapRenderArea area) {
        List<MapLabelLayout> labels = layoutTransientMapLabels(mapData, area);
        this.renderedMapLabels = List.copyOf(labels);
        if (labels.isEmpty()) {
            return;
        }

        MapLabelBounds bounds = mapLabelBounds();
        extractor.nextStratum();
        extractor.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        for (MapLabelLayout label : labels) {
            extractor.fill(label.x() - 1, label.y() - 1,
                    label.x() + label.width() + 1, label.y() + this.font.lineHeight,
                    0xB0000000);
            extractor.text(this.font, label.text(), label.x(), label.y(), 0xFFFFFFFF, true);
        }
        extractor.disableScissor();
    }

    private List<MapLabelLayout> layoutTransientMapLabels(MapItemSavedData mapData, MapRenderArea area) {
        MapLabelBounds bounds = mapLabelBounds();
        int availableWidth = bounds.right() - bounds.left();
        int availableHeight = bounds.bottom() - bounds.top();
        if (availableWidth < 12 || availableHeight < this.font.lineHeight) {
            return List.of();
        }

        int maxLabelWidth = Math.max(8, Math.min(88, availableWidth - 4));
        List<MapLabelLayout> result = new ArrayList<>();
        for (SpaceUnitMapPayload.Entry entry : mapPresentationEntries()) {
            MapDecorationPosition position = mapDecorationPosition(entry, mapData);
            if (position == null) {
                continue;
            }
            String text = trimToWidth(displayNexusName(entry.name()), maxLabelWidth);
            int textWidth = this.font.width(text);
            if (text.isEmpty() || textWidth <= 0) {
                continue;
            }

            int markerX = area.x() + (int) Math.round((position.x() / 2.0D + 64.0D) * area.scale());
            int markerY = area.y() + (int) Math.round((position.y() / 2.0D + 64.0D) * area.scale());
            MapLabelLayout label = chooseMapLabelLayout(text, textWidth, markerX, markerY, bounds, result);
            if (label != null) {
                result.add(label);
            }
        }
        return result;
    }

    private MapLabelLayout chooseMapLabelLayout(
            String text,
            int textWidth,
            int markerX,
            int markerY,
            MapLabelBounds bounds,
            List<MapLabelLayout> occupied) {
        int centeredY = markerY - this.font.lineHeight / 2;
        int centeredX = markerX - textWidth / 2;
        int[][] candidates = {
                {markerX + 5, centeredY},
                {markerX - textWidth - 5, centeredY},
                {centeredX, markerY + 5},
                {centeredX, markerY - this.font.lineHeight - 5}
        };
        for (int[] candidate : candidates) {
            MapLabelLayout layout = constrainedMapLabel(text, textWidth, candidate[0], candidate[1], bounds);
            if (!layout.contains(markerX, markerY) && occupied.stream().noneMatch(layout::overlaps)) {
                return layout;
            }
        }

        int step = this.font.lineHeight + 2;
        for (int distance = step; distance < bounds.bottom() - bounds.top(); distance += step) {
            for (int direction : new int[]{1, -1}) {
                MapLabelLayout layout = constrainedMapLabel(
                        text, textWidth, markerX + 5, centeredY + distance * direction, bounds);
                if (!layout.contains(markerX, markerY) && occupied.stream().noneMatch(layout::overlaps)) {
                    return layout;
                }
            }
        }
        return null;
    }

    private MapLabelLayout constrainedMapLabel(
            String text, int textWidth, int x, int y, MapLabelBounds bounds) {
        int constrainedX = Math.max(bounds.left(), Math.min(bounds.right() - textWidth, x));
        int constrainedY = Math.max(bounds.top(), Math.min(bounds.bottom() - this.font.lineHeight, y));
        return new MapLabelLayout(text, constrainedX, constrainedY, textWidth, this.font.lineHeight);
    }

    private MapLabelBounds mapLabelBounds() {
        MapRenderArea area = mapRenderArea();
        int renderedSize = VANILLA_MAP_SIZE * area.scale();
        return new MapLabelBounds(
                Math.max(mapX() + 2, area.x() + 2),
                Math.max(mapY() + 2, area.y() + 2),
                Math.min(mapX() + mapWidth() - 2, area.x() + renderedSize - 2),
                Math.min(mapY() + mapHeight() - 2, area.y() + renderedSize - 2));
    }

    private MapItemSavedData cachedMapData() {
        if (!hasMapVisualization() || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        return this.minecraft.level.getMapData(new MapId(this.payload.mapId()));
    }

    private MapItemSavedData transientMapData(MapItemSavedData cached) {
        MapItemSavedData result = MapItemSavedData.createForClient(cached.scale, cached.locked, cached.dimension);
        System.arraycopy(cached.colors, 0, result.colors, 0, Math.min(cached.colors.length, result.colors.length));
        List<MapDecoration> decorations = new ArrayList<>();
        for (MapDecoration decoration : cached.getDecorations()) {
            decorations.add(decoration);
        }
        for (SpaceUnitMapPayload.Entry entry : mapPresentationEntries()) {
            MapDecorationPosition position = mapDecorationPosition(entry, cached);
            if (position == null) {
                continue;
            }
            decorations.add(new MapDecoration(
                    entry.id().equals(this.payload.sourceUnitId())
                            ? MapDecorationTypes.BLUE_MARKER
                            : MapDecorationTypes.RED_MARKER,
                    position.x(), position.y(), (byte) 0,
                    Optional.of(nexusName(entry.name()))));
        }
        result.addClientSideDecorations(List.copyOf(decorations));
        return result;
    }

    private List<SpaceUnitMapPayload.Entry> mapPresentationEntries() {
        return this.payload.entries().stream()
                .filter(entry -> entry.dimension().equals(this.payload.sourceDimension()))
                .toList();
    }

    private MapDecorationPosition mapDecorationPosition(
            SpaceUnitMapPayload.Entry entry, MapItemSavedData mapData) {
        double blocksPerPixel = 1 << mapData.scale;
        double mapX = (entry.x() - mapData.centerX) / blocksPerPixel;
        double mapY = (entry.z() - mapData.centerZ) / blocksPerPixel;
        if (mapX < -64.0D || mapX >= 64.0D || mapY < -64.0D || mapY >= 64.0D) {
            return null;
        }
        return new MapDecorationPosition(
                (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, Math.round(mapX * 2.0D))),
                (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, Math.round(mapY * 2.0D))));
    }

    private MapRenderArea mapRenderArea() {
        int scale = Math.max(1, Math.min(mapWidth() - 2, mapHeight() - 2) / VANILLA_MAP_SIZE);
        int renderedSize = VANILLA_MAP_SIZE * scale;
        return new MapRenderArea(
                mapX() + (mapWidth() - renderedSize) / 2,
                mapY() + (mapHeight() - renderedSize) / 2,
                scale);
    }

    private Component nexusName(String name) {
        return name == null || name.isBlank()
                ? Component.translatable("message.deadrecall.space_unit.map_unnamed_nexus")
                : Component.literal(name.strip());
    }

    private String displayNexusName(String name) {
        return nexusName(name).getString();
    }

    private record MapDecorationPosition(byte x, byte y) { }
    private record MapRenderArea(int x, int y, int scale) { }
    private record MapLabelBounds(int left, int top, int right, int bottom) { }
    private record MapLabelLayout(String text, int x, int y, int width, int height) {
        private boolean contains(int pointX, int pointY) {
            return pointX >= this.x && pointX < this.x + this.width
                    && pointY >= this.y && pointY < this.y + this.height;
        }

        private boolean overlaps(MapLabelLayout other) {
            return this.x - 2 < other.x + other.width
                    && this.x + this.width + 2 > other.x
                    && this.y - 2 < other.y + other.height
                    && this.y + this.height + 2 > other.y;
        }

        private boolean inside(MapLabelBounds bounds) {
            return this.x >= bounds.left && this.y >= bounds.top
                    && this.x + this.width <= bounds.right
                    && this.y + this.height <= bounds.bottom;
        }
    }

    private void drawNodeList(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        int width = listWidth();
        int height = listHeight();
        extractor.fill(x, y, x + width, y + height, 0x80101010);
        extractor.outline(x, y, width, height, 0xFF3F4A56);
        List<SpaceUnitMapPayload.Entry> entries = entriesForActiveDimension();
        String nodeTitle = Component.translatable("message.deadrecall.space_unit.map_nodes", entries.size()).getString();
        int titleWidth = Math.max(0, visibilityButtonX() - x - 12);
        if (titleWidth > 8) {
            extractor.text(this.font, trimToWidth(nodeTitle, titleWidth), x + 8, y + 7, 0xFFFFFFFF);
        }

        int rowsVisible = visibleListRows();
        int start = Math.min(this.listScrollIndex, Math.max(0, entries.size() - rowsVisible));
        int rowY = y + LIST_HEADER_HEIGHT;
        for (int i = start; i < entries.size() && i < start + rowsVisible; i++) {
            SpaceUnitMapPayload.Entry entry = entries.get(i);
            boolean selected = entry.id().equals(this.selectedUnitId);
            boolean hovered = isInside(mouseX, mouseY, x + 4, rowY, width - 12, ROW_HEIGHT - 4);
            extractor.fill(x + 4, rowY, x + width - 8, rowY + ROW_HEIGHT - 4,
                    selected ? 0xFF2D3F54 : hovered ? 0xC02A2F36 : 0x9020252B);
            extractor.outline(x + 4, rowY, width - 12, ROW_HEIGHT - 4, selected ? 0xFF78A6D6 : 0xFF343D47);
            extractor.fill(x + 10, rowY + 8, x + 18, rowY + 16, colorForType(entry.type()));
            extractor.text(this.font, trimToWidth(favoritePrefix(entry) + entry.name(), width - 40), x + 24, rowY + 5, 0xFFFFFFFF);
            extractor.text(this.font, entrySummary(entry), x + 24, rowY + 17, 0xFFB8C0C8);
            rowY += ROW_HEIGHT;
        }

        if (entries.isEmpty()) {
            extractor.text(this.font,
                    trimToWidth(Component.translatable("message.deadrecall.space_unit.map_dimension_empty").getString(),
                            Math.max(1, width - 16)),
                    x + 8, y + 28, 0xFFFFC857);
        } else if (entries.size() > rowsVisible) {
            drawScrollBar(extractor, x + width - 5, y + LIST_HEADER_HEIGHT,
                    Math.max(1, height - LIST_HEADER_HEIGHT - LIST_ROW_BOTTOM_GAP),
                    entries.size(), rowsVisible, start);
        }
    }

    private void drawFooter(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        int x = panelX() + PANEL_PADDING;
        int y = panelY() + panelHeight() - FOOTER_HEIGHT + 6;
        int firstButtonX = firstFooterButtonX();
        int width = Math.max(36, firstButtonX - x - 8);
        String title = selected == null
                ? Component.translatable("message.deadrecall.space_unit.map_source_footer", this.payload.sourceName()).getString()
                : Component.translatable(
                        "message.deadrecall.space_unit.map_selected_footer",
                        selected.name(),
                        localizedType(selected.type()),
                        selected.dimension(),
                        selected.x(),
                        selected.y(),
                        selected.z()).getString();
        extractor.text(this.font, trimToWidth(title, width), x, y, 0xFFE0E6EC);

        if (selected == null) {
            return;
        }

        drawQuoteIcons(extractor, selected, x, y + 11, width, mouseX, mouseY);
        if (!selected.canTeleport() && selected.blockedReason() != null && !selected.blockedReason().isBlank()) {
            extractor.text(this.font, trimToWidth(Component.translatable(selected.blockedReason()).getString(), width), x, y + 30, 0xFFFFD166);
        } else {
            extractor.text(this.font, trimToWidth(interfaceFooterSummary(selected), width), x, y + 30,
                    selected.interfaceBonusActive() ? 0xFF8BD9A0 : 0xFF93A4B5);
        }
    }

    private void drawQuoteIcons(
            GuiGraphicsExtractor extractor,
            SpaceUnitMapPayload.Entry entry,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY) {
        List<QuoteMetric> metrics = List.of(
                new QuoteMetric(
                        new ItemStack(Items.SPYGLASS),
                        distanceMetricValue(entry),
                        Component.translatable("message.deadrecall.space_unit.metric.distance", distanceText(entry))),
                new QuoteMetric(
                        new ItemStack(Items.GOLDEN_CARROT),
                        comparisonValue(entry.baseFoodCost(), entry.finalFoodCost()),
                        Component.translatable(
                                "message.deadrecall.space_unit.metric.food_quote",
                                entry.baseFoodCost(),
                                entry.finalFoodCost(),
                                entry.saturationCost(),
                                entry.hungerCost(),
                                entry.foodPointsNeeded(),
                                entry.safeFoodPointsAvailable())),
                new QuoteMetric(
                        new ItemStack(Items.COOKED_BEEF),
                        Integer.toString(entry.hungerCost()),
                        Component.translatable("message.deadrecall.space_unit.metric.hunger", entry.hungerCost())),
                new QuoteMetric(
                        new ItemStack(Items.BREAD),
                        entry.foodPointsNeeded() + "/" + entry.safeFoodPointsAvailable(),
                        Component.translatable("message.deadrecall.space_unit.metric.food",
                                entry.foodPointsNeeded(), entry.safeFoodPointsAvailable())),
                new QuoteMetric(
                        new ItemStack(Items.AMETHYST_SHARD),
                        entry.amethystCost() + "/" + entry.amethystAvailable(),
                        Component.translatable("message.deadrecall.space_unit.metric.amethyst_breakdown",
                                entry.baseAmethystCost(), signed(entry.sourceCatalysts()),
                                signed(entry.targetCatalysts()), signed(entry.catalystDiscount()),
                                entry.amethystCost() + "/" + entry.amethystAvailable())),
                new QuoteMetric(
                        new ItemStack(Items.CLOCK),
                        comparisonValue(seconds(entry.basePrepareTicks()), seconds(entry.prepareTicks())),
                        Component.translatable(
                                "message.deadrecall.space_unit.metric.time_quote",
                                seconds(entry.basePrepareTicks()),
                                seconds(entry.prepareTicks()))),
                new QuoteMetric(
                        new ItemStack(Items.COMPASS),
                        Long.toString(Math.round(entry.resonance() * 100.0D)),
                        Component.translatable("message.deadrecall.space_unit.metric.stability",
                                Math.round(entry.resonance() * 100.0D))),
                new QuoteMetric(
                        new ItemStack(Items.ENDER_PEARL),
                        comparisonValue(
                                entry.baseMaxHorizontalDeviation(),
                                entry.maxHorizontalDeviation()),
                        Component.translatable(
                                "message.deadrecall.space_unit.metric.drift_quote",
                                entry.baseMaxHorizontalDeviation(),
                                entry.maxHorizontalDeviation())),
                new QuoteMetric(
                        new ItemStack(Items.CRACKED_STONE_BRICKS),
                        comparisonPercentValue(
                                entry.baseStructureWearChancePercent(),
                                entry.structureWearChancePercent()),
                        Component.translatable(
                                "message.deadrecall.space_unit.metric.wear_quote",
                                entry.baseStructureWearChancePercent(),
                                entry.structureWearChancePercent()))
        );

        int cursorX = x;
        int maxX = x + width;
        boolean compact = quoteMetricsWidth(metrics, false) > width;
        for (QuoteMetric metric : metrics) {
            cursorX = drawQuoteMetric(extractor, cursorX, y, maxX, mouseX, mouseY, metric, compact);
        }
    }

    private int quoteMetricsWidth(List<QuoteMetric> metrics, boolean compact) {
        int width = 0;
        for (int i = 0; i < metrics.size(); i++) {
            if (i > 0) {
                width += 4;
            }
            width += quoteMetricWidth(metrics.get(i), compact);
        }
        return width;
    }

    private int quoteMetricWidth(QuoteMetric metric, boolean compact) {
        if (compact) {
            return 18;
        }
        return Math.max(27, 19 + this.font.width(metric.value()));
    }

    private int drawQuoteMetric(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            int maxX,
            int mouseX,
            int mouseY,
            QuoteMetric metric,
            boolean compact) {
        int metricWidth = quoteMetricWidth(metric, compact);
        if (x + metricWidth > maxX) {
            return x;
        }

        extractor.fill(x, y, x + metricWidth, y + 18, 0x6020252B);
        extractor.outline(x, y, metricWidth, 18, 0xFF3F4A56);
        extractor.item(metric.icon(), x + 1, y + 1);
        if (!compact) {
            extractor.text(this.font, metric.value(), x + 17, y + 6, 0xFFE8EDF2);
        }
        if (isInside(mouseX, mouseY, x, y, metricWidth, 18)) {
            extractor.setTooltipForNextFrame(metric.tooltip(), mouseX, mouseY);
        }
        return x + metricWidth + 4;
    }

    private record QuoteMetric(ItemStack icon, String value, Component tooltip) {
    }

    private void drawScrollBar(GuiGraphicsExtractor extractor, int x, int y, int height, int totalRows, int visibleRows, int start) {
        int thumbHeight = Math.max(16, height * visibleRows / Math.max(visibleRows, totalRows));
        int thumbTravel = Math.max(1, height - thumbHeight);
        int maxStart = Math.max(1, totalRows - visibleRows);
        int thumbY = y + thumbTravel * start / maxStart;
        extractor.fill(x, y, x + 3, y + height, 0x80333333);
        extractor.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF9A9A9A);
    }

    private boolean selectDimensionAt(double mouseX, double mouseY) {
        int x = panelX() + PANEL_PADDING;
        int y = panelY() + HEADER_HEIGHT;
        int maxRight = panelX() + panelWidth() - PANEL_PADDING;

        for (String dimension : this.dimensions) {
            int tabWidth = Math.min(132, Math.max(64, this.font.width(shortDimension(dimension)) + 18));
            if (x + tabWidth > maxRight) {
                return false;
            }
            if (isInside(mouseX, mouseY, x, y, tabWidth, 18)) {
                this.activeDimension = dimension;
                this.listScrollIndex = 0;
                if (entriesForActiveDimension().stream().noneMatch(entry -> entry.id().equals(this.selectedUnitId))) {
                    this.selectedUnitId = dimension.equals(this.payload.sourceDimension())
                            ? this.payload.sourceUnitId()
                            : entriesForActiveDimension().stream().findFirst().map(SpaceUnitMapPayload.Entry::id).orElse(this.payload.sourceUnitId());
                }
                return true;
            }
            x += tabWidth + 4;
        }
        return false;
    }

    private UUID mapEntryAt(double mouseX, double mouseY) {
        if (!hasMapVisualization()
                || !isInside(mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight())) {
            return null;
        }
        MapItemSavedData mapData = cachedMapData();
        if (mapData == null) {
            return null;
        }
        MapRenderArea area = mapRenderArea();
        SpaceUnitMapPayload.Entry best = null;
        double hitRadius = Math.max(5.0D, 5.0D * area.scale());
        double bestDistance = hitRadius * hitRadius;
        for (SpaceUnitMapPayload.Entry entry : mapPresentationEntries()) {
            MapDecorationPosition position = mapDecorationPosition(entry, mapData);
            if (position == null) {
                continue;
            }
            double pointX = area.x() + (position.x() / 2.0D + 64.0D) * area.scale();
            double pointY = area.y() + (position.y() / 2.0D + 64.0D) * area.scale();
            double dx = mouseX - pointX;
            double dy = mouseY - pointY;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = entry;
            }
        }
        return best == null ? null : best.id();
    }

    private UUID listEntryAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, listX(), listY(), listWidth(), listHeight())) {
            return null;
        }

        List<SpaceUnitMapPayload.Entry> entries = entriesForActiveDimension();
        int rowsVisible = visibleListRows();
        int start = Math.min(this.listScrollIndex, Math.max(0, entries.size() - rowsVisible));
        int relativeY = (int) mouseY - (listY() + LIST_HEADER_HEIGHT);
        if (relativeY < 0) {
            return null;
        }
        int row = relativeY / ROW_HEIGHT;
        int index = start + row;
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index).id();
    }

    private void ensureSelectedVisible() {
        List<SpaceUnitMapPayload.Entry> entries = entriesForActiveDimension();
        int selectedIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(this.selectedUnitId)) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0) {
            return;
        }

        int rowsVisible = visibleListRows();
        if (selectedIndex < this.listScrollIndex) {
            this.listScrollIndex = selectedIndex;
        } else if (selectedIndex >= this.listScrollIndex + rowsVisible) {
            this.listScrollIndex = selectedIndex - rowsVisible + 1;
        }
    }

    private List<SpaceUnitMapPayload.Entry> entriesForActiveDimension() {
        if (!hasMapVisualization()) {
            SpaceUnitMapPayload.Entry source = sourceEntry();
            return source == null ? List.of() : List.of(source);
        }
        List<SpaceUnitMapPayload.Entry> entries = new ArrayList<>();
        for (SpaceUnitMapPayload.Entry entry : this.payload.entries()) {
            if (entry.dimension().equals(this.activeDimension) && matchesFilters(entry)) {
                entries.add(entry);
            }
        }
        entries.sort(entryComparator());
        return entries;
    }

    private boolean matchesFilters(SpaceUnitMapPayload.Entry entry) {
        if (!this.typeFilter.matches(entry)) {
            return false;
        }
        if (!this.friendFilter.matches(entry)) {
            return false;
        }

        String query = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }

        return entry.name().toLowerCase(Locale.ROOT).contains(query)
                || entry.type().toLowerCase(Locale.ROOT).contains(query)
                || entry.visibility().toLowerCase(Locale.ROOT).contains(query)
                || shortDimension(entry.dimension()).toLowerCase(Locale.ROOT).contains(query)
                || entry.dimension().toLowerCase(Locale.ROOT).contains(query)
                || Integer.toString(entry.x()).contains(query)
                || Integer.toString(entry.y()).contains(query)
                || Integer.toString(entry.z()).contains(query);
    }

    private Comparator<SpaceUnitMapPayload.Entry> entryComparator() {
        Comparator<SpaceUnitMapPayload.Entry> byName =
                Comparator.comparing(SpaceUnitMapPayload.Entry::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<SpaceUnitMapPayload.Entry> modeComparator = switch (this.sortMode) {
            case NAME -> byName;
            case DISTANCE -> Comparator
                    .comparingInt(this::sortDistance)
                    .thenComparing(byName);
            case STABILITY -> Comparator
                    .comparingDouble(SpaceUnitMapPayload.Entry::resonance)
                    .reversed()
                    .thenComparing(byName);
            case COST -> Comparator
                    .comparingInt(NexusSpaceUnitMapScreen::totalFoodCost)
                    .thenComparingInt(SpaceUnitMapPayload.Entry::amethystCost)
                    .thenComparing(byName);
            case TIME -> Comparator
                    .comparingInt(SpaceUnitMapPayload.Entry::prepareTicks)
                    .thenComparing(byName);
        };
        return Comparator
                .comparing((SpaceUnitMapPayload.Entry entry) -> !entry.favorite())
                .thenComparing(modeComparator);
    }

    private int sortDistance(SpaceUnitMapPayload.Entry entry) {
        if (entry.distanceBlocks() >= 0) {
            return entry.distanceBlocks();
        }

        long dx = (long) entry.x() - this.payload.sourceX();
        long dz = (long) entry.z() - this.payload.sourceZ();
        return (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(dx * dx + dz * dz)) + 1_000_000L);
    }

    private SpaceUnitMapPayload.Entry selectedEntry() {
        if (!hasMapVisualization()) {
            return sourceEntry();
        }
        return entryById(this.selectedUnitId);
    }

    private SpaceUnitMapPayload.Entry sourceEntry() {
        return entryById(this.payload.sourceUnitId());
    }

    private SpaceUnitMapPayload.Entry entryById(UUID unitId) {
        for (SpaceUnitMapPayload.Entry entry : this.payload.entries()) {
            if (entry.id().equals(unitId)) {
                return entry;
            }
        }
        return null;
    }

    private String sourceSummary() {
        return Component.translatable(
                "message.deadrecall.space_unit.interface_source_summary",
                Component.translatable(interfaceNameKey()),
                this.payload.sourceName(),
                this.payload.entries().size()
        ).getString();
    }

    private ItemStack interfaceIcon() {
        return new ItemStack(switch (this.payload.interfaceType()) {
            case COMPASS -> Items.COMPASS;
            case RECOVERY_COMPASS -> Items.RECOVERY_COMPASS;
            case BOOK -> Items.BOOK;
            case FILLED_MAP -> Items.FILLED_MAP;
        });
    }

    private Component interfaceTooltip() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        return selected == null
                ? Component.translatable(interfaceNameKey())
                : Component.translatable(selected.interfaceBonusMessageKey());
    }

    private String interfaceNameKey() {
        return "message.deadrecall.space_unit.interface_name." + this.payload.interfaceType().id();
    }

    private boolean hasManagementCapabilities() {
        return this.payload.interfaceType().canManage();
    }

    private String interfaceFooterSummary(SpaceUnitMapPayload.Entry entry) {
        String bonus = Component.translatable(entry.interfaceBonusMessageKey()).getString();
        return hasManagementCapabilities() ? bonus + " | " + managementSummary(entry) : bonus;
    }

    private String entrySummary(SpaceUnitMapPayload.Entry entry) {
        if (entry.dimension().equals(this.payload.sourceDimension())) {
            return Component.translatable(
                    "message.deadrecall.space_unit.map_relative_summary",
                    entry.x() - this.payload.sourceX(),
                    entry.z() - this.payload.sourceZ(),
                    totalFoodCost(entry),
                    seconds(entry.prepareTicks()),
                    Math.round(entry.resonance() * 100.0D)).getString();
        }
        return Component.translatable(
                "message.deadrecall.space_unit.map_absolute_summary",
                entry.x(),
                entry.y(),
                entry.z(),
                totalFoodCost(entry),
                seconds(entry.prepareTicks()),
                Math.round(entry.resonance() * 100.0D)).getString();
    }

    private String distanceText(SpaceUnitMapPayload.Entry entry) {
        return entry.distanceBlocks() >= 0
                ? Component.translatable("message.deadrecall.space_unit.map_distance_blocks", entry.distanceBlocks()).getString()
                : Component.translatable("message.deadrecall.space_unit.map_distance_cross_dimension").getString();
    }

    private String distanceMetricValue(SpaceUnitMapPayload.Entry entry) {
        return entry.distanceBlocks() >= 0 ? Integer.toString(entry.distanceBlocks()) : "--";
    }

    private String managementSummary(SpaceUnitMapPayload.Entry entry) {
        return Component.translatable(
                "message.deadrecall.space_unit.map_management_footer",
                visibilitySummary(entry),
                entry.tier(),
                Component.translatable(entry.manageable()
                        ? "message.deadrecall.space_unit.map_manageable"
                        : "message.deadrecall.space_unit.map_readonly").getString(),
                entry.administratorCount(),
                entry.allowedPlayerCount()).getString();
    }

    private String visibilitySummary(SpaceUnitMapPayload.Entry entry) {
        String visibility = Component.translatable("message.deadrecall.space_unit.visibility." + visibilityLabelId(entry.visibility())).getString();
        return entry.friendShared()
                ? Component.translatable("message.deadrecall.space_unit.map_friend_shared", visibility).getString()
                : visibility;
    }

    private static int totalFoodCost(SpaceUnitMapPayload.Entry entry) {
        return entry.finalFoodCost();
    }

    private static String comparisonValue(int baseValue, int finalValue) {
        return baseValue == finalValue
                ? Integer.toString(finalValue)
                : baseValue + "→" + finalValue;
    }

    private static String comparisonPercentValue(int baseValue, int finalValue) {
        return baseValue == finalValue
                ? finalValue + "%"
                : baseValue + "%→" + finalValue + "%";
    }

    private static int seconds(int ticks) {
        return Math.max(0, (int) Math.ceil(ticks / 20.0D));
    }

    private String localizedType(String type) {
        return Component.translatable("message.deadrecall.space_unit.type." + type).getString();
    }

    private String favoritePrefix(SpaceUnitMapPayload.Entry entry) {
        return entry.favorite() ? "* " : "";
    }

    private String shortDimension(String dimension) {
        int index = dimension.indexOf(':');
        return index >= 0 && index + 1 < dimension.length() ? dimension.substring(index + 1) : dimension;
    }

    private String trimToWidth(String value, int width) {
        if (this.font.width(value) <= width) {
            return value;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        String trimmed = value;
        while (!trimmed.isEmpty() && this.font.width(trimmed) + ellipsisWidth > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private boolean containsEntry(UUID unitId) {
        if (unitId == null) {
            return false;
        }
        for (SpaceUnitMapPayload.Entry entry : this.payload.entries()) {
            if (entry.id().equals(unitId)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> collectDimensions(SpaceUnitMapPayload payload) {
        return List.of(payload.sourceDimension());
    }

    private int getMaxListScrollIndex() {
        int rowsVisible = visibleListRows();
        return Math.max(0, entriesForActiveDimension().size() - rowsVisible);
    }

    private int visibleListRows() {
        return Math.max(1, (listHeight() - LIST_HEADER_HEIGHT + LIST_ROW_BOTTOM_GAP) / ROW_HEIGHT);
    }

    private int colorForType(String type) {
        return switch (type) {
            case "death" -> 0xFFE36A6A;
            case "player" -> 0xFF6AD98F;
            case "temporary" -> 0xFFE2C15A;
            case "system" -> 0xFFC084FC;
            default -> 0xFF76B7E8;
        };
    }

    private void updateButtonLayout() {
        int y = panelY() + panelHeight() - 23;
        if (this.searchField != null) {
            this.searchField.setX(searchX());
            this.searchField.setY(controlsY());
            this.searchField.setWidth(searchWidth());
            this.searchField.visible = !this.showMaterials && hasMapVisualization();
        }
        if (this.typeFilterButton != null) {
            this.typeFilterButton.setX(typeFilterX());
            this.typeFilterButton.setY(controlsY());
            this.typeFilterButton.setWidth(typeFilterWidth());
            this.typeFilterButton.visible = !this.showMaterials && hasMapVisualization();
        }
        if (this.friendFilterButton != null) {
            this.friendFilterButton.setX(friendFilterX());
            this.friendFilterButton.setY(controlsY());
            this.friendFilterButton.setWidth(friendFilterWidth());
            this.friendFilterButton.visible = !this.showMaterials && hasMapVisualization();
        }
        if (this.sortButton != null) {
            this.sortButton.setX(sortX());
            this.sortButton.setY(controlsY());
            this.sortButton.setWidth(sortWidth());
            this.sortButton.visible = !this.showMaterials && hasMapVisualization();
        }
        if (this.friendsButton != null) {
            this.friendsButton.setX(friendsButtonX());
            this.friendsButton.setY(friendsButtonY());
            this.friendsButton.setWidth(friendsButtonWidth());
            this.friendsButton.visible = !this.showMaterials && hasManagementCapabilities();
            this.friendsButton.active = this.friendsButton.visible;
        }
        if (this.materialButton != null) {
            this.materialButton.setX(materialButtonX());
            this.materialButton.setY(friendsButtonY());
            this.materialButton.setWidth(materialButtonWidth());
            this.materialButton.visible = true;
            this.materialButton.active = true;
        }
        if (this.repairButton != null) {
            this.repairButton.setX(maintenanceButtonX());
            this.repairButton.setY(maintenanceButtonY());
            this.repairButton.setWidth(78);
            this.repairButton.visible = this.showMaterials && selectedMaintenanceTarget() != null;
            this.repairButton.active = this.repairButton.visible;
        }
        if (this.arrayPreviewButton != null) {
            this.arrayPreviewButton.setX(arrayPreviewButtonX());
            this.arrayPreviewButton.setY(maintenanceButtonY());
            this.arrayPreviewButton.setWidth(arrayPreviewButtonWidth());
            boolean previewActive = NexusArrayVisualizationClient.isActiveFor(this.payload.sourceUnitId());
            boolean sourceSelected = this.payload.sourceUnitId().equals(this.selectedUnitId);
            this.arrayPreviewButton.visible = this.showMaterials;
            this.arrayPreviewButton.active = this.arrayPreviewButton.visible
                    && !observerReadOnly()
                    && "lodestone".equals(this.payload.sourceType())
                    && (previewActive || sourceSelected)
                    && ClientPlayNetworking.canSend(RequestTeleportArrayVisualizationPayload.TYPE);
            this.arrayPreviewButton.setMessage(arrayPreviewButtonText());
            this.arrayPreviewButton.setTooltip(Tooltip.create(arrayPreviewTooltip()));
        }
        if (this.favoriteButton != null) {
            this.favoriteButton.setX(favoriteButtonX());
            this.favoriteButton.setY(favoriteButtonY());
            this.favoriteButton.setWidth(favoriteButtonWidth());
            SpaceUnitMapPayload.Entry selected = selectedEntry();
            this.favoriteButton.visible = !this.showMaterials && hasMapVisualization();
            this.favoriteButton.active = this.favoriteButton.visible && selected != null && canFavorite(selected);
        }
        if (this.visibilityButton != null) {
            this.visibilityButton.setX(visibilityButtonX());
            this.visibilityButton.setY(visibilityButtonY());
            this.visibilityButton.setWidth(visibilityButtonWidth());
            SpaceUnitMapPayload.Entry selected = selectedEntry();
            this.visibilityButton.visible = !this.showMaterials && hasManagementCapabilities();
            this.visibilityButton.active = this.visibilityButton.visible
                    && selected != null
                    && canChangeVisibility(selected);
        }
        updateControlMessages();
        if (this.adminButton != null) {
            this.adminButton.setX(adminButtonX());
            this.adminButton.setY(y);
            this.adminButton.setWidth(FOOTER_BUTTON_WIDTH);
            SpaceUnitMapPayload.Entry selected = selectedEntry();
            this.adminButton.visible = !this.showMaterials && panelWidth() >= 540
                    && selected != null
                    && canManageAccess(selected, ACCESS_ROLE_ADMINISTRATOR);
            this.adminButton.active = this.adminButton.visible;
        }
        if (this.accessButton != null) {
            this.accessButton.setX(accessButtonX());
            this.accessButton.setY(y);
            this.accessButton.setWidth(FOOTER_BUTTON_WIDTH);
            SpaceUnitMapPayload.Entry selected = selectedEntry();
            this.accessButton.visible = !this.showMaterials && panelWidth() >= 500
                    && selected != null
                    && canManageAccess(selected, ACCESS_ROLE_ALLOWED);
            this.accessButton.active = this.accessButton.visible;
        }
        if (this.renameButton != null) {
            this.renameButton.setX(renameButtonX());
            this.renameButton.setY(y);
            this.renameButton.setWidth(FOOTER_BUTTON_WIDTH);
            SpaceUnitMapPayload.Entry selected = selectedEntry();
            this.renameButton.visible = !this.showMaterials && panelWidth() >= 380 && selected != null && canRename(selected);
            this.renameButton.active = this.renameButton.visible;
        }
        if (this.calibrateButton != null) {
            this.calibrateButton.setX(calibrateButtonX());
            this.calibrateButton.setY(y);
            this.calibrateButton.setWidth(FOOTER_BUTTON_WIDTH);
            SpaceUnitMapPayload.Entry selected = selectedEntry();
            this.calibrateButton.visible = !this.showMaterials && hasManagementCapabilities();
            this.calibrateButton.active = this.calibrateButton.visible
                    && selected != null
                    && canCalibrate(selected);
        }
        if (this.teleportButton != null) {
            this.teleportButton.setX(teleportButtonX());
            this.teleportButton.setY(y);
            this.teleportButton.setWidth(FOOTER_BUTTON_WIDTH);
            SpaceUnitMapPayload.Entry selected = selectedEntry();
            this.teleportButton.visible = !this.showMaterials && hasMapVisualization();
            this.teleportButton.active = this.teleportButton.visible && selected != null && selected.canTeleport();
        }
        if (this.refreshButton != null) {
            this.refreshButton.setX(refreshButtonX());
            this.refreshButton.setY(y);
            this.refreshButton.setWidth(FOOTER_BUTTON_WIDTH);
        }
        if (this.doneButton != null) {
            this.doneButton.setX(doneButtonX());
            this.doneButton.setY(y);
            this.doneButton.setWidth(FOOTER_BUTTON_WIDTH);
        }
    }

    private void updateControlMessages() {
        if (this.typeFilterButton != null) {
            this.typeFilterButton.setMessage(typeFilterText());
        }
        if (this.friendFilterButton != null) {
            this.friendFilterButton.setMessage(friendFilterText());
        }
        if (this.sortButton != null) {
            this.sortButton.setMessage(sortModeText());
        }
        if (this.favoriteButton != null) {
            this.favoriteButton.setMessage(favoriteButtonText());
        }
        if (this.visibilityButton != null) {
            this.visibilityButton.setMessage(visibilityButtonText());
        }
        if (this.adminButton != null) {
            this.adminButton.setMessage(Component.translatable("message.deadrecall.space_unit.map_admins"));
        }
        if (this.accessButton != null) {
            this.accessButton.setMessage(Component.translatable("message.deadrecall.space_unit.map_allowed"));
        }
        if (this.renameButton != null) {
            this.renameButton.setMessage(Component.translatable("message.deadrecall.space_unit.map_rename"));
        }
        if (this.calibrateButton != null) {
            this.calibrateButton.setMessage(Component.translatable("message.deadrecall.space_unit.map_calibrate"));
        }
        if (this.friendsButton != null) {
            this.friendsButton.setMessage(Component.translatable("message.deadrecall.space_unit.map_friends"));
        }
        if (this.materialButton != null) {
            this.materialButton.setMessage(materialButtonText());
        }
    }

    private Component typeFilterText() {
        return Component.translatable("message.deadrecall.space_unit.map_filter", typeFilter.label());
    }

    private Component friendFilterText() {
        return Component.translatable("message.deadrecall.space_unit.map_friend_filter", friendFilter.label());
    }

    private Component sortModeText() {
        return Component.translatable("message.deadrecall.space_unit.map_sort", sortMode.label());
    }

    private Component favoriteButtonText() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        return Component.translatable(selected != null && selected.favorite()
                ? "message.deadrecall.space_unit.map_favorite_remove"
                : "message.deadrecall.space_unit.map_favorite_add");
    }

    private Component visibilityButtonText() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return Component.translatable("message.deadrecall.space_unit.visibility.private");
        }
        return Component.translatable("message.deadrecall.space_unit.visibility." + visibilityLabelId(selected.visibility()));
    }

    private void syncSelectionWithFilters() {
        if (this.selectedUnitId != null && entriesForActiveDimension().stream().anyMatch(entry -> entry.id().equals(this.selectedUnitId))) {
            this.listScrollIndex = Math.min(this.listScrollIndex, getMaxListScrollIndex());
            return;
        }

        this.selectedUnitId = entriesForActiveDimension().stream()
                .findFirst()
                .map(SpaceUnitMapPayload.Entry::id)
                .orElse(this.payload.sourceUnitId());
        this.listScrollIndex = Math.min(this.listScrollIndex, getMaxListScrollIndex());
    }

    private int panelWidth() {
        return Math.max(1, Math.min(PANEL_WIDTH, this.width - 12));
    }

    private int panelHeight() {
        return Math.max(1, Math.min(PANEL_HEIGHT, this.height - 12));
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return (this.height - panelHeight()) / 2;
    }

    private int mapX() {
        return panelX() + PANEL_PADDING;
    }

    private int mapY() {
        return panelY() + HEADER_HEIGHT + CONTROL_HEIGHT + 8;
    }

    private int mapWidth() {
        return Math.max(MIN_MAP_SIZE, panelWidth() - PANEL_PADDING * 2 - listWidth() - GAP);
    }

    private int mapHeight() {
        return Math.max(MIN_MAP_SIZE, panelHeight() - HEADER_HEIGHT - CONTROL_HEIGHT - FOOTER_HEIGHT - 18);
    }

    private int listX() {
        return mapX() + mapWidth() + GAP;
    }

    private int listY() {
        return mapY();
    }

    private int listWidth() {
        return Math.min(LIST_WIDTH, Math.max(142, panelWidth() / 3));
    }

    private int listHeight() {
        return mapHeight();
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int controlsY() {
        return panelY() + HEADER_HEIGHT + 4;
    }

    private int searchX() {
        return mapX();
    }

    private int searchWidth() {
        return Math.max(1, (panelWidth() - PANEL_PADDING * 2) / 3);
    }

    private int typeFilterWidth() {
        return Math.max(1, filterControlsWidth() / 3);
    }

    private int friendFilterWidth() {
        return Math.max(1, filterControlsWidth() / 3);
    }

    private int sortWidth() {
        return Math.max(1, filterControlsWidth() - typeFilterWidth() - friendFilterWidth());
    }

    private int typeFilterX() {
        return searchX() + searchWidth() + 6;
    }

    private int friendFilterX() {
        return typeFilterX() + typeFilterWidth() + 6;
    }

    private int sortX() {
        return friendFilterX() + friendFilterWidth() + 6;
    }

    private int filterControlsWidth() {
        int controlsRight = panelX() + panelWidth() - PANEL_PADDING;
        return Math.max(3, controlsRight - typeFilterX() - 12);
    }

    private int favoriteButtonWidth() {
        return 48;
    }

    private int favoriteButtonX() {
        return listX() + listWidth() - favoriteButtonWidth() - 6;
    }

    private int favoriteButtonY() {
        return listY() + 4;
    }

    private int visibilityButtonWidth() {
        return VISIBILITY_BUTTON_WIDTH;
    }

    private int visibilityButtonX() {
        return hasMapVisualization()
                ? favoriteButtonX() - visibilityButtonWidth() - 6
                : teleportButtonX() + (FOOTER_BUTTON_WIDTH - visibilityButtonWidth()) / 2;
    }

    private int visibilityButtonY() {
        return hasMapVisualization() ? favoriteButtonY() : footerButtonY();
    }

    private int friendsButtonWidth() {
        return 62;
    }

    private int friendsButtonX() {
        return panelX() + panelWidth() - PANEL_PADDING - friendsButtonWidth();
    }

    private int materialButtonWidth() {
        return 76;
    }

    private int materialButtonX() {
        return friendsButtonX() - 4 - materialButtonWidth();
    }

    private int maintenanceButtonX() {
        return arrayPreviewButtonX() - 4 - 78;
    }

    private int arrayPreviewButtonWidth() {
        return 88;
    }

    private int arrayPreviewButtonX() {
        // The module's material-reference mixin owns the rightmost 94-pixel slot.
        return panelX() + panelWidth() - PANEL_PADDING - 94 - 4 - arrayPreviewButtonWidth();
    }

    private int maintenanceButtonY() {
        return panelY() + HEADER_HEIGHT + 12;
    }

    private int maintenanceListY() {
        return panelY() + HEADER_HEIGHT + 6 + 166 + 42 + materialFamilyDetailOffset() + 14;
    }

    private int materialFamilyDetailOffset() {
        return expandedMaterialFamily(selectedMaterial()) == null ? 0 : 16;
    }

    private int friendsButtonY() {
        return panelY() + 8;
    }

    private int footerButtonY() {
        return panelY() + panelHeight() - 23;
    }

    private int doneButtonX() {
        return panelX() + panelWidth() - PANEL_PADDING - FOOTER_BUTTON_WIDTH;
    }

    private int refreshButtonX() {
        return doneButtonX() - 6 - FOOTER_BUTTON_WIDTH;
    }

    private int teleportButtonX() {
        return refreshButtonX() - 6 - FOOTER_BUTTON_WIDTH;
    }

    private int calibrateButtonX() {
        return teleportButtonX() - 6 - FOOTER_BUTTON_WIDTH;
    }

    private int renameButtonX() {
        return calibrateButtonX() - 6 - FOOTER_BUTTON_WIDTH;
    }

    private int accessButtonX() {
        return renameButtonX() - 6 - FOOTER_BUTTON_WIDTH;
    }

    private int adminButtonX() {
        return accessButtonX() - 6 - FOOTER_BUTTON_WIDTH;
    }

    private int firstFooterButtonX() {
        if (this.adminButton != null && this.adminButton.visible) {
            return adminButtonX();
        }
        if (this.accessButton != null && this.accessButton.visible) {
            return accessButtonX();
        }
        if (this.renameButton != null && this.renameButton.visible) {
            return renameButtonX();
        }
        if (this.calibrateButton != null && this.calibrateButton.visible) {
            return calibrateButtonX();
        }
        return teleportButtonX();
    }

    private boolean canCalibrate(SpaceUnitMapPayload.Entry entry) {
        return hasManagementCapabilities()
                && entry.manageable()
                && "lodestone".equals(entry.type())
                && entry.dimension().equals(this.payload.sourceDimension())
                && distanceSquaredToSource(entry) <= CALIBRATION_RADIUS_BLOCKS * CALIBRATION_RADIUS_BLOCKS;
    }

    private boolean canRename(SpaceUnitMapPayload.Entry entry) {
        return canCalibrate(entry);
    }

    private boolean canChangeVisibility(SpaceUnitMapPayload.Entry entry) {
        return canCalibrate(entry);
    }

    private boolean canFavorite(SpaceUnitMapPayload.Entry entry) {
        return !"player".equals(entry.type());
    }

    private boolean canManageAccess(SpaceUnitMapPayload.Entry entry, String role) {
        if (!canCalibrate(entry)) {
            return false;
        }
        return !ACCESS_ROLE_ADMINISTRATOR.equals(role) || entry.owned();
    }

    private String visibilityLabelId(String visibility) {
        return switch (visibility) {
            case "friends" -> "friends";
            case "public" -> "public";
            default -> "private";
        };
    }

    private long distanceSquaredToSource(SpaceUnitMapPayload.Entry entry) {
        long dx = (long) entry.x() - this.payload.sourceX();
        long dy = (long) entry.y() - this.payload.sourceY();
        long dz = (long) entry.z() - this.payload.sourceZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private enum TypeFilter {
        ALL("all"),
        LODESTONE("lodestone"),
        DEATH("death"),
        PLAYER("player"),
        TEMPORARY("temporary"),
        SYSTEM("system");

        private final String id;

        TypeFilter(String id) {
            this.id = id;
        }

        private TypeFilter next() {
            TypeFilter[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        private boolean matches(SpaceUnitMapPayload.Entry entry) {
            return this == ALL || entry.type().equals(this.id);
        }

        private Component label() {
            return this == ALL
                    ? Component.translatable("message.deadrecall.space_unit.map_filter_all")
                    : Component.translatable("message.deadrecall.space_unit.type." + this.id);
        }
    }

    private enum FriendFilter {
        ALL("all"),
        SHARED("shared");

        private final String id;

        FriendFilter(String id) {
            this.id = id;
        }

        private FriendFilter next() {
            FriendFilter[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        private boolean matches(SpaceUnitMapPayload.Entry entry) {
            return this == ALL || entry.friendShared();
        }

        private Component label() {
            return Component.translatable("message.deadrecall.space_unit.map_friend_filter_" + this.id);
        }
    }

    private enum SortMode {
        NAME("name"),
        DISTANCE("distance"),
        STABILITY("stability"),
        COST("cost"),
        TIME("time");

        private final String id;

        SortMode(String id) {
            this.id = id;
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        private Component label() {
            return Component.translatable("message.deadrecall.space_unit.map_sort." + this.id);
        }
    }

    private class RenameSpaceUnitScreen extends Screen {
        private final UUID targetUnitId;
        private final String initialName;
        private EditBox nameField;

        private RenameSpaceUnitScreen(SpaceUnitMapPayload.Entry target) {
            super(Component.translatable("message.deadrecall.space_unit.rename_title"));
            this.targetUnitId = target.id();
            this.initialName = target.name();
        }

        @Override
        protected void init() {
            int dialogWidth = 260;
            int dialogHeight = 100;
            int x = (this.width - dialogWidth) / 2;
            int y = (this.height - dialogHeight) / 2;

            this.nameField = new EditBox(this.font, x + 12, y + 38, dialogWidth - 24, 18,
                    Component.translatable("message.deadrecall.space_unit.rename_name"));
            this.nameField.setMaxLength(MAX_RENAME_LENGTH);
            this.nameField.setValue(this.initialName);
            this.addRenderableWidget(this.nameField);

            this.addRenderableWidget(Button.builder(
                            Component.translatable("message.deadrecall.space_unit.rename_save"),
                            button -> submit())
                    .bounds(x + dialogWidth - 124, y + dialogHeight - 28, 54, 18)
                    .build());
            this.addRenderableWidget(Button.builder(
                            Component.translatable("gui.cancel"),
                            button -> this.onClose())
                    .bounds(x + dialogWidth - 64, y + dialogHeight - 28, 52, 18)
                    .build());
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
            extractor.fill(0, 0, this.width, this.height, 0xB0000000);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
            int dialogWidth = 260;
            int dialogHeight = 100;
            int x = (this.width - dialogWidth) / 2;
            int y = (this.height - dialogHeight) / 2;
            extractor.fill(x, y, x + dialogWidth, y + dialogHeight, 0xF016191D);
            extractor.outline(x, y, dialogWidth, dialogHeight, 0xFF657383);
            extractor.text(this.font, this.title, x + 12, y + 10, 0xFFFFFFFF);
            extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.rename_name"), x + 12, y + 28, 0xFFB8C0C8);
            super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(NexusSpaceUnitMapScreen.this);
            }
        }

        private void submit() {
            sendRename(this.targetUnitId, this.nameField.getValue());
            onClose();
        }
    }

    private class AccessSpaceUnitScreen extends Screen {
        private final UUID targetUnitId;
        private final String role;
        private EditBox playerNameField;

        private AccessSpaceUnitScreen(SpaceUnitMapPayload.Entry target, String role) {
            super(Component.translatable("message.deadrecall.space_unit.access_title." + role));
            this.targetUnitId = target.id();
            this.role = role;
        }

        @Override
        protected void init() {
            int dialogWidth = 280;
            int dialogHeight = 116;
            int x = (this.width - dialogWidth) / 2;
            int y = (this.height - dialogHeight) / 2;

            this.playerNameField = new EditBox(this.font, x + 12, y + 42, dialogWidth - 24, 18,
                    Component.translatable("message.deadrecall.space_unit.access_player"));
            this.playerNameField.setMaxLength(MAX_ACCESS_PLAYER_NAME_LENGTH);
            this.addRenderableWidget(this.playerNameField);

            this.addRenderableWidget(Button.builder(
                            Component.translatable("message.deadrecall.space_unit.access_add"),
                            button -> submit(true))
                    .bounds(x + dialogWidth - 184, y + dialogHeight - 28, 52, 18)
                    .build());
            this.addRenderableWidget(Button.builder(
                            Component.translatable("message.deadrecall.space_unit.access_remove"),
                            button -> submit(false))
                    .bounds(x + dialogWidth - 126, y + dialogHeight - 28, 58, 18)
                    .build());
            this.addRenderableWidget(Button.builder(
                            Component.translatable("gui.cancel"),
                            button -> this.onClose())
                    .bounds(x + dialogWidth - 62, y + dialogHeight - 28, 50, 18)
                    .build());
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
            extractor.fill(0, 0, this.width, this.height, 0xB0000000);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
            int dialogWidth = 280;
            int dialogHeight = 116;
            int x = (this.width - dialogWidth) / 2;
            int y = (this.height - dialogHeight) / 2;
            extractor.fill(x, y, x + dialogWidth, y + dialogHeight, 0xF016191D);
            extractor.outline(x, y, dialogWidth, dialogHeight, 0xFF657383);
            extractor.text(this.font, this.title, x + 12, y + 10, 0xFFFFFFFF);
            extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.access_player"), x + 12, y + 30, 0xFFB8C0C8);
            super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(NexusSpaceUnitMapScreen.this);
            }
        }

        private void submit(boolean enabled) {
            sendAccessUpdate(this.targetUnitId, this.role, this.playerNameField.getValue(), enabled);
            onClose();
        }
    }
}
