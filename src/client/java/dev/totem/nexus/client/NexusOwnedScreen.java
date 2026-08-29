package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

/** Shared input firewall for Nexus production screens in Observer mode. */
abstract class NexusOwnedScreen extends Screen implements ObserverReadOnlyScreen {
    private final boolean observerReadOnly;
    private final Runnable observerStop;

    protected NexusOwnedScreen(Component title) { this(title, false, () -> { }); }
    protected NexusOwnedScreen(Component title, boolean observerReadOnly, Runnable observerStop) {
        super(title);
        this.observerReadOnly = observerReadOnly;
        this.observerStop = observerStop;
    }

    protected final boolean observerReadOnly() { return observerReadOnly; }
    protected final void closeOwnedScreen() {
        if (observerReadOnly) observerStop.run(); else super.onClose();
    }
    @Override public boolean totem$isObserverReadOnly() { return observerReadOnly; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return observerReadOnly || super.mouseClicked(event, doubled);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return observerReadOnly || super.mouseDragged(event, dragX, dragY);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return observerReadOnly || super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return observerReadOnly || super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!observerReadOnly) return super.keyPressed(event);
        if (event.key() == 256) onClose();
        return true;
    }
    @Override public boolean charTyped(CharacterEvent event) {
        return observerReadOnly || super.charTyped(event);
    }
    @Override public boolean preeditUpdated(PreeditEvent event) {
        return observerReadOnly || super.preeditUpdated(event);
    }
    @Override public void onClose() { closeOwnedScreen(); }
}
