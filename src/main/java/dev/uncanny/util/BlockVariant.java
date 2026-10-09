package dev.uncanny.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Block ids and their near-neighbours.
 *
 * "The tree came back wrong", "one slab where the step should continue", "the
 * wall is made of what you build with" - all of these need to turn one block
 * into a related block, and all of them must fail SAFE: if the guessed id does
 * not exist, nothing happens. Every lookup here goes through the vanilla
 * registry, so the answer is correct for whatever blocks the game (or a
 * datapack) actually has.
 */
public final class BlockVariant {

    private BlockVariant() {
    }

    /** Looks a block up by id string ("minecraft:oak_planks" or "oak_planks"). */
    public static Block byId(String id) {
        if (id == null || id.isEmpty()) {
            return Blocks.AIR;
        }
        Identifier identifier = id.contains(":")
                ? new Identifier(id)
                : new Identifier("minecraft", id);
        return Registries.BLOCK.get(identifier);
    }

    /** The id string of a block, as "namespace:path". */
    public static String idOf(Block block) {
        return Registries.BLOCK.getId(block).toString();
    }

    /** The vanilla id path of a block, e.g. "oak_planks". */
    public static String pathOf(Block block) {
        return Registries.BLOCK.getId(block).getPath();
    }

    /**
     * A different wood version of a log or plank block, or null if this is not a
     * wood block. Deterministic given the same input is NOT required here; callers
     * pass their own random in.
     */
    public static Block woodVariantOf(Block block, RandomUtil.UncannyRandom random) {
        String path = pathOf(block);
        // Longest names first: "dark_oak" must match before "oak".
        String[] woods = {"dark_oak", "mangrove", "oak", "spruce", "birch", "jungle", "acacia"};
        String current = null;
        for (String wood : woods) {
            if (path.contains(wood)) {
                current = wood;
                break;
            }
        }
        if (current == null) {
            return null;
        }
        for (int attempt = 0; attempt < woods.length; attempt++) {
            String candidate = woods[random.nextInt(woods.length)];
            if (candidate.equals(current)) {
                continue;
            }
            Block swapped = byId(path.replace(current, candidate));
            if (swapped != Blocks.AIR) {
                return swapped;
            }
        }
        return null;
    }

    /**
     * A wrong version of a wood block for a restoration that should be slightly
     * off ("oak_planks" -> "spruce_planks"), or null when no obvious sibling
     * exists, in which case the caller restores the exact block.
     */
    public static Block wrongWoodOf(Block block) {
        String path = pathOf(block);
        String[][] swaps = {
                {"dark_oak", "spruce"},
                {"oak", "birch"},
                {"spruce", "oak"},
                {"birch", "spruce"},
                {"jungle", "acacia"},
                {"acacia", "jungle"},
                {"mangrove", "dark_oak"},
        };
        for (String[] swap : swaps) {
            if (path.contains(swap[0])) {
                Block swapped = byId(path.replace(swap[0], swap[1]));
                if (swapped != Blocks.AIR) {
                    return swapped;
                }
            }
        }
        return null;
    }

    /**
     * The cracked/aged version of a patterned building block, or null.
     * stone_bricks -> cracked_stone_bricks, mossy_stone_bricks -> the cracked
     * form too, deepslate_tiles -> cracked_deepslate_tiles, and so on.
     */
    public static Block crackedOf(Block block) {
        String path = pathOf(block);
        String cracked;
        if (path.startsWith("mossy_")) {
            cracked = "cracked_" + path.substring("mossy_".length());
        } else if (path.startsWith("cracked_")) {
            return null; // already wrong
        } else {
            cracked = "cracked_" + path;
        }
        Block result = byId(cracked);
        return result == Blocks.AIR ? null : result;
    }

    /**
     * The slab that continues a staircase of this material, or null.
     * oak_stairs -> oak_slab.
     */
    public static Block slabFor(Block stairs) {
        String path = pathOf(stairs);
        if (!path.endsWith("_stairs")) {
            return null;
        }
        Block slab = byId(path.substring(0, path.length() - "_stairs".length()) + "_slab");
        return slab == Blocks.AIR ? null : slab;
    }
}
