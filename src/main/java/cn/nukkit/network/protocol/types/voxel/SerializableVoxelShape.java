package cn.nukkit.network.protocol.types.voxel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a serializable voxel shape structure for client-server synchronization.
 * Contains multiple cells with their dimensional data and coordinate arrays.
 *
 * @since v924
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializableVoxelShape {

    /**
     * List of cells that make up this voxel shape.
     * Each cell contains dimensional information and storage data.
     */
    private List<SerializableCells> cells = new ArrayList<>();

    /**
     * X-axis coordinate boundaries for all cells.
     */
    private List<Float> xCoordinates = new ArrayList<>();

    /**
     * Y-axis coordinate boundaries for all cells.
     */
    private List<Float> yCoordinates = new ArrayList<>();

    /**
     * Z-axis coordinate boundaries for all cells.
     */
    private List<Float> zCoordinates = new ArrayList<>();

    /**
     * Represents a single cell within a voxel shape.
     * Contains dimensional size information and storage data for voxel states.
     *
     * @since v924
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerializableCells {

        /**
         * Number of voxels along the X axis.
         */
        private short xSize;

        /**
         * Number of voxels along the Y axis.
         */
        private short ySize;

        /**
         * Number of voxels along the Z axis.
         */
        private short zSize;

        /**
         * Storage data for voxel states.
         * Each value represents the state of a voxel at a specific position.
         */
        private List<Short> storage = new ArrayList<>();
    }
}
