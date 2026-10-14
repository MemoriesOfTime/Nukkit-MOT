package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerEditBookEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBookAndQuill;
import cn.nukkit.item.ItemBookWritten;
import cn.nukkit.network.protocol.BookEditPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.stream.Stream;

import static cn.nukkit.network.protocol.BookEditPacket.Action.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookEditPageIndexTest {
    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values()).filter(v -> !v.isNetEase())
                .filter(v -> v.getProtocol() >= ProtocolInfo.v1_20_0
                        && v.getProtocol() <= ProtocolInfo.v1_26_50);
    }

    static Stream<Arguments> malformedPages() {
        return versions().flatMap(v -> Arrays.stream(v.getProtocol() >= ProtocolInfo.v1_26_0
                ? new int[]{-1, 50, 255, Integer.MIN_VALUE, Integer.MAX_VALUE}
                : new int[]{50, 255}).boxed().flatMap(page -> Stream.of(
                Arguments.of(v, REPLACE_PAGE, page, 0), Arguments.of(v, ADD_PAGE, page, 0),
                Arguments.of(v, DELETE_PAGE, page, 0), Arguments.of(v, SWAP_PAGES, page, 0),
                Arguments.of(v, SWAP_PAGES, 0, page))));
    }

    @ParameterizedTest(name = "{0} {1} page={2} second={3}")
    @MethodSource("malformedPages")
    void malformedWireIndexIsIgnoredWithoutEventMutationOrException(GameVersion version,
            BookEditPacket.Action action, int page, int second) {
        Fixture f = new Fixture();
        byte[] before = f.book.getCompoundTag().clone();
        BookEditPacket packet = decode(version, action, page, second);
        assertDoesNotThrow(() -> f.handle(packet));
        assertArrayEquals(before, f.book.getCompoundTag());
        f.assertNoPublication();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void validLastPageReplaceDeleteAndSwapPreserveBookData(GameVersion version) {
        for (BookEditPacket.Action action : new BookEditPacket.Action[]{REPLACE_PAGE, DELETE_PAGE, SWAP_PAGES}) {
            Fixture f = new Fixture();
            f.book.setPageText(49, "last");
            f.handle(decode(version, action, action == SWAP_PAGES ? 0 : 49, 49));
            ItemBookAndQuill result = assertInstanceOf(ItemBookAndQuill.class, f.written);
            assertEquals("keep", result.getNamedTag().getString("receipt_marker"));
            assertEquals("last", f.book.getPageText(49), "editing a clone must not mutate the source");
            if (action == REPLACE_PAGE) assertEquals("new", result.getPageText(49));
            if (action == DELETE_PAGE) assertFalse(result.pageExists(49));
            if (action == SWAP_PAGES) {
                assertEquals("last", result.getPageText(0));
                assertEquals("first", result.getPageText(49));
            }
            verify(f.manager).callEvent(any(PlayerEditBookEvent.class));
        }
    }

    @ParameterizedTest
    @MethodSource("versions")
    void validLastPageInsertKeepsExistingPages(GameVersion version) {
        Fixture f = new Fixture();
        f.book.setPageText(48, "previous");
        f.handle(decode(version, ADD_PAGE, 49, 0));
        ItemBookAndQuill result = assertInstanceOf(ItemBookAndQuill.class, f.written);
        assertEquals("new", result.getPageText(49));
        assertEquals("previous", result.getPageText(48));
        assertEquals("first", result.getPageText(0));
        assertEquals("keep", result.getNamedTag().getString("receipt_marker"));
    }

    @Test
    void insertingIntoFullBookStillRefusesWithoutDiscardingPages() {
        Fixture f = new Fixture();
        f.book.setPageText(49, "last");
        byte[] before = f.book.getCompoundTag().clone();
        f.handle(decode(GameVersion.V1_26_50, ADD_PAGE, 0, 0));
        assertArrayEquals(before, f.book.getCompoundTag());
        f.assertNoPublication();
    }

    @Test
    void eventCancellationStillPreservesSourceBook() {
        Fixture f = new Fixture();
        doAnswer(i -> { ((PlayerEditBookEvent) i.getArgument(0)).setCancelled(); return null; })
                .when(f.manager).callEvent(any(PlayerEditBookEvent.class));
        byte[] before = f.book.getCompoundTag().clone();
        f.handle(decode(GameVersion.V1_26_50, REPLACE_PAGE, 0, 0));
        assertArrayEquals(before, f.book.getCompoundTag());
        verify(f.inventory, never()).setItem(anyInt(), any(Item.class));
    }

    @Test
    void signingDoesNotValidateUnusedPageFields() {
        Fixture f = new Fixture();
        BookEditPacket packet = new BookEditPacket();
        packet.action = SIGN_BOOK;
        packet.pageNumber = Integer.MIN_VALUE;
        packet.secondaryPageNumber = Integer.MAX_VALUE;
        packet.title = "Title";
        packet.author = "Author";
        packet.xuid = "123";
        try (MockedStatic<Item> items = mockStatic(Item.class, CALLS_REAL_METHODS)) {
            items.when(() -> Item.get(eq(Item.WRITTEN_BOOK), eq(0), eq(1), any(byte[].class)))
                    .thenAnswer(i -> new ItemBookWritten().setCompoundTag((byte[]) i.getArgument(3)));
            f.handle(packet);
        }
        assertInstanceOf(ItemBookWritten.class, f.written);
        assertEquals("keep", f.written.getNamedTag().getString("receipt_marker"));
        assertEquals("first", ((ItemBookWritten) f.written).getPageText(0));
    }

    @Test
    void existingTextLimitStillRefusesWithoutChangingTheBook() {
        Fixture f = new Fixture();
        BookEditPacket packet = decode(GameVersion.V1_26_50, REPLACE_PAGE, 0, 0);
        packet.text = "x".repeat(257);
        f.handle(packet);
        f.assertNoPublication();
        assertEquals("first", f.book.getPageText(0));
    }

    private static BookEditPacket decode(GameVersion version, BookEditPacket.Action action, int page, int second) {
        BinaryStream wire = new BinaryStream();
        if (version.getProtocol() >= ProtocolInfo.v1_26_0) {
            wire.putVarInt(0);
            wire.putUnsignedVarInt(action.ordinal());
            wire.putVarInt(page);
        } else {
            wire.putByte((byte) action.ordinal());
            wire.putByte((byte) 0);
            wire.putByte((byte) page);
        }
        if (action == SWAP_PAGES) {
            if (version.getProtocol() >= ProtocolInfo.v1_26_0) wire.putVarInt(second);
            else wire.putByte((byte) second);
        }
        if (action == ADD_PAGE || action == REPLACE_PAGE) {
            wire.putString("new");
            wire.putString("");
        }
        BookEditPacket packet = new BookEditPacket();
        packet.protocol = version.getProtocol();
        packet.setBuffer(wire.getBuffer());
        packet.decode();
        assertTrue(packet.feof());
        return packet;
    }

    private static final class Fixture {
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final PluginManager manager = mock(PluginManager.class);
        final ItemBookAndQuill book = new ItemBookAndQuill();
        Item written;

        Fixture() {
            Server server = mock(Server.class);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            book.setPageText(0, "first");
            book.setNamedTag(book.getNamedTag().putString("receipt_marker", "keep"));
            when(inventory.getItem(0)).thenAnswer(i -> book);
            when(inventory.setItem(eq(0), any(Item.class))).thenAnswer(i -> {
                written = ((Item) i.getArgument(1)).clone();
                return true;
            });
        }

        void handle(BookEditPacket packet) {
            BookEditProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
        }

        void assertNoPublication() {
            verify(inventory, never()).setItem(anyInt(), any(Item.class));
            verify(manager, never()).callEvent(any(PlayerEditBookEvent.class));
        }
    }
}
