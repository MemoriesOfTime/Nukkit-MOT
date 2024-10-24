package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.TextPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TextProcessor extends DataPacketProcessor<TextPacket> {

    public static final TextProcessor INSTANCE = new TextProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull TextPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }

        if (pk.type == TextPacket.TYPE_CHAT) {
            String chatMessage = pk.message;
            int breakLine = chatMessage.indexOf('\n');
            // Chat messages shouldn't contain break lines so ignore text afterwards
            if (breakLine != -1) {
                chatMessage = chatMessage.substring(0, breakLine);
            }
            player.chat(chatMessage);
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.TEXT_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return TextPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
