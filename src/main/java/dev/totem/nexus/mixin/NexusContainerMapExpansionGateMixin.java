package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapLifecycleAuthority;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapPostProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Last pre-consumption SCALE gate shared by crafting and cartography menus.
 * It runs before either result slot removes its map/paper inputs.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class NexusContainerMapExpansionGateMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void totem$denyUnavailableNexusExpansion(
            int slotId,
            int button,
            ContainerInput input,
            Player player,
            CallbackInfo ci) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        // Packet-valid indices include outside-click / quick-craft sentinels.
        // Only real slots can contain a map result; let vanilla handle sentinels.
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        Slot slot = menu.getSlot(slotId);
        ItemStack result = slot.getItem();
        MapPostProcessing processing = result.get(DataComponents.MAP_POST_PROCESSING);
        if (processing == null
                || NexusMapLifecycleAuthority.postProcessEligibility(result, serverLevel, processing)
                != NexusMapLifecycleAuthority.ScaleEligibility.DENIED) return;
        slot.set(ItemStack.EMPTY);
        menu.broadcastChanges();
        ci.cancel();
    }
}
