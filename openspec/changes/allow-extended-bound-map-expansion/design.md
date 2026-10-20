## Scope and behavior

"Unlimited" means no vanilla scale-four gameplay cap: users may keep applying
the existing expansion recipe until the original-centered map contains the
whole supported world coordinate domain. The physical game world is finite.
At full coverage, no further result is offered and no paper is consumed.
Width and height double per expansion; the center and dimension never move.
The logical rectangle may extend past supported world coordinates, especially
for off-center maps. Sampling is clipped to its intersection with the supported
domain. Out-of-domain space remains blank, cannot be selected as a destination,
and never causes a chunk request. Full-domain containment terminates expansion.

The preceding independent-use feature remains required: destroying the origin
does not revoke an issued map's portable interface or its authorized teleport
to other valid destinations. Expansion retains its existing loaded-origin gate.
UI zoom is inspection of a map; recipe expansion changes its recorded extent.

## Current evidence

- NexusMapLifecycleAuthority rejects SCALE at MapItemSavedData.MAX_SCALE.
- NexusMapExtendingRecipeMixin currently only vetoes an already matching
  vanilla recipe; permitting later expansion requires checking the complete
  recipe independently of vanilla's scale gate.
- NexusMapBindingSavedData caps historical ancestors at the vanilla max scale.
- FilledMapCoverage, MapResolution and detail transforms assume scales 0..4.
- NexusMapDetailSavedData has per-map and world-wide resident page limits.
  Expanding the geographic extent cannot justify unbounded work or memory.

## Decisions

### Authoritative extent and compatibility

Introduce a versioned, server-owned effective scale/extent in the map binding
ledger. Resolve legacy records from their existing vanilla scale when the new
field is absent. Continue validating map ID, UUID, dimension and exact center;
client item metadata alone cannot authorize extended extent.

Keep vanilla MapItemSavedData within its supported scale range. The Nexus
effective extent controls Nexus sampling, coverage and presentation above that
range. Audit held-item and item-frame map presentation as well as the Nexus
Screen, because clamping the vanilla field alone would otherwise misplace
decorations or resume vanilla sampling at the wrong extent. Required support is
held-item maps, item-frame maps, Nexus map Screens and module-owned Observer
reconstruction; shipping only the Nexus Screen is insufficient. Preserve
ordinary maps unchanged. Any narrower support requires a revised proposal.

Use checked wide coordinate arithmetic. Reject malformed or overflowing extents
before any world lookup, item consumption or state change. Derive the last
useful expansion from Minecraft's supported coordinate bounds, not the current
movable world-border setting. Never generate or load remote chunks to expand.

### Transactional expansion

Validate the actual paper recipe or cartography inputs, server ledger, lock
state, loaded original anchor and next useful extent before producing a result.
Each result gets a new MapId and proven lineage while preserving center, UUID
and appropriate item components. Clones of the same MapId keep the same record.
Server result-taking rechecks mutable inputs; denial consumes neither paper nor
the source map. Creative and survival paths must conserve items according to
their existing semantics. Locking freezes the source's recorded terrain and
cannot receive later updates through shared mutable pages.

### Exploration and bounded terrain storage

Use sparse, map-owned spatial pages and retain their lineage proof. Do not scan
all blocks represented by a coarse pixel, allocate an image proportional to
world area, or trust a summary pixel as proof that its entire footprint was
explored. Pages/pyramids must retain exact recorded subcoverage separately from
overview colors. A target in an unrecorded subregion remains unauthorized unless
the current holder independently has the existing compass authorization for it.

Preserve the existing scale-0..4 recording/authorization semantics for legacy
terrain; do not invent finer proof than the saved records support. New extended
coverage must retain sufficient provenance to prevent overview reduction from
granting previously unknown destinations.

Use bounded resident caches, queues, per-tick sampling and per-request delivery;
persistent terrain may grow only with actually recorded terrain. A disk-backed
page/index design is required before removing the current resident-page caps.
Quota exhaustion must be explicit and preserve existing data; it must not
silently forget exploration or turn unknown terrain into authorized coverage.
If persistent capacity is exhausted, pause writes for new terrain and deny new
expansion transactions before consuming items. Existing recorded terrain stays
readable and usable under the same teleport authorization. Explain the storage
condition with localized, rate-limited player feedback; resume new recording
and expansion when capacity is available. This operational failure is separate
from the removed vanilla gameplay cap. Never evict persistent exploration to
make another expansion succeed.
No implementation may load all historical maps per tick or eagerly generate
every lower-resolution page for the expanded world area.

### Sparse storage foundation boundary

The first standalone primitive uses deterministic flat files keyed by a
server-assigned storage owner namespace and a 128-block tile coordinate. The
namespace is not a native item MapId or a client authorization claim. Mutable
namespace page keys are not shared snapshot references. The immutable index
foundation instead shares content-addressed blobs, so later writes cannot
replace another root's page content. The standalone registry below persists
native MapId/root entries; authoritative lifecycle integration, legacy migration
and live recording remain separate work.

One worker owns file IO, a bounded pending queue and a bounded page LRU. It
scans only file metadata on initialization/quota retry, without loading terrain
or running filesystem operations on server ticks. Files store a versioned key,
revision, colors and an independent exact coverage mask with a checksum. CRC
only detects corruption; server-owned identity and access checks remain required.

The fixed staging file is forced before atomic replacement. Quota/cache update
only after the rename commit point. Directory durability errors after commit
return COMMIT_UNCERTAIN rather than claiming rollback; an identical retry must
recheck durability. Corrupt existing entries retain quota and are never
silently overwritten. Initial metadata failure denies new allocations while
allowing existing reads and explicit asynchronous metadata retry. Accepted work
drains before close completion, and a single owner holds JVM/OS write locks.
This foundation is validated on the local filesystem; platform-specific runtime
and owner shutdown integration are still required before live enablement.

