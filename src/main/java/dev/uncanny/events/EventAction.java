package dev.uncanny.events;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.net.UncannyPayloads;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * What an event does.
 *
 * Actions are small on purpose. The interesting part of an anomaly is almost never
 * the change itself - it is that the change is one block, in a place the player
 * has already been, and there is no sound when it happens.
 */
@FunctionalInterface
public interface EventAction {

    void run(EventContext context);

    // -------------------------------------------------------------- helpers

    /** Replaces one block, if the chunk is loaded. */
    static EventAction setBlock(BlockPos pos, BlockState state) {
        return context -> PositionUtil.setQuietly(context.world, pos, state);
    }

    /** Removes one block. */
    static EventAction clearBlock(BlockPos pos) {
        return context -> PositionUtil.clearQuietly(context.world, pos);
    }

    /** Plays a sound at a position. */
    static EventAction sound(BlockPos pos, float pitch) {
        return context -> UncannySounds.at(context.world, pos, pitch, 1.0F);
    }

    /** Plays a sound behind the player, where there is nothing. */
    static EventAction soundBehind(float pitch) {
        return context -> UncannySounds.behind(context.player, pitch);
    }

    /** A short visual effect on the client. */
    static EventAction visual(int effect, float intensity) {
        return context -> UncannyPayloads.sendVisual(context.player, effect, intensity);
    }

    /** Records that the player noticed something. */
    static EventAction clue(String clueId) {
        return context -> {
            context.data.markClue(clueId);
            context.state.markDirty();
        };
    }

    /** Runs several actions in order. */
    static EventAction all(EventAction... actions) {
        return context -> {
            for (EventAction action : actions) {
                action.run(context);
            }
        };
    }

    /** Sends a single action bar line. Used sparingly; the mod prefers silence. */
    static EventAction whisper(String text) {
        return context -> UncannySounds.whisper(context.player, text);
    }

    /** Marks an event as done so it never repeats. */
    static EventAction once(String eventId) {
        return context -> context.data.markEventDone(eventId);
    }

    /** Convenience for the common case: notify a specific player. */
    static EventAction notify(ServerPlayerEntity player, String message) {
        return context -> player.sendMessage(net.minecraft.text.Text.literal(message), true);
    }
}
