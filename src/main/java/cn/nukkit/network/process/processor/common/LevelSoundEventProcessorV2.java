package cn.nukkit.network.process.processor.common;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author LT_Name
 */
public class LevelSoundEventProcessorV2 extends LevelSoundEventProcessor {

    public static final LevelSoundEventProcessorV2 INSTANCE = new LevelSoundEventProcessorV2();

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V2);
    }
}
