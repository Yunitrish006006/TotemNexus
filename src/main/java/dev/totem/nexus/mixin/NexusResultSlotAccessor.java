package dev.totem.nexus.mixin;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ResultSlot.class)
public interface NexusResultSlotAccessor {
    @Accessor("removeCount") int totem$removeCount();
    @Accessor("removeCount") void totem$removeCount(int value);
}
