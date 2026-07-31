package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persisted structural quote data embedded in {@code deadrecall:space_units}. */
public record SpaceStructureSnapshot(double completeness, double symmetry, double resonance, double interference,
                                     double environmentStability, double wear, int tier, int amethystCatalystBlocks) {
    public SpaceStructureSnapshot(
            double completeness,
            double symmetry,
            double resonance,
            double interference,
            double environmentStability,
            double wear,
            int tier) {
        this(completeness, symmetry, resonance, interference, environmentStability, wear, tier, 0);
    }

    public static final SpaceStructureSnapshot EMPTY = new SpaceStructureSnapshot(0, 0, 0, 0, 0, 0, 0, 0);
    public static final Codec<SpaceStructureSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("completeness", 0D).forGetter(SpaceStructureSnapshot::completeness),
            Codec.DOUBLE.optionalFieldOf("symmetry", 0D).forGetter(SpaceStructureSnapshot::symmetry),
            Codec.DOUBLE.optionalFieldOf("resonance", 0D).forGetter(SpaceStructureSnapshot::resonance),
            Codec.DOUBLE.optionalFieldOf("interference", 0D).forGetter(SpaceStructureSnapshot::interference),
            Codec.DOUBLE.optionalFieldOf("environment_stability", 0D).forGetter(SpaceStructureSnapshot::environmentStability),
            Codec.DOUBLE.optionalFieldOf("wear", 0D).forGetter(SpaceStructureSnapshot::wear),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(SpaceStructureSnapshot::tier),
            Codec.INT.optionalFieldOf("amethyst_catalyst_blocks", 0).forGetter(SpaceStructureSnapshot::amethystCatalystBlocks)
    ).apply(instance, SpaceStructureSnapshot::new));
}
