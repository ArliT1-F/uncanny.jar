package dev.uncanny.net;

import dev.uncanny.Uncanny;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.item.UncannyItems;
import dev.uncanny.lore.LedgerManager;
import dev.uncanny.lore.LoreManager;
import dev.uncanny.nbt.UncannyNbtKeys;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client messages.
 *
 * There are only two, and both are display-only:
 *
 *   OPEN_BOOK    "show these pages" - used by records and by the Ledger
 *   VISUAL       "do this small visual thing" - a flicker, a pulse, a dimming
 *
 * Nothing about progression travels to the client. The client is a display, and a
 * client that lies about what it has seen cannot change what the server remembers.
 */
public final class UncannyPayloads {

    public static final Identifier OPEN_BOOK = Uncanny.id("open_book");
    public static final Identifier VISUAL = Uncanny.id("visual");

    /** Visual effect ids, shared with the client by number. */
    public static final int VISUAL_FLICKER = 1;
    public static final int VISUAL_PULSE = 2;
    public static final int VISUAL_DIM = 3;
    public static final int VISUAL_SKY = 4;
    public static final int VISUAL_STATIC = 5;

    private UncannyPayloads() {
    }

    /** Sends a record's pages to the player who is holding it. */
    public static void sendRecord(ServerPlayerEntity player, ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return;
        }
        String title = nbt.getString(UncannyNbtKeys.TITLE);
        List<List<String>> pages = new ArrayList<>();
        NbtList raw = nbt.getList(UncannyNbtKeys.PAGES, NbtElement.STRING_TYPE);
        for (int i = 0; i < raw.size(); i++) {
            // A page may contain line breaks; the screen splits them out.
            pages.add(List.of(raw.getString(i).split("\n", -1)));
        }
        if (pages.isEmpty()) {
            pages.add(List.of(""));
        }
        writeBook(player, title, pages);
    }

    /** Sends the Ledger, generated for this player at this moment. */
    public static void sendLedger(ServerPlayerEntity player, UncannyWorldState state, boolean finalSequence) {
        UncannyPlayerData data = state.player(player.getUuid());
        List<List<String>> pages = finalSequence
                ? LedgerManager.finalPages(data, state)
                : LedgerManager.pages(data, state);
        writeBook(player, "THE LEDGER", pages);
    }

    /** Sends a Ledger page as an item, for the late game. */
    public static ItemStack ledgerPage(ServerPlayerEntity player, UncannyWorldState state) {
        UncannyPlayerData data = state.player(player.getUuid());
        List<List<String>> pages = LedgerManager.pages(data, state);
        ItemStack stack = new ItemStack(UncannyItems.LEDGER_PAGE);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(UncannyNbtKeys.TITLE, "THE LEDGER");
        nbt.putString(UncannyNbtKeys.LORE_ID, "ledger:page");
        NbtList list = new NbtList();
        for (List<String> page : pages) {
            list.add(net.minecraft.nbt.NbtString.of(String.join("\n", page)));
        }
        nbt.put(UncannyNbtKeys.PAGES, list);
        return stack;
    }

    private static void writeBook(ServerPlayerEntity player, String title, List<List<String>> pages) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(title);
        buf.writeVarInt(pages.size());
        for (List<String> page : pages) {
            buf.writeVarInt(page.size());
            for (String line : page) {
                buf.writeString(line);
            }
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, OPEN_BOOK, buf);
    }

    /** Reads an OPEN_BOOK payload. Called on the client. */
    public static Book readBook(PacketByteBuf buf) {
        String title = buf.readString();
        int pageCount = buf.readVarInt();
        List<List<String>> pages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            int lineCount = buf.readVarInt();
            List<String> lines = new ArrayList<>(lineCount);
            for (int j = 0; j < lineCount; j++) {
                lines.add(buf.readString());
            }
            pages.add(lines);
        }
        return new Book(title, pages);
    }

    /** A book, as it arrives over the network. */
    public record Book(String title, List<List<String>> pages) {
    }

    /** Asks the client to do one small visual thing. */
    public static void sendVisual(ServerPlayerEntity player, int effect, float intensity) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeVarInt(effect);
        buf.writeFloat(intensity);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, VISUAL, buf);
    }

    /**
     * Records that a player has read something.
     *
     * This runs on the server, not the client: the item is used server-side, so
     * there is no need to round-trip a packet for it.
     */
    public static void noteRead(ServerPlayerEntity player, String loreId) {
        LoreManager.discover(player, UncannyWorldState.get(player.getServerWorld()), loreId);
    }
}
