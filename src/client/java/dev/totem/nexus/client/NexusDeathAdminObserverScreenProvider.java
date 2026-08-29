package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.observer.ObserverRemoteCursor;
import dev.totem.core.api.v1.client.observer.ObserverScreenContext;
import dev.totem.core.api.v1.client.observer.ObserverScreenHandle;
import dev.totem.core.api.v1.client.observer.ObserverScreenProvider;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.nexus.network.DeathNodeAdminPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Set;
import java.util.Optional;
import java.util.Map;

/** Nexus-owned read-only Death Node administration view. */
public final class NexusDeathAdminObserverScreenProvider implements ObserverScreenProvider {
    @Override public String familyId() { return "nexus_death_node_admin"; }
    @Override public int protocolVersion() { return 1; }
    @Override public Set<String> variants() { return Set.of(""); }

    @Override public Optional<ObserverScreenSnapshot> capture(Screen candidate, long sequence) {
        if (!(candidate instanceof NexusDeathNodeAdminScreen screen) || screen.totem$isObserverReadOnly())
            return Optional.empty();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DeathNodeAdminPayload.CODEC.encode(buffer, screen.observerPayload());
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), bytes);
            return Optional.of(new ObserverScreenSnapshot(familyId(), "", protocolVersion(), sequence,
                    screen.getTitle(), java.util.List.of(), new int[0], Map.of(), bytes));
        } finally { buffer.release(); }
    }

    @Override public ObserverScreenHandle create(ObserverScreenContext context, ObserverScreenSnapshot snapshot) {
        if (!supports(snapshot)) throw new IllegalArgumentException("Incompatible Death Admin Observer snapshot");
        NexusDeathNodeAdminScreen screen = new NexusDeathNodeAdminScreen(decode(snapshot), true,
                context.stopObserving());
        return new ObserverScreenHandle() {
            private long sequence = snapshot.sequence();
            @Override public Screen screen() { return screen; }
            @Override public void applySnapshot(ObserverScreenSnapshot next) {
                if (!NexusDeathAdminObserverScreenProvider.this.supports(next)
                        || next.sequence() <= sequence) return;
                screen.applyPayload(decode(next)); sequence = next.sequence();
            }
            @Override public void applyCursor(ObserverRemoteCursor ignored) { }
        };
    }

    private static DeathNodeAdminPayload decode(ObserverScreenSnapshot snapshot) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot.ownerPayload()));
        try {
            DeathNodeAdminPayload result = DeathNodeAdminPayload.CODEC.decode(buffer);
            if (buffer.readableBytes() != 0) throw new IllegalArgumentException("Trailing Death Admin payload bytes");
            return result;
        } finally { buffer.release(); }
    }
}
