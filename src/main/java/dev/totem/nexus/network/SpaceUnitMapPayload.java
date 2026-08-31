package dev.totem.nexus.network;

import dev.totem.nexus.space.TeleportInterfaceQuotePolicy;
import dev.totem.nexus.space.TeleportInterfaceType;
import dev.totem.nexus.space.SpaceStructureSnapshot;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SpaceUnitMapPayload(
        UUID sourceUnitId,
        String sourceType,
        String sourceName,
        String sourceDimension,
        int sourceX,
        int sourceY,
        int sourceZ,
        TeleportInterfaceType interfaceType,
        int mapId,
        List<Entry> entries,
        MaterialSummary sourceMaterial)
        implements CustomPacketPayload {
    public static final Type<SpaceUnitMapPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "space_unit_map"));
    public static final int MAX_ENTRIES = 128;
    /** Sentinel used by management-only interfaces, which never carry a vanilla map identity. */
    public static final int NO_MAP_ID = -1;
    /** Bump whenever the bounded semantic wire layout changes. */
    public static final int FORMAT_VERSION = 3;
    public static final int MAX_CATALYST_BLOCKS_PER_ENDPOINT = 10_640;
    public static final int MAX_BASE_AMETHYST_COST = 64;

    public SpaceUnitMapPayload {
        if (interfaceType == null) {
            throw new IllegalArgumentException("Teleport interface type cannot be null");
        }
        if (mapId < NO_MAP_ID
                || (interfaceType == TeleportInterfaceType.FILLED_MAP) != (mapId != NO_MAP_ID)) {
            throw new IllegalArgumentException("Only a filled-map payload may carry a non-negative map ID");
        }
        if (entries == null) {
            throw new IllegalArgumentException("Space Unit map entries cannot be null");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "Space Unit map entries exceed limit: " + entries.size() + " > " + MAX_ENTRIES
            );
        }
        entries = List.copyOf(entries);
        sourceMaterial = sourceMaterial == null ? MaterialSummary.EMPTY : sourceMaterial;
    }

    public SpaceUnitMapPayload(
            UUID sourceUnitId, String sourceType, String sourceName, String sourceDimension,
            int sourceX, int sourceY, int sourceZ, TeleportInterfaceType interfaceType, int mapId,
            List<Entry> entries) {
        this(sourceUnitId, sourceType, sourceName, sourceDimension, sourceX, sourceY, sourceZ,
                interfaceType, mapId, entries, MaterialSummary.EMPTY);
    }

    public record Entry(
            UUID id,
            String type,
            String name,
            String visibility,
            boolean friendShared,
            String dimension,
            int x,
            int y,
            int z,
            double resonance,
            int tier,
            int distanceBlocks,
            int baseFoodCost,
            int finalFoodCost,
            int saturationCost,
            int hungerCost,
            int foodPointsNeeded,
            int safeFoodPointsAvailable,
            int amethystCost,
            int amethystAvailable,
            int baseAmethystCost,
            int sourceCatalysts,
            int targetCatalysts,
            int catalystDiscount,
            int basePrepareTicks,
            int prepareTicks,
            int baseMaxHorizontalDeviation,
            int maxHorizontalDeviation,
            int damageChancePercent,
            int baseStructureWearChancePercent,
            int structureWearChancePercent,
            boolean interfaceBonusActive,
            String interfaceBonusMessageKey,
            boolean favorite,
            boolean manageable,
            boolean owned,
            int administratorCount,
            int allowedPlayerCount,
            boolean canTeleport,
            String blockedReason,
            MaterialSummary material) {

        public Entry {
            requireRange("baseFoodCost", baseFoodCost, 0, TeleportInterfaceQuotePolicy.MAX_FOOD_COST);
            requireRange("finalFoodCost", finalFoodCost, 0, TeleportInterfaceQuotePolicy.MAX_FOOD_COST);
            requireReduction("food cost", baseFoodCost, finalFoodCost);
            int allocatedFoodCost = saturationCost + hungerCost + foodPointsNeeded;
            if (allocatedFoodCost != 0 && allocatedFoodCost != finalFoodCost) {
                throw new IllegalArgumentException(
                        "Final food allocation is inconsistent: final=" + finalFoodCost
                                + ", allocated=" + allocatedFoodCost
                );
            }
            requireRange("baseAmethystCost", baseAmethystCost, 0, MAX_BASE_AMETHYST_COST);
            requireRange("amethystCost", amethystCost, 0, MAX_BASE_AMETHYST_COST);
            requireRange("sourceCatalysts", sourceCatalysts,
                    -MAX_CATALYST_BLOCKS_PER_ENDPOINT, MAX_CATALYST_BLOCKS_PER_ENDPOINT);
            requireRange("targetCatalysts", targetCatalysts,
                    -MAX_CATALYST_BLOCKS_PER_ENDPOINT, MAX_CATALYST_BLOCKS_PER_ENDPOINT);
            requireRange("catalystDiscount", catalystDiscount,
                    -MAX_CATALYST_BLOCKS_PER_ENDPOINT / 2, MAX_CATALYST_BLOCKS_PER_ENDPOINT / 2);
            requireRange(
                    "basePrepareTicks",
                    basePrepareTicks,
                    0,
                    TeleportInterfaceQuotePolicy.MAX_PREPARE_TICKS
            );
            requireRange(
                    "prepareTicks",
                    prepareTicks,
                    0,
                    TeleportInterfaceQuotePolicy.MAX_PREPARE_TICKS
            );
            requireReduction("prepare ticks", basePrepareTicks, prepareTicks);
            requireRange(
                    "baseMaxHorizontalDeviation",
                    baseMaxHorizontalDeviation,
                    0,
                    TeleportInterfaceQuotePolicy.MAX_DEVIATION
            );
            requireRange(
                    "maxHorizontalDeviation",
                    maxHorizontalDeviation,
                    0,
                    TeleportInterfaceQuotePolicy.MAX_DEVIATION
            );
            requireReduction("horizontal deviation", baseMaxHorizontalDeviation, maxHorizontalDeviation);
            requireRange("damageChancePercent", damageChancePercent, 0, 60);
            requireRange(
                    "baseStructureWearChancePercent",
                    baseStructureWearChancePercent,
                    0,
                    TeleportInterfaceQuotePolicy.MAX_WEAR_CHANCE_PERCENT
            );
            requireRange(
                    "structureWearChancePercent",
                    structureWearChancePercent,
                    0,
                    TeleportInterfaceQuotePolicy.MAX_WEAR_CHANCE_PERCENT
            );
            requireReduction(
                    "structure wear chance",
                    baseStructureWearChancePercent,
                    structureWearChancePercent
            );
            if (interfaceBonusMessageKey == null
                    || interfaceBonusMessageKey.isBlank()
                    || interfaceBonusMessageKey.length() > 128) {
                throw new IllegalArgumentException("Invalid interface bonus message key");
            }
            material = material == null ? MaterialSummary.EMPTY : material;

            int expectedFinalCost = baseAmethystCost == 0
                    ? 0
                    : Math.max(1, Math.min(MAX_BASE_AMETHYST_COST, baseAmethystCost - catalystDiscount));
            if (amethystCost != expectedFinalCost) {
                throw new IllegalArgumentException(
                        "Inconsistent amethyst quote: base=" + baseAmethystCost
                                + ", discount=" + catalystDiscount
                                + ", final=" + amethystCost
                );
            }
        }

        /** Preserves the pre-material wire construction surface for existing callers. */
        public Entry(
                UUID id, String type, String name, String visibility, boolean friendShared, String dimension,
                int x, int y, int z, double resonance, int tier, int distanceBlocks,
                int baseFoodCost, int finalFoodCost, int saturationCost, int hungerCost, int foodPointsNeeded,
                int safeFoodPointsAvailable, int amethystCost, int amethystAvailable, int baseAmethystCost,
                int sourceCatalysts, int targetCatalysts, int catalystDiscount, int basePrepareTicks, int prepareTicks,
                int baseMaxHorizontalDeviation, int maxHorizontalDeviation, int damageChancePercent,
                int baseStructureWearChancePercent, int structureWearChancePercent, boolean interfaceBonusActive,
                String interfaceBonusMessageKey, boolean favorite, boolean manageable, boolean owned,
                int administratorCount, int allowedPlayerCount, boolean canTeleport, String blockedReason) {
            this(id, type, name, visibility, friendShared, dimension, x, y, z, resonance, tier, distanceBlocks,
                    baseFoodCost, finalFoodCost, saturationCost, hungerCost, foodPointsNeeded, safeFoodPointsAvailable,
                    amethystCost, amethystAvailable, baseAmethystCost, sourceCatalysts, targetCatalysts, catalystDiscount,
                    basePrepareTicks, prepareTicks, baseMaxHorizontalDeviation, maxHorizontalDeviation,
                    damageChancePercent, baseStructureWearChancePercent, structureWearChancePercent,
                    interfaceBonusActive, interfaceBonusMessageKey, favorite, manageable, owned,
                    administratorCount, allowedPlayerCount, canTeleport, blockedReason, MaterialSummary.EMPTY);
        }

        public Entry withMaterial(MaterialSummary nextMaterial) {
            return new Entry(
                    id, type, name, visibility, friendShared, dimension, x, y, z, resonance, tier, distanceBlocks,
                    baseFoodCost, finalFoodCost, saturationCost, hungerCost, foodPointsNeeded, safeFoodPointsAvailable,
                    amethystCost, amethystAvailable, baseAmethystCost, sourceCatalysts, targetCatalysts, catalystDiscount,
                    basePrepareTicks, prepareTicks, baseMaxHorizontalDeviation, maxHorizontalDeviation,
                    damageChancePercent, baseStructureWearChancePercent, structureWearChancePercent,
                    interfaceBonusActive, interfaceBonusMessageKey, favorite, manageable, owned,
                    administratorCount, allowedPlayerCount, canTeleport, blockedReason, nextMaterial
            );
        }

        /** Authoritative quote constructor before catalyst breakdown enrichment. */
        public Entry(
                UUID id,
                String type,
                String name,
                String visibility,
                boolean friendShared,
                String dimension,
                int x,
                int y,
                int z,
                double resonance,
                int tier,
                int distanceBlocks,
                int baseFoodCost,
                int finalFoodCost,
                int saturationCost,
                int hungerCost,
                int foodPointsNeeded,
                int safeFoodPointsAvailable,
                int amethystCost,
                int amethystAvailable,
                int basePrepareTicks,
                int prepareTicks,
                int baseMaxHorizontalDeviation,
                int maxHorizontalDeviation,
                int damageChancePercent,
                int baseStructureWearChancePercent,
                int structureWearChancePercent,
                boolean interfaceBonusActive,
                String interfaceBonusMessageKey,
                boolean favorite,
                boolean manageable,
                boolean owned,
                int administratorCount,
                int allowedPlayerCount,
                boolean canTeleport,
                String blockedReason) {
            this(
                    id, type, name, visibility, friendShared, dimension, x, y, z, resonance, tier,
                    distanceBlocks, baseFoodCost, finalFoodCost,
                    saturationCost, hungerCost, foodPointsNeeded, safeFoodPointsAvailable,
                    amethystCost, amethystAvailable, amethystCost, 0, 0, 0,
                    basePrepareTicks, prepareTicks,
                    baseMaxHorizontalDeviation, maxHorizontalDeviation,
                    damageChancePercent,
                    baseStructureWearChancePercent, structureWearChancePercent,
                    interfaceBonusActive, interfaceBonusMessageKey,
                    favorite, manageable, owned,
                    administratorCount, allowedPlayerCount, canTeleport, blockedReason
            );
        }

        /**
         * Compatibility constructor for call sites that have not yet populated catalyst details.
         */
        public Entry(
                UUID id,
                String type,
                String name,
                String visibility,
                boolean friendShared,
                String dimension,
                int x,
                int y,
                int z,
                double resonance,
                int tier,
                int distanceBlocks,
                int saturationCost,
                int hungerCost,
                int foodPointsNeeded,
                int safeFoodPointsAvailable,
                int amethystCost,
                int amethystAvailable,
                int prepareTicks,
                int maxHorizontalDeviation,
                int damageChancePercent,
                int structureWearChancePercent,
                boolean interfaceBonusActive,
                String interfaceBonusMessageKey,
                boolean favorite,
                boolean manageable,
                boolean owned,
                int administratorCount,
                int allowedPlayerCount,
                boolean canTeleport,
                String blockedReason) {
            this(
                    id, type, name, visibility, friendShared, dimension, x, y, z, resonance, tier,
                    distanceBlocks,
                    saturationCost + hungerCost + foodPointsNeeded,
                    saturationCost + hungerCost + foodPointsNeeded,
                    saturationCost, hungerCost, foodPointsNeeded, safeFoodPointsAvailable,
                    amethystCost, amethystAvailable, amethystCost, 0, 0, 0,
                    prepareTicks, prepareTicks,
                    maxHorizontalDeviation, maxHorizontalDeviation,
                    damageChancePercent,
                    structureWearChancePercent, structureWearChancePercent,
                    interfaceBonusActive, interfaceBonusMessageKey,
                    favorite, manageable, owned,
                    administratorCount, allowedPlayerCount, canTeleport, blockedReason
            );
        }
    }

    public static final StreamCodec<FriendlyByteBuf, SpaceUnitMapPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(FORMAT_VERSION);
                        buf.writeUUID(payload.sourceUnitId());
                        buf.writeUtf(payload.sourceType(), 32);
                        buf.writeUtf(payload.sourceName(), 128);
                        buf.writeUtf(payload.sourceDimension(), 128);
                        buf.writeInt(payload.sourceX());
                        buf.writeInt(payload.sourceY());
                        buf.writeInt(payload.sourceZ());
                        buf.writeUtf(payload.interfaceType().id(), 32);
                        buf.writeInt(payload.mapId());
                        writeMaterialSummary(buf, payload.sourceMaterial());
                        writeEntries(buf, payload.entries());
                    },
                    SpaceUnitMapPayload::readPayload
            );

    private static SpaceUnitMapPayload readPayload(FriendlyByteBuf buf) {
        int formatVersion = buf.readInt();
        if (formatVersion != FORMAT_VERSION) {
            throw new DecoderException("Unsupported Space Unit map payload version: " + formatVersion);
        }
        UUID sourceUnitId = buf.readUUID();
        String sourceType = buf.readUtf(32);
        String sourceName = buf.readUtf(128);
        String sourceDimension = buf.readUtf(128);
        int sourceX = buf.readInt();
        int sourceY = buf.readInt();
        int sourceZ = buf.readInt();
        TeleportInterfaceType interfaceType = readInterfaceType(buf);
        int mapId = buf.readInt();
        MaterialSummary sourceMaterial = readMaterialSummary(buf);
        List<Entry> entries = readEntries(buf);
        return new SpaceUnitMapPayload(
                sourceUnitId, sourceType, sourceName, sourceDimension, sourceX, sourceY, sourceZ,
                interfaceType, mapId, entries, sourceMaterial
        );
    }

    private static void writeEntries(FriendlyByteBuf buf, List<Entry> entries) {
        int size = entries.size();
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            writeEntry(buf, entries.get(i));
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("Space Unit map entry count out of range: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(readEntry(buf));
        }
        return entries;
    }

    private static void writeEntry(FriendlyByteBuf buf, Entry entry) {
        buf.writeUUID(entry.id());
        buf.writeUtf(entry.type(), 32);
        buf.writeUtf(entry.name(), 128);
        buf.writeUtf(entry.visibility(), 32);
        buf.writeBoolean(entry.friendShared());
        buf.writeUtf(entry.dimension(), 128);
        buf.writeInt(entry.x());
        buf.writeInt(entry.y());
        buf.writeInt(entry.z());
        buf.writeDouble(entry.resonance());
        buf.writeInt(entry.tier());
        buf.writeInt(entry.distanceBlocks());
        buf.writeInt(entry.baseFoodCost());
        buf.writeInt(entry.finalFoodCost());
        buf.writeInt(entry.saturationCost());
        buf.writeInt(entry.hungerCost());
        buf.writeInt(entry.foodPointsNeeded());
        buf.writeInt(entry.safeFoodPointsAvailable());
        buf.writeInt(entry.amethystCost());
        buf.writeInt(entry.amethystAvailable());
        buf.writeInt(entry.baseAmethystCost());
        buf.writeInt(entry.sourceCatalysts());
        buf.writeInt(entry.targetCatalysts());
        buf.writeInt(entry.catalystDiscount());
        buf.writeInt(entry.basePrepareTicks());
        buf.writeInt(entry.prepareTicks());
        buf.writeInt(entry.baseMaxHorizontalDeviation());
        buf.writeInt(entry.maxHorizontalDeviation());
        buf.writeInt(entry.damageChancePercent());
        buf.writeInt(entry.baseStructureWearChancePercent());
        buf.writeInt(entry.structureWearChancePercent());
        buf.writeBoolean(entry.interfaceBonusActive());
        buf.writeUtf(entry.interfaceBonusMessageKey(), 128);
        buf.writeBoolean(entry.favorite());
        buf.writeBoolean(entry.manageable());
        buf.writeBoolean(entry.owned());
        buf.writeInt(entry.administratorCount());
        buf.writeInt(entry.allowedPlayerCount());
        buf.writeBoolean(entry.canTeleport());
        buf.writeUtf(entry.blockedReason(), 128);
        writeMaterialSummary(buf, entry.material());
    }

    private static Entry readEntry(FriendlyByteBuf buf) {
        return new Entry(
                buf.readUUID(),
                buf.readUtf(32),
                buf.readUtf(128),
                buf.readUtf(32),
                buf.readBoolean(),
                buf.readUtf(128),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readDouble(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readUtf(128),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readUtf(128),
                readMaterialSummary(buf)
        );
    }

    private static void writeMaterialSummary(FriendlyByteBuf buf, MaterialSummary summary) {
        buf.writeInt(summary.rawStructuralBlocks());
        buf.writeInt(summary.effectiveCapacity());
        buf.writeInt(summary.maximumReachedDistance());
        buf.writeInt(summary.profileRevision());
        buf.writeInt(summary.stability());
        buf.writeInt(summary.arrivalAccuracy());
        buf.writeInt(summary.targetLock());
        buf.writeInt(summary.arrivalSafety());
        buf.writeInt(summary.wearResistance());
        buf.writeInt(summary.maintenanceEfficiency());
        buf.writeInt(summary.interferenceResistance());
        buf.writeInt(summary.foodEfficiency());
        buf.writeInt(summary.phaseSpeed());
        buf.writeInt(summary.cooldownRecovery());
        buf.writeInt(summary.routeLoadCapacity());
        buf.writeInt(summary.crossDimensionCatalystUnits());
        writeMaterialMap(buf, summary.familyCounts());
        writeMaterialMap(buf, summary.dimensionAffinity());
        buf.writeInt(summary.familyContributions().size());
        for (FamilyContribution contribution : summary.familyContributions()) {
            buf.writeUtf(contribution.family(), 64);
            buf.writeInt(contribution.blockCount());
            writeMaterialMap(buf, contribution.attributes());
        }
        buf.writeInt(summary.maintenanceItemCost());
        buf.writeInt(summary.maintenanceTargets().size());
        for (MaintenanceTarget target : summary.maintenanceTargets()) {
            buf.writeInt(target.x());
            buf.writeInt(target.y());
            buf.writeInt(target.z());
            buf.writeUtf(target.family(), 64);
        }
    }

    private static MaterialSummary readMaterialSummary(FriendlyByteBuf buf) {
        int rawStructuralBlocks = buf.readInt();
        int effectiveCapacity = buf.readInt();
        int maximumReachedDistance = buf.readInt();
        int profileRevision = buf.readInt();
        int stability = buf.readInt();
        int arrivalAccuracy = buf.readInt();
        int targetLock = buf.readInt();
        int arrivalSafety = buf.readInt();
        int wearResistance = buf.readInt();
        int maintenanceEfficiency = buf.readInt();
        int interferenceResistance = buf.readInt();
        int foodEfficiency = buf.readInt();
        int phaseSpeed = buf.readInt();
        int cooldownRecovery = buf.readInt();
        int routeLoadCapacity = buf.readInt();
        int crossDimensionCatalystUnits = buf.readInt();
        Map<String, Integer> familyCounts = readMaterialMap(buf);
        Map<String, Integer> dimensionAffinity = readMaterialMap(buf);
        int contributionCount = buf.readInt();
        if (contributionCount < 0 || contributionCount > MaterialSummary.MAX_FAMILY_CONTRIBUTIONS) {
            throw new DecoderException("Material family contribution count out of range: " + contributionCount);
        }
        List<FamilyContribution> familyContributions = new ArrayList<>(contributionCount);
        for (int index = 0; index < contributionCount; index++) {
            familyContributions.add(new FamilyContribution(buf.readUtf(64), buf.readInt(), readMaterialMap(buf)));
        }
        int maintenanceItemCost = buf.readInt();
        int targetCount = buf.readInt();
        if (targetCount < 0 || targetCount > MaterialSummary.MAX_MAINTENANCE_TARGETS) {
            throw new DecoderException("Maintenance target count out of range: " + targetCount);
        }
        List<MaintenanceTarget> maintenanceTargets = new ArrayList<>(targetCount);
        for (int index = 0; index < targetCount; index++) {
            maintenanceTargets.add(new MaintenanceTarget(
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(64)));
        }
        return new MaterialSummary(
                rawStructuralBlocks, effectiveCapacity, maximumReachedDistance, profileRevision,
                stability, arrivalAccuracy, targetLock, arrivalSafety, wearResistance, maintenanceEfficiency,
                interferenceResistance, foodEfficiency, phaseSpeed, cooldownRecovery, routeLoadCapacity,
                crossDimensionCatalystUnits, familyCounts, dimensionAffinity, familyContributions,
                maintenanceItemCost, maintenanceTargets
        );
    }

    private static void writeMaterialMap(FriendlyByteBuf buf, Map<String, Integer> values) {
        buf.writeInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key, 64);
            buf.writeInt(value);
        });
    }

    private static Map<String, Integer> readMaterialMap(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > MaterialSummary.MAX_MAP_ENTRIES) {
            throw new DecoderException("Material map entry count out of range: " + size);
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            int value = buf.readInt();
            if (values.putIfAbsent(key, value) != null) {
                throw new DecoderException("Duplicate material map key: " + key);
            }
        }
        return values;
    }

    private static TeleportInterfaceType readInterfaceType(FriendlyByteBuf buf) {
        String id = buf.readUtf(32);
        return TeleportInterfaceType.fromId(id)
                .orElseThrow(() -> new DecoderException("Unknown teleport interface type: " + id));
    }

    private static void requireRange(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " out of range: " + value + " (expected " + minimum + ".." + maximum + ")"
            );
        }
    }

    private static void requireReduction(String field, int baseValue, int finalValue) {
        if (finalValue > baseValue) {
            throw new IllegalArgumentException(
                    "Final " + field + " exceeds base value: base=" + baseValue + ", final=" + finalValue
            );
        }
    }

    /** Material diagnostics are server-calculated and displayed read-only in the map. */
    public record MaterialSummary(
            int rawStructuralBlocks,
            int effectiveCapacity,
            int maximumReachedDistance,
            int profileRevision,
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
            int crossDimensionCatalystUnits,
            Map<String, Integer> familyCounts,
            Map<String, Integer> dimensionAffinity,
            List<FamilyContribution> familyContributions,
            int maintenanceItemCost,
            List<MaintenanceTarget> maintenanceTargets) {
        static final int MAX_MAP_ENTRIES = 32;
        static final int MAX_FAMILY_CONTRIBUTIONS = 32;
        static final int MAX_MAINTENANCE_TARGETS = 1_330;
        private static final int MAX_RAW_STRUCTURAL_BLOCKS = 1_330;
        private static final int MAX_ATTRIBUTE_TOTAL = MAX_RAW_STRUCTURAL_BLOCKS * 8;
        public static final MaterialSummary EMPTY = new MaterialSummary(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), List.of(), 0, List.of());

        public MaterialSummary(
                int rawStructuralBlocks, int effectiveCapacity, int maximumReachedDistance, int profileRevision,
                int stability, int arrivalAccuracy, int targetLock, int arrivalSafety, int wearResistance,
                int maintenanceEfficiency, int interferenceResistance, int foodEfficiency, int phaseSpeed,
                int cooldownRecovery, int routeLoadCapacity, int crossDimensionCatalystUnits,
                Map<String, Integer> familyCounts, Map<String, Integer> dimensionAffinity) {
            this(rawStructuralBlocks, effectiveCapacity, maximumReachedDistance, profileRevision,
                    stability, arrivalAccuracy, targetLock, arrivalSafety, wearResistance, maintenanceEfficiency,
                    interferenceResistance, foodEfficiency, phaseSpeed, cooldownRecovery, routeLoadCapacity,
                    crossDimensionCatalystUnits, familyCounts, dimensionAffinity, List.of(), 0, List.of());
        }

        public MaterialSummary {
            requireMaterialRange("rawStructuralBlocks", rawStructuralBlocks, 0, MAX_RAW_STRUCTURAL_BLOCKS);
            requireMaterialRange("effectiveCapacity", effectiveCapacity, 0, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("maximumReachedDistance", maximumReachedDistance, 0, 5);
            requireMaterialRange("profileRevision", profileRevision, 0, Integer.MAX_VALUE);
            requireMaterialRange("stability", stability, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("arrivalAccuracy", arrivalAccuracy, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("targetLock", targetLock, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("arrivalSafety", arrivalSafety, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("wearResistance", wearResistance, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("maintenanceEfficiency", maintenanceEfficiency, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("interferenceResistance", interferenceResistance, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("foodEfficiency", foodEfficiency, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("phaseSpeed", phaseSpeed, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("cooldownRecovery", cooldownRecovery, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("routeLoadCapacity", routeLoadCapacity, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("crossDimensionCatalystUnits", crossDimensionCatalystUnits, -MAX_ATTRIBUTE_TOTAL, MAX_ATTRIBUTE_TOTAL);
            requireMaterialRange("maintenanceItemCost", maintenanceItemCost, 0, 64);
            familyCounts = checkedMap(familyCounts, "familyCounts");
            dimensionAffinity = checkedMap(dimensionAffinity, "dimensionAffinity");
            familyContributions = List.copyOf(familyContributions == null ? List.of() : familyContributions);
            if (familyContributions.size() > MAX_FAMILY_CONTRIBUTIONS) {
                throw new IllegalArgumentException("familyContributions exceed " + MAX_FAMILY_CONTRIBUTIONS + " entries");
            }
            maintenanceTargets = List.copyOf(maintenanceTargets == null ? List.of() : maintenanceTargets);
            if (maintenanceTargets.size() > MAX_MAINTENANCE_TARGETS) {
                throw new IllegalArgumentException("maintenanceTargets exceed " + MAX_MAINTENANCE_TARGETS + " entries");
            }
        }

        private static void requireMaterialRange(String field, int value, int minimum, int maximum) {
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(
                        "Material " + field + " out of range: " + value + " (expected " + minimum + ".." + maximum + ")"
                );
            }
        }

        public static MaterialSummary from(SpaceStructureSnapshot snapshot) {
            if (snapshot == null) {
                return EMPTY;
            }
            return new MaterialSummary(
                    snapshot.rawStructuralBlocks(),
                    snapshot.effectiveStructureCapacity(),
                    snapshot.maximumReachedDistance(),
                    snapshot.materialProfileRevision(),
                    snapshot.materialStability(),
                    snapshot.arrivalAccuracy(),
                    snapshot.targetLock(),
                    snapshot.arrivalSafety(),
                    snapshot.wearResistance(),
                    snapshot.maintenanceEfficiency(),
                    snapshot.interferenceResistance(),
                    snapshot.foodEfficiency(),
                    snapshot.phaseSpeed(),
                    snapshot.cooldownRecovery(),
                    snapshot.routeLoadCapacity(),
                    snapshot.crossDimensionCatalystUnits(),
                    snapshot.materialFamilyCounts(),
                    snapshot.dimensionAffinity(),
                    familyContributions(snapshot),
                    0,
                    List.of()
            );
        }

        public MaterialSummary withMaintenance(int itemCost, List<MaintenanceTarget> targets) {
            return new MaterialSummary(
                    rawStructuralBlocks, effectiveCapacity, maximumReachedDistance, profileRevision,
                    stability, arrivalAccuracy, targetLock, arrivalSafety, wearResistance, maintenanceEfficiency,
                    interferenceResistance, foodEfficiency, phaseSpeed, cooldownRecovery, routeLoadCapacity,
                    crossDimensionCatalystUnits, familyCounts, dimensionAffinity, familyContributions, itemCost, targets
            );
        }

        private static List<FamilyContribution> familyContributions(SpaceStructureSnapshot snapshot) {
            return snapshot.materialFamilyContributions().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new FamilyContribution(
                            entry.getKey(),
                            snapshot.materialFamilyCounts().getOrDefault(entry.getKey(), 0),
                            entry.getValue()))
                    .toList();
        }

        private static Map<String, Integer> checkedMap(Map<String, Integer> source, String name) {
            Map<String, Integer> values = source == null ? Map.of() : source;
            if (values.size() > MAX_MAP_ENTRIES) {
                throw new IllegalArgumentException(name + " exceeds " + MAX_MAP_ENTRIES + " entries");
            }
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getKey().length() > 64
                        || entry.getValue() == null || Math.abs((long) entry.getValue()) > MAX_ATTRIBUTE_TOTAL) {
                    throw new IllegalArgumentException("Invalid material map entry");
                }
            }
            return Map.copyOf(values);
        }
    }

    /** One server-computed material family row, with signed scalar contributions. */
    public record FamilyContribution(String family, int blockCount, Map<String, Integer> attributes) {
        public FamilyContribution {
            if (family == null || family.isBlank() || family.length() > 64 || blockCount < 0) {
                throw new IllegalArgumentException("Invalid material family contribution");
            }
            attributes = MaterialSummary.checkedMap(attributes, "family contribution attributes");
        }
    }

    /** A server-validated worn position that a manager may select for repair. */
    public record MaintenanceTarget(int x, int y, int z, String family) {
        public MaintenanceTarget {
            if (family == null || family.isBlank() || family.length() > 64) {
                throw new IllegalArgumentException("Invalid maintenance material family");
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
