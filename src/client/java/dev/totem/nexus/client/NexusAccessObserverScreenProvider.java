package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.observer.*;
import dev.totem.nexus.network.AccessPlayersPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;

/** Versioned access-picker snapshots contain server-supplied rows and selection, never search text. */
public final class NexusAccessObserverScreenProvider implements ObserverScreenProvider {
    @Override public String familyId() { return "nexus_access"; }
    @Override public int protocolVersion() { return 1; }
    @Override public Set<String> variants() { return Set.of("administrator", "allowed"); }
    @Override public Optional<ObserverScreenSnapshot> capture(Screen candidate, long sequence) {
        if (!(candidate instanceof NexusAccessScreen screen) || screen.totem$isObserverReadOnly()) return Optional.empty();
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AccessPlayersPayload.CODEC.encode(b,screen.observerPayload());
            byte[] bytes = new byte[b.readableBytes()]; b.getBytes(b.readerIndex(),bytes);
            var metadata = screen.observerSelection() == null ? Map.<String,String>of()
                    : Map.of("selected",screen.observerSelection().toString());
            return Optional.of(new ObserverScreenSnapshot(familyId(),screen.observerPayload().role(),protocolVersion(),sequence,
                    screen.getTitle(),List.of(),new int[0],metadata,bytes));
        } finally { b.release(); }
    }
    private AccessPlayersPayload decode(ObserverScreenSnapshot snapshot) {
        if (!supports(snapshot) || snapshot.sequence() < 0) throw new IllegalArgumentException("Incompatible access snapshot");
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot.ownerPayload()));
        try {
            var data = AccessPlayersPayload.CODEC.decode(b);
            if (b.isReadable() || !snapshot.variant().equals(data.role())) throw new IllegalArgumentException("Invalid access variant");
            return data;
        } finally { b.release(); }
    }
    private static UUID selection(ObserverScreenSnapshot snapshot) {
        String id = snapshot.metadata().get("selected"); return id == null ? null : UUID.fromString(id);
    }
    @Override public ObserverScreenHandle create(ObserverScreenContext context, ObserverScreenSnapshot snapshot) {
        var screen = new NexusAccessScreen(null,decode(snapshot),true,context.stopObserving());
        screen.applyObserverSelection(selection(snapshot));
        return new ObserverScreenHandle() {
            private long sequence = snapshot.sequence(), cursorSequence = -1;
            @Override public Screen screen() { return screen; }
            @Override public void applySnapshot(ObserverScreenSnapshot next) {
                if (!supports(next) || !snapshot.variant().equals(next.variant()) || next.sequence() <= sequence) return;
                var data = decode(next);
                UUID selected = selection(next);
                if (selected != null && data.players().stream().noneMatch(p -> p.id().equals(selected)))
                    throw new IllegalArgumentException("Unknown selected access player");
                screen.applyPage(data); screen.applyObserverSelection(selected); sequence = next.sequence();
            }
            @Override public void applyCursor(ObserverRemoteCursor cursor) {
                if (cursor.sequence() <= cursorSequence) return;
                cursorSequence = cursor.sequence();
                // The Core read-only screen contract lets Observer render the remote cursor/carried stack.
            }
        };
    }
}