### Immutable sparse snapshot/index boundary

The index is a fixed 19-level quadtree over 128-block tiles. Coordinates are
biased from tile -234375, with the actual world interval checked independently
of the larger 2^19 address square. Branch blocks contain four content hashes
and validated format/depth; terminal page blocks carry tile identity, revision,
colors and exact coverage. Hashes cover the complete versioned typed content.
A nonempty reference to missing/corrupt data fails explicitly; only an empty
child represents an unrecorded page. Old same-leaf coverage cannot shrink,
and same-revision changes are refused relative to the supplied root.

Each root is an immutable storage value. Forking copies that value without IO;
a single update creates at most 19 branch blocks plus one page. Lookups never
walk a version ancestry chain or load all historical pages. The IO store owns
one bounded worker/queue plus separate bounded namespace-page and blob LRUs.
A batch is limited to 20 records/64 KB and verifies existing disk content and
its deduplicated quota before writing. Page and index records share persisted
quota. A new root is returned only after durable OK; capacity refusal writes
nothing. An IO interruption can leave counted, unreachable immutable blocks;
retry reuses and revalidates them instead of double-counting. Index close drains
whole accepted logical operations before closing its underlying IO store.

This root is not authorization or a native item binding. The standalone registry
below supplies storage publication. Integration must authenticate entries against
the native ledger, preserve registered roots during bounded orphan reclamation,
and complete legacy migration and live permission checks before enablement.

### Native MapId/root registry boundary

A standalone privileged registry persists one bounded, versioned, checksummed
row per native MapId on the same IO worker and shared quota. Each row includes
unit identity, dimension, fixed center, effective scale, lock state, revision,
current and previous roots, and a canonical versioned operation receipt. Reads
require exact identity. Publication compares the complete expected entry, not
only its revision or root; a losing concurrent update leaves counted immutable
orphans and cannot replace a newer committed snapshot.

Recording writes immutable blocks before publishing the row. An exact retry
recomputes its result from the expected root and confirms only the same entry;
it cannot rewind a later update. SCALE and LOCK storage forks check source and
target atomically on the worker and share immutable roots without copying terrain.
A completed fork retry preserves its frozen source snapshot even after that
source advances. Locked entries and revision overflow refuse before block writes.
Corrupt entries are not overwritten; a missing expected entry is corruption,
not permission to recreate an old map. Shutdown drains whole accepted operations,
including final publication and cleanup after a cancelled public response.

This registry has no production callers yet. Storage identity validation is not
native-ledger or player authorization. Before old maps can be created/imported,
authoritative lifecycle/birth markers must distinguish first registration from
lost metadata. Migration, native derivation integration and live recording are
pending. Reclamation must retain current, previous and in-flight roots; counted
partial and CAS-loser blocks are not reclaimed by this step.

### Native-ledger import intent boundary

Ledger version 4 persists optional, versioned PREPARED-only import intents.
All existing bindings, including newly bound maps while the old recording path
is active, require legacy import; absent metadata never authorizes empty sparse
registration. A server-thread handoff resolves the exact native MapId and unit
claim, then fixes an immutable ticket UUID, source binding, resolved effective
scale and native lock state. Exact retries retain that ticket; conflicting
sources are refused without replacing it. Derived MapIds prepare independently.
Canonical namespace migration copies intents without changing the legacy object.
The codec rejects duplicate bindings/intents, orphan or conflicting source
proofs, unsupported intent/ledger versions and inconsistent extent metadata.

The intent is not a frozen terrain snapshot, publication receipt, completion
proof or player authorization. No production caller, REGISTERED/activation
transition or legacy-retirement operation is added in this step. The importer
must first durably save PREPARED, freeze/snapshot old recording, derive a complete
terrain manifest and expected root/receipt, then publish and verify the complete
import. Recovery must resume the same ticket using retained old data, including
when the registry row is missing before first publication. Finishing an import
must never be inferred merely from matching registry identity, scale or lock.

### Strict native binding-file load boundary

The module wraps SavedDataStorage.readSavedData only for its binding codec,
covering both canonical and legacy namespaces. Cache hits retain native behavior
without extra filesystem work. A cache-miss read checks NOFOLLOW file metadata;
only an initial NoSuchFileException means absence. Nonregular files/symlinks and
metadata/read failures refuse. Native readTagFromDisk retains Minecraft data
fixing/version handling and registry serialization ops. Only a complete codec
result is accepted; partial decode must not silently drop rejected bindings.

Failures escape outside vanilla's catch and before its cache stores absence.
Thus a broken canonical file cannot fallback to an older legacy ledger; a broken
legacy file cannot authorize creation of an empty canonical ledger. Repeated
reads continue to refuse until an explicit external repair succeeds. No read
failure writes, repairs, replaces or removes a file. Unrelated SavedData retains
native behavior. Confirmed initial absence still permits new-world bootstrap or
non-destructive valid legacy migration.

### Canonical binding-file deletion witness boundary

A fixed eight-byte magic/version witness beside the canonical binding file is
an immutable, monotonic deletion guard. Canonical cache-miss reads probe at most
nine witness bytes; malformed, partial, unsupported or nonregular witnesses fail
closed. A valid witness with a missing canonical file refuses before legacy
fallback or empty creation. Without a witness, old-world canonical-first and
fresh/legacy bootstrap behavior remains compatible. Native cache hits add no IO.
The witness is not a backup, authorization or a terrain-import completion proof.

