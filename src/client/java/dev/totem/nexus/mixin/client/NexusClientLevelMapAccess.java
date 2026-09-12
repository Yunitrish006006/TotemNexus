package dev.totem.nexus.mixin.client;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
@Mixin(ClientLevel.class)
public interface NexusClientLevelMapAccess {
    @Accessor("mapData") Map<MapId,MapItemSavedData> totem$mapData();
}
