package dev.totem.nexus.network;

import dev.totem.nexus.space.TeleportInterfaceQuotePolicy;
import dev.totem.nexus.space.TeleportInterfaceType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
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
        List<Entry> entries)
        implements CustomPacketPayload {
    public static final Type<SpaceUnitMapPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "space_unit_map"));
    public static final int MAX_ENTRIES = 128;
    public static final int MAX_CATALYST_BLOCKS_PER_ENDPOINT = 74;
    public static final int MAX_BASE_AMETHYST_COST = 64;

    public SpaceUnitMapPayload {
        if (interfaceType == null) {
            throw new IllegalArgumentException("Teleport interface type cannot be null");
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
            String blockedReason) {

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
            requireRange(
                    "sourceCatalysts",
                    sourceCatalysts,
                    0,
                    MAX_CATALYST_BLOCKS_PER_ENDPOINT
            );
            requireRange(
                    "targetCatalysts",
                    targetCatalysts,
                    0,
                    MAX_CATALYST_BLOCKS_PER_ENDPOINT
            );
            requireRange("catalystDiscount", catalystDiscount, 0, MAX_BASE_AMETHYST_COST);
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

            int maxAppliedDiscount = Math.max(0, baseAmethystCost - 1);
            if (catalystDiscount > maxAppliedDiscount) {
                throw new IllegalArgumentException(
                        "Catalyst discount exceeds payable cost: base=" + baseAmethystCost
                                + ", discount=" + catalystDiscount
                );
            }
            int expectedFinalCost = baseAmethystCost == 0
                    ? 0
                    : Math.max(1, baseAmethystCost - catalystDiscount);
            if (amethystCost != expectedFinalCost) {
                throw new IllegalArgumentException(
                        "Inconsistent amethyst quote: base=" + baseAmethystCost
                                + ", discount=" + catalystDiscount
                                + ", final=" + amethystCost
                );
            }
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
                        buf.writeUUID(payload.sourceUnitId());
                        buf.writeUtf(payload.sourceType(), 32);
                        buf.writeUtf(payload.sourceName(), 128);
                        buf.writeUtf(payload.sourceDimension(), 128);
                        buf.writeInt(payload.sourceX());
                        buf.writeInt(payload.sourceY());
                        buf.writeInt(payload.sourceZ());
                        buf.writeUtf(payload.interfaceType().id(), 32);
                        writeEntries(buf, payload.entries());
                    },
                    buf -> new SpaceUnitMapPayload(
                            buf.readUUID(),
                            buf.readUtf(32),
                            buf.readUtf(128),
                            buf.readUtf(128),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            readInterfaceType(buf),
                            readEntries(buf)
                    )
            );

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
                buf.readUtf(128)
        );
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
