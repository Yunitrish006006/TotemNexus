package com.adaptor.deadrecall.space;

import com.adaptor.deadrecall.Deadrecall;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SpaceUnitDegradationRules {
    private static final Gson GSON = new Gson();
    private static final String RULE_DIRECTORY = "deadrecall/space_unit_degradation";
    private static final Identifier RELOAD_LISTENER_ID =
            Identifier.fromNamespaceAndPath("deadrecall", "space_unit_degradation_rules");

    private static Map<Block, Block> degradationTargets = Map.of();

    private SpaceUnitDegradationRules() {
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return RELOAD_LISTENER_ID;
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                load(resourceManager);
            }
        });
    }

    public static Optional<BlockState> degradedState(BlockState state) {
        Block target = degradationTargets.get(state.getBlock());
        return target == null ? Optional.empty() : Optional.of(target.withPropertiesOf(state));
    }

    private static void load(ResourceManager resourceManager) {
        Map<Block, Block> loaded = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = resourceManager.listResources(
                RULE_DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                parseRules(resourceId, json, loaded);
            } catch (IOException | RuntimeException exception) {
                Deadrecall.LOGGER.warn("無法載入 Space Unit 石碑退化規則 {}：{}", resourceId, exception.getMessage());
            }
        }

        degradationTargets = Map.copyOf(loaded);
        Deadrecall.LOGGER.info("已載入 {} 個 Space Unit 石碑退化規則", degradationTargets.size());
    }

    private static void parseRules(Identifier resourceId, JsonObject json, Map<Block, Block> output) {
        if (json == null) {
            throw new JsonParseException("rule json is empty");
        }

        JsonArray rules = getArray(json, "rules");
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("rule must be an object");
            }
            JsonObject rule = element.getAsJsonObject();
            Block from = requireBlock(getString(rule, "from"));
            Block to = requireBlock(getString(rule, "to"));
            if (from == to) {
                throw new JsonParseException("rule maps block to itself: " + getString(rule, "from"));
            }
            output.put(from, to);
        }

        if (rules.isEmpty()) {
            Deadrecall.LOGGER.warn("Space Unit 石碑退化規則 {} 沒有任何 rule", resourceId);
        }
    }

    private static Block requireBlock(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new JsonParseException("unknown block: " + rawId);
        }
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    private static JsonArray getArray(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new JsonParseException("missing array: " + key);
        }
        return json.getAsJsonArray(key);
    }

    private static String getString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new JsonParseException("missing string: " + key);
        }
        return json.get(key).getAsString();
    }
}
