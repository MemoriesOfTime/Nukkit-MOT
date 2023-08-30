package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySign;
import cn.nukkit.event.block.SignColorChangeEvent;
import cn.nukkit.event.block.SignGlowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.item.ItemSign;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.OpenSignPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;
import cn.nukkit.utils.Faceable;

/**
 * @author Nukkit Project Team
 */
public class BlockSignPost extends BlockTransparentMeta implements Faceable {

    public BlockSignPost() {
        this(0);
    }

    public BlockSignPost(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SIGN_POST;
    }

    @Override
    public double getHardness() {
        return 1;
    }

    @Override
    public double getResistance() {
        return 5;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public String getName() {
        return "Sign Post";
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return null;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    protected int getPostId() {
        return getId();
    }

    public int getWallId() {
        return WALL_SIGN;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (face != BlockFace.DOWN) {
            CompoundTag nbt = new CompoundTag()
                    .putString("id", BlockEntity.SIGN)
                    .putInt("x", (int) block.x)
                    .putInt("y", (int) block.y)
                    .putInt("z", (int) block.z)
                    .putString("Text1", "")
                    .putString("Text2", "")
                    .putString("Text3", "")
                    .putString("Text4", "");

            if (face == BlockFace.UP) {
                setDamage((int) Math.floor(((player.yaw + 180) * 16 / 360) + 0.5) & 0x0f);
                getLevel().setBlock(block, Block.get(getPostId(), getDamage()), true);
            } else {
                setDamage(face.getIndex());
                getLevel().setBlock(block, Block.get(getWallId(), getDamage()), true);
            }

            if (player != null) {
                nbt.putString("Creator", player.getUniqueId().toString()); //已不再使用，保留是防止有插件用
            }

            if (item.hasCustomBlockData()) {
                for (Tag aTag : item.getCustomBlockData().getAllTags()) {
                    nbt.put(aTag.getName(), aTag);
                }
            }

            BlockEntity blockEntity = BlockEntity.createBlockEntity(BlockEntity.SIGN, this.level.getChunk(block.getChunkX(), block.getChunkZ()), nbt);
            if (player != null && blockEntity instanceof BlockEntitySign blockEntitySign) {
                blockEntitySign.setEditorEntityRuntimeId(player.getId());
                if (player.protocol >= ProtocolInfo.v1_19_80) {
                    OpenSignPacket openSignPacket = new OpenSignPacket();
                    openSignPacket.setPosition(this.asBlockVector3());
                    openSignPacket.setFrontSide(true);
                    player.dataPacket(openSignPacket);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (down().getId() == Block.AIR) {
                getLevel().useBreakOn(this);

                return Level.BLOCK_UPDATE_NORMAL;
            }
        }

        return 0;
    }

    @Override
    public Item toItem() {
        return new ItemSign();
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.AIR_BLOCK_COLOR;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromIndex(this.getDamage() & 0x07);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE) {
            BlockEntity blockEntity = this.level.getBlockEntity(this);
            if (!(blockEntity instanceof BlockEntitySign)) {
                return false;
            }
            BlockEntitySign sign = (BlockEntitySign) blockEntity;

            int meta = item.getDamage();
            if (meta == ItemDye.INK_SAC || meta == ItemDye.GLOW_INK_SAC) {
                boolean glow = meta == ItemDye.GLOW_INK_SAC;
                if (sign.isGlowing() == glow) {
                    if (player != null) {
                        sign.spawnTo(player);
                    }
                    return false;
                }

                SignGlowEvent event = new SignGlowEvent(this, player, glow);
                this.level.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    if (player != null) {
                        sign.spawnTo(player);
                    }
                    return false;
                }

                sign.setGlowing(glow);
                sign.spawnToAll();

                this.level.addLevelEvent(this, LevelEventPacket.EVENT_SOUND_INK_SACE_USED);

                if (player != null && (player.getGamemode() & 0x01) == 0) {
                    item.count--;
                }

                return true;
            }

            BlockColor color = DyeColor.getByDyeData(meta).getSignColor();
            if (color.equals(sign.getColor())) {
                if (player != null) {
                    sign.spawnTo(player);
                }
                return false;
            }

            SignColorChangeEvent event = new SignColorChangeEvent(this, player, color);
            this.level.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                if (player != null) {
                    sign.spawnTo(player);
                }
                return false;
            }

            sign.setColor(color);
            sign.spawnToAll();

            this.level.addLevelEvent(this, LevelEventPacket.EVENT_SOUND_DYE_USED);

            if (player != null && (player.getGamemode() & 0x01) == 0) {
                item.count--;
            }

            return true;
        }
        return false;
    }
}
