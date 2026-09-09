package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla control clicks must pass through the global map-expansion gate. */
public final class NexusVanillaContainerGameTest {
    @GameTest(maxTicks = 20)
    public void rightDragSprucePlanksCraftsSticks(GameTestHelper helper) {
        verifyDrag(helper, 4, 4, 2);
    }

    @GameTest(maxTicks = 20)
    public void leftDragSprucePlanksCraftsSticks(GameTestHelper helper) {
        verifyDrag(helper, 0, 2, 0);
    }

    private static void verifyDrag(GameTestHelper helper, int startButton, int planks, int remainder) {
        var player = helper.makeMockServerPlayerInLevel();
        try {
            player.getAbilities().instabuild = false;
            var menu = player.inventoryMenu;
            menu.setCarried(new ItemStack(Items.SPRUCE_PLANKS, planks));
            menu.clicked(-999, startButton, ContainerInput.QUICK_CRAFT, player);
            menu.clicked(2, startButton + 1, ContainerInput.QUICK_CRAFT, player);
            menu.clicked(4, startButton + 1, ContainerInput.QUICK_CRAFT, player);
            menu.clicked(-999, startButton + 2, ContainerInput.QUICK_CRAFT, player);
            require(menu.getCarried().getCount() == remainder, "Drag lost or duplicated carried planks");
            require(menu.getSlot(2).getItem().is(Items.SPRUCE_PLANKS)
                    && menu.getSlot(2).getItem().getCount() == 1
                    && menu.getSlot(4).getItem().is(Items.SPRUCE_PLANKS)
                    && menu.getSlot(4).getItem().getCount() == 1, "Drag did not fill the right crafting column");
            require(menu.getSlot(0).getItem().is(Items.STICK)
                    && menu.getSlot(0).getItem().getCount() == 4, "Drag did not produce four sticks");
            menu.clicked(9, 0, ContainerInput.PICKUP, player);
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            require(menu.getCarried().is(Items.STICK) && menu.getCarried().getCount() == 4,
                    "Crafted sticks could not be taken");
            require(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(4).getItem().isEmpty()
                    && menu.getSlot(9).getItem().getCount() == remainder,
                    "Taking sticks did not conserve materials");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void outsidePickupAndNegativeNoOpReachVanilla(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        try {
            var menu = player.inventoryMenu;
            menu.setCarried(new ItemStack(Items.SPRUCE_PLANKS, 2));
            menu.clicked(-1, 0, ContainerInput.PICKUP, player);
            require(menu.getCarried().getCount() == 2, "Negative no-op changed carried items");
            menu.clicked(-999, 1, ContainerInput.PICKUP, player);
            require(menu.getCarried().getCount() == 1, "Outside right click did not drop one item");
            menu.clicked(-999, 0, ContainerInput.PICKUP, player);
            require(menu.getCarried().isEmpty(), "Outside left click did not drop the remainder");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
