package dev.uncanny.item;

import dev.uncanny.Uncanny;
import dev.uncanny.block.EyeVentBlock;
import dev.uncanny.block.LedgerBlock;
import dev.uncanny.block.LedgerBlockEntity;
import dev.uncanny.block.SealPlateBlock;
import dev.uncanny.block.StarlightBlock;
import dev.uncanny.block.SurveyMarkerBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.GlassBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.property.IntProperty;

/**
 * Every block the mod adds.
 *
 * There are only six, and each one exists because something in the design needs a
 * physical object:
 *
 *   STARLIGHT          a point of light with no body. The stars.
 *   SEAL_PLATE         shows how many boundaries have given way.
 *   LEDGER             the book. Refuses to open until it should not.
 *   EYE_VENT           a small hole in a wall.
 *   OBSERVATION_GLASS  a window that looks into nothing.
 *   SURVEY_MARKER      an invisible marker where a doorway has been placed.
 *
 * No horror mobs, no weapons, no armour. That is deliberate.
 */
public final class UncannyBlocks {

    /** Exposed so builders can write `UncannyBlocks.LEVEL` without a cast. */
    public static final IntProperty LEVEL = StarlightBlock.LEVEL;
    public static final IntProperty OPENED = SealPlateBlock.OPENED;

    public static final Block STARLIGHT = register("starlight", new StarlightBlock(
            FabricBlockSettings.create().mapColor(MapColor.CLEAR)
                    .noCollision()
                    .nonOpaque()
                    .luminance((BlockState state) -> state.get(StarlightBlock.LEVEL))
                    .sounds(BlockSoundGroup.GLASS)));

    public static final Block SEAL_PLATE = register("seal_plate", new SealPlateBlock(
            FabricBlockSettings.create().mapColor(MapColor.STONE_GRAY)
                    .requiresTool()
                    .strength(3.0F, 9.0F)
                    .luminance((BlockState state) -> state.get(SealPlateBlock.OPENED) > 4 ? 4 : 0)
                    .sounds(BlockSoundGroup.DEEPSLATE)));

    public static final Block LEDGER = register("ledger", new LedgerBlock(
            FabricBlockSettings.create().mapColor(MapColor.OAK_TAN)
                    .strength(-1.0F, 3600000.0F)
                    .dropsNothing()
                    .sounds(BlockSoundGroup.WOOD)));

    public static final Block EYE_VENT = register("eye_vent", new EyeVentBlock(
            FabricBlockSettings.create().mapColor(MapColor.STONE_GRAY)
                    .strength(1.5F, 6.0F)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.STONE)));

    public static final Block OBSERVATION_GLASS = register("observation_glass", new GlassBlock(
            FabricBlockSettings.copyOf(Blocks.BLACK_STAINED_GLASS)
                    .strength(0.3F)
                    .sounds(BlockSoundGroup.GLASS)));

    public static final Block SURVEY_MARKER = register("survey_marker", new SurveyMarkerBlock(
            FabricBlockSettings.create().mapColor(MapColor.CLEAR)
                    .noCollision()
                    .nonOpaque()
                    .dropsNothing()
                    .sounds(BlockSoundGroup.STONE)));

    /** Block entities are registered after their blocks. */
    public static BlockEntityType<LedgerBlockEntity> LEDGER_ENTITY_TYPE;

    private UncannyBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, Uncanny.id(name), block);
    }

    /** Called once from the mod initialiser, before items. */
    public static void register() {
        // Touching the static fields above is what actually triggers registration;
        // this call is here so the ordering is explicit and readable.
        LEDGER_ENTITY_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Uncanny.id("ledger"),
                FabricBlockEntityTypeBuilder.create(LedgerBlockEntity::new, LEDGER).build());
    }
}
