package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Only the consumed grants persist. Active invisibility never survives logout/restart. */
public final class NexusRecoveryGraceSavedData extends SavedData {
    public record Grant(UUID player, UUID node) {
        static final Codec<Grant> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Grant::player),
                UUIDUtil.CODEC.fieldOf("node").forGetter(Grant::node)).apply(i, Grant::new));
    }
    public static final Codec<NexusRecoveryGraceSavedData> CODEC = Grant.CODEC.listOf().xmap(
            NexusRecoveryGraceSavedData::new, data -> data.consumed.stream()
                    .sorted(java.util.Comparator.comparing(g -> g.player() + ":" + g.node())).toList());
    public static final SavedDataType<NexusRecoveryGraceSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem", "nexus_recovery_grace"), NexusRecoveryGraceSavedData::new,
            CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Set<Grant> consumed;
    public NexusRecoveryGraceSavedData() { this(List.of()); }
    private NexusRecoveryGraceSavedData(List<Grant> grants) { consumed = new HashSet<>(grants); }
    public boolean consume(UUID player, UUID node) {
        if (!consumed.add(new Grant(player, node))) return false;
        setDirty();
        return true;
    }
}
