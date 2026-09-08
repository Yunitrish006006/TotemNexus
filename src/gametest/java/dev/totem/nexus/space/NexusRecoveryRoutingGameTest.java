package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** Real SavedData/items/events around the production authority, with no client-supplied map proof. */
public final class NexusRecoveryRoutingGameTest {
    @GameTest(maxTicks = 20)
    public void mapUsesPaintedUndiscoveredSourceWithoutChangingAnchorAndRejectsForgedInputs(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var level = h.getLevel();
        BlockPos anchor = h.absolutePos(new BlockPos(2, 2, 2));
        var a = lodestone(h, p, anchor);
        var b = lodestone(h, p, anchor.offset(3, 0, 0));
        var c = lodestone(h, p, anchor.offset(5, 0, 0));
        ItemStack map = NexusMapLifecycleAuthority.createBoundMap(level, anchor, a.id(), new ItemStack(Items.MAP)).orElseThrow();
        var mapId = map.get(DataComponents.MAP_ID);
        MapItemSavedData data = MapItem.getSavedData(map, level);
        int centerX = data.centerX, centerZ = data.centerZ;
        p.setItemInHand(InteractionHand.MAIN_HAND, map);
        p.setPos(Vec3.atCenterOf(b.pos().above()));
        require(h, NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "lodestone", b.id()).isEmpty(), "Unpainted source accepted");
        Arrays.fill(data.colors, (byte) 4);
        var context = NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "lodestone", b.id()).orElseThrow();
        require(h, context.sourceId().equals(b.id()) && context.boundUnitId().equals(a.id()), "Source replaced map anchor");
        require(h, NexusInterfaceAccess.allows(p, context, c), "Map required discovery for covered destination");
        require(h, !discovery(p).hasDiscovered(p.getUUID(), c.id()), "Map silently changed discovery");
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(b.pos()), Direction.UP, b.pos(), false));
        require(h, NexusSpaceUnitAuthority.currentInterfaceContext(p).orElseThrow().sourceId().equals(b.id()), "Clicked source was not selected");
        NexusSpaceUnitAuthority.setFavorite(p, "lodestone", b.id(), c.id(), true);
        require(h, discovery(p).isFavorite(p.getUUID(), c.id()) && !discovery(p).hasDiscovered(p.getUUID(), c.id()),
                "Map favorite failed or granted compass discovery");
        require(h, map.get(DataComponents.MAP_ID).equals(mapId) && NexusInterfaceBinding.read(map).equals(a.id())
                && data.centerX == centerX && data.centerZ == centerZ, "Activation changed map identity/center");
        require(h, NexusMapLifecycleAuthority.expansionAnchorEligibility(map, level) == NexusMapLifecycleAuthority.ScaleEligibility.ALLOWED,
                "Expansion no longer validates original anchor");
        var outside = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, level.dimension(),
                new BlockPos(centerX + 64, anchor.getY(), centerZ), p.getUUID(), "Outside", SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.ACTIVE, Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1, 1);
        units(p).put(outside);
        require(h, !NexusInterfaceAccess.allows(p, context, outside), "Coverage outside accepted");
        NexusSpaceUnitAuthority.setFavorite(p, "lodestone", b.id(), outside.id(), true);
        require(h, !discovery(p).isFavorite(p.getUUID(), outside.id()), "Forged map favorite escaped coverage");
        p.setPos(Vec3.atCenterOf(outside.pos()));
        require(h, NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "lodestone", outside.id()).isEmpty(), "Outside source accepted");
        p.setPos(Vec3.atCenterOf(b.pos()));
        var foreign = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, level.dimension(), c.pos(),
                UUID.randomUUID(), "Private", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1, 1);
        units(p).put(foreign);
        require(h, !NexusInterfaceAccess.allows(p, context, foreign), "Map leaked unauthorized unit");
        ItemStack forged = map.copy(); NexusInterfaceBinding.writeIdentity(forged, b.id());
        p.setItemInHand(InteractionHand.MAIN_HAND, forged);
        require(h, NexusSpaceUnitAuthority.currentInterfaceContext(p).isEmpty(), "Forged anchor survived held validation");
        p.setItemInHand(InteractionHand.MAIN_HAND, map);
        Arrays.fill(data.colors, (byte) 0);
        require(h, !NexusInterfaceAccess.allows(p, context, c), "Stale painted coverage accepted");
        p.discard(); h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void mapPayloadMaintenanceNeverLoadsPaintedRemoteChunk(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); var level = h.getLevel();
        var anchor = lodestone(h, p, h.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack map = NexusMapLifecycleAuthority.createBoundMap(level, anchor.pos(), anchor.id(), new ItemStack(Items.MAP)).orElseThrow();
        for (int scale = 0; scale < 4; scale++) require(h,
                NexusMapLifecycleAuthority.postProcess(map, level, net.minecraft.world.item.component.MapPostProcessing.SCALE)
                        == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED, "Expansion failed");
        var data = MapItem.getSavedData(map, level); Arrays.fill(data.colors, (byte) 4);
        BlockPos remote = anchor.pos().offset(800, 0, 800);
        require(h, !level.isLoaded(remote), "Remote fixture unexpectedly loaded");
        var target = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, level.dimension(), remote,
                p.getUUID(), "Unloaded owned marker", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1, 1);
        units(p).put(target);
        p.setItemInHand(InteractionHand.MAIN_HAND, map); p.setPos(Vec3.atCenterOf(anchor.pos().above()));
        require(h, NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "lodestone", anchor.id()).isPresent(), "Map open failed");
        NexusSpaceUnitAuthority.sendSpaceUnitMap(p, anchor.id());
        require(h, !level.isLoaded(remote), "Opening map force-loaded remote marker");
        require(h, data.centerX == anchor.pos().getX() && data.centerZ == anchor.pos().getZ(), "Expansion center moved");
        p.discard(); h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void compassFamilyRequiresDiscoveryAndCanRebindThroughRealBlockCallback(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); p.getAbilities().instabuild = false;
        var a = lodestone(h, p, h.absolutePos(new BlockPos(2, 2, 2)));
        var b = lodestone(h, p, a.pos().offset(3, 0, 0));
        discovery(p).markDiscovered(p.getUUID(), a.id());
        for (var item : new net.minecraft.world.item.Item[]{Items.COMPASS, Items.RECOVERY_COMPASS}) {
            ItemStack compass = new ItemStack(item);
            compass.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keep my component"));
            NexusInterfaceBinding.write(compass, h.getLevel(), a.pos(), a.id());
            p.setItemInHand(InteractionHand.MAIN_HAND, compass); p.setPos(Vec3.atCenterOf(a.pos().above()));
            var context = NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "lodestone", a.id()).orElseThrow();
            discovery(p).removeDiscovered(p.getUUID(), b.id());
            require(h, !NexusInterfaceAccess.allows(p, context, b), "Compass accepted undiscovered target");
            discovery(p).markDiscovered(p.getUUID(), b.id());
            require(h, NexusInterfaceAccess.allows(p, context, b), "Compass rejected discovered authorized target");
            p.setPos(Vec3.atCenterOf(b.pos().above()));
            net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p, h.getLevel(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(b.pos()), Direction.UP, b.pos(), false));
            require(h, b.id().equals(NexusInterfaceBinding.read(p.getMainHandItem())), "Previously bound compass failed real rebind");
            require(h, p.getMainHandItem().get(DataComponents.CUSTOM_NAME).getString().equals("Keep my component"), "Rebind lost component");
        }
        p.discard(); h.succeed();
    }

    @GameTest(maxTicks = 85)
    public void exactBackpackRecoveryGetsThreeSecondsAndPreservesExistingInvisibility(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var node = death(h, p); var other = death(h, p);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2400, 1, true, false, true));
        var original = p.getEffect(MobEffects.INVISIBILITY);
        require(h, NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, node.id()), "Own rescue did not grant");
        require(h, p.isInvisible() && p.getEffect(MobEffects.INVISIBILITY) == original, "Grace replaced potion");
        new NexusDeathNodeAuthority().recover(p, other.id());
        require(h, !NexusRecoveryGrace.state(p).recovered(), "Wrong backpack triggered suffix");
        new NexusDeathNodeAuthority().recover(p, node.id());
        require(h, NexusRecoveryGrace.state(p).recovered(), "Exact backpack did not trigger suffix");
        h.runAtTickTime(59, () -> { NexusRecoveryGrace.tick(p); require(h, NexusRecoveryGrace.active(p), "Suffix ended early"); });
        h.runAtTickTime(60, () -> {
            NexusRecoveryGrace.tick(p);
            require(h, !NexusRecoveryGrace.active(p), "Suffix exceeded 3 seconds");
            require(h, p.isInvisible() && p.getEffect(MobEffects.INVISIBILITY) == original, "Grace removed original invisibility");
            p.discard(); h.succeed();
        });
    }

    @GameTest(maxTicks = 1220)
    public void rescueTimeoutDoesNotRefreshAndEndsWithoutPotion(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); var node = death(h, p);
        require(h, NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, node.id()), "First rescue denied");
        require(h, !NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, node.id()), "Repeated rescue refreshed");
        h.runAtTickTime(1199, () -> { NexusRecoveryGrace.tick(p); require(h, p.isInvisible(), "Grace ended before 60 seconds"); });
        h.runAtTickTime(1200, () -> {
            NexusRecoveryGrace.tick(p);
            require(h, !NexusRecoveryGrace.active(p) && !p.isInvisible(), "Timeout failed");
            require(h, !NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, node.id()), "Timeout reset consumed grant");
            p.discard(); h.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void ineligibleTargetsAndInterfacesNeverGrantAndAllOffensiveLifecycleEventsCancel(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); var victim = h.makeMockServerPlayerInLevel();
        var node = death(h, p); var other = death(h, victim);
        for (var type : new TeleportInterfaceType[]{TeleportInterfaceType.COMPASS, TeleportInterfaceType.FILLED_MAP, TeleportInterfaceType.BOOK})
            require(h, !NexusRecoveryGrace.completed(p, type, node.id()), "Wrong interface granted grace");
        require(h, !NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, other.id()), "Foreign death granted grace");
        var lodestone = lodestone(h, p, h.absolutePos(new BlockPos(2, 2, 2)));
        require(h, !NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, lodestone.id()), "Lodestone granted grace");
        grant(h, p);
        AttackEntityCallback.EVENT.invoker().interact(p, h.getLevel(), InteractionHand.MAIN_HAND, victim, null);
        require(h, !NexusRecoveryGrace.active(p), "Attack failed to cancel");
        grant(h, p);
        ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, p.damageSources().playerAttack(victim), 1);
        require(h, !NexusRecoveryGrace.active(p), "PvP failed to cancel");
        grant(h, p);
        var arrow = new Arrow(h.getLevel(), p, new ItemStack(Items.ARROW), null); arrow.setOwner(p);
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(arrow, h.getLevel());
        require(h, NexusRecoveryGrace.active(p), "Loading old projectile cancelled grace");
        net.minecraft.world.entity.projectile.Projectile.spawnProjectile(arrow, h.getLevel(), new ItemStack(Items.BOW));
        require(h, !NexusRecoveryGrace.active(p), "Projectile failed to cancel");
        grant(h, p);
        NexusSpaceUnitAuthority.startTeleport(p, "lodestone", UUID.randomUUID(), UUID.randomUUID());
        require(h, !NexusRecoveryGrace.active(p), "New teleport failed to cancel");
        var recoveredByOther = grant(h, p);
        new NexusDeathNodeAuthority().recover(victim, recoveredByOther.id());
        require(h, !NexusRecoveryGrace.active(p), "Foreign recovery did not immediately invalidate grace");
        var invalidated = grant(h, p);
        units(p).disableDeathUnit(p.getUUID(), invalidated.id(), h.getLevel().getGameTime());
        require(h, !NexusRecoveryGrace.active(p), "Invalid node retained grace");
        var logged = grant(h, p);
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.connection, h.getLevel().getServer());
        require(h, !NexusRecoveryGrace.active(p) && !p.isInvisible(), "Logout retained grace");
        require(h, !NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, logged.id()), "Logout reset consumed grant");
        p.discard(); victim.discard(); h.succeed();
    }

    private static NexusSpaceUnitRecord grant(GameTestHelper h, ServerPlayer p) {
        var node = death(h, p);
        require(h, NexusRecoveryGrace.completed(p, TeleportInterfaceType.RECOVERY_COMPASS, node.id()), "Rescue fixture failed");
        return node;
    }
    private static NexusSpaceUnitRecord death(GameTestHelper h, ServerPlayer p) {
        var authority = new NexusDeathNodeAuthority();
        UUID id = authority.create(p, h.getLevel(), h.absolutePos(new BlockPos(3, 3, 3)));
        authority.bind(h.getLevel(), id, UUID.randomUUID());
        return units(p).get(id).orElseThrow();
    }
    private static NexusSpaceUnitRecord lodestone(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.LODESTONE.defaultBlockState());
        var unit = new NexusSpaceUnitRecord(UUID.randomUUID(), SpaceUnitType.LODESTONE, h.getLevel().dimension(), pos,
                p.getUUID(), "Routing", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1, 1);
        units(p).put(unit); return unit;
    }
    private static NexusSpaceUnitSavedData units(ServerPlayer p) { return NexusSpaceUnitSavedData.loadCanonical(p.level().getServer().overworld().getDataStorage()); }
    private static NexusSpaceDiscoverySavedData discovery(ServerPlayer p) { return NexusSpaceDiscoverySavedData.loadCanonical(p.level().getServer().overworld().getDataStorage()); }
    private static void require(GameTestHelper h, boolean condition, String message) { if (!condition) throw h.assertionException(message); }
}
