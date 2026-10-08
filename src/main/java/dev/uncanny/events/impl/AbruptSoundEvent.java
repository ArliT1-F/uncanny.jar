package dev.uncanny.events.impl;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * A sound that begins and stops.
 *
 * One quiet wooden click, somewhere behind the player, and then nothing ever
 * follows it. It is not a footstep event that failed; it is an attempt that
 * aborted halfway, and it exists so that the real sounds keep their meaning:
 * sometimes nothing is there, and sometimes something is, and the difference
 * cannot be felt at the time.
 *
 * Near misses never count as anomalies: they have their own long cooldown, they
 * book no clue, and they raise nothing.
 */
public class AbruptSoundEvent extends UncannyEvent {

    public AbruptSoundEvent() {
        super("abrupt_sound", Severity.QUIET);
        family(Family.NEAR_MISS);
        nearMiss();
        when(EventCondition.playedMinutes(15));
        chance(0.12);
    }

    @Override
    protected void perform(EventContext context) {
        Vec3d look = context.player.getRotationVector();
        BlockPos target = BlockPos.ofFloored(context.player.getPos().subtract(look.multiply(7.0)));
        UncannySounds.at(context.world, target, SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
                0.22F, 0.7F + context.random().nextFloat() * 0.2F);
        // Nothing follows. That is the entire event.
    }
}
