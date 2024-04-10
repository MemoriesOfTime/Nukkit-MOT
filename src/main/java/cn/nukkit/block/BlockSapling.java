package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.object.BasicGenerator;
import cn.nukkit.level.generator.object.tree.*;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Angelic47
 * Nukkit Project
 */
public class BlockSapling extends BlockFlowable {

    public static final int OAK = 0;
    public static final int SPRUCE = 1;
    public static final int BIRCH = 2;
    public static final int JUNGLE = 3;
    public static final int ACACIA = 4;
    public static final int DARK_OAK = 5;
    public static final int BIRCH_TALL = 10; //TODO fix

    public static final int TYPE_BIT = 0x07;
    public static final int AGED_BIT = 0x08;

    public BlockSapling() {
        this(0);
    }

    public BlockSapling(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SAPLING;
    }

    @Override
    public String getName() {
        String[] names = new String[]{
                "Oak Sapling",
                "Spruce Sapling",
                "Birch Sapling",
                "Jungle Sapling",
                "Acacia Sapling",
                "Dark Oak Sapling",
                "",
                ""
        };
        return names[this.getDamage() & TYPE_BIT];
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (!this.isSupportInvalid()) {
            this.getLevel().setBlock(block, this, true, true);
            return true;
        }

        return false;
    }

    protected boolean isSupportInvalid() {
        Block down = this.down();
        int id = down.getId();
        return !(id == Block.GRASS || id == Block.DIRT || id == Block.FARMLAND || id == Block.PODZOL || id == MYCELIUM);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0F) { // Bone meal
            if (player != null && !player.isCreative()) {
                item.count--;
            }

            this.level.addParticle(new BoneMealParticle(this));
            if (ThreadLocalRandom.current().nextFloat() >= 0.45) {
                return true;
            }

            return this.grow();
        }
        this.getLevel().loadChunk((int) this.x >> 4, (int) this.z >> 4);
        return false;
    }

    public boolean grow() {
        BasicGenerator generator = null;
        boolean bigTree = false;

        int x = 0;
        int z = 0;

        switch (this.getDamage()) {
            case JUNGLE:
                loop:
                for (x = 0; x >= -1; --x) {
                    for (z = 0; z >= -1; --z) {
                        if (this.findSaplings(x, z, JUNGLE)) {
                            generator = new ObjectJungleBigTree(10, 20, Block.get(WOOD, BlockWood.JUNGLE), Block.get(LEAVES, BlockLeaves.JUNGLE));
                            bigTree = true;
                            break loop;
                        }
                    }
                }

                if (!bigTree) {
                    generator = new NewJungleTree(4, 7);
                }
                break;
            case ACACIA:
                generator = new ObjectSavannaTree();
                break;
            case DARK_OAK:
                loop:
                for (x = 0; x >= -1; --x) {
                    for (z = 0; z >= -1; --z) {
                        if (this.findSaplings(x, z, DARK_OAK)) {
                            generator = new ObjectDarkOakTree();
                            bigTree = true;
                            break loop;
                        }
                    }
                }

                if (!bigTree) {
                    return false;
                }
                break;
            case SPRUCE:
                loop:
                for (x = 0; x >= -1; --x) {
                    for (z = 0; z >= -1; --z) {
                        if (this.findSaplings(x, z, SPRUCE)) {
                            new ObjectBigSpruceTree(0.5f, 5, true).placeObject(this.level, (int) this.x, (int) this.y, (int) this.z, new NukkitRandom());
                            bigTree = true;
                            break loop;
                        }
                    }
                }

                if (!bigTree) {
                    ObjectTree.growTree(this.getLevel(), (int) this.x, (int) this.y, (int) this.z, new NukkitRandom(), this.getDamage() & TYPE_BIT);
                }

                return true;
            default:
                ObjectTree.growTree(this.getLevel(), (int) this.x, (int) this.y, (int) this.z, new NukkitRandom(), this.getDamage() & TYPE_BIT);
                return true;
        }

        Block air = Block.get(BlockID.AIR);

        if (bigTree) {
            this.level.setBlock(this.add(x, 0, z), air, true, false);
            this.level.setBlock(this.add(x + 1, 0, z), air, true, false);
            this.level.setBlock(this.add(x, 0, z + 1), air, true, false);
            this.level.setBlock(this.add(x + 1, 0, z + 1), air, true, false);
        } else {
            this.level.setBlock(this, air, true, false);
        }

        if (!generator.generate(this.level, new NukkitRandom(), this.add(x, 0, z))) {
            if (bigTree) {
                this.level.setBlock(this.add(x, 0, z), this, true, false);
                this.level.setBlock(this.add(x + 1, 0, z), this, true, false);
                this.level.setBlock(this.add(x, 0, z + 1), this, true, false);
                this.level.setBlock(this.add(x + 1, 0, z + 1), this, true, false);
            } else {
                this.level.setBlock(this, this, true, false);
            }
        }

        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.isSupportInvalid()) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) { //Growth
            if (Utils.rand(1, 7) == 1) {
                if (this.isAged()) {
                    if ((this.getDamage() & TYPE_BIT) == ACACIA) {
                        this.level.setBlock(this, Block.get(BlockID.AIR), true, false);
                        new ObjectSavannaTree().generate(level, new NukkitRandom(), this);
                    } else {
                        ObjectTree.growTree(this.getLevel(), (int) this.x, (int) this.y, (int) this.z, new NukkitRandom(), this.getDamage() & TYPE_BIT);
                    }
                } else {
                    this.setAged(true);
                    this.getLevel().setBlock(this, this, true);
                    return Level.BLOCK_UPDATE_RANDOM;
                }
            } else {
                return Level.BLOCK_UPDATE_RANDOM;
            }
        }
        return 1;
    }

    private boolean findSaplings(int x, int z, int type) {
        return this.isSameType(this.add(x, 0, z), type) && this.isSameType(this.add(x + 1, 0, z), type) && this.isSameType(this.add(x, 0, z + 1), type) && this.isSameType(this.add(x + 1, 0, z + 1), type);
    }

    public boolean isSameType(Vector3 pos, int type) {
        Block block = this.level.getBlock(pos);
        return block.getId() == SAPLING && block.getDamage() == type;
    }

    @Override
    public Item toItem() {
        return Item.get(BlockID.SAPLING, this.getDamage() & 0x7);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.FOLIAGE_BLOCK_COLOR;
    }

    public boolean isAged() {
        return this.getDamage(AGED_BIT) == 1;
    }

    public void setAged(boolean aged) {
        this.setDamage(AGED_BIT, aged ? 1 : 0);
    }

}
