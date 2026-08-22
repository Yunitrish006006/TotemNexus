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

/** Verifies the server-side unified manual conversion used by lodestone interaction. */
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
    public void moduleRefreshPreservesActiveGuideAndOtherCanonicalBook(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack primary = NexusTeleportManual.create();
            ItemStack other = NexusTeleportManual.create();
            primary.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, primary);
            player.setItemInHand(InteractionHand.OFF_HAND, other);
            if (!NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)
                    || player.getMainHandItem() != primary
                    || !TotemManualAssembler.isCanonical(primary)
                    || !primary.getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)
                    || player.getOffhandItem() != other
                    || !TotemManualAssembler.isCanonical(other)) {
                helper.fail("Unified manual refresh destructively replaced or consumed an existing canonical book");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void basicGuideReceivesNexusChapterInPlace(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack basicGuide = TotemManualAssembler.create(List.of(TotemManualOnboarding.SECTION));
            player.setItemInHand(InteractionHand.MAIN_HAND, basicGuide);
            if (!NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)) {
                helper.fail("Basic guide did not accept the Nexus chapter");
                return;
            }

            List<Identifier> sectionIds = TotemManualAssembler.sections(basicGuide).stream()
                    .map(section -> section.id())
                    .toList();
            List<Identifier> expected = List.of(
                    TotemManualOnboarding.SECTION_ID,
                    Identifier.parse("totem:nexus/teleport")
            );
            if (player.getMainHandItem() != basicGuide || !sectionIds.equals(expected)) {
                helper.fail("Nexus chapter was not appended to the existing shared manual: " + sectionIds);
                return;
            }

            long otherCanonicalGuides = IntStream.range(0, player.getInventory().getContainerSize())
                    .mapToObj(player.getInventory()::getItem)
                    .filter(stack -> stack != basicGuide)
                    .filter(TotemManualAssembler::isCanonical)
                    .count();
            if (otherCanonicalGuides != 0) {
                helper.fail("Granting Nexus created a separate module guide instead of extending the shared manual");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void physicalBookStaysTinyWhileVirtualPagesContainNexusChapter(GameTestHelper helper) {
        ItemStack manual = NexusTeleportManual.create();
        WrittenBookContent content = manual.get(DataComponents.WRITTEN_BOOK_CONTENT);
        int expectedPhysicalPages = 2;
        int expectedVirtualPages = 21;
        int virtualPages = TotemManualAssembler.virtualPages(TotemManualAssembler.sections(manual)).size();
        if (content == null || content.pages().size() != expectedPhysicalPages) {
            helper.fail(
                    "Expected " + expectedPhysicalPages + " physical metadata pages, got "
                            + (content == null ? "no content" : content.pages().size())
            );
            return;
        }
        if (virtualPages != expectedVirtualPages) {
            helper.fail("Expected " + expectedVirtualPages + " virtual Nexus pages, got " + virtualPages);
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
