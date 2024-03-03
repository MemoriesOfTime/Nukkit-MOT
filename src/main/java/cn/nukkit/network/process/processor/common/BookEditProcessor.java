package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerEditBookEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBookAndQuill;
import cn.nukkit.item.ItemBookWritten;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.BookEditPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class BookEditProcessor extends DataPacketProcessor<BookEditPacket> {

    public static final BookEditProcessor INSTANCE = new BookEditProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull BookEditPacket pk) {
        Player player = playerHandle.player;
        Item oldBook = player.getInventory().getItem(pk.inventorySlot);
        if (oldBook.getId() != Item.BOOK_AND_QUILL) {
            return;
        }

        if (pk.text != null && pk.text.length() > 256) {
            player.getServer().getLogger().debug(playerHandle.getUsername() + ": BookEditPacket with too long text");
            return;
        }

        Item newBook = oldBook.clone();
        boolean success;
        switch (pk.action) {
            case REPLACE_PAGE:
                success = ((ItemBookAndQuill) newBook).setPageText(pk.pageNumber, pk.text);
                break;
            case ADD_PAGE:
                success = ((ItemBookAndQuill) newBook).insertPage(pk.pageNumber, pk.text);
                break;
            case DELETE_PAGE:
                success = ((ItemBookAndQuill) newBook).deletePage(pk.pageNumber);
                break;
            case SWAP_PAGES:
                success = ((ItemBookAndQuill) newBook).swapPages(pk.pageNumber, pk.secondaryPageNumber);
                break;
            case SIGN_BOOK:
                if (pk.title == null || pk.author == null || pk.xuid == null || pk.title.length() > 64 || pk.author.length() > 64 || pk.xuid.length() > 64) {
                    player.getServer().getLogger().debug(playerHandle.getUsername() + ": Invalid BookEditPacket action SIGN_BOOK: title/author/xuid is too long");
                    return;
                }
                newBook = Item.get(Item.WRITTEN_BOOK, 0, 1, oldBook.getCompoundTag());
                success = ((ItemBookWritten) newBook).signBook(pk.title, pk.author, pk.xuid, ItemBookWritten.GENERATION_ORIGINAL);
                break;
            default:
                return;
        }

        if (success) {
            PlayerEditBookEvent editBookEvent = new PlayerEditBookEvent(player, oldBook, newBook, pk.action);
            player.getServer().getPluginManager().callEvent(editBookEvent);
            if (!editBookEvent.isCancelled()) {
                player.getInventory().setItem(pk.inventorySlot, editBookEvent.getNewBook());
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.BOOK_EDIT_PACKET);
    }
}
