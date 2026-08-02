package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Immutable, datapack-facing form of a teleport-array material profile.
 *
 * <p>The runtime registry resolves selector identifiers to blocks and then
 * caches a {@link TeleportArrayMaterialProfile} per block state. Keeping this
 * form identifier-only makes the resource codec safe to validate before it
 * touches a live registry.</p>
 */
public final class TeleportArrayMaterialProfileDefinition {
    public static final int SCHEMA_VERSION = 1;
    public static final Codec<Integer> PROFILE_VALUE_CODEC = Codec.intRange(
            -TeleportArrayMaterialAttributes.PROFILE_VALUE_LIMIT,
            TeleportArrayMaterialAttributes.PROFILE_VALUE_LIMIT
    );
    public static final Codec<Identifier> IDENTIFIER_CODEC = Codec.STRING.comapFlatMap(raw -> {
        Identifier id = Identifier.tryParse(raw);
        return id == null
                ? DataResult.error(() -> "Invalid resource identifier: " + raw)
                : DataResult.success(id);
    }, Identifier::toString);
    public static final Codec<Map<String, Integer>> AFFINITY_CODEC = Codec.unboundedMap(
            Codec.STRING,
            PROFILE_VALUE_CODEC
    );

    private TeleportArrayMaterialProfileDefinition() {
    }

