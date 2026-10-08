package dev.uncanny.audio;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.dimension.UncannyDimension;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side ambience.
 *
 * Two jobs, and both are mostly subtraction:
 *
 *   - in the layers where nothing should be heard, stop Minecraft's own music and
 *     ambience from playing;
 *   - very occasionally, play something that is almost too quiet to place.
 *
 * Silence is a feature of this mod, not an absence of features. A player who
 * spends twenty minutes in the Hall should notice at some point that they have
 * stopped hearing the game.
 */
public final class AmbientManager {

    /** Ticks between ambience decisions. Deliberately slow. */
    public static final int INTERVAL = 20 * 30;

    private static int tickCounter = 0;
    /** How many ticks of forced silence are left. */
    private static int silenceTicks = 0;

    private AmbientManager() {
    }

    /** Called from the client tick. */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        UncannyConfig config = UncannyConfig.get();
        if (!config.enabled || !config.audioEnabled) {
            return;
        }

        tickCounter++;

        if (silenceTicks > 0) {
            silenceTicks--;
            // Stopping the music is the one thing the mod is willing to do to the
            // player's own audio. Cave ambience belongs to the game and suppressing
            // it properly would need a mixin, which this mod deliberately avoids.
            client.getMusicTracker().stop();
            return;
        }

        if (tickCounter < INTERVAL) {
            return;
        }
        tickCounter = 0;

        UncannyDimension here = UncannyDimension.fromPath(client.world.getRegistryKey().getValue().getPath());
        if (here == null) {
            return;
        }

        // Nothing plays at all in the two quietest layers.
        if (here == UncannyDimension.ABYSS || here == UncannyDimension.PARTITION) {
            silenceTicks = INTERVAL;
            return;
        }

        double roll = Math.random();
        if (roll < config.silenceChance) {
            // The absence is the event. Nothing is played, and the player will not
            // be able to say when it started.
            silenceTicks = INTERVAL * 2;
            return;
        }

        if (roll < config.silenceChance + 0.06) {
            BlockPos pos = client.player.getBlockPos().add(
                    client.random.nextInt(17) - 8, 0, client.random.nextInt(17) - 8);
            client.world.playSound(pos, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.15F, 0.3F, false);
        } else if (roll < config.silenceChance + 0.09) {
            BlockPos pos = client.player.getBlockPos().add(
                    client.random.nextInt(11) - 5, 0, client.random.nextInt(11) - 5);
            client.world.playSound(pos, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.AMBIENT, 0.25F, 0.6F, false);
        }
    }

    /** True while the mod is holding the world quiet. */
    public static boolean isSilent() {
        return silenceTicks > 0;
    }

}
