package dev.totem.nexus.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.Optional;
import java.util.UUID;

/** Resolves a teleport interface exclusively from a server-owned ItemStack. */
public final class TeleportInterfaceItemResolver {
    private TeleportInterfaceItemResolver() { }

    public static Optional<ResolvedInterface> resolve(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return Optional.empty();
        Optional<NexusHeldMapResolver.ResolvedMap> heldMap = NexusHeldMapResolver.resolve(player, hand);
        if (heldMap.isPresent()) {
            return Optional.of(new ResolvedInterface(
                    TeleportInterfaceType.FILLED_MAP, heldMap.get().mapId(), null));
        }
        ItemStack stack = player.getItemInHand(hand);
        return stack.is(Items.FILLED_MAP) ? Optional.empty() : resolve(stack);
    }

    public static Optional<ResolvedInterface> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        UUID binding = NexusInterfaceBinding.read(stack);
        if (stack.is(Items.COMPASS)) return Optional.of(new ResolvedInterface(TeleportInterfaceType.COMPASS, null, binding));
        if (stack.is(Items.RECOVERY_COMPASS)) return Optional.of(new ResolvedInterface(TeleportInterfaceType.RECOVERY_COMPASS, null, binding));
        if (stack.is(Items.BOOK)) return Optional.of(new ResolvedInterface(TeleportInterfaceType.BOOK, null, binding));
        if (stack.is(Items.FILLED_MAP)) {
            MapId mapId = stack.get(DataComponents.MAP_ID);
            return mapId == null
                    ? Optional.empty()
                    : Optional.of(new ResolvedInterface(TeleportInterfaceType.FILLED_MAP, mapId, binding));
        }
        return Optional.empty();
    }

    public static Optional<RegistrationInput> resolveRegistrationInput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        if (stack.is(Items.MAP)) return Optional.of(new RegistrationInput(RegistrationInputType.EMPTY_MAP, null, null));
        Optional<ResolvedInterface> resolved = resolve(stack);
        if (resolved.isEmpty() || resolved.get().type() == TeleportInterfaceType.FILLED_MAP) {
            return Optional.empty();
        }
        return resolved.map(value -> new RegistrationInput(
                RegistrationInputType.fromInterface(value.type()), value.mapId(), value.boundUnitId()));
    }

    public static Optional<RegistrationInput> resolveRegistrationInput(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return Optional.empty();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.MAP)) return Optional.of(new RegistrationInput(RegistrationInputType.EMPTY_MAP, null, null));
        Optional<ResolvedInterface> resolved = resolve(player, hand);
        if (resolved.isEmpty() || resolved.get().type() == TeleportInterfaceType.FILLED_MAP) {
            return Optional.empty();
        }
        return resolved.map(value -> new RegistrationInput(
                RegistrationInputType.fromInterface(value.type()), value.mapId(), value.boundUnitId()));
    }

    public enum RegistrationInputType {
        COMPASS, RECOVERY_COMPASS, BOOK, EMPTY_MAP;

        static RegistrationInputType fromInterface(TeleportInterfaceType type) {
            return switch (type) {
                case COMPASS -> COMPASS;
                case RECOVERY_COMPASS -> RECOVERY_COMPASS;
                case BOOK -> BOOK;
                case FILLED_MAP -> throw new IllegalArgumentException("Filled maps are not lodestone binding inputs");
            };
        }
    }

    public record RegistrationInput(RegistrationInputType type, MapId mapId, UUID boundUnitId) { }

    public record ResolvedInterface(TeleportInterfaceType type, MapId mapId, UUID boundUnitId) {
        public ResolvedInterface(TeleportInterfaceType type, MapId mapId) { this(type, mapId, null); }
        public ResolvedInterface {
            if (type == null || (type == TeleportInterfaceType.FILLED_MAP) != (mapId != null)) {
                throw new IllegalArgumentException("Only a filled-map interface may carry a map ID");
            }
        }
    }
}
