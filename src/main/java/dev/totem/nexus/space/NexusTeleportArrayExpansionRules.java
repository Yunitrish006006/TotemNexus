package dev.totem.nexus.space;

import dev.totem.core.api.v1.gamerule.TotemGameRuleCategories;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

/** Server-owned world rule selecting the bounded teleport-array expansion algorithm. */
public final class NexusTeleportArrayExpansionRules {
    public static final Identifier EXPANSION_MODE_ID =
            Identifier.fromNamespaceAndPath("deadrecall", "teleport_array_expansion_mode");
    public static final GameRule<ExpansionMode> EXPANSION_MODE =
            GameRuleBuilder.forEnum(ExpansionMode.DEFAULT)
                    .category(TotemGameRuleCategories.TOTEM)
                    .buildAndRegister(EXPANSION_MODE_ID);

    private NexusTeleportArrayExpansionRules() {
    }

    public static void register() {
        GameRuleEvents.changeCallback(EXPANSION_MODE).register(
                (mode, server) -> refreshLoadedSnapshots(server));
    }

    static ExpansionMode mode(ServerLevel level) {
        return level.getGameRules().get(EXPANSION_MODE);
    }

    private static void refreshLoadedSnapshots(MinecraftServer server) {
        NexusSpaceUnitSavedData units = server.overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        for (NexusSpaceUnitRecord unit : units.activeLodestones()) {
            ServerLevel level = server.getLevel(unit.dimension());
            if (level != null
                    && level.isLoaded(unit.pos())
                    && level.getBlockState(unit.pos()).is(Blocks.LODESTONE)) {
                units.rescanLodestone(level, unit.id());
            }
        }
    }

    public enum ExpansionMode {
        LOCAL(0),
        CENTERED(1);

        public static final ExpansionMode DEFAULT = LOCAL;
        private final int snapshotCode;

        ExpansionMode(int snapshotCode) {
            this.snapshotCode = snapshotCode;
        }

        public int snapshotCode() {
            return snapshotCode;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
