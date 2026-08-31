package dev.totem.nexus.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creates vanilla map data without the coordinate snapping in createFresh. */
@Mixin(MapItemSavedData.class)
public interface NexusMapItemSavedDataInvoker {
    @Invoker("<init>")
    static MapItemSavedData deadrecall$createExact(
            int centerX,
            int centerZ,
            byte scale,
            boolean trackingPosition,
            boolean unlimitedTracking,
            boolean locked,
            ResourceKey<Level> dimension) {
        throw new AssertionError("Mixin constructor invoker was not applied");
    }
}
