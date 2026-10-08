package dev.uncanny.util;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;

/**
 * Sign writing for Minecraft 1.20+.
 *
 * 1.19 had {@code SignBlockEntity.setTextOnRow}. 1.20 split signs into front and
 * back faces, so the text lives on {@link SignText} instead.
 */
public final class SignUtil {

    private SignUtil() {
    }

    /** Writes up to four lines on the front of a sign. Missing lines become blank. */
    public static void setLines(SignBlockEntity sign, String... lines) {
        SignText text = sign.getText(true);
        for (int row = 0; row < 4; row++) {
            String line = row < lines.length && lines[row] != null ? lines[row] : "";
            text = text.withMessage(row, Text.literal(line));
        }
        sign.setText(text, true);
    }
}
