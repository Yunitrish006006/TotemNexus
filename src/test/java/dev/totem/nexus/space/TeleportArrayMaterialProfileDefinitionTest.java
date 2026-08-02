package dev.totem.nexus.space;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportArrayMaterialProfileDefinitionTest {
    @Test
    void documentCodecRoundTripsEveryDatapackFacingProfileLayer() {
        TeleportArrayMaterialProfileDefinition.Profile profile =
                new TeleportArrayMaterialProfileDefinition.Profile(
                        Identifier.fromNamespaceAndPath("deadrecall", "codec_test"),
                        "test_family",
                        new TeleportArrayMaterialProfileDefinition.Selector(
                                List.of(Identifier.fromNamespaceAndPath("minecraft", "iron_block")),
                                List.of(Identifier.fromNamespaceAndPath("deadrecall", "test_materials"))),
                        true,
                        new TeleportArrayMaterialProfileDefinition.Attributes(
                                2, 1, -2, 3, 4, 5, 6, -3, 2, 1, -1, 2, 3, -4),
                        Map.of("minecraft:the_nether", 2),
                        3,
                        false,
                        false
                );
        TeleportArrayMaterialProfileDefinition.Document document =
                new TeleportArrayMaterialProfileDefinition.Document(
                        TeleportArrayMaterialProfileDefinition.SCHEMA_VERSION,
                        List.of(profile));

        JsonObject encoded = TeleportArrayMaterialProfileDefinition.Document.CODEC
                .encodeStart(JsonOps.INSTANCE, document)
                .getOrThrow(IllegalArgumentException::new)
                .getAsJsonObject();
        TeleportArrayMaterialProfileDefinition.Document decoded =
                TeleportArrayMaterialProfileDefinition.Document.CODEC.parse(JsonOps.INSTANCE, encoded)
                        .getOrThrow(IllegalArgumentException::new);

        decoded.validate(Identifier.fromNamespaceAndPath("deadrecall", "codec_test.json"));
        assertEquals(document, decoded);
        assertEquals(-4, decoded.profiles().getFirst().compile().attributes().crossDimensionCatalystUnits());
    }

    @Test
    void codecRejectsOutOfRangeScalarsAndDimensionAffinityValues() {
        assertTrue(TeleportArrayMaterialProfileDefinition.Document.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"schema_version":1,"profiles":[{"id":"deadrecall:bad_scalar","family":"test",
                        "selector":{"blocks":["minecraft:stone"]},"attributes":{"stability":9}}]}
                        """))
                .error().isPresent());
        assertTrue(TeleportArrayMaterialProfileDefinition.Document.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"schema_version":1,"profiles":[{"id":"deadrecall:bad_affinity","family":"test",
                        "selector":{"blocks":["minecraft:stone"]},"dimension_affinity":{"minecraft:the_nether":-9}}]}
                        """))
                .error().isPresent());
    }

    @Test
    void documentValidationRejectsInvalidSchemaAndSelectorsBeforeRegistryCompilation() {
        TeleportArrayMaterialProfileDefinition.Document wrongSchema =
                new TeleportArrayMaterialProfileDefinition.Document(2, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> wrongSchema.validate(Identifier.fromNamespaceAndPath("deadrecall", "wrong.json")));

        TeleportArrayMaterialProfileDefinition.Profile missingSelector =
                new TeleportArrayMaterialProfileDefinition.Profile(
                        Identifier.fromNamespaceAndPath("deadrecall", "missing_selector"),
                        "test",
                        new TeleportArrayMaterialProfileDefinition.Selector(List.of(), List.of()),
                        true,
                        TeleportArrayMaterialProfileDefinition.Attributes.ZERO,
                        Map.of(), 0, false, false);
        assertThrows(IllegalArgumentException.class,
                () -> new TeleportArrayMaterialProfileDefinition.Document(1, List.of(missingSelector))
                        .validate(Identifier.fromNamespaceAndPath("deadrecall", "missing.json")));
    }

    @Test
    void validationKeepsOverlayCompositionBoundedToOneExactLayer() {
        TeleportArrayMaterialProfileDefinition.Profile invalidOverlay =
                new TeleportArrayMaterialProfileDefinition.Profile(
                        Identifier.fromNamespaceAndPath("deadrecall", "tag_overlay"), "test",
                        new TeleportArrayMaterialProfileDefinition.Selector(List.of(),
                                List.of(Identifier.fromNamespaceAndPath("deadrecall", "materials"))),
                        true, TeleportArrayMaterialProfileDefinition.Attributes.ZERO,
                        Map.of(), 0, true, false);
        assertThrows(IllegalArgumentException.class,
                () -> new TeleportArrayMaterialProfileDefinition.Document(1, List.of(invalidOverlay))
                        .validate(Identifier.fromNamespaceAndPath("deadrecall", "overlay.json")));

        TeleportArrayMaterialProfileDefinition.Profile misplacedReplace =
                new TeleportArrayMaterialProfileDefinition.Profile(
                        Identifier.fromNamespaceAndPath("deadrecall", "base_replace"), "test",
                        new TeleportArrayMaterialProfileDefinition.Selector(
                                List.of(Identifier.fromNamespaceAndPath("minecraft", "stone")), List.of()),
                        true, TeleportArrayMaterialProfileDefinition.Attributes.ZERO,
                        Map.of(), 0, false, true);
        assertThrows(IllegalArgumentException.class,
                () -> new TeleportArrayMaterialProfileDefinition.Document(1, List.of(misplacedReplace))
                        .validate(Identifier.fromNamespaceAndPath("deadrecall", "replace.json")));
    }

    @Test
    void stateModifierCodecUsesTheSameSignedBoundsAsAProfile() {
        TeleportArrayMaterialProfileDefinition.StateModifier modifier =
                TeleportArrayMaterialProfileDefinition.StateModifier.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{" + "\"stability\":-3,\"wear_resistance\":1}"))
                        .getOrThrow(IllegalArgumentException::new);
        assertEquals(-3, modifier.attributes().stability());
        assertTrue(TeleportArrayMaterialProfileDefinition.StateModifier.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"stability\":-9}"))
                .error().isPresent());
    }
}
