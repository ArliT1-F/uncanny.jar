package dev.uncanny.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * A point of light.
 *
 * Used for the stars in the Prison of Stars and for the lights in a star room.
 * It has no collision and no model - it is a light with a position, which is
 * exactly what a distant reality should look like from here.
 *
 * The level property lets the server change how bright a star is without moving
 * it, which is how "one of them is getting closer" is expressed.
 */
public class StarlightBlock extends Block {

    /** Brightness, 0..15. Also used as a proxy for distance. */
    public static final IntProperty LEVEL = IntProperty.of("level", 0, 15);

    public StarlightBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(LEVEL, 8));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // No outline: you cannot select it, and you cannot lean on it.
        return VoxelShapes.empty();
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }
}
