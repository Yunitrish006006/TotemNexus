package dev.totem.nexus.space;

/** Pure server-side conversion from a scanned material total to array structure values. */
final class TeleportArrayMaterialStructurePolicy {
    private TeleportArrayMaterialStructurePolicy() {
    }

    static int effectiveCapacity(TeleportArrayMaterialAttributes totals) {
        return Math.max(0, totals == null ? 0 : totals.structureCapacity());
    }

    static double completeness(int effectiveCapacity) {
        return Math.min(1.0D, Math.max(0, effectiveCapacity) / 24.0D);
    }

    static int interference(int familyCount, int interferenceResistance) {
        return TeleportArrayMaterialAttributes.clamp(
                (2 * Math.max(0, familyCount - 1)) - interferenceResistance,
                0,
                100
        );
    }

    static int stability(double completeness, double symmetry, int materialStability, int interference) {
        double geometric = Math.min(100.0D, ((completeness * 0.7D) + (symmetry * 0.3D)) * 100.0D);
        return TeleportArrayMaterialAttributes.clamp(
                (int) Math.round(geometric) + materialStability - interference,
                0,
                100
        );
    }

    static int tier(int effectiveCapacity) {
        return effectiveCapacity >= 24 ? 2 : effectiveCapacity >= 8 ? 1 : 0;
    }
}
