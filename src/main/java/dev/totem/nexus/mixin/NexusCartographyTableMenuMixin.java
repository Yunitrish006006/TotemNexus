package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapLifecycleAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refuses unavailable Nexus map post-processing before inputs are consumed. */
@Mixin(CartographyTableMenu.class)
public abstract class NexusCartographyTableMenuMixin {
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final private ResultContainer resultContainer;
    @Inject(method = "setupResultSlot", at = @At("RETURN"))
    private void totem$gateNexusExpansion(
            ItemStack map,
            ItemStack addition,
            ItemStack previousResult,
            CallbackInfo ci) {
        MapPostProcessing processing = addition.is(Items.PAPER)
                ? MapPostProcessing.SCALE
                : addition.is(Items.GLASS_PANE) ? MapPostProcessing.LOCK : null;
        if (processing == null) return;
        this.access.execute((level, pos) -> {
            if (!(level instanceof ServerLevel serverLevel)) return;
            var eligibility = NexusMapLifecycleAuthority.postProcessEligibility(map, serverLevel, processing);
            if (eligibility == NexusMapLifecycleAuthority.ScaleEligibility.DENIED) {
                this.resultContainer.removeItemNoUpdate(CartographyTableMenu.RESULT_SLOT);
            } else if (eligibility == NexusMapLifecycleAuthority.ScaleEligibility.ALLOWED
                    && processing == MapPostProcessing.SCALE) {
                var result = map.copyWithCount(1);
                result.set(net.minecraft.core.component.DataComponents.MAP_POST_PROCESSING, processing);
                this.resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, result);
            }
        });
    }
}
