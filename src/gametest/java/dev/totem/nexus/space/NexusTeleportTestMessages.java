package dev.totem.nexus.space;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Bounded, translation-key-only diagnostics for explicitly watched test players. */
public final class NexusTeleportTestMessages {
    private static final Map<UUID, List<String>> WATCHED = new LinkedHashMap<>();
    private NexusTeleportTestMessages() {}
    public static synchronized void watch(UUID player) {
        if (WATCHED.size() >= 64) WATCHED.remove(WATCHED.keySet().iterator().next());
        WATCHED.put(player, new ArrayList<>());
    }
    public static synchronized void record(UUID player, Component message) {
        var keys = WATCHED.get(player);
        if (keys != null && message.getContents() instanceof TranslatableContents contents
                && contents.getKey().startsWith("message.totem.space_unit.")) {
            if (keys.size() == 8) keys.remove(0);
            keys.add(contents.getKey());
        }
    }
    public static synchronized List<String> messages(UUID player) {
        return List.copyOf(WATCHED.getOrDefault(player, List.of()));
    }
    public static synchronized void clear(UUID player) { WATCHED.remove(player); }
}
