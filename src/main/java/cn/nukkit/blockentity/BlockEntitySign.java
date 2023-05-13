package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.block.SignChangeEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.ByteTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;
import cn.nukkit.utils.TextFormat;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BlockEntitySign extends BlockEntitySpawnable {

    public static final String TAG_TEXT_BLOB = "Text";
    public static final String TAG_TEXT_LINE = "Text%d";
    public static final String TAG_TEXT_COLOR = "SignTextColor";
    public static final String TAG_GLOWING_TEXT = "IgnoreLighting";
    public static final String TAG_PERSIST_FORMATTING = "PersistFormatting";
    public static final String TAG_LEGACY_BUG_RESOLVE = "TextIgnoreLegacyBugResolved";
    public static final String TAG_FRONT_TEXT = "FrontText";
    public static final String TAG_BACK_TEXT = "BackText";
    public static final String TAG_WAXED = "IsWaxed";
    public static final String TAG_LOCKED_FOR_EDITING_BY = "LockedForEditingBy";

    private String[] text;

    public BlockEntitySign(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        text = new String[4];

        if (!namedTag.contains(TAG_TEXT_BLOB)) {

            for (int i = 1; i <= 4; i++) {
                String key = TAG_TEXT_BLOB + i;

                if (namedTag.contains(key)) {
                    String line = namedTag.getString(key);

                    this.text[i - 1] = line;

                    this.namedTag.remove(key);
                }
            }
        } else {
            String[] lines = namedTag.getString(TAG_TEXT_BLOB).split("\n", 4);

            for (int i = 0; i < text.length; i++) {
                if (i < lines.length)
                    text[i] = lines[i];
                else
                    text[i] = "";
            }
        }

        // Check old text to sanitize
        if (text != null) {
            sanitizeText(text);
        }

        if (!this.namedTag.contains(TAG_TEXT_COLOR) || !(this.namedTag.get(TAG_TEXT_COLOR) instanceof IntTag)) {
            this.setColor(DyeColor.BLACK.getSignColor());
        }
        if (!this.namedTag.contains(TAG_GLOWING_TEXT) || !(this.namedTag.get(TAG_GLOWING_TEXT) instanceof ByteTag)) {
            this.setGlowing(false);
        }

        super.initBlockEntity();
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.remove("Creator");
    }

    @Override
    public boolean isBlockEntityValid() {
        int blockID = getBlock().getId();
        return blockID == Block.SIGN_POST || blockID == Block.WALL_SIGN;
    }

    /**
     * 设置编辑此告示牌的玩家的运行时实体 ID。只有此玩家才能编辑告示牌。这用于防止多个玩家同时编辑同一告示牌，并防止玩家编辑他们未放置的告示牌。
     * <p>
     * Sets the runtime entity ID of the player editing this sign. Only this player will be able to edit the sign.
     * This is used to prevent multiple players from editing the same sign at the same time, and to prevent players
     * from editing signs they didn't place.
     */
    public long getEditorEntityRuntimeId() {
        return this.namedTag.getLong(TAG_LOCKED_FOR_EDITING_BY);
    }

    public void setEditorEntityRuntimeId(Long editorEntityRuntimeId) {
        this.namedTag.putLong(TAG_LOCKED_FOR_EDITING_BY, editorEntityRuntimeId == null ? -1L : editorEntityRuntimeId);
    }

    public boolean setText(String... lines) {
        for (int i = 0; i < 4; i++) {
            if (i < lines.length)
                text[i] = lines[i];
            else
                text[i] = "";
        }

        this.namedTag.putString(TAG_TEXT_BLOB, String.join("\n", text));
        this.spawnToAll();

        if (this.chunk != null) {
            setDirty();
        }

        return true;
    }

    public String[] getText() {
        return text;
    }

    public BlockColor getColor() {
        return new BlockColor(this.namedTag.getInt(TAG_TEXT_COLOR), true);
    }

    public void setColor(BlockColor color) {
        this.namedTag.putInt(TAG_TEXT_COLOR, color.getARGB());
    }

    public boolean isGlowing() {
        return this.namedTag.getBoolean(TAG_GLOWING_TEXT);
    }

    public void setGlowing(boolean glowing) {
        this.namedTag.putBoolean(TAG_GLOWING_TEXT, glowing);
    }

    @Override
    public boolean updateCompoundTag(CompoundTag nbt, Player player) {
        if (!nbt.getString("id").equals(BlockEntity.SIGN)) {
            return false;
        }
        String[] lines = new String[4];
        Arrays.fill(lines, "");
        String[] splitLines;
        if (player.protocol >= ProtocolInfo.v1_19_80) { //1.19.80 =+ 开始支持双面文本，这里读取正面文本
            splitLines = nbt.getCompound(TAG_FRONT_TEXT).getString(TAG_TEXT_BLOB).split("\n", 4);
        } else {
            splitLines = nbt.getString(TAG_TEXT_BLOB).split("\n", 4);
        }
        System.arraycopy(splitLines, 0, lines, 0, splitLines.length);

        sanitizeText(lines);

        SignChangeEvent signChangeEvent = new SignChangeEvent(this.getBlock(), player, lines);

        if (!this.namedTag.contains("Creator") || !Objects.equals(player.getUniqueId().toString(), this.namedTag.getString("Creator"))) {
            signChangeEvent.setCancelled();
        }

        if (player.getRemoveFormat()) {
            for (int i = 0; i < lines.length; i++) {
                lines[i] = TextFormat.clean(lines[i]);
            }
        }

        this.server.getPluginManager().callEvent(signChangeEvent);

        if (!signChangeEvent.isCancelled()) {
            this.setText(signChangeEvent.getLines());
            this.setEditorEntityRuntimeId(null);
            return true;
        }

        return false;
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return new CompoundTag()
                .putString("id", BlockEntity.SIGN)
                .putString(TAG_TEXT_BLOB, this.namedTag.getString(TAG_TEXT_BLOB))
                .putInt(TAG_TEXT_COLOR, this.getColor().getARGB())
                .putBoolean(TAG_GLOWING_TEXT, this.isGlowing())
                .putBoolean(TAG_LEGACY_BUG_RESOLVE, true)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putByte(TAG_WAXED, 0)
                .putLong(TAG_LOCKED_FOR_EDITING_BY, getEditorEntityRuntimeId());
    }

    private static void sanitizeText(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            // Don't allow excessive text per line
            if (lines[i] != null) {
                lines[i] = lines[i].substring(0, Math.min(200, lines[i].length()));
            }
        }
    }
}
