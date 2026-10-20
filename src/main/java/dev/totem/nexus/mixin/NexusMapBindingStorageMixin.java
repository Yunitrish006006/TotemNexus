package dev.totem.nexus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.totem.nexus.space.NexusMapBindingStorage;
import dev.totem.nexus.space.NexusMapBindingSavedData;
import dev.totem.nexus.space.NexusMapMigrationOwner;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.file.Path;

/** Leave Mojang's cache intact; strictly load Nexus bindings and legacy terrain on misses. */
@Mixin(SavedDataStorage.class)
public abstract class NexusMapBindingStorageMixin implements NexusMapMigrationOwner.StorageAccess, NexusMapBindingStorage.TerrainFailureAccess {
    @Shadow @Final private HolderLookup.Provider registries;
    @Shadow @Final private com.mojang.datafixers.DataFixer fixerUpper;
    @Shadow @Final private java.util.Map<SavedDataType<?>, java.util.Optional<SavedData>> cache;
    @Shadow private Path getDataFile(Identifier id) { throw new AssertionError(); }
    @Unique private volatile NexusMapMigrationOwner totem$migration;
    @Unique private NexusMapBindingStorage.ReadFailure totem$terrainFailure;
    @Override public NexusMapBindingStorage.ReadFailure totem$terrainFailure() { return totem$terrainFailure; }
    @Override public void totem$terrainFailure(NexusMapBindingStorage.ReadFailure failure) { totem$terrainFailure = failure; }
    @Override public SavedData totem$cachedData(SavedDataType<?> type) { return cache.getOrDefault(type, java.util.Optional.empty()).orElse(null); }
    @Override public dev.totem.nexus.space.NexusMapDiskSourceVerifier.Source totem$sourceFile(SavedDataType<?> type) {
        return new dev.totem.nexus.space.NexusMapDiskSourceVerifier.Source(getDataFile(type.id()), type.dataFixType(), fixerUpper);
    }

    @Override public NexusMapMigrationOwner totem$mapMigrationOwner(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Migration owner requires server thread");
        if (totem$migration == null) totem$migration = new NexusMapMigrationOwner(
                (SavedDataStorage) (Object) this, server, getDataFile(NexusMapBindingSavedData.TYPE.id()), registries);
        totem$migration.checkServer(server);
        return totem$migration;
    }
    @Unique private static boolean totem$isCanonical(SavedDataType<?> type) {
        return type.id().equals(NexusMapBindingSavedData.TYPE.id());
    }

    @WrapMethod(method = "get")
    private <T extends SavedData> T totem$ownedRead(SavedDataType<T> type, Operation<T> original) {
        T result = original.call(type);
        if (totem$migration != null && totem$isCanonical(type)) totem$migration.afterGet(result);
        return result;
    }
    @WrapMethod(method = "set")
    private <T extends SavedData> void totem$ownedSet(SavedDataType<T> type, T value, Operation<Void> original) {
        if (totem$migration != null && totem$isCanonical(type)) totem$migration.beforeSet();
        original.call(type, value);
    }
    @WrapMethod(method = "scheduleSave")
    private java.util.concurrent.CompletableFuture<?> totem$retryDeferredSave(Operation<java.util.concurrent.CompletableFuture<?>> original) {
        if (totem$migration != null) totem$migration.beforeSave();
        return original.call();
    }
    @WrapMethod(method = "encodeUnchecked")
    private <T extends SavedData> CompoundTag totem$tagSnapshot(SavedDataType<T> type, SavedData value,
                                                              RegistryOps<Tag> ops, Operation<CompoundTag> original) {
        CompoundTag result = original.call(type, value, ops);
        return totem$migration != null && totem$isCanonical(type) ? totem$migration.stampSnapshot(value, result) : result;
    }
    @WrapMethod(method = "tryWrite")
    private void totem$serializeBindingIO(SavedDataType<?> type, CompoundTag tag, Operation<Void> original) {
        if (totem$migration != null && totem$isCanonical(type)) totem$migration.nativeWrite(tag, () -> original.call(type, tag));
        else original.call(type, tag);
    }
    @WrapMethod(method = "close")
    private void totem$drainMigration(Operation<Void> original) {
        if (totem$migration != null) totem$migration.closeForStorage();
        original.call();
    }

    @WrapMethod(method = "readSavedData")
    private <T extends SavedData> T totem$strictMapBindingRead(SavedDataType<T> type, Operation<T> original) {
        return NexusMapBindingStorage.handles(type)
                ? NexusMapBindingStorage.readStrict((SavedDataStorage) (Object) this, type, getDataFile(type.id()), registries)
                : original.call(type);
    }
}
