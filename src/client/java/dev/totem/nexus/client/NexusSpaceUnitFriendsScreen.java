package dev.totem.nexus.client;

import dev.totem.nexus.network.RemoveSpaceUnitFriendPayload;
import dev.totem.nexus.network.RequestSpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

final class NexusSpaceUnitFriendsScreen extends NexusOwnedScreen {
    static NexusSpaceUnitFriendsScreen CURRENT = null;

    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 238;
    private static final int PANEL_PADDING = 12;
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 34;
    private static final int ROW_HEIGHT = 28;

    private final Screen parent;
    private SpaceUnitFriendsPayload payload;
    private UUID selectedPlayerId;
    private int scrollIndex = 0;
    private Button removeButton;
    private Button refreshButton;
    private Button doneButton;

    NexusSpaceUnitFriendsScreen(Screen parent, SpaceUnitFriendsPayload payload) {
        this(parent, payload, false, () -> { });
    }

    NexusSpaceUnitFriendsScreen(Screen parent, SpaceUnitFriendsPayload payload, boolean observer, Runnable stop) {
        super(Component.translatable("message.deadrecall.space_unit.friends_title"), observer, stop);
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        CURRENT = this;
        this.refreshButton = Button.builder(
                        Component.translatable("message.deadrecall.space_unit.friends_refresh"),
                        button -> requestRefresh())
                .bounds(refreshButtonX(), footerButtonY(), 62, 18)
                .build();
        this.addRenderableWidget(this.refreshButton);

        this.removeButton = Button.builder(
                        Component.translatable("message.deadrecall.space_unit.friends_remove"),
                        button -> removeSelected())
                .bounds(removeButtonX(), footerButtonY(), 62, 18)
                .build();
        this.addRenderableWidget(this.removeButton);

        this.doneButton = Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(doneButtonX(), footerButtonY(), 62, 18)
                .build();
        this.addRenderableWidget(this.doneButton);

        updateButtons();
        if (this.payload == null && !observerReadOnly()) {
            requestRefresh();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (CURRENT == this) {
            CURRENT = null;
        }
    }

    void applyPayload(SpaceUnitFriendsPayload payload) {
        this.payload = payload;
        if (selectedEntry() == null) {
            this.selectedPlayerId = entries().stream()
                    .findFirst()
                    .map(SpaceUnitFriendsPayload.Entry::id)
                    .orElse(null);
        }
        this.scrollIndex = Math.min(this.scrollIndex, maxScrollIndex());
        updateButtons();
    }

    SpaceUnitFriendsPayload observerPayload() { return payload; }

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
        extractor.text(this.font, this.title, x + PANEL_PADDING, y + 10, 0xFFFFFFFF);
        extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.friends_count", entries().size()),
                x + width - PANEL_PADDING - 88, y + 10, 0xFFB8C0C8);

        drawEntries(extractor, mouseX, mouseY);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (observerReadOnly()) return true;
        UUID hit = entryAt(event.x(), event.y());
        if (hit != null) {
            this.selectedPlayerId = hit;
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
            return true;
        }
        if (verticalAmount > 0) {
            this.scrollIndex = Math.max(0, this.scrollIndex - 1);
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        if (observerReadOnly()) { closeOwnedScreen(); return; }
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private void drawEntries(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        int width = listWidth();
        int height = listHeight();
        extractor.fill(x, y, x + width, y + height, 0x80101010);
        extractor.outline(x, y, width, height, 0xFF3F4A56);

        List<SpaceUnitFriendsPayload.Entry> entries = entries();
        if (entries.isEmpty()) {
            extractor.text(this.font, Component.translatable("message.deadrecall.space_unit.friends_empty"),
                    x + 8, y + 10, 0xFFFFC857);
            return;
        }

        int visibleRows = visibleRows();
        int start = Math.min(this.scrollIndex, Math.max(0, entries.size() - visibleRows));
        int rowY = y + 4;
        for (int i = start; i < entries.size() && i < start + visibleRows; i++) {
            SpaceUnitFriendsPayload.Entry entry = entries.get(i);
            boolean selected = entry.id().equals(this.selectedPlayerId);
            boolean hovered = isInside(mouseX, mouseY, x + 4, rowY, width - 12, ROW_HEIGHT - 4);
            extractor.fill(x + 4, rowY, x + width - 8, rowY + ROW_HEIGHT - 4,
                    selected ? 0xFF2D3F54 : hovered ? 0xC02A2F36 : 0x9020252B);
            extractor.outline(x + 4, rowY, width - 12, ROW_HEIGHT - 4, selected ? 0xFF78A6D6 : 0xFF343D47);
            extractor.fill(x + 10, rowY + 8, x + 18, rowY + 16, entry.online() ? 0xFF6AD98F : 0xFF6B7280);
            extractor.text(this.font, trimToWidth(entry.name(), width - 74), x + 24, rowY + 5, 0xFFFFFFFF);
            extractor.text(this.font, statusLine(entry), x + 24, rowY + 17, 0xFFB8C0C8);
            rowY += ROW_HEIGHT;
        }

        if (entries.size() > visibleRows) {
            drawScrollBar(extractor, x + width - 5, y + 4, height - 8, entries.size(), visibleRows, start);
        }
    }

    private String statusLine(SpaceUnitFriendsPayload.Entry entry) {
        return Component.translatable("message.deadrecall.space_unit.friend_status." + statusId(entry.status())).getString()
                + " | "
                + Component.translatable(entry.online()
                ? "message.deadrecall.space_unit.friend_online"
                : "message.deadrecall.space_unit.friend_offline").getString();
    }

    private String statusId(String status) {
        return switch (status) {
            case "incoming" -> "incoming";
            case "outgoing" -> "outgoing";
            default -> "friend";
        };
    }

    private void drawScrollBar(GuiGraphicsExtractor extractor, int x, int y, int height, int totalRows, int visibleRows, int start) {
        int thumbHeight = Math.max(16, height * visibleRows / Math.max(visibleRows, totalRows));
        int thumbTravel = Math.max(1, height - thumbHeight);
        int maxStart = Math.max(1, totalRows - visibleRows);
        int thumbY = y + thumbTravel * start / maxStart;
        extractor.fill(x, y, x + 3, y + height, 0x80333333);
        extractor.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF9A9A9A);
    }

