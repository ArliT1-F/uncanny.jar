package dev.uncanny.nbt;

/**
 * Every NBT key the mod uses, in one place.
 *
 * Item and block-entity tags are strings. Typo one of them in two different files
 * and the bug is invisible until a player loses a book, so they all live here.
 */
public final class UncannyNbtKeys {

    public static final String TITLE = "uncanny_title";
    public static final String PAGES = "uncanny_pages";
    public static final String LORE_ID = "uncanny_lore_id";
    public static final String LEDGER_STATE = "uncanny_ledger_state";
    public static final String ANCHOR_NUMBER = "uncanny_anchor";
    public static final String ANCHOR_STATUS = "uncanny_anchor_status";
    public static final String SEALS = "uncanny_seals";
    public static final String EYE_SEED = "uncanny_eye_seed";
    public static final String SUBJECT = "uncanny_subject";

    private UncannyNbtKeys() {
    }
}