Establishment is a worker-only barrier called by the explicit metadata owner
below; no player flow automatically starts it yet. The single world owner freezes binding mutations,
join prior native saves and retain that freeze until the barrier returns. The
world/data root must already be durably created. A pure owner-thread capture
fingerprints canonical semantic ledger metadata, including import intents;
codec binding/intent lists are sorted by native MapId for stable serialization.
The worker strict-decodes the existing disk ledger and compares the expected
fingerprint, forces the canonical file plus namespace/data directories, creates
the witness with CREATE_NEW, forces it and the directories, then rechecks the
same expected snapshot before acknowledging. Explicit witness writers serialize;
the primitive itself does not lock native async saves; the owner below supplies
that ordering.

A valid existing witness is only confirmed and forced again on retry, never
truncated. Post-create failures are uncertain commits, not successful barriers
or claimed rollbacks. Partial/corrupt witnesses are never automatically rewritten
or removed and require explicit recovery while preserving the ledger. The live
importer must establish and confirm this barrier before sparse publication or
activation. Pre-witness saves and deletion of both ledger and witness remain
outside this guard; no automatic empty reconstruction is authorized. Current,
previous, in-flight and import roots must be retained before GC.

### Explicit native metadata migration owner

A lazy owner is attached to each native world SavedDataStorage, with one active
session and one worker with a one-item queue. An explicit server-thread prepare
freezes canonical ledger mutations, captures the expected immutable fingerprint,
marks it dirty and calls native scheduleSave. It follows the native future,
including previously queued writes, without joining on a normal server tick.
Storage get/set guards retain the exact frozen canonical object; native map
creation/recipe preflight refuses before allocation or material consumption.
Ledger read authorization remains available. The freeze is transient metadata,
not persisted player authority or a terrain-recording freeze.

Native encoded canonical envelopes receive a transient session epoch, stripped
before every disk write. A per-storage IO lock scopes each native write and
witness operation, never the lifetime of a Lease. After the captured native save
drains, the worker seals canonical IO, forces the world directory so a newly
created data-root entry is durable, and establishes the witness. Frozen/retired
snapshots are deferred even after release instead of replaying old bytes; the
next server-thread save re-snapshots current data. A different frozen snapshot
invalidates the session without writing it. Unrelated/legacy IO keeps native
behavior. A held Lease retains the metadata freeze and must remain valid through
future importer stages; closing it on the server thread resumes mutations.

Cancellation never cancels the internal save/IO pipeline. One idempotent cleanup
releases only its token and flags a fresh save; worker code does not mutate the
ledger maps or dirty flag. Synchronous/asynchronous save failures and dispatch
refusal release ownership. Storage close stops admission, drains internal IO
without requiring a server callback, reclaims any held Lease, shuts down the
worker and then performs the native final save. Shutdown may wait for IO, as
native close already does; normal preparation does not.

No gameplay flow automatically begins this owner yet. Terrain recording is not
frozen, no terrain snapshot/import or completion receipt is produced, and sparse
storage is not activated. Future terrain migration must extend the held-owner
protocol, validate Lease validity, retain old data and obtain complete import
proof before activation. Witness pre-world/double-deletion limits still apply.

### Presentation and Observer contract

Adapt world-to-map transforms, player/target markers, cursor-centered zoom,
selection and detail-page requests to the server-approved effective extent.
Reuse the production Screen and rendering path. If adding extent or viewport
metadata changes a wire/snapshot layout, bump the relevant format/provider
version, enforce monotonic snapshot sequences, and reject incompatible data.
Preserve existing bounded terrain transport; no framebuffer capture or image
streaming. Observer input remains read-only, with no mutation packets.

### Map inherits compass destinations without a list

The user explicitly requires the map to be a compass upgrade and explicitly
rejects a destination list. For a server-resolved registered held map, apply
the current shared identity, active-node and visibility gates first. A node
then qualifies if either the current holder has the existing compass discovery
authorization OR the map has legitimate recorded coverage. Cost, destination
capabilities, safe landing and other route constraints still apply. The player
does not have to carry a compass or expand the map first.

Apply the same union to marker delivery, selection, quote generation, favorites
where supported, start and every countdown revalidation. Loss of one proof is
insufficient to cancel if the other still holds; loss of both must reject or
cancel. Transferred maps never inherit another player's compass discovery.
Preserve the earlier independent-map identity policy and actual permissions;
simply knowing an arbitrary destination UUID never grants access.

Render eligible same-dimension destinations inside the viewport as normal map
markers even on unknown/blank terrain. For eligible destinations outside the
viewport or recorded extent, show clickable edge indicators that select the
exact destination through the existing quote/confirmation flow. Panning and
inspection zoom can move to known destinations outside recorded extent while
the unexplored background remains blank. This changes only the view, not the
stored map center, recorded extent, discovery or geographic expansion cost.
Use existing native-style symbols, integer coordinates, tooltips and narration.

Resolve overlapping edge indicators deterministically without a list: a shared
indicator focuses/zooms to its destinations and renders distinct selectable
markers at that view; truly coincident nodes use bounded icon cycling with
clear hover/focus feedback. Never silently select another node or start a
teleport from cycling. Preserve stable destination IDs across snapshot updates.
Use bounded, authorized spatial requests/delivery so the fixed payload entry
limit does not silently make later compass-known nodes unreachable.

Existing compass-authorized cross-dimension routes remain available subject to
their existing rules and costs. Represent these with a dimension/portal marker
that selects the destination, without inventing a direction on the current
dimension's plane. Do not add terrain maps of other dimensions or expose nodes
that the compass policy would hide. Observer reproduces the same marker state
through the production renderer and suppresses every local selection/action.

### Validation and rollout

First finish and distinguish the separate independent-map regression results.
Then implement extent/persistence, expansion transactions, recording/authority,
and presentation in sequential reviewable steps. Run owning-module JUnit,
server GameTests, build, save/reload and old-world migration checks. UI changes
also require native Client GameTests/screenshots, owning-provider tests,
three-JVM E2E and production-runtime validation under repository instructions.
Obtain independent review of authority, storage bounds and Observer behavior.

