package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Private metadata/detail storage, real server-native cache and production Lease lifecycle. */
public final class NexusMapSourceCaptureGameTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    @GameTest(maxTicks = 300)
    public void frozenCaptureBlocksNativeAndDetailRecordingThenResumes(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureFrozenSource(f.id); refuses(capture::frozenSnapshot);
            f.map.setColor(0, 0, (byte) 8); check(!f.map.updateColor(0, 0, (byte) 12), "Native update escaped freeze");
            check(f.map.colors[0] == 4, "Native set escaped freeze");
            check(!f.detail.record(helper.getLevel(), f.id.id(), 0, 0, (byte) 8), "Detail escaped freeze");
            f.tick = () -> {
                if (!capture.step(1)) return;
                var snapshot = capture.frozenSnapshot(); check(snapshot.base().colors().get(0) == 4, "Frozen native snapshot changed");
                int page = snapshot.detail().maps().get(f.id.id()).getFirst();
                check(snapshot.detail().pages().get(page).colors().get(0) == 4, "Frozen detail changed");
                capture.close(); capture.close();
                check(f.lease.valid() && f.map.updateColor(0, 0, (byte) 12), "Child close did not resume native recording");
                check(f.detail.record(helper.getLevel(), f.id.id(), 0, 0, (byte) 12), "Child close did not resume detail");
                check(snapshot.base().colors().get(0) == 4, "Released snapshot became mutable"); done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void normalCaptureCannotClaimFrozenTerrain(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); refuses(capture::frozenSnapshot);
            check(f.map.updateColor(0, 0, (byte) 8), "Normal capture blocked native recording");
            check(capture.result().base().colors().get(0) == 4, "Normal base was not isolated"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void directPixelTamperingInvalidatesFrozenEvidenceAndReleasesHolds(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureFrozenSource(f.id); while (!capture.step(8)) { }
            f.map.colors[0] = 8; refuses(capture::frozenSnapshot); refuses(capture::result);
            check(f.lease.valid() && f.detail.acceptsMutation(), "Tampered freeze leaked child or released parent");
            check(f.map.updateColor(0, 0, (byte) 12), "Tampered freeze did not release native"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void parentCloseReleasesFrozenNativeAndDetail(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureFrozenSource(f.id); f.lease.close();
            refuses(capture::frozenSnapshot); check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Parent close leaked freezes");
            check(f.map.updateColor(0, 0, (byte) 8), "Parent close did not release native"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void storageCloseReleasesFrozenNativeAndDetail(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureFrozenSource(f.id); f.storage.close(); f.storageClosed = true;
            refuses(capture::frozenSnapshot); check(f.detail.acceptsMutation(), "Shutdown leaked detail hold");
            check(f.map.updateColor(0, 0, (byte) 8), "Shutdown leaked native hold"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void failedDetailFreezeCannotLeakAcquiredNativeHold(GameTestHelper helper) {
        run(helper, true, f -> {
            var detailHold = f.detail.beginMigrationSnapshot(); helper.runBeforeTestEnd(detailHold::close);
            refuses(() -> f.lease.captureFrozenSource(f.id));
            check(f.map.updateColor(0, 0, (byte) 8), "Failed capture leaked native hold");
            check(!f.detail.acceptsMutation(), "Failed capture released another detail hold");
            detailHold.close(); var capture = f.lease.captureFrozenSource(f.id);
            while (!capture.step(8)) { } check(capture.frozenSnapshot().base().colors().get(0) == 8, "Retry lost latest pixels"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void nativeFreezeTokensExcludeOtherOwnersAndRespectServerThread(GameTestHelper helper) {
        run(helper, true, f -> {
            var first = NexusMapTerrainFreeze.acquire(helper.getLevel().getServer(), f.map); helper.runBeforeTestEnd(first::close);
            refuses(() -> f.lease.captureFrozenSource(f.id)); check(f.detail.acceptsMutation(), "Native conflict acquired detail hold");
            first.close(); var second = NexusMapTerrainFreeze.acquire(helper.getLevel().getServer(), f.map); helper.runBeforeTestEnd(second::close); first.close();
            var attempt = CompletableFuture.runAsync(() -> {
                threadRefuses(() -> NexusMapTerrainFreeze.acquire(helper.getLevel().getServer(), f.map));
                threadRefuses(second::close); threadRefuses(second::valid);
            });
            f.tick = () -> {
                if (!attempt.isDone()) return;
                attempt.join(); check(second.valid() && !f.map.updateColor(0, 0, (byte) 8), "Stale close or wrong thread released hold");
                second.close(); check(f.map.updateColor(0, 0, (byte) 8), "Native hold did not release"); done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void cacheReplacementReleasesFrozenOldNativeObject(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureFrozenSource(f.id); while (!capture.step(8)) { }
            helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                    MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
            refuses(capture::frozenSnapshot); check(f.detail.acceptsMutation(), "Cache replacement leaked detail hold");
            check(f.map.updateColor(0, 0, (byte) 8), "Cache replacement leaked old native hold"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void consistentSnapshotSurvivesLaterNativeUpdatesAcrossTicks(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureSource(f.id);
            refuses(capture::result);
            refuses(() -> f.lease.captureSource(f.id));
            check(!capture.step(1), "Detail copy was not incremental");
            f.map.setColor(0, 0, (byte) 8); // Native recording may continue after the T0 copy.
            check(!f.detail.record(helper.getLevel(), f.id.id(), 0, 0, (byte) 8), "Detail freeze escaped");
            f.tick = () -> {
                if (!capture.step(1)) return;
                var result = capture.result();
                check(result.intent().equals(f.intent), "Capture changed the import ticket");
                check(result.base().colors().get(0) == 4 && f.map.colors[0] == 8, "Native T0 pixels were not isolated");
                int page = result.detail().maps().get(f.id.id()).getFirst();
                check(result.detail().pages().get(page).colors().get(0) == 4, "Detail snapshot was not T0");
                capture.close();
                check(f.lease.valid(), "Child close released metadata ownership");
                check(f.detail.record(helper.getLevel(), f.id.id(), 0, 0, (byte) 12), "Detail recording did not resume");
                check(result.detail().pages().get(page).colors().get(0) == 4, "Result changed after release");
                done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void childCancelIsIdempotentAndParentCloseReclaimsNewCapture(GameTestHelper helper) {
        run(helper, true, f -> {
            var old = f.lease.captureSource(f.id); old.step(1); old.close();
            var current = f.lease.captureSource(f.id); old.close();
            check(!f.detail.acceptsMutation(), "Old child released a later freeze");
            f.lease.close();
            check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Parent close leaked a freeze");
            refuses(() -> current.step(1)); refuses(current::result); refuses(() -> f.lease.captureSource(f.id));
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void storageCloseReclaimsIncompleteChild(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureSource(f.id); capture.step(1);
            f.storage.close(); f.storageClosed = true;
            check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Storage close leaked source freeze");
            check(!f.lease.valid(), "Storage close left live metadata lease");
            refuses(capture::result);
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void missingTicketOrUncachedSourcesRefuseWithoutLoadingOrFreezing(GameTestHelper helper) {
        run(helper, false, f -> {
            refuses(() -> f.lease.captureSource(f.id));
            refuses(() -> f.lease.captureSource(new MapId(Integer.MAX_VALUE)));
            check(f.detail.acceptsMutation(), "Missing ticket froze detail");
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void absentNativeCacheDoesNotReadDisk(GameTestHelper helper) {
        run(helper, true, false, f -> {
            refuses(() -> f.lease.captureSource(f.id));
            check(f.detail.acceptsMutation(), "Missing native cache froze detail");
            check(((NexusMapMigrationOwner.StorageAccess) helper.getLevel().getServer().getDataStorage())
                    .totem$cachedData(MapItemSavedData.type(f.id)) == null, "Capture cached native absence");
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void absentDetailCacheNeverBootstrapsAnEmptySource(GameTestHelper helper) {
        run(helper, true, true, false, f -> {
            refuses(() -> f.lease.captureSource(f.id));
            check(((NexusMapMigrationOwner.StorageAccess) f.storage).totem$cachedData(NexusMapDetailSavedData.TYPE) == null,
                    "Capture loaded or created empty detail data");
            check(f.detail.acceptsMutation(), "Uncached detail was frozen");
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void quarantinedDetailInvalidatesAndReleasesChild(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureSource(f.id);
            var state = (NexusMapBindingStorage.TerrainFailureAccess) f.storage;
            state.totem$terrainFailure(new NexusMapBindingStorage.ReadFailure(NexusMapDetailSavedData.TYPE, null));
            refuses(() -> capture.step(1));
            check(f.detail.acceptsMutation(), "Quarantine leaked detail freeze");
            refuses(() -> f.lease.captureSource(f.id));
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void cachedNativeMismatchRefusesBeforeFreeze(GameTestHelper helper) {
        run(helper, true, f -> {
            var other = MapItemSavedData.createFresh(1024, 0, (byte) 0, false, false, Level.OVERWORLD);
            helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id), other);
            refuses(() -> f.lease.captureSource(f.id));
            check(f.detail.acceptsMutation(), "Mismatched native map froze detail");
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void replacingDetailInvalidatesAndReleasesOriginalFreeze(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureSource(f.id);
            f.storage.set(NexusMapDetailSavedData.TYPE, new NexusMapDetailSavedData());
            refuses(() -> capture.step(1)); refuses(capture::result);
            check(f.detail.acceptsMutation() && f.lease.valid(), "Identity refusal leaked child/parent state");
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void replacingNativeInvalidatesBeforeReturningResult(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureSource(f.id);
            while (!capture.step(8)) { }
            var replacement = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
            helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id), replacement);
            refuses(capture::result);
            check(f.detail.acceptsMutation(), "Native replacement leaked child freeze");
            done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void wrongThreadCannotAdvancePublishOrRelease(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureSource(f.id);
            var attempt = CompletableFuture.runAsync(() -> {
                threadRefuses(() -> capture.step(1)); threadRefuses(capture::result);
                threadRefuses(capture::close); threadRefuses(() -> f.lease.captureSource(f.id));
                threadRefuses(capture::verifyDisk); threadRefuses(capture::diskObservation);
                threadRefuses(capture::persistManifest); threadRefuses(capture::manifest);
                threadRefuses(capture::inventory);
                threadRefuses(capture::stageSources); threadRefuses(capture::stagedSources);
                threadRefuses(() -> capture.prepareBatch(1)); threadRefuses(capture::stageBatch); threadRefuses(capture::stagedInventory);
                threadRefuses(capture::publishInventory); threadRefuses(capture::inventoryReceipt);
                threadRefuses(capture::recoverInventory); threadRefuses(capture::recoveredInventory);
                threadRefuses(capture::frozenSnapshot); threadRefuses(() -> f.lease.captureFrozenSource(f.id));
            });
            f.tick = () -> {
                if (!attempt.isDone()) return;
                attempt.join();
                check(f.lease.valid() && !f.detail.acceptsMutation(), "Wrong thread changed ownership");
                while (!capture.step(8)) { }
                done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void invalidatedParentRefusesAndUnfreezesChild(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = f.lease.captureSource(f.id);
            var invalid = new CompoundTag(); invalid.put("data", new CompoundTag());
            f.owner.stampSnapshot(f.ledger, invalid);
            f.owner.nativeWrite(invalid, () -> { throw new AssertionError("Invalid ledger was written"); });
            check(!f.lease.valid(), "Parent lease not invalidated");
            refuses(() -> capture.step(1));
            check(f.detail.acceptsMutation(), "Invalid parent leaked child freeze");
            done(helper, f);
        });
    }

    @GameTest(maxTicks = 300)
    public void diskVerificationIsBackgroundOnlyAndObservationIsChecked(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); stageNative(helper, f);
            var entered = new CompletableFuture<Void>();
            var ready = capture.verifyDisk(() -> {
                check(!helper.getLevel().getServer().isSameThread(), "Disk verification ran on server thread");
                entered.complete(null); f.ioGate.join();
            });
            check(ready == capture.verifyDisk(), "Duplicate verification was scheduled");
            var bytes = Files.readAllBytes(nativePath(helper, f));
            f.tick = () -> {
                if (!entered.isDone()) return;
                check(!ready.isDone(), "Verification blocked/ignored worker gate");
                refuses(capture::diskObservation);
                f.ioGate.complete(null);
                f.tick = () -> {
                    if (!ready.isDone()) return;
                    ready.join(); var observed = capture.diskObservation();
                    check(observed.ticket().equals(f.intent.ticket()) && observed.mapId() == f.id.id(), "Wrong source observation");
                    check(observed.nativeFileHash().matches("[a-f0-9]{64}"), "Missing raw fingerprint");
                    check(java.util.Arrays.equals(bytes, Files.readAllBytes(nativePath(helper, f))), "Verification changed source bytes");
                    capture.close(); refuses(capture::diskObservation); done(helper, f);
                };
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void absentDiskSourceFailsWithoutCreatingReplacement(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); Files.deleteIfExists(nativePath(helper, f));
            var ready = capture.verifyDisk();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::diskObservation);
                check(Files.notExists(nativePath(helper, f)), "Verification bootstrapped native source");
                check(f.detail.acceptsMutation() && f.lease.valid(), "Failed retrieval leaked child or released parent");
                done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void cancelledDiskRequestDrainsBeforeAnotherOwnerSession(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); stageNative(helper, f); var entered = new CompletableFuture<Void>();
            var ready = capture.verifyDisk(() -> { entered.complete(null); f.ioGate.join(); });
            f.tick = () -> {
                if (!entered.isDone()) return;
                ready.cancel(false); capture.close(); f.lease.close();
                check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Cancellation leaked freeze");
                check(f.owner.begin().isCompletedExceptionally(), "New session ignored pending disk reader");
                f.ioGate.complete(null); done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void closingStorageWithDiskJobNeedsNoServerCallback(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); stageNative(helper, f); var entered = new CompletableFuture<Void>();
            var ready = capture.verifyDisk(() -> { entered.complete(null); f.ioGate.join(); });
            f.tick = () -> {
                if (!entered.isDone()) return;
                CompletableFuture.runAsync(() -> f.ioGate.complete(null));
                f.storage.close(); f.storageClosed = true;
                check(ready.isDone() && !f.lease.valid(), "Shutdown left pending source readiness");
                check(f.detail.acceptsMutation(), "Shutdown left source frozen");
                refuses(capture::diskObservation); done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void nativeCacheReplacementCannotExposeFinishedDiskEvidence(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); stageNative(helper, f);
            var ready = capture.verifyDisk();
            f.tick = () -> {
                if (!ready.isDone()) return;
                ready.join();
                helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                        MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
                refuses(capture::diskObservation);
                check(f.detail.acceptsMutation(), "Stale evidence leaked child freeze"); done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void laterNativeDiskPixelsCannotMasqueradeAsT0(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); f.map.setColor(0, 0, (byte) 8); stageNative(helper, f);
            var bytes = Files.readAllBytes(nativePath(helper, f)); var ready = capture.verifyDisk();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::diskObservation);
                check(java.util.Arrays.equals(bytes, Files.readAllBytes(nativePath(helper, f))), "Mismatch rewrote native source");
                done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void manifestPublicationIsBackgroundOnlyAndChecked(GameTestHelper helper) {
        run(helper, true, f -> verified(helper, f, capture -> {
            var nativeBytes = Files.readAllBytes(nativePath(helper, f));
            var directory = f.owner.sourceManifestDirectory();
            check(Files.notExists(directory), "Observation created a manifest directory");
            var entered = new CompletableFuture<Void>();
            var ready = capture.persistManifest(() -> {
                check(!helper.getLevel().getServer().isSameThread(), "Manifest IO ran on server thread");
                entered.complete(null); f.ioGate.join();
            }, stage -> check(!helper.getLevel().getServer().isSameThread(), "Manifest force ran on server thread"));
            check(ready == capture.persistManifest(), "Duplicate manifest work scheduled");
            f.tick = () -> {
                if (!entered.isDone()) return;
                check(!ready.isDone(), "Manifest ignored worker gate"); refuses(capture::manifest);
                f.ioGate.complete(null);
                f.tick = () -> {
                    if (!ready.isDone()) return;
                    ready.join(); var observed = capture.diskObservation(); var manifest = capture.manifest();
                    check(manifest.equals(NexusMapSourceManifest.capture(NexusMapBindingWitness.capture(f.ledger), f.intent, observed)),
                            "Manifest lost held ledger/ticket identity");
                    check(NexusMapSourceManifest.read(directory, f.intent.ticket()).orElseThrow().equals(manifest), "Cold manifest mismatch");
                    check(java.util.Arrays.equals(nativeBytes, Files.readAllBytes(nativePath(helper, f))), "Publication changed native pixels");
                    check(f.lease.valid() && !f.detail.acceptsMutation(), "Publication released held ownership");
                    capture.close(); refuses(capture::manifest); done(helper, f);
                };
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cancelledManifestDrainsBeforeAnotherSession(GameTestHelper helper) {
        run(helper, true, f -> verified(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.persistManifest(() -> { entered.complete(null); f.ioGate.join(); }, stage -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                ready.cancel(false); capture.close(); f.lease.close();
                check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Cancelled manifest leaked freeze");
                check(f.owner.begin().isCompletedExceptionally(), "Session overlapped undrained manifest IO");
                refuses(capture::manifest); f.ioGate.complete(null); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void storageCloseDrainsManifestWithoutServerCallback(GameTestHelper helper) {
        run(helper, true, f -> verified(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.persistManifest(() -> { entered.complete(null); f.ioGate.join(); }, stage -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                CompletableFuture.runAsync(() -> f.ioGate.complete(null));
                f.storage.close(); f.storageClosed = true;
                check(ready.isDone() && !f.lease.valid(), "Storage close left manifest work pending");
                check(f.detail.acceptsMutation(), "Storage close leaked manifest freeze");
                refuses(capture::manifest); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void partialManifestIsPreservedAndCheckedFailureReleasesChild(GameTestHelper helper) {
        run(helper, true, f -> verified(helper, f, capture -> {
            Path directory = f.owner.sourceManifestDirectory(); Files.createDirectory(directory);
            Path file = NexusMapSourceManifest.path(directory, f.intent.ticket()); byte[] bad = {1, 2, 3}; Files.write(file, bad);
            var ready = capture.persistManifest();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::manifest);
                check(java.util.Arrays.equals(bad, Files.readAllBytes(file)), "Partial manifest was repaired/replaced");
                check(f.detail.acceptsMutation() && f.lease.valid(), "Failed manifest retrieval leaked child/released parent");
                done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void invalidManifestDirectoryIsNeverReplaced(GameTestHelper helper) {
        run(helper, true, f -> verified(helper, f, capture -> {
            Path directory = f.owner.sourceManifestDirectory(); Files.writeString(directory, "preserve");
            var ready = capture.persistManifest();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::manifest); check(Files.readString(directory).equals("preserve"), "Invalid directory was replaced");
                done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cacheReplacementCannotExposeCompletedManifest(GameTestHelper helper) {
        run(helper, true, f -> verified(helper, f, capture -> {
            var ready = capture.persistManifest();
            f.tick = () -> {
                if (!ready.isDone()) return;
                ready.join();
                helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                        MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
                refuses(capture::manifest); check(f.detail.acceptsMutation(), "Stale manifest leaked detail freeze");
                done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void uncertainManifestFailureCannotExposePublication(GameTestHelper helper) {
        run(helper, true, f -> verified(helper, f, capture -> {
            var ready = capture.persistManifest(stage -> {
                if (stage == NexusMapSourceManifest.Stage.FILE_FORCED) throw new java.io.IOException("injected pre-directory failure");
            });
            f.tick = () -> {
                if (!ready.isDone()) return;
                try { ready.join(); throw new AssertionError("Uncertain publication acknowledged"); }
                catch (java.util.concurrent.CompletionException failure) {
                    check(failure.getCause() instanceof NexusMapSourceManifest.UncertainCommit, "Lost uncertain publication state");
                }
                check(NexusMapSourceManifest.read(f.owner.sourceManifestDirectory(), f.intent.ticket()).isPresent(), "Complete uncertain record vanished");
                diskRefuses(capture::manifest); check(f.lease.valid(), "Uncertain child released parent ownership");
                done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void inventoryIncludesEveryBindingAndUnpreparedMap(GameTestHelper helper) {
        run(helper, true, true, true, true, f -> {
            var capture = f.lease.captureSource(f.id); refuses(capture::inventory);
            while (!capture.step(8)) { }
            var inventory = capture.inventory(); check(inventory == capture.inventory(), "Duplicate inventory cursor");
            check(!inventory.step(1), "Inventory ignored entry budget"); refuses(inventory::result);
            while (!inventory.step(1)) { }
            var snapshot = inventory.result();
            check(snapshot.bindings().size() == 2 && snapshot.bindings().containsKey(f.extraId.id()), "Unprepared binding omitted");
            check(snapshot.unprepared().equals(java.util.Set.of(f.extraId.id())), "Wrong unprepared source set");
            check(snapshot.nativeSources().containsAll(java.util.Set.of(f.id.id(), f.extraId.id())), "Required native sources omitted");
            check(snapshot.metadata().equals(NexusMapBindingWitness.capture(f.ledger)), "Inventory lost held ledger fingerprint");
            check(snapshot.detail() == capture.result().detail(), "Inventory recopied/lost frozen terrain");
            capture.close(); refuses(inventory::result); refuses(() -> inventory.step(1));
            check(snapshot.bindings().size() == 2 && f.lease.valid(), "Child release changed inventory or parent"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void inventoryParentCloseInvalidatesPartialEnumeration(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); var inventory = capture.inventory(); check(!inventory.step(1), "Inventory was not incremental");
            f.lease.close(); refuses(inventory::result); refuses(() -> inventory.step(1));
            check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Inventory parent close leaked freezes"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void inventoryCannotReturnEvidenceAfterCachedSourceReplacement(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); var inventory = capture.inventory(); while (!inventory.step(8)) { }
            helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                    MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
            refuses(inventory::result); check(f.detail.acceptsMutation(), "Invalid inventory leaked detail freeze"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void inventoryStorageCloseInvalidatesEnumeration(GameTestHelper helper) {
        run(helper, true, f -> {
            var capture = complete(f); var inventory = capture.inventory(); inventory.step(1);
            f.storage.close(); f.storageClosed = true;
            refuses(() -> inventory.step(1)); refuses(inventory::result);
            check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Inventory shutdown leaked freezes"); done(helper, f);
        });
    }
    @GameTest(maxTicks = 300)
    public void inventoryWrongThreadCannotTraverseOrPublish(GameTestHelper helper) {
        run(helper, true, f -> {
            var inventory = complete(f).inventory();
            var attempted = CompletableFuture.runAsync(() -> { threadRefuses(() -> inventory.step(1)); threadRefuses(inventory::result); });
            f.tick = () -> {
                if (!attempted.isDone()) return;
                attempted.join(); check(!inventory.step(1), "Wrong thread advanced inventory");
                while (!inventory.step(8)) { } check(inventory.result().bindings().size() == 1, "Inventory changed"); done(helper, f);
            };
        });
    }
    @GameTest(maxTicks = 300)
    public void stagedPairIsBackgroundVerifiedAndSurvivesOriginalDeletion(GameTestHelper helper) {
        run(helper, true, f -> manifested(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.stageSources(() -> {
                check(!helper.getLevel().getServer().isSameThread(), "Source staging ran on server thread");
                entered.complete(null); f.ioGate.join();
            }, step -> check(!helper.getLevel().getServer().isSameThread(), "Source publication ran on server thread"));
            check(ready == capture.stageSources(), "Duplicate source staging scheduled");
            f.tick = () -> {
                if (!entered.isDone()) return;
                check(!ready.isDone(), "Source staging ignored worker gate"); refuses(capture::stagedSources);
                f.ioGate.complete(null);
                f.tick = () -> {
                    if (!ready.isDone()) return;
                    ready.join(); var proof = capture.stagedSources();
                    check(proof.manifest().equals(capture.manifest()), "Staged evidence lost manifest identity");
                    check(proof.nativeSource().hash().equals(proof.manifest().nativeFileHash()), "Wrong staged native hash");
                    check(proof.detailSource().hash().equals(proof.manifest().detailFileHash()), "Wrong staged detail hash");
                    Files.delete(nativePath(helper, f)); Files.delete(detailPath(f));
                    var directory = f.owner.sourceBlobDirectory();
                    check(NexusMapSourceStaging.verify(directory, proof.nativeSource().kind(), proof.nativeSource().hash(), () -> true).equals(proof.nativeSource()), "Saved native depended on original");
                    check(NexusMapSourceStaging.verify(directory, proof.detailSource().kind(), proof.detailSource().hash(), () -> true).equals(proof.detailSource()), "Saved detail depended on original");
                    check(f.lease.valid() && !f.detail.acceptsMutation(), "Staging released ownership");
                    capture.close(); refuses(capture::stagedSources); done(helper, f);
                };
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cancelledStagingDrainsBeforeNewSession(GameTestHelper helper) {
        run(helper, true, f -> manifested(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.stageSources(() -> { entered.complete(null); f.ioGate.join(); }, step -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                ready.cancel(false); capture.close(); f.lease.close();
                check(f.owner.begin().isCompletedExceptionally(), "New session ignored undrained staging");
                check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Cancelled staging leaked freeze");
                refuses(capture::stagedSources); f.ioGate.complete(null); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void storageCloseDrainsStagingWithoutServerCallback(GameTestHelper helper) {
        run(helper, true, f -> manifested(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.stageSources(() -> { entered.complete(null); f.ioGate.join(); }, step -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                CompletableFuture.runAsync(() -> f.ioGate.complete(null)); f.storage.close(); f.storageClosed = true;
                check(ready.isDone() && !f.lease.valid(), "Staging survived storage shutdown");
                check(f.detail.acceptsMutation(), "Shutdown staging leaked detail freeze");
                refuses(capture::stagedSources); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void changedOriginalCannotBeStagedAsObservedSnapshot(GameTestHelper helper) {
        run(helper, true, f -> manifested(helper, f, capture -> {
            f.map.setColor(0, 0, (byte) 8); stageNative(helper, f);
            byte[] changed = Files.readAllBytes(nativePath(helper, f)); var ready = capture.stageSources();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::stagedSources);
                check(java.util.Arrays.equals(changed, Files.readAllBytes(nativePath(helper, f))), "Failed staging rewrote original");
                check(f.lease.valid() && f.detail.acceptsMutation(), "Failed staged getter released parent/leaked child"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void missingDetailLeavesNativeBlobWithoutPublishingPartialPair(GameTestHelper helper) {
        run(helper, true, f -> manifested(helper, f, capture -> {
            var expected = capture.manifest(); Files.delete(detailPath(f)); var ready = capture.stageSources();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::stagedSources);
                check(Files.notExists(detailPath(f)), "Staging bootstrapped missing detail");
                check(NexusMapSourceStaging.verify(f.owner.sourceBlobDirectory(), NexusMapSourceStaging.Kind.NATIVE,
                        expected.nativeFileHash(), () -> true).hash().equals(expected.nativeFileHash()), "Failed pair discarded saved native source");
                check(f.lease.valid(), "Partial staging released parent"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cacheReplacementCannotExposeFinishedStagedPair(GameTestHelper helper) {
        run(helper, true, f -> manifested(helper, f, capture -> {
            var ready = capture.stageSources();
            f.tick = () -> {
                if (!ready.isDone()) return;
                ready.join(); helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                        MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
                refuses(capture::stagedSources); check(f.detail.acceptsMutation(), "Stale staged evidence leaked freeze"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void uncertainStagingCannotExposePairOrRemovePublishedBlob(GameTestHelper helper) {
        run(helper, true, f -> manifested(helper, f, capture -> {
            var expected = capture.manifest();
            var ready = capture.stageSources(() -> { }, step -> {
                if (step == NexusMapSourceStaging.Step.PUBLISHED) throw new java.io.IOException("injected staged publication failure");
            });
            f.tick = () -> {
                if (!ready.isDone()) return;
                try { ready.join(); throw new AssertionError("Uncertain staged pair acknowledged"); }
                catch (java.util.concurrent.CompletionException failure) {
                    check(failure.getCause() instanceof NexusMapSourceStaging.UncertainCommit, "Staging lost uncertain state");
                }
                diskRefuses(capture::stagedSources);
                check(NexusMapSourceStaging.verify(f.owner.sourceBlobDirectory(), NexusMapSourceStaging.Kind.NATIVE,
                        expected.nativeFileHash(), () -> true).hash().equals(expected.nativeFileHash()), "Uncertain staging deleted final blob");
                done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void heldBatchIncludesUncachedUnpreparedMapAndUsesSavedPrimary(GameTestHelper helper) {
        run(helper, true, true, true, true, f -> {
            stageNative(helper, f.extraId, f.map);
            paired(helper, f, capture -> {
                while (!capture.inventory().step(8)) { }
                refuses(capture::stageBatch); refuses(capture::stagedInventory);
                check(!capture.prepareBatch(1), "Descriptor preparation ignored budget"); refuses(capture::stageBatch);
                check(capture.prepareBatch(1), "Descriptor preparation did not complete");
                Files.delete(nativePath(helper, f));
                var entered = new CompletableFuture<Void>();
                var ready = capture.stageBatch(() -> {
                    check(!helper.getLevel().getServer().isSameThread(), "Batch staging ran on server thread"); entered.complete(null); f.ioGate.join();
                }, (id, phase) -> check(!helper.getLevel().getServer().isSameThread(), "Batch source IO ran on server thread"));
                check(ready == capture.stageBatch(), "Duplicate inventory staging scheduled");
                f.tick = () -> {
                    if (!entered.isDone()) return;
                    check(!ready.isDone(), "Batch ignored worker gate"); refuses(capture::stagedInventory); f.ioGate.complete(null);
                    f.tick = () -> {
                        if (!ready.isDone()) return;
                        ready.join(); var proof = capture.stagedInventory();
                        check(proof.natives().sources().keySet().equals(java.util.Set.of(f.id.id(), f.extraId.id())), "Batch omitted inventory sources");
                        check(proof.natives().unprepared().equals(java.util.Set.of(f.extraId.id())), "Batch lost unprepared source");
                        check(proof.primary().equals(capture.stagedSources()), "Batch lost saved detail proof");
                        check(((NexusMapMigrationOwner.StorageAccess) helper.getLevel().getServer().getDataStorage())
                                .totem$cachedData(MapItemSavedData.type(f.extraId)) == null, "Batch bootstrapped uncached map");
                        check(f.lease.valid() && !f.detail.acceptsMutation(), "Batch released held ownership");
                        capture.close(); refuses(capture::stagedInventory); done(helper, f);
                    };
                };
            });
        });
    }
    @GameTest(maxTicks = 300)
    public void cancelledBatchDrainsBeforeNewOwnerSession(GameTestHelper helper) {
        run(helper, true, f -> paired(helper, f, capture -> {
            prepareBatch(capture); var entered = new CompletableFuture<Void>();
            var ready = capture.stageBatch(() -> { entered.complete(null); f.ioGate.join(); }, (id, phase) -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                ready.cancel(false); capture.close(); f.lease.close();
                check(f.owner.begin().isCompletedExceptionally(), "New owner session overlapped pending batch");
                check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Cancelled batch leaked freezes");
                refuses(capture::stagedInventory); f.ioGate.complete(null); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void storageCloseDrainsBatchWithoutServerCallback(GameTestHelper helper) {
        run(helper, true, f -> paired(helper, f, capture -> {
            prepareBatch(capture); var entered = new CompletableFuture<Void>();
            var ready = capture.stageBatch(() -> { entered.complete(null); f.ioGate.join(); }, (id, phase) -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                CompletableFuture.runAsync(() -> f.ioGate.complete(null)); f.storage.close(); f.storageClosed = true;
                check(ready.isDone() && !f.lease.valid(), "Storage shutdown left live batch");
                check(f.detail.acceptsMutation(), "Batch shutdown leaked detail freeze"); refuses(capture::stagedInventory); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void missingInventorySourceCannotPublishPartialBatch(GameTestHelper helper) {
        run(helper, true, true, true, true, f -> paired(helper, f, capture -> {
            prepareBatch(capture); var primary = capture.stagedSources();
            Files.deleteIfExists(nativePath(helper, f.extraId)); var ready = capture.stageBatch();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::stagedInventory);
                check(Files.notExists(nativePath(helper, f.extraId)), "Batch created missing native source");
                check(NexusMapSourceStaging.verify(f.owner.sourceBlobDirectory(), primary.nativeSource().kind(), primary.nativeSource().hash(), () -> true)
                        .equals(primary.nativeSource()), "Partial batch discarded saved primary");
                check(f.lease.valid() && f.detail.acceptsMutation(), "Failed batch retrieval changed parent or leaked child"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void corruptedSavedDetailCannotJoinCompletedNativeBatch(GameTestHelper helper) {
        run(helper, true, f -> paired(helper, f, capture -> {
            prepareBatch(capture); var detail = capture.stagedSources().detailSource();
            Path file = NexusMapSourceStaging.path(f.owner.sourceBlobDirectory(), detail.kind(), detail.hash());
            var ready = capture.stageBatch(() -> { }, (id, phase) -> {
                if (phase == NexusMapNativeBatchStaging.Phase.STAGED) Files.writeString(file, "corrupt detail");
            });
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::stagedInventory); check(Files.readString(file).equals("corrupt detail"), "Batch repaired corrupt saved detail");
                check(f.lease.valid(), "Corrupt detail batch released parent"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cacheReplacementCannotExposeCompletedInventoryBatch(GameTestHelper helper) {
        run(helper, true, f -> paired(helper, f, capture -> {
            prepareBatch(capture); var ready = capture.stageBatch();
            f.tick = () -> {
                if (!ready.isDone()) return;
                ready.join(); helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                        MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
                refuses(capture::stagedInventory); check(f.detail.acceptsMutation(), "Stale batch leaked child freeze"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void inventoryReceiptPublicationIsWorkerOnlyAndLeaseChecked(GameTestHelper helper) {
        run(helper, true, f -> batched(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.publishInventory(() -> {
                check(!helper.getLevel().getServer().isSameThread(), "Receipt publication ran on server thread");
                entered.complete(null); f.ioGate.join();
            }, step -> check(!helper.getLevel().getServer().isSameThread(), "Receipt IO ran on server thread"));
            check(ready == capture.publishInventory(), "Duplicate receipt publication scheduled");
            f.tick = () -> {
                if (!entered.isDone()) return;
                check(!ready.isDone(), "Receipt ignored worker gate"); refuses(capture::inventoryReceipt); f.ioGate.complete(null);
                f.tick = () -> {
                    if (!ready.isDone()) return;
                    ready.join(); var receipt = capture.inventoryReceipt();
                    check(NexusMapInventoryReceipt.read(f.owner.inventoryReceiptDirectory(), receipt.primary().ticket(), () -> true)
                            .orElseThrow().equals(receipt), "Cold receipt differs from held proof");
                    check(f.lease.valid() && !f.detail.acceptsMutation(), "Receipt released held ownership");
                    capture.close(); refuses(capture::inventoryReceipt); done(helper, f);
                };
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cancelledReceiptDrainsBeforeNewOwnerSession(GameTestHelper helper) {
        run(helper, true, f -> batched(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.publishInventory(() -> { entered.complete(null); f.ioGate.join(); }, step -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                ready.cancel(false); capture.close(); f.lease.close();
                check(f.owner.begin().isCompletedExceptionally(), "New session overlapped receipt IO");
                check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Cancelled receipt leaked freezes");
                refuses(capture::inventoryReceipt); f.ioGate.complete(null); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void storageCloseDrainsReceiptWithoutServerCallback(GameTestHelper helper) {
        run(helper, true, f -> batched(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.publishInventory(() -> { entered.complete(null); f.ioGate.join(); }, step -> { });
            f.tick = () -> {
                if (!entered.isDone()) return;
                CompletableFuture.runAsync(() -> f.ioGate.complete(null)); f.storage.close(); f.storageClosed = true;
                check(ready.isDone() && !f.lease.valid(), "Shutdown left live receipt publication");
                check(f.detail.acceptsMutation(), "Receipt shutdown leaked freeze"); refuses(capture::inventoryReceipt); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void missingSavedNativeCannotPublishInventoryReceipt(GameTestHelper helper) {
        brokenReceiptSource(helper, false);
    }
    @GameTest(maxTicks = 300)
    public void corruptSavedDetailCannotPublishInventoryReceipt(GameTestHelper helper) {
        brokenReceiptSource(helper, true);
    }
    private static void brokenReceiptSource(GameTestHelper helper, boolean detail) {
        run(helper, true, f -> batched(helper, f, capture -> {
            var pair = capture.stagedSources(); var blob = detail ? pair.detailSource() : pair.nativeSource();
            var path = NexusMapSourceStaging.path(f.owner.sourceBlobDirectory(), blob.kind(), blob.hash());
            if (detail) Files.writeString(path, "corrupt"); else Files.delete(path);
            var ready = capture.publishInventory();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::inventoryReceipt);
                check(Files.notExists(f.owner.inventoryReceiptDirectory()), "Invalid sources published receipt directory");
                check(detail ? Files.readString(path).equals("corrupt") : Files.notExists(path), "Publication repaired invalid source");
                check(f.lease.valid() && f.detail.acceptsMutation(), "Failed receipt changed parent or leaked child"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void inventoryReceiptCountQuotaPreservesPartialRecords(GameTestHelper helper) {
        receiptQuota(helper, false);
    }
    @GameTest(maxTicks = 300)
    public void inventoryReceiptByteQuotaReservesFullRecord(GameTestHelper helper) {
        receiptQuota(helper, true);
    }
    private static void receiptQuota(GameTestHelper helper, boolean bytes) {
        run(helper, true, f -> batched(helper, f, capture -> {
            Path directory = f.owner.inventoryReceiptDirectory(); Files.createDirectory(directory);
            int count = bytes ? 10 : 64;
            for (int i = 0; i < count; i++) {
                var path = NexusMapInventoryReceipt.path(directory, new UUID(0, i));
                try (var file = new java.io.RandomAccessFile(path.toFile(), "rw")) {
                    file.setLength(bytes ? NexusMapInventoryReceipt.MAX_BYTES : 0);
                }
            }
            var ready = capture.publishInventory();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::inventoryReceipt);
                try (var files = Files.list(directory)) { check(files.count() == count, "Quota failure changed receipt files"); }
                check(Files.notExists(NexusMapInventoryReceipt.path(directory, f.intent.ticket())), "Quota admitted new receipt");
                check(f.lease.valid(), "Quota failure released parent"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cacheReplacementCannotExposeCompletedInventoryReceipt(GameTestHelper helper) {
        run(helper, true, f -> batched(helper, f, capture -> {
            var ready = capture.publishInventory();
            f.tick = () -> {
                if (!ready.isDone()) return;
                ready.join(); helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                        MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
                refuses(capture::inventoryReceipt); check(f.detail.acceptsMutation(), "Stale receipt leaked freeze"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void uncertainInventoryReceiptCannotBecomeHeldEvidence(GameTestHelper helper) {
        run(helper, true, f -> batched(helper, f, capture -> {
            var expected = NexusMapInventoryReceipt.capture(capture.stagedInventory());
            var ready = capture.publishInventory(() -> { }, step -> {
                if (step == NexusMapInventoryReceipt.Step.FILE_FORCED) throw new java.io.IOException("injected receipt force failure");
            });
            f.tick = () -> {
                if (!ready.isDone()) return;
                try { ready.join(); throw new AssertionError("Uncertain receipt acknowledged"); }
                catch (java.util.concurrent.CompletionException failure) {
                    check(failure.getCause() instanceof NexusMapInventoryReceipt.UncertainCommit, "Lost uncertain receipt state");
                }
                diskRefuses(capture::inventoryReceipt);
                check(NexusMapInventoryReceipt.read(f.owner.inventoryReceiptDirectory(), expected.primary().ticket(), () -> true)
                        .orElseThrow().equals(expected), "Uncertain receipt was deleted");
                check(f.lease.valid(), "Uncertain receipt released parent"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void freshCaptureRecoversSavedInventoryOnWorkerWithoutOriginalFiles(GameTestHelper helper) {
        run(helper, true, f -> recoverable(helper, f, capture -> {
            Files.delete(nativePath(helper, f)); Files.delete(detailPath(f));
            var entered = new CompletableFuture<Void>();
            var ready = capture.recoverInventory(() -> {
                check(!helper.getLevel().getServer().isSameThread(), "Recovery IO ran on server thread");
                entered.complete(null); f.ioGate.join();
            });
            check(ready == capture.recoverInventory(), "Duplicate recovery scheduled");
            refuses(capture::verifyDisk); refuses(capture::persistManifest); refuses(capture::stageSources);
            f.tick = () -> {
                if (!entered.isDone()) return;
                check(!ready.isDone(), "Recovery ignored worker gate"); refuses(capture::recoveredInventory); f.ioGate.complete(null);
                f.tick = () -> {
                    if (!ready.isDone()) return;
                    ready.join(); check(capture.recoveredInventory().receipt().primary().ticket().equals(f.intent.ticket()), "Recovery lost ticket");
                    check(f.lease.valid() && !f.detail.acceptsMutation(), "Recovery released ownership");
                    check(Files.notExists(nativePath(helper, f)) && Files.notExists(detailPath(f)), "Recovery recreated originals");
                    capture.close(); refuses(capture::recoveredInventory); done(helper, f);
                };
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cancelledRecoveryDrainsBeforeNewOwnerSession(GameTestHelper helper) {
        run(helper, true, f -> recoverable(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.recoverInventory(() -> { entered.complete(null); f.ioGate.join(); });
            f.tick = () -> {
                if (!entered.isDone()) return;
                ready.cancel(false); capture.close(); f.lease.close();
                check(f.owner.begin().isCompletedExceptionally(), "New session overlapped recovery IO");
                check(f.detail.acceptsMutation() && f.ledger.acceptsMutation(), "Cancelled recovery leaked freezes");
                refuses(capture::recoveredInventory); f.ioGate.complete(null); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void storageCloseDrainsRecoveryWithoutServerCallback(GameTestHelper helper) {
        run(helper, true, f -> recoverable(helper, f, capture -> {
            var entered = new CompletableFuture<Void>();
            var ready = capture.recoverInventory(() -> { entered.complete(null); f.ioGate.join(); });
            f.tick = () -> {
                if (!entered.isDone()) return;
                CompletableFuture.runAsync(() -> f.ioGate.complete(null)); f.storage.close(); f.storageClosed = true;
                check(ready.isDone() && !f.lease.valid(), "Shutdown left live recovery");
                check(f.detail.acceptsMutation(), "Recovery shutdown leaked freeze"); refuses(capture::recoveredInventory); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void missingReceiptCannotBecomeRecoveredEvidence(GameTestHelper helper) {
        run(helper, true, f -> recoverable(helper, f, capture -> {
            Path file = NexusMapInventoryReceipt.path(f.owner.inventoryReceiptDirectory(), f.intent.ticket()); Files.delete(file);
            var ready = capture.recoverInventory();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::recoveredInventory); check(Files.notExists(file), "Recovery recreated required receipt");
                check(f.lease.valid() && f.detail.acceptsMutation(), "Failed recovery changed parent or leaked child"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void corruptSavedDetailCannotBecomeRecoveredEvidence(GameTestHelper helper) {
        run(helper, true, f -> recoverable(helper, f, capture -> {
            var receipt = NexusMapInventoryReceipt.read(f.owner.inventoryReceiptDirectory(), f.intent.ticket(), () -> true).orElseThrow();
            Path file = NexusMapSourceStaging.path(f.owner.sourceBlobDirectory(), receipt.detail().kind(), receipt.detail().hash()); Files.writeString(file, "broken");
            var ready = capture.recoverInventory();
            f.tick = () -> {
                if (!ready.isDone()) return;
                diskRefuses(capture::recoveredInventory); check(Files.readString(file).equals("broken"), "Recovery repaired detail");
                check(f.lease.valid() && f.detail.acceptsMutation(), "Corrupt recovery changed parent or leaked child"); done(helper, f);
            };
        }));
    }
    @GameTest(maxTicks = 300)
    public void cacheReplacementInvalidatesCompletedRecovery(GameTestHelper helper) {
        run(helper, true, f -> recoverable(helper, f, capture -> {
            var ready = capture.recoverInventory();
            f.tick = () -> {
                if (!ready.isDone()) return;
                ready.join(); helper.getLevel().getServer().getDataStorage().set(MapItemSavedData.type(f.id),
                        MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD));
                refuses(capture::recoveredInventory); check(f.detail.acceptsMutation(), "Stale recovery leaked freeze"); done(helper, f);
            };
        }));
    }
    private static void recoverable(GameTestHelper helper, Fixture f, CaptureBody body) throws Exception {
        batched(helper, f, capture -> {
            refuses(capture::recoverInventory); var ready = capture.publishInventory();
            f.tick = () -> {
                if (!ready.isDone()) return;
                ready.join(); capture.inventoryReceipt(); capture.close();
                f.tick = () -> {
                    // Completion readiness and actual worker drain are deliberately separate.
                    NexusMapMigrationOwner.SourceCapture fresh;
                    try { fresh = f.lease.captureSource(f.id); }
                    catch (IllegalStateException pending) {
                        check(f.lease.valid() && pending.getMessage().equals("Source capture unavailable"), "Recovery lost parent"); return;
                    }
                    refuses(fresh::recoverInventory); refuses(fresh::recoveredInventory);
                    while (!fresh.step(8)) { } refuses(fresh::recoverInventory);
                    while (!fresh.inventory().step(8)) { } body.run(fresh);
                };
            };
        });
    }
    private static void batched(GameTestHelper helper, Fixture f, CaptureBody body) throws Exception {
        paired(helper, f, capture -> {
            refuses(capture::publishInventory); refuses(capture::inventoryReceipt);
            prepareBatch(capture); var ready = capture.stageBatch();
            f.tick = () -> { if (ready.isDone()) { ready.join(); body.run(capture); } };
        });
    }
    private static void prepareBatch(NexusMapMigrationOwner.SourceCapture capture) {
        while (!capture.inventory().step(8)) { } while (!capture.prepareBatch(8)) { }
    }
    private static void paired(GameTestHelper helper, Fixture f, CaptureBody body) throws Exception {
        manifested(helper, f, capture -> {
            refuses(() -> capture.prepareBatch(1)); refuses(capture::stageBatch); refuses(capture::stagedInventory);
            var ready = capture.stageSources();
            f.tick = () -> { if (ready.isDone()) { ready.join(); body.run(capture); } };
        });
    }
    private static Path detailPath(Fixture f) {
        return ((NexusMapMigrationOwner.StorageAccess) f.storage).totem$sourceFile(NexusMapDetailSavedData.TYPE).path();
    }
    private static void manifested(GameTestHelper helper, Fixture f, CaptureBody body) throws Exception {
        verified(helper, f, capture -> {
            refuses(capture::stageSources); refuses(capture::stagedSources);
            var ready = capture.persistManifest();
            f.tick = () -> { if (ready.isDone()) { ready.join(); body.run(capture); } };
        });
    }
    @FunctionalInterface private interface CaptureBody { void run(NexusMapMigrationOwner.SourceCapture capture) throws Exception; }
    private static void verified(GameTestHelper helper, Fixture f, CaptureBody body) throws Exception {
        var capture = complete(f); refuses(capture::persistManifest); refuses(capture::manifest);
        stageNative(helper, f); var ready = capture.verifyDisk();
        f.tick = () -> { if (ready.isDone()) { ready.join(); body.run(capture); } };
    }
    private static NexusMapMigrationOwner.SourceCapture complete(Fixture f) {
        var capture = f.lease.captureSource(f.id); while (!capture.step(8)) { } return capture;
    }
    private static Path nativePath(GameTestHelper helper, Fixture f) {
        return nativePath(helper, f.id);
    }
    private static Path nativePath(GameTestHelper helper, MapId id) {
        return ((NexusMapMigrationOwner.StorageAccess) helper.getLevel().getServer().getDataStorage())
                .totem$sourceFile(MapItemSavedData.type(id)).path();
    }
    private static void stageNative(GameTestHelper helper, Fixture f) throws Exception {
        stageNative(helper, f.id, f.map);
    }
    private static void stageNative(GameTestHelper helper, MapId id, MapItemSavedData map) throws Exception {
        var file = nativePath(helper, id); Files.createDirectories(file.getParent());
        var envelope = new CompoundTag(); envelope.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        envelope.put("data", MapItemSavedData.CODEC.encodeStart(NbtOps.INSTANCE, map).getOrThrow());
        NbtIo.writeCompressed(envelope, file);
    }
    private static void diskRefuses(Runnable body) {
        try { body.run(); }
        catch (java.util.concurrent.CompletionException failure) {
            check(failure.getCause() instanceof java.io.IOException, "Wrong disk verification failure"); return;
        }
        throw new AssertionError("Invalid disk source was acknowledged");
    }

    private static final class Fixture implements AutoCloseable {
        final Path directory;
        final SavedDataStorage storage;
        final NexusMapBindingSavedData ledger = new NexusMapBindingSavedData();
        final NexusMapDetailSavedData detail = new NexusMapDetailSavedData();
        final MapItemSavedData map = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
        final MapId id;
        final MapId extraId;
        final NexusMapBindingSavedData.SparseImportIntent intent;
        final NexusMapMigrationOwner owner;
        final CompletableFuture<NexusMapMigrationOwner.Lease> response;
        final CompletableFuture<Void> ioGate = new CompletableFuture<>();
        NexusMapMigrationOwner.Lease lease;
        Action tick;
        boolean ready, storageClosed, closed;
        Fixture(GameTestHelper helper, boolean ticket, boolean cacheNative, boolean cacheDetail, boolean extraBinding) throws Exception {
            directory = Files.createTempDirectory("nexus-source-capture-");
            storage = new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES);
            var level = helper.getLevel(); id = level.getFreeMapId(); map.setColor(0, 0, (byte) 4);
            if (cacheNative) level.getServer().getDataStorage().set(MapItemSavedData.type(id), map);
            var unit = UUID.randomUUID();
            check(ledger.bind(id, unit, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), map), "Fixture binding failed");
            intent = ticket ? ledger.prepareSparseImport(id, unit, map).orElseThrow() : null;
            detail.initialize(level, id, map, null); detail.record(level, id.id(), 0, 0, (byte) 4);
            extraId = extraBinding ? level.getFreeMapId() : null;
            if (extraBinding) {
                check(ledger.bind(extraId, UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), map), "Extra binding failed");
                detail.initialize(level, extraId, map, null);
            }
            storage.set(NexusMapBindingSavedData.TYPE, ledger);
            if (cacheDetail) storage.set(NexusMapDetailSavedData.TYPE, detail);
            owner = NexusMapMigrationOwner.forStorage(storage, level.getServer()); response = owner.begin();
        }
        @Override public void close() throws Exception {
            if (closed) return;
            ioGate.complete(null);
            if (!storageClosed) { storage.close(); storageClosed = true; }
            try (var paths = Files.walk(directory)) {
                for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
            closed = true;
        }
    }
    private static void run(GameTestHelper helper, boolean ticket, Body body) { run(helper, ticket, true, body); }
    private static void run(GameTestHelper helper, boolean ticket, boolean nativeCache, Body body) {
        run(helper, ticket, nativeCache, true, body);
    }
    private static void run(GameTestHelper helper, boolean ticket, boolean nativeCache, boolean detailCache, Body body) {
        run(helper, ticket, nativeCache, detailCache, false, body);
    }
    private static void run(GameTestHelper helper, boolean ticket, boolean nativeCache, boolean detailCache, boolean extraBinding, Body body) {
        try {
            var f = new Fixture(helper, ticket, nativeCache, detailCache, extraBinding);
            helper.runBeforeTestEnd(() -> guarded(f, f::close));
            helper.onEachTick(() -> {
                if (f.closed) return;
                guarded(f, () -> {
                    if (!f.ready) {
                        if (!f.response.isDone()) return;
                        f.lease = f.response.join(); f.ready = true; body.run(f); return;
                    }
                    if (f.tick != null) f.tick.run();
                });
            });
        } catch (Exception failure) { throw new IllegalStateException("Source capture fixture failed", failure); }
    }
    private static void done(GameTestHelper helper, Fixture f) throws Exception { f.close(); helper.succeed(); }
    private static void guarded(Fixture f, Action body) {
        try { body.run(); }
        catch (Exception | AssertionError failure) {
            try { f.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw new IllegalStateException("Source capture test failed", failure);
        }
    }
    private static void refuses(Runnable operation) {
        try { operation.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Source operation accepted invalid state");
    }
    private static void threadRefuses(Runnable operation) {
        try { operation.run(); }
        catch (IllegalStateException expected) {
            check(expected.getMessage().contains("server thread"), "Wrong refusal for wrong thread"); return;
        }
        throw new AssertionError("Wrong-thread source operation accepted");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @FunctionalInterface private interface Body { void run(Fixture f) throws Exception; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
