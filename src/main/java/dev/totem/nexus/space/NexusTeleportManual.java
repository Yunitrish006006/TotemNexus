package dev.totem.nexus.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

/** Creates the in-game guide obtained by using a plain book on a lodestone. */
public final class NexusTeleportManual {
    private static final String TITLE = "Nexus Teleport Manual";
    private static final String AUTHOR = "Totem Nexus";

    private static final List<String> PAGE_KEYS = List.of(
            "book.deadrecall.nexus_teleport_manual.page.1",
            "book.deadrecall.nexus_teleport_manual.page.2",
            "book.deadrecall.nexus_teleport_manual.page.3",
            "book.deadrecall.nexus_teleport_manual.page.4",
            "book.deadrecall.nexus_teleport_manual.page.5",
            "book.deadrecall.nexus_teleport_manual.page.6",
            "book.deadrecall.nexus_teleport_manual.page.7"
    );

    private NexusTeleportManual() {
    }

    public static boolean isManualRequest(ItemStack stack) {
        return stack != null && stack.is(Items.BOOK);
    }

    static List<String> pageKeys() {
        return PAGE_KEYS;
    }

    public static ItemStack create() {
        ItemStack manual = new ItemStack(Items.WRITTEN_BOOK);
        manual.set(DataComponents.CUSTOM_NAME, Component.translatable("item.deadrecall.nexus_teleport_manual"));
        manual.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(TITLE),
                AUTHOR,
                0,
                PAGE_KEYS.stream()
                        .map(key -> Filterable.<Component>passThrough(Component.translatable(key)))
                        .toList(),
                false
        ));
        return manual;
    }

    /** Replaces exactly one plain book in the requested hand with the guide. */
    public static boolean grant(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!isManualRequest(held)) {
            return false;
        }

        ItemStack manual = create();
        if (held.getCount() == 1) {
            player.setItemInHand(hand, manual);
        } else {
            held.shrink(1);
            if (!player.getInventory().add(manual)) {
                player.drop(manual, false);
            }
        }

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS,
                0.8F,
                1.0F
        );
        player.sendSystemMessage(Component.translatable("message.deadrecall.nexus_teleport_manual.received"));
        return true;
    }
}
