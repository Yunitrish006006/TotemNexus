package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Verifies the server-side book conversion used by lodestone interaction. */
public final class NexusTeleportManualGameTest {
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
                    || converted.get(DataComponents.WRITTEN_BOOK_CONTENT) == null) {
                helper.fail("Manual conversion did not leave a written guide in the active hand");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
