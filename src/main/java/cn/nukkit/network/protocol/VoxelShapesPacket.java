package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.voxel.SerializableVoxelShape;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Syncs client with server voxel shape data on world join.
 * This packet contains a copy of all behavior pack voxel shapes data.
 * Sends the serializable voxel shapes data to the client as it's needed on both the client and server.
 *
 * @since v924
 */
@ToString
public class VoxelShapesPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.VOXEL_SHAPES_PACKET;

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

            // Read cells array
            int cellCount = (int) this.getUnsignedVarInt();
            List<SerializableVoxelShape.SerializableCells> cells = new ArrayList<>(cellCount);

            for (int j = 0; j < cellCount; j++) {
                short xSize = (short) this.getByte();
                short ySize = (short) this.getByte();
                short zSize = (short) this.getByte();

                // Read storage array
                int storageCount = (int) this.getUnsignedVarInt();
                List<Short> storage = new ArrayList<>(storageCount);
                for (int k = 0; k < storageCount; k++) {
                    storage.add((short) this.getByte());
                }

                cells.add(new SerializableVoxelShape.SerializableCells(xSize, ySize, zSize, storage));
            }

            shape.setCells(cells);

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
    }

    @Override
    public void encode() {
        this.reset();

        // Write shapes array
        this.putUnsignedVarInt(this.shapes.size());
        for (SerializableVoxelShape shape : this.shapes) {
            // Write cells array
            this.putUnsignedVarInt(shape.getCells().size());
            for (SerializableVoxelShape.SerializableCells cell : shape.getCells()) {
                this.putByte((byte) cell.getXSize());
                this.putByte((byte) cell.getYSize());
                this.putByte((byte) cell.getZSize());

                // Write storage array
                this.putUnsignedVarInt(cell.getStorage().size());
                for (Short value : cell.getStorage()) {
                    this.putByte(value.byteValue());
                }
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
    }
}
