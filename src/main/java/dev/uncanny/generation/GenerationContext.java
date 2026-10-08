package dev.uncanny.generation;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.util.PositionUtil;
import dev.uncanny.util.RandomUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;

import java.util.function.Consumer;

/**
 * Everything a room builder is allowed to touch, in one object.
 *
 * Coordinates in a builder are LOCAL: (0,0,0) is the corner of the chunk at floor
 * level, and a builder may not write outside its own chunk. Neighbours are never
 * read and never written, which is what makes it safe to generate two chunks at
 * the same time.
 *
 * The two sides of a shared wall each carve their own doorway at the same local
 * coordinates, so corridors meet without the chunks having to coordinate.
 */
public final class GenerationContext {

    /** Size of a chunk. Rooms are chunk-sized so they never straddle a border. */
    public static final int SIZE = 16;

    private final ServerWorld world;
    private final MinecraftServer server;
    private final UncannyWorldState state;
    private final UncannyDimension dimension;
    private final Room room;
    private final RandomUtil.UncannyRandom random;
    private final BlockPos corner;
    private final int floorY;
    private int writes = 0;

    public GenerationContext(ServerWorld world, MinecraftServer server, UncannyWorldState state,
                             UncannyDimension dimension, Room room, long seed) {
        this.world = world;
        this.server = server;
        this.state = state;
        this.dimension = dimension;
        this.room = room;
        this.random = RandomUtil.of(seed);
        this.floorY = dimension.connectorY();
        this.corner = new BlockPos(room.chunk.getStartX(), this.floorY, room.chunk.getStartZ());
    }

    // ------------------------------------------------------------- accessors

    public ServerWorld world() {
        return this.world;
    }

    public MinecraftServer server() {
        return this.server;
    }

    public UncannyWorldState state() {
        return this.state;
    }

    public UncannyDimension dimension() {
        return this.dimension;
    }

    public Room room() {
        return this.room;
    }

    public RoomTemplate template() {
        return this.room.template;
    }

    public ChunkPos chunk() {
        return this.room.chunk;
    }

    public int linkMask() {
        return this.room.linkMask;
    }

    public int floorY() {
        return this.floorY;
    }

    public long seed() {
        return this.room.seed;
    }

    /** A sub-stream of the room's randomness, stable for a given salt. */
    public long salt(long value) {
        return this.room.seed ^ (value * 0x9E3779B97F4A7C15L);
    }

    public RandomUtil.UncannyRandom random() {
        return this.random;
    }

    public boolean hasLink(Direction direction) {
        return (this.room.linkMask & GenerationRules.bitOf(direction)) != 0;
    }

    // ------------------------------------------------------------- positions

    /** Local coordinates to world coordinates. */
    public BlockPos pos(int x, int y, int z) {
        return this.corner.add(x, y, z);
    }

    public int worldX(int x) {
        return this.corner.getX() + x;
    }

    public int worldZ(int z) {
        return this.corner.getZ() + z;
    }

    // ---------------------------------------------------------------- writes

    public void set(int x, int y, int z, BlockState state) {
        BlockPos pos = pos(x, y, z);
        // The chunk is loaded (we are generating it) but a defensive check keeps a
        // race during unload from throwing during world load.
        if (this.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            this.world.setBlockState(pos, state, PositionUtil.QUIET);
            this.writes++;
        }
    }

    public void set(int x, int y, int z, net.minecraft.block.Block block) {
        set(x, y, z, block.getDefaultState());
    }

    public void setBlock(BlockPos pos, BlockState state) {
        if (this.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            this.world.setBlockState(pos, state, PositionUtil.QUIET);
            this.writes++;
        }
    }

