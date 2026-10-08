package dev.uncanny.item;

import dev.uncanny.Uncanny;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Every item the mod adds.
 *
 *   SURVEY_RECORD      a document you can hold and read
 *   LEDGER_PAGE        the same thing, but the page is about you
 *   SURVEYORS_COMPASS  points at boundaries, until it stops
 *   plus a BlockItem for each block, so they can be given and placed
 *
 * None of these have recipes. They are found, never crafted, and the mod never
 * suggests otherwise.
 */
public final class UncannyItems {

    public static final Item SURVEY_RECORD = register("survey_record",
            new SurveyRecordItem(new FabricItemSettings().maxCount(1)));

    public static final Item LEDGER_PAGE = register("ledger_page",
            new SurveyRecordItem(new FabricItemSettings().maxCount(1).fireproof()));

    public static final Item SURVEYORS_COMPASS = register("surveyors_compass",
            new SurveyorsCompassItem(new FabricItemSettings().maxCount(1)));

    public static final Item STARLIGHT = registerBlockItem("starlight", UncannyBlocks.STARLIGHT);
    public static final Item SEAL_PLATE = registerBlockItem("seal_plate", UncannyBlocks.SEAL_PLATE);
    public static final Item LEDGER = registerBlockItem("ledger", UncannyBlocks.LEDGER);
    public static final Item EYE_VENT = registerBlockItem("eye_vent", UncannyBlocks.EYE_VENT);
    public static final Item OBSERVATION_GLASS = registerBlockItem("observation_glass",
            UncannyBlocks.OBSERVATION_GLASS);
    public static final Item SURVEY_MARKER = registerBlockItem("survey_marker", UncannyBlocks.SURVEY_MARKER);

    private UncannyItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Uncanny.id(name), item);
    }

    private static Item registerBlockItem(String name, net.minecraft.block.Block block) {
        return Registry.register(Registries.ITEM, Uncanny.id(name),
                new BlockItem(block, new FabricItemSettings()));
    }

    /** Called once from the mod initialiser, after blocks. */
    public static void register() {
        // Field initialisers do the work; this exists so the order is explicit.
    }
}
