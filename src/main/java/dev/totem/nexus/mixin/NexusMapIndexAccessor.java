package dev.totem.nexus.mixin;

import net.minecraft.world.level.saveddata.maps.MapIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MapIndex.class)
public interface NexusMapIndexAccessor {
    @Accessor("lastMapId") int totem$lastMapId();
}
