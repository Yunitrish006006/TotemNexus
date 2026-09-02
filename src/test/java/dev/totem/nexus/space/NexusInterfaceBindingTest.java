package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusInterfaceBindingTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen builtInLookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Set<?> builtInRegistryKeys = builtInLookup.listRegistryKeys().collect(Collectors.toSet());
        HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.concat(
                builtInLookup.listRegistries(),
                VanillaRegistries.createLookup().listRegistries()
                        .filter(registry -> !builtInRegistryKeys.contains(registry.key()))));
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(initializer -> initializer.apply());
        Bootstrap.validate();
    }

    @Test
    void bindingStoresOnlyUnitIdentityAndVersionWhilePreservingCustomData() {
        ItemStack book = new ItemStack(Items.BOOK);
        CompoundTag original = new CompoundTag();
        original.putString("preserved", "value");
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(original));
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000101");

        assertTrue(NexusInterfaceBinding.writeIdentity(book, unitId));
        assertEquals(unitId, NexusInterfaceBinding.read(book));
        assertEquals(NexusSpaceUnitSavedData.DATA_VERSION, NexusInterfaceBinding.readDataVersion(book));

        CompoundTag written = book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        assertEquals(Set.of("preserved", NexusInterfaceBinding.UNIT_ID_KEY, NexusInterfaceBinding.DATA_VERSION_KEY),
                written.keySet());
        assertEquals("value", written.getStringOr("preserved", ""));
        assertFalse(written.contains("player_id"));
        assertFalse(written.contains("owner"));
    }

    @Test
    void resolverCapabilitiesSeparateTeleportSelectionFromMapVisualization() {
        for (TeleportInterfaceType type : TeleportInterfaceType.values()) {
            assertTrue(type.canBind());
            assertTrue(type.canDiscover());
            assertTrue(type.canManage());
            assertTrue(type.canManageFriends());
            assertEquals(type == TeleportInterfaceType.COMPASS || type == TeleportInterfaceType.FILLED_MAP,
                    type.canSelectTeleportDestination());
            assertEquals(type == TeleportInterfaceType.FILLED_MAP, type.hasMapVisualization());
        }
        assertEquals(TeleportInterfaceType.COMPASS,
                TeleportInterfaceItemResolver.resolve(new ItemStack(Items.COMPASS)).orElseThrow().type());
        assertEquals(TeleportInterfaceType.RECOVERY_COMPASS,
                TeleportInterfaceItemResolver.resolve(new ItemStack(Items.RECOVERY_COMPASS)).orElseThrow().type());
        assertEquals(TeleportInterfaceType.BOOK,
                TeleportInterfaceItemResolver.resolve(new ItemStack(Items.BOOK)).orElseThrow().type());
        assertEquals(TeleportInterfaceItemResolver.RegistrationInputType.EMPTY_MAP,
                TeleportInterfaceItemResolver.resolveRegistrationInput(new ItemStack(Items.MAP)).orElseThrow().type());
        assertTrue(TeleportInterfaceItemResolver.resolve(new ItemStack(Items.FILLED_MAP)).isEmpty());
    }

    @Test
    void bindingRejectsMissingMalformedAndMixedVersionsButReadsLegacyCompassPair() {
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        ItemStack book = new ItemStack(Items.BOOK);
        CompoundTag missingVersion = new CompoundTag();
        missingVersion.store(NexusInterfaceBinding.UNIT_ID_KEY, net.minecraft.core.UUIDUtil.CODEC, unitId);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(missingVersion));
        assertNull(NexusInterfaceBinding.read(book));

        CompoundTag futureVersion = missingVersion.copy();
        futureVersion.putInt(NexusInterfaceBinding.DATA_VERSION_KEY, NexusSpaceUnitSavedData.DATA_VERSION + 1);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(futureVersion));
        assertNull(NexusInterfaceBinding.read(book));

        ItemStack legacyCompass = new ItemStack(Items.COMPASS);
        CompoundTag legacy = new CompoundTag();
        legacy.store("space_unit_id", net.minecraft.core.UUIDUtil.CODEC, unitId);
        legacy.putInt("space_unit_data_version", 1);
        legacyCompass.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
        assertEquals(unitId, NexusInterfaceBinding.read(legacyCompass));
        assertEquals(1, NexusInterfaceBinding.readDataVersion(legacyCompass));

        ItemStack recoveryCompass = new ItemStack(Items.RECOVERY_COMPASS);
        recoveryCompass.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
        assertNull(NexusInterfaceBinding.read(recoveryCompass));
    }

    @Test
    void plainBookUsesNormalRightClickForManualAndCrouchingRightClickForNexus() {
        ItemStack book = new ItemStack(Items.BOOK);
        var input = TeleportInterfaceItemResolver.resolveRegistrationInput(book).orElseThrow();
        assertTrue(NexusInterfaceGesturePolicy.grantsManual(book, false));
        assertFalse(NexusInterfaceGesturePolicy.routesNexus(input, false));
        assertFalse(NexusInterfaceGesturePolicy.grantsManual(book, true));
        assertTrue(NexusInterfaceGesturePolicy.routesNexus(input, true));

        ItemStack writtenBook = new ItemStack(Items.WRITTEN_BOOK);
        assertTrue(TeleportInterfaceItemResolver.resolveRegistrationInput(writtenBook).isEmpty());
    }
}
