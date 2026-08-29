package dev.totem.nexus.client;

import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.ManageDeathNodeAdminPayload;
import dev.totem.nexus.network.RequestDeathNodeAdminPayload;
import dev.totem.nexus.space.NexusDeathNodeAdminService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public final class NexusDeathNodeAdminScreen extends NexusOwnedScreen {
    public static NexusDeathNodeAdminScreen CURRENT;

    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 380;
    private static final int PANEL_PADDING = 12;
    private static final int HEADER_HEIGHT = 34;
    private static final int CONTROL_HEIGHT = 52;
    private static final int DETAIL_HEIGHT = 72;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 34;
    private static final int COMPACT_PANEL_WIDTH = 520;
    private static final int TELEPORT_BUTTON_WIDTH = 72;

    private DeathNodeAdminPayload payload;
    private UUID selectedNodeId;
    private String ownerQuery = "";
    private String dimensionId = "";
    private StatusFilter statusFilter = StatusFilter.ALL;
    private TimeFilter timeFilter = TimeFilter.ALL;
    private int scrollIndex;

    private EditBox ownerQueryField;
    private EditBox dimensionField;
    private Button statusFilterButton;
    private Button timeFilterButton;
    private Button refreshButton;
    private Button batchDisableButton;
    private Button batchPurgeButton;
    private Button previousPageButton;
    private Button nextPageButton;
    private Button teleportButton;
    private Button disableButton;
    private Button purgeButton;
    private Button doneButton;

    public NexusDeathNodeAdminScreen(DeathNodeAdminPayload payload) {
        this(payload, false, () -> { });
    }

    NexusDeathNodeAdminScreen(DeathNodeAdminPayload payload, boolean observer, Runnable stop) {
        super(Component.translatable("container.deadrecall.death_node_admin"), observer, stop);
        this.payload = payload;
        this.selectedNodeId = payload.entries().stream().findFirst().map(DeathNodeAdminPayload.Entry::id).orElse(null);
        CURRENT = this;
    }

    @Override
    protected void init() {
        CURRENT = this;

        this.ownerQueryField = new EditBox(
                this.font,
                ownerQueryX(),
                controlsY(),
                ownerQueryWidth(),
                18,
                Component.translatable("message.deadrecall.death_node_admin.owner_query")
        );
        this.ownerQueryField.setMaxLength(64);
        this.ownerQueryField.setValue(this.ownerQuery);
        this.ownerQueryField.setHint(Component.translatable("message.deadrecall.death_node_admin.owner_query"));
        this.ownerQueryField.setResponder(value -> this.ownerQuery = value == null ? "" : value);
        this.addRenderableWidget(this.ownerQueryField);

        this.dimensionField = new EditBox(
                this.font,
                dimensionX(),
                controlsY(),
                dimensionWidth(),
                18,
                Component.translatable("message.deadrecall.death_node_admin.dimension_query")
        );
        this.dimensionField.setMaxLength(128);
        this.dimensionField.setValue(this.dimensionId);
        this.dimensionField.setHint(Component.translatable("message.deadrecall.death_node_admin.dimension_query"));
        this.dimensionField.setResponder(value -> this.dimensionId = value == null ? "" : value);
        this.addRenderableWidget(this.dimensionField);

        this.statusFilterButton = Button.builder(statusFilterText(), button -> cycleStatus())
                .bounds(statusFilterX(), controlsY(), statusFilterWidth(), 18)
        .build();
        this.addRenderableWidget(this.statusFilterButton);

        this.timeFilterButton = Button.builder(timeFilterText(), button -> cycleTimeFilter())
                .bounds(timeFilterX(), controlsY(), timeFilterWidth(), 18)
                .build();
        this.addRenderableWidget(this.timeFilterButton);

        this.refreshButton = Button.builder(Component.translatable("message.deadrecall.death_node_admin.apply_filters"), button -> requestRefresh())
                .bounds(refreshX(), controlsY(), refreshWidth(), 18)
        .build();
        this.addRenderableWidget(this.refreshButton);

        this.batchDisableButton = Button.builder(batchDisableButtonText(), button -> batchDisable())
                .bounds(batchDisableX(), batchControlsY(), 164, 18)
                .build();
        this.addRenderableWidget(this.batchDisableButton);

        this.batchPurgeButton = Button.builder(batchPurgeButtonText(), button -> batchPurge())
                .bounds(batchPurgeX(), batchControlsY(), 164, 18)
                .build();
        this.addRenderableWidget(this.batchPurgeButton);

        this.previousPageButton = Button.builder(Component.literal("<"), button -> requestPage(this.payload.page() - 1))
                .bounds(previousPageX(), footerY(), pageButtonWidth(), 18)
                .build();
        this.addRenderableWidget(this.previousPageButton);

        this.nextPageButton = Button.builder(Component.literal(">"), button -> requestPage(this.payload.page() + 1))
                .bounds(nextPageX(), footerY(), pageButtonWidth(), 18)
                .build();
        this.addRenderableWidget(this.nextPageButton);

        this.teleportButton = Button.builder(Component.translatable("message.deadrecall.death_node_admin.teleport"), button -> teleportToSelected())
                .bounds(teleportX(), footerY(), TELEPORT_BUTTON_WIDTH, 18)
                .build();
        this.addRenderableWidget(this.teleportButton);

        this.disableButton = Button.builder(Component.translatable("message.deadrecall.death_node_admin.disable"), button -> disableSelected())
                .bounds(disableX(), footerY(), 72, 18)
                .build();
        this.addRenderableWidget(this.disableButton);

        this.purgeButton = Button.builder(purgeButtonText(), button -> purgeSelected())
                .bounds(purgeX(), footerY(), 88, 18)
                .build();
        this.addRenderableWidget(this.purgeButton);

        this.doneButton = Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(doneX(), footerY(), 62, 18)
                .build();
        this.addRenderableWidget(this.doneButton);

        updateButtons();
    }

    @Override
    public void removed() {
        super.removed();
        if (CURRENT == this) {
            CURRENT = null;
        }
    }

    public void applyPayload(DeathNodeAdminPayload payload) {
        this.payload = payload;
        if (selectedEntry() == null) {
            this.selectedNodeId = this.payload.entries().stream().findFirst().map(DeathNodeAdminPayload.Entry::id).orElse(null);
        }
        this.scrollIndex = Math.min(this.scrollIndex, maxScrollIndex());
        updateButtons();
    }

    DeathNodeAdminPayload observerPayload() { return payload; }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        int x = panelX();
        int y = panelY();
        int width = panelWidth();
        int height = panelHeight();

        extractor.fill(x, y, x + width, y + height, 0xF016191D);
        extractor.outline(x, y, width, height, 0xFF657383);
        extractor.text(this.font, screenTitle(), x + PANEL_PADDING, y + 10, 0xFFFFFFFF);
        extractor.text(this.font, countSummary(), x + width - PANEL_PADDING - 180, y + 10, 0xFFB8C0C8);

        drawEntries(extractor, mouseX, mouseY);
        drawSelectedDetails(extractor);
        if (this.payload.truncated()) {
            String moreResults = Component.translatable("message.deadrecall.death_node_admin.more_results").getString();
            int moreResultsX = nextPageX() + pageButtonWidth() + 8;
            int firstActionX = this.payload.administratorView() ? teleportX() : purgeX();
            if (this.font.width(moreResults) <= firstActionX - 8 - moreResultsX) {
                extractor.text(this.font, moreResults, moreResultsX, footerY() + 5, 0xFFFFC857);
            }
        }
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (observerReadOnly()) return true;
        UUID hit = entryAt(event.x(), event.y());
        if (hit != null) {
            this.selectedNodeId = hit;
            updateButtons();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (observerReadOnly()) return true;
        if (!isInside(mouseX, mouseY, listX(), listY(), listWidth(), listHeight())) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (verticalAmount < 0) {
            this.scrollIndex = Math.min(maxScrollIndex(), this.scrollIndex + 1);
        } else if (verticalAmount > 0) {
            this.scrollIndex = Math.max(0, this.scrollIndex - 1);
        }
        return true;
    }

    private void drawEntries(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        int width = listWidth();
        int height = listHeight();
        extractor.fill(x, y, x + width, y + height, 0x80101010);
        extractor.outline(x, y, width, height, 0xFF3F4A56);

        List<DeathNodeAdminPayload.Entry> entries = filteredEntries();
        if (entries.isEmpty()) {
            extractor.text(this.font, Component.translatable("message.deadrecall.death_node_admin.no_results").getString(), x + 8, y + 10, 0xFFFFC857);
            return;
        }

        int visibleRows = visibleRows();
        int start = Math.min(this.scrollIndex, Math.max(0, entries.size() - visibleRows));
        int rowY = y + 4;
        for (int index = start; index < entries.size() && index < start + visibleRows; index++) {
            DeathNodeAdminPayload.Entry entry = entries.get(index);
            boolean selected = entry.id().equals(this.selectedNodeId);
            boolean hovered = isInside(mouseX, mouseY, x + 4, rowY, width - 12, ROW_HEIGHT - 4);
            extractor.fill(x + 4, rowY, x + width - 8, rowY + ROW_HEIGHT - 4,
                    selected ? 0xFF2D3F54 : hovered ? 0xC02A2F36 : 0x9020252B);
            extractor.outline(x + 4, rowY, width - 12, ROW_HEIGHT - 4, selected ? 0xFF78A6D6 : 0xFF343D47);

            int statusColor = "active".equals(entry.status()) ? 0xFF6AD98F : 0xFF9CA3AF;
            extractor.fill(x + 10, rowY + 8, x + 18, rowY + 16, statusColor);
            extractor.text(this.font,
                    trimToWidth(entry.name(), this.payload.administratorView() ? width - 250 : width - 145),
                    x + 24,
                    rowY + 5,
                    0xFFFFFFFF);
            if (this.payload.administratorView()) {
                extractor.text(this.font, trimToWidth(entry.ownerName(), 104), x + width - 226, rowY + 5, 0xFFD2D8E0);
            }
            extractor.text(this.font, statusText(entry.status()), x + width - 110, rowY + 5, statusColor);
            extractor.text(this.font, locationLine(entry), x + 24, rowY + 18, 0xFFB8C0C8);
            rowY += ROW_HEIGHT;
        }

        if (entries.size() > visibleRows) {
            drawScrollBar(extractor, x + width - 5, y + 4, height - 8, entries.size(), visibleRows, start);
        }
    }

    private void drawScrollBar(GuiGraphicsExtractor extractor, int x, int y, int height, int totalRows, int visibleRows, int start) {
        int thumbHeight = Math.max(16, height * visibleRows / Math.max(visibleRows, totalRows));
        int thumbTravel = Math.max(1, height - thumbHeight);
        int maxStart = Math.max(1, totalRows - visibleRows);
        int thumbY = y + thumbTravel * start / maxStart;
        extractor.fill(x, y, x + 3, y + height, 0x80333333);
        extractor.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF9A9A9A);
    }

    private void drawSelectedDetails(GuiGraphicsExtractor extractor) {
        int x = listX();
        int y = detailsY();
        int width = listWidth();
        extractor.fill(x, y, x + width, y + DETAIL_HEIGHT - 6, 0x8020252B);
        extractor.outline(x, y, width, DETAIL_HEIGHT - 6, 0xFF3F4A56);
        DeathNodeAdminPayload.Entry selected = selectedEntry();
        if (selected == null) {
            extractor.text(this.font,
                    Component.translatable("message.deadrecall.death_node_admin.details.none_selected").getString(),
                    x + 8, y + 8, 0xFFB8C0C8);
            return;
        }

        extractor.text(this.font,
                Component.translatable("message.deadrecall.death_node_admin.details.title").getString(),
                x + 8, y + 6, 0xFFFFFFFF);
        if (this.payload.administratorView()) {
            extractor.text(this.font,
                    Component.translatable(
                            "message.deadrecall.death_node_admin.details.owner",
                            selected.ownerName(),
                            selected.ownerId()).getString(),
                    x + 8, y + 20, 0xFFD2D8E0);
        } else {
            extractor.text(this.font,
                    Component.translatable("message.deadrecall.death_node_admin.owner_delete_warning").getString(),
                    x + 8, y + 20, 0xFFFFC857);
        }
        extractor.text(this.font,
                Component.translatable(
                        "message.deadrecall.death_node_admin.details.node",
                        selected.id()).getString(),
                x + 8, y + 32, 0xFFD2D8E0);
        String times = Component.translatable(
                "message.deadrecall.death_node_admin.details.times",
                selected.createdGameTime(),
                selected.updatedGameTime()).getString();
        extractor.text(this.font, times, x + 8, y + 44, 0xFFB8C0C8);
        if (this.payload.administratorView()) {
            String diagnostics = Component.translatable(
                    "message.deadrecall.death_node_admin.details.diagnostics",
                    diagnosticsText(selected)).getString();
            int contentWidth = width - 16;
            if (this.font.width(times) + 12 + this.font.width(diagnostics) <= contentWidth) {
                extractor.text(this.font, diagnostics, x + 20 + this.font.width(times), y + 44, 0xFFFFC857);
            } else {
                extractor.text(this.font, trimToWidth(diagnostics, contentWidth), x + 8, y + 56, 0xFFFFC857);
            }
        }
    }

    private void cycleStatus() {
        this.statusFilter = this.statusFilter.next();
        requestRefresh();
    }

    private void cycleTimeFilter() {
        this.timeFilter = this.timeFilter.next();
        requestRefresh();
    }

    private void requestRefresh() {
        requestPage(0);
    }

    private void requestPage(int page) {
        if (ClientPlayNetworking.canSend(RequestDeathNodeAdminPayload.TYPE)) {
            ClientPlayNetworking.send(new RequestDeathNodeAdminPayload(
                    this.payload.administratorView() ? this.ownerQuery : "",
                    this.dimensionId,
                    this.statusFilter.id,
                    this.timeFilter.createdAfterGameTime(this.payload.serverGameTime()),
                    0L,
                    Math.max(0, page)
            ));
        }
    }

    private void disableSelected() {
        DeathNodeAdminPayload.Entry selected = selectedEntry();
        if (selected == null || !"active".equals(selected.status())) {
            return;
        }
        sendAction(selected.id(), NexusDeathNodeAdminService.ACTION_DISABLE);
    }

    private void teleportToSelected() {
        DeathNodeAdminPayload.Entry selected = selectedEntry();
        if (selected != null) {
            sendAction(selected.id(), NexusDeathNodeAdminService.ACTION_TELEPORT);
        }
    }

    private void purgeSelected() {
        DeathNodeAdminPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        if (!this.payload.administratorView()) {
            if (!this.payload.hasActiveConfirmationFor(
                    selected.id(),
                    NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                    System.currentTimeMillis())) {
                sendAction(selected.id(), NexusDeathNodeAdminService.ACTION_REQUEST_OWNER_PURGE);
                return;
            }
            sendAction(
                    selected.id(),
                    NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                    this.payload.confirmationToken()
            );
            return;
        }
        if ("active".equals(selected.status())) {
            return;
        }
        if (!this.payload.hasActivePurgeConfirmationFor(selected.id(), System.currentTimeMillis())) {
            sendAction(selected.id(), NexusDeathNodeAdminService.ACTION_REQUEST_PURGE);
            return;
        }
        sendAction(selected.id(), NexusDeathNodeAdminService.ACTION_PURGE, this.payload.confirmationToken());
    }

    private void batchDisable() {
        if (!this.payload.hasActiveConfirmationFor(
                NexusDeathNodeAdminService.BATCH_NODE_ID,
                NexusDeathNodeAdminService.ACTION_BATCH_DISABLE,
                System.currentTimeMillis())) {
            sendAction(NexusDeathNodeAdminService.BATCH_NODE_ID, NexusDeathNodeAdminService.ACTION_REQUEST_BATCH_DISABLE);
            return;
        }
        sendAction(
                NexusDeathNodeAdminService.BATCH_NODE_ID,
                NexusDeathNodeAdminService.ACTION_BATCH_DISABLE,
                this.payload.confirmationToken()
        );
    }

    private void batchPurge() {
        if (!this.payload.hasActiveConfirmationFor(
                NexusDeathNodeAdminService.BATCH_NODE_ID,
                NexusDeathNodeAdminService.ACTION_BATCH_PURGE,
                System.currentTimeMillis())) {
            sendAction(NexusDeathNodeAdminService.BATCH_NODE_ID, NexusDeathNodeAdminService.ACTION_REQUEST_BATCH_PURGE);
            return;
        }
        sendAction(
                NexusDeathNodeAdminService.BATCH_NODE_ID,
                NexusDeathNodeAdminService.ACTION_BATCH_PURGE,
                this.payload.confirmationToken()
        );
    }

    private void sendAction(UUID nodeId, String action) {
        sendAction(nodeId, action, null);
    }

    private void sendAction(UUID nodeId, String action, UUID confirmationToken) {
        if (ClientPlayNetworking.canSend(ManageDeathNodeAdminPayload.TYPE)) {
            ClientPlayNetworking.send(new ManageDeathNodeAdminPayload(nodeId, action, confirmationToken));
        }
    }

    private void updateButtons() {
        if (this.ownerQueryField != null) {
            this.ownerQueryField.visible = this.payload.administratorView();
            this.ownerQueryField.setX(ownerQueryX());
            this.ownerQueryField.setY(controlsY());
            this.ownerQueryField.setWidth(ownerQueryWidth());
        }
        if (this.dimensionField != null) {
            this.dimensionField.setX(dimensionX());
            this.dimensionField.setY(controlsY());
            this.dimensionField.setWidth(dimensionWidth());
        }
        if (this.statusFilterButton != null) {
            this.statusFilterButton.setX(statusFilterX());
            this.statusFilterButton.setY(controlsY());
            this.statusFilterButton.setWidth(statusFilterWidth());
            this.statusFilterButton.setMessage(statusFilterText());
        }
        if (this.timeFilterButton != null) {
            this.timeFilterButton.setX(timeFilterX());
            this.timeFilterButton.setY(controlsY());
            this.timeFilterButton.setWidth(timeFilterWidth());
            this.timeFilterButton.setMessage(timeFilterText());
        }
        if (this.refreshButton != null) {
            this.refreshButton.setX(refreshX());
            this.refreshButton.setY(controlsY());
            this.refreshButton.setWidth(refreshWidth());
        }
        if (this.batchDisableButton != null) {
            this.batchDisableButton.visible = this.payload.administratorView();
            this.batchDisableButton.setX(batchDisableX());
            this.batchDisableButton.setY(batchControlsY());
            this.batchDisableButton.setMessage(batchDisableButtonText());
            this.batchDisableButton.active = this.payload.administratorView() && this.payload.totalEntries() > 0;
        }
        if (this.batchPurgeButton != null) {
            this.batchPurgeButton.visible = this.payload.administratorView();
            this.batchPurgeButton.setX(batchPurgeX());
            this.batchPurgeButton.setY(batchControlsY());
            this.batchPurgeButton.setMessage(batchPurgeButtonText());
            this.batchPurgeButton.active = this.payload.administratorView() && this.payload.totalEntries() > 0;
        }
        if (this.previousPageButton != null) {
            this.previousPageButton.setX(previousPageX());
            this.previousPageButton.setY(footerY());
            this.previousPageButton.setWidth(pageButtonWidth());
            this.previousPageButton.active = this.payload.page() > 0;
        }
        if (this.nextPageButton != null) {
            this.nextPageButton.setX(nextPageX());
            this.nextPageButton.setY(footerY());
            this.nextPageButton.setWidth(pageButtonWidth());
            this.nextPageButton.active = this.payload.truncated();
        }
        DeathNodeAdminPayload.Entry selected = selectedEntry();
        if (this.teleportButton != null) {
            this.teleportButton.visible = this.payload.administratorView();
            this.teleportButton.setX(teleportX());
            this.teleportButton.setY(footerY());
            this.teleportButton.setWidth(TELEPORT_BUTTON_WIDTH);
            this.teleportButton.active = this.payload.administratorView() && selected != null;
        }
        if (this.disableButton != null) {
            this.disableButton.visible = this.payload.administratorView();
            this.disableButton.setX(disableX());
            this.disableButton.setY(footerY());
            this.disableButton.active = this.payload.administratorView()
                    && selected != null
                    && "active".equals(selected.status());
        }
        if (this.purgeButton != null) {
            this.purgeButton.setX(purgeX());
            this.purgeButton.setY(footerY());
            this.purgeButton.setMessage(purgeButtonText());
            this.purgeButton.active = selected != null
                    && (!this.payload.administratorView() || !"active".equals(selected.status()));
        }
        if (this.doneButton != null) {
            this.doneButton.setX(doneX());
            this.doneButton.setY(footerY());
        }
    }

    private List<DeathNodeAdminPayload.Entry> filteredEntries() {
        return this.payload.entries();
    }

    private DeathNodeAdminPayload.Entry selectedEntry() {
        if (this.selectedNodeId == null) {
            return null;
        }
        for (DeathNodeAdminPayload.Entry entry : filteredEntries()) {
            if (entry.id().equals(this.selectedNodeId)) {
                return entry;
            }
        }
        return null;
    }

    private UUID entryAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, listX(), listY(), listWidth(), listHeight())) {
            return null;
        }
        int relativeY = (int) mouseY - (listY() + 4);
        if (relativeY < 0) {
            return null;
        }
        List<DeathNodeAdminPayload.Entry> entries = filteredEntries();
        int index = Math.min(this.scrollIndex, maxScrollIndex()) + relativeY / ROW_HEIGHT;
        return index >= 0 && index < entries.size() ? entries.get(index).id() : null;
    }

    private Component statusFilterText() {
        return Component.translatable("message.deadrecall.death_node_admin.status_filter", this.statusFilter.label());
    }

    private Component screenTitle() {
        return Component.translatable(this.payload.administratorView()
                ? "container.deadrecall.death_node_admin"
                : "container.deadrecall.death_node_owner");
    }

    private Component timeFilterText() {
        return Component.translatable("message.deadrecall.death_node_admin.time_filter", this.timeFilter.label());
    }

    private Component purgeButtonText() {
        DeathNodeAdminPayload.Entry selected = selectedEntry();
        if (!this.payload.administratorView()) {
            return selected != null && this.payload.hasActiveConfirmationFor(
                    selected.id(),
                    NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                    System.currentTimeMillis())
                    ? Component.translatable("message.deadrecall.death_node_admin.confirm_owner_purge")
                    : Component.translatable("message.deadrecall.death_node_admin.owner_purge");
        }
        return selected != null && this.payload.hasActivePurgeConfirmationFor(selected.id(), System.currentTimeMillis())
                ? Component.translatable("message.deadrecall.death_node_admin.confirm_purge")
                : Component.translatable(panelWidth() < COMPACT_PANEL_WIDTH
                        ? "message.deadrecall.death_node_admin.purge_compact"
                        : "message.deadrecall.death_node_admin.purge");
    }

    private Component batchDisableButtonText() {
        return this.payload.hasActiveConfirmationFor(
                NexusDeathNodeAdminService.BATCH_NODE_ID,
                NexusDeathNodeAdminService.ACTION_BATCH_DISABLE,
                System.currentTimeMillis())
                ? Component.translatable("message.deadrecall.death_node_admin.confirm_batch_disable")
                : Component.translatable("message.deadrecall.death_node_admin.batch_disable");
    }

    private Component batchPurgeButtonText() {
        return this.payload.hasActiveConfirmationFor(
                NexusDeathNodeAdminService.BATCH_NODE_ID,
                NexusDeathNodeAdminService.ACTION_BATCH_PURGE,
                System.currentTimeMillis())
                ? Component.translatable("message.deadrecall.death_node_admin.confirm_batch_purge")
                : Component.translatable("message.deadrecall.death_node_admin.batch_purge");
    }

    private String countSummary() {
        return Component.translatable(
                "message.deadrecall.death_node_admin.page_summary",
                this.payload.page() + 1,
                this.payload.entries().size(),
                this.payload.totalEntries()
        ).getString();
    }

    private String locationLine(DeathNodeAdminPayload.Entry entry) {
        return shortDimension(entry.dimension()) + "  " + entry.x() + ", " + entry.y() + ", " + entry.z()
                + "  |  " + shortId(entry.id());
    }

    private String statusText(String status) {
        return switch (status) {
            case "active" -> Component.translatable("message.deadrecall.death_node_admin.status.active").getString();
            case "disabled" -> Component.translatable("message.deadrecall.death_node_admin.status.disabled").getString();
            default -> status.toUpperCase(java.util.Locale.ROOT);
        };
    }

    private String diagnosticsText(DeathNodeAdminPayload.Entry entry) {
        if (entry.diagnosticFlags().isEmpty()) {
            return Component.translatable("message.deadrecall.death_node_admin.diagnostics.none").getString();
        }
        return entry.diagnosticFlags().stream()
                .map(this::diagnosticText)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    private String diagnosticText(String diagnosticId) {
        return Component.translatable("message.deadrecall.death_node_admin.diagnostic." + diagnosticId).getString();
    }

    private int maxScrollIndex() {
        return Math.max(0, filteredEntries().size() - visibleRows());
    }

    private int visibleRows() {
        return Math.max(1, (listHeight() - 8) / ROW_HEIGHT);
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

    private int controlsY() {
        return panelY() + HEADER_HEIGHT + 3;
    }

    private int listX() {
        return panelX() + PANEL_PADDING;
    }

    private int listY() {
        return panelY() + HEADER_HEIGHT + CONTROL_HEIGHT;
    }

    private int listWidth() {
        return panelWidth() - PANEL_PADDING * 2;
    }

    private int listHeight() {
        return panelHeight() - HEADER_HEIGHT - CONTROL_HEIGHT - DETAIL_HEIGHT - FOOTER_HEIGHT;
    }

    private int detailsY() {
        return listY() + listHeight() + 4;
    }

    private int ownerQueryX() {
        return panelX() + PANEL_PADDING;
    }

    private int ownerQueryWidth() {
        return this.payload.administratorView() ? scaledAdminControlWidth(120) : 0;
    }

    private int dimensionX() {
        return this.payload.administratorView()
                ? ownerQueryX() + ownerQueryWidth() + 4
                : panelX() + PANEL_PADDING;
    }

    private int dimensionWidth() {
        return this.payload.administratorView() ? scaledAdminControlWidth(130) : 130;
    }

    private int statusFilterX() {
        return dimensionX() + dimensionWidth() + 4;
    }

    private int statusFilterWidth() {
        return this.payload.administratorView() ? scaledAdminControlWidth(96) : 96;
    }

    private int timeFilterX() {
        return statusFilterX() + statusFilterWidth() + 4;
    }

    private int timeFilterWidth() {
        return this.payload.administratorView() ? scaledAdminControlWidth(86) : 86;
    }

    private int footerY() {
        return panelY() + panelHeight() - 25;
    }

    private int batchControlsY() {
        return controlsY() + 24;
    }

    private int batchDisableX() {
        return ownerQueryX();
    }

    private int batchPurgeX() {
        return batchDisableX() + 170;
    }

    private int refreshX() {
        return timeFilterX() + timeFilterWidth() + 4;
    }

    private int refreshWidth() {
        return 60;
    }

    private int scaledAdminControlWidth(int preferredWidth) {
        int innerWidth = Math.max(1, panelWidth() - PANEL_PADDING * 2);
        int scalableWidth = Math.max(1, innerWidth - refreshWidth() - 16);
        return Math.max(1, preferredWidth * scalableWidth / (120 + 130 + 96 + 86));
    }

    private int previousPageX() {
        return panelX() + PANEL_PADDING;
    }

    private int nextPageX() {
        return previousPageX() + pageButtonWidth() + 6;
    }

    private int pageButtonWidth() {
        return panelWidth() < COMPACT_PANEL_WIDTH ? 32 : 40;
    }

    private int doneX() {
        return panelX() + panelWidth() - PANEL_PADDING - 62;
    }

    private int purgeX() {
        return doneX() - 6 - 88;
    }

    private int disableX() {
        return purgeX() - 6 - 72;
    }

    private int teleportX() {
        return disableX() - 6 - TELEPORT_BUTTON_WIDTH;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String shortDimension(String dimension) {
        int index = dimension.indexOf(':');
        return index >= 0 && index + 1 < dimension.length() ? dimension.substring(index + 1) : dimension;
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
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

    private enum StatusFilter {
        ALL("message.deadrecall.death_node_admin.status.all", ""),
        ACTIVE("message.deadrecall.death_node_admin.status.active", "active"),
        DISABLED("message.deadrecall.death_node_admin.status.disabled", "disabled");

        private final String labelKey;
        private final String id;

        StatusFilter(String labelKey, String id) {
            this.labelKey = labelKey;
            this.id = id;
        }

        private Component label() {
            return Component.translatable(this.labelKey);
        }

        private StatusFilter next() {
            return switch (this) {
                case ALL -> ACTIVE;
                case ACTIVE -> DISABLED;
                case DISABLED -> ALL;
            };
        }

    }

    private enum TimeFilter {
        ALL("message.deadrecall.death_node_admin.time.all", 0L),
        DAY("message.deadrecall.death_node_admin.time.day", 24_000L),
        WEEK("message.deadrecall.death_node_admin.time.week", 168_000L),
        MONTH("message.deadrecall.death_node_admin.time.month", 720_000L);

        private final String labelKey;
        private final long windowTicks;

        TimeFilter(String labelKey, long windowTicks) {
            this.labelKey = labelKey;
            this.windowTicks = windowTicks;
        }

        private Component label() {
            return Component.translatable(this.labelKey);
        }

        private TimeFilter next() {
            return switch (this) {
                case ALL -> DAY;
                case DAY -> WEEK;
                case WEEK -> MONTH;
                case MONTH -> ALL;
            };
        }

        private long createdAfterGameTime(long serverGameTime) {
            return this.windowTicks == 0L ? 0L : Math.max(0L, serverGameTime - this.windowTicks);
        }
    }
}
