package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockStructureBlock;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.StructureBlockInventory;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.structure.*;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.StructureBlockUpdatePacket;
import cn.nukkit.network.protocol.types.structure.StructureSettings;

import java.util.HashSet;

/**
 * 结构方块的方块实体：保存编辑器设置、执行保存/加载并驱动客户端界面。
 * <p>
 * Structure block block entity: stores editor settings, performs save/load and
 * drives the client-side editor.
 */
public class BlockEntityStructureBlock extends BlockEntitySpawnable implements InventoryHolder {

    public static final String TAG_DATA = "data";
    public static final String TAG_DATA_FIELD = "dataField";
    public static final String TAG_STRUCTURE_NAME = "structureName";
    public static final String TAG_IGNORE_ENTITIES = "ignoreEntities";
    public static final String TAG_INCLUDE_PLAYERS = "includePlayers";
    public static final String TAG_REMOVE_BLOCKS = "removeBlocks";
    public static final String TAG_SHOW_BOUNDING_BOX = "showBoundingBox";
    public static final String TAG_ROTATION = "rotation";
    public static final String TAG_MIRROR = "mirror";
    public static final String TAG_ANIMATION_MODE = "animationMode";
    public static final String TAG_ANIMATION_SECONDS = "animationSeconds";
    public static final String TAG_INTEGRITY = "integrity";
    public static final String TAG_SEED = "seed";
    public static final String TAG_REDSTONE_SAVE_MODE = "redstoneSaveMode";
    public static final String TAG_IS_POWERED = "isPowered";
    public static final String TAG_LAST_TOUCHED_PLAYER_ID = "lastTouchedPlayerId";
    public static final String TAG_X_OFFSET = "xStructureOffset";
    public static final String TAG_Y_OFFSET = "yStructureOffset";
    public static final String TAG_Z_OFFSET = "zStructureOffset";
    public static final String TAG_X_SIZE = "xStructureSize";
    public static final String TAG_Y_SIZE = "yStructureSize";
    public static final String TAG_Z_SIZE = "zStructureSize";

    public static final int SAVE_TO_MEMORY = 0;
    public static final int SAVE_TO_DISK = 1;

    private static final int MAX_OFFSET = 384;

    private StructureBlockInventory inventory;
    private int data;
    private String dataField;
    private String structureName;
    private boolean ignoreEntities;
    private boolean includePlayers;
    private boolean removeBlocks;
    private boolean showBoundingBox;
    private int rotation;
    private int mirror;
    private int animationMode;
    private float animationSeconds;
    private float integrity;
    private long seed;
    private int redstoneSaveMode;
    private boolean powered;
    private long lastTouchedPlayerId;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private int sizeX;
    private int sizeY;
    private int sizeZ;

    public BlockEntityStructureBlock(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        // 注意：字段默认值必须在这里赋，构造期 initBlockEntity 早于子类字段初始化。
        // Defaults must be assigned here; initBlockEntity runs before subclass field initializers.
        this.inventory = new StructureBlockInventory(this);
        this.data = this.chunk.getBlockData((int) this.x & 0x0f, (int) this.y, (int) this.z & 0x0f);
        this.dataField = "";
        this.structureName = "";
        this.ignoreEntities = false;
        this.includePlayers = false;
        this.removeBlocks = false;
        this.showBoundingBox = true;
        this.rotation = 0;
        this.mirror = 0;
        this.animationMode = 0;
        this.animationSeconds = 0f;
        this.integrity = 100f;
        this.seed = 0L;
        this.redstoneSaveMode = SAVE_TO_DISK;
        this.powered = false;
        this.lastTouchedPlayerId = 0L;
        this.offsetX = 0;
        this.offsetY = -1;
        this.offsetZ = 0;
        this.sizeX = 5;
        this.sizeY = 5;
        this.sizeZ = 5;
        this.loadEditorData();
        this.loadStructureGeometry();
        this.loadStructureOptions();
        super.initBlockEntity();
    }

