package cn.nukkit.network.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Syncs client with server voxel shape data on world join.
 * This packet contains a copy of all behavior pack voxel shapes data.
 * @since v924
 */
@ToString
public class VoxelShapesPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.VOXEL_SHAPES_PACKET;

    public VoxelShape[] shapes = new VoxelShape[0];
    public NameMapEntry[] nameMap = new NameMapEntry[0];

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
        this.shapes = new VoxelShape[shapeCount];
        for (int i = 0; i < shapeCount; i++) {
            VoxelShape shape = new VoxelShape();
            shape.xSize = this.getByte();
            shape.ySize = this.getByte();
            shape.zSize = this.getByte();
            shape.storage = this.getByteArray();

            int xCount = (int) this.getUnsignedVarInt();
            shape.xCoordinates = new float[xCount];
            for (int j = 0; j < xCount; j++) {
                shape.xCoordinates[j] = this.getLFloat();
            }

            int yCount = (int) this.getUnsignedVarInt();
            shape.yCoordinates = new float[yCount];
            for (int j = 0; j < yCount; j++) {
                shape.yCoordinates[j] = this.getLFloat();
            }

            int zCount = (int) this.getUnsignedVarInt();
            shape.zCoordinates = new float[zCount];
            for (int j = 0; j < zCount; j++) {
                shape.zCoordinates[j] = this.getLFloat();
            }

            this.shapes[i] = shape;
        }

        int mapCount = (int) this.getUnsignedVarInt();
        this.nameMap = new NameMapEntry[mapCount];
        for (int i = 0; i < mapCount; i++) {
            this.nameMap[i] = new NameMapEntry(this.getString(), this.getLShort());
        }
    }

    @Override
    public void encode() {
        this.reset();

        this.putUnsignedVarInt(this.shapes.length);
        for (VoxelShape shape : this.shapes) {
            this.putByte((byte) shape.xSize);
            this.putByte((byte) shape.ySize);
            this.putByte((byte) shape.zSize);
            this.putByteArray(shape.storage);

            this.putUnsignedVarInt(shape.xCoordinates.length);
            for (float value : shape.xCoordinates) {
                this.putLFloat(value);
            }

            this.putUnsignedVarInt(shape.yCoordinates.length);
            for (float value : shape.yCoordinates) {
                this.putLFloat(value);
            }

            this.putUnsignedVarInt(shape.zCoordinates.length);
            for (float value : shape.zCoordinates) {
                this.putLFloat(value);
            }
        }

        this.putUnsignedVarInt(this.nameMap.length);
        for (NameMapEntry entry : this.nameMap) {
            this.putString(entry.name);
            this.putLShort(entry.registryHandle);
        }
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class VoxelShape {
        /**
         * Number of cells along the X axis.
         */
        public int xSize;
        /**
         * Number of cells along the Y axis.
         */
        public int ySize;
        /**
         * Number of cells along the Z axis.
         */
        public int zSize;
        /**
         * Solid/empty state per cell.
         */
        public byte[] storage = new byte[0];

        /**
         * Cell boundaries along the X axis.
         */
        public float[] xCoordinates = new float[0];
        /**
         * Cell boundaries along the Y axis.
         */
        public float[] yCoordinates = new float[0];
        /**
         * Cell boundaries along the Z axis.
         */
        public float[] zCoordinates = new float[0];
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class NameMapEntry {
        public String name = "";
        public int registryHandle;
    }
}
