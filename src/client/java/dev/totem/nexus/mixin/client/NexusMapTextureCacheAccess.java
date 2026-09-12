package dev.totem.nexus.mixin.client;
import net.minecraft.client.resources.MapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
@Mixin(MapTextureManager.class)
public interface NexusMapTextureCacheAccess {
    @Accessor("maps") Int2ObjectMap<?> totem$mapTextures();
}
