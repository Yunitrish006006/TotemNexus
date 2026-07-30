package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Incremental server-side safe-landing selection.
 *
 * <p>The search asynchronously prepares only the anchor chunk. All block reads
 * then go through already-loaded {@link LevelChunk} instances, so probing a
 * candidate can never enter the blocking ServerChunkCache#getChunk path.
 */
public final class NexusSafeLanding {
    static final int MAX_HORIZONTAL_RADIUS = 48;
    static final int DEFAULT_COLUMN_BUDGET = 128;
    static final int MAX_SEARCH_TICKS = 200;

    private static final int VERTICAL_SEARCH = 4;
    private static final int MAX_HEIGHTMAP_ADJUSTMENT = 16;
    private static final int RANDOM_PRIORITY_ATTEMPTS = 24;
    private static final int TICKET_RADIUS = 0;

    private NexusSafeLanding() {
    }

    public static Search begin(
            ServerLevel level,
            BlockPos target,
            boolean lodestoneTarget,
            int horizontalDeviation) {
        BlockPos anchor = lodestoneTarget ? target.above() : target;
        return new Search(level, anchor, horizontalDeviation, List.of());
    }

    static Search begin(
            ServerLevel level,
            BlockPos target,
            boolean lodestoneTarget,
            int horizontalDeviation,
            RandomSource random,
            boolean preferExactColumn) {
        BlockPos anchor = lodestoneTarget ? target.above() : target;
        int radius = clampRadius(horizontalDeviation);
        List<ColumnOffset> priority = new ArrayList<>(RANDOM_PRIORITY_ATTEMPTS + 1);
        if (preferExactColumn) {
            priority.add(new ColumnOffset(0, 0));
        }
        if (radius > 0) {
            for (int attempt = 0; attempt < RANDOM_PRIORITY_ATTEMPTS; attempt++) {
                priority.add(new ColumnOffset(
                        random.nextInt(radius * 2 + 1) - radius,
                        random.nextInt(radius * 2 + 1) - radius
                ));
            }
        }
        return new Search(level, anchor, radius, List.copyOf(priority));
    }

    /**
     * Compatibility helper for inactive cutover seams. It examines only a
     * single tick's budget of chunks that are already loaded and never requests
     * or generates a chunk.
     */
    public static Optional<BlockPos> findLoaded(
            ServerLevel level,
            BlockPos target,
            boolean lodestoneTarget,
            int horizontalDeviation) {
        BlockPos anchor = lodestoneTarget ? target.above() : target;
        ColumnCursor cursor = new ColumnCursor(horizontalDeviation);
        Map<Long, LevelChunk> loadedChunks = new HashMap<>();
        Set<Long> unavailableChunks = new HashSet<>();
        for (int checked = 0; checked < DEFAULT_COLUMN_BUDGET && cursor.hasNext(); checked++) {
            ColumnOffset offset = cursor.next();
            BlockPos column = anchor.offset(offset.x(), 0, offset.z());
            LevelChunk chunk = loadedChunk(level, column, loadedChunks, unavailableChunks);
            if (chunk == null) {
                continue;
            }
            Optional<BlockPos> landing = findInColumn(level, chunk, column);
            if (landing.isPresent()) {
                return landing;
            }
        }
        return Optional.empty();
    }

