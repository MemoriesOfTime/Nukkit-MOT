package cn.nukkit.network.process.processor.common;

import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelSoundEventProcessorV2 extends LevelSoundEventProcessor {

    public static final LevelSoundEventProcessorV2 INSTANCE = new LevelSoundEventProcessorV2();

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V2);
    }
}
