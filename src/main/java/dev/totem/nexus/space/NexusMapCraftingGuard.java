package dev.totem.nexus.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapPostProcessing;

import java.util.List;
import java.util.Optional;

/** Revalidates native result slots without modifying ingredients or allocating map records. */
public final class NexusMapCraftingGuard {
    private NexusMapCraftingGuard() { }

    public static boolean allowResultTake(AbstractContainerMenu menu, int slotId, ServerPlayer player,
                                          boolean quickMove) {
        if (slotId < 0 || slotId >= menu.slots.size()) return true;
        Slot resultSlot;
        List<ItemStack> inputs;
        if (menu instanceof AbstractCraftingMenu crafting) {
            resultSlot = crafting.getResultSlot();
            inputs = crafting.getInputGridSlots().stream().map(Slot::getItem).toList();
        } else if (menu instanceof CartographyTableMenu cartography) {
            resultSlot = menu.getSlot(CartographyTableMenu.RESULT_SLOT);
            inputs = List.of(cartography.container.getItem(CartographyTableMenu.MAP_SLOT),
                    cartography.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT));
        } else return true;
        if (menu.getSlot(slotId) != resultSlot || !resultSlot.hasItem()) return true;

        ItemStack result = resultSlot.getItem();
        MapPostProcessing requested = result.get(DataComponents.MAP_POST_PROCESSING);
        Optional<ItemStack> scaleSource = source(inputs, menu, MapPostProcessing.SCALE);
        Optional<ItemStack> lockSource = source(inputs, menu, MapPostProcessing.LOCK);
        MapPostProcessing actual = scaleSource.isPresent() ? MapPostProcessing.SCALE
                : lockSource.isPresent() ? MapPostProcessing.LOCK : null;
        if (actual == null && requested == null) return true; // Native cloning and unrelated recipes.
        MapPostProcessing operation = actual != null ? actual : requested;
        var level = player.level();
        boolean nexus = NexusMapLifecycleAuthority.postProcessEligibility(result, level, operation)
                != NexusMapLifecycleAuthority.ScaleEligibility.NOT_NEXUS
                || inputs.stream().anyMatch(input -> NexusMapLifecycleAuthority.postProcessEligibility(input, level, operation)
                != NexusMapLifecycleAuthority.ScaleEligibility.NOT_NEXUS);
        if (!nexus) return true;

        Optional<ItemStack> source = operation == MapPostProcessing.SCALE ? scaleSource : lockSource;
        boolean valid = actual == requested && source.isPresent()
                && NexusMapRecipeInputs.matchesResult(source.get(), result, operation)
                && NexusMapLifecycleAuthority.postProcessEligibility(source.get(), level, operation)
                == NexusMapLifecycleAuthority.ScaleEligibility.ALLOWED
                && NexusMapLifecycleAuthority.postProcessEligibility(result, level, operation)
                == NexusMapLifecycleAuthority.ScaleEligibility.ALLOWED;
        if (!valid) {
            resultSlot.set(ItemStack.EMPTY);
            menu.broadcastChanges();
            return false;
        }
        // SCALE/LOCK produce a fresh MapId and cannot merge into an existing stack.
        // Vanilla post-processes before checking move capacity; avoid orphan IDs on no-op.
        return !quickMove || player.getInventory().getFreeSlot() >= 0;
    }

    private static Optional<ItemStack> source(List<ItemStack> inputs, AbstractContainerMenu menu,
                                              MapPostProcessing processing) {
        return menu instanceof CartographyTableMenu
                ? NexusMapRecipeInputs.cartographySource(inputs.get(0), inputs.get(1), processing)
                : NexusMapRecipeInputs.workbenchSource(inputs, processing);
    }
}
