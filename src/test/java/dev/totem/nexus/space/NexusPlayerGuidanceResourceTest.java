package dev.totem.nexus.space;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusPlayerGuidanceResourceTest {
    private static final Pattern FORMAT_PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?[a-zA-Z]|%%");
    private static final List<String> PRESERVED_SPANISH_IDENTIFIERS = List.of("Nexus", "Space Unit", "MapId", "UUID");
    private static final List<String> REQUIRED_GUIDANCE_KEYS = List.of(
            "message.totem.space_unit.interface_source_identity",
            "message.totem.space_unit.map_need_bound_interface",
            "message.totem.space_unit.registration_item_changed",
            "message.totem.space_unit.interface.map_source_mismatch",
            "message.totem.space_unit.interface.management_unavailable",
            "message.totem.space_unit.map_unnamed_nexus",
            "book.totem.nexus_diagram.interfaces_bind_manage",
            "book.totem.nexus_diagram.book_normal_manual",
            "book.totem.nexus_diagram.book_sneak_nexus",
            "book.totem.nexus_diagram.empty_map_create",
            "book.totem.nexus_diagram.exact_center",
            "book.totem.nexus_diagram.scale_anchor",
            "book.totem.nexus_diagram.named_markers",
            "book.totem.nexus_diagram.no_player_edges",
            "message.totem.space_unit.array_preview_hint",
            "message.totem.space_unit.build_sites_show",
            "message.totem.space_unit.build_sites_hide",
            "message.totem.space_unit.build_sites_hint",
            "gamerule.totem.nexus.distributed_spawning",
            "gamerule.totem.nexus.distributed_spawning.description",
            "gamerule.totem.nexus.teleport_array_expansion_mode",
            "gamerule.totem.nexus.teleport_array_expansion_mode.description",
            "gamerule.totem.nexus.teleport_array_expansion_mode.local",
            "gamerule.totem.nexus.teleport_array_expansion_mode.centered"
    );

    @Test
    void allLocalesShipTheSameCompleteNonBlankGuidanceSurfaceWithOrderedPlaceholders() {
        JsonObject english = language("en_us");
        JsonObject traditionalChinese = language("zh_tw");
        JsonObject spanish = language("es_es");

        assertEquals(430, english.size(), "The complete English Nexus language surface must remain stable");
        assertLocaleParity(english, "en_us", traditionalChinese, "zh_tw");
        assertLocaleParity(english, "en_us", spanish, "es_es");
        assertSpanishIdentifiersArePreserved(english, spanish);
        for (String key : REQUIRED_GUIDANCE_KEYS) {
            assertTrue(english.has(key), "Missing English guidance key: " + key);
            assertTrue(traditionalChinese.has(key), "Missing Traditional Chinese guidance key: " + key);
            assertTrue(spanish.has(key), "Missing Spanish guidance key: " + key);
            assertFalse(english.get(key).getAsString().isBlank(), "Blank English guidance: " + key);
            assertFalse(traditionalChinese.get(key).getAsString().isBlank(),
                    "Blank Traditional Chinese guidance: " + key);
            assertFalse(spanish.get(key).getAsString().isBlank(), "Blank Spanish guidance: " + key);
        }
    }

    @Test
    void currentInterfaceMessagesDescribeNativeBookAndNexusMapSemantics() {
        JsonObject english = language("en_us");
        JsonObject traditionalChinese = language("zh_tw");

        assertEquals("Book", text(english, "message.totem.space_unit.interface_name.book"));
        assertEquals("普通書", text(traditionalChinese, "message.totem.space_unit.interface_name.book"));
        assertTrue(text(english, "message.totem.space_unit.map_need_interface").contains("Nexus map"));
        assertTrue(text(traditionalChinese, "message.totem.space_unit.map_need_interface")
                .contains("Nexus 地圖"));
        assertTrue(text(english, "book.totem.nexus_diagram.interfaces_bind_manage")
                .contains("Both compasses: destination list"));
        assertTrue(text(english, "book.totem.nexus_diagram.interfaces_bind_manage")
                .contains("Map: markers only"));
        assertTrue(text(traditionalChinese, "book.totem.nexus_diagram.interfaces_bind_manage")
                .contains("兩種羅盤：清單傳送"));
        assertTrue(text(traditionalChinese, "book.totem.nexus_diagram.interfaces_bind_manage")
                .contains("地圖：標記選點"));
        assertTrue(text(english, "book.totem.nexus_diagram.empty_map_create").contains("new MapId"));
        assertTrue(text(traditionalChinese, "book.totem.nexus_diagram.empty_map_create")
                .contains("新 MapId"));
        assertTrue(text(english, "book.totem.nexus_diagram.scale_anchor").contains("unloaded"));
        assertTrue(text(traditionalChinese, "book.totem.nexus_diagram.scale_anchor")
                .contains("未載入"));
        assertTrue(text(english, "message.totem.space_unit.array_preview_hint").contains("until hidden"));
        assertTrue(text(traditionalChinese, "message.totem.space_unit.array_preview_hint")
                .contains("直到手動隱藏"));
        assertTrue(text(english, "message.totem.space_unit.build_sites_hint").contains("green"));
        assertTrue(text(traditionalChinese, "message.totem.space_unit.build_sites_hint")
                .contains("綠色"));
        assertTrue(text(english, "gamerule.totem.nexus.distributed_spawning.description")
                .contains("safe spawn"));
        assertTrue(text(traditionalChinese, "gamerule.totem.nexus.distributed_spawning.description")
                .contains("安全重生位置"));
        assertTrue(text(english, "gamerule.totem.nexus.teleport_array_expansion_mode.description")
                .contains("lodestone-centered"));
        assertTrue(text(traditionalChinese, "gamerule.totem.nexus.teleport_array_expansion_mode.description")
                .contains("磁石為中心"));
    }

    private static JsonObject language(String locale) {
        String path = "/assets/totem/lang/" + locale + ".json";
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

    private static void assertLocaleParity(JsonObject base, String baseLocale, JsonObject localized, String localizedLocale) {
        assertEquals(base.keySet(), localized.keySet(),
                localizedLocale + " keys must exactly match " + baseLocale);
        for (String key : base.keySet()) {
            String baseText = text(base, key);
            String localizedText = text(localized, key);
            assertFalse(baseText.isBlank(), "Blank " + baseLocale + " translation: " + key);
            assertFalse(localizedText.isBlank(), "Blank " + localizedLocale + " translation: " + key);
            assertEquals(placeholders(baseText), placeholders(localizedText),
                    localizedLocale + " must preserve ordered placeholders for " + key);
            assertEquals(newlineCount(baseText), newlineCount(localizedText),
                    localizedLocale + " must preserve newlines for " + key);
        }
    }

    private static void assertSpanishIdentifiersArePreserved(JsonObject english, JsonObject spanish) {
        for (String key : english.keySet()) {
            String englishText = text(english, key);
            String spanishText = text(spanish, key);
            for (String identifier : PRESERVED_SPANISH_IDENTIFIERS) {
                if (englishText.contains(identifier)) {
                    assertTrue(spanishText.contains(identifier),
                            "Spanish must preserve " + identifier + " for " + key);
                }
            }
        }
    }

    private static List<String> placeholders(String value) {
        Matcher matcher = FORMAT_PLACEHOLDER.matcher(value);
        List<String> placeholders = new ArrayList<>();
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }

    private static long newlineCount(String value) {
        return value.chars().filter(character -> character == '\n').count();
    }
}
