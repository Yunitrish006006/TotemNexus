package dev.totem.nexus.client;

import dev.totem.nexus.network.ConfirmSpaceUnitRegistrationPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Nexus-owned registration confirmation screen; opened only by the opt-in client bridge at cutover. */
final class NexusRegistrationPreviewScreen extends NexusOwnedScreen {
    private static final int WIDTH = 286, HEIGHT = 142;
    private SpaceUnitRegistrationPreviewPayload payload;
    NexusRegistrationPreviewScreen(SpaceUnitRegistrationPreviewPayload payload) { this(payload, false, () -> { }); }
    NexusRegistrationPreviewScreen(SpaceUnitRegistrationPreviewPayload payload, boolean observer, Runnable stop) {
        super(Component.translatable("message.deadrecall.space_unit.registration_gui_title"), observer, stop); this.payload = payload;
    }
    @Override protected void init() {
        int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.registration_gui_confirm"), ignored -> confirm()).bounds(x+WIDTH-132,y+HEIGHT-28,58,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose()).bounds(x+WIDTH-68,y+HEIGHT-28,56,18).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor extractor,int mouseX,int mouseY,float partialTick){extractor.fill(0,0,width,height,0xB0000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor extractor,int mouseX,int mouseY,float partialTick){int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
        extractor.fill(x,y,x+WIDTH,y+HEIGHT,0xF016191D); extractor.outline(x,y,WIDTH,HEIGHT,0xFF657383); extractor.text(font,title,x+12,y+10,0xFFFFFFFF);
        extractor.text(font,Component.translatable("message.deadrecall.space_unit.registration_gui_position",payload.dimension(),payload.x(),payload.y(),payload.z()),x+12,y+29,0xFFB8C0C8);
        extractor.text(font,Component.translatable("message.deadrecall.space_unit.registration_gui_structure",payload.tier(),payload.resonancePercent(),payload.completenessPercent(),payload.wearPercent()),x+12,y+47,0xFFE0E6EC);
        extractor.text(font,Component.translatable("message.deadrecall.space_unit.registration_gui_timeout",payload.confirmSeconds()),x+12,y+65,0xFFFFD166);
        int hintY=y+84;for(var line:font.split(Component.translatable("message.deadrecall.space_unit.registration_gui_hint"),WIDTH-24)){extractor.text(font,line,x+12,hintY,0xFF93A4B5);hintY+=10;} super.extractRenderState(extractor,mouseX,mouseY,partialTick);}
    private void confirm(){if(ClientPlayNetworking.canSend(ConfirmSpaceUnitRegistrationPayload.TYPE))ClientPlayNetworking.send(new ConfirmSpaceUnitRegistrationPayload(payload.dimension(),payload.x(),payload.y(),payload.z()));onClose();}
    SpaceUnitRegistrationPreviewPayload observerPayload(){return payload;}
    void apply(SpaceUnitRegistrationPreviewPayload next){payload=next;}
}
