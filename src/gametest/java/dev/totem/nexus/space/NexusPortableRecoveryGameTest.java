package dev.totem.nexus.space;

import dev.totem.nexus.effect.NexusEffects;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.UUID;

public final class NexusPortableRecoveryGameTest {
    @GameTest(maxTicks = 40)
    public void portableFriendTargetsAreVisibleCoarseAndRevalidated(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var friend = h.makeMockServerPlayerInLevel();
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, Blocks.LODESTONE.defaultBlockState());
        var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        var id = UUID.randomUUID();
        units.put(new NexusSpaceUnitRecord(id, SpaceUnitType.LODESTONE, level.dimension(), pos, p.getUUID(), "Array",
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0));
        NexusSpaceDiscoverySavedData.loadCanonical(level.getServer().overworld().getDataStorage()).markDiscovered(p.getUUID(), id);
        var friends = level.getServer().overworld().getDataStorage().computeIfAbsent(NexusFriendSavedData.TYPE);
        p.setPos(Vec3.atCenterOf(pos.above())); p.setNoGravity(true); p.getAbilities().instabuild = true;
        friend.setPos(Vec3.atCenterOf(pos.offset(12, 1, 0))); friend.setNoGravity(true);
        try {
            friends.inviteOrAccept(p.getUUID(), friend.getUUID());
            friends.inviteOrAccept(friend.getUUID(), p.getUUID());
            for (var item : new net.minecraft.world.item.Item[]{Items.COMPASS, Items.RECOVERY_COMPASS}) {
                var stack = new ItemStack(item); NexusInterfaceBinding.write(stack, level, pos, id);
                p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "player", p.getUUID()).orElseThrow();
                var payload = NexusSpaceUnitAuthority.currentMapPayload(p).orElseThrow();
                var entry = payload.entries().stream().filter(e -> e.id().equals(friend.getUUID())).findFirst().orElseThrow();
                check(h, entry.type().equals("player") && entry.friendShared() && entry.canTeleport(), "Friend target is not selectable");
                check(h, Math.floorMod(entry.x(), 64) == 32 && Math.floorMod(entry.z(), 64) == 32
                        && Math.floorMod(entry.y(), 16) == 8 && entry.distanceBlocks() % 64 == 0,
                        "Friend payload exposed precise position or distance");
                NexusSpaceUnitAuthority.startTeleport(p, "player", p.getUUID(), friend.getUUID());
                check(h, NexusSpaceUnitAuthority.hasActiveTeleportSession(p.getUUID()), "Selectable compass cannot start friend teleport");
            }
            friends.removeRelationship(p.getUUID(), friend.getUUID());
            check(h, NexusSpaceUnitAuthority.currentMapPayload(p).orElseThrow().entries().stream()
                    .noneMatch(e -> e.id().equals(friend.getUUID())), "Removed friendship remains exposed");
            NexusSpaceUnitAuthority.startTeleport(p, "player", p.getUUID(), friend.getUUID());
            check(h, !NexusSpaceUnitAuthority.hasActiveTeleportSession(p.getUUID()), "Stale friend selection started teleport");
            h.succeed();
        } finally {
            friends.removeRelationship(p.getUUID(), friend.getUUID());
            NexusSpaceUnitAuthority.clearInterfaceContext(p.getUUID());
            friend.discard(); p.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void portableSourcesWorkBeyondEightBlocksAndOnlyConstructionAddsStability(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        NexusInterfaceLifecycleGameTest.buildFunctionalArray(level, pos);
        var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        var id = UUID.randomUUID();
        units.put(new NexusSpaceUnitRecord(id, SpaceUnitType.LODESTONE, level.dimension(), pos, p.getUUID(), "Array",
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0));
        NexusSpaceDiscoverySavedData.loadCanonical(level.getServer().overworld().getDataStorage()).markDiscovered(p.getUUID(), id);
        for (var item : new net.minecraft.world.item.Item[]{Items.COMPASS, Items.RECOVERY_COMPASS, Items.MAP}) {
            var stack = item == Items.MAP ? NexusMapLifecycleAuthority.createBoundMap(level, pos, id, new ItemStack(item)).orElseThrow() : new ItemStack(item);
            if (item != Items.MAP) NexusInterfaceBinding.write(stack, level, pos, id);
            else java.util.Arrays.fill(MapItem.getSavedData(stack, level).colors, (byte) 4);
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            p.setPos(Vec3.atCenterOf(pos.offset(12, 1, 0)));
            var context = NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "player", p.getUUID()).orElseThrow();
            check(h, NexusPortableSource.array(p, context, true).isEmpty(), "Field acquired array materials");
            check(h, NexusTeleportResolver.source(p, "player", p.getUUID()).orElseThrow().pos().equals(p.blockPosition()), "Source is not actual player position");
            p.setPos(Vec3.atCenterOf(pos.above()));
            var inside = NexusPortableSource.array(p, context, true);
            check(h, inside.isPresent() && NexusPortableSource.stability(inside) > .6, "Constructed array did not add stability");
            p.setPos(Vec3.atCenterOf(pos.offset(4, 1, 0)));
            check(h, NexusPortableSource.array(p, context, true).isEmpty(), "Unbuilt part of scan envelope granted bonus");
            check(h, NexusSpaceUnitAuthority.currentInterfaceContext(p).isPresent(), "Leaving array invalidated portable context");
            check(h, NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "player", UUID.randomUUID()).isEmpty(), "Forged player source accepted");
        }
        p.discard(); h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void remnantBindingTracksOnlyExactBackpackAndDestructionCancelsPhasing(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); var level = h.getLevel();
        var adapter = new NexusDeathBackpackNodeAdapter(new NexusDeathNodeAuthority());
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var id = adapter.create(p, level, pos);
        var stack = new ItemStack(Items.CHEST); var tag = new CompoundTag();
        tag.store("totem_remnant_space_death_node_id", UUIDUtil.CODEC, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        var entity = new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, stack);
        entity.setNoGravity(true); entity.setDeltaMovement(Vec3.ZERO); entity.setNeverPickUp(); level.addFreshEntity(entity);
        adapter.bind(level, id, entity.getUUID());
        var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        var moved = pos.offset(4, 2, 0); entity.setPos(Vec3.atCenterOf(moved));
        adapter.moved(level, id, entity.getUUID(), UUID.randomUUID(), moved);
        check(h, units.get(id).orElseThrow().pos().equals(pos), "Wrong owner changed node");
        adapter.moved(level, id, UUID.randomUUID(), p.getUUID(), moved);
        check(h, units.get(id).orElseThrow().pos().equals(pos), "Wrong entity changed node");
        adapter.moved(level, id, entity.getUUID(), p.getUUID(), moved);
        check(h, units.get(id).orElseThrow().pos().equals(moved), "Live backpack movement was lost");
        check(h, NexusDeathTarget.live(level.getServer(), units.get(id).orElseThrow()) == entity, "Production Remnant tag rejected");
        check(h, NexusRecoveryGrace.completed(p, TeleportInterfaceType.COMPASS, id), "No Phasing");
        entity.discard();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(entity, level);
        check(h, !NexusRecoveryGrace.active(p), "Destroyed backpack retained indefinite Phasing");
        p.discard(); h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void phasingUsesOneEffectAndPreservesIndependentPotions(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
        p.addEffect(new MobEffectInstance(NexusEffects.PHASING, 600));
        h.runAfterDelay(2, () -> {
            try {
                // Mock players are not necessarily in the normal player tick list.
                // Let vanilla process effectsDirty before asserting its synchronized flag.
                p.tick();
                check(h, p.isInvisible() && p.getActiveEffects().size() == 1, "Phasing is not one invisible effect");
                check(h, p.getAttributeValue(Attributes.ATTACK_DAMAGE) == 16, "Phasing lacks Weakness I");
                damage(h, p, p.damageSources().generic(), 8);
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 800, 1));
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 800, 1));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 800));
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 800));
                check(h, p.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12, "Phasing doubled native Weakness II");
                damage(h, p, p.damageSources().generic(), 6);
                damage(h, p, p.damageSources().fellOutOfWorld(), 10);
                var night = p.getEffect(MobEffects.NIGHT_VISION);
                check(h, NexusEffects.nightVision(p, night) == night, "Longer native night vision replaced");
                p.removeEffect(MobEffects.WEAKNESS);
                check(h, p.getAttributeValue(Attributes.ATTACK_DAMAGE) == 16, "Native Weakness removal lost Phasing weakness");
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 800, 1));
                NexusRecoveryGrace.cancel(p);
                check(h, p.isInvisible() && p.getActiveEffects().size() == 4 && p.getEffect(MobEffects.NIGHT_VISION) == night,
                        "Removing Phasing changed independent potions");
                check(h, p.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12, "Phasing removal changed native Weakness II");
                h.succeed();
            } finally { p.discard(); }
        });
    }

    private static void damage(GameTestHelper h, net.minecraft.server.level.ServerPlayer p,
                               net.minecraft.world.damagesource.DamageSource source, float expected) {
        p.setHealth(20);
        p.setAbsorptionAmount(0);
        p.invulnerableTime = 0;
        check(h, p.hurtServer(h.getLevel(), source, 10), "Damage fixture was unexpectedly invulnerable");
        check(h, Math.abs(p.getHealth() - (20 - expected)) < .001F,
                "Wrong Phasing/native resistance damage: expected " + expected + ", actual " + (20 - p.getHealth()));
    }

    @GameTest(maxTicks = 20)
    public void emptyStackDestructionUsesCachedBindingAndCancelsIndefinitePhasing(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); var level = h.getLevel();
        var adapter = new NexusDeathBackpackNodeAdapter(new NexusDeathNodeAuthority());
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var id = adapter.create(p, level, pos);
        var stack = new ItemStack(Items.CHEST);
        var entity = new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, stack);
        try {
            entity.setNoGravity(true); entity.setNeverPickUp();
            check(h, level.addFreshEntity(entity), "Could not load empty-stack destruction fixture");
            adapter.bind(level, id, entity.getUUID());
            // Production capture creates/loads the entity before stamping the node binding.
            DeathNodeBackpackBinding.write(stack, id);
            check(h, NexusRecoveryGrace.completed(p, TeleportInterfaceType.COMPASS, id), "No Phasing before destruction");
            // Creative full-inventory pickup clears the ItemStack before discarding its entity.
            // Nexus-alone fixture reproduces that boundary without depending on Remnant classes.
            entity.setItem(ItemStack.EMPTY);
            entity.discard();
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(entity, level);
            check(h, !NexusRecoveryGrace.active(p) && !p.hasEffect(NexusEffects.PHASING),
                    "Empty-stack destruction retained indefinite Phasing");
            var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
            check(h, units.get(id).orElseThrow().status() == SpaceUnitStatus.DISABLED, "Empty-stack destruction retained active node");
            h.succeed();
        } finally {
            if (!entity.isRemoved()) entity.discard();
            NexusRecoveryGrace.cancel(p); p.discard();
        }
    }

    @GameTest(maxTicks = 20, environment = "totem-nexus-gametest:portable_recovery_landing")
    public void landingRequiresShortWalkAndRejectsSealedDiagonalCorner(GameTestHelper h) {
        var level = h.getLevel(); var bag = h.absolutePos(new BlockPos(3, 2, 3));
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            level.setBlockAndUpdate(bag.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 4; y++) level.setBlockAndUpdate(bag.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        check(h, NexusRecoveryLanding.reachable(level, bag.offset(4, 0, 0), bag), "Short open walk rejected");
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
            for (int y = 0; y < 4; y++) level.setBlockAndUpdate(bag.relative(direction).above(y), Blocks.STONE.defaultBlockState());
        check(h, !NexusRecoveryLanding.reachable(level, bag.offset(1, 0, 1), bag), "Sealed diagonal corner accepted");
        level.setBlockAndUpdate(bag.east(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(bag.east().above(), Blocks.AIR.defaultBlockState());
        check(h, NexusRecoveryLanding.reachable(level, bag.offset(1, 0, 1), bag), "One-turn reachable backpack rejected");
        check(h, !NexusRecoveryLanding.near(bag.offset(6, 0, 1), bag), "Euclidean six-block cap exceeded");
        h.succeed();
    }
    @GameTest(maxTicks = 500, environment = "totem-nexus-gametest:recovery_no_landing")
    public void missingNearbySafeGroundCancelsWithoutCharging(GameTestHelper h) {
        var level = h.getLevel(); var p = h.makeMockServerPlayerInLevel();
        p.getAbilities().instabuild = false; p.setNoGravity(true);
        var sourcePos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(sourcePos, Blocks.LODESTONE.defaultBlockState());
        p.setPos(Vec3.atCenterOf(sourcePos.above()));
        var initialPos = p.position();
        var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        var source = UUID.randomUUID();
        units.put(new NexusSpaceUnitRecord(source, SpaceUnitType.LODESTONE, level.dimension(), sourcePos,
                p.getUUID(), "Source", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0));
        var compass = new ItemStack(Items.COMPASS); NexusInterfaceBinding.write(compass, level, sourcePos, source);
        p.setItemInHand(InteractionHand.MAIN_HAND, compass);
        var authority = new NexusDeathNodeAuthority(); var bagPos = sourcePos.above(30);
        var node = authority.create(p, level, bagPos);
        var stack = new ItemStack(Items.CHEST); DeathNodeBackpackBinding.write(stack, node);
        var bag = new ItemEntity(level, bagPos.getX() + .5, bagPos.getY(), bagPos.getZ() + .5, stack);
        bag.setNoGravity(true); bag.setDeltaMovement(Vec3.ZERO); bag.setNeverPickUp(); level.addFreshEntity(bag);
        authority.bind(level, node, bag.getUUID());
        var discovery = NexusSpaceDiscoverySavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        discovery.markDiscovered(p.getUUID(), source);
        p.getFoodData().setFoodLevel(20); p.getFoodData().setSaturation(5);
        NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "player", p.getUUID()).orElseThrow();
        NexusSpaceUnitAuthority.startTeleport(p, "player", p.getUUID(), node);
        check(h, NexusSpaceUnitAuthority.hasActiveTeleportSession(p.getUUID()), "Field death teleport was blocked before landing search");
        h.succeedWhen(() -> {
            check(h, !NexusSpaceUnitAuthority.hasActiveTeleportSession(p.getUUID()), "Waiting for no-ground cancellation");
            check(h, p.position().equals(initialPos), "Unsafe target moved player");
            check(h, p.getFoodData().getFoodLevel() == 20 && p.getFoodData().getSaturationLevel() == 5,
                    "Failed safe-landing search charged food");
            check(h, !NexusRecoveryGrace.active(p), "Cancelled teleport granted Phasing");
            bag.discard(); p.discard();
        });
    }

    @GameTest(maxTicks = 500, environment = "totem-nexus-gametest:remnant_recovery_integration")
    public void realRemnantPickupEndsSuccessfulDeathTeleportPhasing(GameTestHelper h) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("totem-remnant")) { h.succeed(); return; }
        var level = h.getLevel(); var p = h.makeMockServerPlayerInLevel();
        p.getAbilities().instabuild = true; p.setNoGravity(true); p.getInventory().clearContent();
        var sourcePos = h.absolutePos(new BlockPos(2, 2, 2)); var targetPos = sourcePos.east(12);
        NexusInterfaceLifecycleGameTest.buildFunctionalArray(level, sourcePos);
        NexusInterfaceLifecycleGameTest.buildFunctionalArray(level, targetPos);
        p.setPos(Vec3.atCenterOf(sourcePos.above()));
        var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        var source = UUID.randomUUID();
        units.put(new NexusSpaceUnitRecord(source, SpaceUnitType.LODESTONE, level.dimension(), sourcePos,
                p.getUUID(), "Integration source", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0));
        var compass = new ItemStack(Items.COMPASS); NexusInterfaceBinding.write(compass, level, sourcePos, source);
        p.setItemInHand(InteractionHand.MAIN_HAND, compass);
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse("totem:remnant/death_backpack"));
        check(h, item != null && item != Items.AIR, "Real Remnant item missing");
        var stack = new ItemStack(item);
        var bag = new ItemEntity(level, targetPos.getX() + .5, targetPos.getY() + 1, targetPos.getZ() + .5, stack);
        bag.setDeltaMovement(Vec3.ZERO); bag.setNeverPickUp(); level.addFreshEntity(bag);
        var authority = dev.totem.core.api.v1.death.DeathBackpackNodeLifecycle.current().orElseThrow();
        var node = authority.create(p, level, targetPos.above());
        authority.bind(level, node, bag.getUUID());
        var tag = new CompoundTag();
        tag.store("totem_remnant_space_death_node_id", UUIDUtil.CODEC, node);
        tag.store("totem_remnant_death_backpack_owner", UUIDUtil.CODEC, p.getUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        var discovery = NexusSpaceDiscoverySavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        discovery.markDiscovered(p.getUUID(), source);
        NexusSpaceUnitAuthority.establishInterfaceContext(p, InteractionHand.MAIN_HAND, "player", p.getUUID()).orElseThrow();
        NexusSpaceUnitAuthority.startTeleport(p, "player", p.getUUID(), node);
        check(h, NexusSpaceUnitAuthority.hasActiveTeleportSession(p.getUUID()), "Integration teleport did not start");
        h.startSequence()
                .thenWaitUntil(() -> check(h, !NexusSpaceUnitAuthority.hasActiveTeleportSession(p.getUUID()),
                        "Waiting for actual death teleport"))
                .thenExecute(() -> {
                    check(h, NexusRecoveryGrace.active(p) && p.hasEffect(NexusEffects.PHASING), "Successful real-backpack teleport lacked Phasing");
                    check(h, NexusRecoveryLanding.reachable(level, p.blockPosition(), bag.blockPosition()), "Real backpack is not a short walk away");
                    bag.setNoPickUpDelay(); bag.playerTouch(p);
                    check(h, bag.isRemoved() && NexusRecoveryGrace.state(p).recovered(), "Real pickup did not start exact recovery suffix");
                })
                .thenExecuteAfter(59, () -> check(h, NexusRecoveryGrace.active(p), "Real recovery suffix ended early"))
                .thenExecuteAfter(1, () -> {
                    NexusRecoveryGrace.tick(p);
                    check(h, !NexusRecoveryGrace.active(p) && !p.hasEffect(NexusEffects.PHASING), "Real recovery suffix exceeded three seconds");
                    p.discard();
                })
                .thenSucceed();
    }

    private static void check(GameTestHelper h, boolean condition, String message) { if (!condition) throw h.assertionException(message); }
}
