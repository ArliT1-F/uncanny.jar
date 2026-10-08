package dev.uncanny.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Text formatting for the mod's writing style.
 *
 * The house style for records and ledger pages is spaced-out capital letters on
 * a dark page. Doing that in one helper keeps every book in the mod looking like
 * it came from the same archive.
 */
public final class TextUtil {

    private TextUtil() {
    }

    /** "SUBJECT ENTERED" -> "S U B J E C T   E N T E R E D". */
    public static String spaced(String value) {
        StringBuilder out = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i > 0) {
                // Double space after a word break reads better than a single one.
                out.append(c == ' ' ? "   " : " ");
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    /** Plain grey text, the default for record bodies. */
    public static MutableText record(String value) {
        return Text.literal(value).setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY));
    }

    /** A heading line: spaced capitals, slightly brighter. */
    public static MutableText heading(String value) {
        return Text.literal(spaced(value)).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
    }

    /** A line that the player is not meant to be able to explain. */
    public static MutableText wrong(String value) {
        return Text.literal(value).setStyle(Style.EMPTY.withColor(Formatting.DARK_PURPLE));
    }

    /** A crossed-out correction, used by contradictory Surveyor documents. */
    public static MutableText struck(String value) {
        return Text.literal(value).setStyle(Style.EMPTY
                .withColor(Formatting.DARK_GRAY)
                .withStrikethrough(true));
    }

    /** A quiet, small note, used for margin annotations. */
    public static MutableText margin(String value) {
        return Text.literal(value).setStyle(Style.EMPTY
                .withColor(Formatting.GRAY)
                .withItalic(true));
    }

    /** Builds a page from a list of strings, one Text per line. */
    public static List<Text> page(String... lines) {
        List<Text> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(record(line));
        }
        return out;
    }

    /** Wraps a long string into lines no longer than width, on word boundaries. */
    public static List<String> wrap(String value, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