    public static boolean isSafeLoaded(ServerLevel level, BlockPos feetPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                feetPos.getX() >> 4,
                feetPos.getZ() >> 4
        );
        return chunk != null && isSafe(level, chunk, feetPos);
    }

    public enum State {
        SEARCHING,
        FOUND,
        EXHAUSTED
    }

    public record Progress(State state, Optional<BlockPos> landing) {
        private static final Progress SEARCHING = new Progress(State.SEARCHING, Optional.empty());
        private static final Progress EXHAUSTED = new Progress(State.EXHAUSTED, Optional.empty());

        private static Progress found(BlockPos landing) {
            return new Progress(State.FOUND, Optional.of(landing.immutable()));
        }
    }

    public static final class Search implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos anchor;
        private final int radius;
        private final ChunkPos anchorChunk;
        private final CompletableFuture<?> anchorLoad;
        private final ColumnCursor cursor;
        private final List<ColumnOffset> priorityOffsets;
        private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
        private final Set<Long> unavailableChunks = new HashSet<>();
        private int elapsedTicks;
        private int priorityIndex;
        private boolean closed;
        private Progress terminal;

        private Search(
                ServerLevel level,
                BlockPos anchor,
                int horizontalDeviation,
                List<ColumnOffset> priorityOffsets) {
            if (level == null || anchor == null) {
                throw new IllegalArgumentException("Safe-landing level and anchor are required");
            }
            this.level = level;
            this.anchor = anchor.immutable();
            this.radius = clampRadius(horizontalDeviation);
            this.anchorChunk = ChunkPos.containing(anchor);
            this.cursor = new ColumnCursor(this.radius);
            this.priorityOffsets = priorityOffsets;
            this.anchorLoad = level.getChunkSource().addTicketAndLoadWithRadius(
                    SafeLandingTicketHolder.TYPE,
                    this.anchorChunk,
                    TICKET_RADIUS
            );
        }

        public ServerLevel level() {
            return this.level;
        }

        public BlockPos anchor() {
            return this.anchor;
        }

        public int radius() {
            return this.radius;
        }

        public Progress advance() {
            return advance(DEFAULT_COLUMN_BUDGET);
        }

        Progress advance(int columnBudget) {
            if (this.terminal != null) {
                return this.terminal;
            }
            if (this.closed) {
                return Progress.EXHAUSTED;
            }
            if (columnBudget <= 0) {
                throw new IllegalArgumentException("Column budget must be positive");
            }

            this.elapsedTicks++;
            if (this.elapsedTicks > MAX_SEARCH_TICKS || this.anchorLoad.isCompletedExceptionally()) {
                return finish(Progress.EXHAUSTED);
            }
            if (!this.anchorLoad.isDone()) {
                return Progress.SEARCHING;
            }

            int checked = 0;
            while (checked < columnBudget && hasNextColumn()) {
                ColumnOffset offset = nextColumn();
                checked++;
                BlockPos column = this.anchor.offset(offset.x(), 0, offset.z());
                LevelChunk chunk = loadedChunk(
                        this.level,
                        column,
                        this.loadedChunks,
                        this.unavailableChunks
                );
                if (chunk == null) {
                    continue;
                }

                Optional<BlockPos> landing = findInColumn(this.level, chunk, column);
                if (landing.isPresent()) {
                    return finish(Progress.found(landing.get()));
                }
            }

            return hasNextColumn() ? Progress.SEARCHING : finish(Progress.EXHAUSTED);
        }

        private boolean hasNextColumn() {
            return this.priorityIndex < this.priorityOffsets.size() || this.cursor.hasNext();
        }

        private ColumnOffset nextColumn() {
            if (this.priorityIndex < this.priorityOffsets.size()) {
                return this.priorityOffsets.get(this.priorityIndex++);
            }
            return this.cursor.next();
        }

        private Progress finish(Progress result) {
            this.terminal = result;
            return result;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.level.getChunkSource().removeTicketWithRadius(
                    SafeLandingTicketHolder.TYPE,
                    this.anchorChunk,
                    TICKET_RADIUS
            );
        }
    }

    private static final class SafeLandingTicketHolder {
        private static final TicketType TYPE =
                new TicketType(MAX_SEARCH_TICKS + 20L, TicketType.FLAG_LOADING);
    }

    static final class ColumnCursor {
        private final int radius;
        private int horizontal;
        private int dx;
        private int dz;
        private boolean exhausted;

        ColumnCursor(int horizontalDeviation) {
            this.radius = clampRadius(horizontalDeviation);
        }

        boolean hasNext() {
            return !this.exhausted;
        }

        ColumnOffset next() {
            if (this.exhausted) {
                throw new IllegalStateException("Safe-landing cursor is exhausted");
            }

            while (true) {
                int currentHorizontal = this.horizontal;
                int currentDx = this.dx;
                int currentDz = this.dz;
                advanceCursor();
                if (Math.max(Math.abs(currentDx), Math.abs(currentDz)) == currentHorizontal) {
                    return new ColumnOffset(currentDx, currentDz);
                }
            }
        }

        private void advanceCursor() {
            if (this.dz < this.horizontal) {
                this.dz++;
                return;
            }
            if (this.dx < this.horizontal) {
                this.dx++;
                this.dz = -this.horizontal;
                return;
            }
            if (this.horizontal >= this.radius) {
                this.exhausted = true;
                return;
            }
            this.horizontal++;
            this.dx = -this.horizontal;
            this.dz = -this.horizontal;
        }
    }

    record ColumnOffset(int x, int z) {
    }

    private static int clampRadius(int radius) {
        return Math.max(0, Math.min(MAX_HORIZONTAL_RADIUS, radius));
    }

    private static LevelChunk loadedChunk(
            ServerLevel level,
            BlockPos pos,
            Map<Long, LevelChunk> loadedChunks,
            Set<Long> unavailableChunks) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
        LevelChunk cached = loadedChunks.get(chunkKey);
        if (cached != null) {
            return cached;
        }
        if (unavailableChunks.contains(chunkKey)) {
            return null;
        }

        LevelChunk loaded = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (loaded == null) {
            unavailableChunks.add(chunkKey);
            return null;
        }
        loadedChunks.put(chunkKey, loaded);
        return loaded;
    }

    private static Optional<BlockPos> findInColumn(
            ServerLevel level,
            LevelChunk chunk,
            BlockPos anchor) {
        if (isSafe(level, chunk, anchor)) {
            return Optional.of(anchor.immutable());
        }

        for (int offset = 1; offset <= VERTICAL_SEARCH; offset++) {
            BlockPos above = anchor.above(offset);
            if (isSafe(level, chunk, above)) {
                return Optional.of(above.immutable());
            }

            BlockPos below = anchor.below(offset);
            if (isSafe(level, chunk, below)) {
                return Optional.of(below.immutable());
            }
        }

        if (!hasHeightmap(chunk, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)) {
            return Optional.empty();
        }
        int surfaceY = chunk.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                anchor.getX() & 15,
                anchor.getZ() & 15
        );
        if (Math.abs(surfaceY - anchor.getY()) <= VERTICAL_SEARCH
                || Math.abs(surfaceY - anchor.getY()) > MAX_HEIGHTMAP_ADJUSTMENT) {
            return Optional.empty();
        }

        BlockPos surface = new BlockPos(anchor.getX(), surfaceY, anchor.getZ());
        return isSafe(level, chunk, surface)
                ? Optional.of(surface.immutable())
                : Optional.empty();
    }

    private static boolean hasHeightmap(LevelChunk chunk, Heightmap.Types type) {
        return chunk.getHeightmaps().stream().anyMatch(entry -> entry.getKey() == type);
    }

    private static boolean isSafe(ServerLevel level, LevelChunk chunk, BlockPos feetPos) {
        if (feetPos.getY() <= level.getMinY() || feetPos.getY() >= level.getMaxY()) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(feetPos.getX() + 0.5D, feetPos.getZ() + 0.5D)) {
            return false;
        }

        BlockState feet = chunk.getBlockState(feetPos);
        BlockState head = chunk.getBlockState(feetPos.above());
        BlockState floor = chunk.getBlockState(feetPos.below());
        return open(feet)
                && open(head)
                && !floor.isAir()
                && floor.getFluidState().isEmpty()
                && floor.blocksMotion()
                && !unsafe(feet)
                && !unsafe(head)
                && !unsafe(floor);
    }

    private static boolean open(BlockState state) {
        return state.isAir() && state.getFluidState().isEmpty();
    }

    private static boolean unsafe(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.POWDER_SNOW);
    }
}
