package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.types.structure.StructureSettings;
import lombok.ToString;

/**
 * 结构方块编辑器同步包。服务端下发用于更新客户端界面，客户端在关闭界面时回传设置。
 * <p>
 * Structure block editor sync. Sent by the server to refresh the client UI; the
 * client sends it back with the edited settings (and {@link #powered} set when the
 * player presses the save/load button).
 */
@ToString(exclude = "settings")
public class StructureBlockUpdatePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.STRUCTURE_BLOCK_UPDATE_PACKET;

    public int x;
    public int y;
    public int z;
    public String name = "";
    /**
     * @since v776 1.21.60
     */
    public String filteredName = "";
    public String dataField = "";
    public boolean includingPlayers;
    public boolean boundingBoxVisible;
    /**
     * 0=data 1=save 2=load 3=corner 4=invalid 5=export
     */
    public int type;
    public StructureSettings settings = new StructureSettings();
    /**
     * 0=存档 1=内存
     */
    public int redstoneSaveMode;
    public boolean powered;
    /**
     * @since v554 1.19.30
     */
    public boolean waterlogged;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        BlockVector3 pos = this.getBlockVector3(this.gameVersion);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;

        if (this.protocol < ProtocolInfo.v1_12_0) {
            this.decodeLegacyEditorData();
        } else {
            this.decodeEditorData();
        }
        this.powered = this.getBoolean();
        if (this.protocol >= ProtocolInfo.v1_19_30) {
            this.waterlogged = this.getBoolean();
        }
    }

    private void decodeLegacyEditorData() {
        this.type = (int) this.getUnsignedVarInt();
        this.name = this.getString();
        this.dataField = this.getString();
        BlockVector3 offset = this.getBlockVector3(this.gameVersion);
        BlockVector3 size = this.getBlockVector3(this.gameVersion);
        this.settings.offsetX = offset.x;
        this.settings.offsetY = offset.y;
        this.settings.offsetZ = offset.z;
        this.settings.sizeX = size.x;
        this.settings.sizeY = size.y;
        this.settings.sizeZ = size.z;
        this.settings.ignoringEntities = !this.getBoolean();
        this.settings.ignoringBlocks = !this.getBoolean();
        this.includingPlayers = this.getBoolean();
        this.getBoolean(); // show air, no longer supported by the client
        this.settings.integrity = this.getLFloat();
        this.settings.integritySeed = (int) this.getUnsignedVarInt();
        this.settings.mirror = (int) this.getUnsignedVarInt();
        this.settings.rotation = (int) this.getUnsignedVarInt();
        this.getBoolean(); // ignore entities, duplicated below
        this.getBoolean(); // ignore structure blocks
        this.getSignedBlockPosition(); // bounding box min
        this.getSignedBlockPosition(); // bounding box max
        this.boundingBoxVisible = this.getBoolean();
    }

    private void decodeEditorData() {
        this.name = this.getString();
        if (this.protocol >= ProtocolInfo.v1_21_60) {
            this.filteredName = this.getString();
        } else {
            this.filteredName = this.name;
        }
        this.dataField = this.getString();
        this.includingPlayers = this.getBoolean();
        this.boundingBoxVisible = this.getBoolean();
        this.type = this.getVarInt();
        this.settings.read(this, this.gameVersion);
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.redstoneSaveMode = this.getByte();
        } else {
            this.redstoneSaveMode = this.getVarInt();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putBlockVector3(this.gameVersion, this.x, this.y, this.z);
        if (this.protocol < ProtocolInfo.v1_12_0) {
            this.encodeLegacyEditorData();
        } else {
            this.encodeEditorData();
        }
        this.putBoolean(this.powered);
        if (this.protocol >= ProtocolInfo.v1_19_30) {
            this.putBoolean(this.waterlogged);
        }
    }

    private void encodeLegacyEditorData() {
        this.putUnsignedVarInt(this.type);
        this.putString(this.name);
        this.putString(this.dataField);
        this.putBlockVector3(this.gameVersion, this.settings.offsetX, this.settings.offsetY, this.settings.offsetZ);
        this.putBlockVector3(this.gameVersion, this.settings.sizeX, this.settings.sizeY, this.settings.sizeZ);
        this.putBoolean(!this.settings.ignoringEntities);
        this.putBoolean(!this.settings.ignoringBlocks);
        this.putBoolean(this.includingPlayers);
        this.putBoolean(false); // show air
        this.putLFloat(this.settings.integrity);
        this.putUnsignedVarInt(this.settings.integritySeed);
        this.putUnsignedVarInt(this.settings.mirror);
        this.putUnsignedVarInt(this.settings.rotation);
        this.putBoolean(this.settings.ignoringEntities);
        this.putBoolean(true); // ignore structure blocks
        int minX = this.x + this.settings.offsetX;
        int minY = this.y + this.settings.offsetY;
        int minZ = this.z + this.settings.offsetZ;
        this.putSignedBlockPosition(new BlockVector3(minX, minY, minZ));
        this.putSignedBlockPosition(new BlockVector3(minX + this.settings.sizeX, minY + this.settings.sizeY, minZ + this.settings.sizeZ));
        this.putBoolean(this.boundingBoxVisible);
    }

    private void encodeEditorData() {
        this.putString(this.name);
        if (this.protocol >= ProtocolInfo.v1_21_60) {
            this.putString(this.filteredName);
        }
        this.putString(this.dataField);
        this.putBoolean(this.includingPlayers);
        this.putBoolean(this.boundingBoxVisible);
        this.putVarInt(this.type);
        this.settings.write(this, this.gameVersion);
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putByte(this.redstoneSaveMode);
        } else {
            this.putVarInt(this.redstoneSaveMode);
        }
    }
}
