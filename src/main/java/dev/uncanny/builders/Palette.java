package dev.uncanny.builders;

import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/**
 * What things are made of.
 *
 * Each layer has a base palette, and each chunk picks a variant of it. The
 * variant is what makes one corridor damp and the next one cold without any of it
 * being random at runtime: it is all decided by the chunk seed, so the damp
 * corridor is still damp next week.
 *
 * A small number of chunks pick the RED variant. There is no reason given, in
 * game or here.
 */
public final class Palette {

    /** A chunk's flavour. Chosen deterministically, weighted towards NORMAL. */
    public enum Variant {
        NORMAL, DAMP, COLD, RED, DARK, MOSSY
    }

    /** A resolved set of blocks for one room. */
    public record Scheme(BlockState wall, BlockState floor, BlockState accent, BlockState light,
                         BlockState debris, Variant variant) {
    }

    private Palette() {
    }

    public static Scheme of(UncannyDimension dimension, long seed) {
        Variant variant = variantFor(seed);
        return switch (dimension) {
            case HALL -> hall(seed, variant);
            case ARCHIVE -> archive(seed, variant);
            case HOUSE -> house(seed, variant);
            case COPY -> copy(seed, variant);
            case WOODS -> woods(seed, variant);
            case DEEP -> deep(seed, variant);
            case ABYSS -> abyss(seed, variant);
            case PARTITION -> partition(seed, variant);
            default -> hall(seed, Variant.NORMAL);
        };
    }

    /** The variant roll. 78% of chunks look completely ordinary. */
    public static Variant variantFor(long seed) {
        double roll = ((SeedUtil.derive(seed, "variant") >>> 11) % 1000) / 1000.0;
        if (roll < 0.78) {
            return Variant.NORMAL;
        }
        if (roll < 0.86) {
            return Variant.DAMP;
        }
        if (roll < 0.92) {
            return Variant.COLD;
        }
        if (roll < 0.96) {
            return Variant.MOSSY;
        }
        if (roll < 0.99) {
            return Variant.DARK;
        }
        return Variant.RED;
    }

    // ------------------------------------------------------------ per layer

    private static Scheme hall(long seed, Variant variant) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "hall"));
        return switch (variant) {
            case DAMP -> new Scheme(Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
                    Blocks.STONE_BRICKS.getDefaultState(), Blocks.POLISHED_ANDESITE.getDefaultState(),
                    Blocks.SOUL_LANTERN.getDefaultState(), Blocks.GRAVEL.getDefaultState(), variant);
            case COLD -> new Scheme(Blocks.PACKED_ICE.getDefaultState(),
                    Blocks.SNOW_BLOCK.getDefaultState(), Blocks.BLUE_ICE.getDefaultState(),
                    Blocks.SEA_LANTERN.getDefaultState(), Blocks.SNOW.getDefaultState(), variant);
            case RED -> new Scheme(Blocks.RED_TERRACOTTA.getDefaultState(),
                    Blocks.RED_CONCRETE.getDefaultState(), Blocks.RED_NETHER_BRICKS.getDefaultState(),
                    Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.block.RedstoneLampBlock.LIT, true),
                    Blocks.RED_CARPET.getDefaultState(), variant);
            case DARK -> new Scheme(Blocks.POLISHED_BLACKSTONE.getDefaultState(),
                    Blocks.BLACKSTONE.getDefaultState(), Blocks.GILDED_BLACKSTONE.getDefaultState(),
                    Blocks.SOUL_TORCH.getDefaultState(), Blocks.BLACK_CARPET.getDefaultState(), variant);
            case MOSSY -> new Scheme(Blocks.MOSSY_COBBLESTONE.getDefaultState(),
                    Blocks.MOSS_BLOCK.getDefaultState(), Blocks.ROOTED_DIRT.getDefaultState(),
                    Blocks.LANTERN.getDefaultState(), Blocks.MOSS_CARPET.getDefaultState(), variant);
            case NORMAL -> new Scheme(random.nextBoolean() ? Blocks.STONE_BRICKS.getDefaultState()
                            : Blocks.DEEPSLATE_BRICKS.getDefaultState(),
                    Blocks.POLISHED_ANDESITE.getDefaultState(), Blocks.SMOOTH_STONE.getDefaultState(),
                    Blocks.LANTERN.getDefaultState(), Blocks.GRAVEL.getDefaultState(), variant);
        };
    }

    private static Scheme archive(long seed, Variant variant) {
        return new Scheme(Blocks.DARK_OAK_PLANKS.getDefaultState(),
                Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState(), Blocks.DARK_OAK_SLAB.getDefaultState(),
                Blocks.LANTERN.getDefaultState(), Blocks.BOOKSHELF.getDefaultState(), variant);
    }

    private static Scheme house(long seed, Variant variant) {
        return new Scheme(Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.OAK_LOG.getDefaultState(), Blocks.OAK_SLAB.getDefaultState(),
                Blocks.TORCH.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), variant);
    }

    private static Scheme copy(long seed, Variant variant) {
        return new Scheme(Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.GRASS_BLOCK.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(),
                Blocks.TORCH.getDefaultState(), Blocks.DIRT.getDefaultState(), variant);
    }

    private static Scheme woods(long seed, Variant variant) {
        return new Scheme(Blocks.OAK_LOG.getDefaultState(),
                Blocks.GRASS_BLOCK.getDefaultState(), Blocks.OAK_LEAVES.getDefaultState(),
                Blocks.TORCH.getDefaultState(), Blocks.PODZOL.getDefaultState(), variant);
    }

    private static Scheme deep(long seed, Variant variant) {
        return switch (variant) {
            case DAMP -> new Scheme(Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.MUD.getDefaultState(),
                    Blocks.DRIPSTONE_BLOCK.getDefaultState(), Blocks.SOUL_TORCH.getDefaultState(),
                    Blocks.GRAVEL.getDefaultState(), variant);
            default -> new Scheme(Blocks.DEEPSLATE.getDefaultState(), Blocks.DEEPSLATE.getDefaultState(),
                    Blocks.TUFF.getDefaultState(), Blocks.TORCH.getDefaultState(),
                    Blocks.GRAVEL.getDefaultState(), variant);
        };
    }

    private static Scheme abyss(long seed, Variant variant) {
        return new Scheme(Blocks.BEDROCK.getDefaultState(), Blocks.BEDROCK.getDefaultState(),
                Blocks.CRYING_OBSIDIAN.getDefaultState(), Blocks.SOUL_LANTERN.getDefaultState(),
                Blocks.AIR.getDefaultState(), variant);
    }

    private static Scheme partition(long seed, Variant variant) {
        // The Partition is built out of pieces of everywhere, so its palette is
        // deliberately unstable: it borrows from other layers.
        return new Scheme(Blocks.SMOOTH_QUARTZ.getDefaultState(),
                Blocks.QUARTZ_BLOCK.getDefaultState(), Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),
                Blocks.SEA_LANTERN.getDefaultState(), Blocks.SMOOTH_STONE.getDefaultState(), variant);
    }

    /** A block for "someone lived here": a bed, a table, a shelf. */
    public static BlockState furniture(long seed) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "furniture"));
        return random.pick(java.util.List.of(
                Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.SPRUCE_PLANKS.getDefaultState(),
                Blocks.OAK_STAIRS.getDefaultState(),
                Blocks.CRAFTING_TABLE.getDefaultState(),
                Blocks.BARREL.getDefaultState()));
    }
}
