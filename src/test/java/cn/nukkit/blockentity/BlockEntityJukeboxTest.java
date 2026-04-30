package cn.nukkit.blockentity;

import cn.nukkit.item.*;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlockEntityJukeboxTest {

    @Test
    public void testRecentStringRecordsHaveSoundEvents() {
        assertEquals(LevelSoundEventPacket.SOUND_RECORD_TEARS,
                BlockEntityJukebox.getRecordSoundEvent(new ItemRecordTears()));
        assertEquals(LevelSoundEventPacket.SOUND_RECORD_LAVA_CHICKEN,
                BlockEntityJukebox.getRecordSoundEvent(new ItemRecordLavaChicken()));
    }

    @Test
    public void testStringRecordsHaveComparatorSignals() {
        assertEquals(11, BlockEntityJukebox.getRecordComparatorSignal(new ItemRecordCreatorMusicBox()));
        assertEquals(12, BlockEntityJukebox.getRecordComparatorSignal(new ItemRecordCreator()));
        assertEquals(13, BlockEntityJukebox.getRecordComparatorSignal(new ItemRecordPrecipice()));
        assertEquals(15, BlockEntityJukebox.getRecordComparatorSignal(new ItemRecordTears()));
        assertEquals(15, BlockEntityJukebox.getRecordComparatorSignal(new ItemRecordLavaChicken()));
    }

    @Test
    public void testRelicComparatorSignalMatchesModernDiscOrdering() {
        assertEquals(14, BlockEntityJukebox.getRecordComparatorSignal(new ItemRecordRelic()));
    }
}