    /** All scalar values are bounded at resource decode time, not only at quote time. */
    public record Attributes(
            int structureCapacity,
            int scanExpansionRadius,
            int stability,
            int arrivalAccuracy,
            int targetLock,
            int arrivalSafety,
            int wearResistance,
            int maintenanceEfficiency,
            int interferenceResistance,
            int foodEfficiency,
            int phaseSpeed,
            int cooldownRecovery,
            int routeLoadCapacity,
            int crossDimensionCatalystUnits) {
        public static final Attributes ZERO = new Attributes(0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0);
        public static final Codec<Attributes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PROFILE_VALUE_CODEC.optionalFieldOf("structure_capacity", 0).forGetter(Attributes::structureCapacity),
                PROFILE_VALUE_CODEC.optionalFieldOf("scan_expansion_radius", 0).forGetter(Attributes::scanExpansionRadius),
                PROFILE_VALUE_CODEC.optionalFieldOf("stability", 0).forGetter(Attributes::stability),
                PROFILE_VALUE_CODEC.optionalFieldOf("arrival_accuracy", 0).forGetter(Attributes::arrivalAccuracy),
                PROFILE_VALUE_CODEC.optionalFieldOf("target_lock", 0).forGetter(Attributes::targetLock),
                PROFILE_VALUE_CODEC.optionalFieldOf("arrival_safety", 0).forGetter(Attributes::arrivalSafety),
                PROFILE_VALUE_CODEC.optionalFieldOf("wear_resistance", 0).forGetter(Attributes::wearResistance),
                PROFILE_VALUE_CODEC.optionalFieldOf("maintenance_efficiency", 0).forGetter(Attributes::maintenanceEfficiency),
                PROFILE_VALUE_CODEC.optionalFieldOf("interference_resistance", 0).forGetter(Attributes::interferenceResistance),
                PROFILE_VALUE_CODEC.optionalFieldOf("food_efficiency", 0).forGetter(Attributes::foodEfficiency),
                PROFILE_VALUE_CODEC.optionalFieldOf("phase_speed", 0).forGetter(Attributes::phaseSpeed),
                PROFILE_VALUE_CODEC.optionalFieldOf("cooldown_recovery", 0).forGetter(Attributes::cooldownRecovery),
                PROFILE_VALUE_CODEC.optionalFieldOf("route_load_capacity", 0).forGetter(Attributes::routeLoadCapacity),
                PROFILE_VALUE_CODEC.optionalFieldOf("cross_dimension_catalyst_units", 0).forGetter(Attributes::crossDimensionCatalystUnits)
        ).apply(instance, Attributes::new));

        TeleportArrayMaterialAttributes compile(Map<String, Integer> dimensionAffinity) {
            return new TeleportArrayMaterialAttributes(
                    structureCapacity, scanExpansionRadius, stability, arrivalAccuracy, targetLock,
                    arrivalSafety, wearResistance, maintenanceEfficiency, interferenceResistance,
                    foodEfficiency, phaseSpeed, cooldownRecovery, routeLoadCapacity,
                    crossDimensionCatalystUnits, dimensionAffinity
            );
        }
    }

    /** Selector identifiers are resolved only after the whole document has decoded successfully. */
    public record Selector(List<Identifier> blocks, List<Identifier> blockTags) {
        public static final Codec<Selector> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IDENTIFIER_CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(Selector::blocks),
                IDENTIFIER_CODEC.listOf().optionalFieldOf("block_tags", List.of()).forGetter(Selector::blockTags)
        ).apply(instance, Selector::new));

        public Selector {
            blocks = List.copyOf(blocks == null ? List.of() : blocks);
            blockTags = List.copyOf(blockTags == null ? List.of() : blockTags);
        }

        void validateNonEmpty(Identifier profileId) {
            if (blocks.isEmpty() && blockTags.isEmpty()) {
                throw new IllegalArgumentException("selector requires blocks or block_tags in " + profileId);
            }
        }
    }

    /** A named, bounded state layer; copper applies these in shape, oxidation, wax order. */
    public record StateModifier(Attributes attributes) {
        public static final Codec<StateModifier> CODEC = Attributes.CODEC.xmap(StateModifier::new, StateModifier::attributes);

        public StateModifier {
            attributes = attributes == null ? Attributes.ZERO : attributes;
        }
    }

    /** The single base or exact overlay declared by one datapack entry. */
    public record Profile(
            Identifier id,
            String family,
            Selector selector,
            boolean validStructureMaterial,
            Attributes attributes,
            Map<String, Integer> dimensionAffinity,
            int priority,
            boolean overlay,
            boolean replaceBase) {
        public static final Codec<Profile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IDENTIFIER_CODEC.fieldOf("id").forGetter(Profile::id),
                Codec.STRING.fieldOf("family").forGetter(Profile::family),
                Selector.CODEC.fieldOf("selector").forGetter(Profile::selector),
                Codec.BOOL.optionalFieldOf("valid_structure_material", true).forGetter(Profile::validStructureMaterial),
                Attributes.CODEC.optionalFieldOf("attributes", Attributes.ZERO).forGetter(Profile::attributes),
                AFFINITY_CODEC.optionalFieldOf("dimension_affinity", Map.of()).forGetter(Profile::dimensionAffinity),
                PROFILE_VALUE_CODEC.optionalFieldOf("priority", 0).forGetter(Profile::priority),
                Codec.BOOL.optionalFieldOf("overlay", false).forGetter(Profile::overlay),
                Codec.BOOL.optionalFieldOf("replace_base", false).forGetter(Profile::replaceBase)
        ).apply(instance, Profile::new));

        public Profile {
            family = family == null ? "" : family;
            selector = selector == null ? new Selector(List.of(), List.of()) : selector;
            attributes = attributes == null ? Attributes.ZERO : attributes;
            dimensionAffinity = Map.copyOf(dimensionAffinity == null ? Map.of() : dimensionAffinity);
        }

        void validate() {
            if (family.isBlank()) {
                throw new IllegalArgumentException("missing family in " + id);
            }
            selector.validateNonEmpty(id);
            if (overlay && (!selector.blockTags().isEmpty() || selector.blocks().isEmpty())) {
                throw new IllegalArgumentException("overlay requires exact selector.blocks in " + id);
            }
            if (!overlay && replaceBase) {
                throw new IllegalArgumentException("replace_base requires overlay in " + id);
            }
        }

        TeleportArrayMaterialProfile compile() {
            return new TeleportArrayMaterialProfile(id, family, validStructureMaterial,
                    attributes.compile(dimensionAffinity));
        }
    }

    /** Whole resource document, deliberately decoded before any live registry mutation. */
    public record Document(int schemaVersion, List<Profile> profiles) {
        public static final Codec<Document> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("schema_version").forGetter(Document::schemaVersion),
                Profile.CODEC.listOf().fieldOf("profiles").forGetter(Document::profiles)
        ).apply(instance, Document::new));

        public Document {
            profiles = List.copyOf(profiles == null ? List.of() : profiles);
        }

        public void validate(Identifier resourceId) {
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported or missing schema_version in " + resourceId);
            }
            profiles.forEach(Profile::validate);
        }
    }

    /** A serializable description of a compiled profile, suitable for diagnostics and future snapshots. */
    public record CompiledProfile(Identifier id, String family, boolean validStructureMaterial,
                                  Attributes attributes, Map<String, Integer> dimensionAffinity) {
        public static final Codec<CompiledProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IDENTIFIER_CODEC.fieldOf("id").forGetter(CompiledProfile::id),
                Codec.STRING.fieldOf("family").forGetter(CompiledProfile::family),
                Codec.BOOL.fieldOf("valid_structure_material").forGetter(CompiledProfile::validStructureMaterial),
                Attributes.CODEC.fieldOf("attributes").forGetter(CompiledProfile::attributes),
                AFFINITY_CODEC.optionalFieldOf("dimension_affinity", Map.of()).forGetter(CompiledProfile::dimensionAffinity)
        ).apply(instance, CompiledProfile::new));

        public CompiledProfile {
            family = family == null ? "neutral" : family;
            attributes = attributes == null ? Attributes.ZERO : attributes;
            dimensionAffinity = Map.copyOf(dimensionAffinity == null ? Map.of() : dimensionAffinity);
        }
    }
}
