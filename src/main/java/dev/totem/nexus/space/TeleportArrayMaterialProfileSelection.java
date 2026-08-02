package dev.totem.nexus.space;

/** Pure precedence rule shared by datapack profile compilation and unit tests. */
final class TeleportArrayMaterialProfileSelection {
    private TeleportArrayMaterialProfileSelection() {
    }

    /**
     * Returns a positive value only when the candidate wins. Exact selector
     * matches outrank tag matches; priority is considered only within the same
     * selector class. Zero means a deterministic-reload error is required.
     */
    static int compare(boolean candidateExact, int candidatePriority, boolean currentExact, int currentPriority) {
        if (candidateExact != currentExact) {
            return candidateExact ? 1 : -1;
        }
        return Integer.compare(candidatePriority, currentPriority);
    }

    /** Makes same-class, same-priority selection an explicit reload error. */
    static void requireUniqueWinner(int comparison, String message) {
        if (comparison == 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