    /** Fills an inclusive box. Coordinates are local. */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Clears an inclusive box back to air. */
    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.getDefaultState());
    }

    /** Fills the whole chunk column between two heights. */
    public void column(int y1, int y2, BlockState state) {
        fill(0, y1, 0, SIZE - 1, y2, SIZE - 1, state);
    }

    // ----------------------------------------------------------------- shell

    /**
     * Builds a sealed box and then opens the doorways this room is connected by.
     *
     * Almost every template starts with this. The interior is deliberately left
     * empty for the builder to shape.
     */
    public void shell(BlockState wall, BlockState floor, int interiorHeight) {
        // Floor slab and ceiling slab, then the four border walls.
        fill(0, -1, 0, SIZE - 1, -1, SIZE - 1, floor);
        fill(0, interiorHeight, 0, SIZE - 1, interiorHeight, SIZE - 1, wall);
        fill(0, 0, 0, SIZE - 1, interiorHeight - 1, 0, wall);
        fill(0, 0, SIZE - 1, SIZE - 1, interiorHeight - 1, SIZE - 1, wall);
        fill(0, 0, 0, 0, interiorHeight - 1, SIZE - 1, wall);
        fill(SIZE - 1, 0, 0, SIZE - 1, interiorHeight - 1, SIZE - 1, wall);
        clear(1, 0, 1, SIZE - 2, interiorHeight - 1, SIZE - 2);
        fill(0, 0, 1, SIZE - 1, 0, SIZE - 1, floor);
        openDoorways(interiorHeight);
    }

    /**
     * Carves a doorway on every connected side, at floor level.
     *
     * Centred on local 7-8 so that the neighbouring chunk, doing the same thing,
     * lines up exactly. Two blocks wide and three high is enough to walk through
     * and narrow enough to feel like a corridor.
     */
    public void openDoorways(int interiorHeight) {
        int height = Math.min(3, Math.max(2, interiorHeight - 1));
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (!hasLink(direction)) {
                continue;
            }
            switch (direction) {
                case NORTH -> clear(7, 0, 0, 8, height - 1, 0);
                case SOUTH -> clear(7, 0, SIZE - 1, 8, height - 1, SIZE - 1);
                case WEST -> clear(0, 0, 7, 0, height - 1, 8);
                default -> clear(SIZE - 1, 0, 7, SIZE - 1, height - 1, 8);
            }
        }
    }

    /**
     * Guarantees that a built room can actually be walked through.
     *
     * Called after every builder, so a template cannot accidentally seal its own
     * doorway - which is easy to do, because most templates fill the interior with
     * wall and then carve a shape out of it.
     *
     * It cuts a two-wide, three-high spoke from the middle of the chunk to each
     * open side. The middle of the chunk is always inside the room, so if a
     * doorway is reachable from the middle, it is reachable from everywhere.
     *
     * Templates that manage their own paths can opt out with
     * RoomTemplate.Builder.ensuresOwnAccess().
     */
    public void ensureAccessible() {
        int height = 3;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (!hasLink(direction)) {
                continue;
            }
            switch (direction) {
                case NORTH -> clear(7, 0, 0, 8, height - 1, 8);
                case SOUTH -> clear(7, 0, 8, 8, height - 1, SIZE - 1);
                case WEST -> clear(0, 0, 7, 8, height - 1, 8);
                default -> clear(8, 0, 7, SIZE - 1, height - 1, 8);
            }
        }
    }

    /** Carves a straight corridor through the room along an axis. */
    public void corridorAlong(Direction.Axis axis, int halfWidth, int height) {
        if (axis == Direction.Axis.X) {
            clear(0, 0, 8 - halfWidth, SIZE - 1, height - 1, 8 + halfWidth - 1);
        } else {
            clear(8 - halfWidth, 0, 0, 8 + halfWidth - 1, height - 1, SIZE - 1);
        }
    }

    // ------------------------------------------------------------ containers

    /** Places a chest and lets the caller fill it. Returns false if it did not fit. */
    public boolean chest(int x, int y, int z, Direction facing, Consumer<ChestBlockEntity> filler) {
        set(x, y, z, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
        if (this.world.getBlockEntity(pos(x, y, z)) instanceof ChestBlockEntity chest) {
            filler.accept(chest);
            return true;
        }
        return false;
    }

    /** Convenience: puts one item in the middle of a chest. */
    public void chestWith(int x, int y, int z, Direction facing, ItemStack stack) {
        chest(x, y, z, facing, chest -> chest.setStack(13, stack));
    }

    /**
     * Places a sign with up to four lines.
     *
     * Signs are how most of the mod's short writing reaches the player, so this
     * helper is used a lot. Text is passed in already written; the caller decides
     * the voice.
     */
    public boolean sign(int x, int y, int z, String... lines) {
        BlockPos pos = pos(x, y, z);
        set(x, y, z, Blocks.OAK_SIGN.getDefaultState());
        if (this.world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            for (int row = 0; row < 4; row++) {
                String line = row < lines.length ? lines[row] : "";
                sign.setTextOnRow(row, Text.literal(line));
            }
            return true;
        }
        return false;
    }

    /** A sign flat against a wall, facing away from it. */
    public boolean wallSign(int x, int y, int z, Direction facing, String... lines) {
        BlockPos pos = pos(x, y, z);
        set(x, y, z, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing));
        if (this.world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            for (int row = 0; row < 4; row++) {
                String line = row < lines.length ? lines[row] : "";
                sign.setTextOnRow(row, Text.literal(line));
            }
            return true;
        }
        return false;
    }

    /** A bed. Beds matter to this mod: sleeping is one of the ways in. */
    public void bed(int x, int y, int z, Direction facing) {
        BlockPos foot = pos(x, y, z);
        BlockPos head = foot.offset(facing);
        setBlock(foot, Blocks.RED_BED.getDefaultState()
                .with(net.minecraft.block.BedBlock.FACING, facing)
                .with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.FOOT));
        setBlock(head, Blocks.RED_BED.getDefaultState()
                .with(net.minecraft.block.BedBlock.FACING, facing)
                .with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD));
    }

    /** A door, closed. Some anomalies open it later. */
    public void door(int x, int y, int z, Direction facing, boolean open) {
        BlockPos foot = pos(x, y, z);
        setBlock(foot, Blocks.OAK_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, facing)
                .with(net.minecraft.block.DoorBlock.OPEN, open)
                .with(net.minecraft.block.DoorBlock.HALF, DoubleBlockHalf.LOWER));
        setBlock(foot.up(), Blocks.OAK_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, facing)
                .with(net.minecraft.block.DoorBlock.OPEN, open)
                .with(net.minecraft.block.DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A torch on the floor. Removing one later is a classic quiet anomaly. */
    public void torch(int x, int y, int z) {
        set(x, y, z, Blocks.TORCH.getDefaultState());
    }

    /** How many block writes this room has made, for the debug readout. */
    public int writes() {
        return this.writes;
    }

    /** Convenience used by builders that need the arrival height of the layer. */
    public int arrivalY() {
        return this.dimension.arrival().getY();
    }

    /** The layer's own arrival point, if it is inside this chunk. */
    public boolean containsArrival() {
        BlockPos arrival = this.dimension.arrival();
        return PositionUtil.sameChunk(arrival, this.corner);
    }

    /** Registers a doorway here that leads to another layer. */
    public void threshold(int x, int y, int z, UncannyDimension target) {
        BlockPos pos = pos(x, y, z);
        this.state.addThreshold(pos, target.path());
    }

    /** Ensures a chunk is safe to read from a builder (it always is, but be sure). */
    public boolean loaded() {
        return this.world.isChunkLoaded(this.chunk().x, this.chunk().z);
    }

    /** Used by the House/Copy builders, which need the layer they are echoing. */
    public ServerWorld overworld() {
        return DimensionManager.worldOf(this.server, UncannyDimension.OVERWORLD);
    }
}
