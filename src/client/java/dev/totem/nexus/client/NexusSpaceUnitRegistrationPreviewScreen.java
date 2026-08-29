package dev.totem.nexus.client;

import dev.totem.nexus.network.ConfirmSpaceUnitRegistrationPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class NexusSpaceUnitRegistrationPreviewScreen extends NexusOwnedScreen {
    private static final int DIALOG_WIDTH = 286;
    private static final int DIALOG_HEIGHT = 142;

    private SpaceUnitRegistrationPreviewPayload payload;

    NexusSpaceUnitRegistrationPreviewScreen(SpaceUnitRegistrationPreviewPayload payload) {
        this(payload, false, () -> { });
    }

    NexusSpaceUnitRegistrationPreviewScreen(SpaceUnitRegistrationPreviewPayload payload, boolean observer, Runnable stop) {
        super(Component.translatable("message.deadrecall.space_unit.registration_gui_title"), observer, stop);
        this.payload = payload;
    }

    @Override
    protected void init() {
        int x = (this.width - DIALOG_WIDTH) / 2;
        int y = (this.height - DIALOG_HEIGHT) / 2;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("message.deadrecall.space_unit.registration_gui_confirm"),
                        button -> confirm())
                .bounds(x + DIALOG_WIDTH - 132, y + DIALOG_HEIGHT - 28, 58, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        button -> this.onClose())
                .bounds(x + DIALOG_WIDTH - 68, y + DIALOG_HEIGHT - 28, 56, 18)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = (this.width - DIALOG_WIDTH) / 2;
        int y = (this.height - DIALOG_HEIGHT) / 2;
        extractor.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xF016191D);
        extractor.outline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF657383);
        extractor.text(this.font, this.title, x + 12, y + 10, 0xFFFFFFFF);
        extractor.text(this.font, Component.translatable(
                "message.deadrecall.space_unit.registration_gui_position",
                this.payload.dimension(),
                this.payload.x(),
                this.payload.y(),
                this.payload.z()), x + 12, y + 29, 0xFFB8C0C8);
        extractor.text(this.font, Component.translatable(
                "message.deadrecall.space_unit.registration_gui_structure",
                this.payload.tier(),
                this.payload.resonancePercent(),
                this.payload.completenessPercent(),
                this.payload.wearPercent()), x + 12, y + 47, 0xFFE0E6EC);
        extractor.text(this.font, Component.translatable(
                "message.deadrecall.space_unit.registration_gui_timeout",
                this.payload.confirmSeconds()), x + 12, y + 65, 0xFFFFD166);
        int hintY = y + 84;
        for (var line : this.font.split(Component.translatable(
                "message.deadrecall.space_unit.registration_gui_hint"), DIALOG_WIDTH - 24)) {
            extractor.text(this.font, line, x + 12, hintY, 0xFF93A4B5);
            hintY += 10;
        }
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void confirm() {
        if (ClientPlayNetworking.canSend(ConfirmSpaceUnitRegistrationPayload.TYPE)) {
            ClientPlayNetworking.send(new ConfirmSpaceUnitRegistrationPayload(
                    this.payload.dimension(),
                    this.payload.x(),
                    this.payload.y(),
                    this.payload.z()
            ));
        }
        onClose();
    }

    SpaceUnitRegistrationPreviewPayload observerPayload() { return payload; }
    void applyPayload(SpaceUnitRegistrationPreviewPayload payload) { this.payload = payload; }
}