    private UUID entryAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, listX(), listY(), listWidth(), listHeight())) {
            return null;
        }

        int relativeY = (int) mouseY - (listY() + 4);
        if (relativeY < 0) {
            return null;
        }
        int index = Math.min(this.scrollIndex, maxScrollIndex()) + relativeY / ROW_HEIGHT;
        List<SpaceUnitFriendsPayload.Entry> entries = entries();
        return index >= 0 && index < entries.size() ? entries.get(index).id() : null;
    }

    private SpaceUnitFriendsPayload.Entry selectedEntry() {
        if (this.selectedPlayerId == null) {
            return null;
        }
        for (SpaceUnitFriendsPayload.Entry entry : entries()) {
            if (entry.id().equals(this.selectedPlayerId)) {
                return entry;
            }
        }
        return null;
    }

    private void requestRefresh() {
        if (ClientPlayNetworking.canSend(RequestSpaceUnitFriendsPayload.TYPE)) {
            ClientPlayNetworking.send(new RequestSpaceUnitFriendsPayload());
        }
    }

    private void removeSelected() {
        SpaceUnitFriendsPayload.Entry selected = selectedEntry();
        if (selected == null || !ClientPlayNetworking.canSend(RemoveSpaceUnitFriendPayload.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(new RemoveSpaceUnitFriendPayload(selected.id()));
    }

    private void updateButtons() {
        if (this.refreshButton != null) {
            this.refreshButton.setX(refreshButtonX());
            this.refreshButton.setY(footerButtonY());
        }
        if (this.removeButton != null) {
            this.removeButton.setX(removeButtonX());
            this.removeButton.setY(footerButtonY());
            this.removeButton.active = selectedEntry() != null;
        }
        if (this.doneButton != null) {
            this.doneButton.setX(doneButtonX());
            this.doneButton.setY(footerButtonY());
        }
    }

    private List<SpaceUnitFriendsPayload.Entry> entries() {
        return this.payload == null ? List.of() : this.payload.entries();
    }

    private int maxScrollIndex() {
        return Math.max(0, entries().size() - visibleRows());
    }

    private int visibleRows() {
        return Math.max(1, (listHeight() - 8) / ROW_HEIGHT);
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, Math.max(260, this.width - 12));
    }

    private int panelHeight() {
        return Math.min(PANEL_HEIGHT, Math.max(200, this.height - 12));
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return (this.height - panelHeight()) / 2;
    }

    private int listX() {
        return panelX() + PANEL_PADDING;
    }

    private int listY() {
        return panelY() + HEADER_HEIGHT;
    }

    private int listWidth() {
        return panelWidth() - PANEL_PADDING * 2;
    }

    private int listHeight() {
        return panelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;
    }

    private int footerButtonY() {
        return panelY() + panelHeight() - 24;
    }

    private int refreshButtonX() {
        return panelX() + PANEL_PADDING;
    }

    private int removeButtonX() {
        return panelX() + panelWidth() - PANEL_PADDING - 130;
    }

    private int doneButtonX() {
        return panelX() + panelWidth() - PANEL_PADDING - 62;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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
}
