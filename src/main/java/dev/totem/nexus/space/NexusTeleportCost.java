package dev.totem.nexus.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;

/** Server-only resource snapshot and deduction for an already-authorized quote. */
public final class NexusTeleportCost {
    private NexusTeleportCost() { }
    public static NexusTeleportQuoteCalculator.Resources resources(ServerPlayer player) {
        int safeFood = 0, amethyst = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot); FoodProperties food = safeFood(stack);
            if (food != null) safeFood += food.nutrition() * stack.getCount();
            if (stack.is(Items.AMETHYST_SHARD)) amethyst += stack.getCount();
        }
        return new NexusTeleportQuoteCalculator.Resources(player.getUUID(), player.getAbilities().instabuild,
                (int)Math.floor(player.getFoodData().getSaturationLevel()), player.getFoodData().getFoodLevel(), safeFood, amethyst);
    }
    public static boolean deduct(ServerPlayer player, NexusMapQuote quote) {
        if (player.getAbilities().instabuild) return true;
        if (quote.foodPointsNeeded() > resources(player).safeFoodPoints() || quote.amethystCost() > resources(player).amethyst()) return false;
        player.getFoodData().setSaturation(Math.max(0F, player.getFoodData().getSaturationLevel() - quote.saturationCost()));
        if (quote.hungerCost() > 0) player.getFoodData().setFoodLevel(Math.max(1, player.getFoodData().getFoodLevel() - quote.hungerCost()));
        return consumeFood(player, quote.foodPointsNeeded()) && consumeAmethyst(player, quote.amethystCost());
    }
    private static boolean consumeFood(ServerPlayer player, int points) { int remaining = points;
        for (int slot=0; slot<player.getInventory().getContainerSize() && remaining>0; slot++) { ItemStack stack=player.getInventory().getItem(slot); FoodProperties food=safeFood(stack); if(food==null) continue;
            int count=Math.min(stack.getCount(), (remaining+food.nutrition()-1)/food.nutrition()); stack.shrink(count); remaining-=count*food.nutrition(); } return remaining<=0; }
    private static boolean consumeAmethyst(ServerPlayer player, int amount) { int remaining=amount;
        for(int slot=0;slot<player.getInventory().getContainerSize()&&remaining>0;slot++){ItemStack stack=player.getInventory().getItem(slot);if(!stack.is(Items.AMETHYST_SHARD))continue;int count=Math.min(stack.getCount(),remaining);stack.shrink(count);remaining-=count;}return remaining<=0; }
    private static FoodProperties safeFood(ItemStack stack) { if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_DATA)) return null; FoodProperties food=stack.get(DataComponents.FOOD);
        Consumable consumable=stack.get(DataComponents.CONSUMABLE); return food == null || food.nutrition() <= 0 || consumable != null && !consumable.onConsumeEffects().isEmpty() ? null : food; }
}
