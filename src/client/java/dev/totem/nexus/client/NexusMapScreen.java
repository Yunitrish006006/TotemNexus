package dev.totem.nexus.client;

import dev.totem.nexus.network.RequestSpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.StartSpaceUnitTeleportPayload;
import dev.totem.nexus.network.ToggleSpaceUnitFavoritePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Nexus map UI consuming the authoritative space_unit_map payload. */
final class NexusMapScreen extends NexusOwnedScreen {
    private static final int PREFERRED_WIDTH = 450;
    private static final int PREFERRED_HEIGHT = 270;
    private static final int MARGIN = 6;
    static NexusMapScreen CURRENT; private SpaceUnitMapPayload payload; private UUID selected; private Button teleport,favorite;
    NexusMapScreen(SpaceUnitMapPayload payload){this(payload, false, () -> { });}
    NexusMapScreen(SpaceUnitMapPayload payload, boolean observer, Runnable stop){super(Component.translatable("message.deadrecall.space_unit.map_title"), observer, stop);this.payload=payload;}
    @Override protected void init(){CURRENT=this;int panelWidth=panelWidth(),panelHeight=panelHeight(),x=(width-panelWidth)/2,y=(height-panelHeight)/2,buttonY=y+panelHeight-32;
        addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.map_refresh"),ignored->refresh()).bounds(x+12,buttonY,62,18).build());
        favorite=addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.map_favorite_add"),ignored->favorite()).bounds(x+82,buttonY,94,18).build());
        teleport=addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.teleport_start"),ignored->teleport()).bounds(x+panelWidth-158,buttonY,70,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),ignored->onClose()).bounds(x+panelWidth-76,buttonY,62,18).build());buttons();}
    @Override public void removed(){super.removed();if(CURRENT==this)CURRENT=null;}
    boolean isFor(String sourceType, UUID sourceId) { return payload != null && payload.sourceType().equals(sourceType) && payload.sourceUnitId().equals(sourceId); }
    void apply(SpaceUnitMapPayload next){payload=next;if(selected==null||entries().stream().noneMatch(e->e.id().equals(selected)))selected=entries().isEmpty()?null:entries().getFirst().id();buttons();}
    SpaceUnitMapPayload observerPayload(){return payload;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float tick){g.fill(0,0,width,height,0xA0000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){buttons();int panelWidth=panelWidth(),panelHeight=panelHeight(),x=(width-panelWidth)/2,y=(height-panelHeight)/2;g.fill(x,y,x+panelWidth,y+panelHeight,0xF016191D);g.outline(x,y,panelWidth,panelHeight,0xFF657383);g.text(font,title,x+12,y+10,0xFFFFFFFF);
        int row=y+40,rowLimit=y+panelHeight-44;if(entries().isEmpty())g.text(font,Component.translatable("message.deadrecall.space_unit.map_dimension_empty"),x+18,row+7,0xFFFFD166);for(SpaceUnitMapPayload.Entry e:entries()){int color=e.id().equals(selected)?0xFF33495D:0xFF202830;g.fill(x+12,row,x+panelWidth-12,row+22,color);String label=e.name()+" · "+e.type()+" · "+e.dimension()+" · "+(e.canTeleport()?"ready":e.blockedReason());g.text(font,Component.literal(trim(label,panelWidth-36)),x+18,row+7,0xFFE0E6EC);row+=25;if(row>rowLimit)break;}super.extractRenderState(g,mx,my,tick);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(observerReadOnly())return true;int panelHeight=panelHeight(),y=(height-panelHeight)/2+40,rowLimit=(height-panelHeight)/2+panelHeight-44;for(SpaceUnitMapPayload.Entry e:entries()){if(y>rowLimit)break;if(event.y()>=y&&event.y()<y+22){selected=e.id();buttons();return true;}y+=25;}return super.mouseClicked(event,doubleClick);}
    private List<SpaceUnitMapPayload.Entry> entries(){return payload==null?List.of():payload.entries();}
    private SpaceUnitMapPayload.Entry entry(){return entries().stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);}
    private void buttons(){SpaceUnitMapPayload.Entry e=entry();if(teleport!=null)teleport.active=e!=null&&e.canTeleport();if(favorite!=null){favorite.active=e!=null;favorite.setMessage(Component.translatable(e!=null&&e.favorite()?"message.deadrecall.space_unit.map_favorite_remove":"message.deadrecall.space_unit.map_favorite_add"));}}
    private void refresh(){if(payload!=null&&ClientPlayNetworking.canSend(RequestSpaceUnitMapPayload.TYPE))ClientPlayNetworking.send(new RequestSpaceUnitMapPayload(payload.sourceType(),payload.sourceUnitId()));}
    private void favorite(){SpaceUnitMapPayload.Entry e=entry();if(e!=null&&ClientPlayNetworking.canSend(ToggleSpaceUnitFavoritePayload.TYPE))ClientPlayNetworking.send(new ToggleSpaceUnitFavoritePayload(payload.sourceType(),payload.sourceUnitId(),e.id(),!e.favorite()));}
    private void teleport(){SpaceUnitMapPayload.Entry e=entry();if(e!=null&&ClientPlayNetworking.canSend(StartSpaceUnitTeleportPayload.TYPE))ClientPlayNetworking.send(new StartSpaceUnitTeleportPayload(payload.sourceType(),payload.sourceUnitId(),e.id()));}
    private int panelWidth(){return Math.min(PREFERRED_WIDTH,Math.max(1,width-MARGIN*2));}
    private int panelHeight(){return Math.min(PREFERRED_HEIGHT,Math.max(1,height-MARGIN*2));}
    private String trim(String value,int maxWidth){if(font.width(value)<=maxWidth)return value;String suffix="...";int available=Math.max(0,maxWidth-font.width(suffix));return font.plainSubstrByWidth(value,available)+suffix;}
}
