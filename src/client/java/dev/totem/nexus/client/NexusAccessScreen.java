package dev.totem.nexus.client;

import dev.totem.nexus.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The production access picker and its read-only Observer projection share this screen. */
public final class NexusAccessScreen extends NexusOwnedScreen {
    private static long nextRequest;
    private final Screen parent;
    private AccessPlayersPayload payload;
    private UUID selected;
    private EditBox search;
    private long pendingRequest = -1;
    private boolean initialized;

    public NexusAccessScreen(Screen parent, String source, UUID sourceId, UUID target, String role) {
        this(parent, new AccessPlayersPayload(source, sourceId, target, role, 0, 1, 0, List.of()), false, () -> { });
    }
    NexusAccessScreen(Screen parent, AccessPlayersPayload payload, boolean readOnly, Runnable stop) {
        super(Component.translatable("message.totem.space_unit.access_title." + payload.role()), readOnly, stop);
        this.parent = parent; this.payload = payload;
    }
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(AccessPlayersPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().gui.screen() instanceof NexusAccessScreen screen && !screen.observerReadOnly()
                    && payload.requestId() == screen.pendingRequest && screen.sameContext(payload)) screen.applyPage(payload);
        }));
    }
    private boolean sameContext(AccessPlayersPayload next) {
        return payload.sourceType().equals(next.sourceType()) && payload.sourceId().equals(next.sourceId())
                && payload.targetId().equals(next.targetId()) && payload.role().equals(next.role());
    }
    @Override protected void init() {
        String query = search == null || observerReadOnly() ? "" : search.getValue();
        int x = (width - 300) / 2, y = (height - 238) / 2;
        search = new EditBox(font, x + 12, y + 30, 216, 18, text("access_search"));
        search.setMaxLength(64); search.setHint(text("access_search")); search.setValue(query);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(text("access_search_button"), b -> request(0)).bounds(x + 232,y + 30,56,18).build());
        for (int i = 0; i < payload.players().size(); i++) {
            var entry = payload.players().get(i);
            String name = font.plainSubstrByWidth(entry.name(), 116);
            Component label = Component.literal((entry.id().equals(selected) ? "> " : "") + name + " #" + entry.id().toString().substring(0,8) + " ")
                    .append(text(entry.online() ? "access_online" : "access_offline"))
                    .append(entry.member() ? " *" : "");
            addRenderableWidget(Button.builder(label, b -> select(entry.id()))
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(entry.name() + "\n" + entry.id())))
                    .bounds(x + 12,y + 60 + i * 22,276,20).build());
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> request(payload.page() - 1))
                .bounds(x + 12,y + 194,28,18).build());
        previous.active = payload.page() > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), b -> request(payload.page() + 1))
                .bounds(x + 260,y + 194,28,18).build());
        next.active = payload.page() + 1 < payload.pages();
        var add = addRenderableWidget(Button.builder(text("access_add"), b -> submit(true)).bounds(x + 78,y + 216,66,18).build());
        var remove = addRenderableWidget(Button.builder(text("access_remove"), b -> submit(false)).bounds(x + 148,y + 216,70,18).build());
        add.active = selected != null; remove.active = selected != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 222,y + 216,66,18).build());
        if (!initialized) { initialized = true; if (!observerReadOnly()) request(0); }
    }
    private static Component text(String key) { return Component.translatable("message.totem.space_unit." + key); }
    private void request(int page) {
        if (observerReadOnly() || page < 0 || !ClientPlayNetworking.canSend(RequestAccessPlayersPayload.TYPE)) return;
        pendingRequest = ++nextRequest;
        ClientPlayNetworking.send(new RequestAccessPlayersPayload(payload.sourceType(),payload.sourceId(),payload.targetId(),
                payload.role(),search.getValue(),page,pendingRequest));
    }
    void select(UUID id) {
        if (observerReadOnly() || payload.players().stream().noneMatch(p -> p.id().equals(id))) return;
        selected = id; rebuildWidgets();
    }
    void submit(boolean enabled) {
        if (observerReadOnly() || selected == null || !ClientPlayNetworking.canSend(UpdateSpaceUnitAccessPayload.TYPE)) return;
        ClientPlayNetworking.send(new UpdateSpaceUnitAccessPayload(payload.sourceType(),payload.sourceId(),payload.targetId(),
                payload.role(),selected.toString(),enabled));
        onClose();
    }
    void applyPage(AccessPlayersPayload next) {
        if (!sameContext(next)) throw new IllegalArgumentException("Mismatched access dialog context");
        payload = next; selected = null;
        if (minecraft != null) rebuildWidgets();
    }
    AccessPlayersPayload observerPayload() { return payload; }
    UUID observerSelection() { return selected; }
    void applyObserverSelection(UUID id) {
        if (id != null && payload.players().stream().noneMatch(p -> p.id().equals(id)))
            throw new IllegalArgumentException("Unknown selected access player");
        selected = id;
        if (minecraft != null) rebuildWidgets();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
        int x = (width - 300) / 2, y = (height - 238) / 2;
        graphics.centeredText(font,title,width / 2,y + 10,0xFFFFFFFF);
        graphics.centeredText(font,Component.literal((payload.page() + 1) + " / " + payload.pages()),width / 2,y + 199,0xFFFFFFFF);
        if (payload.players().isEmpty()) graphics.centeredText(font,text("access_no_players"),width / 2,y + 92,0xFFFFFFFF);
    }
    @Override public void onClose() {
        if (observerReadOnly()) { closeOwnedScreen(); return; }
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