    private void loadEditorData() {
        CompoundTag nbt = this.namedTag;
        if (nbt.containsInt(TAG_DATA)) {
            this.data = clamp(nbt.getInt(TAG_DATA), BlockStructureBlock.TYPE_DATA, BlockStructureBlock.TYPE_EXPORT);
        }
        if (nbt.containsString(TAG_DATA_FIELD)) {
            this.dataField = nbt.getString(TAG_DATA_FIELD);
        }
        if (nbt.containsString(TAG_STRUCTURE_NAME)) {
            this.structureName = nbt.getString(TAG_STRUCTURE_NAME);
        }
        if (nbt.containsInt(TAG_REDSTONE_SAVE_MODE)) {
            this.redstoneSaveMode = nbt.getInt(TAG_REDSTONE_SAVE_MODE);
        }
        if (nbt.containsNumber(TAG_LAST_TOUCHED_PLAYER_ID)) {
            this.lastTouchedPlayerId = nbt.getLong(TAG_LAST_TOUCHED_PLAYER_ID);
        }
        if (nbt.containsByte(TAG_INCLUDE_PLAYERS)) {
            this.includePlayers = nbt.getBoolean(TAG_INCLUDE_PLAYERS);
        }
        if (nbt.containsByte(TAG_SHOW_BOUNDING_BOX)) {
            this.showBoundingBox = nbt.getBoolean(TAG_SHOW_BOUNDING_BOX);
        }
    }

    private void loadStructureGeometry() {
        CompoundTag nbt = this.namedTag;
        if (nbt.containsInt(TAG_X_OFFSET)) {
            this.offsetX = nbt.getInt(TAG_X_OFFSET);
        }
        if (nbt.containsInt(TAG_Y_OFFSET)) {
            this.offsetY = nbt.getInt(TAG_Y_OFFSET);
        }
        if (nbt.containsInt(TAG_Z_OFFSET)) {
            this.offsetZ = nbt.getInt(TAG_Z_OFFSET);
        }
        if (nbt.containsInt(TAG_X_SIZE)) {
            this.sizeX = nbt.getInt(TAG_X_SIZE);
        }
        if (nbt.containsInt(TAG_Y_SIZE)) {
            this.sizeY = nbt.getInt(TAG_Y_SIZE);
        }
        if (nbt.containsInt(TAG_Z_SIZE)) {
            this.sizeZ = nbt.getInt(TAG_Z_SIZE);
        }
    }