## Non-goals

Automatic expansion while walking, newly explored terrain without visiting it,
cross-dimension terrain maps, changes to ordinary maps, removal of teleport permissions
or costs, and release/publication are outside this proposal.


### Incremental legacy detail capture

The legacy detail store has an explicit package-private, owner-thread snapshot
cursor. It freezes authoritative initialize/derive/record operations while
allowing reads and disposable LOD work. Each step processes at most eight
16 KiB pixel pages or map indexes (each index is already limited to 293 refs).
The snapshot retains original page identities, shared map references, historical
pages, empty indexes and unreferenced pages. Pixel arrays are copied once and
exposed only through independent read-only buffers; the completed cursor transfers
its sorted maps without a second unbounded copying pass. Incomplete snapshots
cannot be published. Cancellation, malformed references and explicit close
release only that cursor's freeze, without changing saved terrain or format.

Admission caps map indexes at 8192 and retains the existing 8192-page limit.
At that limit capture can require another 128 MiB of pixel memory plus bounded
metadata; incremental work does not make total capture memory small. Oversized
worlds refuse rather than truncate and need a future streaming migration path.

There is no production caller yet. This is an in-memory detail snapshot, not a
complete world manifest. The future controller must run on the server thread,
retain the metadata Lease, strict-load legacy terrain without lossy fallback,
snapshot native base maps consistently, establish disk durability and produce
full import manifests/receipts before activation. Capture does not yet pause
vanilla base-map updates or persist an import receipt. No freeze flag is saved.


### Strict legacy terrain source reads

The native strict cache-miss reader now handles the exact legacy detail CODEC
as well as binding data. Only an initial NOFOLLOW metadata absence permits
bootstrap. Existing directories, symlinks, malformed NBT, unsupported versions,
partial decoding and disappearance during the native read refuse outside
vanilla's lossy exception handler. Native data fixing and registry operations
remain unchanged; cache hits perform no additional IO. A failed read never
caches absence or creates replacement SavedData, so explicit source repair can
be retried in the same storage instance.

Legacy terrain construction rejects duplicate page IDs and map IDs, absent or
repeated page references, and multiple nonhistorical scale-zero pages for the
same tile within one map. Otherwise the fine index could silently choose one
page while dropping another. Valid sharing across maps, historical pages,
unreferenced pages and empty map indexes retain the existing version-one format,
including saves whose optional version field is absent. No automatic repair or
version bump is introduced.

The existing deletion witness still covers only canonical binding data. Complete
absence of legacy detail remains native bootstrap behavior and is not proof that
a bound map has no terrain to import. The future migration owner needs an explicit
source inventory, consistent native base-map capture and durable full-import
receipts before it can declare migration complete or enable sparse recording.


Strict terrain refusal must not stop an unrelated server tick. A typed read
failure is cached per native storage by the gameplay availability facade, logged
once, and remains unavailable until that storage is reopened after repair.
Direct strict get retains retry behavior; gameplay never substitutes empty
SavedData, retries every tick or saves replacement bytes. Networking, sampling
and detail delivery stop on unavailable terrain. Creation and SCALE/LOCK
preflight refuse before ingredient consumption or MapId/binding commit; the
commit reuses the preflight detail store. Other exception classes still escape.


### Metadata-owned single-map source capture

A held metadata Lease can explicitly capture one existing PREPARED import ticket.
Admission reads only native cache entries: the server's global native MapId store
and the owner's canonical legacy detail store must already contain the exact
sources. Missing cache entries, mismatched geometry/scale/lock/ticket or sticky
terrain refusal fail without disk reads, cache-absence insertion or empty-data
bootstrap. Cold disk staging and provenance remain a later controller obligation.

The native base's 16 KiB pixels and metadata are copied and the legacy detail
freeze is acquired consecutively on the server thread, fixing a T0 source view.
Detail copying continues through its existing bounded cursor (1..8 units/step).
The result retains the exact immutable ticket, native map identity/geometry/lock
and read-only pixel copy, plus the full immutable legacy detail snapshot. Native
pixel recording after T0 may continue and cannot mutate that base copy. Every
step/result checks parent validity, cached source identities and terrain health;
replacement or invalidation refuses and releases child ownership. A partial
capture cannot publish a result. Wrong-thread operations refuse without release.

Only one child capture may be held by a Lease. Child cancellation is idempotent
and preserves metadata ownership; parent Lease close or storage close reclaims
the child before releasing metadata. A stale child's close cannot release a
newer freeze. The child has no independent worker or IO lifecycle.

This is one map's in-memory point-in-time terrain source, not an all-world source
inventory, disk manifest, marker copy, full import receipt or activation proof.
The detail snapshot still copies up to 8192 pages (another 128 MiB plus metadata),
although the native base is a bounded 16 KiB. The future controller must stage
strict durable native/detail sources, prove complete presence, and freeze or
reconcile later native pixel updates before cutover. No production flow starts
capture automatically and no sparse activation is enabled.


### Lease-owned disk source observation

After T0 capture completes, its server-thread API can schedule one read-only
verification on the existing bounded owner worker. Admission uses native source
paths and DataFixer instances from each owning storage. Native/detail input is
limited to regular NOFOLLOW files of 4 MiB/256 MiB, with bounded NBT heap quotas
of 8 MiB/256 MiB. Compressed and plain NBT retain native data-fixing/registry
semantics; original future DataVersion, malformed/partial data, native pixel
length/scale normalization and full detail structural failures refuse.

