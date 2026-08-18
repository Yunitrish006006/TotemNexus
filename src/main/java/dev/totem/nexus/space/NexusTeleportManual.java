package dev.totem.nexus.space;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import dev.totem.core.api.v1.manual.TotemManualLifecycle;
import dev.totem.core.api.v1.manual.TotemManualPlayerHelper;
import dev.totem.core.api.v1.manual.TotemManualRegistry;
import dev.totem.core.api.v1.manual.TotemManualSection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/** Nexus section and legacy migration bridge for the shared Totem manual. */
public final class NexusTeleportManual {
    private static final String TITLE = "Nexus Teleport Manual";
    private static final String AUTHOR = "Totem Nexus";
    private static final int LEGACY_PAGE_COUNT = 7;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Identifier MANUAL_ADVANCEMENT =
            Identifier.fromNamespaceAndPath("deadrecall", "nexus_manual");

    private static final List<String> PAGE_KEYS = IntStream.rangeClosed(1, 12)
            .mapToObj(page -> "book.deadrecall.nexus_teleport_manual.page." + page)
            .toList();
    private static final TotemManualSection SECTION = new TotemManualSection(
            Identifier.parse("totem:nexus/teleport"),
            200,
            "book.deadrecall.nexus_teleport_manual.title",
            PAGE_KEYS,
            Map.of()
    );

    private NexusTeleportManual() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        TotemManualRegistry.global().register(SECTION);
        TotemManualLifecycle.registerLoginRefresh();
    }

    public static boolean isManualRequest(ItemStack stack) {
        return TotemManualPlayerHelper.supportsSourceInteraction(
                stack,
                NexusTeleportManual::isLegacyManual
        );
    }

    static List<String> pageKeys() {
        return PAGE_KEYS;
    }

    public static ItemStack create() {
        return TotemManualAssembler.create(List.of(SECTION));
    }

    /** Delegates lodestone acquisition and exact legacy migration to Core. */
    public static boolean grant(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        return TotemManualPlayerHelper.acquireSections(
                player,
                hand,
                List.of(SECTION),
                MANUAL_ADVANCEMENT,
                NexusTeleportManual::isLegacyManual
        ).handled();
    }

    public static boolean isLegacyManual(ItemStack stack) {
        if (stack == null || !stack.is(Items.WRITTEN_BOOK)
                || !Component.translatable("item.deadrecall.nexus_teleport_manual")
                .equals(stack.get(DataComponents.CUSTOM_NAME))) {
            return false;
        }
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content != null
                && TITLE.equals(content.title().raw())
                && AUTHOR.equals(content.author())
                && content.pages().size() == LEGACY_PAGE_COUNT;
    }
}
