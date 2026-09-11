package dev.totem.nexus.space;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import java.util.*;
import java.nio.file.*;

/** Dedicated-server fixture; production command, permission receiver and Observer relay do the work. */
public final class NexusAccessE2eServer implements ModInitializer {
    public static final UUID UNIT = UUID.fromString("bbc66000-0000-4000-8000-000000000001");
    public static final UUID OFFLINE = UUID.fromString("bbc66000-0000-4000-8000-000000000002");
    public static final Path RESULTS = Path.of(System.getProperty("nexus.access.e2e.results","build/access-e2e/results"));
    private boolean setup, observing;
    private int ticks;
    public static boolean exists(String name) { return Files.exists(RESULTS.resolve(name)); }
    public static void mark(String name) {
        try { Files.createDirectories(RESULTS); Files.writeString(RESULTS.resolve(name),"ok\n"); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    public static void fail(Throwable error) {
        try { Files.createDirectories(RESULTS); Files.writeString(RESULTS.resolve("failure.txt"),error.toString()); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (exists("done") || exists("failure.txt")) return;
            try {
                if (++ticks > 20*180) throw new AssertionError("Access E2E timed out");
                var target = server.getPlayerList().getPlayerByName("AccessTarget");
                var observer = server.getPlayerList().getPlayerByName("AccessObserver");
                if (target == null || observer == null) return;
                var level = server.overworld();
                var units = level.getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
                if (!setup) {
                    BlockPos pos = new BlockPos(0,100,0);
                    level.setBlockAndUpdate(pos,Blocks.LODESTONE.defaultBlockState());
                    target.teleportTo(.5,101,.5); target.setGameMode(GameType.CREATIVE);
                    observer.teleportTo(.5,101,.5); observer.setGameMode(GameType.CREATIVE);
                    server.getPlayerList().op(new net.minecraft.server.players.NameAndId(observer.getGameProfile()));
                    units.put(new NexusSpaceUnitRecord(UNIT,SpaceUnitType.LODESTONE,level.dimension(),pos,target.getUUID(),"E2E Anchor",
                            SpaceUnitVisibility.PRIVATE,SpaceUnitStatus.ACTIVE,Set.of(),Set.of(),SpaceStructureSnapshot.EMPTY,0,0));
                    dev.totem.core.player.TotemPlayerDirectoryData.get(server).remember(OFFLINE,"OfflineAlice");
                    NexusSpaceDiscoverySavedData.loadCanonical(level.getDataStorage()).markDiscovered(target.getUUID(), UNIT);
                    var book = new ItemStack(Items.BOOK); NexusInterfaceBinding.writeIdentity(book,UNIT);
                    target.setItemInHand(InteractionHand.MAIN_HAND,book);
                    NexusSpaceUnitAuthority.establishInterfaceContext(target,InteractionHand.MAIN_HAND,"lodestone",UNIT).orElseThrow();
                    setup = true; mark("ready");
                }
                if (!observing && exists("target-open")) {
                    int result = server.getCommands().getDispatcher().execute("observeui AccessTarget",observer.createCommandSourceStack());
                    if (result == 1) { observing = true; mark("observing"); }
                }
                if (units.get(UNIT).orElseThrow().administrators().contains(OFFLINE)) mark("grant-ok");
                if (exists("stop-sent") && !observer.isSpectator()) {
                    if (observer.gameMode.getGameModeForPlayer()!=GameType.CREATIVE) throw new AssertionError("Observer mode not restored");
                    mark("done");
                }
            } catch (Throwable e) { fail(e); }
        });
    }
}