Canonical semantic hashes include all captured pixels, geometry/lock/dimension,
all detail pages including orphans, and map index/reference order. Raw SHA-256 is
computed from the exact bounded raw stream used to decode, including complete
EOF, then compared with a fresh post-read file hash. A separate pre-read hash
cannot establish this relation: A-to-B-to-A replacement could otherwise attach
A's hash to B's validated terrain. Input disappearance/change refuses; no source
file is created, repaired, rewritten or forced by verification.

The public readiness future carries no evidence object. The server-thread
observation accessor rechecks capture/Lease/cache/health state before returning
the private worker result. Child/parent close cancels readiness; the internal IO
future still drains. New captures/sessions refuse while a previous disk reader
is pending. Normal ticks never join IO; storage shutdown alone waits for the
internal drain, without any queued server callback dependency. Failed evidence
retrieval closes the child while preserving the parent's explicit lifetime.

This records observed source bytes and T0 semantic equality, not fsync durability,
an immutable manifest, full-world inventory, import completion or activation.
Subsequent native saves can invalidate a T0 comparison; a later controller must
stage stable sources and freeze/reconcile updates before cutover. There is no
automatic player-facing verification caller and no new sparse activation path.


### Durable per-ticket source observation record

The standalone worker-only source manifest stores a fixed 252-byte, versioned
record with a SHA-256 checksum. It binds the import ticket and MapId, canonical
ledger fingerprint, complete intent fingerprint, and both raw-file and semantic
terrain fingerprints. The intent digest includes unit identity, dimension and
anchor coordinates, original center, binding/resolved scales, lock state and
ordered detail ancestry. It stores no paths or mutable terrain buffers.

Publication requires an already durably created dedicated directory owned by
the future migration controller. External writers must be serialized by that
owner; the helper also serializes its own in-process publishers. CREATE_NEW
prevents any overwrite or repair of a prior ticket record. Complete records
are forced, followed by the directory, and strictly reread before acknowledgment.
Existing records must match all fields exactly and repeat the force barrier.
There is no rename/replacement fallback. Partial creations remain in place and
refuse subsequent reads/retries rather than silently becoming missing records.

Cold reads enforce regular-file/no-follow metadata, exact bounded length,
checksum, current version and filename ticket identity. Cancellation/failure
after creation is an uncertain commit; a complete exact retry may acknowledge
it, while partial/corrupt/conflicting bytes remain untouched. Missing discovery
results cannot establish permission to recreate a record known to have existed.

Only the observation record is durably saved. Original native/detail files are
not backed up or made immutable, and can still change after observation. This
step provides neither a full-world inventory nor an import-completion receipt.
It is not connected to production callers or the Lease owner yet; future
integration must stage durable source bytes, retain ownership, prove complete
inventory and verify imported roots before activation.


### Lease-owned source manifest publication

A completed SourceCapture with checked disk evidence can explicitly request
persistManifest. On the server thread it binds the observation to the held
metadata session fingerprint and immutable intent. Only the existing bounded
owner worker performs filesystem work. It creates the canonical namespace's
nexus_map_sources child without following/replacing conflicting entries and
forces that child's parent before invoking immutable manifest publication. The
metadata witness has already established durability of the namespace ancestors.

Publication runs after verification on the same single worker, so the latest
pending-disk drain also covers a prior reader whose readiness finished before
its final cleanup. Duplicate requests reuse a Void readiness future; private
manifest evidence is obtainable only through the server-thread checked getter,
which revalidates parent, caches and terrain health. A failed checked retrieval
closes the child while retaining parent ownership. Child/parent release cancels
readiness, invalidates publication and releases freezes. New sessions/captures
remain excluded until background IO has drained; storage shutdown waits for that
drain without depending on a queued server callback.

Native test gates pause outside the standalone publisher's global monitor,
preventing test cleanup from waiting on another owner's gate that needs server
ticks. The production hooks are no-ops. Cancellation is checked before/after
the pre-publication hook and before directory IO, as well as during publication.

This is an explicit foundation API with no automatic production entry point.
The persisted record proves an observation under metadata ownership, not that
mutable source bytes remain stable, every world source is inventoried, or terrain
has been imported. It does not establish a persistent known-manifest deletion
guard; later inventory/source-staging/receipt integration must supply that proof
before cutover. Existing conflicting or partial records are never repaired.


### Complete held metadata/source requirements inventory

A completed SourceCapture may create one incremental inventory cursor borrowing
the exact held metadata freeze and its already immutable full detail snapshot.
It enumerates every canonical binding, including bindings without PREPARED
intents, and retains each complete binding and optional exact ticket. Missing
tickets are explicitly listed; an absent intent never removes the source.
The required native-ID union includes all bindings, their ordered ancestry,
and every detail-map index, including empty or unbound indexes. All frozen detail
pages, including historical/shared/unreferenced pages, remain in the snapshot
without another pixel copy.

A step visits at most 1..8 bindings or detail indexes. Each binding has at most
20 ancestors. Preflight refuses more than 8192 bindings or detail indexes; the
unique native-source union is capped at 65536. Count, conflicting binding/ticket,
duplicate ticket and invalid source-ID failures refuse partial publication.
Completed sorted collections transfer ownership to immutable wrappers without
a final unbounded collection copy. The complete immutable detail snapshot is
shared directly with the result. No cache load, disk access, ticket creation,
terrain recording or persistent metadata mutation occurs.

Every step and result call checks the source child's server-thread/Lease/cache/
health guard and exact original metadata freeze token. Parent release, child
release, cache replacement, quarantine or a later freeze cannot authorize an
old cursor. Inventory failure closes the child while retaining the parent Lease.
Already returned immutable data remains readable but is not transferable live
authority. Partial cursors expose no result.

