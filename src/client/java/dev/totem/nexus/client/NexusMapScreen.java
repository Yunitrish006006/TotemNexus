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
final class NexusMapScreen extends Screen {
    static NexusMapScreen CURRENT; private SpaceUnitMapPayload payload; private UUID selected; private Button teleport,favorite;
    NexusMapScreen(SpaceUnitMapPayload payload){super(Component.translatable("message.deadrecall.space_unit.map_title"));this.payload=payload;}
    @Override protected void init(){CURRENT=this;int x=(width-450)/2,y=(height-270)/2;
        addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.map_refresh"),ignored->refresh()).bounds(x+12,y+238,62,18).build());
        favorite=addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.favorite_added"),ignored->favorite()).bounds(x+82,y+238,94,18).build());
        teleport=addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.teleport"),ignored->teleport()).bounds(x+292,y+238,70,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),ignored->onClose()).bounds(x+374,y+238,62,18).build());buttons();}
    @Override public void removed(){super.removed();if(CURRENT==this)CURRENT=null;}
    boolean isFor(String sourceType, UUID sourceId) { return payload != null && payload.sourceType().equals(sourceType) && payload.sourceUnitId().equals(sourceId); }
    void apply(SpaceUnitMapPayload next){payload=next;if(selected==null||entries().stream().noneMatch(e->e.id().equals(selected)))selected=entries().isEmpty()?null:entries().getFirst().id();buttons();}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float tick){g.fill(0,0,width,height,0xA0000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){buttons();int x=(width-450)/2,y=(height-270)/2;g.fill(x,y,x+450,y+270,0xF016191D);g.outline(x,y,450,270,0xFF657383);g.text(font,title,x+12,y+10,0xFFFFFFFF);
        int row=y+40;for(SpaceUnitMapPayload.Entry e:entries()){int color=e.id().equals(selected)?0xFF33495D:0xFF202830;g.fill(x+12,row,x+438,row+22,color);g.text(font,Component.literal(e.name()+" · "+e.type()+" · "+e.dimension()+" · "+(e.canTeleport()?"ready":e.blockedReason())),x+18,row+7,0xFFE0E6EC);row+=25;if(row>y+226)break;}super.extractRenderState(g,mx,my,tick);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){int y=(height-270)/2+40;for(SpaceUnitMapPayload.Entry e:entries()){if(event.y()>=y&&event.y()<y+22){selected=e.id();buttons();return true;}y+=25;}return super.mouseClicked(event,doubleClick);}
    private List<SpaceUnitMapPayload.Entry> entries(){return payload==null?List.of():payload.entries();}
    private SpaceUnitMapPayload.Entry entry(){return entries().stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);}
    private void buttons(){SpaceUnitMapPayload.Entry e=entry();if(teleport!=null)teleport.active=e!=null&&e.canTeleport();if(favorite!=null)favorite.active=e!=null;}
    private void refresh(){if(payload!=null&&ClientPlayNetworking.canSend(RequestSpaceUnitMapPayload.TYPE))ClientPlayNetworking.send(new RequestSpaceUnitMapPayload(payload.sourceType(),payload.sourceUnitId()));}
    private void favorite(){SpaceUnitMapPayload.Entry e=entry();if(e!=null&&ClientPlayNetworking.canSend(ToggleSpaceUnitFavoritePayload.TYPE))ClientPlayNetworking.send(new ToggleSpaceUnitFavoritePayload(payload.sourceType(),payload.sourceUnitId(),e.id(),!e.favorite()));}
    private void teleport(){SpaceUnitMapPayload.Entry e=entry();if(e!=null&&ClientPlayNetworking.canSend(StartSpaceUnitTeleportPayload.TYPE))ClientPlayNetworking.send(new StartSpaceUnitTeleportPayload(payload.sourceType(),payload.sourceUnitId(),e.id()));}
}
