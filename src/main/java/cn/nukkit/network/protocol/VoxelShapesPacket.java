package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.types.voxel.SerializableVoxelShape;
import com.google.common.io.ByteStreams;
import lombok.ToString;

import java.io.IOException;
import java.util.*;
import java.util.zip.Deflater;

/**
 * Syncs client with server voxel shape data on world join.
 * This packet contains a copy of all behavior pack voxel shapes data.
 * Sends the serializable voxel shapes data to the client as it's needed on both the client and server.
 * <p>
 * v2192 起客户端要求 vanilla 体素形状数据，登录时发送缓存的 voxel_shapes_2192.bin；旧版本发送空数据。
 * <p>
 * Since v2192 clients require the vanilla voxel shape data; a cached voxel_shapes_2192.bin is sent on login,
 * older protocols receive an empty data set.
 * <p>
 * Adapted from NukkitPetteriM1Edition (<a href="https://github.com/PetteriM1/NukkitPetteriM1Edition">Nukkit PM1E</a>)
 *
 * @since v924
 */
@ToString
public class VoxelShapesPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.VOXEL_SHAPES_PACKET;

    private static final BatchPacket CACHED_PACKET_2192;
    private static final BatchPacket CACHED_PACKET_EMPTY;

    static {
        VoxelShapesPacket pk = new VoxelShapesPacket();
        pk.protocol = ProtocolInfo.v1_26_50_27;
        pk.gameVersion = GameVersion.V1_26_50_27;
        try {
            pk.bin = ByteStreams.toByteArray(Objects.requireNonNull(
                    VoxelShapesPacket.class.getClassLoader().getResourceAsStream("voxel_shapes_2192.bin")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        pk.tryEncode();
        CACHED_PACKET_2192 = pk.compress(Deflater.BEST_COMPRESSION);

        pk = new VoxelShapesPacket();
        pk.protocol = ProtocolInfo.v1_26_20_26;
        pk.gameVersion = GameVersion.V1_26_20_26;
        pk.tryEncode();
        CACHED_PACKET_EMPTY = pk.compress(Deflater.BEST_COMPRESSION);
    }

    /**
     * 登录期缓存包：v2192 起带 vanilla 数据，旧版本为空数据 / cached join-time packet:
     * vanilla data since v2192, empty data for older protocols.
     */
    public static BatchPacket getCachedPacket(int protocol) {
        if (protocol >= ProtocolInfo.v1_26_50_27) {
            return CACHED_PACKET_2192;
        }
        return CACHED_PACKET_EMPTY;
    }

    /**
     * 预编码的原始载荷，设置后 encode 直接透传 / pre-encoded raw payload; encode passes it through when set.
     */
    private byte[] bin;

    /**
     * List of serializable voxel shapes.
     * Each shape contains multiple cells with their dimensional data.
     */
    public List<SerializableVoxelShape> shapes = new ArrayList<>();

    /**
     * Name to registry handle mapping.
     * Maps voxel shape names to their registry handles.
     */
    public Map<String, Integer> nameMap = new LinkedHashMap<>();

    /**
     * Number of custom shapes.
     *
     * @since v944
     */
    public int customShapeCount;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        int shapeCount = (int) this.getUnsignedVarInt();
        this.shapes = new ArrayList<>(shapeCount);

        for (int i = 0; i < shapeCount; i++) {
            SerializableVoxelShape shape = new SerializableVoxelShape();

            // Read single cells definition
            short xSize = (short) this.getByte();
            short ySize = (short) this.getByte();
            short zSize = (short) this.getByte();

            // Read storage array
            int storageCount = (int) this.getUnsignedVarInt();
            List<Short> storage = new ArrayList<>(storageCount);
            for (int k = 0; k < storageCount; k++) {
                storage.add((short) this.getByte());
            }

            shape.setCells(new SerializableVoxelShape.SerializableCells(xSize, ySize, zSize, storage));

            // Read X coordinates
            int xCount = (int) this.getUnsignedVarInt();
            List<Float> xCoordinates = new ArrayList<>(xCount);
            for (int j = 0; j < xCount; j++) {
                xCoordinates.add(this.getLFloat());
            }
            shape.setXCoordinates(xCoordinates);

            // Read Y coordinates
            int yCount = (int) this.getUnsignedVarInt();
            List<Float> yCoordinates = new ArrayList<>(yCount);
            for (int j = 0; j < yCount; j++) {
                yCoordinates.add(this.getLFloat());
            }
            shape.setYCoordinates(yCoordinates);

            // Read Z coordinates
            int zCount = (int) this.getUnsignedVarInt();
            List<Float> zCoordinates = new ArrayList<>(zCount);
            for (int j = 0; j < zCount; j++) {
                zCoordinates.add(this.getLFloat());
            }
            shape.setZCoordinates(zCoordinates);

            this.shapes.add(shape);
        }

        // Read name map
        int mapCount = (int) this.getUnsignedVarInt();
        this.nameMap = new LinkedHashMap<>(mapCount);
        for (int i = 0; i < mapCount; i++) {
            String name = this.getString();
            int handle = this.getLShort();
            this.nameMap.put(name, handle);
        }

        if (this.protocol >= ProtocolInfo.v1_26_10) {
            this.customShapeCount = this.getLShort();
        }
    }

    @Override
    public void encode() {
        this.reset();

        if (this.bin != null) {
            this.put(this.bin);
            return;
        }

        // Write shapes array
        this.putUnsignedVarInt(this.shapes.size());
        for (SerializableVoxelShape shape : this.shapes) {
            // Write single cells definition
            SerializableVoxelShape.SerializableCells cell = shape.getCells();
            this.putByte((byte) cell.getXSize());
            this.putByte((byte) cell.getYSize());
            this.putByte((byte) cell.getZSize());

            // Write storage array
            this.putUnsignedVarInt(cell.getStorage().size());
            for (Short value : cell.getStorage()) {
                this.putByte(value.byteValue());
            }

            // Write X coordinates
            this.putUnsignedVarInt(shape.getXCoordinates().size());
            for (Float coordinate : shape.getXCoordinates()) {
                this.putLFloat(coordinate);
            }

            // Write Y coordinates
            this.putUnsignedVarInt(shape.getYCoordinates().size());
            for (Float coordinate : shape.getYCoordinates()) {
                this.putLFloat(coordinate);
            }

            // Write Z coordinates
            this.putUnsignedVarInt(shape.getZCoordinates().size());
            for (Float coordinate : shape.getZCoordinates()) {
                this.putLFloat(coordinate);
            }
        }

        // Write name map
        this.putUnsignedVarInt(this.nameMap.size());
        for (Map.Entry<String, Integer> entry : this.nameMap.entrySet()) {
            this.putString(entry.getKey());
            this.putLShort(entry.getValue());
        }

        if (this.protocol >= ProtocolInfo.v1_26_10) {
            this.putLShort(this.customShapeCount);
        }
    }
}
