package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapCraftingGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
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
        if (player instanceof ServerPlayer serverPlayer
                && !NexusMapCraftingGuard.allowResultTake((AbstractContainerMenu) (Object) this,
                slotId, serverPlayer, input == ContainerInput.QUICK_MOVE)) ci.cancel();
    }
}
