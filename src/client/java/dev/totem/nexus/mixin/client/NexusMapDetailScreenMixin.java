package dev.totem.nexus.mixin.client;

import dev.totem.nexus.client.NexusMapDetailClientState;
import dev.totem.nexus.client.NexusSpaceUnitMapScreen;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Keeps vanilla map terrain while turning Nexus zoom into historical-detail
 * zoom. Terrain is submitted without decorations; all overlays are then drawn
 * exactly once in the current-map world transform.
 */
@Mixin(NexusSpaceUnitMapScreen.class)
public abstract class NexusMapDetailScreenMixin {
    @Shadow
    private SpaceUnitMapPayload payload;

    @Shadow
    private int mapZoom;

    @Shadow
    private int mapPanX;

    @Shadow
    private int mapPanY;

    @Shadow
    private MapRenderState mapRenderState;

    @Shadow
    private boolean hasMapVisualization() {
        throw new AssertionError();
    }

    @Shadow
    private MapItemSavedData cachedMapData() {
        throw new AssertionError();
    }

    @Shadow
    private int mapX() {
        throw new AssertionError();
    }

    @Shadow
    private int mapY() {
        throw new AssertionError();
    }

    @Shadow
    private int mapWidth() {
        throw new AssertionError();
    }

    @Shadow
    private int mapHeight() {
        throw new AssertionError();
    }

    @Shadow
    private int baseMapScale() {
        throw new AssertionError();
    }

    @Shadow
    private void clampMapPan() {
        throw new AssertionError();
    }

    @Unique
    private List<MapRenderState.MapDecorationRenderState> totem$currentDecorations = List.of();

    @Inject(method = "init", at = @At("TAIL"))
    private void totem$requestDetailOnOpen(CallbackInfo ci) {
        totem$requestDetailIfOwner();
    }

    @Inject(method = "applyPayload", at = @At("TAIL"))
    private void totem$requestDetailAfterPayload(SpaceUnitMapPayload nextPayload, CallbackInfo ci) {
        if (!totem$isObserver()) {
            this.mapZoom = Math.min(totem$normalizeZoom(this.mapZoom), totem$maximumAvailableDetailZoom());
            clampMapPan();
        }
        totem$requestDetailIfOwner();
    }

