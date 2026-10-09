package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.SignUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * A sign with text nobody wrote.
 *
 * The lines are short and factual and none of them are threats. "MEASURED TWICE"
 * is funnier and worse than "GET OUT", because it implies someone was working here
 * and intends to come back.
 */
public class WrongSignEvent extends UncannyEvent {

    private static final List<String[]> LINES = List.of(
            new String[]{"MEASURED", "TWICE", "", ""},
            new String[]{"ROOM 18", "NOT ON", "ANY MAP", ""},
            new String[]{"DO NOT", "KNOCK BACK", "", ""},
            new String[]{"THIS DOOR", "WAS NOT", "HERE", ""},
            new String[]{"SUBJECT", "REMAINED", "INSIDE", "02:31"},
            new String[]{"WE BURIED", "THE WRONG", "MAN", ""},
            new String[]{"THE STARS", "ARE BEHIND", "IT", ""});

    public WrongSignEvent() {
        super("wrong_sign", Severity.NOTICED);
        family(Family.ARCHITECTURAL);
        when(EventCondition.and(
                EventCondition.playedMinutes(18),
                EventCondition.openGroundNearby(6)));
        chance(0.22);
        fromStage(2);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos ground = EventCondition.findOpenGround(context, 6);
        if (ground == null) {
            return;
        }
        BlockPos pos = ground;
        if (!context.world.getBlockState(pos).isAir()) {
            return;
        }
        context.world.setBlockState(pos, Blocks.OAK_SIGN.getDefaultState(), 3);
        if (context.world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            String[] lines = LINES.get(context.random().nextInt(LINES.size()));
            SignUtil.setLines(sign, lines);
        }
        context.data.markClue("sign_appeared");
    }
}
