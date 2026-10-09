package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import dev.uncanny.util.SignUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * A sign with the player's own name on it, in the Surveyors' voice.
 *
 * Not a threat and not an address: two short lines of status, the kind the
 * anchor rooms carry, except the subject line is whoever is holding the
 * pickaxe. This is the identity family - the register knows who is present,
 * and says so in the world rather than in the book.
 *
 * HIGH instability only, late stage, once per player, and still rare on top of
 * that: identity inconsistencies are supposed to feel like the story turning a
 * corner, not like a jump scare with a nametag.
 */
public class WrongIdentitySignEvent extends UncannyEvent {

    public WrongIdentitySignEvent() {
        super("wrong_identity", Severity.IMPOSSIBLE);
        family(Family.IDENTITY);
        when(EventCondition.and(
                EventCondition.playedMinutes(60),
                EventCondition.stage(4),
                EventCondition.openGroundNearby(6)));
        chance(0.25);
        onlyOnce();
        fromStage(4);
        fromInstability(0.6);
        advanced();
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos ground = EventCondition.findOpenGround(context, 6);
        if (ground == null) {
            return;
        }
        context.world.setBlockState(ground, Blocks.OAK_SIGN.getDefaultState(), 3);
        if (context.world.getBlockEntity(ground) instanceof SignBlockEntity sign) {
            String name = context.data.username.toUpperCase(java.util.Locale.ROOT);
            if (name.length() > 15) {
                name = name.substring(0, 15);
            }
            SignUtil.setLines(sign, name, "IS", "PRESENT");
        }
        context.data.markClue("identity_sign");
    }
}
