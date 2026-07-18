package com.adaptor.deadrecall.space;

/** Pure Phase B specialization rules applied after the ordinary-compass quote is calculated. */
public final class TeleportInterfaceQuotePolicy {
    public static final int MAX_PREPARE_TICKS = 300;
    public static final int MAX_DEVIATION = 96;
    public static final int MAX_WEAR_CHANCE_PERCENT = 60;
    private static final int BOOK_MIN_PREPARE_TICKS = 30;

    private TeleportInterfaceQuotePolicy() {
    }

    public static Quote specialize(
            TeleportInterfaceType interfaceType,
            SpaceUnitType targetType,
            boolean targetOwnedByPlayer,
            int basePrepareTicks,
            int baseDeviation,
            int baseStructureWearChancePercent) {
        if (interfaceType == null || targetType == null) {
            throw new IllegalArgumentException("Interface and target types are required");
        }

        int prepareTicks = clamp(basePrepareTicks, 0, MAX_PREPARE_TICKS);
        int deviation = clamp(baseDeviation, 0, MAX_DEVIATION);
        int wearChance = clamp(baseStructureWearChancePercent, 0, MAX_WEAR_CHANCE_PERCENT);

        if (interfaceType == TeleportInterfaceType.RECOVERY_COMPASS
                && targetType == SpaceUnitType.DEATH
                && targetOwnedByPlayer) {
            return new Quote(
                    prepareTicks,
                    floorMultiply(deviation, 0.50D),
                    wearChance,
                    true,
                    "message.deadrecall.space_unit.interface_bonus.recovery_compass.active"
            );
        }

        if (interfaceType == TeleportInterfaceType.BOOK
                && targetType == SpaceUnitType.LODESTONE
                && prepareTicks > 0) {
            return new Quote(
                    clamp(Math.max(BOOK_MIN_PREPARE_TICKS, ceilMultiply(prepareTicks, 0.80D)),
                            BOOK_MIN_PREPARE_TICKS, MAX_PREPARE_TICKS),
                    deviation,
                    floorMultiply(wearChance, 0.75D),
                    true,
                    "message.deadrecall.space_unit.interface_bonus.book.active"
            );
        }

        return new Quote(
                prepareTicks,
                deviation,
                wearChance,
                false,
                inactiveMessageKey(interfaceType)
        );
    }

    private static String inactiveMessageKey(TeleportInterfaceType interfaceType) {
        return switch (interfaceType) {
            case COMPASS -> "message.deadrecall.space_unit.interface_bonus.compass";
            case RECOVERY_COMPASS ->
                    "message.deadrecall.space_unit.interface_bonus.recovery_compass.inactive";
            case BOOK -> "message.deadrecall.space_unit.interface_bonus.book.inactive";
            case FILLED_MAP -> "message.deadrecall.space_unit.interface_bonus.filled_map.pending";
        };
    }

    private static int ceilMultiply(int value, double multiplier) {
        return (int) Math.ceil(value * multiplier);
    }

    private static int floorMultiply(int value, double multiplier) {
        return (int) Math.floor(value * multiplier);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Quote(
            int prepareTicks,
            int maxHorizontalDeviation,
            int structureWearChancePercent,
            boolean bonusActive,
            String bonusMessageKey) {

        public Quote {
            if (prepareTicks < 0 || prepareTicks > MAX_PREPARE_TICKS
                    || maxHorizontalDeviation < 0 || maxHorizontalDeviation > MAX_DEVIATION
                    || structureWearChancePercent < 0
                    || structureWearChancePercent > MAX_WEAR_CHANCE_PERCENT) {
                throw new IllegalArgumentException("Specialized quote is outside its legal range");
            }
            if (bonusMessageKey == null || bonusMessageKey.isBlank()) {
                throw new IllegalArgumentException("Specialized quote requires a bonus message key");
            }
        }
    }
}
