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

/** Verifies lodestone recording while acquisition instructions remain Core-owned. */
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
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void plainBookRecordsSharedManualWithNexusChapter(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack plainBook = new ItemStack(Items.BOOK);
            player.setItemInHand(InteractionHand.MAIN_HAND, plainBook);
            if (!NexusTeleportManual.isManualRequest(plainBook)
                    || !NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)) {
                helper.fail("Plain book was not accepted at the Nexus lodestone source");
                return;
            }

            ItemStack converted = player.getMainHandItem();
            List<Identifier> sectionIds = TotemManualAssembler.sections(converted).stream()
                    .map(section -> section.id())
                    .toList();
            List<Identifier> expected = List.of(
                    TotemManualOnboarding.SECTION_ID,
                    Identifier.parse("totem:nexus/teleport")
            );
            if (!TotemManualAssembler.isCanonical(converted) || !sectionIds.equals(expected)) {
                helper.fail("Plain book did not become the shared manual with Core + Nexus: " + sectionIds);
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void existingSharedManualReceivesNexusChapterInPlace(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack currentManual = TotemManualAssembler.create(List.of(TotemManualOnboarding.SECTION));
            player.setItemInHand(InteractionHand.MAIN_HAND, currentManual);
            if (!NexusTeleportManual.isManualRequest(currentManual)
                    || !NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)) {
                helper.fail("Existing shared manual was not accepted at the Nexus lodestone source");
                return;
            }

            List<Identifier> sectionIds = TotemManualAssembler.sections(currentManual).stream()
                    .map(section -> section.id())
                    .toList();
            List<Identifier> expected = List.of(
                    TotemManualOnboarding.SECTION_ID,
                    Identifier.parse("totem:nexus/teleport")
            );
            if (player.getMainHandItem() != currentManual || !sectionIds.equals(expected)) {
                helper.fail("Nexus chapter was not appended to the existing shared manual: " + sectionIds);
                return;
            }

            long otherCanonicalGuides = IntStream.range(0, player.getInventory().getContainerSize())
                    .mapToObj(player.getInventory()::getItem)
                    .filter(stack -> stack != currentManual)
                    .filter(TotemManualAssembler::isCanonical)
                    .count();
            if (otherCanonicalGuides != 0) {
                helper.fail("Recording Nexus created a second Totem Manual");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void exactLegacyGuideStillMigratesButTitleOnlyBookDoesNot(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack legacy = legacyManual();
            player.setItemInHand(InteractionHand.MAIN_HAND, legacy);
            if (!NexusTeleportManual.isManualRequest(legacy)
                    || !NexusTeleportManual.grant(player, InteractionHand.MAIN_HAND)
                    || !TotemManualAssembler.isCanonical(player.getMainHandItem())) {
                helper.fail("Exact generated legacy Nexus guide did not migrate");
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
            if (NexusTeleportManual.isLegacyManual(titleOnly)
                    || NexusTeleportManual.isManualRequest(titleOnly)) {
                helper.fail("A player-authored title-only book matched the legacy Nexus signature");
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
        int expectedVirtualPages = NexusTeleportManual.pageKeys().size() + 3;
        int virtualPages = TotemManualAssembler.virtualPages(TotemManualAssembler.sections(manual)).size();
        if (content == null || content.pages().size() != expectedPhysicalPages) {
            helper.fail("Expected two physical metadata pages for the Nexus section");
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
                                Component.translatable("book.deadrecall.nexus_teleport_manual.page." + page)
                        ))
                        .toList(),
                false
        ));
        return manual;
    }
}
