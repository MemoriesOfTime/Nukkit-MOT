package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.StringItem;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class StringItemSlotAuxDecodeTest {
    static Stream<Arguments> slots() {
        return versions().flatMap(v -> Arrays.stream(new int[]{0,1,32767,32768,65534})
                .boxed().flatMap(d -> Stream.of(false,true).map(instance -> Arguments.of(v,d,instance))));
    }
    static Stream<Arguments> nbtDamage() {
        return versions().flatMap(v -> Stream.of(false,true).map(instance -> Arguments.of(v,instance)));
    }
    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values()).filter(v -> !v.isNetEase()
                && v.getProtocol()>=589 && v.getProtocol()<=2193);
    }
    @ParameterizedTest(name="slot {0} aux={1} instance={2}")
    @MethodSource("slots")
    void stringIdentifierIsIndependentOfUnsignedAuxDamage(GameVersion version,int damage,boolean instance) throws Exception {
        Item decoded=decode(version,damage,instance,null);
        assertInstanceOf(StringItem.class,decoded);
        assertEquals("minecraft:echo_shard",decoded.getNamespaceId());
        assertEquals(damage,decoded.getDamage());
        assertEquals(7,decoded.getCount());
        if(!instance)assertEquals(42,decoded.getStackNetId());
    }
    @ParameterizedTest(name="NBT Damage precedence {0} instance={1}")
    @MethodSource("nbtDamage")
    void nbtDamageOverrideDoesNotPassThroughShortIdentifierParser(GameVersion version,boolean instance) throws Exception {
        Item decoded=decode(version,40000,instance,new CompoundTag().putInt("Damage",7).putString("marker","preserved"));
        assertEquals("minecraft:echo_shard",decoded.getNamespaceId());
        assertEquals(7,decoded.getDamage());
        assertEquals("preserved",decoded.getNamedTag().getString("marker"));
    }
    private static Item decode(GameVersion version,int damage,boolean instance,CompoundTag tag) throws Exception {
        Item prototype=Item.fromString("minecraft:echo_shard");
        assertInstanceOf(StringItem.class,prototype);
        int runtime=RuntimeItems.getMapping(version).getNetworkId(prototype);
        BinaryStream wire=new BinaryStream();
        boolean descriptor=version.getProtocol()>=2168;
        if(descriptor&&!instance)wire.putLShort(runtime);else wire.putVarInt(runtime);
        wire.putLShort(7);wire.putUnsignedVarInt(damage);
        if(!instance){wire.putBoolean(true);wire.putVarInt(42);}
        if(descriptor&&!instance)wire.putUnsignedVarInt(0);else wire.putVarInt(0);
        BinaryStream data=new BinaryStream();
        if(tag==null)data.putLShort(0);
        else{data.putLShort(0xffff);data.putByte((byte)1);data.put(NBTIO.write(tag,ByteOrder.LITTLE_ENDIAN));}
        data.putLInt(0);data.putLInt(0);wire.putByteArray(data.getBuffer());
        byte[] encoded=wire.getBuffer();BinaryStream reader=new BinaryStream(encoded);
        Item value;
        if(!instance)value=reader.getSlot(version);
        else{
            var method=BinaryStream.class.getDeclaredMethod("getSlotNew",GameVersion.class,boolean.class);
            method.setAccessible(true);
            try{value=(Item)method.invoke(reader,version,true);}
            catch(InvocationTargetException ex){
                if(ex.getCause() instanceof Exception cause)throw cause;
                throw ex;
            }
        }
        assertEquals(encoded.length,reader.offset,"all slot bytes consumed");
        return value;
    }
}
