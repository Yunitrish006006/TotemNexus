package dev.totem.nexus.migration;

import dev.totem.core.api.v1.gamerule.TotemGameRuleCategories;
import dev.totem.nexus.space.NexusDistributedSpawnAuthority;
import dev.totem.nexus.space.NexusTeleportArrayExpansionRules;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;

/**
 * Decodes the two persisted DeadRecall game rules once, then writes their
 * non-default values to the canonical Nexus rules. No gameplay reads these
 * legacy rules after startup.
 */
public final class LegacyNexusGameRuleMigration {
    private static final GameRule<Boolean> LEGACY_DISTRIBUTED_SPAWNING =
            GameRuleBuilder.forBoolean(false)
                    .category(TotemGameRuleCategories.TOTEM)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            "deadrecall", "dead_recall_distributed_spawning"));
    private static final GameRule<NexusTeleportArrayExpansionRules.ExpansionMode> LEGACY_EXPANSION_MODE =
            GameRuleBuilder.forEnum(NexusTeleportArrayExpansionRules.ExpansionMode.DEFAULT)
                    .category(TotemGameRuleCategories.TOTEM)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            "deadrecall", "teleport_array_expansion_mode"));

    private LegacyNexusGameRuleMigration() {
    }

    /**
     * Registers the persisted legacy keys while the game-rule registry is
     * still mutable.  Value transfer remains deferred until the server has
     * loaded its saved rules.
     */
    public static void registerLegacyRules() {
        // Loading this class performs the one-time Fabric game-rule registration.
    }

    public static void migrate(MinecraftServer server) {
        var rules = server.overworld().getGameRules();
        if ((Boolean) rules.get(LEGACY_DISTRIBUTED_SPAWNING)) {
            rules.set(NexusDistributedSpawnAuthority.DISTRIBUTED_SPAWNING, true, server);
        }

        NexusTeleportArrayExpansionRules.ExpansionMode legacyMode = rules.get(LEGACY_EXPANSION_MODE);
        if (legacyMode != NexusTeleportArrayExpansionRules.ExpansionMode.DEFAULT
                && rules.get(NexusTeleportArrayExpansionRules.EXPANSION_MODE)
                == NexusTeleportArrayExpansionRules.ExpansionMode.DEFAULT) {
            rules.set(NexusTeleportArrayExpansionRules.EXPANSION_MODE, legacyMode, server);
        }
    }
}
