package dev.uncanny.rendering;

import dev.uncanny.net.UncannyPayloads;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side visual state.
 *
 * The server can ask the client to do one small visual thing. That request lands
 * here, decays over a few seconds, and is drawn by {@link UncannyRenderer}.
 *
 * Nothing in here affects gameplay and none of it is authoritative. A client that
 * ignores these messages loses atmosphere and nothing else.
 */
public final class AnomalyEffects {

    private static float flicker = 0;
    private static float pulse = 0;
    private static float dim = 0;
    private static float sky = 0;
    private static float staticNoise = 0;

    private AnomalyEffects() {
    }

    /** Called when a VISUAL payload arrives. */
    public static void apply(int effect, float intensity) {
        switch (effect) {
            case UncannyPayloads.VISUAL_FLICKER -> flicker = Math.min(1.0F, intensity);
            case UncannyPayloads.VISUAL_PULSE -> pulse = Math.min(1.0F, intensity);
            case UncannyPayloads.VISUAL_DIM -> dim = Math.min(1.0F, intensity);
            case UncannyPayloads.VISUAL_SKY -> sky = Math.min(1.0F, intensity);
            default -> staticNoise = Math.min(1.0F, intensity);
        }
    }

    /** Called every client tick. Everything decays, nothing persists. */
    public static void tick() {
        flicker = decay(flicker, 0.08F);
        pulse = decay(pulse, 0.03F);
        dim = decay(dim, 0.004F);
        sky = decay(sky, 0.02F);
        staticNoise = decay(staticNoise, 0.05F);
    }

    private static float decay(float value, float rate) {
        return Math.max(0.0F, value - rate);
    }

    /** Draws whatever is currently active, over the top of the world. */
    public static void draw(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        if (flicker > 0) {
            // A single dropped frame of black. Not a flash: a gap.
            if (flicker > 0.6F) {
                context.fill(0, 0, width, height, 0xE0000000);
            }
        }
        if (dim > 0) {
            int alpha = (int) (Math.min(0.55F, dim * 0.55F) * 255) << 24;
            context.fill(0, 0, width, height, alpha);
        }
        if (pulse > 0) {
            // A vignette that tightens and lets go.
            int alpha = (int) (pulse * 90) << 24;
            int inset = (int) (height * 0.18F);
            context.fill(0, 0, width, inset, alpha);
            context.fill(0, height - inset, width, height, alpha);
        }
        if (sky > 0) {
            // The sky goes the wrong colour for a moment, at the top of the screen
            // only, so it reads as the sky rather than as a filter.
            int alpha = (int) (sky * 120) << 24;
            context.fill(0, 0, width, height / 3, alpha | 0x10202E);
        }
    }

    /** True while any effect is active. Used to skip drawing entirely when idle. */
    public static boolean active() {
        return flicker > 0 || pulse > 0 || dim > 0 || sky > 0 || staticNoise > 0;
    }
}