This is a complete requirements inventory within the held binding ledger and
frozen detail store. It neither scans all unrelated native map files nor proves
that required native files exist. Physical presence, source geometry validation,
durable source-byte staging and an immutable complete inventory manifest still
precede import receipts and activation. Unprepared rows need validated intents
in a later session before they can be imported.


### Immutable raw source-byte staging

A standalone worker-only helper stores exact raw native/detail bytes under
kind-qualified SHA-256 names. It accepts the verified observation hash, streams
with a 64 KiB buffer, checks the complete stream hash and initial byte length,
and refuses growth beyond both per-file and persistent quota. Native sources
are capped at 4 MiB and detail sources at 256 MiB. This helper does not decode
NBT or infer semantic validity from arbitrary bytes; the caller must later
strictly verify staged bytes against its immutable source snapshot and intent.

The caller owns an already durably created dedicated directory and serializes
external writers. Helper publications serialize in-process. A metadata scan
counts existing source names and abandoned pending files, rejects unexpected or
non-regular entries, and enforces explicit file-count/total-byte limits. New
publication reserves TWO names and twice the source length, because a crash
can retain both pending and final hard links to the same inode. The streaming
cap is no more than half the remaining byte budget. New creation therefore
requires two free name slots even though successful cleanup leaves one file.
Scans conservatively count hard-link names separately after restart.

New content is written to an owned CREATE_NEW pending file and forced before
publication. A CREATE_NEW hard link publishes the final name without replacing
another file; unsupported hard links fail closed with no rename fallback. Only
the pending file created by that operation is removed, followed by a directory
force and strict final hash/size readback. Original sources and existing blobs
are never rewritten or repaired. Pre-publication failure removes the owned
pending file when possible. A post-link failure is an uncertain commit; complete
exact retries reverify and force the existing blob and directory without opening
the original source, so the source may already be deleted. Corrupt existing
blobs refuse instead of falling back to the original.

This step is standalone and has no production caller. It provides raw immutable
storage, not a full source inventory, metadata ownership, a known-blob deletion
guard, import receipt or activation proof. Abandoned pending files remain quota
charges until future bounded reclamation. Owner/inventory integration, strict
staged-source semantic verification and durable complete inventory publication
remain required before import/cutover.


### Lease-owned staged source pair

A SourceCapture with a checked persisted observation manifest may explicitly
request stageSources. The server thread captures immutable descriptors and
expected hashes; only the existing bounded owner worker creates the canonical
namespace's nexus_map_source_blobs child and forces its parent. World staging
limits are 65536 names and 1 GiB, conservatively including pending names and the
two-name publication peak; per-source native/detail limits remain unchanged.

The worker durably stages the exact native and detail raw hashes named by the
held manifest. It then strictly decodes BOTH saved files using the original
storage DataFixers/registry context and immutable T0 source snapshot, and requires
the resulting raw/semantic observation to equal the original held observation.
Only then is private immutable pair evidence made available through a checked
server-thread getter. Readiness carries no proof; a changed cache/Lease/terrain
health or closed child prevents evidence access. Repeated requests reuse one
readiness response. Test gates remain outside global staging publication locks.

The latest pending worker drain orders earlier reader/manifest cleanup and
blocks later captures/sessions until staging finishes. Child/parent close cancels
readiness and releases freezes. Storage shutdown drains without requiring a
queued server callback. Partial or uncertain failures never expose a pair, and
checked failure retrieval closes only the child. Published blobs remain in place
when the other source or final semantic verification fails; they may be shared
and must not be deleted as rollback. Existing exact blobs can be retried by the
staging helper without opening a deleted original.

This API covers one captured native map plus the full frozen legacy detail
store. It does not stage all ancestor/unbound native IDs in the requirements
inventory, provide a complete persisted inventory/receipt, or complete cold
recovery. Native recording after T0 still requires freeze/reconciliation before
cutover. Previously returned evidence does not authorize skipping physical
reverification during later import. There is no automatic production entry
point, import, live sparse activation or release in this step.


### Strict cold native observations for remaining inventory IDs

The verifier can now observe a single native map file without requiring a
cached SavedData, import ticket or detail file. A trusted controller must supply
the canonical native storage descriptor and inventory MapId: native map bytes
do not embed that ID, so the observer cannot prove filename/binding/ancestry
identity by itself. No automatic caller is introduced here.

Observation reuses the existing bounded native reader (4 MiB raw/8 MiB NBT),
original DataFixer, exact opened-stream hash, complete codec decode, mandatory
16384-byte pixels and explicit scale validation. Centers outside the supported
world domain refuse. Returned pixels are an immutable 16 KiB copy; the semantic
fingerprint includes the supplied ID, center, dimension, native scale, lock and
pixels. A fresh final path hash must equal the raw stream actually decoded;
changed/deleted inputs, unsupported data, quota errors and cancellation refuse
without creation, cache load, repair or writes. Shared native decode extraction
preserves the original single-map pair verifier's behavior.

This is one file's read observation, not an fsync-backed saved source, a shared
T0 across many maps, binding/ancestry proof or complete physical inventory.
Future inventory staging must validate relationships and source versions, save
these exact bytes and reconcile later native recording before import/cutover.


### Complete native portion of inventory staging

A standalone worker helper consumes the immutable requirements snapshot and
trusted canonical native descriptors. Before filesystem work it requires exact
ID-set coverage (at most 65536), non-null descriptors and unique normalized paths,
then defensively selects them in sorted order and rechecks coverage. Each source
is strictly cold-observed, checked against any binding/prepared extent and lock,
saved by exact raw fingerprint under persistent quotas, and strictly decoded
again from saved bytes. Raw and semantic fingerprints must match.

