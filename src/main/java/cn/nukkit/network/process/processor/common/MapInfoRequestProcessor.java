package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.event.player.PlayerMapInfoRequestEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemMap;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.MapInfoRequestPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.scheduler.AsyncTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author glorydark
 * @date {2024/1/10} {12:45}
 */
public class MapInfoRequestProcessor extends DataPacketProcessor<MapInfoRequestPacket> {

    public static final MapInfoRequestProcessor INSTANCE = new MapInfoRequestProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull MapInfoRequestPacket pk) {
        Player player = playerHandle.player;
        ItemMap mapItem = null;

        for (Item item1 : player.getOffhandInventory().getContents().values()) {
            if (item1 instanceof ItemMap map && map.getMapId() == pk.mapId) {
                mapItem = map;
            }
        }

        if (mapItem == null) {
            for (Item item1 : player.getInventory().getContents().values()) {
                if (item1 instanceof ItemMap map && map.getMapId() == pk.mapId) {
                    mapItem = map;
                }
            }
        }

        if (mapItem == null) {
            for (BlockEntity be : player.level.getBlockEntities().values()) {
                if (be instanceof BlockEntityItemFrame itemFrame1) {

                    if (itemFrame1.getItem() instanceof ItemMap && ((ItemMap) itemFrame1.getItem()).getMapId() == pk.mapId) {
                        ((ItemMap) itemFrame1.getItem()).sendImage(player);
                        break;
                    }
                }
            }
        } else {
            PlayerMapInfoRequestEvent event;
            player.getServer().getPluginManager().callEvent(event = new PlayerMapInfoRequestEvent(player, mapItem));

            if (!event.isCancelled()) {
                if (mapItem.trySendImage(player)) {
                    return;
                }

                ItemMap finalMapItem = mapItem;
                player.getServer().getScheduler().scheduleAsyncTask(new AsyncTask() {
                    @Override
                    public void onRun() {
                        finalMapItem.renderMap(player.getLevel(), (player.getFloorX() / 128) << 7, (player.getFloorZ() / 128) << 7, 1);
                        finalMapItem.sendImage(player);
                    }
                });
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.MAP_INFO_REQUEST_PACKET);
    }
}
