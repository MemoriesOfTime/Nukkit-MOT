package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import lombok.ToString;

import java.io.IOException;
import java.util.zip.Deflater;

/**
 * 结构生成数据；vanilla 数据量庞大，默认发送空数据集。
 * <p>
 * Structure generation data; the vanilla payload is huge, so an empty data set is sent by default.
 * <p>
 * Adapted from NukkitPetteriM1Edition (<a href="https://github.com/PetteriM1/NukkitPetteriM1Edition">Nukkit PM1E</a>)
 */
@ToString
public class JigsawStructureDataPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.JIGSAW_STRUCTURE_DATA_PACKET;

    private static final BatchPacket CACHED_PACKET;

    static {
        JigsawStructureDataPacket pk = new JigsawStructureDataPacket();
        // 空数据集 = 无自定义结构生成 / empty data set = no custom structure generation
        pk.nbt = new CompoundTag("")
                .putList(new ListTag<>("jigsaws"))
                .putList(new ListTag<>("processors"))
                .putList(new ListTag<>("structure_sets"))
                .putList(new ListTag<>("template_pools"));
        pk.protocol = ProtocolInfo.v1_21_120;
        pk.gameVersion = GameVersion.V1_21_120;
        pk.tryEncode();
        CACHED_PACKET = pk.compress(Deflater.BEST_COMPRESSION);
    }

    public static BatchPacket getCachedPacket() {
        return CACHED_PACKET;
    }

    public CompoundTag nbt;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        this.nbt = this.getTag();
    }

    @Override
    public void encode() {
        this.reset();
        try {
            this.put(NBTIO.writeNetwork(this.nbt != null ? this.nbt : new CompoundTag()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
