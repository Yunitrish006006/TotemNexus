package dev.totem.nexus.space;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.UUID;

/** Preserves the legacy compass-to-Space-Unit binding stored on the server item. */
public final class NexusCompassBinding {
    private NexusCompassBinding() { }

    public static UUID read(ItemStack stack) {
        return stack != null && stack.is(Items.COMPASS) ? NexusInterfaceBinding.read(stack) : null;
    }

    public static boolean write(ItemStack stack, ServerLevel level, net.minecraft.core.BlockPos pos, UUID unitId) {
        return stack != null && stack.is(Items.COMPASS)
                && NexusInterfaceBinding.write(stack, level, pos, unitId);
    }
}
