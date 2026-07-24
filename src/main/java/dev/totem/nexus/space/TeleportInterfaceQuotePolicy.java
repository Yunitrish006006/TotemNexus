package dev.totem.nexus.space;

/** Pure interface specialization rules applied after the ordinary-compass quote is calculated. */
public final class TeleportInterfaceQuotePolicy {
    public static final int MAX_FOOD_COST = 20;
    public static final int MAX_PREPARE_TICKS = 300;
    public static final int MAX_DEVIATION = 96;
    public static final int MAX_WEAR_CHANCE_PERCENT = 60;
    private static final int BOOK_MIN_PREPARE_TICKS = 30;
    private TeleportInterfaceQuotePolicy() { }
    public static Quote specialize(TeleportInterfaceType interfaceType, SpaceUnitType targetType, boolean targetOwnedByPlayer,
                                   boolean filledMapCoversTarget, int baseFoodCost, int basePrepareTicks,
                                   int baseDeviation, int baseWearChance) {
        if (interfaceType == null || targetType == null) throw new IllegalArgumentException("Interface and target types are required");
        int food = clamp(baseFoodCost, 0, MAX_FOOD_COST);
        int prepare = clamp(basePrepareTicks, 0, MAX_PREPARE_TICKS);
        int deviation = clamp(baseDeviation, 0, MAX_DEVIATION);
        int wear = clamp(baseWearChance, 0, MAX_WEAR_CHANCE_PERCENT);
        if (interfaceType == TeleportInterfaceType.RECOVERY_COMPASS && targetType == SpaceUnitType.DEATH && targetOwnedByPlayer)
            return new Quote(food, prepare, floor(deviation, .5D), wear, true, "message.deadrecall.space_unit.interface_bonus.recovery_compass.active");
        if (interfaceType == TeleportInterfaceType.BOOK && targetType == SpaceUnitType.LODESTONE && prepare > 0)
            return new Quote(food, clamp(Math.max(BOOK_MIN_PREPARE_TICKS, ceil(prepare, .8D)), BOOK_MIN_PREPARE_TICKS, MAX_PREPARE_TICKS), deviation, floor(wear, .75D), true, "message.deadrecall.space_unit.interface_bonus.book.active");
        if (interfaceType == TeleportInterfaceType.FILLED_MAP && filledMapCoversTarget && (food > 0 || deviation > 0))
            return new Quote(food == 0 ? 0 : Math.max(1, ceil(food, .8D)), prepare, floor(deviation, .8D), wear, true, "message.deadrecall.space_unit.interface_bonus.filled_map.active");
        return new Quote(food, prepare, deviation, wear, false, inactiveMessageKey(interfaceType));
    }
    private static String inactiveMessageKey(TeleportInterfaceType type) { return switch (type) {
        case COMPASS -> "message.deadrecall.space_unit.interface_bonus.compass";
        case RECOVERY_COMPASS -> "message.deadrecall.space_unit.interface_bonus.recovery_compass.inactive";
        case BOOK -> "message.deadrecall.space_unit.interface_bonus.book.inactive";
        case FILLED_MAP -> "message.deadrecall.space_unit.interface_bonus.filled_map.inactive"; }; }
    private static int ceil(int value, double multiplier) { return (int) Math.ceil(value * multiplier); }
    private static int floor(int value, double multiplier) { return (int) Math.floor(value * multiplier); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public record Quote(int foodCost, int prepareTicks, int maxHorizontalDeviation, int structureWearChancePercent,
                        boolean bonusActive, String bonusMessageKey) {
        public Quote {
            if (foodCost < 0 || foodCost > MAX_FOOD_COST || prepareTicks < 0 || prepareTicks > MAX_PREPARE_TICKS
                    || maxHorizontalDeviation < 0 || maxHorizontalDeviation > MAX_DEVIATION
                    || structureWearChancePercent < 0 || structureWearChancePercent > MAX_WEAR_CHANCE_PERCENT
                    || bonusMessageKey == null || bonusMessageKey.isBlank()) throw new IllegalArgumentException("Specialized quote is outside its legal range");
        }
    }
}
