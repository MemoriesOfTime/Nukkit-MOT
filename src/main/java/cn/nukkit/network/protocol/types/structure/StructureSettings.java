package cn.nukkit.network.protocol.types.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;

/**
 * 结构方块的编辑器设置（与结构更新包、结构数据请求包共用同一套线格式）。
 * <p>
 * Structure editor settings shared by the structure block update packet and the
 * structure template data request packet. The wire layout grew over several
 * protocol versions; {@link #read}/{@link #write} encode every historical variant.
 */
public class StructureSettings {

    public String paletteName = "";
    public boolean ignoringEntities;
    public boolean ignoringBlocks;
    public boolean nonTickingPlayersAndTickingAreasEnabled = true;
    public int sizeX;
    public int sizeY;
    public int sizeZ;
    public int offsetX;
    public int offsetY;
    public int offsetZ;
    public long lastEditedByEntityId;
    public int rotation;
    public int mirror;
    public int animationMode;
    public float animationSeconds;
    public float integrity = 100f;
    public int integritySeed;
    public float pivotX;
    public float pivotY;
    public float pivotZ;

    public void read(BinaryStream stream, GameVersion version) {
        int protocol = version.getProtocol();
        this.paletteName = stream.getString();
        this.ignoringEntities = stream.getBoolean();
        this.ignoringBlocks = stream.getBoolean();
        if (protocol >= ProtocolInfo.v1_18_30) {
            this.nonTickingPlayersAndTickingAreasEnabled = stream.getBoolean();
        }
        BlockVector3 size = stream.getBlockVector3(version);
        this.sizeX = size.x;
        this.sizeY = size.y;
        this.sizeZ = size.z;
        BlockVector3 offset = stream.getBlockVector3(version);
        this.offsetX = offset.x;
        this.offsetY = offset.y;
        this.offsetZ = offset.z;
        this.lastEditedByEntityId = stream.getVarLong();
        this.rotation = stream.getByte();
        this.mirror = stream.getByte();
        if (protocol >= ProtocolInfo.v1_17_0) {
            this.animationMode = stream.getByte();
            this.animationSeconds = stream.getLFloat();
        }
        this.integrity = stream.getLFloat();
        this.integritySeed = stream.getLInt();
        if (protocol >= ProtocolInfo.v1_13_0) {
            Vector3f pivot = stream.getVector3f();
            this.pivotX = pivot.x;
            this.pivotY = pivot.y;
            this.pivotZ = pivot.z;
        }
    }

    public void write(BinaryStream stream, GameVersion version) {
        int protocol = version.getProtocol();
        stream.putString(this.paletteName);
        stream.putBoolean(this.ignoringEntities);
        stream.putBoolean(this.ignoringBlocks);
        if (protocol >= ProtocolInfo.v1_18_30) {
            stream.putBoolean(this.nonTickingPlayersAndTickingAreasEnabled);
        }
        stream.putBlockVector3(version, this.sizeX, this.sizeY, this.sizeZ);
        stream.putBlockVector3(version, this.offsetX, this.offsetY, this.offsetZ);
        stream.putVarLong(this.lastEditedByEntityId);
        stream.putByte((byte) this.rotation);
        stream.putByte((byte) this.mirror);
        if (protocol >= ProtocolInfo.v1_17_0) {
            stream.putByte((byte) this.animationMode);
            stream.putLFloat(this.animationSeconds);
        }
        stream.putLFloat(this.integrity);
        stream.putLInt(this.integritySeed);
        if (protocol >= ProtocolInfo.v1_13_0) {
            stream.putVector3f(this.pivotX, this.pivotY, this.pivotZ);
        }
    }
}
