package dev.totem.nexus.space;

import java.util.UUID;

/** Seeking has no deadline; exact recovery starts one non-refreshable three-second suffix. */
public record RecoveryGraceState(UUID playerId, UUID nodeId, UUID backpackId, long arrivalTick,
                                 long deadlineTick, boolean recovered) {
    public static final long RECOVERED_TICKS = 20 * 3;
    public static RecoveryGraceState arrive(UUID player, UUID node, UUID backpack, long tick) {
        return new RecoveryGraceState(player, node, backpack, tick, Long.MAX_VALUE, false);
    }
    public RecoveryGraceState recover(UUID player, UUID node, UUID backpack, long tick) {
        if (recovered || !playerId.equals(player) || !nodeId.equals(node)
                || !java.util.Objects.equals(backpackId, backpack) || expired(tick)) return this;
        return new RecoveryGraceState(playerId, nodeId, backpackId, arrivalTick,
                tick + RECOVERED_TICKS, true);
    }
    public boolean expired(long tick) { return recovered && tick >= deadlineTick; }
    public boolean accepts(NexusSpaceUnitRecord node) {
        return node != null && node.id().equals(nodeId) && node.type() == SpaceUnitType.DEATH
                && node.owner().equals(playerId)
                && (recovered ? node.status() == SpaceUnitStatus.DISABLED : node.status() == SpaceUnitStatus.ACTIVE)
                && (recovered || java.util.Objects.equals(node.backpackId().orElse(null), backpackId));
    }
}
