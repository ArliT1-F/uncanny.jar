package dev.uncanny.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything a player or pack author might want to change, in one JSON file.
 *
 * The file is written to <instance>/config/uncanny.json the first time the game
 * starts. Any key you leave out simply keeps the default below, so you can delete
 * keys you do not care about.
 *
 * Written to be edited by someone who is happier in Python than in Java: flat
 * keys, plain numbers, no nesting deeper than one level.
 */
public final class UncannyConfig {

    // ---------------------------------------------------------------- master

    /** Turns the entire mod off. The dimensions still exist but nothing happens. */
    public boolean enabled = true;

    /** Prints what the event director is considering. Useful, but very chatty. */
    public boolean debugLogging = false;

    // ------------------------------------------------------------- pacing

    /** How often (in ticks) the event director looks for something to do. 20 = 1s. */
    public int eventCheckIntervalTicks = 40;

    /** Shortest possible gap between two anomalies for one player, in ticks. */
    public int anomalyCooldownTicks = 20 * 60 * 3;

    /** Shortest possible gap between two MAJOR anomalies, in ticks. */
    public int majorCooldownTicks = 20 * 60 * 25;

    /** Multiplies every anomaly probability. 0.5 is calmer, 2.0 is much busier. */
    public double anomalyFrequency = 1.0;

    /** Minutes of play before the first anomaly of any kind can happen. */
    public int gracePeriodMinutes = 12;

    /** Chance per check that ambience goes completely silent instead of playing. */
    public double silenceChance = 0.04;

    // ------------------------------------------------------------ lore

    /** Starting lore stage (1..9). Raising it skips the slow burn. */
    public int startingStage = 1;

    /** How many discoveries advance a stage. Lower = faster revelation. */
    public int discoveriesPerStage = 6;

    /** Chance a generated container holds a Surveyor record at all. */
    public double loreContainerChance = 0.05;

    // --------------------------------------------------------- generation

    /** Room template weights, in percent. They do not have to sum to exactly 100. */
    public Map<String, Double> roomWeights = defaultRoomWeights();

    /** Chance that a chunk which may hold an anomaly actually holds one. */
    public double anomalyRoomChance = 0.01;

    /** Chance that a chunk which may hold an impossible room actually holds one. */
    public double impossibleRoomChance = 0.02;

    /** How often a landmark (anchor room, archive hall, ring chamber) appears. */
    public double landmarkChance = 0.035;

    // --------------------------------------------------------- transitions

    public boolean allowSleepTransitions = true;
    public boolean allowDoorTransitions = true;
    public boolean allowWaterTransitions = true;
    public boolean allowDeepFallTransitions = true;

    // -------------------------------------------------------- multiplayer

    /** When false, anomalies are world-wide instead of per player. */
    public boolean perPlayerAnomalies = true;

    /** How far apart two players must be before they can share an anomaly. */
    public int playerAnomalySpacing = 64;

    // ------------------------------------------------------------- audio

    public boolean audioEnabled = true;
    /** Master volume for mod sounds. 0.0 disables audio without disabling events. */
    public double audioVolume = 0.8;

    private static Map<String, Double> defaultRoomWeights() {
        // Starting values from the design document. Rename or remove freely;
        // any template not listed here falls back to its own built-in weight.
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("hallway_straight", 35.0);
        weights.put("hallway_corner", 20.0);
        weights.put("hallway_t", 9.0);
        weights.put("hallway_cross", 6.0);
        weights.put("room_small", 8.0);
        weights.put("room_large", 10.0);
        weights.put("dead_end", 8.0);
        weights.put("staircase", 5.0);
        weights.put("flooded_room", 4.0);
        weights.put("survey_room", 3.0);
        weights.put("strange_room", 4.0);
        weights.put("impossible_room", 2.0);
        weights.put("anomaly_room", 1.0);
        weights.put("eyes_room", 2.0);
        weights.put("archive_room", 3.0);
        weights.put("anchor_room", 2.0);
        weights.put("star_room", 1.0);
        return weights;
    }

    // ------------------------------------------------------------- loading

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static UncannyConfig instance;

    /** Returns the loaded config, loading it from disk the first time. */
    public static UncannyConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("uncanny.json");
    }

    private static UncannyConfig load() {
        Path path = configFile();
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path);
                UncannyConfig loaded = GSON.fromJson(json, UncannyConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException e) {
            // A broken config must never stop the game from starting.
            System.err.println("[uncanny] could not read config/uncanny.json, using defaults: " + e);
        }
        UncannyConfig fresh = new UncannyConfig();
        fresh.save();
        return fresh;
    }

    /** Writes the config back out (used to create it on first launch). */
    public void save() {
        try {
            Files.createDirectories(configFile().getParent());
            Files.writeString(configFile(), GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[uncanny] could not write config/uncanny.json: " + e);
        }
    }

    /** Looks up a template weight, falling back to the template's own default. */
    public double weightFor(String templateId, double fallback) {
        Double value = this.roomWeights.get(templateId);
        return value == null ? fallback : value;
    }
}
