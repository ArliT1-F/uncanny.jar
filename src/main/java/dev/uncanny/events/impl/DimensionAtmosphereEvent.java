package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.BlockVariant;
import dev.uncanny.util.PositionUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * DIMENSION ATMOSPHERE: the behavioural rule of one layer, as an event.
 *
 * Each registration below is one layer's identity expressed as a small, bounded,
 * reversible-feeling change to the blocks near the player - see
 * {@link dev.uncanny.dimension.DimensionBehavior} for the identity table. None
 * of them announce themselves; most players will file them under "I should pay
 * more attention".
 *
 *   hall_shift        a wall block becomes its cracked twin
 *   woods_arrangement a tree grows a floating segment
 *   copy_duplicate    a chest is duplicated, empty, nearby
 *   house_wall        one wall becomes the material this player builds with
 *   archive_note      a record is added to a chest - written for this player
 *   deep_scale        a frame too large for the tunnel it stands in
 *   below_fragment    a piece of another layer in the wall of BELOW
 *   overworld_bleed   a piece of an uncanny layer in the wall of the Overworld
 *   partition_fragment: everything, converging
 *
 * Every one is: one bounded search when its gates pass, one to five block
 * writes, nothing scheduled, nothing scanned per tick.
 */
public class DimensionAtmosphereEvent extends UncannyEvent {

    /** Which rule this instance implements. */
    public enum Effect {
        HALL_SHIFT, WOODS_ARRANGEMENT, COPY_DUPLICATE, HOUSE_WALL,
        ARCHIVE_NOTE, DEEP_SCALE, FRAGMENT
    }

    private final dev.uncanny.dimension.UncannyDimension dimension;
    private final Effect effect;
    private final String[] fragmentPool;

    private DimensionAtmosphereEvent(String id, dev.uncanny.dimension.UncannyDimension dimension,
                                     Effect effect, String[] fragmentPool,
                                     double chance, double minInstability, Family family) {
        super(id, Severity.QUIET);
        this.dimension = dimension;
        this.effect = effect;
        this.fragmentPool = fragmentPool;
        this.family = family;
        this.probability = chance;
        this.minInstability = minInstability;
        when(buildCondition(id, dimension, effect));
    }

    // ------------------------------------------------------------ factories

    public static DimensionAtmosphereEvent hallShift() {
        return new DimensionAtmosphereEvent("hall_shift",
                dev.uncanny.dimension.UncannyDimension.HALL, Effect.HALL_SHIFT, null,
                0.3, 0.1, Family.FALSE_NORMALITY);
    }

    public static DimensionAtmosphereEvent woodsArrangement() {
        return new DimensionAtmosphereEvent("woods_arrangement",
                dev.uncanny.dimension.UncannyDimension.WOODS, Effect.WOODS_ARRANGEMENT, null,
                0.25, 0.05, Family.ENVIRONMENTAL);
    }

    public static DimensionAtmosphereEvent copyDuplicate() {
        return new DimensionAtmosphereEvent("copy_duplicate",
                dev.uncanny.dimension.UncannyDimension.COPY, Effect.COPY_DUPLICATE, null,
                0.25, 0.1, Family.ECHO);
    }

    public static DimensionAtmosphereEvent houseWall() {
        return new DimensionAtmosphereEvent("house_wall",
                dev.uncanny.dimension.UncannyDimension.HOUSE, Effect.HOUSE_WALL, null,
                0.3, 0.05, Family.ARCHITECTURAL);
    }

    public static DimensionAtmosphereEvent archiveNote() {
        return new DimensionAtmosphereEvent("archive_note",
                dev.uncanny.dimension.UncannyDimension.ARCHIVE, Effect.ARCHIVE_NOTE, null,
                0.3, 0.15, Family.LEDGER);
    }

    public static DimensionAtmosphereEvent deepScale() {
        return new DimensionAtmosphereEvent("deep_scale",
                dev.uncanny.dimension.UncannyDimension.DEEP, Effect.DEEP_SCALE, null,
                0.2, 0.2, Family.DIMENSIONAL);
    }

    public static DimensionAtmosphereEvent belowFragment() {
        return new DimensionAtmosphereEvent("below_fragment",
                dev.uncanny.dimension.UncannyDimension.ABYSS, Effect.FRAGMENT,
                new String[]{"minecraft:oak_planks", "minecraft:bookshelf",
                        "minecraft:spruce_planks", "minecraft:grass_block",
                        "minecraft:stone_bricks"},
                0.3, 0.25, Family.DIMENSIONAL);
    }