    /** Existing callers pass current+1/current-1; convert those steps to 1,2,4,8,16. */
    @Inject(method = "setMapZoom", at = @At("HEAD"), cancellable = true)
    private void totem$setDetailZoom(int requestedZoom, CallbackInfo ci) {
        int current = totem$normalizeZoom(this.mapZoom);
        int maximum = totem$maximumAvailableDetailZoom();
        int next = current;
        if (requestedZoom > this.mapZoom) {
            next = Math.min(maximum, current >= 16 ? 16 : current << 1);
        } else if (requestedZoom < this.mapZoom) {
            next = Math.max(1, current >> 1);
        } else {
            next = Math.min(current, maximum);
        }
        this.mapZoom = next;
        clampMapPan();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getNarrator().saySystemNow(Component.translatable(
                    "message.totem.space_unit.map_zoom_narration", this.mapZoom * 100));
        }
        ci.cancel();
    }

    /** Observer semantic zoom is already validated by the provider; do not use observer-local detail to clamp it. */
    @Inject(method = "applyObserverMapView", at = @At("HEAD"), cancellable = true)
    private void totem$applyObserverDetailView(int zoom, int panX, int panY, CallbackInfo ci) {
        if (!totem$isObserver() || !hasMapVisualization()) return;
        this.mapZoom = totem$normalizeZoom(zoom);
        this.mapPanX = panX;
        this.mapPanY = panY;
        clampMapPan();
        ci.cancel();
    }

    /** Capture the already-authoritative transient decorations, then submit the current map as terrain only. */
    @Inject(
            method = "drawMap",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;map(Lnet/minecraft/client/renderer/state/MapRenderState;)V",
                    ordinal = 0
            )
    )
    private void totem$separateCurrentTerrainAndOverlays(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        this.totem$currentDecorations = List.copyOf(this.mapRenderState.decorations);
        this.mapRenderState.decorations.clear();
    }

    /** Fine historical maps cover only their true world extent; overlays are restored above every terrain layer. */
    @Inject(
            method = "drawMap",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;disableScissor()V",
                    shift = At.Shift.AFTER
            )
    )
    private void totem$drawDetailAndOverlays(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        MapItemSavedData current = cachedMapData();
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || minecraft == null || minecraft.level == null) {
            this.totem$currentDecorations = List.of();
            return;
        }

        int currentPixelScale = baseMapScale() * totem$normalizeZoom(this.mapZoom);
        int currentRenderedSize = 128 * currentPixelScale;
        int currentLeft = mapX() + (mapWidth() - currentRenderedSize) / 2 + this.mapPanX;
        int currentTop = mapY() + (mapHeight() - currentRenderedSize) / 2 + this.mapPanY;
        float centerX = currentLeft + currentRenderedSize / 2.0F;
        float centerY = currentTop + currentRenderedSize / 2.0F;

        extractor.enableScissor(mapX() + 1, mapY() + 1, mapX() + mapWidth() - 1, mapY() + mapHeight() - 1);
        List<Integer> ancestors = NexusMapDetailClientState.ancestorMapIds(this.payload.mapId());
        int detailStateIndex = 0;
        for (int index = ancestors.size() - 1; index >= 0; index--) {
            MapId ancestorId = new MapId(ancestors.get(index));
            MapItemSavedData ancestor = minecraft.level.getMapData(ancestorId);
            if (!totem$isCompatibleAncestor(current, ancestor)) continue;
            int scaleDelta = current.scale - ancestor.scale;
            int requiredZoom = 1 << scaleDelta;
            if (requiredZoom > this.mapZoom || currentPixelScale % requiredZoom != 0) continue;

            int ancestorPixelScale = currentPixelScale / requiredZoom;
            int ancestorRenderedSize = 128 * ancestorPixelScale;
            int ancestorLeft = Math.round(centerX - ancestorRenderedSize / 2.0F);
            int ancestorTop = Math.round(centerY - ancestorRenderedSize / 2.0F);
            MapRenderState detailState = new MapRenderState();
            minecraft.getMapRenderer().extractRenderState(ancestorId, ancestor, detailState);
            detailState.decorations.clear();

            extractor.nextStratum();
            extractor.pose().pushMatrix();
            extractor.pose().translate(ancestorLeft, ancestorTop);
            extractor.pose().scale(ancestorPixelScale, ancestorPixelScale);
            extractor.map(detailState);
            extractor.pose().popMatrix();
            detailStateIndex++;
        }

        extractor.nextStratum();
        int overlayScale = Math.max(1, baseMapScale());
        for (MapRenderState.MapDecorationRenderState decoration : this.totem$currentDecorations) {
            totem$drawDecoration(extractor, decoration, currentLeft, currentTop, currentPixelScale, overlayScale);
        }
        totem$drawLocalPlayer(extractor, current, currentLeft, currentTop, currentPixelScale, overlayScale);
        extractor.disableScissor();
        this.totem$currentDecorations = List.of();
    }

    @Unique
    private void totem$requestDetailIfOwner() {
        if (hasMapVisualization() && this.payload.mapId() >= 0 && !totem$isObserver()) {
            NexusMapDetailClientState.request(this.payload.mapId());
        }
    }

    @Unique
    private boolean totem$isObserver() {
        return ((NexusSpaceUnitMapScreen) (Object) this).totem$isObserverReadOnly();
    }

    @Unique
    private int totem$maximumAvailableDetailZoom() {
        MapItemSavedData current = cachedMapData();
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || minecraft == null || minecraft.level == null || this.payload.mapId() < 0) return 1;
        int maximum = 1;
        for (int ancestorValue : NexusMapDetailClientState.ancestorMapIds(this.payload.mapId())) {
            MapItemSavedData ancestor = minecraft.level.getMapData(new MapId(ancestorValue));
            if (!totem$isCompatibleAncestor(current, ancestor)) continue;
            int delta = current.scale - ancestor.scale;
            if (delta > 0 && delta <= MapItemSavedData.MAX_SCALE) maximum = Math.max(maximum, 1 << delta);
        }
        return Math.min(16, maximum);
    }

    @Unique
    private static int totem$normalizeZoom(int zoom) {
        if (zoom <= 1) return 1;
        if (zoom >= 16) return 16;
        int highest = Integer.highestOneBit(zoom);
        return highest == zoom ? zoom : highest;
    }

    @Unique
    private static boolean totem$isCompatibleAncestor(MapItemSavedData current, MapItemSavedData ancestor) {
        return ancestor != null
                && ancestor.scale >= 0
                && ancestor.scale < current.scale
                && ancestor.centerX == current.centerX
                && ancestor.centerZ == current.centerZ
                && ancestor.dimension.equals(current.dimension);
    }

    @Unique
    private static void totem$drawDecoration(
            GuiGraphicsExtractor extractor,
            MapRenderState.MapDecorationRenderState decoration,
            int currentLeft,
            int currentTop,
            int currentPixelScale,
            int iconScale
    ) {
        if (decoration == null || decoration.atlasSprite == null) return;
        float screenX = currentLeft + (64.0F + decoration.x / 2.0F) * currentPixelScale;
        float screenY = currentTop + (64.0F + decoration.y / 2.0F) * currentPixelScale;
        extractor.pose().pushMatrix();
        extractor.pose().translate(screenX, screenY);
        extractor.pose().rotate((decoration.rot & 15) * ((float) Math.PI * 2.0F / 16.0F));
        extractor.pose().scale(iconScale, iconScale);
        extractor.blitSprite(RenderPipelines.GUI_TEXTURED, decoration.atlasSprite, -2, -2, 4, 4);
        extractor.pose().popMatrix();
    }

    @Unique
    private void totem$drawLocalPlayer(
            GuiGraphicsExtractor extractor,
            MapItemSavedData current,
            int currentLeft,
            int currentTop,
            int currentPixelScale,
            int iconScale
    ) {
        if (totem$isObserver()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().equals(current.dimension)) return;

        double blocksPerPixel = 1 << current.scale;
        double mapX = (minecraft.player.getX() - current.centerX) / blocksPerPixel;
        double mapY = (minecraft.player.getZ() - current.centerZ) / blocksPerPixel;
        if (mapX < -64.0D || mapX >= 64.0D || mapY < -64.0D || mapY >= 64.0D) return;

        byte x = (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, Math.round(mapX * 2.0D)));
        byte y = (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, Math.round(mapY * 2.0D)));
        byte rotation = (byte) (((int) Math.floor(minecraft.player.getYRot() * 16.0D / 360.0D + 0.5D)) & 15);
        MapDecoration decoration = new MapDecoration(
                MapDecorationTypes.PLAYER,
                x,
                y,
                rotation,
                Optional.empty()
        );
        MapRenderState.MapDecorationRenderState state =
                ((NexusMapRendererInvoker) (Object) minecraft.getMapRenderer())
                        .totem$extractDecorationRenderState(decoration);
        totem$drawDecoration(extractor, state, currentLeft, currentTop, currentPixelScale, iconScale);
    }
}
