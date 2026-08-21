package dev.totem.nexus.space;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import dev.totem.core.api.v1.manual.TotemManualOnboarding;
import dev.totem.core.api.v1.manual.TotemManualPlayerHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;
import java.util.stream.IntStream;

/** Verifies the server-side book conversion used by lodestone interaction. */
public final class NexusTeleportManualGameTest {
    @GameTest(maxTicks = 20)
    public void basicGuideIsGrantedExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            var root = player.level().getServer().getAdvancements().get(
                    Identifier.fromNamespaceAndPath("deadrecall", "root")
            );
            if (root == null) {
                helper.fail("Core onboarding advancement was not loaded");
                return;
            }
            player.getAdvancements().revoke(root, "received_basic_manual");
            player.getInventory().clearContent();
            if (!TotemManualPlayerHelper.ensureBasicManual(player)) {
                helper.fail("First onboarding check did not deliver the basic guide");
                return;
            }
            long firstCount = player.getInventory().getNonEquipmentItems().stream()
                    .filter(TotemManualAssembler::isCanonical)
                    .filter(stack -> TotemManualAssembler.sections(stack).stream()
                            .anyMatch(section -> section.id().equals(TotemManualOnboarding.SECTION_ID)))
                    .count();
            if (firstCount != 1 || TotemManualPlayerHelper.ensureBasicManual(player)) {
                helper.fail("Basic guide grant repeated or did not create exactly one guide");
                return;
            }
            long secondCount = player.getInventory().getNonEquipmentItems().stream()
                    .filter(TotemManualAssembler::isCanonical)
                    .count();
            if (secondCount != 1) {
                helper.fail("Reconnect-style onboarding check duplicated the basic guide");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void lodestoneManualConversionReplacesOnePlainBook(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
            if (!NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)) {
                helper.fail("Plain book did not become a Nexus teleport manual");
                return;
            }

            ItemStack converted = player.getMainHandItem();
            if (!converted.is(Items.WRITTEN_BOOK)
                    || converted.get(DataComponents.WRITTEN_BOOK_CONTENT) == null
                    || !TotemManualAssembler.isCanonical(converted)) {
                helper.fail("Manual conversion did not leave a written guide in the active hand");
                return;
            }
            var advancement = player.level().getServer().getAdvancements().get(
                    Identifier.fromNamespaceAndPath("deadrecall", "nexus_manual")
            );
            if (advancement == null
                    || !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                helper.fail("Obtaining the Nexus guide did not award its module advancement");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void exactLegacyGuideMigratesButTitleOnlyBookDoesNot(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack legacy = legacyManual();
            player.setItemInHand(InteractionHand.MAIN_HAND, legacy);
            if (!NexusTeleportManual.isLegacyManual(legacy)
                    || !NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)
                    || !TotemManualAssembler.isCanonical(player.getMainHandItem())) {
                helper.fail("Exact generated legacy guide did not migrate");
                return;
            }

            ItemStack titleOnly = new ItemStack(Items.WRITTEN_BOOK);
            titleOnly.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                    Filterable.passThrough("Nexus Teleport Manual"),
                    "Player",
                    0,
                    java.util.List.of(),
                    false
            ));
            if (NexusTeleportManual.isLegacyManual(titleOnly)) {
                helper.fail("A player-authored title-only book matched the legacy signature");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void activeModuleGuideConsolidatesOffhandGuide(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack primary = NexusTeleportManual.create();
            primary.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, primary);
            player.setItemInHand(InteractionHand.OFF_HAND, NexusTeleportManual.create());
            if (!NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)
                    || !TotemManualAssembler.isCanonical(player.getMainHandItem())
                    || !player.getMainHandItem().getOrDefault(
                            DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                            false
                    )
                    || !player.getOffhandItem().isEmpty()) {
                helper.fail("Module refresh did not preserve the active guide and consolidate the offhand guide");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void basicGuideRemainsSeparateFromGrantedModuleGuide(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack basicGuide = TotemManualAssembler.create(List.of(TotemManualOnboarding.SECTION));
            player.setItemInHand(InteractionHand.MAIN_HAND, basicGuide);
            if (!NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)) {
                helper.fail("Basic guide did not act as a reusable Nexus recording reference");
                return;
            }
            if (player.getMainHandItem() != basicGuide
                    || !TotemManualAssembler.sections(basicGuide).stream()
                    .map(section -> section.id())
                    .toList()
                    .equals(List.of(TotemManualOnboarding.SECTION_ID))) {
                helper.fail("Granting the Nexus guide modified or replaced the basic guide");
                return;
            }

            List<Identifier> nexusSection = List.of(Identifier.parse("totem:nexus/teleport"));
            long targetGuides = IntStream.range(0, player.getInventory().getContainerSize())
                    .mapToObj(player.getInventory()::getItem)
                    .filter(TotemManualAssembler::isCanonical)
                    .filter(stack -> TotemManualAssembler.sections(stack).stream()
                            .map(section -> section.id())
                            .toList()
                            .equals(nexusSection))
                    .count();
            if (targetGuides != 1) {
                helper.fail("Expected one separate Nexus guide, found " + targetGuides);
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void assembledPageCountMatchesInstalledManualSections(GameTestHelper helper) {
        ItemStack manual = NexusTeleportManual.create();
        WrittenBookContent content = manual.get(DataComponents.WRITTEN_BOOK_CONTENT);
        int expectedPages = 21;
        if (content == null || content.pages().size() != expectedPages) {
            helper.fail(
                    "Expected " + expectedPages + " assembled pages, got "
                            + (content == null ? "no content" : content.pages().size())
            );
            return;
        }
        helper.succeed();
    }

    private static ItemStack legacyManual() {
        ItemStack manual = new ItemStack(Items.WRITTEN_BOOK);
        manual.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable("item.deadrecall.nexus_teleport_manual")
        );
        manual.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Nexus Teleport Manual"),
                "Totem Nexus",
                0,
                IntStream.rangeClosed(1, 7)
                        .mapToObj(page -> Filterable.<Component>passThrough(
                                Component.translatable(
                                        "book.deadrecall.nexus_teleport_manual.page." + page
                                )
                        ))
                        .toList(),
                false
        ));
        return manual;
    }
}
