package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.FilterTextPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author glorydark
 * @date {2024/1/10} {12:30}
 */
public class FilterTextProcessor extends DataPacketProcessor<FilterTextPacket> {

    public static final FilterTextProcessor INSTANCE = new FilterTextProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull FilterTextPacket pk) {
        Player player = playerHandle.player;
        if (pk.text == null || pk.text.length() > 64) {
            player.getServer().getLogger().debug(player.getUsername() + ": FilterTextPacket with too long text");
            return;
        }
        FilterTextPacket textResponsePacket = new FilterTextPacket();
        textResponsePacket.text = pk.text;
        textResponsePacket.fromServer = true;
        player.dataPacket(textResponsePacket);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.FILTER_TEXT_PACKET);
    }
}
