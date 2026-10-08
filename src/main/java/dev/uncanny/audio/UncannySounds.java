package dev.uncanny.audio;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.util.TextUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Sound, and the deliberate absence of it.
 *
 * The mod adds no audio files. Everything here is an ordinary Minecraft sound
 * played at an unusual pitch, in an unusual place, or at an unusual volume, which
 * has two advantages: it works in any resource pack, and it sounds like Minecraft
 * doing something slightly wrong rather than like a horror mod bolted on.
 *
 * The two sounds that matter most are the ones that are almost inaudible, and the
 * one that never comes.
 */
public final class UncannySounds {

    /** A scheduled sound. */
    private record Scheduled(long tick, World world, BlockPos pos, SoundEvent sound,
                             float volume, float pitch) {
    }

    private static final Deque<Scheduled> QUEUE = new ArrayDeque<>();

    private UncannySounds() {
    }

    // ---------------------------------------------------------------- playing

    /** Plays a sound at a position, in the AMBIENT category, respecting config. */
    public static void at(World world, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        UncannyConfig config = UncannyConfig.get();
        if (!config.audioEnabled || config.audioVolume <= 0) {
            return;
        }
        world.playSound(null, pos, sound, SoundCategory.AMBIENT,
                (float) (volume * config.audioVolume), pitch);
    }

    /** Convenience overload using a default volume. */
    public static void at(World world, BlockPos pos, float pitch, float volume) {
        at(world, pos, SoundEvents.BLOCK_WOOD_STEP, volume, pitch);
    }

    /** Plays a sound at the player, quiet. */
    public static void near(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        at(player.getWorld(), player.getBlockPos(), sound, volume, pitch);
    }

    /**
     * Plays a footstep behind the player.
     *
     * The position is computed from the player's look vector, six blocks back, so
     * the sound is spatialised correctly and the player turns around to find
     * nothing. It is the oldest trick here and it is used rarely for that reason.
     */
    public static void behind(ServerPlayerEntity player, float pitch) {
        Vec3d look = player.getRotationVector();
        Vec3d target = player.getPos().subtract(look.multiply(6.0));
        BlockPos pos = BlockPos.ofFloored(target);
        at(player.getWorld(), pos, SoundEvents.BLOCK_GRAVEL_STEP, 0.5F, pitch);
    }

    /** A low environmental hum. Long, quiet, and easy to mistake for a cave. */
    public static void hum(World world, BlockPos pos) {
        at(world, pos, SoundEvents.BLOCK_BEACON_AMBIENT, 0.25F, 0.2F);
    }

    /** Wood, under load. */
    public static void creak(World world, BlockPos pos) {
        at(world, pos, SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.35F, 0.5F);
    }

    /** Three knocks, spaced. Uses the scheduler so the spacing is real. */
    public static void knock(MinecraftServer server, World world, BlockPos pos) {
        long now = server.getOverworld().getTime();
        schedule(server, now + 2, world, pos, SoundEvents.BLOCK_WOOD_HIT, 0.6F, 0.7F);
        schedule(server, now + 9, world, pos, SoundEvents.BLOCK_WOOD_HIT, 0.55F, 0.66F);
        schedule(server, now + 16, world, pos, SoundEvents.BLOCK_WOOD_HIT, 0.5F, 0.62F);
    }

    /** A door closing somewhere out of sight. */
    public static void distantDoor(World world, BlockPos pos) {
        at(world, pos, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, 0.3F, 0.8F);
    }

    /** Minecraft's own cave ambience, pitched down until it is almost a chord. */
    public static void wrongAmbience(World world, BlockPos pos) {
        at(world, pos, SoundEvents.AMBIENT_CAVE, 0.4F, 0.35F);
    }

    /** Breathing that is not attached to anything. */
    public static void breathing(World world, BlockPos pos) {
        at(world, pos, SoundEvents.ENTITY_PLAYER_BREATH, 0.3F, 0.7F);
    }

    /** The sound a dimension change makes. One sound. No fanfare. */
    public static void playTransition(ServerPlayerEntity player) {
        at(player.getWorld(), player.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRIGGER, 0.25F, 0.4F);
    }

    /**
     * One line of text on the action bar.
     *
     * Used maybe four times in a whole playthrough. When the mod does speak
     * directly it is a whisper, and it never explains anything.
     */
    public static void whisper(ServerPlayerEntity player, String text) {
        player.sendMessage(Text.literal(TextUtil.spaced(text))
                .setStyle(net.minecraft.text.Style.EMPTY.withColor(net.minecraft.util.Formatting.DARK_GRAY)), true);
    }

    // -------------------------------------------------------------- scheduler

    /**
     * Queues a sound for a future tick.
     *
     * Needed because a knock is three sounds with gaps between them, and because
     * several anomalies are "change something, wait, change it back".
     */
    public static void schedule(MinecraftServer server, long tick, World world, BlockPos pos,
                                SoundEvent sound, float volume, float pitch) {
        QUEUE.addLast(new Scheduled(tick, world, pos, sound, volume, pitch));
        // The queue is only ever a handful of entries, but cap it anyway.
        while (QUEUE.size() > 64) {
            QUEUE.pollFirst();
        }
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        long now = server.getOverworld().getTime();
        List<Scheduled> due = new ArrayList<>();
        while (!QUEUE.isEmpty() && QUEUE.peekFirst().tick() <= now) {
            due.add(QUEUE.pollFirst());
        }
        for (Scheduled entry : due) {
            at(entry.world(), entry.pos(), entry.sound(), entry.volume(), entry.pitch());
        }
    }

    /** Clears pending sounds. Called when a server stops. */
    public static void reset() {
        QUEUE.clear();
    }
}
