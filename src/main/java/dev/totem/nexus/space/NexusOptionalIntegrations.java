package dev.totem.nexus.space;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional cross-feature notification seam. Nexus never links directly to the
 * Discord feature; an installed bridge can subscribe to these events instead.
 */
public final class NexusOptionalIntegrations {
    private static final AtomicReference<Listener> LISTENER = new AtomicReference<>(Listener.NO_OP);

    private NexusOptionalIntegrations() {
    }

    public static void install(Listener listener) {
        LISTENER.set(Objects.requireNonNullElse(listener, Listener.NO_OP));
    }

    static void deathBackpackRecovered(String playerName) {
        LISTENER.get().deathBackpackRecovered(playerName);
    }

    static void publicSpaceUnitUpdate(String actor, String message) {
        LISTENER.get().publicSpaceUnitUpdate(actor, message);
    }

    static void adminAction(String actor, String action, String target) {
        LISTENER.get().adminAction(actor, action, target);
    }

    public interface Listener {
        Listener NO_OP = new Listener() {
            @Override
            public void deathBackpackRecovered(String playerName) {
            }

            @Override
            public void publicSpaceUnitUpdate(String actor, String message) {
            }

            @Override
            public void adminAction(String actor, String action, String target) {
            }
        };

        void deathBackpackRecovered(String playerName);

        void publicSpaceUnitUpdate(String actor, String message);

        void adminAction(String actor, String action, String target);
    }
}
