package dev.uncanny.generation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * A template that has been placed somewhere.
 *
 * Created fresh for every chunk and thrown away afterwards: nothing about a room
 * is stored, because the same room can always be recomputed from its coordinates.
 */
public final class Room {

    public final RoomTemplate template;
    public final ChunkPos chunk;
    public final BlockPos origin;
    public final int linkMask;
    public final long seed;

    /** For landmarks: which chunk of the landmark this is, 0 or 1 on each axis. */
    public final int landmarkOffsetX;
    public final int landmarkOffsetZ;

    public Room(RoomTemplate template, ChunkPos chunk, BlockPos origin, int linkMask, long seed,
                int landmarkOffsetX, int landmarkOffsetZ) {
        this.template = template;
        this.chunk = chunk;
        this.origin = origin;
        this.linkMask = linkMask;
        this.seed = seed;
        this.landmarkOffsetX = landmarkOffsetX;
        this.landmarkOffsetZ = landmarkOffsetZ;
    }

    /** True for the chunk of a landmark that holds its centre piece. */
    public boolean isLandmarkPrimary() {
        return this.template.isLandmark && this.landmarkOffsetX == 0 && this.landmarkOffsetZ == 0;
    }
}
