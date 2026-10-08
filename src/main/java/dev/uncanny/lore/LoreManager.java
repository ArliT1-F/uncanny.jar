package dev.uncanny.lore;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.uncanny.Uncanny;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.generation.GenerationContext;
import dev.uncanny.item.UncannyItems;
import dev.uncanny.nbt.UncannyNbtKeys;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every document the mod can hand out, and the rules for handing them out.
 *
 * Entries live in src/main/resources/data/uncanny/lore/*.json. One file per
 * document, or several documents in one file under a "records" array - whichever
 * is easier to maintain. Editing them needs no Java at all.
 *
 * Two kinds of record exist:
 *   - JSON entries, for the writing that has to be exactly right;
 *   - generated records from {@link SurveyorRecord}, for the volume filler that
 *     makes the Archive feel like an archive.
 */
public final class LoreManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/lore");
    private static final Gson GSON = new Gson();

    private static final Map<String, LoreEntry> ENTRIES = new LinkedHashMap<>();

    private LoreManager() {
    }

    // -------------------------------------------------------------- loading

    /** Reads every lore JSON out of the mod jar (or out of the dev resources). */
    public static void load() {
        ENTRIES.clear();
        var container = FabricLoader.getInstance().getModContainer(Uncanny.MOD_ID);
        if (container.isEmpty()) {
            LOGGER.warn("[uncanny] mod container missing, no lore loaded");
            return;
        }
        var root = container.get().findPath("data/" + Uncanny.MOD_ID + "/lore");
        if (root.isEmpty()) {
            LOGGER.warn("[uncanny] no lore directory found");
            return;
        }
        try (Stream<Path> files = Files.walk(root.get())) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(LoreManager::loadFile);
        } catch (IOException e) {
            LOGGER.error("[uncanny] could not walk lore directory", e);
        }
        LOGGER.info("[uncanny] loaded {} lore documents", ENTRIES.size());
    }

    private static void loadFile(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null) {
                return;
            }
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    readEntry(element.getAsJsonObject());
                }
            } else if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                if (object.has("records")) {
                    JsonArray records = object.getAsJsonArray("records");
                    for (JsonElement element : records) {
                        readEntry(element.getAsJsonObject());
                    }
                } else {
                    readEntry(object);
                }
            }
        } catch (IOException | RuntimeException e) {
            // One bad file must not cost the player the whole archive.
            LOGGER.error("[uncanny] bad lore file {}", path, e);
        }
    }

    private static void readEntry(JsonObject object) {
        String id = stringOr(object, "id", null);
        if (id == null) {
            return;
        }
        String title = stringOr(object, "title", "UNTITLED");
        int stage = object.has("stage") ? object.get("stage").getAsInt() : 1;
        double weight = object.has("weight") ? object.get("weight").getAsDouble() : 1.0;
        boolean unique = object.has("unique") && object.get("unique").getAsBoolean();
        LoreEntry.Category category = LoreEntry.Category.SURVEY;
        if (object.has("category")) {
            try {
                category = LoreEntry.Category.valueOf(object.get("category").getAsString().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Unknown category falls back to SURVEY.
            }
        }
        List<String> pages = new ArrayList<>();
        if (object.has("pages")) {
            for (JsonElement page : object.getAsJsonArray("pages")) {
                pages.add(page.getAsString());
            }
        }
        ENTRIES.put(id, new LoreEntry(id, title, pages, category, stage, weight, unique));
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    // ------------------------------------------------------------ selection

    public static LoreEntry byId(String id) {
        return ENTRIES.get(id);
    }

    public static int size() {
        return ENTRIES.size();
    }

    /**
     * Picks a document for a reader.
     *
     * Documents the player has already read are skipped, and documents that are
     * too advanced for the player's current stage are skipped. If nothing is left
     * the method returns a generated Surveyor record instead, so a chest is never
     * embarrassingly empty in a place the player was told to look.
     */
    public static LoreEntry pickFor(ServerPlayerEntity player, UncannyWorldState state, long seed) {
        LoreStage stage = PlayerProgress.stage(state.player(player.getUuid()), state);
        UncannyPlayerData data = state.player(player.getUuid());
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);

        List<LoreEntry> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (LoreEntry entry : ENTRIES.values()) {
            if (!entry.availableAt(stage.level())) {
                continue;
            }
            if (data.knowsLore(entry.id) || state.knowsWorldLore(entry.id)) {
                continue;
            }
            candidates.add(entry);
            weights.add(entry.weight);
        }
        if (candidates.isEmpty()) {
            return SurveyorRecord.compose(seed, Math.max(1, stage.level() - 1));
        }
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    // --------------------------------------------------------------- items

    /** Builds the written record a player can hold and read. */
    public static ItemStack createRecord(LoreEntry entry, ServerPlayerEntity player, UncannyWorldState state) {
        ItemStack stack = new ItemStack(UncannyItems.SURVEY_RECORD);
        var nbt = stack.getOrCreateNbt();
        nbt.putString(UncannyNbtKeys.TITLE, LoreTokens.expand(entry.title, player, state));
        nbt.putString(UncannyNbtKeys.LORE_ID, entry.id);
        NbtList pages = new NbtList();
        for (String page : LoreTokens.expandAll(entry.pages, player, state)) {
            pages.add(NbtString.of(page));
        }
        nbt.put(UncannyNbtKeys.PAGES, pages);
        return stack;
    }

    /**
     * Puts a record into a generated container.
     *
     * Called during generation, when there is no player to personalise the text
     * for yet, so the pages are expanded when the player opens it instead. That is
     * what lets one chest hold a document that names whoever finally opens it.
     */
    public static void placeInChest(GenerationContext context, int x, int y, int z, int stage, long seed) {
        LoreEntry entry = pickByStage(stage, seed);
        ItemStack stack = new ItemStack(UncannyItems.SURVEY_RECORD);
        var nbt = stack.getOrCreateNbt();
        nbt.putString(UncannyNbtKeys.TITLE, entry.title);
        nbt.putString(UncannyNbtKeys.LORE_ID, entry.id);
        NbtList pages = new NbtList();
        for (String page : entry.pages) {
            pages.add(NbtString.of(page));
        }
        nbt.put(UncannyNbtKeys.PAGES, pages);
        context.chestWith(x, y, z, Direction.NORTH, stack);
    }

    /**
     * The same thing, without a generation context.
     *
     * Used by the carved layers, which write blocks directly into the world rather
     * than going through a room builder.
     */
    public static void placeChest(net.minecraft.server.world.ServerWorld world,
                                  net.minecraft.util.math.BlockPos pos, int stage, long seed) {
        world.setBlockState(pos, net.minecraft.block.Blocks.CHEST.getDefaultState(), 3);
        LoreEntry entry = pickByStage(stage, seed);
        ItemStack stack = new ItemStack(UncannyItems.SURVEY_RECORD);
        var nbt = stack.getOrCreateNbt();
        nbt.putString(UncannyNbtKeys.TITLE, entry.title);
        nbt.putString(UncannyNbtKeys.LORE_ID, entry.id);
        NbtList pages = new NbtList();
        for (String page : entry.pages) {
            pages.add(NbtString.of(page));
        }
        nbt.put(UncannyNbtKeys.PAGES, pages);
        if (world.getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
            chest.setStack(13, stack);
        }
    }

    private static LoreEntry pickByStage(int stage, long seed) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "lore"));
        List<LoreEntry> candidates = new ArrayList<>();
        for (LoreEntry entry : ENTRIES.values()) {
            if (entry.minStage <= stage && entry.minStage >= stage - 1) {
                candidates.add(entry);
            }
        }
        if (candidates.isEmpty()) {
            return SurveyorRecord.compose(seed, stage);
        }
        return random.pick(candidates);
    }

    // ---------------------------------------------------------- discovery

    /** Records that a player has read something. Called when a record is opened. */
    public static void discover(ServerPlayerEntity player, UncannyWorldState state, String loreId) {
        if (loreId == null || loreId.isEmpty() || loreId.startsWith("generated:")) {
            return;
        }
        UncannyPlayerData data = state.player(player.getUuid());
        boolean fresh = !data.knowsLore(loreId);
        data.markLore(loreId);
        state.markWorldLore(loreId);
        if (fresh) {
            // Reading a document is a discovery, and discoveries move the stage.
            // They also thin the player's reality, very slowly.
            dev.uncanny.player.RealityInstability.raise(data, 0.004);
            state.markDirty();
        }
    }
}
