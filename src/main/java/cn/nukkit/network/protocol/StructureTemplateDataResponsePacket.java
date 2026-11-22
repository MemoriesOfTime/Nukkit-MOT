package cn.nukkit.network.protocol;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.ToString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * 服务端返回结构模板数据（“导出”时客户端据此在本地生成 .mcstructure 文件）。
 * <p>
 * Server response carrying the structure template NBT; the client turns it into a
 * local .mcstructure file when {@link #save} is set.
 */
@ToString(exclude = "tag")
public class StructureTemplateDataResponsePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.STRUCTURE_TEMPLATE_DATA_EXPORT_RESPONSE;

    public static final int RESPONSE_NONE = 0;
    public static final int RESPONSE_EXPORT = 1;
    public static final int RESPONSE_QUERY = 2;
    public static final int RESPONSE_IMPORT = 3;

    public String name = "";
    public boolean save;
    public CompoundTag tag;
    /**
     * @since v388 1.14.0
     */
    public int type;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.name = this.getString();
        this.save = this.getBoolean();
        if (this.save) {
            byte[] remaining = this.get();
            ByteArrayInputStream input = new ByteArrayInputStream(remaining);
            try {
                this.tag = NBTIO.readTag(input, ByteOrder.LITTLE_ENDIAN, true) instanceof CompoundTag compound ? compound : null;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to decode structure template data", e);
            }
            this.setOffset(this.getOffset() - input.available());
        }
        if (this.protocol >= ProtocolInfo.v1_13_0) {
            this.type = this.getByte();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.name);
        this.putBoolean(this.save);
        if (this.save) {
            try {
                this.put(NBTIO.write(this.tag, ByteOrder.LITTLE_ENDIAN, true));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to encode structure template data", e);
            }
        }
        if (this.protocol >= ProtocolInfo.v1_13_0) {
            this.putByte((byte) this.type);
        }
    }
}
