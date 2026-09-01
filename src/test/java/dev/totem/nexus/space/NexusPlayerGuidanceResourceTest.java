package dev.totem.nexus.space;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusPlayerGuidanceResourceTest {
    private static final List<String> REQUIRED_GUIDANCE_KEYS = List.of(
            "message.deadrecall.space_unit.map_need_bound_interface",
            "message.deadrecall.space_unit.registration_item_changed",
            "message.deadrecall.space_unit.interface.map_source_mismatch",
            "message.deadrecall.space_unit.interface.management_unavailable",
            "message.deadrecall.space_unit.map_unnamed_nexus",
            "book.deadrecall.nexus_diagram.interfaces_bind_manage",
            "book.deadrecall.nexus_diagram.book_normal_manual",
            "book.deadrecall.nexus_diagram.book_sneak_nexus",
            "book.deadrecall.nexus_diagram.empty_map_create",
            "book.deadrecall.nexus_diagram.exact_center",
            "book.deadrecall.nexus_diagram.scale_anchor",
            "book.deadrecall.nexus_diagram.named_markers",
            "book.deadrecall.nexus_diagram.no_player_edges",
            "message.deadrecall.space_unit.array_preview_hint",
            "message.deadrecall.space_unit.build_sites_show",
            "message.deadrecall.space_unit.build_sites_hide",
            "message.deadrecall.space_unit.build_sites_hint",
            "gamerule.deadrecall.teleport_array_expansion_mode",
            "gamerule.deadrecall.teleport_array_expansion_mode.description",
            "gamerule.deadrecall.teleport_array_expansion_mode.local",
            "gamerule.deadrecall.teleport_array_expansion_mode.centered"
    );

    @Test
    void englishAndTraditionalChineseShipTheSameCompleteGuidanceSurface() {
        JsonObject english = language("en_us");
        JsonObject traditionalChinese = language("zh_tw");

        assertEquals(english.keySet(), traditionalChinese.keySet());
        for (String key : REQUIRED_GUIDANCE_KEYS) {
            assertTrue(english.has(key), "Missing English guidance key: " + key);
            assertTrue(traditionalChinese.has(key), "Missing Traditional Chinese guidance key: " + key);
            assertFalse(english.get(key).getAsString().isBlank(), "Blank English guidance: " + key);
            assertFalse(traditionalChinese.get(key).getAsString().isBlank(),
                    "Blank Traditional Chinese guidance: " + key);
        }
    }

    @Test
    void currentInterfaceMessagesDescribeNativeBookAndNexusMapSemantics() {
        JsonObject english = language("en_us");
        JsonObject traditionalChinese = language("zh_tw");

        assertEquals("Book", text(english, "message.deadrecall.space_unit.interface_name.book"));
        assertEquals("普通書", text(traditionalChinese, "message.deadrecall.space_unit.interface_name.book"));
        assertTrue(text(english, "message.deadrecall.space_unit.map_need_interface").contains("Nexus map"));
        assertTrue(text(traditionalChinese, "message.deadrecall.space_unit.map_need_interface")
                .contains("Nexus 地圖"));
        assertTrue(text(english, "book.deadrecall.nexus_diagram.empty_map_create").contains("new MapId"));
        assertTrue(text(traditionalChinese, "book.deadrecall.nexus_diagram.empty_map_create")
                .contains("新 MapId"));
        assertTrue(text(english, "book.deadrecall.nexus_diagram.scale_anchor").contains("unloaded"));
        assertTrue(text(traditionalChinese, "book.deadrecall.nexus_diagram.scale_anchor")
                .contains("未載入"));
        assertTrue(text(english, "message.deadrecall.space_unit.array_preview_hint").contains("until hidden"));
        assertTrue(text(traditionalChinese, "message.deadrecall.space_unit.array_preview_hint")
                .contains("直到手動隱藏"));
        assertTrue(text(english, "message.deadrecall.space_unit.build_sites_hint").contains("green"));
        assertTrue(text(traditionalChinese, "message.deadrecall.space_unit.build_sites_hint")
                .contains("綠色"));
        assertTrue(text(english, "gamerule.deadrecall.teleport_array_expansion_mode.description")
                .contains("lodestone-centered"));
        assertTrue(text(traditionalChinese, "gamerule.deadrecall.teleport_array_expansion_mode.description")
                .contains("磁石為中心"));
    }

    private static JsonObject language(String locale) {
        String path = "/assets/deadrecall/lang/" + locale + ".json";
        var stream = NexusPlayerGuidanceResourceTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing language resource: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read language resource: " + path, exception);
        }
    }

    private static String text(JsonObject language, String key) {
        assertTrue(language.has(key), "Missing translation: " + key);
        return language.get(key).getAsString();
    }
}
