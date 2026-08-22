package dev.totem.nexus.space;

import dev.totem.nexus.network.MaterialCatalogPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds a deterministic, read-only block reference table from the live datapack profile registry. */
public final class TeleportArrayMaterialCatalog {
    private TeleportArrayMaterialCatalog() {
    }

    public static MaterialCatalogPayload snapshot() {
        List<MaterialCatalogPayload.Entry> entries = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.asItem() == Items.AIR) {
                continue;
            }
            TeleportArrayMaterialProfile profile = TeleportArrayMaterialProfiles.profileFor(block.defaultBlockState());
            if (!profile.validStructureMaterial() && profile.attributes().isZero()) {
                continue;
            }
            entries.add(new MaterialCatalogPayload.Entry(
                    BuiltInRegistries.BLOCK.getKey(block).toString(),
                    profile.id().toString(),
                    profile.family(),
                    profile.validStructureMaterial(),
                    profile.attributes().scalarValues(),
                    profile.attributes().dimensionAffinity()
            ));
        }
        entries.sort(Comparator.comparing(MaterialCatalogPayload.Entry::blockId));
        return new MaterialCatalogPayload(TeleportArrayMaterialProfiles.revision(), List.copyOf(entries));
    }
}
