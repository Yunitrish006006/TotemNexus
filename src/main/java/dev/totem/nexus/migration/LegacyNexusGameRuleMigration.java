package dev.totem.nexus.migration;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

/** Migrates saved keys before registry decoding, without exposing legacy world rules. */
public final class LegacyNexusGameRuleMigration {
    private LegacyNexusGameRuleMigration() { }

    public static <A> Codec<A> wrap(Codec<A> codec) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                Dynamic<T> data = new Dynamic<>(ops, input);
                data = rename(data, "deadrecall:dead_recall_distributed_spawning",
                        "totem:nexus/distributed_spawning");
                data = rename(data, "deadrecall:teleport_array_expansion_mode",
                        "totem:nexus/teleport_array_expansion_mode");
                return codec.decode(ops, data.getValue());
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }

    private static <T> Dynamic<T> rename(Dynamic<T> data, String legacy, String canonical) {
        var value = data.get(legacy).result();
        if (value.isEmpty()) return data;
        // A saved canonical value, including its default, always wins.
        if (data.get(canonical).result().isEmpty()) data = data.set(canonical, value.get());
        return data.remove(legacy);
    }
}
