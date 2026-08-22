package dev.totem.nexus.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-authoritative per-block teleport-array material profiles for the client reference table. */
public record MaterialCatalogPayload(long revision, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<MaterialCatalogPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "material_catalog"));
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_ENTRIES = 4096;
    private static final int MAX_MAP_ENTRIES = 32;
    private static final int MAX_VALUE = 64;

    public MaterialCatalogPayload {
        if (revision < 0) {
            throw new IllegalArgumentException("Material catalog revision cannot be negative");
        }
        entries = List.copyOf(entries == null ? List.of() : entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Material catalog exceeds " + MAX_ENTRIES + " entries");
        }
    }

    public static final StreamCodec<FriendlyByteBuf, MaterialCatalogPayload> CODEC = StreamCodec.of(
            MaterialCatalogPayload::write,
            MaterialCatalogPayload::read
    );

    private static void write(FriendlyByteBuf buf, MaterialCatalogPayload payload) {
        buf.writeInt(FORMAT_VERSION);
        buf.writeLong(payload.revision());
        buf.writeInt(payload.entries().size());
        for (Entry entry : payload.entries()) {
            buf.writeUtf(entry.blockId(), 128);
            buf.writeUtf(entry.profileId(), 128);
            buf.writeUtf(entry.family(), 64);
            buf.writeBoolean(entry.validStructureMaterial());
            writeMap(buf, entry.attributes());
            writeMap(buf, entry.dimensionAffinity());
        }
    }

    private static MaterialCatalogPayload read(FriendlyByteBuf buf) {
        int format = buf.readInt();
        if (format != FORMAT_VERSION) {
            throw new DecoderException("Unsupported material catalog payload version: " + format);
        }
        long revision = buf.readLong();
        if (revision < 0) {
            throw new DecoderException("Negative material catalog revision");
        }
        int size = buf.readInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("Material catalog entry count out of range: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new Entry(
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readUtf(64),
                    buf.readBoolean(),
                    readMap(buf),
                    readMap(buf)
            ));
        }
        return new MaterialCatalogPayload(revision, entries);
    }

    private static void writeMap(FriendlyByteBuf buf, Map<String, Integer> values) {
        buf.writeInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key, 128);
            buf.writeInt(value);
        });
    }

    private static Map<String, Integer> readMap(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > MAX_MAP_ENTRIES) {
            throw new DecoderException("Material catalog map size out of range: " + size);
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            String key = buf.readUtf(128);
            int value = buf.readInt();
            if (Math.abs((long) value) > MAX_VALUE) {
                throw new DecoderException("Material catalog value out of range: " + value);
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new DecoderException("Duplicate material catalog key: " + key);
            }
        }
        return Map.copyOf(values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            String blockId,
            String profileId,
            String family,
            boolean validStructureMaterial,
            Map<String, Integer> attributes,
            Map<String, Integer> dimensionAffinity) {
        public Entry {
            requireId(blockId, "blockId");
            requireId(profileId, "profileId");
            if (family == null || family.isBlank() || family.length() > 64) {
                throw new IllegalArgumentException("Invalid material family");
            }
            attributes = checkedMap(attributes, "attributes");
            dimensionAffinity = checkedMap(dimensionAffinity, "dimensionAffinity");
        }

        public int attribute(String key) {
            return attributes.getOrDefault(key, 0);
        }

        private static void requireId(String value, String field) {
            if (value == null || value.isBlank() || value.length() > 128 || Identifier.tryParse(value) == null) {
                throw new IllegalArgumentException("Invalid " + field + ": " + value);
            }
        }

        private static Map<String, Integer> checkedMap(Map<String, Integer> source, String field) {
            Map<String, Integer> values = source == null ? Map.of() : source;
            if (values.size() > MAX_MAP_ENTRIES) {
                throw new IllegalArgumentException(field + " exceeds " + MAX_MAP_ENTRIES + " entries");
            }
            Map<String, Integer> checked = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key == null || key.isBlank() || key.length() > 128 || value == null
                        || Math.abs((long) value) > MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid material catalog " + field + " entry");
                }
                checked.put(key, value);
            });
            return Map.copyOf(checked);
        }
    }
}
