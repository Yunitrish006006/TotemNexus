package dev.totem.nexus.client;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import dev.totem.core.api.v1.manual.TotemManualRegistry;
import dev.totem.core.api.v1.manual.TotemManualSection;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Captures both supported Nexus manual languages in the real shared-book screen. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusManualVisualGameTest implements FabricClientGameTest {
    private static final Identifier NEXUS_SECTION = Identifier.parse("totem:nexus/teleport");

    @Override
    public void runTest(ClientGameTestContext context) {
        selectLanguage(context, "zh_tw", "Nexus 傳送陣");
        context.getInput().resizeWindow(1280, 720);
        captureManual(context, "nexus-manual-spread");

        selectLanguage(context, "en_us", "Nexus Teleport Arrays");
        context.getInput().resizeWindow(1279, 720);
        context.getInput().resizeWindow(1280, 720);
        captureManual(context, "nexus-manual-en-us-spread");
    }

    private static void selectLanguage(
            ClientGameTestContext context,
            String language,
            String expectedTitle
    ) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> {
            client.options.languageCode = language;
            client.getLanguageManager().setSelected(language);
            reload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> reload.get() != null && reload.get().isDone());
        context.waitFor(client -> client.gui.overlay() == null);
        context.runOnClient(client -> {
            String title = I18n.get("book.deadrecall.nexus_teleport_manual.title");
            if (!title.equals(expectedTitle)) {
                throw new AssertionError(language + " Nexus manual resources were not loaded: " + title);
            }
            client.options.guiScale().set(3);
        });
    }

    private static void captureManual(
            ClientGameTestContext context,
            String screenshotPrefix
    ) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            List<TotemManualSection> sections = TotemManualRegistry.global().sections();
            int firstPage = firstPageOf(sections, NEXUS_SECTION);
            int pageCount = sectionPageCount(sections, NEXUS_SECTION);
            ItemStack manual = TotemManualAssembler.create(sections);

            context.runOnClient(client -> client.setScreenAndShow(new BookViewScreen(
                    BookViewScreen.BookAccess.fromItem(manual)
            )));
            context.waitForScreen(BookViewScreen.class);
            context.waitTicks(10);

            int firstSpread = firstPage & ~1;
            int lastPage = firstPage + pageCount - 1;
            for (int page = firstSpread; page <= lastPage; page += 2) {
                int capturedPage = page;
                context.runOnClient(client -> {
                    BookViewScreen screen = (BookViewScreen) client.gui.screen();
                    if (screen == null) {
                        throw new AssertionError("Totem manual screen closed before Nexus page " + (capturedPage + 1));
                    }
                    screen.setPage(capturedPage);
                });
                context.waitTicks(2);
                context.takeScreenshot((screenshotPrefix + "-%02d-%02d")
                        .formatted(page + 1, page + 2));
            }

            context.runOnClient(client -> client.setScreenAndShow(null));
        }
    }

    private static int firstPageOf(List<TotemManualSection> sections, Identifier target) {
        int page = 2;
        for (TotemManualSection section : sections.stream().sorted().toList()) {
            if (section.id().equals(target)) {
                return page;
            }
            page += 1 + section.pageKeys().size();
        }
        throw new AssertionError("Missing Nexus section in the shared Totem manual");
    }

    private static int sectionPageCount(List<TotemManualSection> sections, Identifier target) {
        return sections.stream()
                .filter(section -> section.id().equals(target))
                .findFirst()
                .map(section -> 1 + section.pageKeys().size())
                .orElseThrow(() -> new AssertionError("Missing Nexus section in the shared Totem manual"));
    }
}