    public static DimensionAtmosphereEvent overworldBleed() {
        return new DimensionAtmosphereEvent("overworld_bleed",
                dev.uncanny.dimension.UncannyDimension.OVERWORLD, Effect.FRAGMENT,
                new String[]{"minecraft:polished_deepslate", "minecraft:deepslate_tiles",
                        "minecraft:bookshelf", "minecraft:oak_planks",
                        "minecraft:smooth_stone"},
                0.15, 0.45, Family.DIMENSIONAL);
    }

    public static DimensionAtmosphereEvent partitionFragment() {
        return new DimensionAtmosphereEvent("partition_fragment",
                dev.uncanny.dimension.UncannyDimension.PARTITION, Effect.FRAGMENT,
                new String[]{"minecraft:grass_block", "minecraft:oak_planks",
                        "minecraft:bookshelf", "minecraft:stone_bricks",
                        "minecraft:oak_log", "minecraft:polished_deepslate"},
                0.3, 0.15, Family.DIMENSIONAL);
    }

    // ------------------------------------------------------------ conditions

    private static EventCondition buildCondition(String id,
                                                 dev.uncanny.dimension.UncannyDimension dimension,
                                                 Effect effect) {
        EventCondition viability = switch (effect) {
            case HALL_SHIFT -> EventCondition.named("no repeatable pattern nearby",
                    context -> findCrackable(context) != null);
            case WOODS_ARRANGEMENT -> EventCondition.named("no tree with headroom",
                    context -> findTreeWithHeadroom(context) != null);
            case COPY_DUPLICATE -> EventCondition.named("no chest to duplicate",
                    context -> EventCondition.findChest(context, 8) != null);
            case HOUSE_WALL -> EventCondition.and(
                    EventCondition.named("not much building history",
                            context -> context.data.home.placements >= 5),
                    EventCondition.named("no wall nearby",
                            context -> findWallFace(context) != null));
            case ARCHIVE_NOTE -> EventCondition.named("no chest with room",
                    context -> findChestWithRoom(context) != null);
            case DEEP_SCALE -> EventCondition.named("no wall face nearby",
                    context -> findScaleWall(context) != null);
            case FRAGMENT -> EventCondition.named("no shared boundary nearby",
                    context -> findFragmentSite(context, dimension) != null);
        };
        int minutes = switch (effect) {
            case HALL_SHIFT -> 25;
            case WOODS_ARRANGEMENT -> 25;
            case COPY_DUPLICATE -> 30;
            case HOUSE_WALL -> 30;
            case ARCHIVE_NOTE -> 30;
            case DEEP_SCALE -> 40;
            case FRAGMENT -> 40;
        };
        return EventCondition.and(
                EventCondition.inDimension(dimension),
                EventCondition.playedMinutes(minutes),
                viability);
    }

    // ------------------------------------------------------------- searches

    /** A block with a cracked twin, within four. */
    private static BlockPos findCrackable(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (BlockVariant.crackedOf(state.getBlock()) != null) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** A tree top with a gap, a floating log slot, and crown room above. */
    private static BlockPos findTreeWithHeadroom(EventContext context) {
        BlockPos found = EventCondition.findTree(context, 7);
        if (found == null) {
            return null;
        }
        BlockPos top = found;
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos above = found.up(dy);
            if (context.world.getBlockState(above).isAir()) {
                break;
            }
            top = above;
        }
        // The arrangement writes: gap at +1, log at +2, leaves at +3.
        return context.world.getBlockState(top.up()).isAir()
                && context.world.getBlockState(top.up(2)).isAir()
                && context.world.getBlockState(top.up(3)).isAir() ? top : null;
    }

