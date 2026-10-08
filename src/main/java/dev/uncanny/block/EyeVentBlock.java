package dev.uncanny.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * A small hole in a wall.
 *
 * On its own this is a decorative block. It becomes part of the horror only in
 * quantity, and only when the player notices that the arrangement of holes in a
 * corridor matches the arrangement in a chamber they found an hour ago.
 *
 * The mod never says the word "eye" in game. Not once.
 */
public class EyeVentBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 3, 3, 13, 13, 13);

    public EyeVentBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
