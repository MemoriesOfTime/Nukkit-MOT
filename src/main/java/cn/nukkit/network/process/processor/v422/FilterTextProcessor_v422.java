package cn.nukkit.network.process.processor.v422;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.FilterTextPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author glorydark
 * @date {2024/1/10} {12:30}
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FilterTextProcessor_v422 extends DataPacketProcessor<FilterTextPacket> {

    public static final FilterTextProcessor_v422 INSTANCE = new FilterTextProcessor_v422();

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

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_16_200;
    }
}
