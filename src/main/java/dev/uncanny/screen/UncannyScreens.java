package dev.uncanny.screen;

import dev.uncanny.net.UncannyPayloads;
import net.minecraft.client.MinecraftClient;

/**
 * The one place client screens are opened.
 *
 * Kept separate from the screen class so the networking code does not have to
 * know anything about rendering, and so a server-only environment never loads a
 * client class by accident.
 */
public final class UncannyScreens {

    private UncannyScreens() {
    }

    /** Opens a book on the client. Must be called on the client thread. */
    public static void openBook(UncannyPayloads.Book book) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.setScreen(new UncannyBookScreen(book));
    }
}
