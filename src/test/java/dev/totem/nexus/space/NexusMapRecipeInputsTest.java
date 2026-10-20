package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapRecipeInputsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }

    @Test
    void onlyACompleteThreeByThreePaperRingCanExpandAMap() {
        ItemStack map = map(1);
        List<ItemStack> grid = grid(map);
        assertEquals(map, NexusMapRecipeInputs.workbenchSource(grid, MapPostProcessing.SCALE).orElseThrow());
        assertTrue(NexusMapRecipeInputs.workbenchSource(grid.subList(0, 4), MapPostProcessing.SCALE).isEmpty());
        assertTrue(NexusMapRecipeInputs.workbenchSource(grid, MapPostProcessing.LOCK).isEmpty());
        for (int i = 0; i < 9; i++) {
            List<ItemStack> missing = new ArrayList<>(grid); missing.set(i, ItemStack.EMPTY);
            assertTrue(NexusMapRecipeInputs.workbenchSource(missing, MapPostProcessing.SCALE).isEmpty());
        }
        grid.set(0, map(2));
        assertTrue(NexusMapRecipeInputs.workbenchSource(grid, MapPostProcessing.SCALE).isEmpty());
    }

    @Test
    void cartographyRequiresItsExactOperationIngredient() {
        ItemStack map = map(1);
        assertTrue(NexusMapRecipeInputs.cartographySource(map, new ItemStack(Items.PAPER), MapPostProcessing.SCALE).isPresent());
        assertTrue(NexusMapRecipeInputs.cartographySource(map, new ItemStack(Items.GLASS_PANE), MapPostProcessing.LOCK).isPresent());
        assertTrue(NexusMapRecipeInputs.cartographySource(map, new ItemStack(Items.PAPER), MapPostProcessing.LOCK).isEmpty());
        assertTrue(NexusMapRecipeInputs.cartographySource(map, new ItemStack(Items.MAP), MapPostProcessing.SCALE).isEmpty());
        assertTrue(NexusMapRecipeInputs.cartographySource(map, ItemStack.EMPTY, MapPostProcessing.SCALE).isEmpty());
    }

    @Test
    void resultMustMatchMapIdBindingComponentsAndProcessing() {
        ItemStack source = map(1);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Original"));
        ItemStack result = source.copyWithCount(1);
        result.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        assertTrue(NexusMapRecipeInputs.matchesResult(source, result, MapPostProcessing.SCALE));
        for (int variant = 0; variant < 5; variant++) {
            ItemStack wrong = result.copy();
            switch (variant) {
                case 0 -> wrong.set(DataComponents.MAP_ID, new MapId(2));
                case 1 -> NexusInterfaceBinding.writeIdentity(wrong, UUID.randomUUID());
                case 2 -> wrong.set(DataComponents.CUSTOM_NAME, Component.literal("Other"));
                case 3 -> wrong.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
                case 4 -> wrong.setCount(2);
            }
            assertFalse(NexusMapRecipeInputs.matchesResult(source, wrong, MapPostProcessing.SCALE));
        }
        assertEquals("Original", source.getHoverName().getString());
        assertFalse(source.has(DataComponents.MAP_POST_PROCESSING));
    }

    @Test
    void stackedSourceMapsAreReadWithoutMutationButPendingResultsAreNotIngredients() {
        ItemStack source = map(1); source.setCount(5);
        ItemStack result = source.copyWithCount(1);
        result.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
        assertTrue(NexusMapRecipeInputs.matchesResult(source, result, MapPostProcessing.LOCK));
        assertEquals(5, source.getCount());
        assertTrue(NexusMapRecipeInputs.cartographySource(result, new ItemStack(Items.PAPER), MapPostProcessing.SCALE).isEmpty());
        assertFalse(NexusMapRecipeInputs.matchesResult(source, ItemStack.EMPTY, MapPostProcessing.SCALE));
    }

    private static ItemStack map(int id) {
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, new MapId(id));
        NexusInterfaceBinding.writeIdentity(map, UUID.randomUUID());
        return map;
    }

    private static List<ItemStack> grid(ItemStack map) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(i == 4 ? map : new ItemStack(Items.PAPER));
        return grid;
    }
}
