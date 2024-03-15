package cn.nukkit.network.process.processor.common;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.PacketViolationWarningPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PacketViolationWarningProcessor extends DataPacketProcessor<PacketViolationWarningPacket> {

    public static final PacketViolationWarningProcessor INSTANCE = new PacketViolationWarningProcessor();

    private static Stream<Field> pkIDs;

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull PacketViolationWarningPacket pk) {
        if (pkIDs == null) {
            pkIDs = Arrays.stream(ProtocolInfo.class.getDeclaredFields()).filter(field -> field.getName().endsWith("_PACKET"));
        }
        Optional<String> PVWpkName = pkIDs
                .filter(field -> {
                    try {
                        if (pk.packetId >= 300) { //int id数据包从300开始
                            return field.getInt(null) == pk.packetId;
                        }
                        return field.getByte(null) == pk.packetId;
                    } catch (IllegalAccessException e) {
                        return false;
                    }
                }).map(Field::getName).findFirst();
        playerHandle.player.getServer().getLogger().warning("PacketViolationWarningPacket " + PVWpkName.map(name -> " for packet " + name).orElse(" UNKNOWN") + " from " + playerHandle.getUsername() + " (Protocol " + playerHandle.getProtocol() + "): " + pk);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.PACKET_VIOLATION_WARNING_PACKET);
    }
}
