package dev.uncanny.rendering;

import dev.uncanny.dimension.UncannyDimension;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;

/**
 * Registers the look of every layer.
 *
 * One call each, at client startup. The identifiers here must match the "effects"
 * field in the matching dimension type JSON, or Minecraft will fall back to the
 * Overworld's sky - which is a very obvious bug, so the two files are kept next to
 * each other in the documentation.
 */
public final class FogEffects {

    private FogEffects() {
    }

    public static void register() {
        register(UncannyDimension.HALL, UncannyDimensionEffects.hall());
        register(UncannyDimension.ARCHIVE, UncannyDimensionEffects.archive());
        register(UncannyDimension.HOUSE, UncannyDimensionEffects.house());
        register(UncannyDimension.COPY, UncannyDimensionEffects.copy());
        register(UncannyDimension.WOODS, UncannyDimensionEffects.woods());
        register(UncannyDimension.DEEP, UncannyDimensionEffects.deep());
        register(UncannyDimension.ABYSS, UncannyDimensionEffects.abyss());
        register(UncannyDimension.PARTITION, UncannyDimensionEffects.partition());
    }

    private static void register(UncannyDimension dimension, UncannyDimensionEffects effects) {
        DimensionRenderingRegistry.registerDimensionEffects(dimension.id(), effects);
    }
}
