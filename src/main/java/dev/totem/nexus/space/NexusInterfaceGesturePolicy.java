package dev.totem.nexus.space;

import net.minecraft.world.item.ItemStack;

/** Keeps plain-book manual refresh and crouching Nexus activation disjoint. */
final class NexusInterfaceGesturePolicy {
    private NexusInterfaceGesturePolicy() { }

    static boolean grantsManual(ItemStack stack, boolean crouching) {
        return !crouching && NexusTeleportManual.isManualRequest(stack);
    }

    static boolean routesNexus(
            TeleportInterfaceItemResolver.RegistrationInput input,
            boolean crouching) {
        return input != null
                && (input.type() != TeleportInterfaceItemResolver.RegistrationInputType.BOOK || crouching);
    }
}
