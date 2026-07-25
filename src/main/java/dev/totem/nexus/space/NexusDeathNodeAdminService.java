package dev.totem.nexus.space;

import dev.totem.nexus.mixin.NexusSpaceDiscoverySavedDataAccessor;
import dev.totem.nexus.mixin.NexusSpaceUnitSavedDataAccessor;
import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.RequestDeathNodeAdminPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Relative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

public final class NexusDeathNodeAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger("totem-nexus");
    public static final String ACTION_DISABLE = "disable";
    public static final String ACTION_PURGE = "purge";
    public static final String ACTION_REQUEST_PURGE = "request_purge";
    public static final String ACTION_TELEPORT = "teleport";
    public static final String ACTION_BATCH_DISABLE = "batch_disable";
    public static final String ACTION_REQUEST_BATCH_DISABLE = "request_batch_disable";
    public static final String ACTION_BATCH_PURGE = "batch_purge";
    public static final String ACTION_REQUEST_BATCH_PURGE = "request_batch_purge";
    /** Reserved wire value for server-recomputed batch operations; never a real node target. */
    public static final UUID BATCH_NODE_ID = new UUID(0L, 0L);
    public static final int MAX_PAGE_SIZE = DeathNodeAdminPayload.MAX_ENTRIES;
    private static final int ADMIN_TELEPORT_SEARCH_RADIUS = 4;
    private static final long PURGE_CONFIRMATION_DURATION_MILLIS = 30_000L;
    private static final DestructiveConfirmationStore PENDING_DESTRUCTIVE_CONFIRMATIONS =
            new DestructiveConfirmationStore();
    private static final Map<UUID, DeathNodeQuery> ACTIVE_QUERIES = new HashMap<>();

    /**
     * A bounded, server-owned request for one stable death-node result page.
     *
     * <p>Clients may choose a page number, but the service clamps both fields
     * before selecting records. Later filters and destructive batch actions
     * will reuse this query shape rather than trusting a client result list.</p>
     */
    public record PageRequest(int page, int pageSize) {
        public PageRequest {
            page = Math.max(0, page);
            pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        }
    }

    /** A bounded filter and page selection that the server reevaluates on every request. */
    public record DeathNodeQuery(
            String ownerQuery,
            String dimensionId,
            String statusId,
            long createdAfterGameTime,
            long createdBeforeGameTime,
            PageRequest pageRequest) {
        public DeathNodeQuery {
            ownerQuery = normalizeQuery(ownerQuery, 64);
            dimensionId = normalizeQuery(dimensionId, 128);
            statusId = normalizeQuery(statusId, 32).toLowerCase(Locale.ROOT);
            createdAfterGameTime = Math.max(0L, createdAfterGameTime);
            createdBeforeGameTime = Math.max(0L, createdBeforeGameTime);
            pageRequest = pageRequest == null ? new PageRequest(0, MAX_PAGE_SIZE) : pageRequest;
        }

        public static DeathNodeQuery from(RequestDeathNodeAdminPayload payload) {
            if (payload == null) {
                return defaults();
            }
            return new DeathNodeQuery(
                    payload.ownerQuery(),
                    payload.dimensionId(),
                    payload.statusId(),
                    payload.createdAfterGameTime(),
                    payload.createdBeforeGameTime(),
                    new PageRequest(payload.page(), MAX_PAGE_SIZE)
            );
        }

        public static DeathNodeQuery defaults() {
            return new DeathNodeQuery("", "", "", 0L, 0L, new PageRequest(0, MAX_PAGE_SIZE));
        }
    }

    /** A stable, immutable server result page. */
    public record Page<T>(List<T> entries, int page, int pageSize, int totalEntries) {
        public Page {
            entries = List.copyOf(entries == null ? List.of() : entries);
            page = Math.max(0, page);
            pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
            totalEntries = Math.max(0, totalEntries);
        }

        public boolean hasNextPage() {
            return ((long) page + 1L) * pageSize < totalEntries;
        }

        public boolean hasPreviousPage() {
            return page > 0;
        }
    }

    /**
     * Read-only consistency signals derived only from persisted death-node
     * records and the persisted discovery index. They deliberately do not
     * inspect loaded entities: an unloaded death backpack is not evidence
     * that it has gone missing.
     */
    public enum DiagnosticFlag {
        ORPHANED_OWNER_DISCOVERY("orphaned_owner_discovery"),
        NON_PRIVATE_VISIBILITY("non_private_visibility"),
        UNEXPECTED_ACCESS_LIST("unexpected_access_list"),
        UNEXPECTED_STRUCTURE("unexpected_structure"),
        DUPLICATE_ACTIVE_LOCATION("duplicate_active_location");

        private final String id;

        DiagnosticFlag(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    public record DeathNodeDiagnostics(Set<DiagnosticFlag> flags) {
        private static final DeathNodeDiagnostics NONE = new DeathNodeDiagnostics(Set.of());

        public DeathNodeDiagnostics {
            flags = Set.copyOf(flags == null ? Set.of() : flags);
        }

        public boolean hasFlags() {
            return !this.flags.isEmpty();
        }

        public List<String> ids() {
            return this.flags.stream()
                    .sorted(Comparator.comparing(DiagnosticFlag::id))
                    .map(DiagnosticFlag::id)
                    .toList();
        }
    }

    record DeathNodeDiagnosticInput(
            UUID id,
            UUID owner,
            boolean active,
            boolean privateVisibility,
            boolean hasAccessList,
            boolean hasStructure,
            String locationKey) {
    }

    record DestructiveConfirmation(
            UUID token,
            UUID administratorId,
            UUID nodeId,
            String action,
            String queryFingerprint,
            long expiresAtMillis) {
        boolean matches(
                UUID administratorId,
                UUID nodeId,
                String action,
                String queryFingerprint,
                UUID token,
                long nowMillis) {
            return this.expiresAtMillis > nowMillis
                    && this.administratorId.equals(administratorId)
                    && this.nodeId.equals(nodeId)
                    && this.action.equals(action)
                    && this.queryFingerprint.equals(queryFingerprint)
                    && this.token.equals(token);
        }
    }

    enum ConfirmationConsumeResult {
        CONFIRMED,
        MISSING,
        EXPIRED,
        MISMATCH
    }

    /**
     * Keeps destructive-operation confirmations in server memory. This makes
     * their short lifetime explicit and prevents persistence or reconnects
     * from turning a confirmation into a reusable credential.
     */
    static final class DestructiveConfirmationStore {
        private final Map<UUID, DestructiveConfirmation> byAdministrator = new HashMap<>();

        DestructiveConfirmation issue(UUID administratorId, UUID nodeId, String action, long nowMillis, long durationMillis) {
            return issue(administratorId, nodeId, action, "", nowMillis, durationMillis);
        }

        DestructiveConfirmation issue(
                UUID administratorId,
                UUID nodeId,
                String action,
                String queryFingerprint,
                long nowMillis,
                long durationMillis) {
            DestructiveConfirmation confirmation = new DestructiveConfirmation(
                    UUID.randomUUID(),
                    administratorId,
                    nodeId,
                    action,
                    queryFingerprint == null ? "" : queryFingerprint,
                    nowMillis + Math.max(1L, durationMillis)
            );
            this.byAdministrator.put(administratorId, confirmation);
            return confirmation;
        }

        ConfirmationConsumeResult consume(
                UUID administratorId,
                UUID nodeId,
                String action,
                UUID token,
                long nowMillis) {
            return consume(administratorId, nodeId, action, "", token, nowMillis);
        }

        ConfirmationConsumeResult consume(
                UUID administratorId,
                UUID nodeId,
                String action,
                String queryFingerprint,
                UUID token,
                long nowMillis) {
            DestructiveConfirmation confirmation = this.byAdministrator.get(administratorId);
            if (confirmation == null) {
                return ConfirmationConsumeResult.MISSING;
            }
            if (confirmation.expiresAtMillis() <= nowMillis) {
                this.byAdministrator.remove(administratorId, confirmation);
                return ConfirmationConsumeResult.EXPIRED;
            }
            if (!confirmation.matches(administratorId, nodeId, action,
                    queryFingerprint == null ? "" : queryFingerprint, token, nowMillis)) {
                return ConfirmationConsumeResult.MISMATCH;
            }
            this.byAdministrator.remove(administratorId, confirmation);
            return ConfirmationConsumeResult.CONFIRMED;
        }

        void clear(UUID administratorId) {
            if (administratorId != null) {
                this.byAdministrator.remove(administratorId);
            }
        }
    }

    private NexusDeathNodeAdminService() {
    }

    public static boolean canManage(ServerPlayer player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    public static void clearSession(UUID administratorId) {
        if (administratorId == null) {
            return;
        }
        ACTIVE_QUERIES.remove(administratorId);
        PENDING_DESTRUCTIVE_CONFIRMATIONS.clear(administratorId);
    }

    /**
     * Sends the current authorized administration snapshot.
     *
     * @return {@code true} only when a private snapshot was sent to an
     * authorized administrator; {@code false} when the request was denied.
     */
    public static boolean sendSnapshot(ServerPlayer player) {
        return sendSnapshot(player, activeQuery(player), null);
    }

    /**
     * Applies a client query only after permission is established so an
     * unauthorized payload cannot resolve profile data or receive a snapshot.
     *
     * @return {@code true} only when a private snapshot was sent.
     */
    public static boolean sendSnapshot(ServerPlayer player, RequestDeathNodeAdminPayload request) {
        if (!canManage(player)) {
            deny(player);
            return false;
        }
        DeathNodeQuery query = resolveOwnerQuery(player.level().getServer(), DeathNodeQuery.from(request));
        ACTIVE_QUERIES.put(player.getUUID(), query);
        return sendSnapshot(player, query, null);
    }

    private static boolean sendSnapshot(
            ServerPlayer player,
            DeathNodeQuery query,
            DestructiveConfirmation confirmation) {
        if (!canManage(player)) {
            deny(player);
            return false;
        }

        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData data = units(server);
        Map<UUID, Set<UUID>> discoveredByPlayer = discoveredByPlayer(server);
        Map<UUID, DeathNodeDiagnostics> diagnosticsByNode = diagnoseDeathNodes(
                unitMap(data).values(),
                discoveredByPlayer
        );
        Page<NexusSpaceUnitRecord> page = queryDeathNodes(
                unitMap(data).values(),
                ownerId -> ownerDisplayName(server, ownerId),
                query
        );
        List<DeathNodeAdminPayload.Entry> entries = new ArrayList<>(page.entries().size());
        for (NexusSpaceUnitRecord unit : page.entries()) {
            entries.add(new DeathNodeAdminPayload.Entry(
                    unit.id(),
                    unit.owner(),
                    ownerDisplayName(server, unit.owner()),
                    unit.name(),
                    unit.status().id(),
                    unit.dimension().identifier().toString(),
                    unit.pos().getX(),
                    unit.pos().getY(),
                    unit.pos().getZ(),
                    unit.createdGameTime(),
                    unit.updatedGameTime(),
                    diagnosticsByNode.getOrDefault(unit.id(), DeathNodeDiagnostics.NONE).ids()
            ));
        }

        ServerPlayNetworking.send(player, new DeathNodeAdminPayload(
                entries,
                page.hasNextPage(),
                page.page(),
                page.pageSize(),
                page.totalEntries(),
                player.level().getGameTime(),
                confirmation == null ? null : confirmation.nodeId(),
                confirmation == null ? null : confirmation.token(),
                confirmation == null ? "" : confirmation.action(),
                confirmation == null ? 0L : confirmation.expiresAtMillis()
        ));
        return true;
    }

    /**
     * Selects one deterministic page of death-node records without mutating
     * SavedData. The caller supplies the server's current owner-name resolver
     * so offline-name policy is kept at the server boundary.
     */
    public static Page<NexusSpaceUnitRecord> queryDeathNodes(
            Collection<NexusSpaceUnitRecord> units,
            Function<UUID, String> ownerNameResolver,
            PageRequest request) {
        return queryDeathNodes(
                units,
                ownerNameResolver,
                new DeathNodeQuery("", "", "", 0L, 0L, request)
        );
    }

    /**
     * Computes administration-only consistency signals without changing
     * records, indexes, chunk tickets or entity load state.
     */
    static Map<UUID, DeathNodeDiagnostics> diagnoseDeathNodes(
            Collection<NexusSpaceUnitRecord> units,
            Map<UUID, Set<UUID>> discoveredByPlayer) {
        List<DeathNodeDiagnosticInput> inputs = new ArrayList<>();
        if (units != null) {
            for (NexusSpaceUnitRecord unit : units) {
                if (unit != null && unit.type() == SpaceUnitType.DEATH) {
                    inputs.add(new DeathNodeDiagnosticInput(
                            unit.id(),
                            unit.owner(),
                            unit.status() == SpaceUnitStatus.ACTIVE,
                            unit.visibility() == SpaceUnitVisibility.PRIVATE,
                            !unit.administrators().isEmpty() || !unit.allowedPlayers().isEmpty(),
                            !SpaceStructureSnapshot.EMPTY.equals(unit.structure()),
                            deathNodeLocationKey(unit)
                    ));
                }
            }
        }
        return diagnoseDeathNodeInputs(inputs, discoveredByPlayer);
    }

    static Map<UUID, DeathNodeDiagnostics> diagnoseDeathNodeInputs(
            Collection<DeathNodeDiagnosticInput> inputs,
            Map<UUID, Set<UUID>> discoveredByPlayer) {
        Map<UUID, Set<UUID>> discovery = discoveredByPlayer == null ? Map.of() : discoveredByPlayer;
        Map<String, Integer> activeLocationCounts = new HashMap<>();
        if (inputs != null) {
            for (DeathNodeDiagnosticInput input : inputs) {
                if (input != null && input.active()) {
                    activeLocationCounts.merge(input.locationKey(), 1, Integer::sum);
                }
            }
        }

        Map<UUID, DeathNodeDiagnostics> diagnosticsByNode = new HashMap<>();
        if (inputs == null) {
            return diagnosticsByNode;
        }
        for (DeathNodeDiagnosticInput input : inputs) {
            if (input == null || input.id() == null) {
                continue;
            }
            Set<DiagnosticFlag> flags = new LinkedHashSet<>();
            if (input.active()
                    && !discovery.getOrDefault(input.owner(), Set.of()).contains(input.id())) {
                flags.add(DiagnosticFlag.ORPHANED_OWNER_DISCOVERY);
            }
            if (!input.privateVisibility()) {
                flags.add(DiagnosticFlag.NON_PRIVATE_VISIBILITY);
            }
            if (input.hasAccessList()) {
                flags.add(DiagnosticFlag.UNEXPECTED_ACCESS_LIST);
            }
            if (input.hasStructure()) {
                flags.add(DiagnosticFlag.UNEXPECTED_STRUCTURE);
            }
            if (input.active() && activeLocationCounts.getOrDefault(input.locationKey(), 0) > 1) {
                flags.add(DiagnosticFlag.DUPLICATE_ACTIVE_LOCATION);
            }
            diagnosticsByNode.put(input.id(), new DeathNodeDiagnostics(flags));
        }
        return diagnosticsByNode;
    }

    public static Page<NexusSpaceUnitRecord> queryDeathNodes(
            Collection<NexusSpaceUnitRecord> units,
            Function<UUID, String> ownerNameResolver,
            DeathNodeQuery query) {
        DeathNodeQuery effectiveQuery = query == null ? DeathNodeQuery.defaults() : query;
        return pageDeathNodes(
                units,
                unit -> unit.type() == SpaceUnitType.DEATH
                        && matchesQuery(
                                unit.owner(),
                                ownerNameResolver == null ? "" : ownerNameResolver.apply(unit.owner()),
                                unit.dimension().identifier().toString(),
                                unit.status().id(),
                                unit.createdGameTime(),
                                effectiveQuery
                        ),
                NexusSpaceUnitRecord::owner,
                NexusSpaceUnitRecord::createdGameTime,
                NexusSpaceUnitRecord::id,
                ownerNameResolver,
                effectiveQuery.pageRequest()
        );
    }

    static boolean matchesQuery(
            UUID ownerId,
            String ownerName,
            String dimensionId,
            String statusId,
            long createdGameTime,
            DeathNodeQuery query) {
        DeathNodeQuery effectiveQuery = query == null ? DeathNodeQuery.defaults() : query;
        if (!effectiveQuery.ownerQuery().isEmpty()
                && !effectiveQuery.ownerQuery().equalsIgnoreCase(ownerId == null ? "" : ownerId.toString())
                && !effectiveQuery.ownerQuery().equalsIgnoreCase(ownerName == null ? "" : ownerName.trim())) {
            return false;
        }
        if (!effectiveQuery.dimensionId().isEmpty()
                && !effectiveQuery.dimensionId().equalsIgnoreCase(dimensionId == null ? "" : dimensionId.trim())) {
            return false;
        }
        if (!effectiveQuery.statusId().isEmpty()
                && !effectiveQuery.statusId().equalsIgnoreCase(statusId == null ? "" : statusId.trim())) {
            return false;
        }
        return (effectiveQuery.createdAfterGameTime() == 0L
                || createdGameTime >= effectiveQuery.createdAfterGameTime())
                && (effectiveQuery.createdBeforeGameTime() == 0L
                || createdGameTime <= effectiveQuery.createdBeforeGameTime());
    }

    /**
     * Resolves a cached current or former player name to its UUID before the
     * query is stored. From this point onward filtering remains UUID-based,
     * even if the profile cache later exposes a different display name.
     */
    static DeathNodeQuery resolveOwnerQuery(DeathNodeQuery query, Function<String, UUID> nameResolver) {
        DeathNodeQuery effectiveQuery = query == null ? DeathNodeQuery.defaults() : query;
        if (effectiveQuery.ownerQuery().isEmpty() || isUuid(effectiveQuery.ownerQuery()) || nameResolver == null) {
            return effectiveQuery;
        }
        UUID ownerId = nameResolver.apply(effectiveQuery.ownerQuery());
        if (ownerId == null) {
            return effectiveQuery;
        }
        return new DeathNodeQuery(
                ownerId.toString(),
                effectiveQuery.dimensionId(),
                effectiveQuery.statusId(),
                effectiveQuery.createdAfterGameTime(),
                effectiveQuery.createdBeforeGameTime(),
                effectiveQuery.pageRequest()
        );
    }

    static <T> Page<T> pageDeathNodes(
            Collection<T> candidates,
            Predicate<T> isDeathNode,
            Function<T, UUID> ownerId,
            ToLongFunction<T> createdGameTime,
            Function<T, UUID> id,
            Function<UUID, String> ownerNameResolver,
            PageRequest request) {
        PageRequest boundedRequest = request == null ? new PageRequest(0, MAX_PAGE_SIZE) : request;
        Function<UUID, String> names = ownerNameResolver == null
                ? NexusDeathNodeAdminService::shortOwnerId
                : ownerNameResolver;
        List<T> deathNodes = new ArrayList<>();
        if (candidates != null && isDeathNode != null) {
            for (T candidate : candidates) {
                if (candidate != null && isDeathNode.test(candidate)) {
                    deathNodes.add(candidate);
                }
            }
        }

        deathNodes.sort(Comparator
                .comparing((T node) -> normalizedOwnerName(names, ownerId.apply(node)), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.comparingLong(createdGameTime).reversed())
                .thenComparing(id));

        int totalEntries = deathNodes.size();
        long requestedStart = (long) boundedRequest.page() * boundedRequest.pageSize();
        int start = requestedStart >= totalEntries ? totalEntries : (int) requestedStart;
        int end = Math.min(totalEntries, start + boundedRequest.pageSize());
        return new Page<>(
                deathNodes.subList(start, end),
                boundedRequest.page(),
                boundedRequest.pageSize(),
                totalEntries
        );
    }

    /**
     * Re-evaluates the active server-side filter against the complete current
     * SavedData set. It deliberately ignores the query page and accepts no
     * client-supplied node list.
     */
    static List<NexusSpaceUnitRecord> batchTargets(
            Collection<NexusSpaceUnitRecord> units,
            Function<UUID, String> ownerNameResolver,
            DeathNodeQuery query,
            boolean purge) {
        DeathNodeQuery effectiveQuery = query == null ? DeathNodeQuery.defaults() : query;
        return batchTargets(
                units,
                unit -> unit != null
                        && unit.type() == SpaceUnitType.DEATH
                        && matchesQuery(
                                unit.owner(),
                                ownerNameResolver == null ? "" : ownerNameResolver.apply(unit.owner()),
                                unit.dimension().identifier().toString(),
                                unit.status().id(),
                                unit.createdGameTime(),
                                effectiveQuery),
                unit -> unit.status() == SpaceUnitStatus.ACTIVE,
                NexusSpaceUnitRecord::owner,
                NexusSpaceUnitRecord::createdGameTime,
                NexusSpaceUnitRecord::id,
                ownerNameResolver,
                purge
        );
    }

    static <T> List<T> batchTargets(
            Collection<T> candidates,
            Predicate<T> matchesCurrentQuery,
            Predicate<T> isActive,
            Function<T, UUID> ownerId,
            ToLongFunction<T> createdGameTime,
            Function<T, UUID> id,
            Function<UUID, String> ownerNameResolver,
            boolean purge) {
        Function<UUID, String> names = ownerNameResolver == null
                ? NexusDeathNodeAdminService::shortOwnerId
                : ownerNameResolver;
        List<T> targets = new ArrayList<>();
        if (candidates != null && matchesCurrentQuery != null && isActive != null) {
            for (T candidate : candidates) {
                if (candidate != null
                        && matchesCurrentQuery.test(candidate)
                        && (purge ? !isActive.test(candidate) : isActive.test(candidate))) {
                    targets.add(candidate);
                }
            }
        }
        targets.sort(Comparator
                .comparing((T target) -> normalizedOwnerName(names, ownerId.apply(target)), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.comparingLong(createdGameTime).reversed())
                .thenComparing(id));
        return List.copyOf(targets);
    }

    public static void handleAction(ServerPlayer player, UUID nodeId, String actionId) {
        handleAction(player, nodeId, actionId, null);
    }

    public static void handleAction(ServerPlayer player, UUID nodeId, String actionId, UUID confirmationToken) {
        if (!canManage(player)) {
            deny(player);
            return;
        }
        String action = actionId == null ? "" : actionId.trim().toLowerCase(Locale.ROOT);
        if (isBatchAction(action)) {
            handleBatchAction(player, action, confirmationToken);
            return;
        }
        if (nodeId == null) {
            player.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.missing_uuid")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        MinecraftServer server = player.level().getServer();
        NexusSpaceUnitSavedData data = units(server);
        Map<UUID, NexusSpaceUnitRecord> unitsById = unitMap(data);
        NexusSpaceUnitRecord unit = unitsById.get(nodeId);
        if (unit == null || unit.type() != SpaceUnitType.DEATH) {
            player.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.not_found")
                    .withStyle(ChatFormatting.RED));
            sendSnapshot(player);
            return;
        }

        switch (action) {
            case ACTION_TELEPORT -> teleportToNode(player, server, unit);
            case ACTION_DISABLE -> disable(player, data, unitsById, unit);
            case ACTION_REQUEST_PURGE -> {
                requestPurgeConfirmation(player, unit);
                return;
            }
            case ACTION_PURGE -> purge(player, data, unitsById, unit, confirmationToken);
            default -> player.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.unsupported_action")
                    .withStyle(ChatFormatting.RED));
        }
        sendSnapshot(player);
    }

    private static void handleBatchAction(ServerPlayer administrator, String action, UUID confirmationToken) {
        MinecraftServer server = administrator.level().getServer();
        NexusSpaceUnitSavedData data = units(server);
        DeathNodeQuery query = activeQuery(administrator);
        switch (action) {
            case ACTION_REQUEST_BATCH_DISABLE -> requestBatchConfirmation(administrator, data, query, false);
            case ACTION_REQUEST_BATCH_PURGE -> requestBatchConfirmation(administrator, data, query, true);
            case ACTION_BATCH_DISABLE -> executeBatchDisable(administrator, data, query, confirmationToken);
            case ACTION_BATCH_PURGE -> executeBatchPurge(administrator, data, query, confirmationToken);
            default -> {
                administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.unsupported_action")
                        .withStyle(ChatFormatting.RED));
                sendSnapshot(administrator);
            }
        }
    }

    private static void requestBatchConfirmation(
            ServerPlayer administrator,
            NexusSpaceUnitSavedData data,
            DeathNodeQuery query,
            boolean purge) {
        List<NexusSpaceUnitRecord> targets = batchTargets(
                unitMap(data).values(),
                ownerId -> ownerDisplayName(administrator.level().getServer(), ownerId),
                query,
                purge
        );
        if (targets.isEmpty()) {
            administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.batch_no_matches")
                    .withStyle(ChatFormatting.YELLOW));
            sendSnapshot(administrator);
            return;
        }

        String action = purge ? ACTION_BATCH_PURGE : ACTION_BATCH_DISABLE;
        DestructiveConfirmation confirmation = PENDING_DESTRUCTIVE_CONFIRMATIONS.issue(
                administrator.getUUID(),
                BATCH_NODE_ID,
                action,
                queryFingerprint(query),
                System.currentTimeMillis(),
                PURGE_CONFIRMATION_DURATION_MILLIS
        );
        administrator.sendSystemMessage(Component.translatable(
                "message.deadrecall.death_node_admin.batch_confirmation_issued",
                targets.size()).withStyle(ChatFormatting.YELLOW));
        sendSnapshot(administrator, query, confirmation);
    }

    private static void executeBatchDisable(
            ServerPlayer administrator,
            NexusSpaceUnitSavedData data,
            DeathNodeQuery query,
            UUID confirmationToken) {
        if (!consumeBatchConfirmation(administrator, ACTION_BATCH_DISABLE, query, confirmationToken)) {
            sendSnapshot(administrator);
            return;
        }
        Map<UUID, NexusSpaceUnitRecord> unitsById = unitMap(data);
        List<NexusSpaceUnitRecord> targets = batchTargets(
                unitsById.values(),
                ownerId -> ownerDisplayName(administrator.level().getServer(), ownerId),
                query,
                false
        );
        long gameTime = administrator.level().getGameTime();
        for (NexusSpaceUnitRecord target : targets) {
            unitsById.put(target.id(), target.withStatus(SpaceUnitStatus.DISABLED, gameTime));
        }
        if (!targets.isEmpty()) {
            data.setDirty();
        }
        auditBatchMutation(administrator, "batch disabled", query, targets.size());
        administrator.sendSystemMessage(Component.translatable(
                "message.deadrecall.death_node_admin.batch_disabled", targets.size()).withStyle(ChatFormatting.GREEN));
        sendSnapshot(administrator);
    }

    private static void executeBatchPurge(
            ServerPlayer administrator,
            NexusSpaceUnitSavedData data,
            DeathNodeQuery query,
            UUID confirmationToken) {
        if (!consumeBatchConfirmation(administrator, ACTION_BATCH_PURGE, query, confirmationToken)) {
            sendSnapshot(administrator);
            return;
        }
        Map<UUID, NexusSpaceUnitRecord> unitsById = unitMap(data);
        List<NexusSpaceUnitRecord> targets = batchTargets(
                unitsById.values(),
                ownerId -> ownerDisplayName(administrator.level().getServer(), ownerId),
                query,
                true
        );
        Set<UUID> targetIds = targets.stream().map(NexusSpaceUnitRecord::id).collect(java.util.stream.Collectors.toSet());
        for (UUID targetId : targetIds) {
            unitsById.remove(targetId);
        }
        if (!targetIds.isEmpty()) {
            data.setDirty();
            removeDiscoveryReferences(administrator.level().getServer(), targetIds);
        }
        auditBatchMutation(administrator, "batch permanently purged", query, targets.size());
        administrator.sendSystemMessage(Component.translatable(
                "message.deadrecall.death_node_admin.batch_purged", targets.size()).withStyle(ChatFormatting.GREEN));
        sendSnapshot(administrator);
    }

    private static void disable(
            ServerPlayer administrator,
            NexusSpaceUnitSavedData data,
            Map<UUID, NexusSpaceUnitRecord> unitsById,
            NexusSpaceUnitRecord unit) {
        if (unit.status() != SpaceUnitStatus.ACTIVE) {
            administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.already_disabled")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        NexusSpaceUnitRecord disabled = unit.withStatus(SpaceUnitStatus.DISABLED, administrator.level().getGameTime());
        unitsById.put(disabled.id(), disabled);
        data.setDirty();
        auditMutation(administrator, "disabled", unit);
        administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.disabled", unit.name())
                .withStyle(ChatFormatting.GREEN));
    }

    private static void purge(
            ServerPlayer administrator,
            NexusSpaceUnitSavedData data,
            Map<UUID, NexusSpaceUnitRecord> unitsById,
            NexusSpaceUnitRecord unit,
            UUID confirmationToken) {
        if (unit.status() == SpaceUnitStatus.ACTIVE) {
            administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.must_disable_before_purge")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!consumePurgeConfirmation(administrator, unit.id(), confirmationToken)) {
            return;
        }

        unitsById.remove(unit.id());
        data.setDirty();
        removeDiscoveryReferences(administrator.level().getServer(), unit.id());
        auditMutation(administrator, "permanently purged", unit);
        administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.purged", unit.name())
                .withStyle(ChatFormatting.GREEN));
    }

    private static void requestPurgeConfirmation(ServerPlayer administrator, NexusSpaceUnitRecord unit) {
        if (unit.status() == SpaceUnitStatus.ACTIVE) {
            administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.must_disable_before_purge")
                    .withStyle(ChatFormatting.RED));
            sendSnapshot(administrator, activeQuery(administrator), null);
            return;
        }

        long nowMillis = System.currentTimeMillis();
        DestructiveConfirmation confirmation = PENDING_DESTRUCTIVE_CONFIRMATIONS.issue(
                administrator.getUUID(),
                unit.id(),
                ACTION_PURGE,
                nowMillis,
                PURGE_CONFIRMATION_DURATION_MILLIS
        );
        administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.purge_confirmation_issued")
                .withStyle(ChatFormatting.YELLOW));
        sendSnapshot(administrator, activeQuery(administrator), confirmation);
    }

    private static boolean consumePurgeConfirmation(ServerPlayer administrator, UUID nodeId, UUID confirmationToken) {
        ConfirmationConsumeResult result = PENDING_DESTRUCTIVE_CONFIRMATIONS.consume(
                administrator.getUUID(),
                nodeId,
                ACTION_PURGE,
                confirmationToken,
                System.currentTimeMillis()
        );
        return switch (result) {
            case CONFIRMED -> true;
            case MISSING -> {
                administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.purge_confirmation_required")
                        .withStyle(ChatFormatting.RED));
                yield false;
            }
            case EXPIRED -> {
                administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.purge_confirmation_expired")
                        .withStyle(ChatFormatting.RED));
                yield false;
            }
            case MISMATCH -> {
                administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.purge_confirmation_mismatch")
                        .withStyle(ChatFormatting.RED));
                yield false;
            }
        };
    }

    private static boolean consumeBatchConfirmation(
            ServerPlayer administrator,
            String action,
            DeathNodeQuery query,
            UUID confirmationToken) {
        ConfirmationConsumeResult result = PENDING_DESTRUCTIVE_CONFIRMATIONS.consume(
                administrator.getUUID(),
                BATCH_NODE_ID,
                action,
                queryFingerprint(query),
                confirmationToken,
                System.currentTimeMillis()
        );
        return switch (result) {
            case CONFIRMED -> true;
            case MISSING -> {
                administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.batch_confirmation_required")
                        .withStyle(ChatFormatting.RED));
                yield false;
            }
            case EXPIRED -> {
                administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.batch_confirmation_expired")
                        .withStyle(ChatFormatting.RED));
                yield false;
            }
            case MISMATCH -> {
                administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.batch_confirmation_mismatch")
                        .withStyle(ChatFormatting.RED));
                yield false;
            }
        };
    }

    private static void teleportToNode(ServerPlayer administrator, MinecraftServer server, NexusSpaceUnitRecord unit) {
        ServerLevel targetLevel = server.getLevel(unit.dimension());
        if (targetLevel == null) {
            administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.dimension_unavailable")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        BlockPos anchor = unit.pos();
        var landing = NexusSpaceUnitAuthority.findSafeLandingNear(targetLevel, anchor, ADMIN_TELEPORT_SEARCH_RADIUS);
        if (landing.isEmpty()) {
            administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.no_safe_landing")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        BlockPos landingPos = landing.get();
        administrator.teleportTo(
                targetLevel,
                landingPos.getX() + 0.5D,
                landingPos.getY(),
                landingPos.getZ() + 0.5D,
                Relative.DELTA,
                administrator.getYRot(),
                administrator.getXRot(),
                false
        );
        auditMutation(administrator, "teleported to", unit);
        administrator.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.teleported", unit.name())
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Records a completed administrative operation locally and forwards a summary to the
     * optional Discord transport. Notification failure is deliberately
     * isolated: the completed operation is never rolled back.
     */
    private static void auditMutation(ServerPlayer administrator, String action, NexusSpaceUnitRecord unit) {
        String actor = administrator.getName().getString();
        String target = unit.name() + " (" + shortOwnerId(unit.owner()) + ", " + shortOwnerId(unit.id()) + ")";
        LOGGER.info(
                "Administrator {} {} death node {} owned by {} at {} {}",
                actor,
                action,
                unit.id(),
                unit.owner(),
                unit.dimension().identifier(),
                unit.pos()
        );
        try {
            NexusOptionalIntegrations.adminAction(actor, "death node " + action, target);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Administrator {} {} death node {}, but Discord audit delivery failed",
                    actor,
                    action,
                    unit.id(),
                    exception
            );
        }
    }

    private static void auditBatchMutation(
            ServerPlayer administrator,
            String action,
            DeathNodeQuery query,
            int affectedCount) {
        String actor = administrator.getName().getString();
        String summary = querySummary(query);
        LOGGER.info(
                "Administrator {} {} {} death nodes with filter {}",
                actor,
                action,
                affectedCount,
                summary
        );
        try {
            NexusOptionalIntegrations.adminAction(actor, "death nodes " + action,
                    affectedCount + " nodes; " + summary);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Administrator {} {} {} death nodes, but Discord audit delivery failed",
                    actor,
                    action,
                    affectedCount,
                    exception
            );
        }
    }

    private static void removeDiscoveryReferences(MinecraftServer server, UUID unitId) {
        removeDiscoveryReferences(server, Set.of(unitId));
    }

    private static void removeDiscoveryReferences(MinecraftServer server, Set<UUID> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) {
            return;
        }
        NexusSpaceDiscoverySavedData data = discovery(server);
        NexusSpaceDiscoverySavedDataAccessor accessor =
                (NexusSpaceDiscoverySavedDataAccessor) (Object) data;
        boolean changed = removeReferences(accessor.deadrecall$getDiscoveredByPlayer(), unitIds);
        changed = removeReferences(accessor.deadrecall$getFavoritesByPlayer(), unitIds) || changed;
        if (changed) {
            data.setDirty();
        }
    }

    private static Map<UUID, Set<UUID>> discoveredByPlayer(MinecraftServer server) {
        NexusSpaceDiscoverySavedDataAccessor accessor =
                (NexusSpaceDiscoverySavedDataAccessor) (Object) discovery(server);
        return accessor.deadrecall$getDiscoveredByPlayer();
    }

    private static boolean removeReferences(Map<UUID, Set<UUID>> referencesByPlayer, Set<UUID> unitIds) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Set<UUID>>> iterator = referencesByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<UUID>> entry = iterator.next();
            if (entry.getValue().removeAll(unitIds)) {
                changed = true;
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        return changed;
    }

    private static Map<UUID, NexusSpaceUnitRecord> unitMap(NexusSpaceUnitSavedData data) {
        return ((NexusSpaceUnitSavedDataAccessor) (Object) data).deadrecall$getUnitsById();
    }

    private static NexusSpaceUnitSavedData units(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
    }

    private static NexusSpaceDiscoverySavedData discovery(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
    }

    private static String ownerDisplayName(MinecraftServer server, UUID ownerId) {
        return NexusSpaceUnitAuthority.playerDisplayName(server, ownerId);
    }

    private static String normalizedOwnerName(Function<UUID, String> names, UUID ownerId) {
        String name = names.apply(ownerId);
        return name == null || name.isBlank() ? shortOwnerId(ownerId) : name;
    }

    private static String deathNodeLocationKey(NexusSpaceUnitRecord unit) {
        return unit.owner() + "|" + unit.dimension().identifier() + "|" + unit.pos().asLong();
    }

    private static String shortOwnerId(UUID ownerId) {
        if (ownerId == null) {
            return "";
        }
        String id = ownerId.toString();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static DeathNodeQuery activeQuery(ServerPlayer player) {
        if (player == null) {
            return DeathNodeQuery.defaults();
        }
        return ACTIVE_QUERIES.getOrDefault(player.getUUID(), DeathNodeQuery.defaults());
    }

    private static DeathNodeQuery resolveOwnerQuery(MinecraftServer server, DeathNodeQuery query) {
        return resolveOwnerQuery(query, ownerName -> {
            if (server == null) {
                return null;
            }
            ServerPlayer online = server.getPlayerList().getPlayerByName(ownerName);
            if (online != null) {
                return online.getUUID();
            }
            return server.services().nameToIdCache().get(ownerName)
                    .map(net.minecraft.server.players.NameAndId::id)
                    .orElse(null);
        });
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBatchAction(String action) {
        return ACTION_REQUEST_BATCH_DISABLE.equals(action)
                || ACTION_BATCH_DISABLE.equals(action)
                || ACTION_REQUEST_BATCH_PURGE.equals(action)
                || ACTION_BATCH_PURGE.equals(action);
    }

    private static String queryFingerprint(DeathNodeQuery query) {
        DeathNodeQuery effectiveQuery = query == null ? DeathNodeQuery.defaults() : query;
        return String.join("|",
                effectiveQuery.ownerQuery(),
                effectiveQuery.dimensionId(),
                effectiveQuery.statusId(),
                Long.toString(effectiveQuery.createdAfterGameTime()),
                Long.toString(effectiveQuery.createdBeforeGameTime()));
    }

    private static String querySummary(DeathNodeQuery query) {
        DeathNodeQuery effectiveQuery = query == null ? DeathNodeQuery.defaults() : query;
        return "owner=" + (effectiveQuery.ownerQuery().isEmpty() ? "*" : effectiveQuery.ownerQuery())
                + ", dimension=" + (effectiveQuery.dimensionId().isEmpty() ? "*" : effectiveQuery.dimensionId())
                + ", status=" + (effectiveQuery.statusId().isEmpty() ? "*" : effectiveQuery.statusId())
                + ", created=" + effectiveQuery.createdAfterGameTime() + "-" + effectiveQuery.createdBeforeGameTime();
    }

    private static String normalizeQuery(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }

    private static void deny(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.deadrecall.death_node_admin.permission_denied")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