    private void loadStructureOptions() {
        CompoundTag nbt = this.namedTag;
        if (nbt.containsByte(TAG_IGNORE_ENTITIES)) {
            this.ignoreEntities = nbt.getBoolean(TAG_IGNORE_ENTITIES);
        }
        if (nbt.containsByte(TAG_REMOVE_BLOCKS)) {
            this.removeBlocks = nbt.getBoolean(TAG_REMOVE_BLOCKS);
        }
        if (nbt.containsByte(TAG_ROTATION)) {
            this.rotation = nbt.getByte(TAG_ROTATION);
        }
        if (nbt.containsByte(TAG_MIRROR)) {
            this.mirror = nbt.getByte(TAG_MIRROR);
        }
        if (nbt.containsByte(TAG_ANIMATION_MODE)) {
            this.animationMode = nbt.getByte(TAG_ANIMATION_MODE);
        }
        if (nbt.containsFloat(TAG_ANIMATION_SECONDS)) {
            this.animationSeconds = nbt.getFloat(TAG_ANIMATION_SECONDS);
        }
        if (nbt.containsFloat(TAG_INTEGRITY)) {
            this.integrity = nbt.getFloat(TAG_INTEGRITY);
        }
        if (nbt.containsNumber(TAG_SEED)) {
            this.seed = nbt.getLong(TAG_SEED);
        }
        if (nbt.containsByte(TAG_IS_POWERED)) {
            this.powered = nbt.getBoolean(TAG_IS_POWERED);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.writeEditorFields(this.namedTag);
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag tag = getDefaultCompound(this, STRUCTURE_BLOCK);
        this.writeEditorFields(tag);
        return tag;
    }

    /**
     * 存档与下发共用同一份编辑器字段，避免两处列表漂移。
     * <p>
     * Shared by persistence and spawn packets so the two field lists cannot drift apart.
     */
    private void writeEditorFields(CompoundTag tag) {
        tag.putInt(TAG_DATA, this.data)
                .putString(TAG_DATA_FIELD, this.dataField)
                .putString(TAG_STRUCTURE_NAME, this.structureName)
                .putBoolean(TAG_IGNORE_ENTITIES, this.ignoreEntities)
                .putBoolean(TAG_INCLUDE_PLAYERS, this.includePlayers)
                .putBoolean(TAG_REMOVE_BLOCKS, this.removeBlocks)
                .putBoolean(TAG_SHOW_BOUNDING_BOX, this.showBoundingBox)
                .putByte(TAG_ROTATION, (byte) this.rotation)
                .putByte(TAG_MIRROR, (byte) this.mirror)
                .putByte(TAG_ANIMATION_MODE, (byte) this.animationMode)
                .putFloat(TAG_ANIMATION_SECONDS, this.animationSeconds)
                .putFloat(TAG_INTEGRITY, this.integrity)
                .putLong(TAG_SEED, this.seed)
                .putInt(TAG_REDSTONE_SAVE_MODE, this.redstoneSaveMode)
                .putBoolean(TAG_IS_POWERED, this.powered)
                .putLong(TAG_LAST_TOUCHED_PLAYER_ID, this.lastTouchedPlayerId)
                .putInt(TAG_X_OFFSET, this.offsetX)
                .putInt(TAG_Y_OFFSET, this.offsetY)
                .putInt(TAG_Z_OFFSET, this.offsetZ)
                .putInt(TAG_X_SIZE, this.sizeX)
                .putInt(TAG_Y_SIZE, this.sizeY)
                .putInt(TAG_Z_SIZE, this.sizeZ);
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getLevelBlock().getId() == BlockID.STRUCTURE_BLOCK;
    }

    @Override
    public void close() {
        if (!this.closed) {
            for (Player player : new HashSet<>(this.inventory.getViewers())) {
                player.removeWindow(this.inventory);
            }
            super.close();
        }
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public int getStructureType() {
        return this.data;
    }

    public String getStructureName() {
        return this.structureName;
    }

    /**
     * 应用客户端提交的编辑器设置；{@code packet.powered} 表示玩家按下了保存/加载按钮。
     * <p>
     * Applies editor settings from the client. {@code packet.powered} means the player
     * pressed the save/load button.
     */
    public void updateFromPacket(StructureBlockUpdatePacket packet, Player player) {
        StructureSettings settings = packet.settings;
        this.data = packet.type >= BlockStructureBlock.TYPE_DATA && packet.type <= BlockStructureBlock.TYPE_EXPORT
                ? packet.type : BlockStructureBlock.TYPE_DATA;
        this.dataField = packet.dataField;
        this.structureName = packet.name;
        this.ignoreEntities = settings.ignoringEntities;
        this.includePlayers = packet.includingPlayers;
        this.removeBlocks = settings.ignoringBlocks;
        this.showBoundingBox = packet.boundingBoxVisible;
        this.rotation = settings.rotation & 0x03;
        this.mirror = settings.mirror & 0x03;
        this.animationMode = settings.animationMode & 0x03;
        this.animationSeconds = Math.max(0f, settings.animationSeconds);
        this.integrity = Math.min(100f, Math.max(0f, settings.integrity));
        this.seed = settings.integritySeed;
        this.redstoneSaveMode = packet.redstoneSaveMode == SAVE_TO_MEMORY ? SAVE_TO_MEMORY : SAVE_TO_DISK;
        this.offsetX = clamp(settings.offsetX, -MAX_OFFSET, MAX_OFFSET);
        this.offsetY = clamp(settings.offsetY, -MAX_OFFSET, MAX_OFFSET);
        this.offsetZ = clamp(settings.offsetZ, -MAX_OFFSET, MAX_OFFSET);
        this.sizeX = clamp(settings.sizeX, 1, StructureIO.MAX_SIZE_X);
        this.sizeY = clamp(settings.sizeY, 1, StructureIO.MAX_SIZE_Y);
        this.sizeZ = clamp(settings.sizeZ, 1, StructureIO.MAX_SIZE_Z);
        this.lastTouchedPlayerId = player.getId();
        this.setDirty();
        if (packet.powered) {
            this.onPower();
        }
    }

    /**
     * 红石上升沿：保存或加载（导出不参与红石）。
     * <p>
     * Redstone rising edge: save or load. Export is not redstone-driven.
     */
    public void onRedstoneRise() {
        if (this.data == BlockStructureBlock.TYPE_SAVE || this.data == BlockStructureBlock.TYPE_LOAD) {
            this.execute(true);
        }
    }

    /**
     * 界面按钮触发的保存/加载；与原版一致，手动保存总是写盘，红石存档才看 Save Mode。
     * <p>
     * Save/load triggered from the UI. Manual saves always go to disk, matching vanilla;
     * only redstone-triggered saves honour the redstone save mode.
     */
    public void onPower() {
        this.execute(false);
    }

    private void execute(boolean fromRedstone) {
        if (this.data == BlockStructureBlock.TYPE_SAVE) {
            this.saveStructure(fromRedstone && this.redstoneSaveMode == SAVE_TO_MEMORY);
        } else if (this.data == BlockStructureBlock.TYPE_LOAD) {
            this.loadStructure();
        }
    }

    public boolean saveStructure(boolean memory) {
        if (!StructureAPI.isValidName(this.structureName)) {
            return false;
        }
        if (this.resolveBox()) {
            this.spawnToAll();
        }
        Structure structure = StructureCapture.capture(this.level, this.box(), !this.ignoreEntities);
        if (memory) {
            StructureAPI.saveMemory(structure, this.structureName);
            return true;
        }
        return StructureAPI.save(structure, this.structureName);
    }

    public boolean loadStructure() {
        Structure structure = StructureAPI.load(this.structureName);
        if (structure == null) {
            return false;
        }
        StructurePlacementOptions options = new StructurePlacementOptions(this.destination());
        options.rotation = this.rotation;
        options.mirror = this.mirror;
        options.integrity = this.integrity;
        options.integritySeed = (int) this.seed;
        options.includeEntities = !this.ignoreEntities;
        options.ignoringBlocks = this.removeBlocks;
        options.animationMode = this.animationMode;
        options.animationSeconds = this.animationSeconds;
        StructurePlacement.place(structure, this.level, options);
        return true;
    }

    /**
     * 导出：保存模式导出当前区域，加载模式导出已存模板。
     * <p>
     * Export: the save mode exports the current box, the load mode exports the saved template.
     */
    public CompoundTag exportNbt(boolean fromSaveMode) {
        if (!fromSaveMode) {
            Structure saved = StructureAPI.load(this.structureName);
            return saved == null ? null : StructureIO.toNbt(saved);
        }
        if (!StructureAPI.isValidName(this.structureName)) {
            return null;
        }
        if (this.resolveBox()) {
            this.spawnToAll();
        }
        return StructureIO.toNbt(StructureCapture.capture(this.level, this.box(), !this.ignoreEntities));
    }

    /**
     * 角落模式：同名 corner 结构方块与本方块共同确定包围盒（对齐原版 DETECT）。
     * <p>
     * Corner mode: a same-named corner structure block and this block define the box
     * (matching vanilla's detect action). Returns
     * {@code {offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ}} or {@code null}.
     */
    public int[] detectBox() {
        return this.detectBox(this.structureName);
    }

    /**
     * 按给定结构名找同名 corner 方块；界面查询尚未提交设置时用请求里的名字。
     * <p>
     * Detects the box against a given structure name; UI queries use the request name
     * before the edited settings are committed.
     */
    public int[] detectBox(String structureName) {
        if (this.data != BlockStructureBlock.TYPE_SAVE || structureName.isEmpty()) {
            return null;
        }
        BlockEntityStructureBlock corner = this.findCorner(structureName);
        if (corner == null) {
            return null;
        }
        int minX = Math.min((int) this.x, (int) corner.x);
        int minY = Math.min((int) this.y, (int) corner.y);
        int minZ = Math.min((int) this.z, (int) corner.z);
        return new int[]{
                minX - (int) this.x,
                minY - (int) this.y,
                minZ - (int) this.z,
                Math.abs((int) corner.x - (int) this.x) + 1,
                Math.abs((int) corner.y - (int) this.y) + 1,
                Math.abs((int) corner.z - (int) this.z) + 1
        };
    }

    private boolean resolveBox() {
        int[] box = this.detectBox();
        if (box == null) {
            return false;
        }
        this.offsetX = box[0];
        this.offsetY = box[1];
        this.offsetZ = box[2];
        this.sizeX = box[3];
        this.sizeY = box[4];
        this.sizeZ = box[5];
        this.setDirty();
        return true;
    }

    private BlockEntityStructureBlock findCorner(String structureName) {
        for (BlockEntity blockEntity : this.level.getBlockEntities().values()) {
            if (!(blockEntity instanceof BlockEntityStructureBlock candidate) || candidate == this) {
                continue;
            }
            if (candidate.data != BlockStructureBlock.TYPE_CORNER || !candidate.structureName.equals(structureName)) {
                continue;
            }
            if (Math.abs(candidate.x - this.x) > MAX_OFFSET || Math.abs(candidate.y - this.y) > MAX_OFFSET
                    || Math.abs(candidate.z - this.z) > MAX_OFFSET) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private StructureBox box() {
        return new StructureBox(
                new BlockVector3((int) this.x + this.offsetX, (int) this.y + this.offsetY, (int) this.z + this.offsetZ),
                new BlockVector3(this.sizeX, this.sizeY, this.sizeZ));
    }

    private BlockVector3 destination() {
        return new BlockVector3((int) this.x + this.offsetX, (int) this.y + this.offsetY, (int) this.z + this.offsetZ);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            this.setDirty();
        }
    }

    public boolean isPowered() {
        return this.powered;
    }
}