Only bounded source metadata enters the completed sorted immutable result:
ID, center, dimension (at most 256 characters), native scale/lock, blob descriptor
and semantic hash. No per-file pixel snapshot is retained across the batch.
The held ledger fingerprint and explicit unprepared set remain attached. All
required sources are included, even unprepared bindings and unbound detail
indexes. Original sources may be deleted after saving; blob verification does
not need them. A new batch still cold-observes originals; cold restart from a
persisted batch receipt is a separate pending controller operation.

After all files are saved, ancestry must match parent center/dimension and the
expected native scale for each ordered historical level. Bound ancestors must
also retain the same unit/anchor and exact resolved logical scale; clamping both
native maps to scale 4 cannot conceal a conflicting logical level or cycle.
Unbound historical sources carry geometry proof only, since native bytes embed
neither MapId nor unit identity. Trusted descriptors/metadata remain essential.

Missing/extra inputs, changed/corrupt sources, binding or ancestry conflicts,
quota failure and cancellation expose no partial batch result. Already published
blobs are retained for retry/recovery rather than deleted as rollback. There is
no final full-map copy when immutable result wrappers take ownership.

This helper has no automatic/owner entry point yet. It stages only the native
portion, not the separate detail source, and does not establish a common T0 or
a durable complete batch receipt. Owner lifecycle integration, pairing with the
held detail proof, persisted complete physical inventory, cold recovery and
recording freeze/reconciliation remain required before import and activation.


### Lease-owned complete native batch joined to saved detail

A SourceCapture with completed requirements inventory and checked staged primary/
detail evidence prepares canonical native descriptors with prepareBatch(1..8).
Each server-thread call visits at most that many IDs and performs only path/type
construction, never loading native SavedData or reading files. A TreeMap avoids
server-thread hash-table growth spikes. Descriptor failure releases the child.
The primary descriptor points to its already saved immutable native blob, so
later recording or original deletion cannot replace its captured T0 source.
Remaining IDs use the server-global canonical native storage descriptors.

Only completed preparation admits stageBatch. The existing owner worker stages
the exact native requirements through the standalone batch helper, checks the
held metadata hash and exact primary blob/semantic identity, then strictly
revalidates the saved primary/detail pair against the original immutable source
and observation. Only a complete successful join publishes private immutable
StagedInventory evidence. Void readiness, duplicate-request memoization, checked
server-thread retrieval and failed-getter child release follow existing rules.
The latest pending IO drain covers prior queued cleanup; child/parent close
cancels readiness and storage shutdown drains without a queued server callback.
All test waiting gates remain outside publication locks.

A missing extra source or damaged saved detail refuses the combined batch even
when native blobs already exist. Those published blobs remain intact; no rollback
can delete shared source bytes. Unprepared/uncached sources remain explicit and
are not loaded into native storage. Completed data remains a held observation,
not authority to skip later physical checks. No persisted batch-completion
receipt, restart controller or live activation is introduced here. The primary
and detail retain captured T0; all other native files are observed separately,
so whole-world recording freeze/reconciliation remains required before cutover.


### Durable saved-source inventory receipt

A standalone worker-only receipt stores the checked staged-inventory result:
primary ticket/manifest (held ledger and intent hashes plus raw/semantic pair
hashes), saved detail length, every native source metadata/blob descriptor and
the explicit unprepared set. Primary native and detail identity must remain
consistent. Capture rejects a mismatching held ledger or primary blob. All
collections are defensively copied into sorted immutable data on the worker.

The versioned binary format is bounded to 24 MiB, 65536 native rows and 8192
unprepared IDs. It uses bounded streaming IO rather than a full encoded byte
array. Reader checks filename UUID, version, exact native/unprepared ordering,
strict booleans, finite coordinates, native scales, dimension syntax/length,
blob kinds/sizes and primary linkage. SHA-256 covers the entire body; exactly
one 32-byte trailer and EOF are required, and the opened file length must match
its initial metadata. Invalid/partial/future receipts never become absence.

Publication requires a caller-owned already durable dedicated directory and
external writer exclusion. CREATE_NEW never replaces or repairs an old record.
The file and directory are forced and the complete record strictly reread before
acknowledgment. Exact retry rechecks/forces existing data without rewriting it.
Creation/force/cancellation failures after possible creation are uncertain
commits; a complete exact retry may acknowledge, while partial/corrupt files
remain in place and refuse. This is not a persistent deletion/recreation guard.
Aggregate directory quota and bounded reclamation remain caller/controller
requirements before production integration.

This is a durable saved-source checkpoint, not a terrain-import receipt. Cold
metadata read does not verify referenced blob bytes or prove completeness
against a newly held ledger/detail inventory. Owner publication, required-set
comparison, strict physical-source recovery, global recording reconciliation,
import and activation are separate pending operations. There is no automatic
production caller or common-T0 claim.


### Lease-owned saved-inventory receipt publication

SourceCapture may publish the complete inventory only after checked batch staging.
It captures path descriptors on the server thread and constructs receipt metadata
on the owner worker. Before publication, every saved native blob is rehashed,
strictly decoded, and compared with its recorded raw/terrain hash and geometry;
the saved detail blob is rehashed and the primary/detail pair is strictly decoded
against the held source snapshot. This revalidation is a point observation under
external writer exclusion, not a common terrain T0 or cold recovery controller.

The worker durably creates the dedicated receipt directory and reserves quota
before invoking the immutable receipt publisher. The directory allows 64 regular
receipt filenames and 256 MiB total physical lengths; a new record reserves the
full 24 MiB record limit. Partial/corrupt regular files remain quota charges;
unrecognized names, links and oversized entries refuse. Nothing is reclaimed or
repaired automatically. The directory has one owner and excludes external writers.

