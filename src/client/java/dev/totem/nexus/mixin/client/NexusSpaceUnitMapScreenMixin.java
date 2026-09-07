package dev.totem.nexus.mixin.client;

import dev.totem.nexus.client.NexusSpaceUnitMapScreen;
import dev.totem.nexus.network.RefreshSpaceUnitQuotePayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.StartSpaceUnitTeleportPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

@Mixin(NexusSpaceUnitMapScreen.class)
public abstract class NexusSpaceUnitMapScreenMixin {
    @Shadow
    private SpaceUnitMapPayload payload;

    @Shadow
    private UUID selectedUnitId;

    @Shadow
    private Button teleportButton;

    @Shadow
    private SpaceUnitMapPayload.Entry selectedEntry() {
        throw new AssertionError();
    }

    @Shadow
    private int panelX() {
        throw new AssertionError();
    }

    @Shadow
    private int panelY() {
        throw new AssertionError();
    }

    @Shadow
    private int panelHeight() {
        throw new AssertionError();
    }

    @Shadow
    private int firstFooterButtonX() {
        throw new AssertionError();
    }

    @Shadow
    private String trimToWidth(String value, int width) {
        throw new AssertionError();
    }

    @Unique
    private UUID totem$selectionBeforeClick;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void totem$captureSelection(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        this.totem$selectionBeforeClick = this.selectedUnitId;
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void totem$refreshSelectedQuote(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue() || Objects.equals(this.totem$selectionBeforeClick, this.selectedUnitId)) {
            return;
        }
        totem$requestSelectedQuoteRefresh();
    }

    @Inject(method = "requestRefresh", at = @At("HEAD"), cancellable = true)
    private void totem$refreshCurrentSelection(CallbackInfo ci) {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || selected.id().equals(this.payload.sourceUnitId())) {
            return;
        }
        if (totem$sendQuoteRefresh(selected.id())) {
            ci.cancel();
        }
    }

    @Inject(method = "updateButtonLayout", at = @At("TAIL"))
    private void totem$allowAuthoritativeTeleportCheck(CallbackInfo ci) {
        if (this.teleportButton == null) {
            return;
        }
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        this.teleportButton.active = selected != null && !selected.id().equals(this.payload.sourceUnitId());
    }

    @Inject(method = "drawFooter", at = @At("TAIL"))
    private void totem$drawCatalystQuote(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || selected.baseAmethystCost() <= 0) {
            return;
        }

        int x = panelX() + 12;
        int y = panelY() + panelHeight() - 46 + 27;
        int width = Math.max(36, firstFooterButtonX() - x - 8);
        String text = Component.translatable(
                "message.totem.space_unit.metric.amethyst_breakdown",
                selected.baseAmethystCost(),
                selected.sourceCatalysts(),
                selected.targetCatalysts(),
                selected.catalystDiscount(),
                selected.amethystCost()
        ).getString();
        extractor.text(Minecraft.getInstance().font, trimToWidth(text, width), x, y, 0xFFB9A3E3);
    }

    @Inject(method = "requestTeleport", at = @At("HEAD"), cancellable = true)
    private void totem$startWithFreshStructureCheck(CallbackInfo ci) {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected == null || selected.id().equals(this.payload.sourceUnitId())) {
            ci.cancel();
            return;
        }

        if (ClientPlayNetworking.canSend(StartSpaceUnitTeleportPayload.TYPE)) {
            ClientPlayNetworking.send(new StartSpaceUnitTeleportPayload(
                    this.payload.sourceType(),
                    this.payload.sourceUnitId(),
                    selected.id()
            ));
            ((NexusSpaceUnitMapScreen) (Object) this).onClose();
        }
        ci.cancel();
    }

    @Unique
    private void totem$requestSelectedQuoteRefresh() {
        SpaceUnitMapPayload.Entry selected = selectedEntry();
        if (selected != null && !selected.id().equals(this.payload.sourceUnitId())) {
            totem$sendQuoteRefresh(selected.id());
        }
    }

    @Unique
    private boolean totem$sendQuoteRefresh(UUID targetUnitId) {
        if (!ClientPlayNetworking.canSend(RefreshSpaceUnitQuotePayload.TYPE)) {
            return false;
        }
        ClientPlayNetworking.send(new RefreshSpaceUnitQuotePayload(
                this.payload.sourceType(),
                this.payload.sourceUnitId(),
                targetUnitId
        ));
        return true;
    }
}
