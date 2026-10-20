package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Strict cache-miss reads for map bindings and legacy terrain, preserving native IO/data fixing. */
public final class NexusMapBindingStorage {
    private NexusMapBindingStorage() {}
    public interface TerrainFailureAccess {
        ReadFailure totem$terrainFailure();
        void totem$terrainFailure(ReadFailure failure);
    }
    public static final class ReadFailure extends IllegalStateException {
        ReadFailure(SavedDataType<?> type, Throwable cause) {
            super("Refusing to recreate unreadable Nexus map "
                    + (type.codec() == NexusMapDetailSavedData.CODEC ? "terrain" : "bindings") + ": " + type.id(), cause);
        }
    }

    public static boolean handles(SavedDataType<?> type) {
        return type.codec() == NexusMapBindingSavedData.CODEC || type.codec() == NexusMapDetailSavedData.CODEC;
    }

    /**
     * Preserves the native DataFixer/registry pipeline, but never accepts partial
     * decode or turns an existing unreadable file into permission to recreate it.
     * The caller must let failures escape outside vanilla readSavedData's catch.
     */
    public static <T extends SavedData> T readStrict(SavedDataStorage storage, SavedDataType<T> type,
                                                    Path file, HolderLookup.Provider registries) {
        if (!handles(type)) throw new IllegalArgumentException("Not Nexus map persistence data");
        final boolean witnessed;
        try {
            witnessed = type.id().equals(NexusMapBindingSavedData.TYPE.id()) && NexusMapBindingWitness.present(file);
        } catch (IOException | RuntimeException failure) {
            throw refused(type, failure);
        }
        final BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            if (witnessed) throw refused(type, absent);
            return null;
        } catch (IOException | RuntimeException failure) {
            throw refused(type, failure);
        }
        if (!attributes.isRegularFile()) throw refused(type, null);
        try {
            var envelope = storage.readTagFromDisk(file, type.dataFixType(),
                    SharedConstants.getCurrentVersion().dataVersion().version());
            if (!(envelope.get("data") instanceof CompoundTag data)) throw refused(type, null);
            // resultOrPartial would allow a subset of bindings to erase rejected rows.
            return type.codec().parse(registries.createSerializationContext(NbtOps.INSTANCE), data)
                    .result().orElseThrow(() -> refused(type, null));
        } catch (IOException | RuntimeException failure) {
            // Includes native ReportedNbtException: malformed NBT is not always an IOException.
            // Disappearance after the metadata check is a failed read, not absence.
            throw refused(type, failure);
        }
    }

    private static ReadFailure refused(SavedDataType<?> type, Throwable cause) {
        return new ReadFailure(type, cause);
    }
}
