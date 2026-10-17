package cn.nukkit.level.explosion;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Raw resource regressions for canonical property types and unchanged row identity, without Server bootstrap. */
public final class NativeExplosionPaletteTypedStateTest {
    private record Slot(int protocol,int ordinal,int id,int meta,int runtimeId) { }
    private static final Slot[] ANCHOR = {
        new Slot(440,7773,527,1,6523),
        new Slot(440,7774,527,2,6524),
        new Slot(440,7775,527,3,6525),
        new Slot(440,7776,527,4,6526),
        new Slot(448,7923,527,1,6673),
        new Slot(448,7924,527,2,6674),
        new Slot(448,7925,527,3,6675),
        new Slot(448,7926,527,4,6676),
        new Slot(465,7923,527,1,6697),
        new Slot(465,7924,527,2,6698),
        new Slot(465,7925,527,3,6699),
        new Slot(465,7926,527,4,6700),
        new Slot(471,7923,527,1,6700),
        new Slot(471,7924,527,2,6701),
        new Slot(471,7925,527,3,6702),
        new Slot(471,7926,527,4,6703),
        new Slot(486,7923,527,1,6703),
        new Slot(486,7924,527,2,6704),
        new Slot(486,7925,527,3,6705),
        new Slot(486,7926,527,4,6706),
        new Slot(503,8125,527,1,690),
        new Slot(503,8126,527,2,691),
        new Slot(503,8127,527,3,692),
        new Slot(503,8128,527,4,693),
        new Slot(527,8332,527,1,682),
        new Slot(527,8333,527,2,683),
        new Slot(527,8334,527,3,684),
        new Slot(527,8335,527,4,685),
        new Slot(544,8573,527,1,684),
        new Slot(544,8574,527,2,685),
        new Slot(544,8575,527,3,686),
        new Slot(544,8576,527,4,687),
        new Slot(560,11889,527,1,1297),
        new Slot(560,11890,527,2,1298),
        new Slot(560,11891,527,3,1299),
        new Slot(560,11892,527,4,1300),
        new Slot(567,11952,527,1,1360),
        new Slot(567,11953,527,2,1361),
        new Slot(567,11954,527,3,1362),
        new Slot(567,11955,527,4,1363),
        new Slot(575,11966,527,1,1366),
        new Slot(575,11967,527,2,1367),
        new Slot(575,11968,527,3,1368),
        new Slot(575,11969,527,4,1369),
        new Slot(582,12416,527,1,1777),
        new Slot(582,12417,527,2,1778),
        new Slot(582,12418,527,3,1779),
        new Slot(582,12419,527,4,1780),
        new Slot(589,12793,527,1,1777),
        new Slot(589,12794,527,2,1778),
        new Slot(589,12795,527,3,1779),
        new Slot(589,12796,527,4,1780),
        new Slot(618,13063,527,1,1775),
        new Slot(618,13064,527,2,1776),
        new Slot(618,13065,527,3,1777),
        new Slot(618,13066,527,4,1778),
        new Slot(622,13053,527,1,1775),
        new Slot(622,13054,527,2,1776),
        new Slot(622,13055,527,3,1777),
        new Slot(622,13056,527,4,1778),
    };
    private static final Slot[] SOUL = {
        new Slot(419,7050,545,0,5649),
        new Slot(419,7051,545,1,5650),
        new Slot(419,7052,545,2,5651),
        new Slot(419,7053,545,3,5652),
        new Slot(419,7054,545,4,5653),
        new Slot(419,7055,545,5,5654),
        new Slot(419,7056,545,6,5655),
        new Slot(419,7057,545,7,5656),
    };
    private static int expectedRows(int protocol) {
        return switch (protocol) {
            case 419 -> 8324;
            case 440 -> 9660;
            case 448 -> 9830;
            case 465 -> 9830;
            case 471 -> 9830;
            case 486 -> 9830;
            case 503 -> 10272;
            case 527 -> 10561;
            case 544 -> 10756;
            case 560 -> 14840;
            case 567 -> 14903;
            case 575 -> 14912;
            case 582 -> 15424;
            case 589 -> 15827;
            case 594 -> 15827;
            case 618 -> 15839;
            case 622 -> 15819;
            case 1001 -> 20082;
            case 2168 -> 20082;
            case 2193 -> 20050;
            default -> throw new AssertionError("Unreviewed palette protocol " + protocol);
        };
    }
    private static ListTag<?> palette(int protocol) throws Exception {
        String resource="runtime_block_states_" + protocol + ".dat";
        try (var input=NativeExplosionPaletteTypedStateTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input,resource);
            try (var buffered=new BufferedInputStream(new GZIPInputStream(input))) {
                ListTag<?> root=assertInstanceOf(ListTag.class,NBTIO.readTag(new java.io.FilterInputStream(buffered) {
                    @Override public void close() {}
                },ByteOrder.BIG_ENDIAN,false));
                assertEquals(-1,buffered.read(),"Exact root EOF " + resource);
                assertEquals(expectedRows(protocol),root.size(),"Every original row retained " + resource);
                return root;
            }
        }
    }
    private static CompoundTag exact(ListTag<?> rows,Slot slot,String name) throws Exception {
        CompoundTag row=assertInstanceOf(CompoundTag.class,rows.get(slot.ordinal()));
        assertTrue(row.containsInt("id") && row.containsShort("data") && row.containsInt("runtimeId"));
        assertEquals(slot.id(),row.getInt("id")); assertEquals(slot.meta(),row.getShort("data"));
        assertEquals(slot.runtimeId(),row.getInt("runtimeId"),"Native runtime ordinal remains unchanged");
        assertTrue(!row.exist("stateOverload") || row.containsByte("stateOverload"));
        assertFalse(row.getBoolean("stateOverload"));
        assertTrue(row.containsString("name") && row.containsCompound("states"));assertEquals(name,row.getString("name"));
        return row;
    }
    @Test void chargedAnchorsUseIntegerChargeInAllFifteenAffectedPalettes() throws Exception {
        int protocol=-1,checked=0;ListTag<?> rows=null;
        for (Slot slot:ANCHOR) {
            if (slot.protocol()!=protocol) { protocol=slot.protocol();rows=palette(protocol); }
            CompoundTag row=exact(rows,slot,"minecraft:respawn_anchor"),states=row.getCompound("states");
            assertEquals(Set.of("respawn_anchor_charge"),states.getTags().keySet());
            assertTrue(states.containsInt("respawn_anchor_charge"),"Integer anchor charge " + slot);
            assertEquals(slot.meta(),states.getInt("respawn_anchor_charge"));checked++;
        }
        assertEquals(60,checked);
    }
    @Test void soulCampfire419UsesBooleanByteWithoutChangingDirections() throws Exception {
        ListTag<?> rows=palette(419);int checked=0;
        for (Slot slot:SOUL) {
            CompoundTag row=exact(rows,slot,"minecraft:soul_campfire"),states=row.getCompound("states");
            assertEquals(Set.of("direction","extinguished"),states.getTags().keySet());
            assertTrue(states.containsByte("extinguished") && states.containsInt("direction"));
            assertEquals(slot.meta()>>>2,states.getByte("extinguished"));
            assertEquals(slot.meta()&3,states.getInt("direction"));checked++;
        }
        assertEquals(8,checked);
    }
    @Test void alreadyCorrectUnchargedModernAnchorsAndOrdinaryCampfiresRemainTyped() throws Exception {
        int checked=0;
        for (int protocol:new int[]{440,448,465,471,486,503,527,544,560,567,575,582,589,618,622,594,2193}) {
            int anchors=0;
            for (var value:palette(protocol).getAll()) {
                CompoundTag row=assertInstanceOf(CompoundTag.class,value);
                if (!row.getString("name").equals("minecraft:respawn_anchor")) continue;
                CompoundTag states=row.getCompound("states");
                int charge=states.getInt("respawn_anchor_charge");
                if (charge!=0 && protocol!=594 && protocol!=2193) continue;
                assertTrue(states.containsInt("respawn_anchor_charge"));assertTrue(charge>=0 && charge<=4);
                anchors++;checked++;
            }
            assertEquals(protocol==594 || protocol==2193 ? 5 : 1,anchors);
        }
        int camps=0;
        for (var value:palette(419).getAll()) {
            CompoundTag row=assertInstanceOf(CompoundTag.class,value);
            if (!row.getString("name").equals("minecraft:campfire")) continue;
            CompoundTag states=row.getCompound("states");
            assertTrue(states.containsByte("extinguished") && states.containsInt("direction"));
            assertTrue(states.getByte("extinguished")==0 || states.getByte("extinguished")==1);
            assertTrue(states.getInt("direction")>=0 && states.getInt("direction")<=3);camps++;
        }
        assertEquals(8,camps);assertEquals(25,checked);
    }
}
