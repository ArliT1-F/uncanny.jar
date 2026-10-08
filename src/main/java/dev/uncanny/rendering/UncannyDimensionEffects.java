package dev.uncanny.rendering;

import net.minecraft.client.render.DimensionEffects;

/**
 * How a layer looks from the inside.
 *
 * Minecraft picks sky colour, cloud height and fog colour from a
 * {@link DimensionEffects} object, keyed by an identifier that the dimension type
 * JSON names. Registering one of these is how the mod changes the mood of a layer
 * without touching shaders or adding a mixin.
 *
 * Every layer in this mod has:
 *   - no sky (there is nothing above any of them to see)
 *   - no clouds
 *   - a fog colour slightly darker than the layer's stone
 *   - no brightening of the lightmap, so torches matter
 *
 * The Prison of Stars is the exception. It has a little ambient light, because a
 * completely black void is indistinguishable from a rendering error.
 */
public class UncannyDimensionEffects extends DimensionEffects {

    private final float[] fogColor;

    public UncannyDimensionEffects(float cloudsHeight, float red, float green, float blue, boolean darkened) {
        // alternateSkyColor=false, skyType=NONE (no sky, no sun, no moon),
        // brightenLighting=false, darkened as requested.
        super(cloudsHeight, false, SkyType.NONE, false, darkened);
        this.fogColor = new float[]{red, green, blue};
    }

    @Override
    public float[] getFogColor(float sunAngle, float tickDelta) {
        return this.fogColor;
    }

    @Override
    public boolean isDarkened() {
        return super.isDarkened();
    }

    /** The Hall: cold grey, close fog. */
    public static UncannyDimensionEffects hall() {
        return new UncannyDimensionEffects(Float.NaN, 0.05F, 0.05F, 0.06F, true);
    }

    /** The Archive: warmer, brown-black. */
    public static UncannyDimensionEffects archive() {
        return new UncannyDimensionEffects(Float.NaN, 0.07F, 0.05F, 0.04F, true);
    }

    /** The House: almost Overworld-like, which is worse. */
    public static UncannyDimensionEffects house() {
        return new UncannyDimensionEffects(Float.NaN, 0.10F, 0.10F, 0.12F, false);
    }

    /** The Copy: a sky that is not quite the right blue. */
    public static UncannyDimensionEffects copy() {
        return new UncannyDimensionEffects(192.0F, 0.42F, 0.48F, 0.58F, false);
    }

    /** The Woods: green-grey, with clouds, because it is pretending to be outside. */
    public static UncannyDimensionEffects woods() {
        return new UncannyDimensionEffects(192.0F, 0.30F, 0.34F, 0.30F, false);
    }

    /** The Deep: near black. */
    public static UncannyDimensionEffects deep() {
        return new UncannyDimensionEffects(Float.NaN, 0.02F, 0.02F, 0.03F, true);
    }

    /** Below: black, with just enough light to prove the void is real. */
    public static UncannyDimensionEffects abyss() {
        return new UncannyDimensionEffects(Float.NaN, 0.01F, 0.01F, 0.02F, true);
    }

    /** The Partition: white-grey, bright enough to read by, and silent. */
    public static UncannyDimensionEffects partition() {
        return new UncannyDimensionEffects(Float.NaN, 0.16F, 0.16F, 0.18F, false);
    }
}
