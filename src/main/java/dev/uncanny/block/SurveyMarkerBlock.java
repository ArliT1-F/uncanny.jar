package dev.uncanny.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * An invisible marker.
 *
 * Placed where the mod has decided a doorway is. The block itself does nothing;
 * the transition manager reads the position from world state. It exists so that
 * the spot is occupied - if the player fills it with something else, the doorway
 * stops working, which is the correct behaviour for a door.
 */
public class SurveyMarkerBlock extends Block {

    public SurveyMarkerBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }
}