Readiness carries no receipt. The server-thread getter rechecks Lease, child,
cache identity and storage health before exposing private immutable evidence.
Cancellation and storage shutdown invalidate retrieval and retain true worker
drain ownership without requiring a server callback. Failed or uncertain writes
cannot become held evidence. Cold receipt recovery still needs required-set
comparison against newly held metadata plus physical blob verification. Known
record deletion protection, import, recording reconciliation and activation remain
pending; this API has no automatic gameplay caller.


### Strict cold saved-inventory recovery verification

A worker-only, read-only verifier can reopen a required durable inventory receipt
and compare it with a freshly held immutable inventory. Receipt absence is an
error, never permission to recreate it. Ledger fingerprint, exact native source
set, unprepared bindings and selected primary ticket must agree. Staging and
recovery share binding/ordered-ancestor geometry, dimension, logical scale,
unit and anchor validation. Every saved native blob must match its recorded raw
hash/length and strict decoded terrain/geometry; valid hashes alone do not allow
malformed NBT. Only the primary immutable base is retained temporarily.

The detail blob must match raw hash/length and strict full detail semantics of
the held inventory, including empty indexes and unreferenced/historical pages.
The decoded primary plus held detail snapshot must reproduce the exact stored
source manifest, including the selected intent fingerprint. A private result
constructor exposes immutable receipt metadata only after every check succeeds.
Cancellation returns no partial result. The verifier never reads original map
files, stages missing files, repairs receipts, or writes activation state.

The caller still owns fresh inventory/metadata lifetime, worker scheduling and
external writer exclusion. This verifier supplies point-in-time observations,
not a common terrain T0 or proof that recording since the backup can be ignored.
Lease-owned recovery cancellation/shutdown handling, deletion protection,
recording reconciliation, import and activation remain separate work.


### Lease-owned cold inventory recovery

A fresh SourceCapture may request cold recovery only after incremental held-detail
capture and the complete requirements inventory are finished. Recovery and new
source observation are exclusive branches: recovery refuses after disk observation
has been requested, and observation refuses once recovery is requested. Recovery
also requires idle actual owner IO before scheduling, so its latest drain cannot
mask older work or overlap the publication branch.

The server captures only fixer/path metadata and the held immutable inventory.
The owner worker performs strict receipt/blob verification and completes Void
readiness; the verified result stays private. The server-thread getter rechecks
parent Lease, child lifetime, native/detail cache identity and storage health.
Cancel, child/parent close and storage shutdown invalidate access and drain actual
IO without waiting for server callbacks. Errors close only the child on checked
retrieval. Missing/corrupt receipts or blobs are preserved without original-file
fallback, repair or recreation.

This is explicit recovery under an existing cache-only capture, not automatic
startup discovery or bootstrap of missing native caches. It does not reconcile
terrain recorded since backup, establish a common T0, import terrain or activate
extended maps. Those obligations still gate production integration.


### Opt-in selected-native recording freeze

Lease.captureFrozenSource acquires an object-scoped native recording token and
full-detail migration snapshot consecutively on the server thread before copying
the selected native base. Ordinary captureSource retains its existing point-copy
behavior. The native mixin suppresses setColor and returns false from updateColor
only while this exact map object is held; unheld maps and client copies continue
normally. This pauses recording and is not a persistent map lock.

The selected native hold is server-thread-only and exclusive by token identity.
Child/parent close, failed acquisition, cache replacement and storage shutdown
release owned holds. Idempotent stale close cannot release a later token. A
frozenSnapshot accessor requires both completed detail capture and the optional
native hold. Every checked capture operation also compares the live selected
native pixel buffer to the immutable base (bounded 16 KiB) and checks hold/cache
identity. Direct public colors-array writes cannot be intercepted; disagreement
invalidates the child and prevents checked evidence retrieval. External writers
must still be excluded; transient ABA writes are not claimed detectable.

This establishes a held selected native base and full immutable detail snapshot,
not a common T0 across every native map in the requirements inventory. Other
native sources require their own recording ownership and reconciliation before
terrain import or activation. Startup automation, saved-source reconciliation,
legacy import and activation remain unimplemented.


### Production-first server expansion path

The user prioritizes completing gameplay over further recovery infrastructure.
The live path therefore reuses the existing atomic legacy sparse detail SavedData
instead of requiring the experimental immutable-store migration framework. Paper
recipes on the workbench and cartography table may increase the persisted logical
extent beyond native scale four, retaining the original center. Native map data
stays within Mojang's scale range; beyond four its existing pixels keep their true
scale-four coordinates instead of being stretched or assigned false coverage.
Each derivation retains the source base as a historical layer, including scale
four, and inherits fine exploration through existing copy-on-write references.

Loaded-only route sampling receives the validated binding extent. Extended maps
may record nearby loaded terrain outside the native footprint but inside supported
world/logical bounds; ordinary maps retain the painted-native-pixel prerequisite.
The existing per-tick read/deadline/work budgets remain unchanged. No chunk is
force-loaded by sampling. Terrain ownership remains per map and survives the same
strict detail codec. The per-map sparse page allowance now shares the existing
8192-page world bound; this is bounded storage, not unlimited exploration storage.

Pure conservative preflight reserves ancestor initialization and one historical
snapshot before ingredients or MapIds are consumed. At capacity expansion/locking
refuse without evicting old exploration. The original lodestone must still be
loaded and present for expansion; independent map use retains its prior rules.
Workbench override requires the actual recipe pattern and exact paper/map grid;
cartography previews are produced only for server-validated eligible Nexus maps.

This server path does not finish the user-facing feature. The client viewport,
logical-extent presentation and module-owned Observer still need integration, as
does current-holder compass-equivalent off-map destination authorization. Native
map pixels/packets must not be relabeled with an unsupported vanilla scale. No
production gameplay caller depends on the experimental recovery components.
