package dev.totem.nexus.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;

/** One-shot, map-specific fault injection, present only in the GameTest mod. */
public final class NexusMapPostProcessFailureProbe {
    public enum Failure { DENY, ERROR, REMOVE_ORIGIN }
    private static final ThreadLocal<Plan> ARMED = new ThreadLocal<>();
    private static final class Plan {
        final MapId map; int remaining; final Failure failure;
        Plan(MapId map, int remaining, Failure failure) { this.map=map; this.remaining=remaining; this.failure=failure; }
    }
    public static void arm(ItemStack map, int successesBefore, Failure failure) {
        ARMED.set(new Plan(map.get(DataComponents.MAP_ID), successesBefore, failure));
    }
    public static Failure take(ItemStack map) {
        Plan plan=ARMED.get();
        if(plan==null || !plan.map.equals(map.get(DataComponents.MAP_ID))) return null;
        if(plan.remaining-- > 0) return null;
        ARMED.remove(); return plan.failure;
    }
    public static void clear() { ARMED.remove(); }
}
