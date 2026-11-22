package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.types.structure.StructureSettings;
import lombok.ToString;

/**
 * 客户端请求结构方块执行导出/查询（“导出”按钮与结构列表查询）。
 * <p>
 * Client request for a structure template export or query, sent when the player
 * presses "Export" or asks the server which structures exist.
 */
@ToString(exclude = "settings")
public class StructureTemplateDataRequestPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.STRUCTURE_TEMPLATE_DATA_EXPORT_REQUEST;

    public static final int OPERATION_NONE = 0;
    public static final int OPERATION_EXPORT_FROM_SAVED_MODE = 1;
    public static final int OPERATION_EXPORT_FROM_LOAD_MODE = 2;
    public static final int OPERATION_QUERY_SAVED_STRUCTURE = 3;
    public static final int OPERATION_IMPORT = 4;

    public String name = "";
    public int x;
    public int y;
    public int z;
    public StructureSettings settings = new StructureSettings();
    public int operation;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.name = this.getString(256);
        BlockVector3 pos = this.getBlockVector3(this.gameVersion);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.settings.read(this, this.gameVersion);
        this.operation = this.getByte();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.name);
        this.putBlockVector3(this.gameVersion, this.x, this.y, this.z);
        this.settings.write(this, this.gameVersion);
        this.putByte((byte) this.operation);
    }
}
