package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapDetailPersistenceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void validLegacyWithoutVersionRetainsSharingHistoryOrphansAndEmptyMaps() {
        var tag = fixture(); tag.remove("version");
        var data = decode(tag);
        var encoded = NexusMapDetailSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        var restored = NexusMapDetailSavedData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        try (var capture = restored.beginMigrationSnapshot()) {
            while (!capture.step(8)) { }
            var result = capture.result();
            assertEquals(3, result.pages().size());
            assertEquals(java.util.List.of(1, 2), result.maps().get(31));
            assertEquals(java.util.List.of(1), result.maps().get(32));
            assertEquals(java.util.List.of(), result.maps().get(33));
            assertTrue(result.pages().get(2).historical);
            assertEquals(4, result.pages().get(1).colors().get(0));
        }
    }
    @Test void duplicatePageIdsCannotOverwritePixels() {
        var tag = fixture(); tag.getListOrEmpty("pages").add(page(1, (byte) 8)); refuse(tag);
    }
    @Test void duplicateMapIdsCannotOverwriteExploration() {
        var tag = fixture(); tag.getListOrEmpty("maps").add(index(31, 3)); refuse(tag);
    }
    @Test void missingReferencesCannotSilentlyDisappear() {
        var tag = fixture(); tag.getListOrEmpty("maps").add(index(34, 999)); refuse(tag);
    }
    @Test void repeatedPageReferenceCannotInflateSharing() {
        var tag = fixture(); tag.getListOrEmpty("maps").add(index(34, 1, 1)); refuse(tag);
    }
    @Test void ambiguousFineTilesCannotChooseOnePageAndDiscardAnother() {
        var tag = fixture(); tag.getListOrEmpty("maps").add(index(34, 1, 3)); refuse(tag);
    }
    @Test void malformedPixelsAndPartialListsNeverDecodeSuccessfully() {
        var tag = fixture(); var bad = page(4, (byte) 8); bad.putByteArray("colors", new byte[3]);
        tag.getListOrEmpty("pages").add(bad); refuse(tag);
        tag = fixture(); bad = new CompoundTag(); bad.putString("map_id", "invalid");
        tag.getListOrEmpty("maps").add(bad); refuse(tag);
    }
    @Test void unsupportedVersionIsNotReinterpretedAsLegacy() {
        var tag = fixture(); tag.putInt("version", 2); refuse(tag);
    }
    private static void refuse(CompoundTag tag) {
        try {
            assertTrue(NexusMapDetailSavedData.CODEC.parse(NbtOps.INSTANCE, tag).result().isEmpty());
        } catch (IllegalArgumentException expected) { /* Structural constructor refusal is also strict. */ }
    }
    private static NexusMapDetailSavedData decode(CompoundTag tag) {
        return NexusMapDetailSavedData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
    static CompoundTag fixture() {
        var tag = new CompoundTag(); tag.putInt("version", 1);
        var pages = new ListTag(); pages.add(page(1, (byte) 4));
        var history = page(2, (byte) 8); history.putBoolean("historical", true); history.putInt("scale", 1);
        pages.add(history); pages.add(page(3, (byte) 12)); tag.put("pages", pages);
        var maps = new ListTag(); maps.add(index(31, 1, 2)); maps.add(index(32, 1)); maps.add(index(33)); tag.put("maps", maps);
        return tag;
    }
    private static CompoundTag page(int id, byte color) {
        var p = new CompoundTag(); p.putInt("id", id); p.putInt("x", 64); p.putInt("z", 64);
        p.putInt("scale", 0); p.putBoolean("historical", false);
        var colors = new byte[16384]; colors[0] = color; p.putByteArray("colors", colors); return p;
    }
    private static CompoundTag index(int id, int... refs) {
        var tag = new CompoundTag(); tag.putInt("map_id", id); var pages = new ListTag();
        for (int ref : refs) pages.add(IntTag.valueOf(ref)); tag.put("pages", pages); return tag;
    }
}
