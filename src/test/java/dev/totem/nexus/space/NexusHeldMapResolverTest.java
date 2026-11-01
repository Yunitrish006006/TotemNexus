package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusHeldMapResolverTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen builtInLookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Set<?> builtInRegistryKeys = builtInLookup.listRegistryKeys().collect(Collectors.toSet());
        HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.concat(
                builtInLookup.listRegistries(),
                VanillaRegistries.createWorldLookup().listRegistries()
                        .filter(registry -> !builtInRegistryKeys.contains(registry.key()))));
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(initializer -> initializer.apply());
        Bootstrap.validate();
    }

    @Test
    void ordinaryFilledMapResolvesOnlyThroughServerNativeLookup() {
        var id = new MapId(101);
        var nativeData = map(128, -256, (byte) 2);
        var stack = filledMap(id);
        var lookups = new AtomicInteger();

        var resolved = NexusHeldMapResolver.resolve(stack, requested -> {
            lookups.incrementAndGet();
            assertEquals(id, requested);
            return nativeData;
        }).orElseThrow();

        assertEquals(id, resolved.mapId());
        assertSame(nativeData, resolved.nativeData());
        assertEquals(1, lookups.get());
    }

    @Test
    void legacySpaceUnitIdentityIsIgnoredDuringMapResolution() {
        var id = new MapId(102);
        var nativeData = map(0, 0, (byte) 4);
        var stack = filledMap(id);
        var oldUnit = UUID.randomUUID();
        assertTrue(NexusInterfaceBinding.writeIdentity(stack, oldUnit));

        var resolved = NexusHeldMapResolver.resolve(stack, requested -> nativeData).orElseThrow();

        assertEquals(id, resolved.mapId());
        assertSame(nativeData, resolved.nativeData());
        assertEquals(oldUnit, NexusInterfaceBinding.read(stack));
    }

    @Test
    void physicalCopiesWithOneMapIdResolveOneNativeRecord() {
        var id = new MapId(103);
        var nativeData = map(512, 512, (byte) 1);

        var first = NexusHeldMapResolver.resolve(filledMap(id), requested -> nativeData).orElseThrow();
        var second = NexusHeldMapResolver.resolve(filledMap(id), requested -> nativeData).orElseThrow();

        assertEquals(first.mapId(), second.mapId());
        assertSame(first.nativeData(), second.nativeData());
    }

    @Test
    void missingOrInvalidHeldMapFailsClosed() {
        var nativeData = map(0, 0, (byte) 0);
        assertTrue(NexusHeldMapResolver.resolve(null, id -> nativeData).isEmpty());
        assertTrue(NexusHeldMapResolver.resolve(ItemStack.EMPTY, id -> nativeData).isEmpty());
        assertTrue(NexusHeldMapResolver.resolve(new ItemStack(Items.MAP), id -> nativeData).isEmpty());
        assertTrue(NexusHeldMapResolver.resolve(new ItemStack(Items.FILLED_MAP), id -> nativeData).isEmpty());
        assertTrue(NexusHeldMapResolver.resolve(filledMap(new MapId(104)), id -> null).isEmpty());
        assertTrue(NexusHeldMapResolver.resolve(filledMap(new MapId(-1)), id -> nativeData).isEmpty());
        assertTrue(NexusHeldMapResolver.resolve(filledMap(new MapId(105)), null).isEmpty());
    }

    private static ItemStack filledMap(MapId id) {
        var stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        return stack;
    }

    private static MapItemSavedData map(int x, int z, byte scale) {
        return MapItemSavedData.createFresh(x, z, scale, false, false, Level.OVERWORLD);
    }
}
