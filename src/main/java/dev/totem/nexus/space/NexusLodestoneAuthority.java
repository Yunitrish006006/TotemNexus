package dev.totem.nexus.space;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Persisted lodestone mutation rules, detached from source-item and UI wiring until cutover. */
public final class NexusLodestoneAuthority {
    public enum AccessRole { ADMINISTRATOR, ALLOWED;
        public static Optional<AccessRole> fromId(String value) {
            if (value == null) return Optional.empty();
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "administrator" -> Optional.of(ADMINISTRATOR); case "allowed" -> Optional.of(ALLOWED); default -> Optional.empty();
            };
        }
    }

    public Optional<NexusSpaceUnitRecord> setVisibility(NexusSpaceUnitSavedData units, UUID actor, UUID unitId, String visibility, long time) {
        return parseVisibility(visibility).flatMap(next -> units.updateLodestoneVisibility(actor, unitId, next, time));
    }
    public Optional<NexusSpaceUnitRecord> rename(NexusSpaceUnitSavedData units, UUID actor, UUID unitId, String name, long time) {
        String normalized = normalizeName(name); return normalized.isEmpty() ? Optional.empty() : units.renameLodestone(actor, unitId, normalized, time);
    }
    public Optional<NexusSpaceUnitRecord> setAccess(NexusSpaceUnitSavedData units, UUID actor, UUID unitId, UUID target, String role, boolean enabled, long time) {
        return AccessRole.fromId(role).flatMap(next -> units.updateLodestoneAccess(actor, unitId, target, next, enabled, time));
    }
    private static Optional<SpaceUnitVisibility> parseVisibility(String value) {
        if (value == null) return Optional.empty();
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "private" -> Optional.of(SpaceUnitVisibility.PRIVATE); case "friends" -> Optional.of(SpaceUnitVisibility.FRIENDS); case "public" -> Optional.of(SpaceUnitVisibility.PUBLIC); default -> Optional.empty();
        };
    }
    private static String normalizeName(String value) {
        if (value == null) return ""; String normalized = value.trim(); return normalized.isEmpty() ? "" : normalized.substring(0, Math.min(64, normalized.length()));
    }
}
