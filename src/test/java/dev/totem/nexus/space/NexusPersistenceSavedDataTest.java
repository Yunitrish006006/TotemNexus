package dev.totem.nexus.space;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusPersistenceSavedDataTest {
    @Test
    void favoriteRequiresDiscoveryAndIsRemovedWithIt() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID unit = UUID.fromString("00000000-0000-0000-0000-000000000012");
        NexusSpaceDiscoverySavedData data = new NexusSpaceDiscoverySavedData();
        assertFalse(data.setFavorite(player, unit, true));
        assertTrue(data.markDiscovered(player, unit));
        assertTrue(data.setFavorite(player, unit, true));
        assertTrue(data.removeDiscovered(player, unit));
        assertFalse(data.isFavorite(player, unit));
    }

    @Test
    void legacyDiscoveryJsonLoadsAndReencodesItsStableFieldsWithDefaultVersionSemantics() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID unit = UUID.fromString("00000000-0000-0000-0000-000000000022");
        // UUIDUtil.CODEC stores UUIDs in legacy SavedData as four integer components.
        JsonObject legacy = JsonParser.parseString("""
                {"data_version":2,"players":[{"player":[0,0,0,33],"units":[[0,0,0,34]],"favorites":[[0,0,0,34]]}]}
                """).getAsJsonObject();
        NexusSpaceDiscoverySavedData restored = NexusSpaceDiscoverySavedData.CODEC.parse(JsonOps.INSTANCE, legacy)
                .getOrThrow(IllegalArgumentException::new);
        assertTrue(restored.hasDiscovered(player, unit));
        assertTrue(restored.isFavorite(player, unit));
        JsonObject reencoded = NexusSpaceDiscoverySavedData.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow(IllegalArgumentException::new).getAsJsonObject();
        assertTrue(!reencoded.has("data_version") || reencoded.get("data_version").getAsInt() == 2);
        assertTrue(reencoded.has("players") && reencoded.getAsJsonArray("players").size() == 1);
    }

}
