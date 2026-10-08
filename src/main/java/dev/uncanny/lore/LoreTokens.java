package dev.uncanny.lore;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Locale;

/**
 * Substitution for lore text.
 *
 * Documents are written with placeholders so the same JSON can be personal
 * without any of it being hardcoded. The set is deliberately small: a name, an
 * entry number, a day count, a coordinate. Anything more and the documents stop
 * feeling like records and start feeling like a status page.
 *
 *   {PLAYER}  the player's Minecraft username
 *   {ENTRY}   the player's anchor number, three digits
 *   {ANCHOR}  "ANCHOR 741"
 *   {DAY}     how many in-game days the world has run
 *   {SEALS}   how many seals have given way
 *   {WORLD}   a short hash of the world, so documents can cite "this instance"
 */
public final class LoreTokens {

    private LoreTokens() {
    }

    public static String expand(String text, ServerPlayerEntity player, UncannyWorldState state) {
        if (text.indexOf('{') < 0) {
            return text;
        }
        UncannyPlayerData data = state.player(player.getUuid());
        long days = player.getWorld().getTimeOfDay() / 24000L;
        String worldTag = Integer.toHexString((int) (player.getWorld().getSeed() & 0xFFFFL)).toUpperCase(Locale.ROOT);

        return text
                .replace("{PLAYER}", player.getName().getString())
                .replace("{ENTRY}", data.entryLabel())
                .replace("{ANCHOR}", data.anchorLabel())
                .replace("{DAY}", Long.toString(days))
                .replace("{SEALS}", Integer.toString(state.openedSealCount()))
                .replace("{WORLD}", worldTag);
    }

    public static List<String> expandAll(List<String> pages, ServerPlayerEntity player, UncannyWorldState state) {
        return pages.stream().map(page -> expand(page, player, state)).toList();
    }
}
