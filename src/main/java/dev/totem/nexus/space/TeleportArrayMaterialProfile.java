package dev.totem.nexus.space;

import net.minecraft.resources.Identifier;

/** A compiled, exact-block material profile. */
public record TeleportArrayMaterialProfile(
        Identifier id,
        String family,
        boolean validStructureMaterial,
        TeleportArrayMaterialAttributes attributes) {
    public static final TeleportArrayMaterialProfile NEUTRAL = new TeleportArrayMaterialProfile(
            Identifier.fromNamespaceAndPath("deadrecall", "neutral"),
            "neutral",
            false,
            TeleportArrayMaterialAttributes.ZERO
    );

    public TeleportArrayMaterialProfile {
        family = family == null || family.isBlank() ? "neutral" : family;
        attributes = attributes == null ? TeleportArrayMaterialAttributes.ZERO : attributes;
    }
}
