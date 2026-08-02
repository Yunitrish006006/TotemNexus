package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TeleportArrayMaterialProfilesTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unloadedRegistryUsesOneCachedNeutralCompatibilityProfile() {
        BlockState state = Blocks.COBBLESTONE.defaultBlockState();

        TeleportArrayMaterialProfile first = TeleportArrayMaterialProfiles.profileFor(state);
        TeleportArrayMaterialProfile second = TeleportArrayMaterialProfiles.profileFor(state);

        assertSame(first, second);
        assertEquals(TeleportArrayMaterialProfile.NEUTRAL, first);
    }
}