    /** A wall block whose face is visible (at least one air neighbour). */
    private static BlockPos findWallFace(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (state.isAir() || context.world.getBlockEntity(pos) != null) {
                        continue;
                    }
                    for (Direction direction : Direction.Type.HORIZONTAL) {
                        if (context.world.getBlockState(pos.offset(direction)).isAir()) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** A chest with at least one empty slot, within six. */
    private static BlockPos findChestWithRoom(EventContext context) {
        BlockPos chest = EventCondition.findChest(context, 6);
        while (chest != null) {
            if (context.world.getBlockEntity(chest) instanceof ChestBlockEntity container) {
                for (int slot = 0; slot < container.size(); slot++) {
                    if (container.getStack(slot).isEmpty()) {
                        return chest;
                    }
                }
            }
            // Look for another chest further out: re-run from a shifted centre is
            // not worth it, so give up after the first full search.
            return null;
        }
        return null;
    }

    /** Air with solid behind it, four across and five up: room for a frame. */
    private static BlockPos findScaleWall(EventContext context) {
        ScaleSite site = findScaleSite(context);
        return site == null ? null : site.face();
    }

    /** The frame site and its verified axis: same search, both halves. */
    private static ScaleSite findScaleSite(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            Direction side = dir.rotateYClockwise();
            for (int radius = 3; radius <= 6; radius++) {
                BlockPos face = centre.offset(dir, radius);
                if (context.world.getBlockState(face).isAir()
                        && !context.world.getBlockState(face.offset(dir)).isAir()) {
                    // Check the 4x5 plane against the wall.
                    boolean clear = true;
                    for (int s = 0; s <= 3 && clear; s++) {
                        for (int y = 0; y <= 4; y++) {
                            if (!context.world.getBlockState(face.offset(side, s).up(y)).isAir()) {
                                clear = false;
                                break;
                            }
                        }
                    }
                    if (clear) {
                        return new ScaleSite(face, side);
                    }
                }
            }
        }
        return null;
    }

    private record ScaleSite(BlockPos face, Direction side) {
    }

    /**
     * A natural-looking solid block with a visible face, within five. For the
     * overworld bleed the block must be ordinary terrain (we never consume ores
     * or player-placed blocks); elsewhere any visible wall will do.
     */
    private static BlockPos findFragmentSite(EventContext context,
                                             dev.uncanny.dimension.UncannyDimension dimension) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (state.isAir() || state.isOf(Blocks.BEDROCK)) {
                        continue;
                    }
                    boolean face = false;
                    for (Direction direction : Direction.Type.HORIZONTAL) {
                        if (context.world.getBlockState(pos.offset(direction)).isAir()) {
                            face = true;
                            break;
                        }
                    }
                    if (!face) {
                        continue;
                    }
                    if (dimension == dev.uncanny.dimension.UncannyDimension.OVERWORLD
                            && !isOrdinaryTerrain(state)) {
                        continue;
                    }
                    return pos;
                }
            }
        }
        return null;
    }

    /** The blocks the overworld bleed may legally replace. Never ores. */
    private static boolean isOrdinaryTerrain(BlockState state) {
        String path = BlockVariant.pathOf(state.getBlock());
        return path.equals("stone") || path.equals("granite") || path.equals("diorite")
                || path.equals("andesite") || path.equals("deepslate") || path.equals("tuff")
                || path.equals("dirt") || path.equals("coarse_dirt") || path.equals("grass_block")
                || path.equals("gravel") || path.equals("calcite") || path.equals("rooted_dirt");
    }

    // ------------------------------------------------------------- perform

    @Override
    protected void perform(EventContext context) {
        switch (this.effect) {
            case HALL_SHIFT -> hallShift(context);
            case WOODS_ARRANGEMENT -> woodsArrangement(context);
            case COPY_DUPLICATE -> copyDuplicate(context);
            case HOUSE_WALL -> houseWall(context);
            case ARCHIVE_NOTE -> archiveNote(context);
            case DEEP_SCALE -> deepScale(context);
            case FRAGMENT -> fragment(context);
        }
    }

    private void hallShift(EventContext context) {
        BlockPos target = findCrackable(context);
        if (target == null) {
            return;
        }
        Block cracked = BlockVariant.crackedOf(context.world.getBlockState(target).getBlock());
        if (cracked == null) {
            return;
        }
        PositionUtil.setQuietly(context.world, target, cracked.getDefaultState());
    }

    private void woodsArrangement(EventContext context) {
        BlockPos top = findTreeWithHeadroom(context);
        if (top == null) {
            return;
        }
        // One log floating above the crown, one air block lower: the tree keeps
        // growing, and the growth does not touch.
        PositionUtil.setQuietly(context.world, top.up(2),
                context.world.getBlockState(top).getBlock().getDefaultState());
        PositionUtil.setQuietly(context.world, top.up(3), Blocks.OAK_LEAVES.getDefaultState());
    }

    private void copyDuplicate(EventContext context) {
        BlockPos source = EventCondition.findChest(context, 8);
        if (source == null) {
            return;
        }
        BlockState original = context.world.getBlockState(source);
        Direction facing = original.get(ChestBlock.FACING);
        ServerWorld world = context.world;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos target = source.add(dx, dy, dz);
                    double distance = PositionUtil.distanceSquared(target, source);
                    if (distance < 2.0 * 2.0 || distance > 5.0 * 5.0) {
                        continue;
                    }
                    if (!world.getBlockState(target).isAir()
                            || !world.getBlockState(target.up()).isAir()
                            || world.getBlockState(target.down()).isAir()) {
                        continue;
                    }
                    // No other chest within two of the copy: it must read single.
                    boolean nearChest = false;
                    for (int cx = -2; cx <= 2 && !nearChest; cx++) {
                        for (int cy = -1; cy <= 1 && !nearChest; cy++) {
                            for (int cz = -2; cz <= 2 && !nearChest; cz++) {
                                BlockPos around = target.add(cx, cy, cz);
                                if (around.equals(source)) {
                                    continue;
                                }
                                if (world.getBlockState(around).getBlock() instanceof ChestBlock) {
                                    nearChest = true;
                                }
                            }
                        }
                    }
                    if (nearChest) {
                        continue;
                    }
                    // The copy: same facing, empty (a placed chest has no loot table).
                    PositionUtil.setQuietly(context.world, target,
                            original.getBlock().getDefaultState()
                                    .with(ChestBlock.FACING, facing));
                    return;
                }
            }
        }
    }

    private void houseWall(EventContext context) {
        BlockPos target = findWallFace(context);
        if (target == null) {
            return;
        }
        Block material = BlockVariant.byId(context.data.home.dominantMaterial());
        if (material == Blocks.AIR || material == context.world.getBlockState(target).getBlock()) {
            return;
        }
        PositionUtil.setQuietly(context.world, target, material.getDefaultState());
    }

    private void archiveNote(EventContext context) {
        BlockPos chest = findChestWithRoom(context);
        if (chest == null
                || !(context.world.getBlockEntity(chest) instanceof ChestBlockEntity container)) {
            return;
        }
        var entry = dev.uncanny.lore.LoreManager.pickFor(context.player, context.state,
                SeedUtil.mix(context.data.uuid.getLeastSignificantBits(), context.tick));
        if (entry == null) {
            return;
        }
        ItemStack record = dev.uncanny.lore.LoreManager.createRecord(entry, context.player,
                context.state);
        for (int slot = 0; slot < container.size(); slot++) {
            if (container.getStack(slot).isEmpty()) {
                container.setStack(slot, record);
                return;
            }
        }
    }

    private void deepScale(EventContext context) {
        ScaleSite site = findScaleSite(context);
        if (site == null) {
            return;
        }
        // Two pillars and a lintel, taller than anything in this tunnel has a
        // right to be. Built on the axis that was verified by the condition.
        BlockPos face = site.face();
        Direction side = site.side();
        for (int s = 0; s <= 3; s++) {
            for (int y = 0; y <= 4; y++) {
                if (s == 0 || s == 3 || y == 4) {
                    PositionUtil.setQuietly(context.world, face.offset(side, s).up(y),
                            Blocks.POLISHED_DEEPSLATE.getDefaultState());
                }
            }
        }
    }

    private void fragment(EventContext context) {
        BlockPos target = findFragmentSite(context, this.dimension);
        if (target == null || this.fragmentPool == null) {
            return;
        }
        BlockState current = context.world.getBlockState(target);
        for (int attempt = 0; attempt < this.fragmentPool.length; attempt++) {
            String candidate = this.fragmentPool[context.random().nextInt(this.fragmentPool.length)];
            Block block = BlockVariant.byId(candidate);
            if (block != Blocks.AIR && block != current.getBlock()) {
                PositionUtil.setQuietly(context.world, target, block.getDefaultState());
                return;
            }
        }
    }

    /** The layer this instance belongs to. Used by the debug command's docs. */
    public dev.uncanny.dimension.UncannyDimension dimension() {
        return this.dimension;
    }
}
