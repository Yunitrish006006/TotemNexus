package dev.totem.nexus.space;

/** Server-side conversion of material totals into transient route-load limits. */
public final class TeleportRouteLoadPolicy {
    public static final int BASE_RECOVERY_TICKS = 200;
    public static final int MIN_RECOVERY_TICKS = 20;
    public static final int MAX_RECOVERY_TICKS = 600;
    public static final int MIN_SLOTS = 1;
    public static final int MAX_SLOTS = 8;
    public static final int BASE_REPAIR_ITEM_COST = 4;

    private TeleportRouteLoadPolicy() {
    }

    public static int slotCapacity(int routeLoadCapacityTotal) {
        return TeleportArrayMaterialAttributes.clamp(
                1 + routeLoadCapacityTotal,
                MIN_SLOTS,
                MAX_SLOTS
        );
    }

    public static int recoveryTicks(int cooldownRecoveryTotal) {
        int multiplier = TeleportArrayMaterialAttributes.clamp(
                100 + cooldownRecoveryTotal,
                25,
                200
        );
        return TeleportArrayMaterialAttributes.clamp(
                (int) Math.round((double) BASE_RECOVERY_TICKS * 100.0D / multiplier),
                MIN_RECOVERY_TICKS,
                MAX_RECOVERY_TICKS
        );
    }

    public static int maintenanceItemCost(int maintenanceEfficiencyTotal) {
        int multiplier = TeleportArrayMaterialAttributes.clamp(
                100 + maintenanceEfficiencyTotal,
                25,
                200
        );
        return Math.max(1, (int) Math.ceil((double) BASE_REPAIR_ITEM_COST * 100.0D / multiplier));
    }
}
