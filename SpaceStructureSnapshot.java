package com.adaptor.deadrecall.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SpaceStructureSnapshot(
        double completeness,
        double symmetry,
        double resonance,
        double interference,
        double environmentStability,
        double wear,
        int tier) {

    public static final SpaceStructureSnapshot EMPTY = new SpaceStructureSnapshot(
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            0
    );

    public static final Codec<SpaceStructureSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("completeness", 0.0D).forGetter(SpaceStructureSnapshot::completeness),
            Codec.DOUBLE.optionalFieldOf("symmetry", 0.0D).forGetter(SpaceStructureSnapshot::symmetry),
            Codec.DOUBLE.optionalFieldOf("resonance", 0.0D).forGetter(SpaceStructureSnapshot::resonance),
            Codec.DOUBLE.optionalFieldOf("interference", 0.0D).forGetter(SpaceStructureSnapshot::interference),
            Codec.DOUBLE.optionalFieldOf("environment_stability", 0.0D).forGetter(SpaceStructureSnapshot::environmentStability),
            Codec.DOUBLE.optionalFieldOf("wear", 0.0D).forGetter(SpaceStructureSnapshot::wear),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(SpaceStructureSnapshot::tier)
    ).apply(instance, SpaceStructureSnapshot::new));
}
