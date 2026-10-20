package dev.totem.nexus.space;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One native result take. Nested shift-click iterations commit independently. */
public final class NexusMapCraftingTransaction {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private NexusMapCraftingTransaction() {}

    public static <T> T run(AbstractContainerMenu menu, ServerPlayer player, int slotId,
                            Supplier<T> operation, T denied) {
        Slot result = menu instanceof AbstractCraftingMenu crafting ? crafting.getResultSlot()
                : menu instanceof CartographyTableMenu ? menu.getSlot(CartographyTableMenu.RESULT_SLOT) : null;
        if (result == null || slotId < 0 || slotId >= menu.slots.size() || menu.getSlot(slotId) != result
                || !result.getItem().has(DataComponents.MAP_POST_PROCESSING)
                || NexusMapLifecycleAuthority.postProcessEligibility(result.getItem(), player.level(),
                    result.getItem().get(DataComponents.MAP_POST_PROCESSING))
                    == NexusMapLifecycleAuthority.ScaleEligibility.NOT_NEXUS) return operation.get();
        Scope previous = CURRENT.get();
        Scope scope = new Scope(menu, player, result);
        CURRENT.set(scope);
        try {
            return operation.get();
        } catch (Denied failure) {
            if (failure.scope != scope) throw failure;
            scope.restore();
            return denied;
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    /** Native quick-move invokes postprocessing again on its bookkeeping copy. */
    public static boolean reuseProcessed(ItemStack stack, Level level) {
        Scope scope = matchingScope(stack, level);
        if (scope == null || scope.processed == null) return false;
        stack.set(DataComponents.MAP_ID, scope.processed);
        stack.remove(DataComponents.MAP_POST_PROCESSING);
        return true;
    }

    public static void rememberProcessed(ItemStack stack, Level level,
                                          net.minecraft.world.level.saveddata.maps.MapId source) {
        Scope scope = CURRENT.get();
        if (scope != null && scope.player.level() == level
                && java.util.Objects.equals(scope.result.get(DataComponents.MAP_ID), source)
                && java.util.Objects.equals(NexusInterfaceBinding.read(scope.result), NexusInterfaceBinding.read(stack)))
            scope.processed = stack.get(DataComponents.MAP_ID);
    }

    private static Scope matchingScope(ItemStack stack, Level level) {
        Scope scope = CURRENT.get();
        return scope != null && scope.player.level() == level
                && java.util.Objects.equals(scope.result.get(DataComponents.MAP_ID), stack.get(DataComponents.MAP_ID))
                && java.util.Objects.equals(scope.result.get(DataComponents.MAP_POST_PROCESSING), stack.get(DataComponents.MAP_POST_PROCESSING))
                && java.util.Objects.equals(NexusInterfaceBinding.read(scope.result), NexusInterfaceBinding.read(stack)) ? scope : null;
    }

    /** Called only for an expected, pre-commit lifecycle refusal. Unrelated errors propagate. */
    public static boolean abortIfActive(ItemStack result, Level level) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.player.level() != level
                || !java.util.Objects.equals(scope.result.get(DataComponents.MAP_ID), result.get(DataComponents.MAP_ID))
                || !java.util.Objects.equals(NexusInterfaceBinding.read(scope.result), NexusInterfaceBinding.read(result))) return false;
        throw new Denied(scope);
    }

    private record Cell(Container container, int index, ItemStack value) {}
    private static final class Scope {
        final AbstractContainerMenu menu;
        final ServerPlayer player;
        final Slot output;
        final ItemStack result;
        final ItemStack carried;
        final int removeCount;
        final int craftedStat;
        net.minecraft.world.level.saveddata.maps.MapId processed;
        final List<Cell> cells = new ArrayList<>();
        Scope(AbstractContainerMenu menu, ServerPlayer player, Slot output) {
            this.menu = menu; this.player = player; this.output = output;
            result = output.getItem().copy(); carried = menu.getCarried().copy();
            removeCount = output instanceof dev.totem.nexus.mixin.NexusResultSlotAccessor accessor ? accessor.totem$removeCount() : 0;
            craftedStat = player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(net.minecraft.world.item.Items.FILLED_MAP));
            Map<Container, java.util.Set<Integer>> seen = new IdentityHashMap<>();
            for (Slot slot : menu.slots) save(seen, slot.container, slot.getContainerSlot());
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) save(seen, player.getInventory(), i);
        }
        void save(Map<Container, java.util.Set<Integer>> seen, Container container, int index) {
            if (seen.computeIfAbsent(container, ignored -> new java.util.HashSet<>()).add(index))
                cells.add(new Cell(container, index, container.getItem(index).copy()));
        }
        void restore() {
            // Input listeners may recompute previews while restoring. Restore output last.
            for (Cell cell : cells) if (cell.container != output.container || cell.index != output.getContainerSlot())
                cell.container.setItem(cell.index, cell.value.copy());
            output.set(result.copy());
            menu.setCarried(carried.copy());
            if (output instanceof dev.totem.nexus.mixin.NexusResultSlotAccessor accessor) accessor.totem$removeCount(removeCount);
            player.getStats().setValue(player, net.minecraft.stats.Stats.ITEM_CRAFTED.get(net.minecraft.world.item.Items.FILLED_MAP), craftedStat);
            menu.broadcastFullState();
        }
    }
    private static final class Denied extends RuntimeException {
        final Scope scope;
        Denied(Scope scope) { super(null, null, false, false); this.scope = scope; }
    }
}
