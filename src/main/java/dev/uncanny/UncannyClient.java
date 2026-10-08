package dev.uncanny;

import dev.uncanny.net.UncannyPayloads;
import dev.uncanny.rendering.AnomalyEffects;
import dev.uncanny.rendering.UncannyRenderer;
import dev.uncanny.screen.UncannyScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client initialisation.
 *
 * Three things happen here:
 *   - the layer visuals are registered;
 *   - the two display-only payloads get receivers;
 *   - nothing else. No progression, no state, no decisions.
 *
 * A client without this mod can still play on a server that has it. They simply
 * will not see the fog, the effects, or the pages - and the server will not care.
 */
public class UncannyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        UncannyRenderer.register();

        // "Show these pages." Read on the network thread, used on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(UncannyPayloads.OPEN_BOOK,
                (client, handler, buf, responseSender) -> {
                    UncannyPayloads.Book book = UncannyPayloads.readBook(buf);
                    client.execute(() -> UncannyScreens.openBook(book));
                });

        // "Do this small visual thing."
        ClientPlayNetworking.registerGlobalReceiver(UncannyPayloads.VISUAL,
                (client, handler, buf, responseSender) -> {
                    int effect = buf.readVarInt();
                    float intensity = buf.readFloat();
                    client.execute(() -> AnomalyEffects.apply(effect, intensity));
                });
    }
}
