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

/** Nexus-owned friend-list UI. It is inactive until client payload registration is enabled at cutover. */
final class NexusFriendsScreen extends NexusOwnedScreen {
    static NexusFriendsScreen CURRENT;
    private SpaceUnitFriendsPayload payload; private UUID selected; private Button remove;
    NexusFriendsScreen(SpaceUnitFriendsPayload payload) { this(payload, false, () -> { }); }
    NexusFriendsScreen(SpaceUnitFriendsPayload payload, boolean observer, Runnable stop) { super(Component.translatable("message.deadrecall.space_unit.friends_title"), observer, stop); this.payload=payload; }
    @Override protected void init(){CURRENT=this; int x=(width-330)/2,y=(height-238)/2;
        addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.friends_refresh"), ignored->refresh()).bounds(x+12,y+208,62,18).build());
        remove=addRenderableWidget(Button.builder(Component.translatable("message.deadrecall.space_unit.friends_remove"), ignored->remove()).bounds(x+82,y+208,62,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored->onClose()).bounds(x+256,y+208,62,18).build()); buttons(); if(payload==null && !observerReadOnly())refresh(); }
    @Override public void removed(){super.removed();if(CURRENT==this)CURRENT=null;}
    void apply(SpaceUnitFriendsPayload next){payload=next;if(selected==null||entries().stream().noneMatch(e->e.id().equals(selected)))selected=entries().isEmpty()?null:entries().getFirst().id();buttons();}
    SpaceUnitFriendsPayload observerPayload(){return payload;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float tick){g.fill(0,0,width,height,0xA0000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){buttons();int x=(width-330)/2,y=(height-238)/2;g.fill(x,y,x+330,y+238,0xF016191D);g.outline(x,y,330,238,0xFF657383);g.text(font,title,x+12,y+10,0xFFFFFFFF);
        int row=y+42;for(SpaceUnitFriendsPayload.Entry e:entries()){int color=e.id().equals(selected)?0xFF33495D:0xFF202830;g.fill(x+12,row,x+318,row+24,color);g.text(font,Component.literal(e.name()+"  ["+e.status()+"]"+(e.online()?" ●":"")),x+18,row+8,0xFFE0E6EC);row+=28;if(row>y+198)break;}super.extractRenderState(g,mx,my,tick);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(observerReadOnly())return true;int y=(height-238)/2+42;for(SpaceUnitFriendsPayload.Entry e:entries()){if(event.y()>=y&&event.y()<y+24){selected=e.id();buttons();return true;}y+=28;}return super.mouseClicked(event,doubleClick);}
    private List<SpaceUnitFriendsPayload.Entry> entries(){return payload==null?List.of():payload.entries();}
    private void buttons(){if(remove!=null)remove.active=selected!=null&&entries().stream().anyMatch(e->e.id().equals(selected)&&e.status().equals("friend"));}
    private void refresh(){if(ClientPlayNetworking.canSend(RequestSpaceUnitFriendsPayload.TYPE))ClientPlayNetworking.send(new RequestSpaceUnitFriendsPayload());}
    private void remove(){if(selected!=null&&ClientPlayNetworking.canSend(RemoveSpaceUnitFriendPayload.TYPE))ClientPlayNetworking.send(new RemoveSpaceUnitFriendPayload(selected));}
}
