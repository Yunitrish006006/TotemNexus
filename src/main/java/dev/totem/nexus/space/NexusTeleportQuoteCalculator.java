package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Pure, server-fed calculation for the legacy teleport map quote. */
public final class NexusTeleportQuoteCalculator {
    private static final int FOOD_BLOCKS_PER_POINT = 384;
    private NexusTeleportQuoteCalculator() { }

    public static NexusMapQuote calculate(Source source, Target target, TeleportInterfaceType interfaceType,
                                          Resources resources, boolean filledMapCoversTarget) {
        boolean sameDimension = source.dimension.equals(target.dimension);
        boolean sameUnit = source.type.equals("lodestone") && source.id.equals(target.id);
        int distance = sameDimension ? distance(source.pos, target.pos) : -1;
        double stability = sameUnit ? 1D : stability(source, target, sameDimension, distance);
        int baseFood = sameUnit ? 0 : baseFood(target, sameDimension, distance);
        int basePrepare = sameUnit ? 0 : prepare(target, sameDimension, distance, stability);
        int baseDeviation = sameUnit ? 0 : deviation(stability);
        int damage = sameUnit ? 0 : damage(source, target, sameDimension, distance, stability);
        int baseAmethyst = sameUnit || sameDimension ? 0 : Math.max(2, 2 + (int) Math.ceil((1D - stability) * 4D));
        TeleportInterfaceQuotePolicy.Quote specialization = TeleportInterfaceQuotePolicy.specialize(interfaceType, target.type,
                resources.playerId.equals(target.ownerId), filledMapCoversTarget, baseFood, basePrepare, baseDeviation, damage);
        int payableFood = resources.creative ? 0 : specialization.foodCost();
        int payableBaseFood = resources.creative ? 0 : baseFood;
        int saturation = resources.creative ? 0 : Math.min(payableFood, Math.max(0, resources.saturation));
        int remaining = payableFood - saturation;
        int hunger = resources.creative ? 0 : Math.min(remaining, Math.max(0, resources.foodLevel - 1));
        int needed = resources.creative ? 0 : Math.max(0, remaining - hunger);
        String blocked = blocked(source, target, stability, needed, resources.safeFoodPoints, baseAmethyst, resources.amethyst, sameDimension, sameUnit);
        return new NexusMapQuote(stability, target.tier, distance, payableBaseFood, payableFood, saturation, hunger, needed, resources.safeFoodPoints,
                baseAmethyst, resources.amethyst, baseAmethyst, source.catalysts, target.catalysts, 0, basePrepare, specialization.prepareTicks(),
                baseDeviation, specialization.maxHorizontalDeviation(), damage, damage, specialization.structureWearChancePercent(),
                specialization.bonusActive(), specialization.bonusMessageKey(), blocked.isEmpty(), blocked);
    }

    private static String blocked(Source s, Target t, double stability, int foodNeeded, int safeFood, int amethyst, int available, boolean sameDimension, boolean sameUnit) {
        if (sameUnit) return "message.deadrecall.space_unit.teleport_blocked.same_source";
        if (stability < .2D) return "message.deadrecall.space_unit.teleport_blocked.unstable";
        if (!sameDimension && s.type.equals("lodestone") && s.tier < 1) return "message.deadrecall.space_unit.teleport_blocked.source_tier";
        if (!sameDimension && t.lodestone && t.tier < 1) return "message.deadrecall.space_unit.teleport_blocked.target_tier";
        if (foodNeeded > safeFood) return "message.deadrecall.space_unit.teleport_blocked.food";
        if (amethyst > available) return "message.deadrecall.space_unit.teleport_blocked.amethyst";
        return "";
    }
    private static double stability(Source s, Target t, boolean sameDimension, int distance) {
        double result = Math.min(unitStability(s.type, s.stability), unitStability(t.type, t.stability));
        result *= sameDimension ? Math.max(.55D, 1D - distance / 12000D) : .65D;
        result *= switch (t.type) { case DEATH -> .72D; case PLAYER -> .65D; case TEMPORARY -> .85D; default -> 1D; };
        if (s.type.equals("player")) result *= .85D;
        return clamp(result, 0D, 1D);
    }
    private static double unitStability(String type, double value) { return type.equals("player") || type.equals("lodestone") ? clamp(value, 0D, 1D) : .8D; }
    private static double unitStability(SpaceUnitType type, double value) { return switch (type) { case LODESTONE -> clamp(value, 0D, 1D); case DEATH -> .55D; case PLAYER -> .6D; case TEMPORARY -> .5D; case SYSTEM -> .8D; }; }
    private static int baseFood(Target t, boolean same, int distance) { int value = same ? Math.max(1, (distance + FOOD_BLOCKS_PER_POINT - 1) / FOOD_BLOCKS_PER_POINT) : 6;
        value += switch (t.type) { case DEATH, PLAYER -> 4; case TEMPORARY -> 2; default -> 0; }; return clamp(value + (same ? 0 : 4), 1, 20); }
    private static int prepare(Target t, boolean same, int distance, double stability) { int value = 60 + (int) Math.round((1D - stability) * 80D) + (same ? Math.min(120, Math.max(0, distance / 32)) : 100);
        return clamp(value + switch (t.type) { case DEATH -> 40; case PLAYER -> 50; case TEMPORARY -> 20; default -> 0; }, 40, 300); }
    private static int deviation(double stability) { return stability >= .95D ? 1 : stability >= .8D ? 3 : stability >= .6D ? 8 : stability >= .4D ? 20 : stability >= .2D ? 48 : 96; }
    private static int damage(Source s, Target t, boolean same, int distance, double stability) { double value = (1D-stability)*20D + (same ? Math.min(8D, distance/1500D) : 10D) + Math.max(0D, 1D-s.stability)*6D + t.wear*18D;
        value += switch(t.type) { case DEATH -> 8D; case PLAYER -> 10D; case TEMPORARY -> 4D; default -> 0D; }; if (s.type.equals("lodestone") && s.tier >= 2) value -= 3D; if (t.lodestone && t.tier >= 2) value -= 3D; return clamp((int)Math.round(value),0,60); }
    private static int distance(BlockPos a, BlockPos b) { long x=(long)a.getX()-b.getX(), y=(long)a.getY()-b.getY(), z=(long)a.getZ()-b.getZ(); return (int)Math.round(Math.sqrt(x*x+y*y+z*z)); }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));} private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    public record Source(UUID id, String type, ResourceKey<Level> dimension, BlockPos pos, double stability, int tier, int catalysts) { }
    public record Target(UUID id, SpaceUnitType type, ResourceKey<Level> dimension, BlockPos pos, double stability, int tier, double wear, boolean lodestone, UUID ownerId, int catalysts) { }
    public record Resources(UUID playerId, boolean creative, int saturation, int foodLevel, int safeFoodPoints, int amethyst) { }
}
