package dev.totem.nexus.space;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads exact-block teleport-array profiles from server datapacks.  A failed
 * reload is atomic: the previously compiled profile map remains live.
 */
public final class TeleportArrayMaterialProfiles {
    private static final Logger LOGGER = LoggerFactory.getLogger("totem-nexus");
    private static final String PROFILE_DIRECTORY = "teleport_array_material_profiles";
    private static final Identifier RELOAD_LISTENER_ID =
            Identifier.fromNamespaceAndPath("deadrecall", "teleport_array_material_profiles");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private static volatile CompiledProfileRegistry registry = CompiledProfileRegistry.EMPTY;
    private static final Map<BlockState, TeleportArrayMaterialProfile> compiledByState = new ConcurrentHashMap<>();
    private static final Set<Identifier> loggedLegacyFallbacks = ConcurrentHashMap.newKeySet();
    private static volatile long revision;

    private TeleportArrayMaterialProfiles() {
    }

    public static void registerReloadListener() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return RELOAD_LISTENER_ID;
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                TeleportArrayMaterialProfiles.reload(resourceManager);
            }
        });
    }

    public static TeleportArrayMaterialProfile profileFor(BlockState state) {
        if (state == null) {
            return TeleportArrayMaterialProfile.NEUTRAL;
        }
        return compiledByState.computeIfAbsent(state, TeleportArrayMaterialProfiles::compileState);
    }

    public static long revision() {
        return revision;
    }

    /** Emits one actionable warning per legacy structural block rather than silently changing its balance. */
    static void logLegacyFallback(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (loggedLegacyFallbacks.add(id)) {
            LOGGER.warn("傳送陣結構方塊 {} 沒有材料 profile，使用 neutral 相容 profile", id);
        }
    }

    private static TeleportArrayMaterialProfile compileState(BlockState state) {
        TeleportArrayMaterialProfile base = registry.baseFor(state);
        return registry.overlayFor(state, copperStateAdjusted(state, base));
    }

    /** Copper profile composition is intentionally fixed: shape, oxidation, then wax. */
    private static TeleportArrayMaterialProfile copperStateAdjusted(BlockState state, TeleportArrayMaterialProfile base) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"minecraft".equals(blockId.getNamespace()) || !base.family().equals("copper")) {
            return base;
        }
        String path = blockId.getPath();
        TeleportArrayMaterialAttributes modifier = TeleportArrayMaterialAttributes.ZERO;
        if (path.startsWith("waxed_")) {
            path = path.substring("waxed_".length());
        }
        if (path.startsWith("exposed_")) {
            modifier = modifier.plus(copperOxidationModifier(1));
        } else if (path.startsWith("weathered_")) {
            modifier = modifier.plus(copperOxidationModifier(2));
        } else if (path.startsWith("oxidized_")) {
            modifier = modifier.plus(copperOxidationModifier(3));
        }
        if (blockId.getPath().startsWith("waxed_")) {
            modifier = modifier.plus(new TeleportArrayMaterialAttributes(
                    0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, Map.of()));
        }
        return modifier.isZero()
                ? base
                : new TeleportArrayMaterialProfile(base.id(), base.family(), base.validStructureMaterial(),
                base.attributes().plus(modifier));
    }

    private static TeleportArrayMaterialAttributes copperOxidationModifier(int level) {
        return new TeleportArrayMaterialAttributes(
                0, -level, -level, -level, 0, 0, -level, -level, 0, 0, 0, 0, 0, 0, Map.of());
    }

    private static void reload(ResourceManager resourceManager) {
        try {
            List<ProfileRule> loaded = new ArrayList<>();
            Map<Identifier, Resource> resources = resourceManager.listResources(
                    PROFILE_DIRECTORY,
                    id -> id.getPath().endsWith(".json")
            );
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    parseResource(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject(), loaded);
                }
            }
            CompiledProfileRegistry nextRegistry = CompiledProfileRegistry.compile(loaded);
            registry = nextRegistry;
            compiledByState.clear();
            loggedLegacyFallbacks.clear();
            revision++;
            LOGGER.info("已載入 {} 個傳送陣材料 profile（revision {}）", nextRegistry.ruleCount(), revision);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("傳送陣材料 profile 載入失敗，保留 revision {}：{}", revision, exception.getMessage());
        }
    }

    private static void parseResource(
            Identifier resourceId,
            JsonObject json,
            List<ProfileRule> output) {
        if (json == null) {
            throw new JsonParseException("profile document must be an object in " + resourceId);
        }
        TeleportArrayMaterialProfileDefinition.Document document =
                TeleportArrayMaterialProfileDefinition.Document.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(message -> new JsonParseException(resourceId + ": " + message));
        try {
            document.validate(resourceId);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException(exception.getMessage());
        }
        JsonArray rawProfiles = json.getAsJsonArray("profiles");
        for (int profileIndex = 0; profileIndex < document.profiles().size(); profileIndex++) {
            TeleportArrayMaterialProfileDefinition.Profile entry = document.profiles().get(profileIndex);
            if (entry.overlay() && rawProfiles.get(profileIndex).getAsJsonObject().has("valid_structure_material")) {
                throw new JsonParseException("overlay cannot declare valid_structure_material in " + entry.id());
            }
            List<Block> blocks = entry.selector().blocks().stream()
                    .map(id -> requireBlock(id.toString()))
                    .toList();
            List<TagKey<Block>> tags = entry.selector().blockTags().stream()
                    .map(id -> TagKey.create(Registries.BLOCK, id))
                    .toList();
            output.add(new ProfileRule(
                    entry.compile(),
                    blocks,
                    tags,
                    entry.priority(),
                    entry.overlay(),
                    entry.overlay() && entry.replaceBase()
            ));
        }
    }

    private static Block requireBlock(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new JsonParseException("unknown block: " + rawId);
        }
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    /**
     * Immutable reload product. Exact blocks outrank a matching block tag;
     * priority only applies inside that selector class, so resource iteration
     * order cannot alter a server's balance.
     */
    static record CompiledProfileRegistry(List<ProfileRule> bases, List<ProfileRule> overlays) {
        private static final CompiledProfileRegistry EMPTY = new CompiledProfileRegistry(List.of(), List.of());

        CompiledProfileRegistry {
            bases = List.copyOf(bases);
            overlays = List.copyOf(overlays);
        }

        static CompiledProfileRegistry compile(List<ProfileRule> rules) {
            List<ProfileRule> bases = rules.stream().filter(rule -> !rule.overlay()).toList();
            List<ProfileRule> overlays = rules.stream().filter(ProfileRule::overlay).toList();
            CompiledProfileRegistry compiled = new CompiledProfileRegistry(bases, overlays);
            compiled.validateSelection();
            compiled.validateCopperMatrix();
            return compiled;
        }

        int ruleCount() {
            return bases.size() + overlays.size();
        }

        TeleportArrayMaterialProfile baseFor(BlockState state) {
            ProfileRule winner = null;
            for (ProfileRule candidate : bases) {
                if (!candidate.matches(state)) {
                    continue;
                }
                if (winner == null || candidate.scoreAgainst(winner, state.getBlock()) > 0) {
                    winner = candidate;
                }
            }
            return winner == null ? TeleportArrayMaterialProfile.NEUTRAL : winner.profile();
        }

        TeleportArrayMaterialProfile overlayFor(BlockState state, TeleportArrayMaterialProfile base) {
            ProfileRule winner = null;
            for (ProfileRule candidate : overlays) {
                if (!candidate.exactBlocks().contains(state.getBlock())) {
                    continue;
                }
                if (winner == null || candidate.priority() > winner.priority()) {
                    winner = candidate;
                }
            }
            if (winner == null) {
                return base;
            }
            if (winner.replaceBase()) {
                return new TeleportArrayMaterialProfile(
                        winner.profile().id(),
                        winner.profile().family(),
                        base.validStructureMaterial(),
                        winner.profile().attributes());
            }
            return new TeleportArrayMaterialProfile(
                    base.id(), base.family(), base.validStructureMaterial(),
                    base.attributes().plus(winner.profile().attributes()));
        }

        private void validateSelection() {
            for (Block block : BuiltInRegistries.BLOCK) {
                List<ProfileRule> exactBases = new ArrayList<>();
                List<ProfileRule> tagBases = new ArrayList<>();
                for (ProfileRule candidate : bases) {
                    if (!candidate.matches(block.defaultBlockState())) {
                        continue;
                    }
                    (candidate.exactBlocks().contains(block) ? exactBases : tagBases).add(candidate);
                }
                validateHighestPriority(block, "base", exactBases.isEmpty() ? tagBases : exactBases);
                validateHighestPriority(block, "overlay", overlays.stream()
                        .filter(candidate -> candidate.exactBlocks().contains(block))
                        .toList());
            }
        }

        /** Only a tie among the candidates that can actually win makes a reload ambiguous. */
        private static void validateHighestPriority(Block block, String kind, List<ProfileRule> candidates) {
            ProfileRule winner = null;
            for (ProfileRule candidate : candidates) {
                if (winner == null) {
                    winner = candidate;
                    continue;
                }
                int comparison = Integer.compare(candidate.priority(), winner.priority());
                TeleportArrayMaterialProfileSelection.requireUniqueWinner(comparison,
                        "same-priority " + kind + " profile tie for " + BuiltInRegistries.BLOCK.getKey(block) + ": "
                                + winner.profile().id() + " and " + candidate.profile().id());
                if (comparison > 0) {
                    winner = candidate;
                }
            }
        }

        /** The built-in balance explicitly promises all five copper shapes × oxidation × wax. */
        private void validateCopperMatrix() {
            String[] shapes = {"copper_block", "cut_copper", "chiseled_copper", "copper_grate", "copper_bulb"};
            String[] oxidation = {"", "exposed_", "weathered_", "oxidized_"};
            String[] wax = {"", "waxed_"};
            for (String shape : shapes) {
                for (String oxidationPrefix : oxidation) {
                    for (String waxPrefix : wax) {
                        Identifier id = Identifier.fromNamespaceAndPath("minecraft",
                                copperBlockId(shape, oxidationPrefix, waxPrefix));
                        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                            throw new JsonParseException("missing vanilla copper matrix block: " + id);
                        }
                        Block block = BuiltInRegistries.BLOCK.getValue(id);
                        TeleportArrayMaterialProfile compiled = overlayFor(
                                block.defaultBlockState(),
                                copperStateAdjusted(block.defaultBlockState(), baseFor(block.defaultBlockState())));
                        if (!compiled.validStructureMaterial() || !"copper".equals(compiled.family())) {
                            throw new JsonParseException("copper matrix did not resolve exactly once: " + id);
                        }
                    }
                }
            }
        }

        private static String copperBlockId(String shape, String oxidationPrefix, String waxPrefix) {
            if (!"copper_block".equals(shape)) {
                return waxPrefix + oxidationPrefix + shape;
            }
            if (oxidationPrefix.isEmpty()) {
                return waxPrefix + "copper_block";
            }
            return waxPrefix + oxidationPrefix + "copper";
        }
    }

    static record ProfileRule(
            TeleportArrayMaterialProfile profile,
            List<Block> exactBlocks,
            List<TagKey<Block>> blockTags,
            int priority,
            boolean overlay,
            boolean replaceBase) {
        ProfileRule {
            exactBlocks = List.copyOf(exactBlocks);
            blockTags = List.copyOf(blockTags);
        }

        boolean matches(BlockState state) {
            if (exactBlocks.contains(state.getBlock())) {
                return true;
            }
            return blockTags.stream().anyMatch(state::is);
        }

        int scoreAgainst(ProfileRule other, Block block) {
            return TeleportArrayMaterialProfileSelection.compare(
                    exactBlocks.contains(block), priority,
                    other.exactBlocks().contains(block), other.priority());
        }
    }
}
