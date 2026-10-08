package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.generation.GenerationContext;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.util.PositionUtil;
import dev.uncanny.util.Throttle;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The ring chamber.
 *
 * Four rings, intersecting, set into the walls of a room that is far too large for
 * the corridor that leads to it. Every ring has openings in it. The openings are
 * evenly spaced, evenly sized, and there are far too many of them.
 *
 * The description this is built from is old: wheels within wheels, rims full of
 * eyes, movement in four directions without turning. The mod treats that as a
 * description of a place, not of a creature, and it never shows a creature. There
 * is nothing in this room. That is the whole point of it.
 *
 * The only thing the room does is this: when nobody is looking at it, the dark
 * openings move to the side nearest the player. It costs a handful of block
 * updates, it happens at most once every few seconds, and it is the single most
 * reported moment in the mod.
 */
public final class EzekielRings {

    private static final Throttle THROTTLE = new Throttle(40);

    /** Radius of the largest ring, in blocks. */
    public static final int RADIUS = 7;

    private EzekielRings() {
    }

    /**
     * Builds the chamber.
     *
     * Four rings on three axes, which is geometrically wrong and visually
     * unmistakable. The "eyes" are dark blocks placed in the rims.
     */
    public static void build(GenerationContext ctx) {
        int cx = 8;
        int cy = 8;
        int cz = 8;
        long eyeSeed = ctx.state().eyeSeed(ctx.world().getSeed());

        // Ring one: flat, in the ceiling.
        StructureBits.eyeRing(ctx, cx, cy + 3, cz, RADIUS, eyeSeed, net.minecraft.util.math.Direction.Axis.Y);
        // Ring two: vertical, facing north-south.
        StructureBits.eyeRing(ctx, cx, cy, cz, RADIUS, eyeSeed + 1, net.minecraft.util.math.Direction.Axis.X);
        // Ring three: vertical, facing east-west.
        StructureBits.eyeRing(ctx, cx, cy, cz, RADIUS, eyeSeed + 2, net.minecraft.util.math.Direction.Axis.Z);
        // Ring four: smaller, inside the others. Wheels within wheels.
        StructureBits.eyeRing(ctx, cx, cy, cz, RADIUS - 3, eyeSeed + 3, net.minecraft.util.math.Direction.Axis.Y);

        // The pupils. These are the blocks that move.
        for (int angle = 0; angle < 360; angle += 30) {
            double radians = Math.toRadians(angle);
            int dx = (int) Math.round(Math.cos(radians) * RADIUS);
            int dz = (int) Math.round(Math.sin(radians) * RADIUS);
            ctx.set(cx + dx, cy, cz + dz, Blocks.BLACK_CONCRETE.getDefaultState());
        }

        ctx.state().setRingPosition(ctx.pos(cx, cy, cz));
    }

    /**
     * Runs every couple of seconds while a player is in the chamber.
     *
     * Cheap by construction: one position check per player, and a dozen block
     * writes only when the player is actually not looking.
     */
    public static void tick(MinecraftServer server) {
        UncannyWorldState state = UncannyWorldState.get(server);
        BlockPos centre = state.ringPosition();
        if (centre == null) {
            return;
        }
        ServerWorld world = server.getWorld(dev.uncanny.dimension.UncannyDimension.PARTITION.key());
        if (world == null || !world.isChunkLoaded(centre.getX() >> 4, centre.getZ() >> 4)) {
            return;
        }
        if (!THROTTLE.ready(server.getOverworld().getTime())) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() != world) {
                continue;
            }
            if (player.squaredDistanceTo(Vec3d.ofCenter(centre)) > 45 * 45) {
                continue;
            }
            if (isLookingAt(player, centre)) {
                return;
            }
            faceThePlayer(world, centre, player.getBlockPos());
            return;
        }
    }

    private static boolean isLookingAt(ServerPlayerEntity player, BlockPos centre) {
        Vec3d toCentre = Vec3d.ofCenter(centre).subtract(player.getEyePos()).normalize();
        return player.getRotationVector().dotProduct(toCentre) > 0.85;
    }

    /** Moves the pupils to the arc nearest the player. Twelve block writes. */
    private static void faceThePlayer(ServerWorld world, BlockPos centre, BlockPos player) {
        double dx = player.getX() - centre.getX();
        double dz = player.getZ() - centre.getZ();
        double base = Math.atan2(dz, dx);
        for (int i = -3; i <= 3; i++) {
            double angle = base + i * 0.22;
            BlockPos at = centre.add(
                    (int) Math.round(Math.cos(angle) * RADIUS),
                    0,
                    (int) Math.round(Math.sin(angle) * RADIUS));
            PositionUtil.setQuietly(world, at, Blocks.BLACK_CONCRETE.getDefaultState());
            PositionUtil.setQuietly(world, at.up(), UncannyBlocks.EYE_VENT.getDefaultState());
        }
    }
}
