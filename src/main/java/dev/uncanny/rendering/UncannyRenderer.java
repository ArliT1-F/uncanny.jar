package dev.uncanny.rendering;

import dev.uncanny.audio.AmbientManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Wires the client systems up.
 *
 * Called once from the client initialiser. Two hooks:
 *
 *   ClientTickEvents.END_CLIENT_TICK  ambience, effect decay
 *   HudRenderCallback.EVENT           the visual effects, drawn over the world
 *
 * Everything is client-only. If a server has this mod and a client does not, the
 * client simply misses the visuals; nothing about progression changes.
 */
public final class UncannyRenderer {

    private UncannyRenderer() {
    }

    public static void register() {
        FogEffects.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AnomalyEffects.tick();
            AmbientManager.tick(client);
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!AnomalyEffects.active()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) {
                return;
            }
            AnomalyEffects.draw(context, client);
        });
    }
}
