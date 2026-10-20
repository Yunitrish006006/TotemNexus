package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapTerrainFreeze;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MapItemSavedData.class)
public abstract class NexusMapTerrainFreezeMixin implements NexusMapTerrainFreeze.Access {
    @Unique private volatile Object totem$terrainFreeze;
    @Override public synchronized boolean totem$swapTerrainFreeze(Object expected, Object replacement) {
        if (totem$terrainFreeze != expected) return false;
        totem$terrainFreeze = replacement; return true;
    }
    @Override public boolean totem$hasTerrainFreeze(Object token) { return token != null && totem$terrainFreeze == token; }
    @Inject(method = "updateColor", at = @At("HEAD"), cancellable = true)
    private void totem$holdUpdate(int x, int z, byte color, CallbackInfoReturnable<Boolean> cir) {
        if (totem$terrainFreeze != null) cir.setReturnValue(false);
    }
    @Inject(method = "setColor", at = @At("HEAD"), cancellable = true)
    private void totem$holdSet(int x, int z, byte color, CallbackInfo ci) {
        if (totem$terrainFreeze != null) ci.cancel();
    }
}
